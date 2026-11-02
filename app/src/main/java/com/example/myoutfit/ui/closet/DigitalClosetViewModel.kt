package com.example.myoutfit.ui.closet

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myoutfit.data.local.entity.ClothingCategory
import com.example.myoutfit.data.local.entity.ClothingItem
import com.example.myoutfit.data.local.preferences.UserPreferencesRepository
import com.example.myoutfit.data.repository.ClothingRepository
import com.example.myoutfit.domain.ai.GeminiClothingAnalysisService
import com.example.myoutfit.util.ImageStorageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DigitalClosetUiState(
    val items: List<ClothingItem> = emptyList(),
    val selectedCategory: ClothingCategory? = null,
    val searchQuery: String = "",
    val isAddDialogOpen: Boolean = false,
    val itemToDelete: ClothingItem? = null,
    val itemToEdit: ClothingItem? = null,
    val userMessage: String? = null,
    val isAiCategorizing: Boolean = false,
    val aiCategorizationMessage: String? = null,
    val isAiCategorizationEnabled: Boolean = true
)

class DigitalClosetViewModel(
    private val clothingRepository: ClothingRepository,
    private val userPreferencesRepository: UserPreferencesRepository? = null,
    private val aiAnalysisService: GeminiClothingAnalysisService = GeminiClothingAnalysisService()
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow<ClothingCategory?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _isAddDialogOpen = MutableStateFlow(false)
    private val _itemToDelete = MutableStateFlow<ClothingItem?>(null)
    private val _itemToEdit = MutableStateFlow<ClothingItem?>(null)
    private val _userMessage = MutableStateFlow<String?>(null)
    private val _isAiCategorizing = MutableStateFlow(false)
    private val _aiCategorizationMessage = MutableStateFlow<String?>(null)

    private val _aiPreferencesFlow = userPreferencesRepository?.userPreferencesFlow
        ?.map { it.autoCategorizeWithAi } ?: flowOf(true)

    private data class DialogState(
        val isAddOpen: Boolean,
        val itemToDelete: ClothingItem?,
        val itemToEdit: ClothingItem?,
        val message: String?,
        val isAiCategorizing: Boolean,
        val aiCategorizationMessage: String?
    )

    private val _dialogState = combine(
        _isAddDialogOpen,
        _itemToDelete,
        _itemToEdit,
        _userMessage,
        _isAiCategorizing,
        _aiCategorizationMessage
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        DialogState(
            isAddOpen = args[0] as Boolean,
            itemToDelete = args[1] as ClothingItem?,
            itemToEdit = args[2] as ClothingItem?,
            message = args[3] as String?,
            isAiCategorizing = args[4] as Boolean,
            aiCategorizationMessage = args[5] as String?
        )
    }

    val uiState: StateFlow<DigitalClosetUiState> = combine(
        clothingRepository.getAllClothingItems(),
        _selectedCategory,
        _searchQuery,
        _dialogState,
        _aiPreferencesFlow
    ) { allItems, category, query, dialogState, isAiEnabled ->
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
            isAddDialogOpen = dialogState.isAddOpen,
            itemToDelete = dialogState.itemToDelete,
            itemToEdit = dialogState.itemToEdit,
            userMessage = dialogState.message,
            isAiCategorizing = dialogState.isAiCategorizing,
            aiCategorizationMessage = dialogState.aiCategorizationMessage,
            isAiCategorizationEnabled = isAiEnabled
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

    fun setItemToEdit(item: ClothingItem?) {
        _itemToEdit.value = item
    }

    fun toggleAiCategorization(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository?.updateAutoCategorizeWithAi(enabled)
        }
    }

    fun categorizeImageWithAi(
        context: Context,
        imageUri: Uri,
        onResult: (ClothingCategory, String, List<String>, String) -> Unit
    ) {
        viewModelScope.launch {
            _isAiCategorizing.value = true
            _aiCategorizationMessage.value = "Gemini está analizando tu prenda..."
            try {
                val apiKey = userPreferencesRepository?.userPreferencesFlow?.first()?.geminiApiKey ?: ""
                val result = aiAnalysisService.categorizeClothingImage(context, imageUri, apiKey)
                if (result.isSuccess) {
                    val classification = result.getOrThrow()
                    onResult(
                        classification.category,
                        classification.color,
                        classification.styleTags,
                        classification.description
                    )
                    _userMessage.value = "✨ Prenda categorizada por Gemini"
                } else {
                    _userMessage.value = "No se pudo autocategorizar: ${result.exceptionOrNull()?.message ?: "Verifica tu clave"}"
                }
            } catch (e: Exception) {
                _userMessage.value = "Error al analizar prenda con IA: ${e.message}"
            } finally {
                _isAiCategorizing.value = false
                _aiCategorizationMessage.value = null
            }
        }
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

    fun updateClothingItem(
        context: Context,
        originalItem: ClothingItem,
        newImageUri: Uri?,
        category: ClothingCategory,
        color: String,
        styleTags: List<String>,
        description: String
    ) {
        viewModelScope.launch {
            var finalImagePath = originalItem.imagePath

            if (newImageUri != null) {
                val savedPath = ImageStorageManager.saveUri(context, newImageUri, "clothing")
                if (!savedPath.isNullOrBlank()) {
                    if (originalItem.imagePath != savedPath && originalItem.imagePath.isNotBlank()) {
                        ImageStorageManager.deleteImage(originalItem.imagePath)
                    }
                    finalImagePath = savedPath
                } else {
                    _userMessage.value = "Error al actualizar la imagen de la prenda"
                    return@launch
                }
            }

            val updatedItem = originalItem.copy(
                imagePath = finalImagePath,
                category = category,
                color = color.trim(),
                styleTags = styleTags.map { it.trim() }.filter { it.isNotEmpty() },
                description = description.trim()
            )

            clothingRepository.updateClothingItem(updatedItem)
            _itemToEdit.value = null
            _userMessage.value = "Prenda actualizada correctamente"
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
    private val repository: ClothingRepository,
    private val userPreferencesRepository: UserPreferencesRepository? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DigitalClosetViewModel::class.java)) {
            return DigitalClosetViewModel(repository, userPreferencesRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
