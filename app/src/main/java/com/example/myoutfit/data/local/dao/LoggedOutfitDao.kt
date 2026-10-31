package com.example.myoutfit.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.myoutfit.data.local.entity.LoggedOutfit
import kotlinx.coroutines.flow.Flow

@Dao
interface LoggedOutfitDao {
    @Query("SELECT * FROM logged_outfits ORDER BY dateUsed DESC")
    fun getAllLoggedOutfits(): Flow<List<LoggedOutfit>>

    @Query("SELECT * FROM logged_outfits WHERE id = :id")
    suspend fun getLoggedOutfitById(id: Long): LoggedOutfit?

    @Query("SELECT * FROM logged_outfits WHERE dateUsed >= :startDate AND dateUsed <= :endDate ORDER BY dateUsed DESC")
    fun getLoggedOutfitsForDateRange(startDate: Long, endDate: Long): Flow<List<LoggedOutfit>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLoggedOutfit(loggedOutfit: LoggedOutfit): Long

    @Update
    suspend fun updateLoggedOutfit(loggedOutfit: LoggedOutfit)

    @Delete
    suspend fun deleteLoggedOutfit(loggedOutfit: LoggedOutfit)

    @Query("DELETE FROM logged_outfits WHERE id = :id")
    suspend fun deleteLoggedOutfitById(id: Long)
}
