package com.example.myoutfit.ui.profile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myoutfit.data.local.entity.ClothingItem
import com.example.myoutfit.data.local.entity.LoggedOutfit
import com.example.myoutfit.data.local.preferences.UserPreferencesRepository
import com.example.myoutfit.data.remote.social.BlockedUserDto
import com.example.myoutfit.data.remote.social.CalendarEntryDto
import com.example.myoutfit.data.remote.social.SocialSession
import com.example.myoutfit.data.remote.social.YearlyRecapDto
import com.example.myoutfit.data.repository.ClothingRepository
import com.example.myoutfit.data.repository.LoggedOutfitRepository
import com.example.myoutfit.data.repository.OutfitRepository
import com.example.myoutfit.data.repository.SocialRepository
import com.example.myoutfit.data.repository.WishlistRepository
import com.example.myoutfit.util.ImageStorageManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

data class ProfileUiState(
    val userName: String = "Joel Molina",
    val accountEmail: String = "",
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
    val isUsernameDialogOpen: Boolean = false,
    val isUpdatingUsername: Boolean = false,
    val usernameDialogError: String? = null,
    val canChangeUsername: Boolean = true,
    val nextUsernameChangeAt: String? = null,
    val usernameCooldownDaysRemaining: Int = 0,
    val isPrivateAccount: Boolean = false,
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val currentStreak: Int = 0,
    val isApiKeyDialogOpen: Boolean = false,
    val userMessage: String? = null,
    val calendarYear: Int = YearMonth.now().year,
    val calendarMonth: Int = YearMonth.now().monthValue,
    val calendarDayEntries: Map<Int, CalendarDayOutfitUi> = emptyMap(),
    val yearlyRecap: YearlyRecapDto? = null,
    val isSocialConnected: Boolean = false,
    val isExportingGdprData: Boolean = false,
    val isDeletingGdprAccount: Boolean = false
)

private data class ProfileStats(
    val loggedOutfits: List<LoggedOutfit>,
    val clothes: List<ClothingItem>,
    val loggedCount: Int,
    val savedCount: Int,
    val clothesCount: Int,
    val wishlistCount: Int,
    val favoriteCount: Int
)

private data class DialogState(
    val isEditOpen: Boolean,
    val isUsernameOpen: Boolean,
    val isUpdatingUsername: Boolean,
    val usernameError: String?,
    val isApiKeyOpen: Boolean,
    val userMessage: String?,
    val isExportingGdprData: Boolean,
    val isDeletingGdprAccount: Boolean
)

private data class CalendarCloudData(
    val yearMonth: YearMonth,
    val cloudEntries: List<CalendarEntryDto>,
    val recap: YearlyRecapDto?,
    val session: SocialSession
)

