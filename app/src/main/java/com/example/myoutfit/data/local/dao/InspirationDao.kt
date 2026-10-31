package com.example.myoutfit.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.myoutfit.data.local.entity.InspirationItem
import kotlinx.coroutines.flow.Flow

@Dao
interface InspirationDao {
    @Query("SELECT * FROM inspiration_items ORDER BY id DESC")
    fun getAllInspirationItems(): Flow<List<InspirationItem>>

    @Query("SELECT * FROM inspiration_items WHERE id = :id")
    suspend fun getInspirationItemById(id: Long): InspirationItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInspirationItem(item: InspirationItem): Long

    @Update
    suspend fun updateInspirationItem(item: InspirationItem)

    @Delete
    suspend fun deleteInspirationItem(item: InspirationItem)

    @Query("DELETE FROM inspiration_items WHERE id = :id")
    suspend fun deleteInspirationItemById(id: Long)
}
