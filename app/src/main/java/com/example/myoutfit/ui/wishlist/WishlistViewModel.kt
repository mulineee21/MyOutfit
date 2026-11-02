package com.example.myoutfit.ui.wishlist

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myoutfit.data.local.entity.ClothingCategory
import com.example.myoutfit.data.local.entity.WishlistItem
import com.example.myoutfit.data.local.preferences.UserPreferencesRepository
import com.example.myoutfit.data.repository.ClothingRepository
import com.example.myoutfit.data.repository.WishlistRepository
import com.example.myoutfit.domain.ai.GeminiClothingAnalysisService
import com.example.myoutfit.domain.ai.ProductAiAnalysis
import com.example.myoutfit.domain.wishlist.ProductMetadataExtractor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WishlistUiState(
    val items: List<WishlistItem> = emptyList(),
    val allTags: List<String> = emptyList(),
    val allStores: List<String> = emptyList(),
    val selectedStyleTag: String? = null,
    val selectedStore: String? = null,
    val searchQuery: String = "",
    val isAddDialogOpen: Boolean = false,
    val itemToDelete: WishlistItem? = null,
    val itemToEdit: WishlistItem? = null,
    val itemToMoveToCloset: WishlistItem? = null,
    val isAnalyzing: Boolean = false,
    val analysisProgressText: String? = null,
    val analyzedPreview: ProductAiAnalysis? = null,
    val userMessage: String? = null
)

