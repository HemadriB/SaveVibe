package com.hemadri.statushub.viewmodel

import com.hemadri.statushub.R
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hemadri.statushub.instagram.InstagramClient
import com.hemadri.statushub.instagram.InstagramUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Instagram account search with debounce (one request after the user stops typing)
 */
class InstagramSearchViewModel(application: Application) : AndroidViewModel(application) {

    private val client = InstagramClient.get(application)
    private var searchJob: Job? = null

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<InstagramUser>>(emptyList())
    val results: StateFlow<List<InstagramUser>> = _results.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun onQueryChange(value: String) {
        _query.value = value
        searchJob?.cancel()
        val trimmed = value.trim().removePrefix("@")
        if (trimmed.length < 2) {
            _results.value = emptyList()
            _isSearching.value = false
            return
        }
        searchJob = viewModelScope.launch {
            delay(500)
            _isSearching.value = true
            try {
                _results.value = client.searchUsers(trimmed)
                _error.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = e.message ?: getApplication<Application>().getString(R.string.ig_error_search_failed)
            } finally {
                _isSearching.value = false
            }
        }
    }
}
