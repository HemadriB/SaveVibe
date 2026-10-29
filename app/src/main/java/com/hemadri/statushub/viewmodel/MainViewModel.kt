package com.hemadri.statushub.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hemadri.statushub.data.StatusItem
import com.hemadri.statushub.data.StatusSource
import com.hemadri.statushub.data.StatusType
import com.hemadri.statushub.repository.StatusRepository
import com.hemadri.statushub.utils.StatusScanner
import com.hemadri.statushub.analytics.Analytics
import com.hemadri.statushub.utils.PreferencesHelper
import com.hemadri.statushub.widget.StatusWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for Main Screen
 * Manages status items list, folder access and UI state
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = StatusRepository(application)

    private val _images = MutableStateFlow<List<StatusItem>>(emptyList())
    val images: StateFlow<List<StatusItem>> = _images.asStateFlow()

    private val _videos = MutableStateFlow<List<StatusItem>>(emptyList())
    val videos: StateFlow<List<StatusItem>> = _videos.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** Installed WhatsApp apps whose status folder the user hasn't granted yet (Android 11+) */
    private val _sourcesNeedingAccess = MutableStateFlow<List<StatusSource>>(emptyList())
    val sourcesNeedingAccess: StateFlow<List<StatusSource>> = _sourcesNeedingAccess.asStateFlow()

    /** Pull-to-refresh indicator (only for refreshes the user started) */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** Status Uris currently being saved / already saved this session (drives the animated save buttons) */
    private val _savingUris = MutableStateFlow<Set<String>>(emptySet())
    val savingUris: StateFlow<Set<String>> = _savingUris.asStateFlow()

    private val _savedUris = MutableStateFlow<Set<String>>(emptySet())
    val savedUris: StateFlow<Set<String>> = _savedUris.asStateFlow()

    /** True when at least one status folder can be read */
    private val _hasAccess = MutableStateFlow(true)
    val hasAccess: StateFlow<Boolean> = _hasAccess.asStateFlow()

    /**
     * Re-scan statuses. Called whenever the app comes back to the foreground,
     * so statuses viewed in WhatsApp in the meantime show up.
     */
    fun refresh(userInitiated: Boolean = false) {
        if (userInitiated) _isRefreshing.value = true
        viewModelScope.launch {
            val app = getApplication<Application>()
            // Only show the spinner on first load to avoid flicker on every resume
            _isLoading.value = _images.value.isEmpty() && _videos.value.isEmpty()
            try {
                val needing = withContext(Dispatchers.IO) { StatusScanner.sourcesNeedingAccess(app) }
                _sourcesNeedingAccess.value = needing
                _hasAccess.value = needing.size < StatusScanner.installedSources(app).size
                val all = if (_hasAccess.value) repository.scanStatusFiles() else emptyList()
                _images.value = all.filter { it.type == StatusType.IMAGE }
                _videos.value = all.filter { it.type == StatusType.VIDEO }
                markStatusesSeen(app, all)
            } finally {
                _isLoading.value = false
                _isRefreshing.value = false
            }
        }
    }

    /** The user has now seen these statuses: clear the widget's "new" count and skip them in alerts */
    private fun markStatusesSeen(app: Application, all: List<StatusItem>) {
        val newest = all.maxOfOrNull { it.lastModified } ?: return
        PreferencesHelper.setLastSeenStatusAt(app, newest)
        // With auto-save on, the worker still has to save them even though they were seen
        if (!PreferencesHelper.isAutoSaveEnabled(app)) PreferencesHelper.setLastProcessedStatusAt(app, newest)
        PreferencesHelper.setNewStatusCount(app, 0)
        StatusWidgetProvider.updateAll(app)
    }

    /**
     * Handles the folder chosen in the system folder picker
     * @return the source that was granted, or null if the user picked a non-WhatsApp folder
     */
    fun onFolderPicked(uri: Uri): StatusSource? {
        val source = StatusScanner.onFolderPicked(getApplication(), uri)
        if (source != null) {
            Analytics.log(Analytics.STATUS_ACCESS_GRANTED, "source" to source.name, "from" to "status_tab")
            refresh()
        } else {
            Analytics.log(Analytics.STATUS_ACCESS_WRONG_FOLDER, "from" to "status_tab")
        }
        return source
    }

    /**
     * The list (photos or videos) containing [uri] and its index, for the swipeable preview
     */
    fun statusListFor(uri: String): Pair<List<StatusItem>, Int>? =
        listOf(_images.value, _videos.value).firstNotNullOfOrNull { list ->
            list.indexOfFirst { it.uri.toString() == uri }.takeIf { it >= 0 }?.let { list to it }
        }

    fun saveStatus(item: StatusItem, onResult: (Boolean) -> Unit) {
        val key = item.uri.toString()
        if (key in _savingUris.value) return
        _savingUris.value = _savingUris.value + key
        viewModelScope.launch {
            val saved = repository.saveStatusFile(item)
            _savingUris.value = _savingUris.value - key
            if (saved) _savedUris.value = _savedUris.value + key
            onResult(saved)
        }
    }
}
