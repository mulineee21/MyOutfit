package com.example.myoutfit.ui.recommendation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myoutfit.data.local.entity.ClothingItem
import com.example.myoutfit.data.local.entity.InspirationItem
import com.example.myoutfit.data.local.entity.LoggedOutfit
import com.example.myoutfit.data.local.entity.Outfit
import com.example.myoutfit.data.local.preferences.UserPreferences
import com.example.myoutfit.data.local.preferences.UserPreferencesRepository
import com.example.myoutfit.data.repository.ClothingRepository
import com.example.myoutfit.data.repository.InspirationRepository
import com.example.myoutfit.data.repository.LoggedOutfitRepository
import com.example.myoutfit.data.repository.OutfitRepository
import com.example.myoutfit.domain.recommendation.GeminiOutfitService
import com.example.myoutfit.domain.recommendation.GeminiRecommendationRequest
import com.example.myoutfit.domain.recommendation.OutfitRecommendationEngine
import com.example.myoutfit.domain.recommendation.RecommendationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecommendationUiState(
    val clothingItems: List<ClothingItem> = emptyList(),
    val inspirationItems: List<InspirationItem> = emptyList(),
    val userPreferences: UserPreferences = UserPreferences(),
    val selectedStyleFilter: String = "Sorpréndeme",
    val currentRecommendation: RecommendationResult? = null,
    val isLoading: Boolean = false,
    val isAiLoading: Boolean = false,
    val activityPlan: String = "",
    val desiredVibe: String = "",
    val useInspirations: Boolean = true,
    val showApiKeyDialog: Boolean = false,
    val showStylePreferencesDialog: Boolean = false,
    val showLogDialog: Boolean = false,
    val snackbarMessage: String? = null
)

