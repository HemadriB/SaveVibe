package com.statushub.utils

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

/**
 * Splits a video into multiple clips of a given duration (default 30s for WhatsApp status).
 * Uses Android's built-in MediaExtractor + MediaMuxer — no ffmpeg needed.
 */
object VideoSplitter {

    private const val TAG = "VideoSplitter"
    const val DEFAULT_CLIP_DURATION_S = 30

    data class SplitResult(
        val clips: List<File>,
        val durationMs: Long,
        val clipCount: Int
    )

    /** Returns the video duration in milliseconds, or -1 on error */
    fun getVideoDurationMs(context: Context, uri: Uri): Long {
        return try {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, uri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: -1L
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get duration: ${e.message}")
            -1L
        }
    }

    /**
     * Splits the video at [uri] into clips of [clipDurationS] seconds each.
     * The last clip may be shorter. All clips are saved in [outputDir].
     *
     * @param onProgress called with (clipIndex, totalClips) as each clip finishes
     * @return SplitResult with the list of clip files, or null on failure
     */
    fun split(
        context: Context,
        uri: Uri,
        clipDurationS: Int = DEFAULT_CLIP_DURATION_S,
        outputDir: File,
        baseName: String = "clip",
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): SplitResult? {
        val durationMs = getVideoDurationMs(context, uri)
        if (durationMs <= 0) {
            Log.e(TAG, "Invalid video duration")
            return null
        }

        val clipDurationUs = clipDurationS * 1_000_000L
        val totalClips = ((durationMs * 1000L + clipDurationUs - 1) / clipDurationUs).toInt()
            .coerceAtLeast(1)

        if (!outputDir.exists()) outputDir.mkdirs()

        val clips = mutableListOf<File>()

        try {
            for (clipIndex in 0 until totalClips) {
                val startUs = clipIndex * clipDurationUs
                val endUs = minOf(startUs + clipDurationUs, durationMs * 1000L)
                val outputFile = File(outputDir, "${baseName}_part${clipIndex + 1}.mp4")

                val success = extractClip(context, uri, startUs, endUs, outputFile)
                if (success) {
                    clips.add(outputFile)
                } else {
                    Log.w(TAG, "Failed to extract clip ${clipIndex + 1}")
                }
                onProgress?.invoke(clipIndex + 1, totalClips)
            }

            return if (clips.isNotEmpty()) {
                SplitResult(clips, durationMs, clips.size)
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Split failed: ${e.message}", e)
            clips.forEach { it.delete() }
            return null
        }
    }

    private fun extractClip(
        context: Context,
        uri: Uri,
        startUs: Long,
        endUs: Long,
        output: File
    ): Boolean {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        return try {
            extractor.setDataSource(context, uri, null)
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            // Add all tracks (video + audio)
            val trackMap = mutableMapOf<Int, Int>() // extractor track -> muxer track
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    trackMap[i] = muxer.addTrack(format)
                }
            }
            if (trackMap.isEmpty()) {
                Log.w(TAG, "No video/audio tracks found")
                return false
            }

            muxer.start()
            val buffer = ByteBuffer.allocate(2 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()

            for ((extractorTrack, muxerTrack) in trackMap) {
                extractor.selectTrack(extractorTrack)
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

                while (true) {
                    val sampleTime = extractor.sampleTime
                    if (sampleTime < 0 || sampleTime >= endUs) break

                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break

                    info.offset = 0
                    info.size = size
                    info.presentationTimeUs = sampleTime - startUs
                    info.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                        MediaCodec.BUFFER_FLAG_KEY_FRAME
                    } else 0

                    muxer.writeSampleData(muxerTrack, buffer, info)
                    extractor.advance()
                }

                extractor.unselectTrack(extractorTrack)
            }

            muxer.stop()
            true
        } catch (e: Exception) {
            Log.e(TAG, "extractClip failed: ${e.message}", e)
            output.delete()
            false
        } finally {
            extractor.release()
            try { muxer?.release() } catch (_: Exception) {}
        }
    }
}
