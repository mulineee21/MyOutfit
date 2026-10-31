package com.example.myoutfit.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clothing_items")
data class ClothingItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val imagePath: String,
    val category: ClothingCategory,
    val color: String,
    val styleTags: List<String>,
    val description: String,
    val dateAdded: Long = System.currentTimeMillis()
)
