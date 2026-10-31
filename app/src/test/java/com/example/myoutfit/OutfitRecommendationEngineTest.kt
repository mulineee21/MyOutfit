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

    @Test
    fun generateRecommendation_withDress_returnsValidOutfit() {
        val dress = ClothingItem(
            id = 1,
            imagePath = "dress.jpg",
            category = ClothingCategory.DRESSES,
            color = "Rojo",
            styleTags = listOf("Elegante"),
            description = "Vestido rojo de fiesta"
        )
        val footwear = ClothingItem(
            id = 2,
            imagePath = "shoes.jpg",
            category = ClothingCategory.FOOTWEAR,
            color = "Negro",
            styleTags = listOf("Elegante"),
            description = "Tacones negros"
        )

        val result = engine.generateRecommendation(
            clothingItems = listOf(dress, footwear),
            userPreferences = UserPreferences(),
            requestedStyle = "Elegante"
        )

        assertTrue(result.isSufficientItems)
        assertTrue(result.items.contains(dress))
        assertTrue(result.items.contains(footwear))
    }

    @Test
    fun generateRecommendation_withTShirtAndSkirt_returnsValidOutfit() {
        val tshirt = ClothingItem(
            id = 1,
            imagePath = "tshirt.jpg",
            category = ClothingCategory.T_SHIRTS,
            color = "Blanco",
            styleTags = listOf("Casual"),
            description = "Camiseta básica blanca"
        )
        val skirt = ClothingItem(
            id = 2,
            imagePath = "skirt.jpg",
            category = ClothingCategory.SKIRTS,
            color = "Azul",
            styleTags = listOf("Casual"),
            description = "Falda vaquera"
        )

        val result = engine.generateRecommendation(
            clothingItems = listOf(tshirt, skirt),
            userPreferences = UserPreferences(),
            requestedStyle = "Casual"
        )

        assertTrue(result.isSufficientItems)
        assertTrue(result.items.contains(tshirt))
        assertTrue(result.items.contains(skirt))
    }

    @Test
    fun clothingCategory_classificationProperties_areCorrect() {
        assertTrue(ClothingCategory.T_SHIRTS.isTop)
        assertTrue(ClothingCategory.SHIRTS.isTop)
        assertTrue(ClothingCategory.SWEATERS.isTop)
        assertTrue(ClothingCategory.TOPS.isTop)

        assertTrue(ClothingCategory.PANTS.isBottom)
        assertTrue(ClothingCategory.JEANS.isBottom)
        assertTrue(ClothingCategory.SKIRTS.isBottom)
        assertTrue(ClothingCategory.SHORTS.isBottom)
        assertTrue(ClothingCategory.BOTTOMS.isBottom)

        assertTrue(ClothingCategory.DRESSES.isOnePiece)
        assertTrue(ClothingCategory.JUMPSUITS.isOnePiece)

        assertTrue(ClothingCategory.FOOTWEAR.isFootwear)
        assertTrue(ClothingCategory.OUTERWEAR.isOuterwear)

        assertTrue(ClothingCategory.BAGS.isAccessory)
        assertTrue(ClothingCategory.ACCESSORIES.isAccessory)
    }
}
