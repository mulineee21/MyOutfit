package com.example.myoutfit.data.repository

import android.content.Context
import android.net.Uri
import com.example.myoutfit.data.remote.social.CalendarMonthResponseDto
import com.example.myoutfit.data.remote.social.CommentDto
import com.example.myoutfit.data.remote.social.CreateCommentRequestDto
import com.example.myoutfit.data.remote.social.CreatedPostDto
import com.example.myoutfit.data.remote.social.FeedResponseDto
import com.example.myoutfit.data.remote.social.FollowActionRequestDto
import com.example.myoutfit.data.remote.social.FollowDecisionRequestDto
import com.example.myoutfit.data.remote.social.FollowRequestItemDto
import com.example.myoutfit.data.remote.social.ForgotPasswordRequestDto
import com.example.myoutfit.data.remote.social.GoogleAuthRequestDto
import com.example.myoutfit.data.remote.social.LoginRequestDto
import com.example.myoutfit.data.remote.social.LogoutRequestDto
import com.example.myoutfit.data.remote.social.MeProfileDto
import com.example.myoutfit.data.remote.social.BlockUserRequestDto
import com.example.myoutfit.data.remote.social.BlockedUserDto
import com.example.myoutfit.data.remote.social.ReactionRequestDto
import com.example.myoutfit.data.remote.social.RegisterRequestDto
import com.example.myoutfit.data.remote.social.ReportPostRequestDto
import com.example.myoutfit.data.remote.social.ResetPasswordRequestDto
import com.example.myoutfit.data.remote.social.SearchedUserDto
import com.example.myoutfit.data.remote.social.SocialApiService
import com.example.myoutfit.data.remote.social.SocialSession
import com.example.myoutfit.data.remote.social.SocialSessionManager
import com.example.myoutfit.data.remote.social.UpdateProfileRequestDto
import com.example.myoutfit.data.remote.social.UpdateUsernameRequestDto
import com.example.myoutfit.data.remote.social.UpdateUsernameResponseDto
import com.example.myoutfit.data.remote.social.UpdatedProfileDto
import com.example.myoutfit.data.remote.social.UserNotificationDto
import com.example.myoutfit.data.remote.social.UserProfileDto
import com.example.myoutfit.data.remote.social.YearlyRecapDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.File
import java.time.LocalDate

interface SocialRepository {
    val sessionState: StateFlow<SocialSession>
    suspend fun register(
        username: String,
        email: String,
        password: String,
        displayName: String,
        isPrivate: Boolean
    ): Result<Unit>

    suspend fun login(identifier: String, password: String): Result<Unit>
    suspend fun loginWithGoogle(idToken: String): Result<Unit>
    suspend fun requestPasswordReset(email: String): Result<String>
    suspend fun confirmPasswordReset(email: String, code: String, newPassword: String): Result<String>
    suspend fun logout()
    suspend fun getFeed(discover: Boolean = false): Result<FeedResponseDto>
    suspend fun publishDailyOutfit(
        context: Context,
        imageUriOrPath: String,
        caption: String,
        sourceType: String = "REAL_CAMERA",
        visibility: String = "FOLLOWERS",
        styleTags: List<String> = emptyList(),
        dominantColors: List<String> = emptyList(),
        wornDate: String = LocalDate.now().toString()
    ): Result<CreatedPostDto>

