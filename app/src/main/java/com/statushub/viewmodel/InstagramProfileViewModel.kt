package com.statushub.viewmodel

import com.statushub.R
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.statushub.data.DownloadedItem
import com.statushub.instagram.Highlight
import com.statushub.instagram.InstagramClient
import com.statushub.instagram.InstagramException
import com.statushub.instagram.InstagramMedia
import com.statushub.instagram.InstagramUser
import com.statushub.instagram.StoryItem
import com.statushub.utils.GallerySaver
import com.statushub.utils.VideoDownloader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class InstagramProfileState(
    val username: String,
    /** Known immediately when opened from the stories row / search; otherwise resolved once */
    val user: InstagramUser? = null,
    val isLoadingProfile: Boolean = true,
    /** null until the user opens the Highlights section (loaded on demand to save requests) */
    val highlights: List<Highlight>? = null,
    val isLoadingHighlights: Boolean = false,
    /** null = current stories, otherwise the selected highlight */
    val selectedHighlight: Highlight? = null,
    val items: List<StoryItem> = emptyList(),
    val isLoadingItems: Boolean = false,
    /** Story ids currently downloading / already saved in this session */
    val saving: Set<String> = emptySet(),
    val saved: Map<String, DownloadedItem> = emptyMap(),
    val error: String? = null,
    val loginRequired: Boolean = false
)

/**
 * A profile's current stories, highlights and HD profile picture, with saving to the gallery
 */
class InstagramProfileViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "InstagramProfile"
        const val ARG_USERNAME = "username"
        const val ARG_USER_ID = "userId"
        const val ARG_PICTURE = "pic"
        const val PROFILE_PICTURE_ID = "profile_picture"
    }

    private val client = InstagramClient.get(application)
    private val downloader = VideoDownloader(application)

    private val _state = MutableStateFlow(
        InstagramProfileState(
            username = savedStateHandle.get<String>(ARG_USERNAME).orEmpty(),
            user = savedStateHandle.get<String>(ARG_USER_ID)?.takeIf { it.isNotEmpty() }?.let { id ->
                InstagramUser(
                    id = id,
                    username = savedStateHandle.get<String>(ARG_USERNAME).orEmpty(),
                    fullName = null,
                    profilePicUrl = savedStateHandle.get<String>(ARG_PICTURE)?.ifEmpty { null }
                )
            }
        )
    )
    val state: StateFlow<InstagramProfileState> = _state.asStateFlow()

    /** HD profile picture URL, fetched only when the user asks to save it */
    private var hdPictureUrl: String? = null

    init {
        loadProfile()
    }

    /**
     * Opening a profile costs a single request (its stories) when we already know the user id.
     */
    fun loadProfile() {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingProfile = true, error = null) }
            try {
                if (_state.value.user == null) {
                    val user = client.findUser(_state.value.username)
                    _state.update { it.copy(user = user) }
                }
                _state.update { it.copy(isLoadingProfile = false) }
                loadItems(null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "loadProfile failed", e)
                _state.update {
                    it.copy(
                        isLoadingProfile = false,
                        error = e.message ?: getApplication<Application>().getString(R.string.ig_error_load_profile),
                        loginRequired = (e as? InstagramException)?.loginRequired == true
                    )
                }
            }
        }
    }

    /** @param highlight null shows the current stories */
    fun loadItems(highlight: Highlight?) {
        val userId = _state.value.user?.id ?: return
        viewModelScope.launch {
            _state.update { it.copy(selectedHighlight = highlight, isLoadingItems = true, items = emptyList(), error = null) }
            try {
                val items = client.fetchStoryItems(highlight?.id ?: userId)
                _state.update { it.copy(items = items, isLoadingItems = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "loadItems failed", e)
                _state.update { it.copy(isLoadingItems = false, error = e.message ?: getApplication<Application>().getString(R.string.ig_error_load_stories)) }
            }
        }
    }

    fun save(item: StoryItem) = saveMedia(item.id, item.media)

    fun saveAll() {
        _state.value.items.filter { it.id !in _state.value.saved }.forEach { save(it) }
    }

    fun loadHighlights() {
        val userId = _state.value.user?.id ?: return
        if (_state.value.isLoadingHighlights) return
        viewModelScope.launch {
            _state.update { it.copy(isLoadingHighlights = true) }
            try {
                val highlights = client.fetchHighlights(userId)
                _state.update { it.copy(highlights = highlights, isLoadingHighlights = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "loadHighlights failed", e)
                _state.update { it.copy(isLoadingHighlights = false, error = e.message ?: getApplication<Application>().getString(R.string.ig_error_load_highlights)) }
            }
        }
    }

    fun saveProfilePicture() {
        val user = _state.value.user ?: return
        if (PROFILE_PICTURE_ID in _state.value.saving) return
        viewModelScope.launch {
            val url = hdPictureUrl ?: try {
                _state.update { it.copy(saving = it.saving + PROFILE_PICTURE_ID) }
                client.fetchHdProfilePicture(user.id).also { hdPictureUrl = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "HD picture lookup failed, using standard size", e)
                user.profilePicUrl
            } finally {
                _state.update { it.copy(saving = it.saving - PROFILE_PICTURE_ID) }
            }
            url?.let { saveMedia(PROFILE_PICTURE_ID, InstagramMedia(it, isVideo = false, thumbnailUrl = it)) }
                ?: _state.update { it.copy(error = getApplication<Application>().getString(R.string.ig_error_profile_photo)) }
        }
    }

    private fun saveMedia(id: String, media: InstagramMedia) {
        if (id in _state.value.saving || id in _state.value.saved) return
        _state.update { it.copy(saving = it.saving + id) }
        viewModelScope.launch {
            try {
                val extension = media.url.substringBefore("?").substringAfterLast(".", "").lowercase()
                    .takeIf { it in listOf("mp4", "jpg", "jpeg", "png", "webp", "heic") }
                    ?: if (media.isVideo) "mp4" else "jpg"
                val kind = when (id) {
                    PROFILE_PICTURE_ID -> "profile"
                    else -> if (_state.value.selectedHighlight != null) "highlight" else "story"
                }
                val file = downloader.downloadFile(
                    url = media.url,
                    fileName = "${_state.value.username}_${kind}_${id.take(24)}.$extension",
                    referer = "https://www.instagram.com/"
                )
                val saved = withContext(Dispatchers.IO) { GallerySaver.moveToGallery(getApplication(), file) }
                _state.update { it.copy(saving = it.saving - id, saved = it.saved + (id to saved)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "save failed", e)
                _state.update { it.copy(saving = it.saving - id, error = getApplication<Application>().getString(R.string.ig_error_save, e.message.orEmpty())) }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
