package com.example.myoutfit.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "logged_outfits")
data class LoggedOutfit(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val outfitId: Long? = null,
    val clothingItemIds: List<Long> = emptyList(),
    val dateUsed: Long = System.currentTimeMillis(),
    val rating: Float = 0.0f,
    val notes: String = ""
)
