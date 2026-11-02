package com.example.myoutfit.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wishlist_items")
data class WishlistItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val productUrl: String,
    val imageUrl: String = "",
    val description: String = "",
    val styleTags: List<String> = emptyList(),
    val category: String = "Moda",
    val color: String = "",
    val price: String = "",
    val storeName: String = "",
    val dateAdded: Long = System.currentTimeMillis()
)
