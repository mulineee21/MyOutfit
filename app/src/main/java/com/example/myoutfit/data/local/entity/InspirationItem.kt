package com.example.myoutfit.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "inspiration_items")
data class InspirationItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val imagePath: String,
    val title: String,
    val styleTags: List<String>,
    val notes: String = "",
    val sourceUrl: String = ""
)
