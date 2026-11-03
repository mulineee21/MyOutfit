package com.example.myoutfit.ui.profile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myoutfit.data.local.preferences.UserPreferencesRepository
import com.example.myoutfit.data.repository.ClothingRepository
import com.example.myoutfit.data.repository.LoggedOutfitRepository
import com.example.myoutfit.data.repository.OutfitRepository
import com.example.myoutfit.data.repository.WishlistRepository
import com.example.myoutfit.util.ImageStorageManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileUiState(
    val userName: String = "Joel Molina",
    val profileImagePath: String = "",
    val bio: String = "Mi armario digital & estilista personal",
    val loggedOutfitsCount: Int = 0,
    val savedOutfitsCount: Int = 0,
    val closetItemsCount: Int = 0,
    val wishlistItemsCount: Int = 0,
    val favoriteOutfitsCount: Int = 0,
    val geminiApiKey: String = "",
    val autoCategorizeWithAi: Boolean = true,
    val isEditDialogOpen: Boolean = false,
    val isApiKeyDialogOpen: Boolean = false,
    val userMessage: String? = null
)

private data class ProfileStats(
    val loggedCount: Int,
    val savedCount: Int,
    val clothesCount: Int,
    val wishlistCount: Int,
    val favoriteCount: Int
)

private data class DialogState(
    val isEditOpen: Boolean,
    val isApiKeyOpen: Boolean,
    val userMessage: String?
)

class ProfileViewModel(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val loggedOutfitRepository: LoggedOutfitRepository,
    private val outfitRepository: OutfitRepository,
    private val clothingRepository: ClothingRepository,
    private val wishlistRepository: WishlistRepository
) : ViewModel() {

    private val _isEditDialogOpen = MutableStateFlow(false)
    private val _isApiKeyDialogOpen = MutableStateFlow(false)
    private val _userMessage = MutableStateFlow<String?>(null)

    private val statsFlow: Flow<ProfileStats> = combine(
        loggedOutfitRepository.getAllLoggedOutfits(),
        outfitRepository.getAllOutfits(),
        clothingRepository.getAllClothingItems(),
        wishlistRepository.getAllWishlistItems()
    ) { loggedOutfits, outfits, clothes, wishlist ->
        ProfileStats(
            loggedCount = loggedOutfits.size,
            savedCount = outfits.size,
            clothesCount = clothes.size,
            wishlistCount = wishlist.size,
            favoriteCount = outfits.count { it.isFavorite }
        )
    }

    private val dialogsFlow: Flow<DialogState> = combine(
        _isEditDialogOpen,
        _isApiKeyDialogOpen,
        _userMessage
    ) { editOpen, apiKeyOpen, msg ->
        DialogState(editOpen, apiKeyOpen, msg)
    }

    val uiState: StateFlow<ProfileUiState> = combine(
        userPreferencesRepository.userPreferencesFlow,
        statsFlow,
        dialogsFlow
    ) { prefs, stats, dialogs ->
        ProfileUiState(
            userName = prefs.userName.ifBlank { "Joel Molina" },
            profileImagePath = prefs.profileImagePath,
            bio = prefs.bio,
            loggedOutfitsCount = stats.loggedCount,
            savedOutfitsCount = stats.savedCount,
            closetItemsCount = stats.clothesCount,
            wishlistItemsCount = stats.wishlistCount,
            favoriteOutfitsCount = stats.favoriteCount,
            geminiApiKey = prefs.geminiApiKey,
            autoCategorizeWithAi = prefs.autoCategorizeWithAi,
            isEditDialogOpen = dialogs.isEditOpen,
            isApiKeyDialogOpen = dialogs.isApiKeyOpen,
            userMessage = dialogs.userMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ProfileUiState()
    )

    fun openEditDialog() {
        _isEditDialogOpen.value = true
    }

    fun closeEditDialog() {
        _isEditDialogOpen.value = false
    }

    fun openApiKeyDialog() {
        _isApiKeyDialogOpen.value = true
    }

    fun closeApiKeyDialog() {
        _isApiKeyDialogOpen.value = false
    }

    fun updateProfile(name: String, bio: String) {
        viewModelScope.launch {
            if (name.isNotBlank()) {
                userPreferencesRepository.updateUserName(name)
            }
            userPreferencesRepository.updateBio(bio)
            _isEditDialogOpen.value = false
            _userMessage.value = "Perfil actualizado con éxito"
        }
    }

    fun updateProfilePhoto(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val savedPath = ImageStorageManager.saveUri(context, uri, "profile")
                if (!savedPath.isNullOrBlank()) {
                    userPreferencesRepository.updateProfileImagePath(savedPath)
                    _userMessage.value = "Foto de perfil actualizada"
                } else {
                    _userMessage.value = "No se pudo guardar la imagen de perfil"
                }
            } catch (e: Exception) {
                _userMessage.value = "Error al actualizar la foto: ${e.message}"
            }
        }
    }

    fun removeProfilePhoto() {
        viewModelScope.launch {
            userPreferencesRepository.updateProfileImagePath("")
            _userMessage.value = "Foto de perfil eliminada"
        }
    }

    fun updateGeminiApiKey(apiKey: String) {
        viewModelScope.launch {
            userPreferencesRepository.updateGeminiApiKey(apiKey)
            _isApiKeyDialogOpen.value = false
            _userMessage.value = if (apiKey.isBlank()) "Clave de API eliminada" else "Clave de API guardada"
        }
    }

    fun toggleAutoCategorize(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.updateAutoCategorizeWithAi(enabled)
            _userMessage.value = if (enabled) "Autocategorización con IA activada" else "Autocategorización con IA desactivada"
        }
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }
}

class ProfileViewModelFactory(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val loggedOutfitRepository: LoggedOutfitRepository,
    private val outfitRepository: OutfitRepository,
    private val clothingRepository: ClothingRepository,
    private val wishlistRepository: WishlistRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
            return ProfileViewModel(
                userPreferencesRepository,
                loggedOutfitRepository,
                outfitRepository,
                clothingRepository,
                wishlistRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
