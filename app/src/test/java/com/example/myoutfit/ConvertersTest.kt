package com.example.myoutfit

import com.example.myoutfit.data.local.converters.Converters
import com.example.myoutfit.data.local.entity.ClothingCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun testClothingCategoryConverter() {
        val category = ClothingCategory.FOOTWEAR
        val stringVal = converters.fromClothingCategory(category)
        assertEquals("FOOTWEAR", stringVal)

        val restoredCategory = converters.toClothingCategory(stringVal)
        assertEquals(ClothingCategory.FOOTWEAR, restoredCategory)
    }

    @Test
    fun testStringListConverter() {
        val list = listOf("Gótico", "Casual", "Colorido")
        val json = converters.fromStringList(list)
        val restored = converters.toStringList(json)
        assertEquals(list, restored)
    }

    @Test
    fun testLongListConverter() {
        val list = listOf(1L, 2L, 3L, 42L)
        val json = converters.fromLongList(list)
        val restored = converters.toLongList(json)
        assertEquals(list, restored)
    }
}
