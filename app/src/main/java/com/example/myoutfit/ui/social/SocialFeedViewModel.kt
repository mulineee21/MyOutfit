package com.example.myoutfit.ui.social

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myoutfit.data.local.entity.LoggedOutfit
import com.example.myoutfit.data.remote.social.CommentDto
import com.example.myoutfit.data.remote.social.FeedPostDto
import com.example.myoutfit.data.remote.social.FollowRequestItemDto
import com.example.myoutfit.data.remote.social.SearchedUserDto
import com.example.myoutfit.data.remote.social.SocialSession
import com.example.myoutfit.data.remote.social.UserNotificationDto
import com.example.myoutfit.data.remote.social.UserProfileDto
import com.example.myoutfit.data.repository.LoggedOutfitRepository
import com.example.myoutfit.data.repository.SocialRepository
import com.example.myoutfit.util.OutfitNotificationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SocialFeedUiState(
    val session: SocialSession = SocialSession(),
    val isLoading: Boolean = false,
    val isAuthSubmitting: Boolean = false,
    val resetCodePendingEmail: String? = null,
    val passwordResetCompleted: Boolean = false,
    val isPublishing: Boolean = false,
    val hasPostedToday: Boolean = false,
    val discoverMode: Boolean = false,
    val posts: List<FeedPostDto> = emptyList(),
    val searchedUsers: List<SearchedUserDto> = emptyList(),
    val pendingRequests: List<FollowRequestItemDto> = emptyList(),
    val notifications: List<UserNotificationDto> = emptyList(),
    val isSearchingUsers: Boolean = false,
    val activeCommentsPost: FeedPostDto? = null,
    val activeComments: List<CommentDto> = emptyList(),
    val isLoadingComments: Boolean = false,
    val selectedUserProfile: UserProfileDto? = null,
    val isLoadingUserProfile: Boolean = false,
    val userMessage: String? = null
)

