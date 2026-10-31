package com.example.myoutfit.domain.recommendation

import com.example.myoutfit.data.local.entity.ClothingCategory
import com.example.myoutfit.data.local.entity.ClothingItem
import com.example.myoutfit.data.local.entity.InspirationItem
import com.example.myoutfit.data.local.preferences.UserPreferences
import kotlin.random.Random

data class RecommendationResult(
    val id: String = System.currentTimeMillis().toString(),
    val title: String,
    val items: List<ClothingItem>,
    val styleTag: String,
    val matchPercentage: Int,
    val matchReason: String,
    val isSufficientItems: Boolean = true,
    val missingCategories: List<ClothingCategory> = emptyList(),
    val isAiGenerated: Boolean = false,
    val styleAdvice: String? = null
)

class OutfitRecommendationEngine {

    /**
     * Generates an outfit recommendation based on user clothing, style preferences,
     * saved inspiration tags, and requested target style or occasion.
     */
    fun generateRecommendation(
        clothingItems: List<ClothingItem>,
        userPreferences: UserPreferences,
        inspirationItems: List<InspirationItem> = emptyList(),
        requestedStyle: String? = null,
        seed: Long = System.currentTimeMillis()
    ): RecommendationResult {
        val tops = clothingItems.filter { it.category.isTop }
        val bottoms = clothingItems.filter { it.category.isBottom }
        val onePieces = clothingItems.filter { it.category.isOnePiece }
        val footwear = clothingItems.filter { it.category.isFootwear }
        val outerwear = clothingItems.filter { it.category.isOuterwear }
        val accessories = clothingItems.filter { it.category.isAccessory }

        val canMakeTopBottomOutfit = tops.isNotEmpty() && bottoms.isNotEmpty()
        val canMakeOnePieceOutfit = onePieces.isNotEmpty()

        val missingCategories = mutableListOf<ClothingCategory>()
        if (!canMakeOnePieceOutfit) {
            if (tops.isEmpty()) missingCategories.add(ClothingCategory.TOPS)
            if (bottoms.isEmpty()) missingCategories.add(ClothingCategory.BOTTOMS)
        }

        if (!canMakeTopBottomOutfit && !canMakeOnePieceOutfit) {
            val missingText = missingCategories.joinToString(", ") { cat ->
                when (cat) {
                    ClothingCategory.TOPS -> "Prendas superiores (Camisetas, Camisas, Tops...)"
                    ClothingCategory.BOTTOMS -> "Prendas inferiores (Pantalones, Faldas, Shorts...)"
                    else -> cat.displayName
                }
            }
            return RecommendationResult(
                title = "Armario incompleto",
                items = emptyList(),
                styleTag = requestedStyle ?: "General",
                matchPercentage = 0,
                matchReason = "Para sugerir outfits, agrega al menos $missingText o un Vestido a tu armario.",
                isSufficientItems = false,
                missingCategories = missingCategories
            )
        }

        // Determine target style
        val targetStyle = when {
            !requestedStyle.isNullOrBlank() && requestedStyle != "Sorpréndeme" -> requestedStyle
            userPreferences.favoriteStyles.isNotEmpty() -> {
                val rand = Random(seed)
                userPreferences.favoriteStyles.toList()[rand.nextInt(userPreferences.favoriteStyles.size)]
            }
            else -> "Casual"
        }

        // Collect all inspiration tags
        val inspirationTags = inspirationItems.flatMap { it.styleTags }.map { it.lowercase() }.toSet()

        // Generate candidate combinations
        val candidates = mutableListOf<ScoredCombination>()
        val rng = Random(seed)

        // Sample combinations of available items
        val shuffledFootwear = footwear.shuffled(rng)
        val shuffledOuterwear = outerwear.shuffled(rng)
        val shuffledAccessories = accessories.shuffled(rng)

        // Combinations with Tops and Bottoms
        if (canMakeTopBottomOutfit) {
            val shuffledTops = tops.shuffled(rng)
            val shuffledBottoms = bottoms.shuffled(rng)

            for (top in shuffledTops.take(6)) {
                for (bottom in shuffledBottoms.take(6)) {
                    val footwearItem = shuffledFootwear.firstOrNull()
                    val outerwearItem = if (shuffledOuterwear.isNotEmpty() && rng.nextBoolean()) shuffledOuterwear.firstOrNull() else null
                    val accessoryItem = if (shuffledAccessories.isNotEmpty() && rng.nextBoolean()) shuffledAccessories.firstOrNull() else null

                    val combinationItems = listOfNotNull(top, bottom, footwearItem, outerwearItem, accessoryItem)

                    val score = calculateScore(
                        items = combinationItems,
                        targetStyle = targetStyle,
                        userFavoriteStyles = userPreferences.favoriteStyles,
                        inspirationTags = inspirationTags
                    )

                    candidates.add(ScoredCombination(combinationItems, score))
                }
            }
        }

        // Combinations with One-Piece garments (Dresses, Jumpsuits)
        if (canMakeOnePieceOutfit) {
            val shuffledOnePieces = onePieces.shuffled(rng)

            for (onePiece in shuffledOnePieces.take(6)) {
                val footwearItem = shuffledFootwear.firstOrNull()
                val outerwearItem = if (shuffledOuterwear.isNotEmpty() && rng.nextBoolean()) shuffledOuterwear.firstOrNull() else null
                val accessoryItem = if (shuffledAccessories.isNotEmpty() && rng.nextBoolean()) shuffledAccessories.firstOrNull() else null

                val combinationItems = listOfNotNull(onePiece, footwearItem, outerwearItem, accessoryItem)

                val score = calculateScore(
                    items = combinationItems,
                    targetStyle = targetStyle,
                    userFavoriteStyles = userPreferences.favoriteStyles,
                    inspirationTags = inspirationTags
                )

                candidates.add(ScoredCombination(combinationItems, score))
            }
        }

        // Pick highest scored candidate or pick among top-scoring ties
        candidates.sortByDescending { it.score }
        val maxScore = candidates.firstOrNull()?.score ?: 0
        val topScoringCandidates = candidates.filter { it.score >= maxScore - 5 }
        val selectedCandidate = if (topScoringCandidates.isNotEmpty()) {
            topScoringCandidates[rng.nextInt(topScoringCandidates.size)]
        } else {
            candidates.first()
        }

        val normalizedMatch = ((selectedCandidate.score.coerceIn(30, 100).toFloat() / 100f) * 35 + 64).toInt().coerceIn(65, 99)
        val title = generateOutfitTitle(targetStyle)
        val reason = generateMatchReason(selectedCandidate.items, targetStyle, userPreferences.favoriteStyles, inspirationTags)

        return RecommendationResult(
            title = title,
            items = selectedCandidate.items,
            styleTag = targetStyle,
            matchPercentage = normalizedMatch,
            matchReason = reason,
            isSufficientItems = true
        )
    }

