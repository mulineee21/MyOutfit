package com.example.myoutfit.ui.logfavorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myoutfit.data.local.entity.ClothingItem
import com.example.myoutfit.data.local.entity.LoggedOutfit
import com.example.myoutfit.data.local.entity.Outfit
import com.example.myoutfit.data.repository.ClothingRepository
import com.example.myoutfit.data.repository.LoggedOutfitRepository
import com.example.myoutfit.data.repository.OutfitRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoggedOutfitWithDetails(
    val loggedOutfit: LoggedOutfit,
    val items: List<ClothingItem>
)

data class FavoriteOutfitWithDetails(
    val outfit: Outfit,
    val items: List<ClothingItem>
)

data class LogAndFavoritesUiState(
    val loggedOutfits: List<LoggedOutfitWithDetails> = emptyList(),
    val favoriteOutfits: List<FavoriteOutfitWithDetails> = emptyList(),
    val allClothingItems: List<ClothingItem> = emptyList(),
    val selectedTab: Int = 0,
    val isLoading: Boolean = false,
    val showManualLogDialog: Boolean = false,
    val snackbarMessage: String? = null
)

class LogAndFavoritesViewModel(
    private val loggedOutfitRepository: LoggedOutfitRepository,
    private val outfitRepository: OutfitRepository,
    private val clothingRepository: ClothingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LogAndFavoritesUiState())
    val uiState: StateFlow<LogAndFavoritesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                loggedOutfitRepository.getAllLoggedOutfits(),
                outfitRepository.getFavoriteOutfits(),
                clothingRepository.getAllClothingItems()
            ) { loggedList, favList, clothingList ->
                val clothingMap = clothingList.associateBy { it.id }

                val loggedDetailed = loggedList.map { logged ->
                    val items = logged.clothingItemIds.mapNotNull { clothingMap[it] }
                    LoggedOutfitWithDetails(loggedOutfit = logged, items = items)
                }

                val favDetailed = favList.map { fav ->
                    val items = fav.clothingItemIds.mapNotNull { clothingMap[it] }
                    FavoriteOutfitWithDetails(outfit = fav, items = items)
                }

                Triple(loggedDetailed, favDetailed, clothingList)
            }.collect { (loggedDetailed, favDetailed, clothingList) ->
                _uiState.update { state ->
                    state.copy(
                        loggedOutfits = loggedDetailed,
                        favoriteOutfits = favDetailed,
                        allClothingItems = clothingList
                    )
                }
            }
        }
    }

    fun selectTab(tabIndex: Int) {
        _uiState.update { it.copy(selectedTab = tabIndex) }
    }

    fun deleteLoggedOutfit(id: Long) {
        viewModelScope.launch {
            loggedOutfitRepository.deleteLoggedOutfitById(id)
            _uiState.update { it.copy(snackbarMessage = "Entrada eliminada del Diario") }
        }
    }

    fun deleteFavoriteOutfit(id: Long) {
        viewModelScope.launch {
            outfitRepository.deleteOutfitById(id)
            _uiState.update { it.copy(snackbarMessage = "Outfit eliminado de Favoritos") }
        }
    }

    fun toggleFavoriteStatus(outfitId: Long, isFavorite: Boolean) {
        viewModelScope.launch {
            outfitRepository.updateFavoriteStatus(outfitId, isFavorite)
            val msg = if (isFavorite) "Añadido a Favoritos" else "Quitado de Favoritos"
            _uiState.update { it.copy(snackbarMessage = msg) }
        }
    }

    fun logFavoriteAsWornToday(outfit: Outfit, rating: Float, notes: String) {
        viewModelScope.launch {
            val loggedOutfit = LoggedOutfit(
                outfitId = outfit.id,
                clothingItemIds = outfit.clothingItemIds,
                dateUsed = System.currentTimeMillis(),
                rating = rating,
                notes = notes
            )
            loggedOutfitRepository.insertLoggedOutfit(loggedOutfit)
            _uiState.update { it.copy(snackbarMessage = "¡Outfit registrado en tu Diario!") }
        }
    }

    fun logCustomOutfit(selectedItemIds: List<Long>, rating: Float, notes: String) {
        if (selectedItemIds.isEmpty()) return
        viewModelScope.launch {
            val loggedOutfit = LoggedOutfit(
                outfitId = null,
                clothingItemIds = selectedItemIds,
                dateUsed = System.currentTimeMillis(),
                rating = rating,
                notes = notes
            )
            loggedOutfitRepository.insertLoggedOutfit(loggedOutfit)
            _uiState.update {
                it.copy(
                    showManualLogDialog = false,
                    snackbarMessage = "¡Outfit manual registrado en tu Diario!"
                )
            }
        }
    }

    fun setShowManualLogDialog(show: Boolean) {
        _uiState.update { it.copy(showManualLogDialog = show) }
    }

    fun dismissSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}

class LogAndFavoritesViewModelFactory(
    private val loggedOutfitRepository: LoggedOutfitRepository,
    private val outfitRepository: OutfitRepository,
    private val clothingRepository: ClothingRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LogAndFavoritesViewModel::class.java)) {
            return LogAndFavoritesViewModel(
                loggedOutfitRepository = loggedOutfitRepository,
                outfitRepository = outfitRepository,
                clothingRepository = clothingRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
