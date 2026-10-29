package com.hemadri.statushub.browser

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

/**
 * Rewrites MPEG-TS / fragmented-MP4 stream downloads into one regular .mp4 using Android's built-in
 * MediaExtractor + MediaMuxer (no ffmpeg). Can take video from one file and audio from another.
 */
object MediaRemuxer {

    private const val TAG = "MediaRemuxer"

    /**
     * @param video file containing the video track (and audio, if [audio] is null)
     * @param audio optional separate audio file (HLS streams with an audio rendition)
     * @return true on success
     */
    fun remux(video: File, audio: File?, output: File): Boolean {
        val extractors = mutableListOf<MediaExtractor>()
        var muxer: MediaMuxer? = null
        return try {
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            // (extractor, source track, muxer track)
            val tracks = mutableListOf<Triple<MediaExtractor, Int, Int>>()

            fun addTracks(file: File, wanted: (String) -> Boolean) {
                val extractor = MediaExtractor().apply { setDataSource(file.absolutePath) }
                extractors += extractor
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                    if (!wanted(mime)) continue
                    tracks += Triple(extractor, i, muxer.addTrack(format))
                }
            }

            if (audio == null) {
                addTracks(video) { it.startsWith("video/") || it.startsWith("audio/") }
            } else {
                addTracks(video) { it.startsWith("video/") }
                addTracks(audio) { it.startsWith("audio/") }
            }
            if (tracks.none { it.first.getTrackFormat(it.second).getString(MediaFormat.KEY_MIME)!!.startsWith("video/") }) {
                Log.w(TAG, "No video track found")
                return false
            }

            muxer.start()
            val buffer = ByteBuffer.allocate(4 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()
            for ((extractor, sourceTrack, muxerTrack) in tracks) {
                extractor.selectTrack(sourceTrack)
                extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                while (true) {
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break
                    info.offset = 0
                    info.size = size
                    info.presentationTimeUs = extractor.sampleTime
                    info.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                        MediaCodec.BUFFER_FLAG_KEY_FRAME
                    } else 0
                    muxer.writeSampleData(muxerTrack, buffer, info)
                    extractor.advance()
                }
                extractor.unselectTrack(sourceTrack)
            }
            muxer.stop()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Remux failed: ${e.message}", e)
            output.delete()
            false
        } finally {
            extractors.forEach { it.release() }
            try {
                muxer?.release()
            } catch (_: Exception) {
            }
        }
    }
}