class RecommendationViewModel(
    private val clothingRepository: ClothingRepository,
    private val inspirationRepository: InspirationRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val outfitRepository: OutfitRepository,
    private val loggedOutfitRepository: LoggedOutfitRepository,
    private val engine: OutfitRecommendationEngine = OutfitRecommendationEngine(),
    private val geminiService: GeminiOutfitService = GeminiOutfitService()
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecommendationUiState())
    val uiState: StateFlow<RecommendationUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                clothingRepository.getAllClothingItems(),
                inspirationRepository.getAllInspirationItems(),
                userPreferencesRepository.userPreferencesFlow
            ) { clothing, inspiration, prefs ->
                Triple(clothing, inspiration, prefs)
            }.collect { (clothing, inspiration, prefs) ->
                _uiState.update { state ->
                    val isFirstLoad = state.currentRecommendation == null
                    val newState = state.copy(
                        clothingItems = clothing,
                        inspirationItems = inspiration,
                        userPreferences = prefs
                    )

                    if (isFirstLoad && clothing.isNotEmpty()) {
                        val rec = engine.generateRecommendation(
                            clothingItems = clothing,
                            userPreferences = prefs,
                            inspirationItems = inspiration,
                            requestedStyle = state.selectedStyleFilter
                        )
                        newState.copy(currentRecommendation = rec)
                    } else if (state.currentRecommendation != null && !state.currentRecommendation.isAiGenerated) {
                        // Re-evaluate current local recommendation if items or preferences changed
                        val rec = engine.generateRecommendation(
                            clothingItems = clothing,
                            userPreferences = prefs,
                            inspirationItems = inspiration,
                            requestedStyle = state.selectedStyleFilter,
                            seed = state.currentRecommendation.id.hashCode().toLong()
                        )
                        newState.copy(currentRecommendation = rec)
                    } else {
                        newState
                    }
                }
            }
        }
    }

    fun selectStyleFilter(style: String) {
        _uiState.update { it.copy(selectedStyleFilter = style) }
        generateNewRecommendation()
    }

    fun generateNewRecommendation() {
        val currentState = _uiState.value
        val rec = engine.generateRecommendation(
            clothingItems = currentState.clothingItems,
            userPreferences = currentState.userPreferences,
            inspirationItems = currentState.inspirationItems,
            requestedStyle = currentState.selectedStyleFilter,
            seed = System.currentTimeMillis()
        )
        _uiState.update { it.copy(currentRecommendation = rec) }
    }

    fun setActivityPlan(plan: String) {
        _uiState.update { it.copy(activityPlan = plan) }
    }

    fun setDesiredVibe(vibe: String) {
        _uiState.update { it.copy(desiredVibe = vibe) }
    }

    fun setUseInspirations(use: Boolean) {
        _uiState.update { it.copy(useInspirations = use) }
    }

    fun setShowApiKeyDialog(show: Boolean) {
        _uiState.update { it.copy(showApiKeyDialog = show) }
    }

    fun saveGeminiApiKey(apiKey: String) {
        viewModelScope.launch {
            userPreferencesRepository.updateGeminiApiKey(apiKey)
            _uiState.update {
                it.copy(
                    showApiKeyDialog = false,
                    snackbarMessage = "API Key de Gemini guardada correctamente"
                )
            }
        }
    }

    fun generateGeminiRecommendation() {
        val state = _uiState.value
        val apiKey = state.userPreferences.geminiApiKey

        if (apiKey.isBlank()) {
            _uiState.update {
                it.copy(
                    showApiKeyDialog = true,
                    snackbarMessage = "Introduce tu API Key de Gemini para activar las recomendaciones con IA"
                )
            }
            return
        }

        if (state.clothingItems.isEmpty()) {
            _uiState.update { it.copy(snackbarMessage = "Tu armario está vacío. Agrega prendas primero.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isAiLoading = true) }

            val request = GeminiRecommendationRequest(
                activityPlan = state.activityPlan,
                desiredVibe = state.desiredVibe,
                useInspirations = state.useInspirations,
                availableItems = state.clothingItems,
                inspirationItems = state.inspirationItems,
                apiKey = apiKey
            )

            val result = geminiService.recommendOutfit(request)

            result.onSuccess { geminiResult ->
                android.util.Log.d("RecommendationViewModel", "geminiResult: title=${geminiResult.title}, selectedItemIds=${geminiResult.selectedItemIds}")
                android.util.Log.d("RecommendationViewModel", "Armario disponible (${state.clothingItems.size} prendas): ${state.clothingItems.map { "${it.id}: ${it.category.displayName} (${it.color})" }}")

                var selectedItems = state.clothingItems.filter { it.id in geminiResult.selectedItemIds }

                if (selectedItems.isEmpty()) {
                    android.util.Log.w("RecommendationViewModel", "IDs de Gemini no coincidieron directamente con el armario. Usando fallback con prendas del armario.")
                    val top = state.clothingItems.firstOrNull { it.category.isTop }
                    val bottom = state.clothingItems.firstOrNull { it.category.isBottom }
                    val onePiece = state.clothingItems.firstOrNull { it.category.isOnePiece }
                    val shoes = state.clothingItems.firstOrNull { it.category.isFootwear }

                    selectedItems = when {
                        onePiece != null -> listOfNotNull(onePiece, shoes)
                        top != null && bottom != null -> listOfNotNull(top, bottom, shoes)
                        else -> state.clothingItems.take(2)
                    }
                }

                val recResult = RecommendationResult(
                    id = System.currentTimeMillis().toString(),
                    title = geminiResult.title,
                    items = selectedItems,
                    styleTag = geminiResult.styleTag,
                    matchPercentage = geminiResult.matchPercentage,
                    matchReason = geminiResult.matchReason,
                    isSufficientItems = true,
                    isAiGenerated = true,
                    styleAdvice = geminiResult.styleAdvice
                )

                _uiState.update {
                    it.copy(
                        currentRecommendation = recResult,
                        isAiLoading = false,
                        snackbarMessage = "✨ ¡Outfit generado con éxito por Gemini!"
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isAiLoading = false,
                        snackbarMessage = "Error con Gemini: ${error.localizedMessage ?: "Ocurrió un error inesperado"}"
                    )
                }
            }
        }
    }

    fun saveCurrentToFavorites() {
        val currentRec = _uiState.value.currentRecommendation ?: return
        if (!currentRec.isSufficientItems || currentRec.items.isEmpty()) return

        viewModelScope.launch {
            val outfit = Outfit(
                title = currentRec.title,
                clothingItemIds = currentRec.items.map { it.id },
                styleTag = currentRec.styleTag,
                isFavorite = true,
                dateCreated = System.currentTimeMillis()
            )
            outfitRepository.insertOutfit(outfit)
            _uiState.update { it.copy(snackbarMessage = "¡Outfit guardado en Favoritos!") }
        }
    }

    fun logCurrentAsWorn(rating: Float, notes: String) {
        val currentRec = _uiState.value.currentRecommendation ?: return
        if (!currentRec.isSufficientItems || currentRec.items.isEmpty()) return

        viewModelScope.launch {
            val outfitId = outfitRepository.insertOutfit(
                Outfit(
                    title = currentRec.title,
                    clothingItemIds = currentRec.items.map { it.id },
                    styleTag = currentRec.styleTag,
                    isFavorite = false
                )
            )

            val loggedOutfit = LoggedOutfit(
                outfitId = outfitId,
                clothingItemIds = currentRec.items.map { it.id },
                dateUsed = System.currentTimeMillis(),
                rating = rating,
                notes = notes
            )
            loggedOutfitRepository.insertLoggedOutfit(loggedOutfit)
            _uiState.update {
                it.copy(
                    showLogDialog = false,
                    snackbarMessage = "¡Outfit registrado en tu Diario!"
                )
            }
        }
    }

    fun updateFavoriteStyles(newStyles: Set<String>) {
        viewModelScope.launch {
            userPreferencesRepository.updateFavoriteStyles(newStyles)
            _uiState.update { it.copy(showStylePreferencesDialog = false) }
            generateNewRecommendation()
        }
    }

    fun setShowStylePreferencesDialog(show: Boolean) {
        _uiState.update { it.copy(showStylePreferencesDialog = show) }
    }

    fun setShowLogDialog(show: Boolean) {
        _uiState.update { it.copy(showLogDialog = show) }
    }

    fun dismissSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}

class RecommendationViewModelFactory(
    private val clothingRepository: ClothingRepository,
    private val inspirationRepository: InspirationRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val outfitRepository: OutfitRepository,
    private val loggedOutfitRepository: LoggedOutfitRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RecommendationViewModel::class.java)) {
            return RecommendationViewModel(
                clothingRepository = clothingRepository,
                inspirationRepository = inspirationRepository,
                userPreferencesRepository = userPreferencesRepository,
                outfitRepository = outfitRepository,
                loggedOutfitRepository = loggedOutfitRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
