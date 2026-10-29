package com.hemadri.statushub.browser

import android.net.Uri

/**
 * A video found on the current page
 */
data class DetectedVideo(
    val url: String,
    val kind: Kind,
    val pageUrl: String,
    val pageTitle: String?,
    val thumbnailUrl: String?,
    /** Bytes, when the server reports it (MP4) */
    val sizeBytes: Long? = null,
    /** e.g. "1080p" for streams */
    val quality: String? = null
) {
    enum class Kind { MP4, HLS }
}

/**
 * Decides which URLs seen on a page are downloadable videos, and which sites are off-limits.
 */
object VideoDetector {

    /** YouTube is never supported (YouTube Terms of Service / Google Play policy) */
    private val BLOCKED_HOSTS = listOf("youtube.com", "youtu.be", "youtube-nocookie.com", "googlevideo.com")

    private val MP4_EXTENSIONS = listOf(".mp4", ".webm", ".m4v", ".mov", ".3gp")
    /** Parts of streams we can't download on their own */
    private val SEGMENT_EXTENSIONS = listOf(".m4s", ".ts", ".aac", ".m4a", ".mpd", ".vtt", ".key")
    /** Range parameters some CDNs (e.g. Facebook) add to fetch a file in chunks; removing them gives the whole file */
    private val RANGE_PARAMS = setOf("bytestart", "byteend", "range")

    fun isBlocked(url: String): Boolean {
        val host = hostOf(url) ?: return false
        return BLOCKED_HOSTS.any { host == it || host.endsWith(".$it") }
    }

    private fun hostOf(url: String): String? = try {
        Uri.parse(url).host?.lowercase()
    } catch (e: Exception) {
        null
    }

    /** Cleans escaped URLs taken from page source (e.g. https:\/\/...&...) */
    fun unescape(raw: String): String =
        raw.replace("\\/", "/")
            .replace("\\u0026", "&")
            .replace("\\u003d", "=")
            .replace("\\u002F", "/")
            .replace("&amp;", "&")
            .trim()

    /**
     * @return the cleaned URL and its kind, or null if it isn't a downloadable video
     */
    fun classify(rawUrl: String): Pair<String, DetectedVideo.Kind>? {
        val url = unescape(rawUrl)
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null
        if (isBlocked(url)) return null
        val uri = try {
            Uri.parse(url)
        } catch (e: Exception) {
            return null
        }
        val path = (uri.path ?: "").lowercase()
        val query = (uri.query ?: "").lowercase()

        if (path.endsWith(".m3u8") || query.contains("format=m3u8")) return url to DetectedVideo.Kind.HLS
        if (SEGMENT_EXTENSIONS.any { path.endsWith(it) }) return null

        val isMp4 = MP4_EXTENSIONS.any { path.endsWith(it) } ||
            query.contains("mime_type=video_mp4") ||
            query.contains("mime=video%2fmp4") ||
            query.contains("mime=video/mp4")
        if (!isMp4) return null

        val cleaned = if (uri.queryParameterNames.any { it.lowercase() in RANGE_PARAMS }) {
            uri.buildUpon().clearQuery().apply {
                uri.queryParameterNames.filter { it.lowercase() !in RANGE_PARAMS }.forEach { name ->
                    uri.getQueryParameters(name).forEach { appendQueryParameter(name, it) }
                }
            }.build().toString()
        } else url
        return cleaned to DetectedVideo.Kind.MP4
    }

    /**
     * Runs inside the page: collects <video> sources, og:video tags and the video URLs that popular
     * sites embed in their page data. Returns JSON {title, thumb, urls[]}.
     */
    val PAGE_SCAN_SCRIPT = """
        (function() {
          var out = [];
          function add(u) { if (u && typeof u === 'string' && u.indexOf('blob:') !== 0 && u.length < 4000) out.push(u); }
          document.querySelectorAll('video').forEach(function(v) {
            add(v.currentSrc); add(v.src);
            v.querySelectorAll('source').forEach(function(s) { add(s.src); });
          });
          ['og:video', 'og:video:url', 'og:video:secure_url', 'twitter:player:stream'].forEach(function(p) {
            var m = document.querySelector('meta[property="' + p + '"],meta[name="' + p + '"]');
            if (m) add(m.content);
          });
          var html = document.documentElement.innerHTML;
          ['browser_native_hd_url', 'browser_native_sd_url', 'playable_url_quality_hd', 'playable_url',
           'hd_src', 'sd_src', 'playAddr', 'downloadAddr', 'contentUrl', 'video_url'].forEach(function(k) {
            var re = new RegExp('"' + k + '"\\s*:\\s*"([^"]+)"', 'g'); var m; var n = 0;
            while ((m = re.exec(html)) && n < 10) { add(m[1]); n++; }
          });
          var re2 = /https?:\\?\/\\?\/[^"'\s<>]+?\.(mp4|m3u8)[^"'\s<>]*/g; var m2; var c = 0;
          while ((m2 = re2.exec(html)) && c < 30) { add(m2[0]); c++; }
          var og = document.querySelector('meta[property="og:image"]');
          var poster = (document.querySelector('video[poster]') || {}).poster;
          return JSON.stringify({ title: document.title, thumb: poster || (og && og.content) || null, urls: out });
        })();
    """.trimIndent()
}