class SocialFeedViewModel(
    private val socialRepository: SocialRepository,
    private val loggedOutfitRepository: LoggedOutfitRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SocialFeedUiState(session = socialRepository.sessionState.value)
    )
    val uiState: StateFlow<SocialFeedUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            socialRepository.sessionState.collect { session ->
                val wasAuth = _uiState.value.session.isAuthenticated
                _uiState.update { it.copy(session = session) }
                if (session.isAuthenticated && !wasAuth) {
                    refreshFeed()
                    loadFollowRequests()
                } else if (!session.isAuthenticated && wasAuth) {
                    _uiState.update {
                        it.copy(
                            posts = emptyList(),
                            pendingRequests = emptyList(),
                            searchedUsers = emptyList(),
                            hasPostedToday = false
                        )
                    }
                }
            }
        }
        if (socialRepository.sessionState.value.isAuthenticated) {
            refreshFeed()
            loadFollowRequests()
        }
    }

    fun login(identifier: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAuthSubmitting = true, userMessage = null) }
            val result = socialRepository.login(identifier, password)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isAuthSubmitting = false,
                            userMessage = "¡Bienvenido de vuelta!"
                        )
                    }
                    refreshFeed()
                    loadFollowRequests()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isAuthSubmitting = false,
                            userMessage = err.message ?: "No se pudo iniciar sesión. Revisa tus datos."
                        )
                    }
                }
            )
        }
    }

    fun loginWithGoogle(idToken: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAuthSubmitting = true, userMessage = null) }
            val result = socialRepository.loginWithGoogle(idToken)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isAuthSubmitting = false,
                            userMessage = "¡Sesión iniciada con Google!"
                        )
                    }
                    refreshFeed()
                    loadFollowRequests()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isAuthSubmitting = false,
                            userMessage = err.message ?: "No se pudo iniciar sesión con Google."
                        )
                    }
                }
            )
        }
    }

    fun register(
        username: String,
        email: String,
        password: String,
        displayName: String,
        isPrivate: Boolean
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAuthSubmitting = true, userMessage = null) }
            val result = socialRepository.register(
                username = username,
                email = email,
                password = password,
                displayName = displayName,
                isPrivate = isPrivate
            )
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isAuthSubmitting = false,
                            userMessage = "¡Bienvenido a MyOutfit!"
                        )
                    }
                    refreshFeed()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isAuthSubmitting = false,
                            userMessage = err.message ?: "No se pudo crear la cuenta."
                        )
                    }
                }
            )
        }
    }

    fun requestPasswordReset(email: String) {
        viewModelScope.launch {
            val trimmedEmail = email.trim()
            _uiState.update { it.copy(isAuthSubmitting = true, userMessage = null) }
            socialRepository.requestPasswordReset(trimmedEmail).fold(
                onSuccess = { message ->
                    _uiState.update {
                        it.copy(
                            isAuthSubmitting = false,
                            resetCodePendingEmail = trimmedEmail,
                            userMessage = message
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isAuthSubmitting = false,
                            userMessage = err.message ?: "No se pudo solicitar el código de recuperación."
                        )
                    }
                }
            )
        }
    }

    fun confirmPasswordReset(email: String, code: String, newPassword: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAuthSubmitting = true, userMessage = null) }
            socialRepository.confirmPasswordReset(email, code, newPassword).fold(
                onSuccess = { message ->
                    _uiState.update {
                        it.copy(
                            isAuthSubmitting = false,
                            resetCodePendingEmail = null,
                            passwordResetCompleted = true,
                            userMessage = message
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isAuthSubmitting = false,
                            userMessage = err.message ?: "Código inválido o caducado."
                        )
                    }
                }
            )
        }
    }

    fun cancelPasswordReset() {
        _uiState.update { it.copy(resetCodePendingEmail = null) }
    }

    fun consumePasswordResetCompleted() {
        _uiState.update { it.copy(passwordResetCompleted = false) }
    }

    fun showMessage(message: String) {
        _uiState.update { it.copy(userMessage = message) }
    }

    fun logout() {
        viewModelScope.launch {
            socialRepository.logout()
            _uiState.update { it.copy(userMessage = "Sesión cerrada") }
        }
    }

    fun setDiscoverMode(discover: Boolean) {
        if (_uiState.value.discoverMode == discover) return
        _uiState.update { it.copy(discoverMode = discover) }
        refreshFeed()
    }

    fun refreshFeed() {
        if (!socialRepository.sessionState.value.isAuthenticated) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val discover = _uiState.value.discoverMode
            socialRepository.getFeed(discover = discover).fold(
                onSuccess = { feed ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            hasPostedToday = feed.hasPostedToday,
                            posts = feed.items
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            userMessage = "No se pudo cargar el feed: ${err.message ?: "revisa tu conexión"}"
                        )
                    }
                }
            )
        }
    }

    fun publishDailyOutfit(
        context: Context,
        imageUriOrPath: String,
        caption: String,
        sourceType: String,
        visibility: String,
        styleTags: List<String>
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isPublishing = true) }
            socialRepository.publishDailyOutfit(
                context = context,
                imageUriOrPath = imageUriOrPath,
                caption = caption,
                sourceType = sourceType,
                visibility = visibility,
                styleTags = styleTags
            ).fold(
                onSuccess = { createdPost ->
                    OutfitNotificationHelper.scheduleExpiration(
                        context = context,
                        expiresAtIso = createdPost.expiresAt,
                        caption = caption
                    )
                    // Sincronizar también con el diario local para mantener contadores locales al día
                    runCatching {
                        loggedOutfitRepository?.insertLoggedOutfit(
                            LoggedOutfit(
                                rating = 5.0f,
                                notes = if (caption.isNotBlank()) "[BeReal] $caption" else "Outfit diario compartido"
                            )
                        )
                    }
                    _uiState.update {
                        it.copy(
                            isPublishing = false,
                            hasPostedToday = true,
                            userMessage = "¡Outfit publicado por 24h y guardado en tu Calendario Privado!"
                        )
                    }
                    refreshFeed()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isPublishing = false,
                            userMessage = "Error al publicar outfit: ${err.message ?: "inténtalo de nuevo"}"
                        )
                    }
                }
            )
        }
    }

    fun toggleReaction(post: FeedPostDto, emoji: String) {
        viewModelScope.launch {
            val removing = post.myReaction == emoji
            // Actualización optimista inmediata
            _uiState.update { state ->
                state.copy(
                    posts = state.posts.map { item ->
                        if (item.postId != post.postId) item
                        else if (removing) {
                            item.copy(
                                myReaction = null,
                                reactionsCount = (item.reactionsCount - 1).coerceAtLeast(0)
                            )
                        } else {
                            val delta = if (item.myReaction == null) 1 else 0
                            item.copy(
                                myReaction = emoji,
                                reactionsCount = item.reactionsCount + delta
                            )
                        }
                    }
                )
            }

            val res = if (removing) {
                socialRepository.removeReaction(post.postId)
            } else {
                socialRepository.reactToPost(post.postId, emoji)
            }
            if (res.isFailure) {
                refreshFeed()
            }
        }
    }

    fun openComments(post: FeedPostDto) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    activeCommentsPost = post,
                    activeComments = emptyList(),
                    isLoadingComments = true
                )
            }
            socialRepository.getComments(post.postId).fold(
                onSuccess = { list ->
                    _uiState.update {
                        it.copy(activeComments = list, isLoadingComments = false)
                    }
                },
                onFailure = {
                    _uiState.update {
                        it.copy(
                            isLoadingComments = false,
                            userMessage = "No se pudieron cargar los comentarios"
                        )
                    }
                }
            )
        }
    }

    fun closeComments() {
        _uiState.update { it.copy(activeCommentsPost = null, activeComments = emptyList()) }
    }

    fun addComment(content: String) {
        val targetPost = _uiState.value.activeCommentsPost ?: return
        if (content.isBlank()) return
        viewModelScope.launch {
            socialRepository.addComment(targetPost.postId, content).fold(
                onSuccess = {
                    openComments(targetPost)
                    refreshFeed()
                },
                onFailure = {
                    _uiState.update { it.copy(userMessage = "Error al enviar comentario") }
                }
            )
        }
    }

    fun searchUsers(query: String) {
        if (query.trim().length < 2) {
            _uiState.update { it.copy(searchedUsers = emptyList(), isSearchingUsers = false) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSearchingUsers = true) }
            socialRepository.searchUsers(query).fold(
                onSuccess = { users ->
                    _uiState.update {
                        it.copy(searchedUsers = users, isSearchingUsers = false)
                    }
                },
                onFailure = {
                    _uiState.update { it.copy(isSearchingUsers = false) }
                }
            )
        }
    }

    fun toggleFollowUser(user: SearchedUserDto) {
        toggleFollowUser(
            targetUserId = user.id,
            targetUsername = user.username,
            currentFollowStatus = user.followStatus,
            isTargetPrivate = user.isPrivate
        )
    }

    fun toggleFollowUser(
        targetUserId: String,
        targetUsername: String,
        currentFollowStatus: String,
        isTargetPrivate: Boolean
    ) {
        viewModelScope.launch {
            val shouldFollow = currentFollowStatus != "ACCEPTED" && currentFollowStatus != "PENDING"
            socialRepository.toggleFollow(targetUserId, follow = shouldFollow)
                .onSuccess { newStatus ->
                    val feedback = when (newStatus) {
                        "PENDING" ->
                            "Solicitud enviada a @$targetUsername. Al ser cuenta privada, debe aceptar tu solicitud."
                        "ACCEPTED" ->
                            "¡Ahora sigues a @$targetUsername!"
                        "NONE" ->
                            "Has dejado de seguir a @$targetUsername."
                        else ->
                            "Estado de seguimiento actualizado."
                    }
                    _uiState.update { state ->
                        state.copy(
                            userMessage = feedback,
                            searchedUsers = state.searchedUsers.map {
                                if (it.id == targetUserId) it.copy(followStatus = newStatus) else it
                            },
                            posts = state.posts.map { post ->
                                if (post.author.id == targetUserId) {
                                    post.copy(author = post.author.copy(followStatus = newStatus))
                                } else post
                            },
                            selectedUserProfile = state.selectedUserProfile?.let { p ->
                                if (p.id == targetUserId) {
                                    val deltaFollowers = when {
                                        newStatus == "ACCEPTED" && currentFollowStatus != "ACCEPTED" -> 1
                                        newStatus != "ACCEPTED" && currentFollowStatus == "ACCEPTED" -> -1
                                        else -> 0
                                    }
                                    p.copy(
                                        followStatus = newStatus,
                                        followersCount = (p.followersCount + deltaFollowers).coerceAtLeast(0)
                                    )
                                } else p
                            }
                        )
                    }
                    refreshFeed()
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(userMessage = err.message ?: "No se pudo actualizar el seguimiento.")
                    }
                }
        }
    }

    fun loadFollowRequests() {
        if (!socialRepository.sessionState.value.isAuthenticated) return
        viewModelScope.launch {
            socialRepository.getPendingFollowRequests().onSuccess { reqs ->
                _uiState.update { it.copy(pendingRequests = reqs) }
            }
            socialRepository.getNotifications().onSuccess { notifs ->
                _uiState.update { it.copy(notifications = notifs) }
            }
        }
    }

    fun scheduleExpirationIfNeeded(context: Context) {
        val myPost = _uiState.value.posts.firstOrNull { it.author.id == _uiState.value.session.userId }
        if (myPost != null) {
            OutfitNotificationHelper.scheduleExpiration(context, myPost.expiresAt, myPost.caption)
        }
    }

    fun respondToFollowRequest(followerId: String, accept: Boolean) {
        viewModelScope.launch {
            socialRepository.respondFollowRequest(followerId, accept)
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            pendingRequests = state.pendingRequests.filterNot { it.userId == followerId },
                            userMessage = if (accept) {
                                "Solicitud aceptada. Ahora puede ver tus outfits de 24h."
                            } else {
                                "Solicitud rechazada."
                            }
                        )
                    }
                    refreshFeed()
                    socialRepository.refreshMyProfile()
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(userMessage = err.message ?: "No se pudo responder a la solicitud.")
                    }
                }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    fun deleteMyPost(postId: String, purgeArchive: Boolean = true) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            socialRepository.deletePost(postId, purgeArchive).fold(
                onSuccess = { msg ->
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            posts = state.posts.filterNot { it.postId == postId },
                            hasPostedToday = state.posts.filterNot { it.postId == postId }.none { it.author.id == state.session.userId },
                            userMessage = msg.ifBlank { "Fotografía eliminada permanentemente (Art. 17 RGPD)." }
                        )
                    }
                    refreshFeed()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            userMessage = err.message ?: "No se pudo eliminar la fotografía."
                        )
                    }
                }
            )
        }
    }

    fun reportPost(postId: String, reason: String, details: String = "") {
        viewModelScope.launch {
            socialRepository.reportPost(postId, reason, details).fold(
                onSuccess = { msg ->
                    _uiState.update { state ->
                        state.copy(
                            posts = state.posts.filterNot { it.postId == postId },
                            userMessage = msg.ifBlank { "Publicación denunciada conforme a la DSA. Gracias por avisarnos." }
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            userMessage = err.message ?: "No se pudo enviar la denuncia."
                        )
                    }
                }
            )
        }
    }

    fun blockUser(targetUserId: String, targetUsername: String, block: Boolean = true) {
        viewModelScope.launch {
            socialRepository.blockUser(targetUserId, block).fold(
                onSuccess = {
                    _uiState.update { state ->
                        state.copy(
                            posts = state.posts.filterNot { it.author.id == targetUserId },
                            searchedUsers = state.searchedUsers.filterNot { it.id == targetUserId },
                            selectedUserProfile = if (state.selectedUserProfile?.id == targetUserId) {
                                state.selectedUserProfile.copy(
                                    isBlockedByMe = block,
                                    followStatus = if (block) "BLOCKED" else "NONE",
                                    canViewPosts = !block,
                                    activePost = if (block) null else state.selectedUserProfile.activePost
                                )
                            } else state.selectedUserProfile,
                            userMessage = if (block) {
                                "Has bloqueado a @$targetUsername. No verás sus publicaciones ni podrá interactuar contigo."
                            } else {
                                "Has desbloqueado a @$targetUsername."
                            }
                        )
                    }
                    refreshFeed()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(userMessage = err.message ?: "No se pudo actualizar el bloqueo.")
                    }
                }
            )
        }
    }

    fun openUserProfile(userId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingUserProfile = true) }
            socialRepository.getUserProfile(userId).fold(
                onSuccess = { profile ->
                    _uiState.update {
                        it.copy(
                            selectedUserProfile = profile,
                            isLoadingUserProfile = false
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoadingUserProfile = false,
                            userMessage = "No se pudo cargar el perfil: ${err.message ?: "inténtalo de nuevo"}"
                        )
                    }
                }
            )
        }
    }

    fun closeUserProfile() {
        _uiState.update { it.copy(selectedUserProfile = null, isLoadingUserProfile = false) }
    }
}

class SocialFeedViewModelFactory(
    private val socialRepository: SocialRepository,
    private val loggedOutfitRepository: LoggedOutfitRepository? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SocialFeedViewModel::class.java)) {
            return SocialFeedViewModel(socialRepository, loggedOutfitRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
