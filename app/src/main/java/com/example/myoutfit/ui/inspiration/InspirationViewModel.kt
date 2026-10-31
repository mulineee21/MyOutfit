package com.example.myoutfit.ui.inspiration

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myoutfit.data.local.entity.InspirationItem
import com.example.myoutfit.data.repository.InspirationRepository
import com.example.myoutfit.util.ImageStorageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class InspirationUiState(
    val items: List<InspirationItem> = emptyList(),
    val searchQuery: String = "",
    val selectedStyleFilter: String? = null,
    val availableStyles: List<String> = emptyList(),
    val isAddDialogOpen: Boolean = false,
    val itemToDelete: InspirationItem? = null,
    val userMessage: String? = null
)

class InspirationViewModel(
    private val inspirationRepository: InspirationRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedStyleFilter = MutableStateFlow<String?>(null)
    private val _isAddDialogOpen = MutableStateFlow(false)
    private val _itemToDelete = MutableStateFlow<InspirationItem?>(null)
    private val _userMessage = MutableStateFlow<String?>(null)

    private val _dialogState = combine(
        _isAddDialogOpen,
        _itemToDelete,
        _userMessage
    ) { isAddOpen, deleteItem, message ->
        Triple(isAddOpen, deleteItem, message)
    }

    val uiState: StateFlow<InspirationUiState> = combine(
        inspirationRepository.getAllInspirationItems(),
        _searchQuery,
        _selectedStyleFilter,
        _dialogState
    ) { allItems: List<InspirationItem>, query: String, selectedStyle: String?, (isAddOpen, deleteItem, message) ->
        val defaultStyles = listOf("Casual", "Gótico", "Streetwear", "Colorido", "Formal", "Vintage", "Elegante")
        val itemStyles = allItems.flatMap { it.styleTags }.filter { it.isNotBlank() }
        val availableStyles = (defaultStyles + itemStyles).distinct()

        val filteredItems = allItems.filter { item ->
            val matchesStyle = selectedStyle == null || item.styleTags.any { it.equals(selectedStyle, ignoreCase = true) }
            val matchesQuery = query.isBlank() ||
                item.title.contains(query, ignoreCase = true) ||
                item.notes.contains(query, ignoreCase = true) ||
                item.styleTags.any { it.contains(query, ignoreCase = true) }
            matchesStyle && matchesQuery
        }

        InspirationUiState(
            items = filteredItems,
            searchQuery = query,
            selectedStyleFilter = selectedStyle,
            availableStyles = availableStyles,
            isAddDialogOpen = isAddOpen,
            itemToDelete = deleteItem,
            userMessage = message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = InspirationUiState()
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onStyleFilterSelected(style: String?) {
        _selectedStyleFilter.value = style
    }

    fun setAddDialogOpen(isOpen: Boolean) {
        _isAddDialogOpen.value = isOpen
    }

    fun setItemToDelete(item: InspirationItem?) {
        _itemToDelete.value = item
    }

    fun addInspirationItem(
        context: Context,
        imageUri: Uri?,
        title: String,
        styleTags: List<String>,
        notes: String,
        sourceUrl: String
    ) {
        viewModelScope.launch {
            if (imageUri == null) {
                _userMessage.value = "Por favor, selecciona o toma una foto"
                return@launch
            }

            if (title.isBlank()) {
                _userMessage.value = "Por favor, ingresa un título"
                return@launch
            }

            val savedPath = ImageStorageManager.saveUri(context, imageUri, "inspiration")
            if (savedPath.isNullOrBlank()) {
                _userMessage.value = "Error al guardar la imagen"
                return@launch
            }

            val newItem = InspirationItem(
                imagePath = savedPath,
                title = title.trim(),
                styleTags = styleTags.map { it.trim() }.filter { it.isNotEmpty() },
                notes = notes.trim(),
                sourceUrl = sourceUrl.trim()
            )

            inspirationRepository.insertInspirationItem(newItem)
            _isAddDialogOpen.value = false
            _userMessage.value = "Outfit de inspiración añadido"
        }
    }

    fun deleteInspirationItem(item: InspirationItem) {
        viewModelScope.launch {
            inspirationRepository.deleteInspirationItem(item)
            ImageStorageManager.deleteImage(item.imagePath)
            _itemToDelete.value = null
            _userMessage.value = "Inspiración eliminada"
        }
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }
}

class InspirationViewModelFactory(
    private val repository: InspirationRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(InspirationViewModel::class.java)) {
            return InspirationViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