class WishlistViewModel(
    private val wishlistRepository: WishlistRepository,
    private val clothingRepository: ClothingRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val metadataExtractor: ProductMetadataExtractor = ProductMetadataExtractor(),
    private val aiAnalysisService: GeminiClothingAnalysisService = GeminiClothingAnalysisService()
) : ViewModel() {

    private val _selectedStyleTag = MutableStateFlow<String?>(null)
    private val _selectedStore = MutableStateFlow<String?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _isAddDialogOpen = MutableStateFlow(false)
    private val _itemToDelete = MutableStateFlow<WishlistItem?>(null)
    private val _itemToEdit = MutableStateFlow<WishlistItem?>(null)
    private val _itemToMoveToCloset = MutableStateFlow<WishlistItem?>(null)
    private val _isAnalyzing = MutableStateFlow(false)
    private val _analysisProgressText = MutableStateFlow<String?>(null)
    private val _analyzedPreview = MutableStateFlow<ProductAiAnalysis?>(null)
    private val _userMessage = MutableStateFlow<String?>(null)

    private data class DialogState(
        val isAddOpen: Boolean,
        val itemToDelete: WishlistItem?,
        val itemToEdit: WishlistItem?,
        val itemToMove: WishlistItem?,
        val isAnalyzing: Boolean,
        val progressText: String?,
        val preview: ProductAiAnalysis?,
        val message: String?
    )

    private val _dialogState = combine(
        _isAddDialogOpen,
        _itemToDelete,
        _itemToEdit,
        _itemToMoveToCloset,
        _isAnalyzing,
        _analysisProgressText,
        _analyzedPreview,
        _userMessage
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        DialogState(
            isAddOpen = args[0] as Boolean,
            itemToDelete = args[1] as WishlistItem?,
            itemToEdit = args[2] as WishlistItem?,
            itemToMove = args[3] as WishlistItem?,
            isAnalyzing = args[4] as Boolean,
            progressText = args[5] as String?,
            preview = args[6] as ProductAiAnalysis?,
            message = args[7] as String?
        )
    }

    val uiState: StateFlow<WishlistUiState> = combine(
        wishlistRepository.getAllWishlistItems(),
        _selectedStyleTag,
        _selectedStore,
        _searchQuery,
        _dialogState
    ) { allItems, selectedTag, selectedStore, query, dialogState ->
        val filtered = allItems.filter { item ->
            val matchesTag = selectedTag == null || item.styleTags.any { it.equals(selectedTag, ignoreCase = true) }
            val matchesStore = selectedStore == null || item.storeName.equals(selectedStore, ignoreCase = true)
            val matchesQuery = query.isBlank() ||
                item.title.contains(query, ignoreCase = true) ||
                item.description.contains(query, ignoreCase = true) ||
                item.category.contains(query, ignoreCase = true) ||
                item.storeName.contains(query, ignoreCase = true) ||
                item.styleTags.any { it.contains(query, ignoreCase = true) }
            matchesTag && matchesStore && matchesQuery
        }

        val allTags = allItems.flatMap { it.styleTags }.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        val allStores = allItems.map { it.storeName.trim() }.filter { it.isNotBlank() }.distinct()

        WishlistUiState(
            items = filtered,
            allTags = allTags,
            allStores = allStores,
            selectedStyleTag = selectedTag,
            selectedStore = selectedStore,
            searchQuery = query,
            isAddDialogOpen = dialogState.isAddOpen,
            itemToDelete = dialogState.itemToDelete,
            itemToEdit = dialogState.itemToEdit,
            itemToMoveToCloset = dialogState.itemToMove,
            isAnalyzing = dialogState.isAnalyzing,
            analysisProgressText = dialogState.progressText,
            analyzedPreview = dialogState.preview,
            userMessage = dialogState.message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = WishlistUiState()
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onStyleTagSelected(tag: String?) {
        _selectedStyleTag.value = if (_selectedStyleTag.value == tag) null else tag
    }

    fun onStoreSelected(store: String?) {
        _selectedStore.value = if (_selectedStore.value == store) null else store
    }

    fun openAddDialog(initialUrl: String? = null) {
        _analyzedPreview.value = null
        _isAddDialogOpen.value = true
        if (!initialUrl.isNullOrBlank()) {
            analyzeUrl(initialUrl)
        }
    }

    fun closeAddDialog() {
        _isAddDialogOpen.value = false
        _analyzedPreview.value = null
        _isAnalyzing.value = false
    }

    fun setItemToDelete(item: WishlistItem?) {
        _itemToDelete.value = item
    }

    fun setItemToEdit(item: WishlistItem?) {
        _itemToEdit.value = item
    }

    fun setItemToMoveToCloset(item: WishlistItem?) {
        _itemToMoveToCloset.value = item
    }

    fun analyzeUrl(url: String) {
        if (url.isBlank()) {
            _userMessage.value = "Introduce un enlace para analizar"
            return
        }

        viewModelScope.launch {
            _isAnalyzing.value = true
            _analysisProgressText.value = "Obteniendo datos de la prenda..."

            try {
                val metadata = metadataExtractor.extractMetadata(url)
                _analysisProgressText.value = "Analizando estilo y categoría con IA..."

                val prefs = userPreferencesRepository.userPreferencesFlow.first()
                val analysisResult = aiAnalysisService.analyzeProductLink(url, metadata, prefs.geminiApiKey)

                if (analysisResult.isSuccess) {
                    _analyzedPreview.value = analysisResult.getOrThrow()
                } else {
                    _analyzedPreview.value = ProductAiAnalysis(
                        title = metadata.title.ifBlank { "Prenda ${metadata.storeName}" },
                        category = "Moda",
                        styleTags = listOf("Casual"),
                        color = "",
                        description = metadata.description,
                        price = metadata.price,
                        storeName = metadata.storeName,
                        imageUrl = metadata.imageUrl
                    )
                }
            } catch (e: Exception) {
                _userMessage.value = "No se pudieron obtener todos los datos. Puedes completarlos manualmente."
            } finally {
                _isAnalyzing.value = false
                _analysisProgressText.value = null
            }
        }
    }

    fun saveWishlistItem(
        title: String,
        productUrl: String,
        imageUrl: String,
        description: String,
        styleTags: List<String>,
        category: String,
        color: String,
        price: String,
        storeName: String
    ) {
        viewModelScope.launch {
            if (title.isBlank()) {
                _userMessage.value = "El título de la prenda es obligatorio"
                return@launch
            }

            val item = WishlistItem(
                title = title.trim(),
                productUrl = productUrl.trim(),
                imageUrl = imageUrl.trim(),
                description = description.trim(),
                styleTags = styleTags.map { it.trim() }.filter { it.isNotBlank() },
                category = category.trim().ifBlank { "Moda" },
                color = color.trim(),
                price = price.trim(),
                storeName = storeName.trim().ifBlank { metadataExtractor.detectStoreFromUrl(productUrl) }
            )

            wishlistRepository.insertWishlistItem(item)
            closeAddDialog()
            _userMessage.value = "Prenda guardada en tu lista de deseos"
        }
    }

    fun updateWishlistItem(
        original: WishlistItem,
        title: String,
        productUrl: String,
        imageUrl: String,
        description: String,
        styleTags: List<String>,
        category: String,
        color: String,
        price: String,
        storeName: String
    ) {
        viewModelScope.launch {
            val updated = original.copy(
                title = title.trim(),
                productUrl = productUrl.trim(),
                imageUrl = imageUrl.trim(),
                description = description.trim(),
                styleTags = styleTags.map { it.trim() }.filter { it.isNotBlank() },
                category = category.trim().ifBlank { "Moda" },
                color = color.trim(),
                price = price.trim(),
                storeName = storeName.trim()
            )

            wishlistRepository.updateWishlistItem(updated)
            _itemToEdit.value = null
            _userMessage.value = "Prenda actualizada en la lista de deseos"
        }
    }

    fun deleteWishlistItem(item: WishlistItem) {
        viewModelScope.launch {
            wishlistRepository.deleteWishlistItem(item)
            _itemToDelete.value = null
            _userMessage.value = "Prenda eliminada de la lista de deseos"
        }
    }

    fun moveWishlistItemToCloset(
        context: Context,
        item: WishlistItem,
        category: ClothingCategory,
        color: String,
        deleteFromWishlist: Boolean = false
    ) {
        viewModelScope.launch {
            try {
                wishlistRepository.moveToCloset(
                    context = context,
                    item = item,
                    clothingRepository = clothingRepository,
                    category = category,
                    color = color,
                    deleteFromWishlist = deleteFromWishlist
                )
                _itemToMoveToCloset.value = null
                _userMessage.value = "¡Prenda añadida con éxito a tu armario!"
            } catch (e: Exception) {
                _userMessage.value = "Error al mover la prenda al armario: ${e.message}"
            }
        }
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }
}

class WishlistViewModelFactory(
    private val wishlistRepository: WishlistRepository,
    private val clothingRepository: ClothingRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WishlistViewModel::class.java)) {
            return WishlistViewModel(
                wishlistRepository,
                clothingRepository,
                userPreferencesRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
