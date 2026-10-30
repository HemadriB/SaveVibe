package com.statushub.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.statushub.repository.SavedMedia
import com.statushub.repository.SavedMediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Saved tab - everything saved or downloaded by the app
 */
class SavedViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SavedMediaRepository(application)

    private val _items = MutableStateFlow<List<SavedMedia>>(emptyList())
    val items: StateFlow<List<SavedMedia>> = _items.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            _items.value = repository.loadSavedMedia()
            _isLoading.value = false
        }
    }

    fun delete(item: SavedMedia, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val deleted = repository.delete(item)
            if (deleted) {
                _items.value = _items.value - item
            }
            onResult(deleted)
        }
    }
}
