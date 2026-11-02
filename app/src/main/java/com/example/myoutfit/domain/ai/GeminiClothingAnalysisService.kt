package com.example.myoutfit.domain.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import com.example.myoutfit.data.local.entity.ClothingCategory
import com.example.myoutfit.domain.wishlist.ExtractedProductMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ClothingAiClassification(
    val category: ClothingCategory,
    val color: String,
    val styleTags: List<String>,
    val description: String
)

data class ProductAiAnalysis(
    val title: String,
    val category: String,
    val styleTags: List<String>,
    val color: String,
    val description: String,
    val price: String,
    val storeName: String,
    val imageUrl: String
)

class GeminiClothingAnalysisService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()
) {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val preferredModelList = listOf(
        "gemini-2.5-flash",
        "gemini-2.0-flash",
        "gemini-1.5-flash",
        "gemini-2.5-pro",
        "gemini-1.5-pro"
    )

    /**
     * Categorizes a clothing item image using Gemini multimodal vision.
     */
    suspend fun categorizeClothingImage(
        context: Context,
        imageUri: Uri,
        apiKey: String
    ): Result<ClothingAiClassification> = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim().removeSurrounding("\"").removeSurrounding("'")
        val bitmap = loadBitmapFromUri(context, imageUri)
            ?: return@withContext Result.failure(IllegalArgumentException("No se pudo cargar la imagen para analizar."))

        if (trimmedKey.isBlank()) {
            // Local fallback classification when no API key is provided
            return@withContext Result.success(
                ClothingAiClassification(
                    category = ClothingCategory.TOPS,
                    color = "Negro",
                    styleTags = listOf("Casual"),
                    description = "Prenda añadida al armario"
                )
            )
        }

        val base64Image = bitmapToBase64(bitmap)
        val prompt = """
            Eres un experto estilista de moda y clasificador de prendas de vestir.
            Analiza con atención la prenda mostrada en la imagen y clasifícala para el armario del usuario.

            CATEGORÍAS PERMITIDAS (Elige EXACTAMENTE una de estas opciones):
            - TOPS (Tops y partes superiores)
            - T_SHIRTS (Camisetas)
            - SHIRTS (Camisas y blusas)
            - HOODIES (Sudaderas y hoodies)
            - SWEATERS (Jerséis y punto)
            - PANTS (Pantalones)
            - JEANS (Vaqueros / Jeans)
            - SHORTS (Bermudas y shorts)
            - SKIRTS (Faldas)
            - DRESSES (Vestidos y monos)
            - JACKETS (Chaquetas y cazadoras)
            - COATS (Abrigos y parkas)
            - SHOES (Calzado y zapatillas)
            - ACCESSORIES (Accesorios, bolsos, cinturones, gorras)

            INSTRUCCIONES:
            1. 'category': El identificador en mayúsculas de la categoría arriba indicada (ej. 'T_SHIRTS', 'HOODIES', 'JEANS', 'SHOES').
            2. 'color': Color principal visible (ej. Negro, Blanco, Azul, Rojo, Gris, Beige, Verde, Marrón, etc.).
            3. 'styleTags': Lista con entre 1 y 4 etiquetas de estilo relevantes en español (ej. ["Gótico", "Streetwear"], ["Deporte", "Casual"], ["Formal", "Elegante"], ["Vintage", "Y2K", "Grunge"]).
            4. 'description': Breve descripción concisa de la prenda y detalles visibles (ej. "Camiseta negra oversize con corte holgado y cuello redondo").

            FORMATO OBLIGATORIO: Devuelve ÚNICAMENTE un objeto JSON válido con esta estructura:
            {
              "category": "T_SHIRTS",
              "color": "Negro",
              "styleTags": ["Gótico", "Streetwear"],
              "description": "Camiseta negra oversize de algodón..."
            }
        """.trimIndent()

        val models = getCandidateModels(trimmedKey)
        var lastError: Exception? = null

        for (model in models) {
            try {
                val responseJson = callGeminiMultimodal(model, trimmedKey, prompt, base64Image)
                val parsed = parseClothingClassification(responseJson)
                return@withContext Result.success(parsed)
            } catch (e: Exception) {
                Log.w("GeminiAnalysis", "Fallo modelo $model: ${e.message}")
                lastError = e
            }
        }

        Result.failure(lastError ?: Exception("No se pudo categorizar la prenda con IA."))
    }

    /**
     * Analyzes a product link / metadata (Shein, Amazon, Zara, etc.) to extract clean info, styles, and categories.
     */
    suspend fun analyzeProductLink(
        url: String,
        metadata: ExtractedProductMetadata,
        apiKey: String
    ): Result<ProductAiAnalysis> = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim().removeSurrounding("\"").removeSurrounding("'")

        if (trimmedKey.isBlank()) {
            // Smart local heuristic fallback
            val heuristic = createHeuristicAnalysis(url, metadata)
            return@withContext Result.success(heuristic)
        }

        val prompt = """
            Eres un experto en moda digital, tendencias y compras online.
            Un usuario quiere añadir una prenda de ropa a su 'Lista de Deseos' en MyOutfit a partir de un enlace o datos de una tienda online (ej. SHEIN, Amazon, Zara, etc.).

            DATOS DEL PRODUCTO:
            - Tienda: ${metadata.storeName.ifBlank { "Tienda Online" }}
            - Enlace / URL: $url
            - Título original detectado: "${metadata.title}"
            - Descripción detectada: "${metadata.description}"
            - Precio detectado: "${metadata.price}"

            INSTRUCCIONES:
            1. 'title': Genera un nombre limpio, comercial y atractivo para la prenda en español (ej. "Sudadera Crop Gótica con Capucha", "Pantalón Cargo Deportivo", "Vestido Ajustado Negro Elegante").
            2. 'category': Clasifica el tipo de prenda (ej. "Camisetas", "Sudaderas", "Pantalones", "Vaqueros", "Vestidos", "Faldas", "Calzado", "Chaquetas", "Accesorios", "Deporte").
            3. 'styleTags': Extrae o deduce entre 2 y 4 estilos de moda clave a los que pertenece la prenda (ej. "Gótico", "Deporte", "Streetwear", "Casual", "Vintage", "Grunge", "Y2K", "Formal", "Elegante", "Boho", "Cyberpunk"). Es fundamental identificar si es estilo 'Gótico', 'Deporte', etc. según su diseño.
            4. 'color': Color principal deducido (ej. Negro, Blanco, Azul, Rojo, Beige, etc.).
            5. 'description': Resumen atractivo de 1-2 frases destacando cómo se ve y con qué tipo de outfits combinaría.
            6. 'price': Precio estimado con símbolo (ej. "15,99 €"), manteniendo el detectado o dejándolo vacío si no se conoce.
            7. 'storeName': Nombre de la tienda (ej. "SHEIN", "Amazon", "Zara", etc.).

            FORMATO OBLIGATORIO: Devuelve ÚNICAMENTE un objeto JSON válido con esta estructura:
            {
              "title": "...",
              "category": "...",
              "styleTags": ["Gótico", "Streetwear"],
              "color": "...",
              "description": "...",
              "price": "...",
              "storeName": "..."
            }
        """.trimIndent()

        val models = getCandidateModels(trimmedKey)
        var lastError: Exception? = null

        for (model in models) {
            try {
                val responseJson = callGeminiText(model, trimmedKey, prompt)
                val analysis = parseProductAnalysis(responseJson, metadata)
                return@withContext Result.success(analysis)
            } catch (e: Exception) {
                Log.w("GeminiAnalysis", "Fallo análisis con $model: ${e.message}")
                lastError = e
            }
        }

        // If network/AI fails, fallback to heuristic extraction
        val fallback = createHeuristicAnalysis(url, metadata)
        Result.success(fallback)
    }

    private fun callGeminiMultimodal(
        modelName: String,
        apiKey: String,
        textPrompt: String,
        base64Jpeg: String
    ): String {
        val versions = listOf("v1beta", "v1")
        var lastException: Exception? = null

        for (version in versions) {
            val url = "https://generativelanguage.googleapis.com/$version/models/$modelName:generateContent?key=$apiKey"

            val requestJson = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put("role", "user")
                            val partsArray = JSONArray().apply {
                                put(JSONObject().apply { put("text", textPrompt) })
                                put(
                                    JSONObject().apply {
                                        put(
                                            "inlineData",
                                            JSONObject().apply {
                                                put("mimeType", "image/jpeg")
                                                put("data", base64Jpeg)
                                            }
                                        )
                                    }
                                )
                            }
                            put("parts", partsArray)
                        }
                    )
                }
                put("contents", contentsArray)

                put(
                    "generationConfig",
                    JSONObject().apply {
                        put("temperature", 0.4)
                        put("responseMimeType", "application/json")
                    }
                )
            }

            val requestBody = requestJson.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(url)
                .addHeader("x-goog-api-key", apiKey)
                .post(requestBody)
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val bodyString = response.body.string()
                    if (response.isSuccessful) {
                        return extractTextFromResponse(bodyString)
                    }
                    if (response.code == 400 && bodyString.contains("responseMimeType", ignoreCase = true)) {
                        return callGeminiMultimodalWithoutMimeType(version, modelName, apiKey, textPrompt, base64Jpeg)
                    }
                    lastException = IOException("HTTP ${response.code}: $bodyString")
                }
            } catch (e: Exception) {
                lastException = e
            }
        }

        throw lastException ?: IOException("No se pudo comunicar con $modelName")
    }

    private fun callGeminiMultimodalWithoutMimeType(
        version: String,
        modelName: String,
        apiKey: String,
        textPrompt: String,
        base64Jpeg: String
    ): String {
        val url = "https://generativelanguage.googleapis.com/$version/models/$modelName:generateContent?key=$apiKey"
        val requestJson = JSONObject().apply {
            val contentsArray = JSONArray().apply {
                put(
                    JSONObject().apply {
                        put("role", "user")
                        val partsArray = JSONArray().apply {
                            put(JSONObject().apply { put("text", "$textPrompt\n\nResponde estrictamente en formato JSON.") })
                            put(
                                JSONObject().apply {
                                    put(
                                        "inlineData",
                                        JSONObject().apply {
                                            put("mimeType", "image/jpeg")
                                            put("data", base64Jpeg)
                                        }
                                    )
                                }
                            )
                        }
                        put("parts", partsArray)
                    }
                )
            }
            put("contents", contentsArray)
        }

        val requestBody = requestJson.toString().toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", apiKey)
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            val bodyString = response.body.string()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: $bodyString")
            }
            return extractTextFromResponse(bodyString)
        }
    }

    private fun callGeminiText(
        modelName: String,
        apiKey: String,
        textPrompt: String
    ): String {
        val versions = listOf("v1beta", "v1")
        var lastException: Exception? = null

        for (version in versions) {
            val url = "https://generativelanguage.googleapis.com/$version/models/$modelName:generateContent?key=$apiKey"

            val requestJson = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put("role", "user")
                            val partsArray = JSONArray().apply {
                                put(JSONObject().apply { put("text", textPrompt) })
                            }
                            put("parts", partsArray)
                        }
                    )
                }
                put("contents", contentsArray)

                put(
                    "generationConfig",
                    JSONObject().apply {
                        put("temperature", 0.4)
                        put("responseMimeType", "application/json")
                    }
                )
            }

            val requestBody = requestJson.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(url)
                .addHeader("x-goog-api-key", apiKey)
                .post(requestBody)
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val bodyString = response.body.string()
                    if (response.isSuccessful) {
                        return extractTextFromResponse(bodyString)
                    }
                    lastException = IOException("HTTP ${response.code}: $bodyString")
                }
            } catch (e: Exception) {
                lastException = e
            }
        }

        throw lastException ?: IOException("No se pudo comunicar con $modelName")
    }

    private fun extractTextFromResponse(responseBody: String): String {
        val root = JSONObject(responseBody)
        val candidates = root.optJSONArray("candidates") ?: throw IOException("Sin candidatos")
        if (candidates.length() == 0) throw IOException("Candidatos vacíos")
        val content = candidates.getJSONObject(0).optJSONObject("content") ?: throw IOException("Sin contenido")
        val parts = content.optJSONArray("parts") ?: throw IOException("Sin parts")
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val part = parts.getJSONObject(i)
            sb.append(part.optString("text"))
        }
        return sb.toString().trim()
    }

    private fun parseClothingClassification(jsonRaw: String): ClothingAiClassification {
        val cleaned = jsonRaw.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val json = JSONObject(cleaned)

        val catString = json.optString("category", "TOPS").uppercase()
        val category = ClothingCategory.entries.firstOrNull { it.name == catString }
            ?: ClothingCategory.entries.firstOrNull { it.displayName.equals(catString, ignoreCase = true) }
            ?: ClothingCategory.TOPS

        val color = json.optString("color", "Negro").trim().replaceFirstChar { it.uppercase() }

        val tags = mutableListOf<String>()
        val tagsArray = json.optJSONArray("styleTags")
        if (tagsArray != null) {
            for (i in 0 until tagsArray.length()) {
                val tag = tagsArray.optString(i).trim().replaceFirstChar { it.uppercase() }
                if (tag.isNotBlank()) tags.add(tag)
            }
        }
        if (tags.isEmpty()) tags.add("Casual")

        val description = json.optString("description", "Prenda clasificada con IA").trim()

        return ClothingAiClassification(
            category = category,
            color = color,
            styleTags = tags.distinct(),
            description = description
        )
    }

    private fun parseProductAnalysis(jsonRaw: String, metadata: ExtractedProductMetadata): ProductAiAnalysis {
        val cleaned = jsonRaw.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val json = JSONObject(cleaned)

        val title = json.optString("title").ifBlank { metadata.title }.trim()
        val category = json.optString("category", "Moda").trim()
        val color = json.optString("color", "").trim()
        val description = json.optString("description").ifBlank { metadata.description }.trim()
        val price = json.optString("price").ifBlank { metadata.price }.trim()
        val store = json.optString("storeName").ifBlank { metadata.storeName }.trim()

        val tags = mutableListOf<String>()
        val tagsArray = json.optJSONArray("styleTags")
        if (tagsArray != null) {
            for (i in 0 until tagsArray.length()) {
                val tag = tagsArray.optString(i).trim().replaceFirstChar { it.uppercase() }
                if (tag.isNotBlank()) tags.add(tag)
            }
        }
        if (tags.isEmpty()) tags.add("Casual")

        return ProductAiAnalysis(
            title = title,
            category = category,
            styleTags = tags.distinct(),
            color = color,
            description = description,
            price = price,
            storeName = store,
            imageUrl = metadata.imageUrl
        )
    }

    private fun createHeuristicAnalysis(url: String, metadata: ExtractedProductMetadata): ProductAiAnalysis {
        val fullText = "${metadata.title} ${metadata.description} $url".lowercase()
        val detectedTags = mutableListOf<String>()

        if (fullText.contains("goth") || fullText.contains("gotic") || fullText.contains("dark") || fullText.contains("grunge")) {
            detectedTags.add("Gótico")
        }
        if (fullText.contains("deport") || fullText.contains("sport") || fullText.contains("gym") || fullText.contains("fitness") || fullText.contains("run")) {
            detectedTags.add("Deporte")
        }
        if (fullText.contains("street") || fullText.contains("urban") || fullText.contains("hoodie") || fullText.contains("cargo")) {
            detectedTags.add("Streetwear")
        }
        if (fullText.contains("formal") || fullText.contains("elegante") || fullText.contains("blazer") || fullText.contains("traje")) {
            detectedTags.add("Formal")
        }
        if (fullText.contains("vintage") || fullText.contains("retro") || fullText.contains("y2k")) {
            detectedTags.add("Vintage")
        }
        if (detectedTags.isEmpty()) {
            detectedTags.add("Casual")
        }

        val category = when {
            fullText.contains("camiseta") || fullText.contains("t-shirt") || fullText.contains("tee") -> "Camisetas"
            fullText.contains("sudadera") || fullText.contains("hoodie") || fullText.contains("sweater") -> "Sudaderas"
            fullText.contains("pantalon") || fullText.contains("pants") || fullText.contains("jogger") -> "Pantalones"
            fullText.contains("vaquero") || fullText.contains("jean") || fullText.contains("denim") -> "Vaqueros"
            fullText.contains("vestido") || fullText.contains("dress") -> "Vestidos"
            fullText.contains("falda") || fullText.contains("skirt") -> "Faldas"
            fullText.contains("zapat") || fullText.contains("shoe") || fullText.contains("sneaker") -> "Calzado"
            fullText.contains("chaqueta") || fullText.contains("jacket") || fullText.contains("cazadora") -> "Chaquetas"
            else -> "Moda"
        }

        return ProductAiAnalysis(
            title = metadata.title.ifBlank { "Prenda de ${metadata.storeName}" },
            category = category,
            styleTags = detectedTags.distinct(),
            color = if (fullText.contains("negro") || fullText.contains("black")) "Negro" else if (fullText.contains("blanco") || fullText.contains("white")) "Blanco" else "",
            description = metadata.description.ifBlank { "Prenda guardada desde ${metadata.storeName}" },
            price = metadata.price,
            storeName = metadata.storeName,
            imageUrl = metadata.imageUrl
        )
    }

    private fun getCandidateModels(apiKey: String): List<String> {
        return preferredModelList
    }

    private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
        } catch (e: Exception) {
            Log.e("GeminiAnalysis", "Error cargando bitmap", e)
            null
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap, maxDimension: Int = 1024, quality: Int = 85): String {
        val scaled = if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
            val ratio = minOf(maxDimension.toFloat() / bitmap.width, maxDimension.toFloat() / bitmap.height)
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).toInt().coerceAtLeast(1),
                (bitmap.height * ratio).toInt().coerceAtLeast(1),
                true
            )
        } else {
            bitmap
        }
        val outputStream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        val bytes = outputStream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
