package com.example.myoutfit.ui.closet

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myoutfit.data.local.entity.ClothingCategory
import com.example.myoutfit.data.local.entity.ClothingItem
import com.example.myoutfit.data.repository.ClothingRepository
import com.example.myoutfit.util.ImageStorageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DigitalClosetUiState(
    val items: List<ClothingItem> = emptyList(),
    val selectedCategory: ClothingCategory? = null,
    val searchQuery: String = "",
    val isAddDialogOpen: Boolean = false,
    val itemToDelete: ClothingItem? = null,
    val userMessage: String? = null
)

class DigitalClosetViewModel(
    private val clothingRepository: ClothingRepository
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow<ClothingCategory?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _isAddDialogOpen = MutableStateFlow(false)
    private val _itemToDelete = MutableStateFlow<ClothingItem?>(null)
    private val _userMessage = MutableStateFlow<String?>(null)

    private val _dialogState = combine(
        _isAddDialogOpen,
        _itemToDelete,
        _userMessage
    ) { isAddOpen, deleteItem, message ->
        Triple(isAddOpen, deleteItem, message)
    }

    val uiState: StateFlow<DigitalClosetUiState> = combine(
        clothingRepository.getAllClothingItems(),
        _selectedCategory,
        _searchQuery,
        _dialogState
    ) { allItems: List<ClothingItem>, category: ClothingCategory?, query: String, (isAddOpen, deleteItem, message) ->
        val filteredItems = allItems.filter { item ->
            val matchesCategory = (category == null || item.category == category)
            val matchesQuery = query.isBlank() ||
                item.description.contains(query, ignoreCase = true) ||
                item.color.contains(query, ignoreCase = true) ||
                item.styleTags.any { it.contains(query, ignoreCase = true) }
            matchesCategory && matchesQuery
        }

        DigitalClosetUiState(
            items = filteredItems,
            selectedCategory = category,
            searchQuery = query,
            isAddDialogOpen = isAddOpen,
            itemToDelete = deleteItem,
            userMessage = message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DigitalClosetUiState()
    )

    fun onCategorySelected(category: ClothingCategory?) {
        _selectedCategory.value = category
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun setAddDialogOpen(isOpen: Boolean) {
        _isAddDialogOpen.value = isOpen
    }

    fun setItemToDelete(item: ClothingItem?) {
        _itemToDelete.value = item
    }

    fun addClothingItem(
        context: Context,
        imageUri: Uri?,
        category: ClothingCategory,
        color: String,
        styleTags: List<String>,
        description: String
    ) {
        viewModelScope.launch {
            if (imageUri == null) {
                _userMessage.value = "Por favor, selecciona o toma una foto"
                return@launch
            }

            val savedPath = ImageStorageManager.saveUri(context, imageUri, "clothing")
            if (savedPath.isNullOrBlank()) {
                _userMessage.value = "Error al guardar la imagen"
                return@launch
            }

            val newItem = ClothingItem(
                imagePath = savedPath,
                category = category,
                color = color.trim(),
                styleTags = styleTags.map { it.trim() }.filter { it.isNotEmpty() },
                description = description.trim()
            )

            clothingRepository.insertClothingItem(newItem)
            _isAddDialogOpen.value = false
            _userMessage.value = "Prenda añadida correctamente"
        }
    }

    fun deleteClothingItem(item: ClothingItem) {
        viewModelScope.launch {
            clothingRepository.deleteClothingItem(item)
            ImageStorageManager.deleteImage(item.imagePath)
            _itemToDelete.value = null
            _userMessage.value = "Prenda eliminada"
        }
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }
}

class DigitalClosetViewModelFactory(
    private val repository: ClothingRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DigitalClosetViewModel::class.java)) {
            return DigitalClosetViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
