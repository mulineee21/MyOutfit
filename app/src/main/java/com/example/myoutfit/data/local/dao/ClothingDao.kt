package com.example.myoutfit.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.myoutfit.data.local.entity.ClothingCategory
import com.example.myoutfit.data.local.entity.ClothingItem
import kotlinx.coroutines.flow.Flow

@Dao
interface ClothingDao {
    @Query("SELECT * FROM clothing_items ORDER BY dateAdded DESC")
    fun getAllClothingItems(): Flow<List<ClothingItem>>

    @Query("SELECT * FROM clothing_items WHERE id = :id")
    suspend fun getClothingItemById(id: Long): ClothingItem?

    @Query("SELECT * FROM clothing_items WHERE category = :category ORDER BY dateAdded DESC")
    fun getClothingItemsByCategory(category: ClothingCategory): Flow<List<ClothingItem>>

    @Query("SELECT * FROM clothing_items WHERE description LIKE '%' || :query || '%' OR color LIKE '%' || :query || '%' OR styleTags LIKE '%' || :query || '%' ORDER BY dateAdded DESC")
    fun searchClothingItems(query: String): Flow<List<ClothingItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClothingItem(item: ClothingItem): Long

    @Update
    suspend fun updateClothingItem(item: ClothingItem)

    @Delete
    suspend fun deleteClothingItem(item: ClothingItem)

    @Query("DELETE FROM clothing_items WHERE id = :id")
    suspend fun deleteClothingItemById(id: Long)
}
