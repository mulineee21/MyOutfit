package com.example.myoutfit.data.repository

import com.example.myoutfit.data.local.dao.OutfitDao
import com.example.myoutfit.data.local.entity.Outfit
import kotlinx.coroutines.flow.Flow

interface OutfitRepository {
    fun getAllOutfits(): Flow<List<Outfit>>
    suspend fun getOutfitById(id: Long): Outfit?
    fun getFavoriteOutfits(): Flow<List<Outfit>>
    fun getOutfitsByStyleTag(styleTag: String): Flow<List<Outfit>>
    suspend fun insertOutfit(outfit: Outfit): Long
    suspend fun updateOutfit(outfit: Outfit)
    suspend fun deleteOutfit(outfit: Outfit)
    suspend fun deleteOutfitById(id: Long)
    suspend fun updateFavoriteStatus(id: Long, isFavorite: Boolean)
}

class OutfitRepositoryImpl(
    private val outfitDao: OutfitDao
) : OutfitRepository {
    override fun getAllOutfits(): Flow<List<Outfit>> =
        outfitDao.getAllOutfits()

    override suspend fun getOutfitById(id: Long): Outfit? =
        outfitDao.getOutfitById(id)

    override fun getFavoriteOutfits(): Flow<List<Outfit>> =
        outfitDao.getFavoriteOutfits()

    override fun getOutfitsByStyleTag(styleTag: String): Flow<List<Outfit>> =
        outfitDao.getOutfitsByStyleTag(styleTag)

    override suspend fun insertOutfit(outfit: Outfit): Long =
        outfitDao.insertOutfit(outfit)

    override suspend fun updateOutfit(outfit: Outfit) =
        outfitDao.updateOutfit(outfit)

    override suspend fun deleteOutfit(outfit: Outfit) =
        outfitDao.deleteOutfit(outfit)

    override suspend fun deleteOutfitById(id: Long) =
        outfitDao.deleteOutfitById(id)

    override suspend fun updateFavoriteStatus(id: Long, isFavorite: Boolean) =
        outfitDao.updateFavoriteStatus(id, isFavorite)
}