    suspend fun reactToPost(postId: String, emoji: String): Result<Unit>
    suspend fun removeReaction(postId: String): Result<Unit>
    suspend fun getComments(postId: String): Result<List<CommentDto>>
    suspend fun addComment(postId: String, content: String): Result<Unit>
    suspend fun getMonthlyCalendar(year: Int, month: Int): Result<CalendarMonthResponseDto>
    suspend fun getYearlyRecap(year: Int): Result<YearlyRecapDto>
    suspend fun searchUsers(query: String): Result<List<SearchedUserDto>>
    suspend fun getUserProfile(userId: String): Result<UserProfileDto>
    suspend fun toggleFollow(targetUserId: String, follow: Boolean): Result<String>
    suspend fun getPendingFollowRequests(): Result<List<FollowRequestItemDto>>
    suspend fun getNotifications(): Result<List<UserNotificationDto>>
    suspend fun respondFollowRequest(followerId: String, accept: Boolean): Result<Unit>
    suspend fun refreshMyProfile(): Result<MeProfileDto>
    suspend fun updateMyUsername(newUsername: String): Result<UpdateUsernameResponseDto>
    suspend fun updateMyProfile(
        displayName: String? = null,
        bio: String? = null,
        isPrivate: Boolean? = null
    ): Result<UpdatedProfileDto>
    suspend fun uploadMyAvatar(context: Context, imageUriOrPath: String): Result<String>
    suspend fun exportMyGdprDataJson(): Result<String>
    suspend fun deleteMyAccount(): Result<String>
    suspend fun deletePost(postId: String, purgeArchive: Boolean = true): Result<String>
    suspend fun reportPost(postId: String, reason: String, details: String = ""): Result<String>
    suspend fun blockUser(targetUserId: String, block: Boolean): Result<String>
    suspend fun getBlockedUsers(): Result<List<BlockedUserDto>>
}

