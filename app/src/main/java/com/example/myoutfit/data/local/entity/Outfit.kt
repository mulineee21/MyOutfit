package com.example.myoutfit.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "outfits")
data class Outfit(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val clothingItemIds: List<Long>,
    val styleTag: String,
    val isFavorite: Boolean = false,
    val dateCreated: Long = System.currentTimeMillis()
)
