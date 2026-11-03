package com.example.myoutfit.data.backup

import kotlinx.serialization.Serializable

@Serializable
data class MyOutfitBackup(
    val version: Int = 1,
    val exportDate: Long = System.currentTimeMillis(),
    val appAuthor: String = "Joel Molina",
    val clothingItems: List<ClothingItemBackupDto> = emptyList(),
    val outfits: List<OutfitBackupDto> = emptyList(),
    val inspirations: List<InspirationBackupDto> = emptyList(),
    val loggedOutfits: List<LoggedOutfitBackupDto> = emptyList(),
    val wishlistItems: List<WishlistItemBackupDto> = emptyList(),
    val userPreferences: UserPreferencesBackupDto? = null
)

@Serializable
data class ClothingItemBackupDto(
    val id: Long,
    val imageFileName: String,
    val categoryName: String,
    val color: String,
    val styleTags: List<String>,
    val description: String,
    val dateAdded: Long
)

@Serializable
data class OutfitBackupDto(
    val id: Long,
    val title: String,
    val clothingItemIds: List<Long>,
    val styleTag: String,
    val isFavorite: Boolean,
    val dateCreated: Long
)

@Serializable
data class InspirationBackupDto(
    val id: Long,
    val imageFileName: String,
    val title: String,
    val styleTags: List<String>,
    val notes: String,
    val sourceUrl: String
)

@Serializable
data class LoggedOutfitBackupDto(
    val id: Long,
    val outfitId: Long?,
    val clothingItemIds: List<Long>,
    val dateUsed: Long,
    val rating: Float,
    val notes: String
)

@Serializable
data class WishlistItemBackupDto(
    val id: Long,
    val title: String,
    val productUrl: String,
    val imageUrl: String,
    val description: String,
    val styleTags: List<String>,
    val category: String,
    val color: String,
    val price: String,
    val storeName: String,
    val dateAdded: Long
)

@Serializable
data class UserPreferencesBackupDto(
    val favoriteStyles: List<String>,
    val selectedTheme: String,
    val geminiApiKey: String,
    val autoCategorizeWithAi: Boolean,
    val userName: String = "Joel Molina",
    val profileImagePath: String = "",
    val bio: String = ""
)

data class BackupStats(
    val clothingCount: Int = 0,
    val outfitCount: Int = 0,
    val inspirationCount: Int = 0,
    val loggedCount: Int = 0,
    val wishlistCount: Int = 0,
    val imageCount: Int = 0,
    val fileSizeBytes: Long = 0L
)