class ProfileViewModel(
    private val userPreferencesRepository: UserPreferencesRepository,
    loggedOutfitRepository: LoggedOutfitRepository,
    outfitRepository: OutfitRepository,
    clothingRepository: ClothingRepository,
    wishlistRepository: WishlistRepository,
    private val socialRepository: SocialRepository? = null
) : ViewModel() {

    private val _isEditDialogOpen = MutableStateFlow(false)
    private val _isUsernameDialogOpen = MutableStateFlow(false)
    private val _isUpdatingUsername = MutableStateFlow(false)
    private val _usernameDialogError = MutableStateFlow<String?>(null)
    private val _isApiKeyDialogOpen = MutableStateFlow(false)
    private val _userMessage = MutableStateFlow<String?>(null)
    private val _isExportingGdprData = MutableStateFlow(false)
    private val _isDeletingGdprAccount = MutableStateFlow(false)

    private val _blockedUsers = MutableStateFlow<List<BlockedUserDto>>(emptyList())
    val blockedUsers: StateFlow<List<BlockedUserDto>> = _blockedUsers

    private val _isLoadingBlockedUsers = MutableStateFlow(false)
    val isLoadingBlockedUsers: StateFlow<Boolean> = _isLoadingBlockedUsers

    private val _selectedYearMonth = MutableStateFlow(YearMonth.now())
    private val _cloudCalendarEntries = MutableStateFlow<List<CalendarEntryDto>>(emptyList())
    private val _cloudYearlyRecap = MutableStateFlow<YearlyRecapDto?>(null)

    val sessionState: StateFlow<SocialSession> =
        socialRepository?.sessionState ?: MutableStateFlow(SocialSession())
    private val sessionFlow: Flow<SocialSession> = sessionState

    init {
        // Sincronizar datos del perfil de cuenta automáticamente al iniciar sesión
        viewModelScope.launch {
            sessionFlow
                .map { it.userId to it.isAuthenticated }
                .distinctUntilChanged()
                .collect { (_, isAuth) ->
                    if (isAuth && socialRepository != null) {
                        socialRepository.refreshMyProfile()
                            .onSuccess { me ->
                                if (me.username.isNotBlank()) {
                                    userPreferencesRepository.updateUserName(me.username)
                                }
                                if (me.bio.isNotBlank()) {
                                    userPreferencesRepository.updateBio(me.bio)
                                }
                            }
                    }
                }
        }

        // Sincronizar calendario mensual y resumen anual
        viewModelScope.launch {
            combine(_selectedYearMonth, sessionFlow) { ym, session -> ym to session }
                .collect { (ym, session) ->
                    if (session.isAuthenticated && socialRepository != null) {
                        socialRepository.getMonthlyCalendar(ym.year, ym.monthValue)
                            .onSuccess { res -> _cloudCalendarEntries.value = res.entries }
                        socialRepository.getYearlyRecap(ym.year)
                            .onSuccess { recap -> _cloudYearlyRecap.value = recap }
                    } else {
                        _cloudCalendarEntries.value = emptyList()
                        _cloudYearlyRecap.value = null
                    }
                }
        }
    }

    fun syncAccountProfile() {
        val repo = socialRepository ?: return
        if (!repo.sessionState.value.isAuthenticated) return
        viewModelScope.launch {
            repo.refreshMyProfile()
        }
    }

    private val statsFlow: Flow<ProfileStats> = combine(
        loggedOutfitRepository.getAllLoggedOutfits(),
        outfitRepository.getAllOutfits(),
        clothingRepository.getAllClothingItems(),
        wishlistRepository.getAllWishlistItems()
    ) { loggedOutfits, outfits, clothes, wishlist ->
        ProfileStats(
            loggedOutfits = loggedOutfits,
            clothes = clothes,
            loggedCount = loggedOutfits.size,
            savedCount = outfits.size,
            clothesCount = clothes.size,
            wishlistCount = wishlist.size,
            favoriteCount = outfits.count { it.isFavorite }
        )
    }

    private val dialogsFlow: Flow<DialogState> = combine(
        combine(_isEditDialogOpen, _isUsernameDialogOpen, _isUpdatingUsername, _usernameDialogError) { a, b, c, d ->
            listOf(a, b, c, d)
        },
        combine(_isApiKeyDialogOpen, _userMessage, _isExportingGdprData, _isDeletingGdprAccount) { e, f, g, h ->
            listOf(e, f, g, h)
        }
    ) { left, right ->
        DialogState(
            isEditOpen = left[0] as Boolean,
            isUsernameOpen = left[1] as Boolean,
            isUpdatingUsername = left[2] as Boolean,
            usernameError = left[3] as String?,
            isApiKeyOpen = right[0] as Boolean,
            userMessage = right[1] as String?,
            isExportingGdprData = right[2] as Boolean,
            isDeletingGdprAccount = right[3] as Boolean
        )
    }

    private val calendarCloudFlow: Flow<CalendarCloudData> = combine(
        _selectedYearMonth,
        _cloudCalendarEntries,
        _cloudYearlyRecap,
        sessionFlow
    ) { ym, entries, recap, session ->
        CalendarCloudData(ym, entries, recap, session)
    }

    val uiState: StateFlow<ProfileUiState> = combine(
        userPreferencesRepository.userPreferencesFlow,
        statsFlow,
        dialogsFlow,
        calendarCloudFlow
    ) { prefs, stats, dialogs, cal ->
        val session = cal.session
        val mergedDays = buildMergedCalendarDays(
            yearMonth = cal.yearMonth,
            localLogged = stats.loggedOutfits,
            clothes = stats.clothes,
            cloudEntries = cal.cloudEntries,
            session = session
        )

        val resolvedName = if (session.isAuthenticated && session.username.isNotBlank()) {
            session.username
        } else {
            prefs.userName.ifBlank { "Joel Molina" }
        }

        val resolvedBio = if (session.isAuthenticated) {
            session.bio.ifBlank { prefs.bio }
        } else {
            prefs.bio
        }

        val resolvedAvatar = if (session.isAuthenticated) {
            val cloudAvatar = session.resolveMediaUrl(session.avatarUrl).orEmpty()
            prefs.profileImagePath.ifBlank { cloudAvatar }
        } else {
            prefs.profileImagePath
        }

        ProfileUiState(
            userName = resolvedName,
            accountEmail = if (session.isAuthenticated) session.email else "",
            profileImagePath = resolvedAvatar,
            bio = resolvedBio,
            loggedOutfitsCount = maxOf(stats.loggedCount, cal.recap?.totalOutfits ?: 0),
            savedOutfitsCount = stats.savedCount,
            closetItemsCount = stats.clothesCount,
            wishlistItemsCount = stats.wishlistCount,
            favoriteOutfitsCount = stats.favoriteCount,
            geminiApiKey = prefs.geminiApiKey,
            autoCategorizeWithAi = prefs.autoCategorizeWithAi,
            isEditDialogOpen = dialogs.isEditOpen,
            isUsernameDialogOpen = dialogs.isUsernameOpen,
            isUpdatingUsername = dialogs.isUpdatingUsername,
            usernameDialogError = dialogs.usernameError,
            canChangeUsername = if (session.isAuthenticated) session.canChangeUsername else true,
            nextUsernameChangeAt = if (session.isAuthenticated) session.nextUsernameChangeAt else null,
            usernameCooldownDaysRemaining = if (session.isAuthenticated) session.usernameCooldownDaysRemaining else 0,
            isPrivateAccount = session.isPrivate,
            followersCount = session.followersCount,
            followingCount = session.followingCount,
            currentStreak = session.currentStreak,
            isApiKeyDialogOpen = dialogs.isApiKeyOpen,
            userMessage = dialogs.userMessage,
            calendarYear = cal.yearMonth.year,
            calendarMonth = cal.yearMonth.monthValue,
            calendarDayEntries = mergedDays,
            yearlyRecap = cal.recap,
            isSocialConnected = session.isAuthenticated,
            isExportingGdprData = dialogs.isExportingGdprData,
            isDeletingGdprAccount = dialogs.isDeletingGdprAccount
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ProfileUiState()
    )

    fun previousCalendarMonth() {
        _selectedYearMonth.value = _selectedYearMonth.value.minusMonths(1)
    }

    fun nextCalendarMonth() {
        _selectedYearMonth.value = _selectedYearMonth.value.plusMonths(1)
    }

    fun openEditDialog() {
        _isEditDialogOpen.value = true
    }

    fun closeEditDialog() {
        _isEditDialogOpen.value = false
    }

    fun openChangeUsernameDialog() {
        _usernameDialogError.value = null
        _isUsernameDialogOpen.value = true
    }

    fun closeChangeUsernameDialog() {
        if (_isUpdatingUsername.value) return
        _usernameDialogError.value = null
        _isUsernameDialogOpen.value = false
    }

    fun changeAccountUsername(rawNewUsername: String) {
        val cleaned = rawNewUsername.trim().removePrefix("@")
        val validRegex = Regex("^[a-zA-Z0-9_.]{3,30}$")
        if (!validRegex.matches(cleaned)) {
            _usernameDialogError.value =
                "Usa entre 3 y 30 caracteres (solo letras, números, puntos y guiones bajos)."
            return
        }

        val repo = socialRepository
        val session = repo?.sessionState?.value
        if (repo == null || session == null || !session.isAuthenticated) {
            viewModelScope.launch {
                userPreferencesRepository.updateUserName(cleaned)
                _isUsernameDialogOpen.value = false
                _userMessage.value = "Nombre de perfil actualizado"
            }
            return
        }

        if (cleaned.equals(session.username, ignoreCase = false)) {
            _usernameDialogError.value = "Ese ya es tu nombre de perfil actual."
            return
        }

        viewModelScope.launch {
            _isUpdatingUsername.value = true
            _usernameDialogError.value = null
            repo.updateMyUsername(cleaned)
                .onSuccess { res ->
                    userPreferencesRepository.updateUserName(res.username)
                    _isUpdatingUsername.value = false
                    _isUsernameDialogOpen.value = false
                    _userMessage.value =
                        "Nombre cambiado a @${res.username}. Próximo cambio disponible en 30 días."
                }
                .onFailure { err ->
                    _isUpdatingUsername.value = false
                    _usernameDialogError.value =
                        err.message ?: "No se pudo cambiar el nombre de perfil."
                    repo.refreshMyProfile()
                }
        }
    }

    fun openApiKeyDialog() {
        _isApiKeyDialogOpen.value = true
    }

    fun closeApiKeyDialog() {
        _isApiKeyDialogOpen.value = false
    }

    fun updateProfile(name: String, bio: String) {
        viewModelScope.launch {
            val repo = socialRepository
            val session = repo?.sessionState?.value
            if (repo != null && session != null && session.isAuthenticated) {
                repo.updateMyProfile(bio = bio.trim())
                    .onSuccess {
                        userPreferencesRepository.updateBio(bio.trim())
                        _isEditDialogOpen.value = false
                        _userMessage.value = "Perfil sincronizado con tu cuenta"
                    }
                    .onFailure { err ->
                        _userMessage.value = err.message ?: "No se pudo sincronizar la descripción"
                    }
            } else {
                if (name.isNotBlank()) {
                    userPreferencesRepository.updateUserName(name.trim())
                }
                userPreferencesRepository.updateBio(bio.trim())
                _isEditDialogOpen.value = false
                _userMessage.value = "Perfil actualizado con éxito"
            }
        }
    }

    fun toggleAccountPrivacy(isPrivate: Boolean) {
        val repo = socialRepository ?: return
        if (!repo.sessionState.value.isAuthenticated) return
        viewModelScope.launch {
            repo.updateMyProfile(isPrivate = isPrivate)
                .onSuccess {
                    _userMessage.value = if (isPrivate) {
                        "Cuenta configurada como Privada"
                    } else {
                        "Cuenta configurada como Pública"
                    }
                }
                .onFailure { err ->
                    _userMessage.value = err.message ?: "No se pudo actualizar la privacidad"
                }
        }
    }

    fun updateProfilePhoto(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val savedPath = ImageStorageManager.saveUri(context, uri, "profile")
                if (!savedPath.isNullOrBlank()) {
                    userPreferencesRepository.updateProfileImagePath(savedPath)
                    val repo = socialRepository
                    if (repo != null && repo.sessionState.value.isAuthenticated) {
                        repo.uploadMyAvatar(context, savedPath)
                    }
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

    fun exportServerGdprData(context: Context, destinationUri: Uri) {
        val repo = socialRepository
        val session = repo?.sessionState?.value
        if (repo == null || session == null || !session.isAuthenticated) {
            _userMessage.value = "Debes iniciar sesión para exportar tus datos del servidor."
            return
        }

        viewModelScope.launch {
            _isExportingGdprData.value = true
            repo.exportMyGdprDataJson()
                .onSuccess { jsonString ->
                    try {
                        context.contentResolver.openOutputStream(destinationUri)?.use { out ->
                            out.write(jsonString.toByteArray(Charsets.UTF_8))
                            out.flush()
                        }
                        _isExportingGdprData.value = false
                        _userMessage.value = "Copia RGPD guardada con éxito (Art. 20 RGPD — Portabilidad)."
                    } catch (e: Exception) {
                        _isExportingGdprData.value = false
                        _userMessage.value = "Error al guardar el archivo: ${e.message}"
                    }
                }
                .onFailure { err ->
                    _isExportingGdprData.value = false
                    _userMessage.value = err.message ?: "No se pudo exportar la información del servidor."
                }
        }
    }

    fun deleteSocialAccount() {
        val repo = socialRepository
        val session = repo?.sessionState?.value
        if (repo == null || session == null || !session.isAuthenticated) {
            _userMessage.value = "No hay ninguna cuenta conectada al servidor."
            return
        }

        viewModelScope.launch {
            _isDeletingGdprAccount.value = true
            repo.deleteMyAccount()
                .onSuccess {
                    repo.logout()
                    _isDeletingGdprAccount.value = false
                    _userMessage.value = "Tu cuenta y fotos del servidor se han eliminado definitivamente (Art. 17 RGPD)."
                }
                .onFailure { err ->
                    _isDeletingGdprAccount.value = false
                    _userMessage.value = err.message ?: "No se pudo eliminar la cuenta del servidor."
                }
        }
    }

    fun loadBlockedUsers() {
        val repo = socialRepository ?: return
        if (!repo.sessionState.value.isAuthenticated) return
        viewModelScope.launch {
            _isLoadingBlockedUsers.value = true
            repo.getBlockedUsers()
                .onSuccess { res ->
                    _blockedUsers.value = res
                }
                .onFailure {
                    _userMessage.value = "Error al cargar la lista de usuarios bloqueados."
                }
            _isLoadingBlockedUsers.value = false
        }
    }

    fun unblockUser(targetUserId: String, targetUsername: String) {
        val repo = socialRepository ?: return
        viewModelScope.launch {
            repo.blockUser(targetUserId, block = false)
                .onSuccess {
                    _userMessage.value = "Se ha desbloqueado a @$targetUsername"
                    loadBlockedUsers()
                }
                .onFailure { err ->
                    _userMessage.value = err.message ?: "No se pudo desbloquear al usuario."
                }
        }
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    private fun buildMergedCalendarDays(
        yearMonth: YearMonth,
        localLogged: List<LoggedOutfit>,
        clothes: List<ClothingItem>,
        cloudEntries: List<CalendarEntryDto>,
        session: SocialSession
    ): Map<Int, CalendarDayOutfitUi> {
        val clothesMap = clothes.associateBy { it.id }
        val result = mutableMapOf<Int, CalendarDayOutfitUi>()

        // 1. Poblar primero con registros locales del Diario de Outfits
        localLogged.forEach { logged ->
            val localDate = Instant.ofEpochMilli(logged.dateUsed)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            if (localDate.year == yearMonth.year && localDate.monthValue == yearMonth.monthValue) {
                val firstCloth = logged.clothingItemIds.firstNotNullOfOrNull { clothesMap[it] }
                result.putIfAbsent(
                    localDate.dayOfMonth,
                    CalendarDayOutfitUi(
                        date = localDate.toString(),
                        thumbnailModel = firstCloth?.imagePath,
                        fullImageModel = firstCloth?.imagePath,
                        caption = logged.notes,
                        styleTags = firstCloth?.styleTags.orEmpty(),
                        isSyncedOnServer = false
                    )
                )
            }
        }

        // 2. Priorizar la bóveda privada en la nube (con fotos reales WebP de cada día)
        cloudEntries.forEach { entry ->
            val dayPart = entry.wornDate.takeLast(2).toIntOrNull()
            if (dayPart != null && (entry.isPrimaryOfDay || dayPart !in result)) {
                result[dayPart] = CalendarDayOutfitUi(
                    date = entry.wornDate,
                    thumbnailModel = session.resolveMediaUrl(entry.thumbnailUrl),
                    fullImageModel = session.resolveMediaUrl(entry.fullImageUrl),
                    caption = entry.caption,
                    styleTags = entry.styleTags,
                    isSyncedOnServer = true
                )
            }
        }

        return result
    }
}

class ProfileViewModelFactory(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val loggedOutfitRepository: LoggedOutfitRepository,
    private val outfitRepository: OutfitRepository,
    private val clothingRepository: ClothingRepository,
    private val wishlistRepository: WishlistRepository,
    private val socialRepository: SocialRepository? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
            return ProfileViewModel(
                userPreferencesRepository,
                loggedOutfitRepository,
                outfitRepository,
                clothingRepository,
                wishlistRepository,
                socialRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
