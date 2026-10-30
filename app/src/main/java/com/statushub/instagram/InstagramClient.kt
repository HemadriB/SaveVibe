package com.statushub.instagram

import com.statushub.R
import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves Instagram links to direct media URLs.
 *
 * Requests run through a hidden WebView on the instagram.com origin instead of OkHttp:
 * Instagram fingerprints non-browser HTTP clients and serves them a login wall, while a
 * WebView is a real Chrome (TLS fingerprint, cookies, session tokens).
 *
 * - Logged out: public posts/reels via the same GraphQL query the Instagram website uses
 * - Logged in (cookies from [InstagramLoginDialog]): private posts the user follows, stories, highlights
 *
 * Note: Instagram rotates [POST_QUERY_DOC_ID] every few months; yt-dlp's instagram extractor
 * is a good place to find the current value.
 */
class InstagramClient private constructor(private val context: Context) {

    companion object {
        private const val TAG = "InstagramClient"
        private const val BASE_URL = "https://www.instagram.com"
        private const val APP_ID = "936619743392459"
        private const val ASBD_ID = "359341"
        private const val POST_QUERY_NAME = "PolarisLoggedOutDesktopWWWPostRootContentQuery"
        private const val POST_QUERY_DOC_ID = "27130156389949648"
        private const val HOST_TIMEOUT_MS = 20_000L
        private const val FETCH_TIMEOUT_MS = 30_000L
        /** Minimum gap between Instagram requests so fast browsing doesn't trigger rate limits */
        private const val MIN_REQUEST_GAP_MS = 1_200L
        /** After Instagram says "slow down", stop calling its API for this long */
        private const val COOLDOWN_MS = 3 * 60_000L

        @Volatile
        private var instance: InstagramClient? = null

        fun get(context: Context): InstagramClient =
            instance ?: synchronized(this) {
                instance ?: InstagramClient(context.applicationContext).also { instance = it }
            }

        fun isLoggedIn(): Boolean =
            CookieManager.getInstance().getCookie(BASE_URL)?.contains("sessionid=") == true

        /** Strips the WebView marker so Instagram treats us like mobile Chrome */
        fun browserUserAgent(webViewUserAgent: String): String =
            webViewUserAgent.replace("; wv", "").replace(Regex("""Version/\d+\.\d+ """), "")
    }

    private data class FetchResult(val status: Int, val url: String, val body: String) {
        val isRateLimited: Boolean
            get() = status == 429 || body.contains("\"spam\":true") ||
                body.contains("Please wait a few minutes", ignoreCase = true)
    }

    private var hostWebView: WebView? = null
    private var hostReady: CompletableDeferred<Unit>? = null
    private val pending = ConcurrentHashMap<String, CompletableDeferred<FetchResult>>()
    private var lsdToken: String? = null
    private val throttle = Mutex()
    private var lastRequestAt = 0L
    @Volatile private var blockedUntil = 0L
    /** Value of Instagram's x-ig-set-www-claim header; the website echoes it back on every API call */
    @Volatile private var wwwClaim = "0"

