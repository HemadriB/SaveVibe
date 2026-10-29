package com.hemadri.statushub.utils

import com.hemadri.statushub.R
import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Downloads media files over HTTP into the app's private Download folder.
 * Used for direct video links and for Instagram media URLs resolved by InstagramClient.
 * YouTube is intentionally not supported (YouTube Terms of Service / Google Play policy).
 */
class VideoDownloader(private val context: Context) {

    companion object {
        private const val TAG = "VideoDownload"
        private val VIDEO_EXTENSIONS = listOf(".mp4", ".webm", ".mkv", ".mov", ".avi", ".flv", ".m4v", ".3gp")
    }

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val browserUserAgent = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36"

    /**
     * Downloads a direct video link (.mp4, .webm, ...)
     * @return the downloaded file
     */
    suspend fun downloadDirectVideo(url: String, onProgress: (Int) -> Unit): File {
        val fileName = getFileNameFromUrl(url) ?: "video_${System.currentTimeMillis()}.mp4"
        return downloadFile(url, fileName, referer = null, onProgress = onProgress)
    }

    /**
     * Downloads [url] to the app's private Download folder as [fileName].
     * @param onProgress 0-100, only reported when the server sends a content length
     */
    suspend fun downloadFile(
        url: String,
        fileName: String,
        referer: String? = null,
        headers: Map<String, String> = emptyMap(),
        onProgress: (Int) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        Log.d(TAG, "downloadFile start name=$fileName")
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", headers["User-Agent"] ?: browserUserAgent)
            .apply {
                referer?.let { addHeader("Referer", it) }
                headers.filterKeys { it != "User-Agent" }.forEach { (k, v) -> addHeader(k, v) }
            }
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Failed to download: ${response.code} ${response.message}")
            }
            val body = response.body ?: throw IOException("Response body is null")
            val contentType = response.header("Content-Type")?.lowercase() ?: ""
            if (contentType.contains("text/html")) {
                throw IOException(context.getString(R.string.download_error_web_page))
            }

            val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            val file = File(downloadsDir, fileName)
            val contentLength = body.contentLength()

            body.byteStream().use { input ->
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(8192)
                    var totalBytesRead = 0L
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        if (contentLength > 0) {
                            onProgress(((totalBytesRead * 100) / contentLength).toInt())
                        }
                    }
                }
            }
            onProgress(100)
            Log.d(TAG, "downloadFile success path=${file.absolutePath}")
            file
        }
    }

    fun isYouTubeUrl(url: String): Boolean {
        val host = try { URL(url).host.lowercase() } catch (e: Exception) { url.lowercase() }
        return host.contains("youtube.com") || host.contains("youtu.be") || host.contains("youtube-nocookie.com")
    }

    fun isDirectVideoUrl(url: String): Boolean {
        val path = try { URL(url).path.lowercase() } catch (e: Exception) { url.lowercase() }
        return VIDEO_EXTENSIONS.any { path.endsWith(it) || path.contains("$it?") }
    }

    /**
     * Extracts file name from URL
     */
    private fun getFileNameFromUrl(url: String): String? {
        return try {
            val fileName = URL(url).path.substringAfterLast("/")
            if (fileName.isNotEmpty() && fileName.contains(".")) fileName else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Validates if the URL is a valid URL that may contain a video
     */
    fun isValidVideoUrl(url: String): Boolean {
        return url.isNotEmpty() && (url.startsWith("http://") || url.startsWith("https://"))
    }
}
