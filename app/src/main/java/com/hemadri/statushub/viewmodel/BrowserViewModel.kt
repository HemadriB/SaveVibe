package com.hemadri.statushub.viewmodel

import android.app.Application
import android.util.Log
import android.webkit.CookieManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hemadri.statushub.browser.DetectedVideo
import com.hemadri.statushub.browser.HlsDownloader
import com.hemadri.statushub.browser.HlsException
import com.hemadri.statushub.R
import com.hemadri.statushub.browser.VideoDetector
import com.hemadri.statushub.data.DownloadedItem
import com.hemadri.statushub.utils.GallerySaver
import com.hemadri.statushub.utils.VideoDownloader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Download state of one detected video */
data class BrowserDownload(
    val progress: Int = 0,
    val isRunning: Boolean = false,
    val result: DownloadedItem? = null,
    val error: String? = null
)

/**
 * In-app browser that finds downloadable videos on the pages the user visits
 */
class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "BrowserVM"
        /** Smaller files are usually stream init parts, previews or ads */
        private const val MIN_VIDEO_BYTES = 250_000L
        private const val MAX_DETECTED = 25
    }

    private val videoDownloader = VideoDownloader(application)
    private val hlsDownloader = HlsDownloader()
    private val probeClient = OkHttpClient.Builder()
        .followRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Text in the address bar */
    private val _addressText = MutableStateFlow("")
    val addressText: StateFlow<String> = _addressText.asStateFlow()

    /** Page to show; null = browser home (shortcuts) */
    private val _currentUrl = MutableStateFlow<String?>(null)
    val currentUrl: StateFlow<String?> = _currentUrl.asStateFlow()

    /** Incremented to ask the WebView to load [currentUrl] (typed / shortcut / shared link) */
    private val _loadRequest = MutableStateFlow(0)
    val loadRequest: StateFlow<Int> = _loadRequest.asStateFlow()

    private val _pageProgress = MutableStateFlow(0)
    val pageProgress: StateFlow<Int> = _pageProgress.asStateFlow()

    private val _detected = MutableStateFlow<List<DetectedVideo>>(emptyList())
    val detected: StateFlow<List<DetectedVideo>> = _detected.asStateFlow()

    private val _downloads = MutableStateFlow<Map<String, BrowserDownload>>(emptyMap())
    val downloads: StateFlow<Map<String, BrowserDownload>> = _downloads.asStateFlow()

    private val _blockedMessage = MutableStateFlow<String?>(null)
    val blockedMessage: StateFlow<String?> = _blockedMessage.asStateFlow()

    /** URLs already handled (accepted or rejected), so the same request isn't probed twice */
    private val seen = ConcurrentHashMap.newKeySet<String>()
    private val jobs = ConcurrentHashMap<String, Job>()

    @Volatile private var pageTitle: String? = null
    @Volatile private var pageThumbnail: String? = null
    @Volatile var userAgent: String? = null

    fun updateAddress(text: String) {
        _addressText.value = text
    }

    /** Opens a typed address, a search query or a shared link */
    fun open(input: String) {
        val text = input.trim()
        if (text.isEmpty()) return
        val url = when {
            text.startsWith("http://") || text.startsWith("https://") -> text
            Regex("""^[\w-]+(\.[\w-]+)+(/.*)?$""").matches(text) -> "https://$text"
            else -> "https://www.google.com/search?q=" + java.net.URLEncoder.encode(text, "UTF-8")
        }
        if (VideoDetector.isBlocked(url)) {
            _blockedMessage.value = getApplication<Application>().getString(R.string.browser_youtube_blocked)
            return
        }
        _detected.value = emptyList()
        seen.clear()
        _addressText.value = url
        _currentUrl.value = url
        _loadRequest.value++
    }

    fun goHome() {
        _currentUrl.value = null
        _addressText.value = ""
        _detected.value = emptyList()
        seen.clear()
    }

    fun clearBlockedMessage() {
        _blockedMessage.value = null
    }

    fun onBlockedNavigation() {
        _blockedMessage.value = getApplication<Application>().getString(R.string.browser_youtube_blocked)
    }

    // region Page events (called from WebView callbacks)

    fun onPageStarted(url: String) {
        _currentUrl.value = url
        _addressText.value = url
    }

    fun onProgress(progress: Int) {
        _pageProgress.value = progress
    }

    /** Result of [VideoDetector.PAGE_SCAN_SCRIPT] (a JSON string literal) */
    fun onPageScan(rawResult: String?, pageUrl: String) {
        if (rawResult.isNullOrEmpty() || rawResult == "null") return
        try {
            val json = JSONObject(JSONArray("[$rawResult]").getString(0))
            pageTitle = json.optString("title").ifBlank { null }
            pageThumbnail = json.optString("thumb").takeIf { it.isNotBlank() && it != "null" }
            val urls = json.optJSONArray("urls") ?: return
            for (i in 0 until urls.length()) onCandidate(urls.optString(i), pageUrl)
        } catch (e: Exception) {
            Log.w(TAG, "Page scan parse failed: ${e.message}")
        }
    }

    /** A URL the page requested or embeds; may be called from WebView background threads */
    fun onCandidate(rawUrl: String, pageUrl: String) {
        val (url, kind) = VideoDetector.classify(rawUrl) ?: return
        if (!seen.add(url)) return
        viewModelScope.launch {
            val headers = headersFor(url, pageUrl)
            val video = try {
                when (kind) {
                    DetectedVideo.Kind.MP4 -> {
                        val size = try {
                            withContext(Dispatchers.IO) { probeSize(url, headers) }
                        } catch (e: IllegalStateException) {
                            return@launch // the URL serves a web page, not a video
                        } catch (e: Exception) {
                            null // size unknown; still offer the download
                        }
                        if (size != null && size < MIN_VIDEO_BYTES) return@launch
                        DetectedVideo(url, kind, pageUrl, pageTitle, pageThumbnail, sizeBytes = size)
                    }
                    DetectedVideo.Kind.HLS -> {
                        val info = hlsDownloader.probe(url, headers)
                        DetectedVideo(url, kind, pageUrl, pageTitle, pageThumbnail, quality = info.bestHeight?.let { "${it}p" })
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Probe failed for $url: ${e.message}")
                return@launch
            }
            _detected.update { list ->
                // A master playlist makes its own variant playlists redundant
                (listOf(video) + list).distinctBy { it.url }.take(MAX_DETECTED)
            }
        }
    }

    // endregion

    // region Downloads

    fun download(video: DetectedVideo) {
        val key = video.url
        if (_downloads.value[key]?.isRunning == true) return
        setDownload(key) { BrowserDownload(isRunning = true) }
        jobs[key] = viewModelScope.launch {
            try {
                val headers = headersFor(video.url, video.pageUrl)
                val baseName = fileBaseName(video)
                val file = when (video.kind) {
                    DetectedVideo.Kind.MP4 -> videoDownloader.downloadFile(
                        url = video.url,
                        fileName = "$baseName.${extensionOf(video.url)}",
                        referer = video.pageUrl,
                        headers = headers
                    ) { p -> setDownload(key) { it.copy(progress = p) } }
                    DetectedVideo.Kind.HLS -> hlsDownloader.download(
                        url = video.url,
                        headers = headers,
                        workDir = File(getApplication<Application>().cacheDir, "streams"),
                        outputName = baseName
                    ) { p -> setDownload(key) { it.copy(progress = p) } }
                }
                val saved = withContext(Dispatchers.IO) { GallerySaver.moveToGallery(getApplication(), file) }
                setDownload(key) { BrowserDownload(progress = 100, result = saved) }
            } catch (e: CancellationException) {
                setDownload(key) { BrowserDownload() }
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                val app = getApplication<Application>()
                val message = (e as? HlsException)?.let { app.getString(it.messageRes) }
                    ?: e.message ?: app.getString(R.string.browser_download_failed)
                setDownload(key) { BrowserDownload(error = message) }
            } finally {
                jobs.remove(key)
            }
        }
    }

    fun cancel(video: DetectedVideo) {
        jobs[video.url]?.cancel()
    }

    private fun setDownload(key: String, change: (BrowserDownload) -> BrowserDownload) {
        _downloads.update { it + (key to change(it[key] ?: BrowserDownload())) }
    }

    // endregion

    /** Same cookies, user agent and referer as the page, so CDNs accept the request */
    private fun headersFor(url: String, pageUrl: String): Map<String, String> = buildMap {
        userAgent?.let { put("User-Agent", it) }
        put("Referer", pageUrl)
        CookieManager.getInstance().getCookie(url)?.let { put("Cookie", it) }
    }

    private fun probeSize(url: String, headers: Map<String, String>): Long? {
        fun build(method: String) = Request.Builder().url(url).apply {
            headers.forEach { (k, v) -> addHeader(k, v) }
            if (method == "HEAD") head() else addHeader("Range", "bytes=0-0")
        }.build()
        probeClient.newCall(build("HEAD")).execute().use { response ->
            if (response.isSuccessful) {
                if (response.header("Content-Type")?.contains("text/html") == true) throw IllegalStateException("Not a video")
                response.header("Content-Length")?.toLongOrNull()?.let { return it }
            }
        }
        probeClient.newCall(build("GET")).execute().use { response ->
            return response.header("Content-Range")?.substringAfter('/')?.toLongOrNull()
                ?: response.header("Content-Length")?.toLongOrNull()?.takeIf { response.code == 200 }
        }
    }

    private fun fileBaseName(video: DetectedVideo): String {
        val title = (video.pageTitle ?: "video")
            .replace(Regex("""[^\p{L}\p{N}]+"""), "_")
            .trim('_')
            .take(40)
            .ifEmpty { "video" }
        return "${title}_${System.currentTimeMillis() % 100000}"
    }

    private fun extensionOf(url: String): String {
        val ext = url.substringBefore('?').substringAfterLast('.', "").lowercase()
        return if (ext in listOf("mp4", "webm", "m4v", "mov", "3gp")) ext else "mp4"
    }
}
