package com.hemadri.statushub.browser

import android.util.Log
import androidx.annotation.StringRes
import com.hemadri.statushub.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Downloads an HLS (.m3u8) stream to a single .mp4:
 * picks the best variant, downloads every segment (decrypting AES-128 if needed),
 * downloads the separate audio rendition when there is one, then remuxes to MP4.
 */
class HlsDownloader {

    companion object {
        private const val TAG = "HlsDownloader"
    }

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Info shown before downloading, e.g. "1080p" */
    data class StreamInfo(val bestHeight: Int?)

    internal data class Variant(val uri: String, val bandwidth: Long, val height: Int?, val audioGroup: String?)
    internal data class Segment(val uri: String, val byteRange: LongRange?, val key: Key?, val sequence: Long)
    internal data class Key(val uri: String, val iv: ByteArray?)
    internal data class MediaPlaylist(val initUri: String?, val segments: List<Segment>)

    /** Reads the master playlist to show the best available quality (cheap: one request) */
    suspend fun probe(url: String, headers: Map<String, String>): StreamInfo = withContext(Dispatchers.IO) {
        val text = fetchText(url, headers)
        StreamInfo(parseVariants(text, url).maxOfOrNull { it.height ?: 0 }?.takeIf { it > 0 })
    }

    suspend fun download(
        url: String,
        headers: Map<String, String>,
        workDir: File,
        outputName: String,
        onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        workDir.mkdirs()
        val masterText = fetchText(url, headers)
        val variants = parseVariants(masterText, url)
        val best = variants.maxWithOrNull(compareBy<Variant>({ it.height ?: 0 }, { it.bandwidth }))
        val mediaUrl = best?.uri ?: url
        val audioUrl = best?.audioGroup?.let { parseAudioRendition(masterText, url, it) }

        val video = fetchMedia(mediaUrl, headers)
        val audio = audioUrl?.let { fetchMedia(it, headers) }
        val totalSegments = video.segments.size + (audio?.segments?.size ?: 0)
        var done = 0
        val tick = { done++; onProgress((done * 95) / totalSegments.coerceAtLeast(1)) }

        val videoFile = File(workDir, "$outputName.video")
        writeStream(video, headers, videoFile, tick)
        val audioFile = audio?.let { File(workDir, "$outputName.audio").also { f -> writeStream(it, headers, f, tick) } }

        val output = File(workDir, "$outputName.mp4")
        val ok = MediaRemuxer.remux(videoFile, audioFile, output)
        audioFile?.delete()
        onProgress(100)
        if (ok) {
            videoFile.delete()
            output
        } else {
            // Couldn't convert (unusual codec): keep the raw stream so the download isn't lost
            val raw = File(workDir, "$outputName.${if (video.initUri != null) "mp4" else "ts"}")
            videoFile.renameTo(raw)
            raw
        }
    }