    fun logout() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        lsdToken = null
    }

    /** Called after login so the next request picks up the new session */
    fun onLoginChanged() {
        lsdToken = null
    }

    suspend fun fetchPost(url: String): InstagramPost = withContext(Dispatchers.IO) {
        when (val target = InstagramLinks.parse(url)) {
            is InstagramLinks.Target.Post -> fetchByShortcode(target.shortcode)
            is InstagramLinks.Target.Story -> fetchStory(target)
            is InstagramLinks.Target.Highlight -> fetchReel("highlight:${target.highlightId}", target.highlightId, null)
            is InstagramLinks.Target.Share -> {
                val resolved = jsFetch(target.url).url
                val resolvedTarget = InstagramLinks.parse(resolved)
                if (resolvedTarget == null || resolvedTarget is InstagramLinks.Target.Share) {
                    throw InstagramException(str(R.string.ig_error_share_link))
                }
                fetchPost(resolved)
            }
            null -> throw InstagramException(str(R.string.ig_error_unsupported_link))
        }
    }

    // region Posts / reels

    private suspend fun fetchByShortcode(shortcode: String): InstagramPost {
        val mediaId = try {
            InstagramLinks.shortcodeToMediaId(shortcode)
        } catch (e: IllegalArgumentException) {
            throw InstagramException(str(R.string.ig_error_invalid_link))
        }

        if (isLoggedIn()) {
            val result = jsFetch("/api/v1/media/$mediaId/info/", headers = apiHeaders())
            val item = parseJson(result.body)?.optJSONArray("items")?.optJSONObject(0)
            if (result.status == 200 && item != null) {
                return parsePost(item, shortcode)
            }
            Log.w(TAG, "Logged-in media info failed status=${result.status}, trying logged-out")
        }

        val lsd = lsdToken ?: loadLsdToken()

        // Same order as the website: the ruling check also refreshes the csrftoken cookie
        val ruling = parseJson(
            jsFetch(
                "/api/v1/web/get_ruling_for_content/?content_type=MEDIA&target_id=$mediaId",
                headers = apiHeaders()
            ).body
        )

        val form = formEncode(
            "lsd" to (lsd ?: ""),
            "fb_api_caller_class" to "RelayModern",
            "fb_api_req_friendly_name" to POST_QUERY_NAME,
            "server_timestamps" to "true",
            "variables" to JSONObject().put("media_id", mediaId).toString(),
            "doc_id" to POST_QUERY_DOC_ID
        )
        val headers = apiHeaders() + buildMap {
            put("X-FB-Friendly-Name", POST_QUERY_NAME)
            put("Content-Type", "application/x-www-form-urlencoded")
            lsd?.let { put("X-FB-LSD", it) }
        }
        val result = jsFetch(
            "/api/graphql",
            method = "POST",
            headers = headers,
            body = form,
            referrer = "$BASE_URL/p/$shortcode/"
        )
        val product = parseJson(result.body)
            ?.optJSONObject("data")
            ?.optJSONObject("xig_polaris_media")
            ?.optJSONObject("if_not_gated_logged_out")
        if (product != null) {
            return parsePost(product, shortcode)
        }

        Log.w(TAG, "GraphQL returned no media status=${result.status} length=${result.body.length}")
        val rulingError = listOfNotNull(
            ruling?.optString("title")?.takeIf { it.isNotBlank() },
            ruling?.optString("description")?.takeIf { it.isNotBlank() }
        ).joinToString(": ")
        throw when {
            rulingError.isNotEmpty() -> InstagramException(rulingError, loginRequired = !isLoggedIn())
            isLoggedIn() -> InstagramException(str(R.string.ig_error_private_or_deleted))
            shortcode.length > 28 -> InstagramException(
                str(R.string.ig_error_private_login),
                loginRequired = true
            )
            else -> InstagramException(
                str(R.string.ig_error_not_returned),
                loginRequired = true
            )
        }
    }

    private suspend fun loadLsdToken(): String? {
        val html = jsFetch("/").body
        val token = Regex("""\["LSD",\[\],\{"token":"([^"]+)"""").find(html)?.groupValues?.get(1)
            ?: Regex("""id="__eqmc"[^>]*>(\{.*?\})</script>""").find(html)?.groupValues?.get(1)
                ?.let { parseJson(it)?.optString("l")?.takeIf { l -> l.isNotEmpty() } }
            ?: Regex(""""lsd":"([^"]+)"""").find(html)?.groupValues?.get(1)
        if (token == null) Log.w(TAG, "LSD token not found")
        lsdToken = token
        return token
    }

    // endregion

    // region Stories / highlights (login required)

    private suspend fun fetchStory(target: InstagramLinks.Target.Story): InstagramPost {
        requireLogin(str(R.string.ig_error_login_stories))
        val profile = jsFetch(
            "/api/v1/users/web_profile_info/?username=${urlEncode(target.username)}",
            headers = apiHeaders()
        )
        val userId = parseJson(profile.body)?.optJSONObject("data")?.optJSONObject("user")?.optString("id")
            ?.takeIf { it.isNotEmpty() }
            ?: throw InstagramException(str(R.string.ig_error_user_not_found, target.username))
        return fetchReel(userId, target.storyId, target.username)
    }

    /**
     * @param reelId user id for stories, "highlight:<id>" for highlights
     * @param storyId when set, only this story item is returned (falls back to all items)
     */
    private suspend fun fetchReel(reelId: String, storyId: String?, username: String?): InstagramPost {
        requireLogin(str(R.string.ig_error_login_stories_highlights))
        val reel = fetchReelJson(reelId)
        val items = reel?.optJSONArray("items")?.objects().orEmpty()
        if (items.isEmpty()) {
            throw InstagramException(str(R.string.ig_error_story_expired))
        }
        val selected = items.filter { item ->
            storyId != null && (item.optString("pk") == storyId || item.optString("id").startsWith(storyId))
        }.ifEmpty { items }
        val media = selected.flatMap { parseMedia(it) }
        if (media.isEmpty()) throw InstagramException(str(R.string.ig_error_story_no_media))
        return InstagramPost(
            code = storyId ?: reelId,
            username = username ?: reel?.optJSONObject("user")?.optString("username"),
            caption = null,
            media = media
        )
    }

    private suspend fun fetchReelJson(reelId: String): JSONObject? {
        val result = jsFetch("/api/v1/feed/reels_media/?reel_ids=${urlEncode(reelId)}", headers = apiHeaders())
        val json = parseJson(result.body)
        if (result.status != 200 || json == null) throw apiError(result, str(R.string.ig_error_load_stories_dot))
        return json?.optJSONObject("reels")?.optJSONObject(reelId)
            ?: json?.optJSONArray("reels_media")?.optJSONObject(0)
    }

    // endregion

    // region Stories browser (login required)

    /** Logged-in user's numeric id (from the ds_user_id cookie) */
    fun currentUserId(): String? = cookie("ds_user_id")

    /**
     * People you follow who currently have stories (the row of circles at the top of Instagram)
     */
    suspend fun fetchStoriesTray(): List<StoryTrayItem> = withContext(Dispatchers.IO) {
        requireLogin(str(R.string.ig_error_login_see_stories))
        val result = jsFetch("/api/v1/feed/reels_tray/", headers = apiHeaders())
        val tray = parseJson(result.body)?.optJSONArray("tray")
            ?: throw apiError(result, str(R.string.ig_error_load_stories_dot))
        val ownId = currentUserId()
        tray.objects().mapNotNull { reel ->
            val user = reel.optJSONObject("user")?.let(::parseUser) ?: return@mapNotNull null
            val latest = reel.optLong("latest_reel_media")
            StoryTrayItem(
                user = user,
                seen = latest > 0 && reel.optLong("seen") >= latest,
                isOwn = user.id == ownId
            )
        }.sortedWith(compareByDescending<StoryTrayItem> { it.isOwn }.thenBy { it.seen })
    }

    /** Current stories of a user, or the items of a highlight ("highlight:<id>") */
    suspend fun fetchStoryItems(reelId: String): List<StoryItem> = withContext(Dispatchers.IO) {
        requireLogin(str(R.string.ig_error_login_see_stories))
        val reel = fetchReelJson(reelId)
        reel?.optJSONArray("items")?.objects().orEmpty().mapNotNull { item ->
            val media = parseMedia(item).firstOrNull() ?: return@mapNotNull null
            StoryItem(
                id = item.optString("pk").ifEmpty { item.optString("id") },
                media = media,
                takenAt = item.optLong("taken_at")
            )
        }
    }

    suspend fun searchUsers(query: String): List<InstagramUser> = withContext(Dispatchers.IO) {
        requireLogin(str(R.string.ig_error_login_search))
        val q = urlEncode(query.trim().removePrefix("@"))
        val result = jsFetch("/web/search/topsearch/?context=blended&query=$q&include_reel=true", headers = apiHeaders())
        val users = parseJson(result.body)?.optJSONArray("users")
            ?: throw apiError(result, str(R.string.ig_error_search_failed_dot))
        users.objects().mapNotNull { it.optJSONObject("user")?.let(::parseUser) }
    }

    suspend fun fetchProfile(username: String): InstagramProfile = withContext(Dispatchers.IO) {
        requireLogin(str(R.string.ig_error_login_profiles))
        val result = jsFetch(
            "/api/v1/users/web_profile_info/?username=${urlEncode(username.removePrefix("@"))}",
            headers = apiHeaders()
        )
        val userJson = parseJson(result.body)?.optJSONObject("data")?.optJSONObject("user")
            ?: throw apiError(result, str(R.string.ig_error_profile_not_found, username))
        val user = parseUser(userJson) ?: throw InstagramException(str(R.string.ig_error_profile_not_found, username))
        InstagramProfile(
            user = user,
            hdProfilePicUrl = userJson.optString("profile_pic_url_hd").ifEmpty { null } ?: user.profilePicUrl,
            highlights = emptyList()
        )
    }

    /**
     * Full-size profile picture. Only called when the user taps "Save HD profile photo".
     */
    suspend fun fetchHdProfilePicture(userId: String): String? = withContext(Dispatchers.IO) {
        requireLogin(str(R.string.ig_error_login_profile_photos))
        val result = jsFetch("/api/v1/users/$userId/info/", headers = apiHeaders())
        val user = parseJson(result.body)?.optJSONObject("user") ?: throw apiError(result, str(R.string.ig_error_load_profile_photo))
        user.optJSONObject("hd_profile_pic_url_info")?.optString("url")?.ifEmpty { null }
            ?: user.optJSONArray("hd_profile_pic_versions")?.objects()
                ?.maxByOrNull { it.optInt("width") }?.optString("url")?.ifEmpty { null }
            ?: user.optString("profile_pic_url").ifEmpty { null }
    }

    /**
     * Resolves a typed username to an account. Uses search (cheap) and only falls back to the
     * heavily rate-limited profile endpoint when search has no exact match.
     */
    suspend fun findUser(username: String): InstagramUser = withContext(Dispatchers.IO) {
        val wanted = username.trim().removePrefix("@").lowercase()
        searchUsers(wanted).firstOrNull { it.username.lowercase() == wanted }
            ?: fetchProfile(wanted).user
    }

    /** Highlight albums; loaded only when the user opens the Highlights section */
    suspend fun fetchHighlights(userId: String): List<Highlight> = withContext(Dispatchers.IO) {
        requireLogin(str(R.string.ig_error_login_highlights))
        val result = jsFetch("/api/v1/highlights/$userId/highlights_tray/", headers = apiHeaders())
        if (result.status != 200) throw apiError(result, str(R.string.ig_error_load_highlights_dot))
        parseJson(result.body)?.optJSONArray("tray")?.objects().orEmpty().mapNotNull { h ->
            val id = h.optString("id").takeIf { it.startsWith("highlight:") } ?: return@mapNotNull null
            Highlight(
                id = id,
                title = h.optString("title"),
                coverUrl = h.optJSONObject("cover_media")?.optJSONObject("cropped_image_version")?.optString("url")
                    ?.ifEmpty { null }
            )
        }
    }

    private fun parseUser(json: JSONObject): InstagramUser? {
        val username = json.optString("username").ifEmpty { return null }
        return InstagramUser(
            id = json.optString("pk_id").ifEmpty { json.optString("pk") }.ifEmpty { json.optString("id") },
            username = username,
            fullName = json.optString("full_name").ifEmpty { null },
            profilePicUrl = json.optString("profile_pic_url").ifEmpty { null },
            isPrivate = json.optBoolean("is_private")
        )
    }

    /** Turns an unexpected API response into a user-facing error */
    private fun apiError(result: FetchResult, fallback: String): InstagramException {
        val message = parseJson(result.body)?.optString("message")?.takeIf { it.isNotBlank() }
        return when {
            result.status == 401 || result.status == 403 || parseJson(result.body)?.optBoolean("require_login") == true ->
                InstagramException(str(R.string.ig_error_session_expired), loginRequired = true)
            result.status == 429 || message?.contains("wait", ignoreCase = true) == true ->
                InstagramException(str(R.string.ig_error_slow_down))
            else -> InstagramException(message ?: fallback)
        }
    }

    private fun str(id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun requireLogin(message: String) {
        if (!isLoggedIn()) throw InstagramException(message, loginRequired = true)
    }

    // endregion

    // region Parsing

    private fun parsePost(item: JSONObject, fallbackCode: String): InstagramPost {
        val media = parseMedia(item)
        if (media.isEmpty()) throw InstagramException(str(R.string.ig_error_post_no_media))
        val caption = item.optJSONObject("caption")?.optString("text")
            ?: item.optJSONObject("edge_media_to_caption")?.optJSONArray("edges")
                ?.optJSONObject(0)?.optJSONObject("node")?.optString("text")
        val username = (item.optJSONObject("user") ?: item.optJSONObject("owner"))?.optString("username")
        return InstagramPost(
            code = item.optString("code").ifEmpty { item.optString("shortcode") }.ifEmpty { fallbackCode },
            username = username?.takeIf { it.isNotEmpty() },
            caption = caption?.takeIf { it.isNotBlank() },
            media = media
        )
    }

    /** Handles both the current API shape (video_versions/image_versions2) and the legacy GraphQL shape */
    private fun parseMedia(item: JSONObject): List<InstagramMedia> {
        item.optJSONArray("carousel_media")?.let { children ->
            return children.objects().flatMap { parseMedia(it) }
        }
        item.optJSONObject("edge_sidecar_to_children")?.optJSONArray("edges")?.let { edges ->
            return edges.objects().mapNotNull { it.optJSONObject("node") }.flatMap { parseMedia(it) }
        }

        val image = bestImageUrl(item) ?: item.optString("display_url").takeIf { it.isNotEmpty() }
        val video = bestVideoUrl(item) ?: item.optString("video_url").takeIf { it.isNotEmpty() }
        return when {
            video != null -> listOf(InstagramMedia(video, isVideo = true, thumbnailUrl = image))
            image != null -> listOf(InstagramMedia(image, isVideo = false, thumbnailUrl = image))
            else -> emptyList()
        }
    }

    private fun bestVideoUrl(item: JSONObject): String? =
        item.optJSONArray("video_versions")?.objects()
            ?.maxByOrNull { it.optInt("width") * it.optInt("height") }
            ?.optString("url")?.takeIf { it.isNotEmpty() }

    private fun bestImageUrl(item: JSONObject): String? =
        item.optJSONObject("image_versions2")?.optJSONArray("candidates")?.objects()
            ?.maxByOrNull { it.optInt("width") * it.optInt("height") }
            ?.optString("url")?.takeIf { it.isNotEmpty() }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

    private fun parseJson(body: String): JSONObject? =
        if (body.trimStart().startsWith("{")) {
            try {
                JSONObject(body)
            } catch (e: Exception) {
                null
            }
        } else null

    // endregion

    // region WebView transport

    private fun apiHeaders(): Map<String, String> = buildMap {
        put("X-IG-App-ID", APP_ID)
        put("X-ASBD-ID", ASBD_ID)
        put("X-IG-WWW-Claim", wwwClaim)
        put("X-Requested-With", "XMLHttpRequest")
        put("Accept", "*/*")
        csrfToken()?.let { put("X-CSRFToken", it) }
    }

    private fun csrfToken(): String? = cookie("csrftoken")

    private fun cookie(name: String): String? =
        CookieManager.getInstance().getCookie(BASE_URL)
            ?.split(";")
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("$name=") }
            ?.substringAfter("=")

    private fun formEncode(vararg pairs: Pair<String, String>): String =
        pairs.joinToString("&") { (k, v) -> "${urlEncode(k)}=${urlEncode(v)}" }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8")

    /**
     * Runs fetch() inside the hidden instagram.com page and returns status, final URL and body
     */
    private suspend fun jsFetch(
        path: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        referrer: String? = null
    ): FetchResult {
        if (path.startsWith("/api/") && System.currentTimeMillis() < blockedUntil) {
            throw rateLimitException()
        }
        val webView = ensureHost()
        throttle.withLock {
            val wait = lastRequestAt + MIN_REQUEST_GAP_MS - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            lastRequestAt = System.currentTimeMillis()
        }
        val id = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<FetchResult>()
        pending[id] = deferred

        val options = JSONObject().apply {
            put("method", method)
            put("headers", JSONObject(headers))
            put("credentials", "include")
            body?.let { put("body", it) }
            referrer?.let { put("referrer", it) }
        }
        val quotedId = JSONObject.quote(id)
        val script = """
            (function() {
              fetch(${JSONObject.quote(path)}, $options)
                .then(function(r) { return r.text().then(function(t) {
                  StatusHubBridge.onResult($quotedId, r.status, r.url, t, r.headers.get('x-ig-set-www-claim') || '');
                }); })
                .catch(function(e) { StatusHubBridge.onError($quotedId, String(e)); });
            })();
        """.trimIndent()

        withContext(Dispatchers.Main) { webView.evaluateJavascript(script, null) }
        val result = try {
            withTimeout(FETCH_TIMEOUT_MS) { deferred.await() }
        } catch (e: TimeoutCancellationException) {
            throw InstagramException(str(R.string.ig_error_timeout))
        } finally {
            pending.remove(id)
        }
        if (path.startsWith("/api/") && result.isRateLimited) {
            Log.w(TAG, "Rate limited on $path (status=${result.status}); pausing requests")
            blockedUntil = System.currentTimeMillis() + COOLDOWN_MS
            throw rateLimitException()
        }
        return result
    }

    private fun rateLimitException(): InstagramException {
        val minutes = ((blockedUntil - System.currentTimeMillis()) / 60_000 + 1).coerceAtLeast(1)
        return InstagramException(
            context.resources.getQuantityString(R.plurals.ig_error_rate_limited, minutes.toInt(), minutes.toInt())
        )
    }

    /** Creates (once) a hidden WebView sitting on an instagram.com page so fetch() is same-origin */
    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun ensureHost(): WebView = withContext(Dispatchers.Main) {
        val existing = hostWebView
        val existingReady = hostReady
        try {
            if (existing != null && existingReady != null) {
                withTimeout(HOST_TIMEOUT_MS) { existingReady.await() }
                existing
            } else {
                val ready = CompletableDeferred<Unit>()
                CookieManager.getInstance().setAcceptCookie(true)
                val webView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = browserUserAgent(settings.userAgentString)
                    addJavascriptInterface(Bridge(), "StatusHubBridge")
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) =
                            request.url.host?.endsWith("instagram.com") != true

                        override fun onPageFinished(view: WebView, url: String) {
                            ready.complete(Unit)
                        }
                    }
                }
                hostWebView = webView
                hostReady = ready
                // Lightweight same-origin page; avoids running Instagram's full web app
                webView.loadUrl("$BASE_URL/robots.txt")
                withTimeout(HOST_TIMEOUT_MS) { ready.await() }
                webView
            }
        } catch (e: TimeoutCancellationException) {
            hostWebView?.destroy()
            hostWebView = null
            hostReady = null
            throw InstagramException(str(R.string.ig_error_unreachable))
        }
    }

    private inner class Bridge {
        @JavascriptInterface
        fun onResult(id: String, status: Int, url: String, body: String, claim: String) {
            if (claim.isNotEmpty()) wwwClaim = claim
            pending.remove(id)?.complete(FetchResult(status, url, body))
        }

        @JavascriptInterface
        fun onError(id: String, message: String) {
            pending.remove(id)?.completeExceptionally(
                InstagramException(str(R.string.ig_error_network, message))
            )
        }
    }

    // endregion
}
