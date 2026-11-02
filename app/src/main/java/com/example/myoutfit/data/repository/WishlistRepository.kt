package com.example.myoutfit.data.repository

import android.content.Context
import com.example.myoutfit.data.local.dao.WishlistDao
import com.example.myoutfit.data.local.entity.ClothingCategory
import com.example.myoutfit.data.local.entity.ClothingItem
import com.example.myoutfit.data.local.entity.WishlistItem
import com.example.myoutfit.util.ImageStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

interface WishlistRepository {
    fun getAllWishlistItems(): Flow<List<WishlistItem>>
    suspend fun getWishlistItemById(id: Long): WishlistItem?
    suspend fun insertWishlistItem(item: WishlistItem): Long
    suspend fun updateWishlistItem(item: WishlistItem)
    suspend fun deleteWishlistItem(item: WishlistItem)
    suspend fun moveToCloset(
        context: Context,
        item: WishlistItem,
        clothingRepository: ClothingRepository,
        category: ClothingCategory,
        color: String,
        deleteFromWishlist: Boolean = false
    ): ClothingItem
}

class WishlistRepositoryImpl(
    private val wishlistDao: WishlistDao
) : WishlistRepository {

    override fun getAllWishlistItems(): Flow<List<WishlistItem>> {
        return wishlistDao.getAllWishlistItems()
    }

    override suspend fun getWishlistItemById(id: Long): WishlistItem? {
        return wishlistDao.getWishlistItemById(id)
    }

    override suspend fun insertWishlistItem(item: WishlistItem): Long {
        return wishlistDao.insertWishlistItem(item)
    }

    override suspend fun updateWishlistItem(item: WishlistItem) {
        wishlistDao.updateWishlistItem(item)
    }

    override suspend fun deleteWishlistItem(item: WishlistItem) {
        wishlistDao.deleteWishlistItem(item)
    }

    override suspend fun moveToCloset(
        context: Context,
        item: WishlistItem,
        clothingRepository: ClothingRepository,
        category: ClothingCategory,
        color: String,
        deleteFromWishlist: Boolean
    ): ClothingItem = withContext(Dispatchers.IO) {
        // If image is a remote URL, download and save locally
        val localImagePath = if (item.imageUrl.isNotBlank()) {
            ImageStorageManager.saveRemoteUrl(context, item.imageUrl, "clothing") ?: item.imageUrl
        } else {
            ""
        }

        val clothingItem = ClothingItem(
            imagePath = localImagePath,
            category = category,
            color = color.ifBlank { item.color },
            styleTags = item.styleTags,
            description = buildString {
                if (item.title.isNotBlank()) append(item.title)
                if (item.storeName.isNotBlank()) append(" (${item.storeName})")
                if (item.description.isNotBlank()) {
                    if (isNotEmpty()) append(". ")
                    append(item.description)
                }
            }
        )

        val insertedId = clothingRepository.insertClothingItem(clothingItem)
        if (deleteFromWishlist) {
            deleteWishlistItem(item)
        }

        clothingItem.copy(id = insertedId)
    }
}
