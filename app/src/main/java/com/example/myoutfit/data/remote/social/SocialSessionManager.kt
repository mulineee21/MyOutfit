package com.example.myoutfit.data.remote.social

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.myoutfit.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.socialSessionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "social_session_preferences"
)

data class SocialSession(
    val serverBaseUrl: String = DEFAULT_SERVER_URL,
    val accessToken: String = "",
    val refreshToken: String = "",
    val userId: String = "",
    val username: String = "",
    val email: String = "",
    val displayName: String = "",
    val bio: String = "",
    val avatarUrl: String? = null,
    val isPrivate: Boolean = false,
    val currentStreak: Int = 0,
    val canChangeUsername: Boolean = true,
    val nextUsernameChangeAt: String? = null,
    val usernameCooldownDaysRemaining: Int = 0,
    val followersCount: Int = 0,
    val followingCount: Int = 0
) {
    val isAuthenticated: Boolean
        get() = accessToken.isNotBlank() && userId.isNotBlank()

    fun resolveMediaUrl(relativeOrAbsolute: String?): String? {
        if (relativeOrAbsolute.isNullOrBlank()) return null
        if (relativeOrAbsolute.startsWith("http://") || relativeOrAbsolute.startsWith("https://")) {
            return relativeOrAbsolute
        }
        val cleanBase = serverBaseUrl.trimEnd('/')
        val cleanPath = if (relativeOrAbsolute.startsWith("/")) relativeOrAbsolute else "/$relativeOrAbsolute"
        return "$cleanBase$cleanPath"
    }

    companion object {
        val DEFAULT_SERVER_URL: String = BuildConfig.SOCIAL_API_BASE_URL.ifBlank {
            "https://myoutfit.duckdns.org"
        }
    }
}

class SocialSessionManager(private val context: Context) {

    private object Keys {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val USER_ID = stringPreferencesKey("user_id")
        val USERNAME = stringPreferencesKey("username")
        val EMAIL = stringPreferencesKey("email")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val BIO = stringPreferencesKey("bio")
        val AVATAR_URL = stringPreferencesKey("avatar_url")
        val IS_PRIVATE = booleanPreferencesKey("is_private")
        val CURRENT_STREAK = intPreferencesKey("current_streak")
        val CAN_CHANGE_USERNAME = booleanPreferencesKey("can_change_username")
        val NEXT_USERNAME_CHANGE_AT = stringPreferencesKey("next_username_change_at")
        val USERNAME_COOLDOWN_DAYS = intPreferencesKey("username_cooldown_days")
        val FOLLOWERS_COUNT = intPreferencesKey("followers_count")
        val FOLLOWING_COUNT = intPreferencesKey("following_count")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _cachedSession = MutableStateFlow(SocialSession())
    val sessionState: StateFlow<SocialSession> = _cachedSession.asStateFlow()

    val sessionFlow: Flow<SocialSession> = context.socialSessionDataStore.data.map { prefs ->
        SocialSession(
            serverBaseUrl = SocialSession.DEFAULT_SERVER_URL,
            accessToken = prefs[Keys.ACCESS_TOKEN] ?: "",
            refreshToken = prefs[Keys.REFRESH_TOKEN] ?: "",
            userId = prefs[Keys.USER_ID] ?: "",
            username = prefs[Keys.USERNAME] ?: "",
            email = prefs[Keys.EMAIL] ?: "",
            displayName = prefs[Keys.DISPLAY_NAME] ?: "",
            bio = prefs[Keys.BIO] ?: "",
            avatarUrl = prefs[Keys.AVATAR_URL]?.ifBlank { null },
            isPrivate = prefs[Keys.IS_PRIVATE] ?: false,
            currentStreak = prefs[Keys.CURRENT_STREAK] ?: 0,
            canChangeUsername = prefs[Keys.CAN_CHANGE_USERNAME] ?: true,
            nextUsernameChangeAt = prefs[Keys.NEXT_USERNAME_CHANGE_AT]?.ifBlank { null },
            usernameCooldownDaysRemaining = prefs[Keys.USERNAME_COOLDOWN_DAYS] ?: 0,
            followersCount = prefs[Keys.FOLLOWERS_COUNT] ?: 0,
            followingCount = prefs[Keys.FOLLOWING_COUNT] ?: 0
        )
    }

    init {
        scope.launch {
            sessionFlow.collect { session ->
                _cachedSession.value = session
            }
        }
    }

    val currentSnapshot: SocialSession
        get() = _cachedSession.value

    suspend fun saveAuthSession(
        accessToken: String,
        refreshToken: String,
        user: SocialUserDto
    ) {
        val updated = _cachedSession.value.copy(
            accessToken = accessToken,
            refreshToken = refreshToken,
            userId = user.id,
            username = user.username,
            email = user.email,
            displayName = user.displayName,
            bio = user.bio,
            avatarUrl = user.avatarUrl,
            isPrivate = user.isPrivate,
            currentStreak = user.currentStreak,
            canChangeUsername = user.canChangeUsername,
            nextUsernameChangeAt = user.nextUsernameChangeAt,
            usernameCooldownDaysRemaining = user.usernameCooldownDaysRemaining
        )
        _cachedSession.value = updated
        context.socialSessionDataStore.edit { prefs ->
            prefs[Keys.ACCESS_TOKEN] = accessToken
            prefs[Keys.REFRESH_TOKEN] = refreshToken
            prefs[Keys.USER_ID] = user.id
            prefs[Keys.USERNAME] = user.username
            prefs[Keys.EMAIL] = user.email
            prefs[Keys.DISPLAY_NAME] = user.displayName
            prefs[Keys.BIO] = user.bio
            prefs[Keys.AVATAR_URL] = user.avatarUrl ?: ""
            prefs[Keys.IS_PRIVATE] = user.isPrivate
            prefs[Keys.CURRENT_STREAK] = user.currentStreak
            prefs[Keys.CAN_CHANGE_USERNAME] = user.canChangeUsername
            prefs[Keys.NEXT_USERNAME_CHANGE_AT] = user.nextUsernameChangeAt ?: ""
            prefs[Keys.USERNAME_COOLDOWN_DAYS] = user.usernameCooldownDaysRemaining
        }
    }

