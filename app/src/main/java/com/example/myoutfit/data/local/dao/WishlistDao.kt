package com.example.myoutfit.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.myoutfit.data.local.entity.WishlistItem
import kotlinx.coroutines.flow.Flow

@Dao
interface WishlistDao {

    @Query("SELECT * FROM wishlist_items ORDER BY dateAdded DESC")
    fun getAllWishlistItems(): Flow<List<WishlistItem>>

    @Query("SELECT * FROM wishlist_items WHERE id = :id")
    suspend fun getWishlistItemById(id: Long): WishlistItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWishlistItem(item: WishlistItem): Long

    @Update
    suspend fun updateWishlistItem(item: WishlistItem)

    @Delete
    suspend fun deleteWishlistItem(item: WishlistItem)
}