class SocialRepositoryImpl(
    private val apiService: SocialApiService,
    private val sessionManager: SocialSessionManager
) : SocialRepository {

    override val sessionState: StateFlow<SocialSession> = sessionManager.sessionState

    override suspend fun register(
        username: String,
        email: String,
        password: String,
        displayName: String,
        isPrivate: Boolean
    ): Result<Unit> = runSocialCall {
        val res = apiService.register(
            RegisterRequestDto(
                username = username.trim().removePrefix("@"),
                email = email.trim(),
                password = password,
                displayName = displayName.trim(),
                isPrivate = isPrivate,
                acceptedPrivacyPolicy = true,
                confirmedAge14Plus = true
            )
        )
        sessionManager.saveAuthSession(res.accessToken, res.refreshToken, res.user)
    }

    override suspend fun login(identifier: String, password: String): Result<Unit> = runSocialCall {
        val res = apiService.login(
            LoginRequestDto(
                identifier = identifier.trim().removePrefix("@"),
                password = password
            )
        )
        sessionManager.saveAuthSession(res.accessToken, res.refreshToken, res.user)
    }

    override suspend fun loginWithGoogle(idToken: String): Result<Unit> = runSocialCall {
        val res = apiService.loginWithGoogle(GoogleAuthRequestDto(idToken = idToken))
        sessionManager.saveAuthSession(res.accessToken, res.refreshToken, res.user)
    }

    override suspend fun requestPasswordReset(email: String): Result<String> = runSocialCall {
        val res = apiService.forgotPassword(ForgotPasswordRequestDto(email = email.trim()))
        res.message.ifBlank {
            "Si el correo está registrado, recibirás un código de 6 dígitos en unos segundos."
        }
    }

    override suspend fun confirmPasswordReset(
        email: String,
        code: String,
        newPassword: String
    ): Result<String> = runSocialCall {
        val res = apiService.resetPassword(
            ResetPasswordRequestDto(
                email = email.trim(),
                code = code.trim(),
                newPassword = newPassword
            )
        )
        res.message.ifBlank { "Contraseña actualizada. Ya puedes iniciar sesión." }
    }

    override suspend fun logout() {
        val refreshToken = sessionManager.currentSnapshot.refreshToken
        if (refreshToken.isNotBlank()) {
            runCatching { apiService.logout(LogoutRequestDto(refreshToken)) }
        }
        sessionManager.clearAuthSession()
    }

    override suspend fun getFeed(discover: Boolean): Result<FeedResponseDto> = runSocialCall {
        apiService.getFeed(discover = discover)
    }

    override suspend fun publishDailyOutfit(
        context: Context,
        imageUriOrPath: String,
        caption: String,
        sourceType: String,
        visibility: String,
        styleTags: List<String>,
        dominantColors: List<String>,
        wornDate: String
    ): Result<CreatedPostDto> = withContext(Dispatchers.IO) {
        runSocialCall {
            val bytes = readImageBytes(context, imageUriOrPath)
                ?: throw IllegalArgumentException("No se pudo leer la imagen seleccionada")

            val imagePart = MultipartBody.Part.createFormData(
                name = "image",
                filename = "outfit_${System.currentTimeMillis()}.jpg",
                body = bytes.toRequestBody("image/jpeg".toMediaType())
            )

            val plainText = "text/plain".toMediaType()
            apiService.createPost(
                image = imagePart,
                caption = caption.trim().toRequestBody(plainText),
                wornDate = wornDate.toRequestBody(plainText),
                sourceType = sourceType.toRequestBody(plainText),
                visibility = visibility.toRequestBody(plainText),
                styleTagsJson = Json.encodeToString(styleTags).toRequestBody(plainText),
                dominantColorsJson = Json.encodeToString(dominantColors).toRequestBody(plainText)
            )
        }
    }

    override suspend fun reactToPost(postId: String, emoji: String): Result<Unit> = runSocialCall {
        apiService.reactToPost(postId, ReactionRequestDto(emoji))
    }

    override suspend fun removeReaction(postId: String): Result<Unit> = runSocialCall {
        apiService.removeReaction(postId)
    }

    override suspend fun getComments(postId: String): Result<List<CommentDto>> = runSocialCall {
        apiService.getComments(postId).comments
    }

    override suspend fun addComment(postId: String, content: String): Result<Unit> = runSocialCall {
        apiService.addComment(postId, CreateCommentRequestDto(content.trim()))
    }

    override suspend fun getMonthlyCalendar(
        year: Int,
        month: Int
    ): Result<CalendarMonthResponseDto> = runSocialCall {
        val userId = sessionManager.currentSnapshot.userId
        require(userId.isNotBlank()) { "Inicia sesión para consultar tu calendario en la nube" }
        apiService.getMonthlyCalendar(userId = userId, year = year, month = month)
    }

    override suspend fun getYearlyRecap(year: Int): Result<YearlyRecapDto> = runSocialCall {
        val userId = sessionManager.currentSnapshot.userId
        require(userId.isNotBlank()) { "Inicia sesión para consultar tu resumen anual" }
        apiService.getYearlyRecap(userId = userId, year = year)
    }

    override suspend fun searchUsers(query: String): Result<List<SearchedUserDto>> = runSocialCall {
        apiService.searchUsers(query).users
    }

    override suspend fun getUserProfile(userId: String): Result<UserProfileDto> = runSocialCall {
        apiService.getUserProfile(userId)
    }

    override suspend fun toggleFollow(targetUserId: String, follow: Boolean): Result<String> =
        runSocialCall {
            val action = if (follow) "FOLLOW" else "UNFOLLOW"
            apiService.followOrUnfollow(
                FollowActionRequestDto(targetUserId = targetUserId, action = action)
            ).status
        }

    override suspend fun getPendingFollowRequests(): Result<List<FollowRequestItemDto>> =
        runSocialCall {
            apiService.getPendingFollowRequests().requests
        }

    override suspend fun getNotifications(): Result<List<UserNotificationDto>> =
        runSocialCall {
            apiService.getNotifications().notifications
        }

    override suspend fun respondFollowRequest(followerId: String, accept: Boolean): Result<Unit> =
        runSocialCall {
            val decision = if (accept) "ACCEPT" else "REJECT"
            apiService.respondToFollowRequest(followerId, FollowDecisionRequestDto(decision))
        }

    override suspend fun refreshMyProfile(): Result<MeProfileDto> = runSocialCall {
        val me = apiService.getMyProfile()
        sessionManager.syncMyProfile(me)
        me
    }

    override suspend fun updateMyUsername(newUsername: String): Result<UpdateUsernameResponseDto> =
        runSocialCall {
            val cleaned = newUsername.trim().removePrefix("@")
            val res = apiService.updateMyUsername(UpdateUsernameRequestDto(username = cleaned))
            sessionManager.applyUsernameChange(res)
            res
        }

    override suspend fun updateMyProfile(
        displayName: String?,
        bio: String?,
        isPrivate: Boolean?
    ): Result<UpdatedProfileDto> = runSocialCall {
        val res = apiService.updateMyProfile(
            UpdateProfileRequestDto(
                displayName = displayName,
                bio = bio,
                isPrivate = isPrivate
            )
        )
        sessionManager.applyProfileUpdate(res)
        res
    }

    override suspend fun uploadMyAvatar(
        context: Context,
        imageUriOrPath: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runSocialCall {
            val bytes = readImageBytes(context, imageUriOrPath)
                ?: throw IllegalArgumentException("No se pudo leer la foto de perfil seleccionada")
            val part = MultipartBody.Part.createFormData(
                name = "avatar",
                filename = "avatar_${System.currentTimeMillis()}.jpg",
                body = bytes.toRequestBody("image/jpeg".toMediaType())
            )
            val res = apiService.uploadMyAvatar(part)
            sessionManager.applyAvatarUpdate(res.avatarUrl)
            res.avatarUrl
        }
    }

    override suspend fun exportMyGdprDataJson(): Result<String> = withContext(Dispatchers.IO) {
        runSocialCall {
            apiService.exportMyGdprData().use { it.string() }
        }
    }

    override suspend fun deleteMyAccount(): Result<String> = runSocialCall {
        val res = apiService.deleteMyAccount()
        sessionManager.clearAuthSession()
        res.message.ifBlank {
            "Cuenta y datos personales eliminados definitivamente del servidor (Art. 17 RGPD)."
        }
    }

    override suspend fun deletePost(postId: String, purgeArchive: Boolean): Result<String> = runSocialCall {
        val res = apiService.deletePost(postId, purgeArchive)
        res.message.ifBlank { "Fotografía eliminada conforme al Art. 17 RGPD." }
    }

    override suspend fun reportPost(postId: String, reason: String, details: String): Result<String> = runSocialCall {
        val res = apiService.reportPost(postId, ReportPostRequestDto(reason = reason, details = details))
        res.message.ifBlank { "Denuncia registrada conforme a la Ley de Servicios Digitales (DSA)." }
    }

    override suspend fun blockUser(targetUserId: String, block: Boolean): Result<String> = runSocialCall {
        val action = if (block) "BLOCK" else "UNBLOCK"
        val res = apiService.blockUser(BlockUserRequestDto(targetUserId = targetUserId, action = action))
        res.message.ifBlank { if (block) "Usuario bloqueado." else "Usuario desbloqueado." }
    }

    override suspend fun getBlockedUsers(): Result<List<BlockedUserDto>> = runSocialCall {
        apiService.getBlockedUsers().blockedUsers
    }

    private inline fun <T> runSocialCall(block: () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (e: HttpException) {
            val rawBody = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
            val parsedError = rawBody?.let { body ->
                runCatching {
                    val obj = Json.parseToJsonElement(body).jsonObject
                    obj["message"]?.jsonPrimitive?.content ?: obj["error"]?.jsonPrimitive?.content
                }.getOrNull()
            }
            Result.failure(Exception(parsedError ?: "Error del servicio (${e.code()})", e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun readImageBytes(context: Context, uriOrPath: String): ByteArray? {
        return try {
            if (uriOrPath.startsWith("content://") || uriOrPath.startsWith("file://")) {
                context.contentResolver.openInputStream(Uri.parse(uriOrPath))?.use { it.readBytes() }
            } else {
                val file = File(uriOrPath)
                if (file.exists()) file.readBytes() else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
