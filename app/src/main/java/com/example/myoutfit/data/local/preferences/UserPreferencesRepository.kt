package com.example.myoutfit.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.myoutfit.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

data class UserPreferences(
    val userName: String = "Joel Molina",
    val profileImagePath: String = "",
    val bio: String = "Mi armario digital & estilista personal",
    val favoriteStyles: Set<String> = setOf("Casual", "Streetwear", "Gótico", "Colorido", "Formal"),
    val selectedTheme: String = "SYSTEM",
    val geminiApiKey: String = "",
    val autoCategorizeWithAi: Boolean = true
)

interface UserPreferencesRepository {
    val userPreferencesFlow: Flow<UserPreferences>
    suspend fun updateUserName(name: String)
    suspend fun updateProfileImagePath(imagePath: String)
    suspend fun updateBio(bio: String)
    suspend fun updateFavoriteStyles(styles: Set<String>)
    suspend fun addFavoriteStyle(style: String)
    suspend fun removeFavoriteStyle(style: String)
    suspend fun updateSelectedTheme(theme: String)
    suspend fun updateGeminiApiKey(apiKey: String)
    suspend fun updateAutoCategorizeWithAi(enabled: Boolean)
}

class UserPreferencesRepositoryImpl(
    private val context: Context
) : UserPreferencesRepository {

    private object PreferencesKeys {
        val USER_NAME = stringPreferencesKey("user_name")
        val PROFILE_IMAGE_PATH = stringPreferencesKey("profile_image_path")
        val BIO = stringPreferencesKey("bio")
        val FAVORITE_STYLES = stringSetPreferencesKey("favorite_styles")
        val SELECTED_THEME = stringPreferencesKey("selected_theme")
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val AUTO_CATEGORIZE_WITH_AI = booleanPreferencesKey("auto_categorize_with_ai")
    }

    override val userPreferencesFlow: Flow<UserPreferences> = context.userPreferencesDataStore.data
        .map { preferences ->
            val userName = preferences[PreferencesKeys.USER_NAME] ?: "Joel Molina"
            val profileImage = preferences[PreferencesKeys.PROFILE_IMAGE_PATH] ?: ""
            val bio = preferences[PreferencesKeys.BIO] ?: "Mi armario digital & estilista personal"
            val favoriteStyles = preferences[PreferencesKeys.FAVORITE_STYLES]
                ?: setOf("Casual", "Streetwear", "Gótico", "Colorido", "Formal")
            val theme = preferences[PreferencesKeys.SELECTED_THEME] ?: "SYSTEM"
            val geminiKey = preferences[PreferencesKeys.GEMINI_API_KEY]?.ifBlank { null }
                ?: BuildConfig.GEMINI_API_KEY
            val autoCategorize = preferences[PreferencesKeys.AUTO_CATEGORIZE_WITH_AI] ?: true
            UserPreferences(
                userName = userName,
                profileImagePath = profileImage,
                bio = bio,
                favoriteStyles = favoriteStyles,
                selectedTheme = theme,
                geminiApiKey = geminiKey,
                autoCategorizeWithAi = autoCategorize
            )
        }

    override suspend fun updateUserName(name: String) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[PreferencesKeys.USER_NAME] = name.trim()
        }
    }

    override suspend fun updateProfileImagePath(imagePath: String) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[PreferencesKeys.PROFILE_IMAGE_PATH] = imagePath.trim()
        }
    }

    override suspend fun updateBio(bio: String) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[PreferencesKeys.BIO] = bio.trim()
        }
    }

    override suspend fun updateFavoriteStyles(styles: Set<String>) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[PreferencesKeys.FAVORITE_STYLES] = styles
        }
    }

    override suspend fun addFavoriteStyle(style: String) {
        context.userPreferencesDataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.FAVORITE_STYLES] ?: setOf("Casual", "Streetwear", "Gótico", "Colorido", "Formal")
            preferences[PreferencesKeys.FAVORITE_STYLES] = current + style
        }
    }

    override suspend fun removeFavoriteStyle(style: String) {
        context.userPreferencesDataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.FAVORITE_STYLES] ?: emptySet()
            preferences[PreferencesKeys.FAVORITE_STYLES] = current - style
        }
    }

    override suspend fun updateSelectedTheme(theme: String) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[PreferencesKeys.SELECTED_THEME] = theme
        }
    }

    override suspend fun updateGeminiApiKey(apiKey: String) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[PreferencesKeys.GEMINI_API_KEY] = apiKey.trim()
        }
    }

    override suspend fun updateAutoCategorizeWithAi(enabled: Boolean) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_CATEGORIZE_WITH_AI] = enabled
        }
    }
}