    private data class ScoredCombination(
        val items: List<ClothingItem>,
        val score: Int
    )

    private fun calculateScore(
        items: List<ClothingItem>,
        targetStyle: String,
        userFavoriteStyles: Set<String>,
        inspirationTags: Set<String>
    ): Int {
        var score = 40

        val lowerTargetStyle = targetStyle.lowercase()

        for (item in items) {
            val itemTagsLower = item.styleTags.map { it.lowercase() }

            // Target style match
            if (itemTagsLower.contains(lowerTargetStyle)) {
                score += 20
            }

            // User favorite styles match
            if (userFavoriteStyles.any { fav -> itemTagsLower.contains(fav.lowercase()) }) {
                score += 10
            }

            // Inspiration tags match
            if (itemTagsLower.any { tag -> inspirationTags.contains(tag) }) {
                score += 10
            }

            // Specific style color preferences
            when (lowerTargetStyle) {
                "gótico" -> {
                    if (item.color.equals("negro", ignoreCase = true) || item.color.equals("gris", ignoreCase = true)) {
                        score += 15
                    }
                }
                "colorido" -> {
                    val neutralColors = setOf("negro", "blanco", "gris", "beige")
                    if (!neutralColors.contains(item.color.lowercase())) {
                        score += 15
                    }
                }
                "minimalista" -> {
                    val neutralColors = setOf("negro", "blanco", "gris", "beige")
                    if (neutralColors.contains(item.color.lowercase())) {
                        score += 15
                    }
                }
            }
        }

        // Color harmony between items
        val colors = items.map { it.color.lowercase() }
        val neutralColors = setOf("negro", "blanco", "gris", "beige")

        val hasNeutrals = colors.any { neutralColors.contains(it) }
        if (hasNeutrals) score += 10

        val distinctColors = colors.toSet()
        if (distinctColors.size in 1..3) {
            score += 15 // Good color balance
        }

        // Item completeness bonus
        if (items.any { it.category.isFootwear }) score += 10
        if (items.any { it.category.isOuterwear }) score += 5
        if (items.any { it.category.isAccessory }) score += 5

        return score
    }

    private fun generateOutfitTitle(style: String): String {
        return when (style.lowercase()) {
            "gótico" -> "Outfit Gótico Vanguardista"
            "colorido" -> "Combinación Vibrant & Colorida"
            "casual" -> "Look Casual Urbano"
            "formal" -> "Atuendo Formal Elegante"
            "streetwear" -> "Estilo Streetwear Moderno"
            "minimalista" -> "Look Minimalista Pulido"
            "vintage" -> "Outfit Retro Vintage"
            "deportivo" -> "Estilo Athleisure Deportivo"
            else -> "Outfit $style Recomendado"
        }
    }

    private fun generateMatchReason(
        items: List<ClothingItem>,
        targetStyle: String,
        userFavoriteStyles: Set<String>,
        inspirationTags: Set<String>
    ): String {
        val styleMatches = items.flatMap { it.styleTags }.filter { it.equals(targetStyle, ignoreCase = true) }
        val colors = items.map { it.color }.distinct().joinToString(", ")

        return when {
            targetStyle.equals("Gótico", ignoreCase = true) ->
                "Combinación con predominio de tonos oscuros ($colors) y corte con estética Gótica."
            targetStyle.equals("Colorido", ignoreCase = true) ->
                "Paleta vibrante con prendas en tonos $colors para un look lleno de energía."
            targetStyle.equals("Minimalista", ignoreCase = true) ->
                "Líneas limpias y paleta neutra ($colors) para un estilo sobrio y elegante."
            styleMatches.isNotEmpty() ->
                "Prendas seleccionadas con etiqueta $targetStyle y paleta de color $colors en sintonía con tus gustos."
            inspirationTags.isNotEmpty() ->
                "Inspirado en los estilos guardados en tu Lookbook y tus preferencias favoritas."
            else ->
                "Emparejamiento armonioso de prendas ($colors) compatible con tu estilo habitual."
        }
    }
}
