package com.example.myoutfit.data.repository

import com.example.myoutfit.data.local.dao.InspirationDao
import com.example.myoutfit.data.local.entity.InspirationItem
import kotlinx.coroutines.flow.Flow

interface InspirationRepository {
    fun getAllInspirationItems(): Flow<List<InspirationItem>>
    suspend fun getInspirationItemById(id: Long): InspirationItem?
    suspend fun insertInspirationItem(item: InspirationItem): Long
    suspend fun updateInspirationItem(item: InspirationItem)
    suspend fun deleteInspirationItem(item: InspirationItem)
    suspend fun deleteInspirationItemById(id: Long)
}

class InspirationRepositoryImpl(
    private val inspirationDao: InspirationDao
) : InspirationRepository {
    override fun getAllInspirationItems(): Flow<List<InspirationItem>> =
        inspirationDao.getAllInspirationItems()

    override suspend fun getInspirationItemById(id: Long): InspirationItem? =
        inspirationDao.getInspirationItemById(id)

    override suspend fun insertInspirationItem(item: InspirationItem): Long =
        inspirationDao.insertInspirationItem(item)

    override suspend fun updateInspirationItem(item: InspirationItem) =
        inspirationDao.updateInspirationItem(item)

    override suspend fun deleteInspirationItem(item: InspirationItem) =
        inspirationDao.deleteInspirationItem(item)

    override suspend fun deleteInspirationItemById(id: Long) =
        inspirationDao.deleteInspirationItemById(id)
}
