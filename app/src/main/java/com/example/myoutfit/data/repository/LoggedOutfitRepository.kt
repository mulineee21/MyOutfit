package com.example.myoutfit.data.repository

import com.example.myoutfit.data.local.dao.LoggedOutfitDao
import com.example.myoutfit.data.local.entity.LoggedOutfit
import kotlinx.coroutines.flow.Flow

interface LoggedOutfitRepository {
    fun getAllLoggedOutfits(): Flow<List<LoggedOutfit>>
    suspend fun getLoggedOutfitById(id: Long): LoggedOutfit?
    fun getLoggedOutfitsForDateRange(startDate: Long, endDate: Long): Flow<List<LoggedOutfit>>
    suspend fun insertLoggedOutfit(loggedOutfit: LoggedOutfit): Long
    suspend fun updateLoggedOutfit(loggedOutfit: LoggedOutfit)
    suspend fun deleteLoggedOutfit(loggedOutfit: LoggedOutfit)
    suspend fun deleteLoggedOutfitById(id: Long)
}

class LoggedOutfitRepositoryImpl(
    private val loggedOutfitDao: LoggedOutfitDao
) : LoggedOutfitRepository {
    override fun getAllLoggedOutfits(): Flow<List<LoggedOutfit>> =
        loggedOutfitDao.getAllLoggedOutfits()

    override suspend fun getLoggedOutfitById(id: Long): LoggedOutfit? =
        loggedOutfitDao.getLoggedOutfitById(id)

    override fun getLoggedOutfitsForDateRange(startDate: Long, endDate: Long): Flow<List<LoggedOutfit>> =
        loggedOutfitDao.getLoggedOutfitsForDateRange(startDate, endDate)

    override suspend fun insertLoggedOutfit(loggedOutfit: LoggedOutfit): Long =
        loggedOutfitDao.insertLoggedOutfit(loggedOutfit)

    override suspend fun updateLoggedOutfit(loggedOutfit: LoggedOutfit) =
        loggedOutfitDao.updateLoggedOutfit(loggedOutfit)

    override suspend fun deleteLoggedOutfit(loggedOutfit: LoggedOutfit) =
        loggedOutfitDao.deleteLoggedOutfit(loggedOutfit)

    override suspend fun deleteLoggedOutfitById(id: Long) =
        loggedOutfitDao.deleteLoggedOutfitById(id)
}
