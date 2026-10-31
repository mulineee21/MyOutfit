package com.example.myoutfit

import com.example.myoutfit.data.local.entity.ClothingCategory
import com.example.myoutfit.data.local.entity.ClothingItem
import com.example.myoutfit.data.local.entity.InspirationItem
import com.example.myoutfit.data.local.preferences.UserPreferences
import com.example.myoutfit.domain.recommendation.OutfitRecommendationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OutfitRecommendationEngineTest {

    private lateinit var engine: OutfitRecommendationEngine

    @Before
    fun setUp() {
        engine = OutfitRecommendationEngine()
    }

    @Test
    fun generateRecommendation_insufficientItems_returnsFalse() {
        val clothingItems = listOf(
            ClothingItem(
                id = 1,
                imagePath = "path1",
                category = ClothingCategory.TOPS,
                color = "Negro",
                styleTags = listOf("Casual"),
                description = "Camiseta negra"
            )
            // Missing Bottoms!
        )

        val result = engine.generateRecommendation(
            clothingItems = clothingItems,
            userPreferences = UserPreferences()
        )

        assertFalse(result.isSufficientItems)
        assertTrue(result.items.isEmpty())
        assertTrue(result.missingCategories.contains(ClothingCategory.BOTTOMS))
    }

    @Test
    fun generateRecommendation_withTopsAndBottoms_returnsValidOutfit() {
        val top = ClothingItem(
            id = 1,
            imagePath = "path1",
            category = ClothingCategory.TOPS,
            color = "Negro",
            styleTags = listOf("Gótico"),
            description = "Camiseta Gótica"
        )
        val bottom = ClothingItem(
            id = 2,
            imagePath = "path2",
            category = ClothingCategory.BOTTOMS,
            color = "Negro",
            styleTags = listOf("Gótico"),
            description = "Pantalón de cuero"
        )
        val footwear = ClothingItem(
            id = 3,
            imagePath = "path3",
            category = ClothingCategory.FOOTWEAR,
            color = "Negro",
            styleTags = listOf("Gótico"),
            description = "Botas pesadas"
        )

        val clothingItems = listOf(top, bottom, footwear)

        val result = engine.generateRecommendation(
            clothingItems = clothingItems,
            userPreferences = UserPreferences(favoriteStyles = setOf("Gótico")),
            requestedStyle = "Gótico",
            seed = 12345L
        )

        assertTrue(result.isSufficientItems)
        assertEquals("Gótico", result.styleTag)
        assertTrue(result.items.contains(top))
        assertTrue(result.items.contains(bottom))
        assertTrue(result.matchPercentage >= 70)
    }

    @Test
    fun generateRecommendation_styleMatching_favorsRequestedStyle() {
        val topGothic = ClothingItem(
            id = 1,
            imagePath = "path1",
            category = ClothingCategory.TOPS,
            color = "Negro",
            styleTags = listOf("Gótico"),
            description = "Top Gótico"
        )
        val topCasual = ClothingItem(
            id = 2,
            imagePath = "path2",
            category = ClothingCategory.TOPS,
            color = "Azul",
            styleTags = listOf("Casual"),
            description = "Top Casual"
        )
        val bottomGothic = ClothingItem(
            id = 3,
            imagePath = "path3",
            category = ClothingCategory.BOTTOMS,
            color = "Negro",
            styleTags = listOf("Gótico"),
            description = "Pantalón Gótico"
        )

        val clothing = listOf(topGothic, topCasual, bottomGothic)

        val result = engine.generateRecommendation(
            clothingItems = clothing,
            userPreferences = UserPreferences(favoriteStyles = setOf("Gótico")),
            requestedStyle = "Gótico",
            seed = 100L
        )

        assertTrue(result.isSufficientItems)
        assertTrue(result.items.contains(topGothic))
        assertTrue(result.items.contains(bottomGothic))
    }

    @Test
    fun generateRecommendation_incorporatesInspirationTags() {
        val top = ClothingItem(
            id = 1,
            imagePath = "path1",
            category = ClothingCategory.TOPS,
            color = "Blanco",
            styleTags = listOf("Streetwear"),
            description = "Hoodie oversized"
        )
        val bottom = ClothingItem(
            id = 2,
            imagePath = "path2",
            category = ClothingCategory.BOTTOMS,
            color = "Negro",
            styleTags = listOf("Streetwear"),
            description = "Cargo pants"
        )

        val inspiration = listOf(
            InspirationItem(
                id = 10,
                imagePath = "insp1",
                title = "Streetwear Look",
                styleTags = listOf("Streetwear")
            )
        )

        val result = engine.generateRecommendation(
            clothingItems = listOf(top, bottom),
            userPreferences = UserPreferences(),
            inspirationItems = inspiration,
            requestedStyle = "Streetwear"
        )

        assertTrue(result.isSufficientItems)
        assertEquals("Streetwear", result.styleTag)
        assertTrue(result.matchPercentage >= 80)
    }
}
