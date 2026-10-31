package com.example.myoutfit.data.repository

import com.example.myoutfit.data.local.dao.ClothingDao
import com.example.myoutfit.data.local.entity.ClothingCategory
import com.example.myoutfit.data.local.entity.ClothingItem
import kotlinx.coroutines.flow.Flow

interface ClothingRepository {
    fun getAllClothingItems(): Flow<List<ClothingItem>>
    suspend fun getClothingItemById(id: Long): ClothingItem?
    fun getClothingItemsByCategory(category: ClothingCategory): Flow<List<ClothingItem>>
    fun searchClothingItems(query: String): Flow<List<ClothingItem>>
    suspend fun insertClothingItem(item: ClothingItem): Long
    suspend fun updateClothingItem(item: ClothingItem)
    suspend fun deleteClothingItem(item: ClothingItem)
    suspend fun deleteClothingItemById(id: Long)
}

class ClothingRepositoryImpl(
    private val clothingDao: ClothingDao
) : ClothingRepository {
    override fun getAllClothingItems(): Flow<List<ClothingItem>> =
        clothingDao.getAllClothingItems()

    override suspend fun getClothingItemById(id: Long): ClothingItem? =
        clothingDao.getClothingItemById(id)

    override fun getClothingItemsByCategory(category: ClothingCategory): Flow<List<ClothingItem>> =
        clothingDao.getClothingItemsByCategory(category)

    override fun searchClothingItems(query: String): Flow<List<ClothingItem>> =
        clothingDao.searchClothingItems(query)

    override suspend fun insertClothingItem(item: ClothingItem): Long =
        clothingDao.insertClothingItem(item)

    override suspend fun updateClothingItem(item: ClothingItem) =
        clothingDao.updateClothingItem(item)

    override suspend fun deleteClothingItem(item: ClothingItem) =
        clothingDao.deleteClothingItem(item)

    override suspend fun deleteClothingItemById(id: Long) =
        clothingDao.deleteClothingItemById(id)
}
