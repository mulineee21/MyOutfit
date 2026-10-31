package com.example.myoutfit.data.local.converters

import androidx.room.TypeConverter
import com.example.myoutfit.data.local.entity.ClothingCategory
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @TypeConverter
    fun fromClothingCategory(category: ClothingCategory?): String {
        return category?.name ?: ClothingCategory.TOPS.name
    }

    @TypeConverter
    fun toClothingCategory(value: String?): ClothingCategory {
        return try {
            if (value != null) ClothingCategory.valueOf(value) else ClothingCategory.TOPS
        } catch (e: Exception) {
            ClothingCategory.TOPS
        }
    }

    @TypeConverter
    fun fromStringList(list: List<String>?): String {
        return json.encodeToString(list ?: emptyList())
    }

    @TypeConverter
    fun toStringList(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString(value)
        } catch (e: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromLongList(list: List<Long>?): String {
        return json.encodeToString(list ?: emptyList())
    }

    @TypeConverter
    fun toLongList(value: String?): List<Long> {
        if (value.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString(value)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
