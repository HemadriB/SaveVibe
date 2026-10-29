package com.hemadri.statushub.viewmodel

import com.hemadri.statushub.R
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hemadri.statushub.data.DownloadedItem
import com.hemadri.statushub.instagram.InstagramClient
import com.hemadri.statushub.instagram.InstagramException
import com.hemadri.statushub.instagram.InstagramLinks
import com.hemadri.statushub.instagram.InstagramMedia
import com.hemadri.statushub.instagram.InstagramPost
import com.hemadri.statushub.instagram.StoryTrayItem
import com.hemadri.statushub.utils.GallerySaver
import com.hemadri.statushub.utils.VideoDownloader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class DownloadState(
    /** Resolving an Instagram link to media URLs */
    val isFetching: Boolean = false,
    val isDownloading: Boolean = false,
    /** Overall progress across all items, 0-100 */
    val progress: Int = 0,
    /** 1-based index of the item currently downloading, with [totalItems] */
    val currentItem: Int = 0,
    val totalItems: Int = 0,
    /** Instagram post being downloaded (username, caption, thumbnails) */
    val post: InstagramPost? = null,
    val downloaded: List<DownloadedItem> = emptyList(),
    val error: String? = null,
    /** True when logging in to Instagram would likely fix [error] */
    val loginSuggested: Boolean = false
) {
    val isBusy: Boolean get() = isFetching || isDownloading
}

class VideoDownloadViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "VideoDownload"
    }

    private val videoDownloader = VideoDownloader(application)
    private val instagramClient = InstagramClient.get(application)
    private var downloadJob: Job? = null

    private val _downloadState = MutableStateFlow(DownloadState())
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    private val _isInstagramLoggedIn = MutableStateFlow(InstagramClient.isLoggedIn())
    val isInstagramLoggedIn: StateFlow<Boolean> = _isInstagramLoggedIn.asStateFlow()

    /** Stories row (people you follow with active stories); only loaded while logged in */
    private val _storiesTray = MutableStateFlow<List<StoryTrayItem>>(emptyList())
    val storiesTray: StateFlow<List<StoryTrayItem>> = _storiesTray.asStateFlow()

    private val _isTrayLoading = MutableStateFlow(false)
    val isTrayLoading: StateFlow<Boolean> = _isTrayLoading.asStateFlow()

    private val _trayError = MutableStateFlow<String?>(null)
    val trayError: StateFlow<String?> = _trayError.asStateFlow()

    private var trayLoadedAt = 0L

    /** Last clipboard link we auto-filled, so clearing the field doesn't re-fill it */
    private var lastClipboardUrl: String? = null

    fun updateUrl(url: String) {
        _urlInput.value = url
        // Clear error when user types
        if (_downloadState.value.error != null) {
            _downloadState.value = _downloadState.value.copy(error = null, loginSuggested = false)
        }
    }

    /**
     * Auto-fills an Instagram link found on the clipboard (like IGSaver: copy link → open app)
     * @return true if the field was filled
     */
    fun onClipboardText(text: String?): Boolean {
        val url = text?.let { InstagramLinks.findInstagramUrl(it) } ?: return false
        if (url == lastClipboardUrl || url == _urlInput.value || _downloadState.value.isBusy) return false
        lastClipboardUrl = url
        updateUrl(url)
        return true
    }

    /** Link shared to the app from Instagram's Share sheet: fill and start immediately */
    fun startFromSharedText(text: String) {
        val url = InstagramLinks.findInstagramUrl(text)
            ?: Regex("""https?://\S+""").find(text)?.value
            ?: return
        lastClipboardUrl = url
        updateUrl(url)
        downloadVideo()
    }

    fun downloadVideo() {
        val url = _urlInput.value.trim()

        if (url.isEmpty()) {
            _downloadState.value = DownloadState(error = getApplication<Application>().getString(R.string.ig_error_paste_link))
            return
        }
        if (!videoDownloader.isValidVideoUrl(url)) {
            _downloadState.value = DownloadState(error = getApplication<Application>().getString(R.string.ig_error_invalid_url))
            return
        }
        if (videoDownloader.isYouTubeUrl(url)) {
            _downloadState.value = DownloadState(
                error = getApplication<Application>().getString(R.string.ig_error_youtube)
            )
            return
        }
        if (_downloadState.value.isBusy) return

        downloadJob = viewModelScope.launch {
            Log.d(TAG, "[ViewModel] download starting url=$url")
            try {
                when {
                    InstagramLinks.isInstagramUrl(url) -> downloadInstagram(url)
                    videoDownloader.isDirectVideoUrl(url) -> downloadDirect(url)
                    else -> _downloadState.value = DownloadState(
                        error = getApplication<Application>().getString(R.string.ig_error_use_videos_tab)
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: InstagramException) {
                Log.e(TAG, "[ViewModel] Instagram failure: ${e.message}")
                _downloadState.value = DownloadState(
                    post = _downloadState.value.post,
                    downloaded = _downloadState.value.downloaded,
                    error = e.message,
                    loginSuggested = e.loginRequired && !InstagramClient.isLoggedIn()
                )
            } catch (e: Exception) {
                Log.e(TAG, "[ViewModel] download failure", e)
                _downloadState.value = DownloadState(
                    post = _downloadState.value.post,
                    downloaded = _downloadState.value.downloaded,
                    error = e.message ?: getApplication<Application>().getString(R.string.ig_error_download_failed)
                )
            }
        }
    }

    private suspend fun downloadInstagram(url: String) {
        _downloadState.value = DownloadState(isFetching = true)
        val post = instagramClient.fetchPost(url)
        val total = post.media.size
        _downloadState.value = DownloadState(isDownloading = true, post = post, totalItems = total, currentItem = 1)

        val downloaded = mutableListOf<DownloadedItem>()
        post.media.forEachIndexed { index, media ->
            _downloadState.value = _downloadState.value.copy(currentItem = index + 1)
            val file = videoDownloader.downloadFile(
                url = media.url,
                fileName = instagramFileName(post, media, index),
                referer = "https://www.instagram.com/"
            ) { itemProgress ->
                val overall = ((index * 100) + itemProgress) / total
                _downloadState.value = _downloadState.value.copy(progress = overall)
            }
            downloaded += moveToGallery(file)
            _downloadState.value = _downloadState.value.copy(downloaded = downloaded.toList())
        }
        _downloadState.value = DownloadState(progress = 100, post = post, downloaded = downloaded.toList(), totalItems = total)
    }

    private suspend fun downloadDirect(url: String) {
        _downloadState.value = DownloadState(isDownloading = true, totalItems = 1, currentItem = 1)
        val file = videoDownloader.downloadDirectVideo(url) { progress ->
            _downloadState.value = _downloadState.value.copy(progress = progress)
        }
        _downloadState.value = DownloadState(progress = 100, downloaded = listOf(moveToGallery(file)), totalItems = 1)
    }

    private fun instagramFileName(post: InstagramPost, media: InstagramMedia, index: Int): String {
        val extension = media.url.substringBefore("?").substringAfterLast(".", "").lowercase()
            .takeIf { it in listOf("mp4", "jpg", "jpeg", "png", "webp", "heic") }
            ?: if (media.isVideo) "mp4" else "jpg"
        val owner = (post.username ?: "instagram").replace(Regex("[^A-Za-z0-9._-]"), "_")
        val suffix = if (post.media.size > 1) "_${index + 1}" else ""
        return "${owner}_${post.code.take(20)}$suffix.$extension"
    }

    private suspend fun moveToGallery(file: File): DownloadedItem = withContext(Dispatchers.IO) {
        GallerySaver.moveToGallery(getApplication(), file)
    }

    /**
     * Loads the stories row. Cached for 2 minutes so switching tabs doesn't hit Instagram again.
     */
    fun loadStoriesTray(force: Boolean = false) {
        if (!InstagramClient.isLoggedIn() || _isTrayLoading.value) return
        if (!force && System.currentTimeMillis() - trayLoadedAt < 2 * 60_000 && _storiesTray.value.isNotEmpty()) return
        viewModelScope.launch {
            _isTrayLoading.value = true
            try {
                _storiesTray.value = instagramClient.fetchStoriesTray()
                _trayError.value = null
                trayLoadedAt = System.currentTimeMillis()
            } catch (e: CancellationException) {
                throw e
            } catch (e: InstagramException) {
                _trayError.value = e.message
                if (e.loginRequired) _isInstagramLoggedIn.value = InstagramClient.isLoggedIn()
            } catch (e: Exception) {
                Log.e(TAG, "[ViewModel] stories tray failed", e)
                _trayError.value = getApplication<Application>().getString(R.string.ig_error_load_stories)
            } finally {
                _isTrayLoading.value = false
            }
        }
    }

    fun onInstagramLoggedIn() {
        instagramClient.onLoginChanged()
        _isInstagramLoggedIn.value = true
        loadStoriesTray(force = true)
        // Retry the link that needed login
        if (_downloadState.value.loginSuggested && _urlInput.value.isNotBlank()) {
            _downloadState.value = DownloadState()
            downloadVideo()
        }
    }

    fun logoutInstagram() {
        instagramClient.logout()
        _isInstagramLoggedIn.value = false
        _storiesTray.value = emptyList()
        trayLoadedAt = 0
    }

    fun cancel() {
        downloadJob?.cancel()
        _downloadState.value = DownloadState()
    }

    /**
     * Clears the link field (and any finished result/error) so a new link can be pasted.
     * The cleared clipboard link is remembered, so it won't be auto-filled again.
     */
    fun clearLink() {
        _urlInput.value = ""
        if (!_downloadState.value.isBusy) {
            _downloadState.value = DownloadState()
        }
    }

    fun clearState() {
        _downloadState.value = DownloadState()
        _urlInput.value = ""
    }

    fun clearError() {
        _downloadState.value = _downloadState.value.copy(error = null, loginSuggested = false)
    }
}
