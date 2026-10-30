package com.statushub.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.statushub.utils.GallerySaver
import com.statushub.utils.VideoSplitter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class SplitterState(
    val videoUri: Uri? = null,
    val durationMs: Long = 0L,
    val clipDurationS: Int = VideoSplitter.DEFAULT_CLIP_DURATION_S,
    val isSplitting: Boolean = false,
    val progress: Int = 0,
    val totalClips: Int = 0,
    val result: List<ClipItem>? = null,
    val error: String? = null
)

data class ClipItem(
    val file: File,
    val galleryUri: Uri? = null,
    val index: Int,
    val saved: Boolean = false
)

class VideoSplitterViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(SplitterState())
    val state: StateFlow<SplitterState> = _state.asStateFlow()

    fun setVideo(uri: Uri) {
        val context = getApplication<Application>()
        val durationMs = VideoSplitter.getVideoDurationMs(context, uri)
        _state.value = SplitterState(
            videoUri = uri,
            durationMs = durationMs,
            clipDurationS = VideoSplitter.DEFAULT_CLIP_DURATION_S,
            totalClips = if (durationMs > 0) {
                ((durationMs + (VideoSplitter.DEFAULT_CLIP_DURATION_S * 1000L) - 1) /
                        (VideoSplitter.DEFAULT_CLIP_DURATION_S * 1000L)).toInt().coerceAtLeast(1)
            } else 0
        )
    }

    fun setClipDuration(seconds: Int) {
        val s = _state.value
        if (s.durationMs <= 0) return
        val clamped = seconds.coerceIn(5, 60)
        _state.value = s.copy(
            clipDurationS = clamped,
            totalClips = ((s.durationMs + (clamped * 1000L) - 1) / (clamped * 1000L))
                .toInt().coerceAtLeast(1)
        )
    }

    fun split() {
        val s = _state.value
        val uri = s.videoUri ?: return
        if (s.isSplitting) return

        _state.value = s.copy(isSplitting = true, progress = 0, result = null, error = null)

        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val outputDir = File(context.cacheDir, "splits")
            outputDir.listFiles()?.forEach { it.delete() }

            val result = VideoSplitter.split(
                context = context,
                uri = uri,
                clipDurationS = s.clipDurationS,
                outputDir = outputDir,
                baseName = "status_clip",
                onProgress = { current, total ->
                    _state.value = _state.value.copy(progress = current, totalClips = total)
                }
            )

            if (result != null) {
                _state.value = _state.value.copy(
                    isSplitting = false,
                    result = result.clips.mapIndexed { i, file -> ClipItem(file, null, i) }
                )
            } else {
                _state.value = _state.value.copy(
                    isSplitting = false,
                    error = "Failed to split video. Try a different file."
                )
            }
        }
    }

    fun saveClipToGallery(clipItem: ClipItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val galleryUri = GallerySaver.saveToGallery(context, clipItem.file)
            if (galleryUri != null) {
                val current = _state.value.result ?: return@launch
                _state.value = _state.value.copy(
                    result = current.map {
                        if (it.index == clipItem.index) it.copy(galleryUri = galleryUri, saved = true) else it
                    }
                )
            }
        }
    }

    fun saveAllClips() {
        val clips = _state.value.result ?: return
        clips.filter { !it.saved }.forEach { saveClipToGallery(it) }
    }

    fun reset() {
        val context = getApplication<Application>()
        File(context.cacheDir, "splits").listFiles()?.forEach { it.delete() }
        _state.value = SplitterState()
    }
}