    private suspend fun writeStream(playlist: MediaPlaylist, headers: Map<String, String>, file: File, tick: () -> Unit) {
        val keys = mutableMapOf<String, ByteArray>()
        FileOutputStream(file).use { out ->
            playlist.initUri?.let { out.write(fetchBytes(it, headers, null)) }
            for (segment in playlist.segments) {
                kotlin.coroutines.coroutineContext.ensureActive()
                var bytes = fetchBytes(segment.uri, headers, segment.byteRange)
                segment.key?.let { key ->
                    val keyBytes = keys.getOrPut(key.uri) { fetchBytes(key.uri, headers, null) }
                    val iv = key.iv ?: ByteArray(16).also { iv ->
                        var seq = segment.sequence
                        for (i in 15 downTo 8) {
                            iv[i] = (seq and 0xFF).toByte()
                            seq = seq shr 8
                        }
                    }
                    bytes = Cipher.getInstance("AES/CBC/PKCS5Padding").run {
                        init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(iv))
                        doFinal(bytes)
                    }
                }
                out.write(bytes)
                tick()
            }
        }
    }

    // region Playlist parsing

    private fun attributes(line: String): Map<String, String> =
        Regex("""([A-Z0-9-]+)=("[^"]*"|[^,]*)""").findAll(line.substringAfter(':'))
            .associate { it.groupValues[1] to it.groupValues[2].trim('"') }

    private fun resolve(base: String, uri: String): String = URI(base).resolve(uri.trim()).toString()

    internal fun parseVariants(text: String, base: String): List<Variant> {
        val lines = text.lines().map { it.trim() }
        val result = mutableListOf<Variant>()
        lines.forEachIndexed { i, line ->
            if (!line.startsWith("#EXT-X-STREAM-INF")) return@forEachIndexed
            val attrs = attributes(line)
            val uri = lines.drop(i + 1).firstOrNull { it.isNotEmpty() && !it.startsWith("#") } ?: return@forEachIndexed
            result += Variant(
                uri = resolve(base, uri),
                bandwidth = attrs["BANDWIDTH"]?.toLongOrNull() ?: 0,
                height = attrs["RESOLUTION"]?.substringAfter('x')?.toIntOrNull(),
                audioGroup = attrs["AUDIO"]
            )
        }
        return result
    }

    internal fun parseAudioRendition(master: String, base: String, group: String): String? {
        val renditions = master.lines().map { it.trim() }
            .filter { it.startsWith("#EXT-X-MEDIA") }
            .map { attributes(it) }
            .filter { it["TYPE"] == "AUDIO" && it["GROUP-ID"] == group && it["URI"] != null }
        val chosen = renditions.firstOrNull { it["DEFAULT"] == "YES" } ?: renditions.firstOrNull()
        return chosen?.get("URI")?.let { resolve(base, it) }
    }

    private fun fetchMedia(url: String, headers: Map<String, String>): MediaPlaylist =
        parseMediaPlaylist(fetchText(url, headers), url)

    internal fun parseMediaPlaylist(text: String, url: String): MediaPlaylist {
        var initUri: String? = null
        var key: Key? = null
        var sequence = 0L
        var pendingRange: LongRange? = null
        var lastRangeEnd = 0L
        val segments = mutableListOf<Segment>()
        for (raw in text.lines()) {
            val line = raw.trim()
            when {
                line.startsWith("#EXT-X-MEDIA-SEQUENCE") -> sequence = line.substringAfter(':').toLongOrNull() ?: 0
                line.startsWith("#EXT-X-MAP") -> initUri = attributes(line)["URI"]?.let { resolve(url, it) }
                line.startsWith("#EXT-X-KEY") -> {
                    val attrs = attributes(line)
                    key = when (attrs["METHOD"]) {
                        "AES-128" -> Key(
                            uri = resolve(url, attrs["URI"] ?: throw IOException("Encrypted stream without key")),
                            iv = attrs["IV"]?.removePrefix("0x")?.removePrefix("0X")?.chunked(2)
                                ?.map { it.toInt(16).toByte() }?.toByteArray()
                        )
                        "NONE", null -> null
                        else -> throw HlsException(R.string.browser_error_drm, "This video is DRM-protected and can't be downloaded.")
                    }
                }
                line.startsWith("#EXT-X-BYTERANGE") -> {
                    val spec = line.substringAfter(':')
                    val length = spec.substringBefore('@').toLong()
                    val start = spec.substringAfter('@', "").toLongOrNull() ?: lastRangeEnd
                    pendingRange = start until start + length
                    lastRangeEnd = start + length
                }
                line.isNotEmpty() && !line.startsWith("#") -> {
                    segments += Segment(resolve(url, line), pendingRange, key, sequence++)
                    pendingRange = null
                }
            }
        }
        if (segments.isEmpty()) throw HlsException(R.string.browser_error_no_parts, "This stream has no downloadable parts.")
        return MediaPlaylist(initUri, segments)
    }

    // endregion

    private fun request(url: String, headers: Map<String, String>, range: LongRange?) =
        Request.Builder().url(url).apply {
            headers.forEach { (k, v) -> addHeader(k, v) }
            range?.let { addHeader("Range", "bytes=${it.first}-${it.last}") }
        }.build()

    private fun fetchText(url: String, headers: Map<String, String>): String =
        client.newCall(request(url, headers, null)).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Stream request failed (${response.code})")
            response.body?.string() ?: throw IOException("Empty playlist")
        }

    private fun fetchBytes(url: String, headers: Map<String, String>, range: LongRange?): ByteArray {
        var attempt = 0
        while (true) {
            try {
                return client.newCall(request(url, headers, range)).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Segment request failed (${response.code})")
                    response.body?.bytes() ?: throw IOException("Empty segment")
                }
            } catch (e: IOException) {
                if (++attempt >= 3) throw e
                Log.w(TAG, "Retrying segment ($attempt): ${e.message}")
            }
        }
    }
}

/** A download failure the user can act on; [messageRes] is shown instead of the English message */
class HlsException(@StringRes val messageRes: Int, message: String) : IOException(message)