    suspend fun syncMyProfile(me: MeProfileDto) {
        val updated = _cachedSession.value.copy(
            username = me.username,
            email = me.email,
            displayName = me.displayName,
            bio = me.bio,
            avatarUrl = me.avatarUrl,
            isPrivate = me.isPrivate,
            currentStreak = me.currentStreak,
            canChangeUsername = me.canChangeUsername,
            nextUsernameChangeAt = me.nextUsernameChangeAt,
            usernameCooldownDaysRemaining = me.usernameCooldownDaysRemaining,
            followersCount = me.followersCount,
            followingCount = me.followingCount
        )
        _cachedSession.value = updated
        context.socialSessionDataStore.edit { prefs ->
            prefs[Keys.USERNAME] = me.username
            prefs[Keys.EMAIL] = me.email
            prefs[Keys.DISPLAY_NAME] = me.displayName
            prefs[Keys.BIO] = me.bio
            prefs[Keys.AVATAR_URL] = me.avatarUrl ?: ""
            prefs[Keys.IS_PRIVATE] = me.isPrivate
            prefs[Keys.CURRENT_STREAK] = me.currentStreak
            prefs[Keys.CAN_CHANGE_USERNAME] = me.canChangeUsername
            prefs[Keys.NEXT_USERNAME_CHANGE_AT] = me.nextUsernameChangeAt ?: ""
            prefs[Keys.USERNAME_COOLDOWN_DAYS] = me.usernameCooldownDaysRemaining
            prefs[Keys.FOLLOWERS_COUNT] = me.followersCount
            prefs[Keys.FOLLOWING_COUNT] = me.followingCount
        }
    }

    suspend fun applyUsernameChange(res: UpdateUsernameResponseDto) {
        val newToken = res.accessToken?.takeIf { it.isNotBlank() } ?: _cachedSession.value.accessToken
        val updated = _cachedSession.value.copy(
            accessToken = newToken,
            username = res.username,
            displayName = res.displayName,
            canChangeUsername = res.canChangeUsername,
            nextUsernameChangeAt = res.nextUsernameChangeAt,
            usernameCooldownDaysRemaining = res.usernameCooldownDaysRemaining
        )
        _cachedSession.value = updated
        context.socialSessionDataStore.edit { prefs ->
            prefs[Keys.ACCESS_TOKEN] = newToken
            prefs[Keys.USERNAME] = res.username
            prefs[Keys.DISPLAY_NAME] = res.displayName
            prefs[Keys.CAN_CHANGE_USERNAME] = res.canChangeUsername
            prefs[Keys.NEXT_USERNAME_CHANGE_AT] = res.nextUsernameChangeAt ?: ""
            prefs[Keys.USERNAME_COOLDOWN_DAYS] = res.usernameCooldownDaysRemaining
        }
    }

    suspend fun applyProfileUpdate(updatedDto: UpdatedProfileDto) {
        val updated = _cachedSession.value.copy(
            username = updatedDto.username,
            displayName = updatedDto.displayName,
            bio = updatedDto.bio,
            isPrivate = updatedDto.isPrivate
        )
        _cachedSession.value = updated
        context.socialSessionDataStore.edit { prefs ->
            prefs[Keys.USERNAME] = updatedDto.username
            prefs[Keys.DISPLAY_NAME] = updatedDto.displayName
            prefs[Keys.BIO] = updatedDto.bio
            prefs[Keys.IS_PRIVATE] = updatedDto.isPrivate
        }
    }

    suspend fun applyAvatarUpdate(avatarUrl: String?) {
        val updated = _cachedSession.value.copy(avatarUrl = avatarUrl)
        _cachedSession.value = updated
        context.socialSessionDataStore.edit { prefs ->
            prefs[Keys.AVATAR_URL] = avatarUrl ?: ""
        }
    }

    suspend fun updateTokens(accessToken: String, refreshToken: String) {
        _cachedSession.value = _cachedSession.value.copy(
            accessToken = accessToken,
            refreshToken = refreshToken
        )
        context.socialSessionDataStore.edit { prefs ->
            prefs[Keys.ACCESS_TOKEN] = accessToken
            prefs[Keys.REFRESH_TOKEN] = refreshToken
        }
    }

    suspend fun clearAuthSession() {
        val keepUrl = _cachedSession.value.serverBaseUrl
        _cachedSession.value = SocialSession(serverBaseUrl = keepUrl)
        context.socialSessionDataStore.edit { prefs ->
            prefs.clear()
        }
    }

    companion object {
        fun normalizeServerUrl(raw: String): String {
            val trimmed = raw.trim().trimEnd('/')
            if (trimmed.isBlank()) return SocialSession.DEFAULT_SERVER_URL
            return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                trimmed
            } else {
                "https://$trimmed"
            }
        }
    }
}
