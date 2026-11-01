package com.example.myoutfit.domain.recommendation

import android.util.Log
import com.example.myoutfit.data.local.entity.ClothingItem
import com.example.myoutfit.data.local.entity.InspirationItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class GeminiRecommendationRequest(
    val activityPlan: String,
    val desiredVibe: String,
    val useInspirations: Boolean,
    val availableItems: List<ClothingItem>,
    val inspirationItems: List<InspirationItem>,
    val apiKey: String,
)

data class GeminiRecommendationResult(
    val title: String,
    val selectedItemIds: List<Long>,
    val styleTag: String,
    val matchPercentage: Int,
    val matchReason: String,
    val styleAdvice: String? = null,
)

class GeminiOutfitService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build(),
) {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val preferredModelList = listOf(
        "gemini-2.5-flash",
        "gemini-2.0-flash",
        "gemini-1.5-flash",
        "gemini-1.5-flash-latest",
        "gemini-1.5-flash-002",
        "gemini-1.5-flash-001",
        "gemini-1.5-flash-8b",
        "gemini-2.0-flash-exp",
        "gemini-2.5-pro",
        "gemini-1.5-pro",
        "gemini-1.5-pro-latest"
    )

    suspend fun recommendOutfit(request: GeminiRecommendationRequest): Result<GeminiRecommendationResult> = withContext(Dispatchers.IO) {
        val apiKey = request.apiKey.trim().removeSurrounding("\"").removeSurrounding("'")

        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Configura tu API Key de Gemini en el recomendador para obtener sugerencias con IA.")
            )
        }

        if (request.availableItems.isEmpty()) {
            return@withContext Result.failure(
                IllegalStateException("Tu armario está vacío. Agrega prendas antes de pedir recomendaciones con IA.")
            )
        }

        // 1. Discover available models for this specific API key
        val availableModelsResult = fetchAvailableModels(apiKey)

        val candidateModels: List<String> = if (availableModelsResult.isSuccess && availableModelsResult.getOrThrow().isNotEmpty()) {
            val supported = availableModelsResult.getOrThrow()
                .filter { name ->
                    !name.contains("tts", ignoreCase = true) &&
                    !name.contains("embed", ignoreCase = true) &&
                    !name.contains("imagen", ignoreCase = true) &&
                    !name.contains("audio", ignoreCase = true) &&
                    !name.contains("realtime", ignoreCase = true)
                }

            supported.sortedWith { m1, m2 ->
                val i1 = preferredModelList.indexOf(m1)
                val i2 = preferredModelList.indexOf(m2)
                when {
                    i1 >= 0 && i2 >= 0 -> i1.compareTo(i2)
                    i1 >= 0 -> -1
                    i2 >= 0 -> 1
                    else -> m1.compareTo(m2)
                }
            }.ifEmpty { preferredModelList }
        } else {
            val ex = availableModelsResult.exceptionOrNull()
            if (ex != null && (ex is IllegalArgumentException || ex.message?.contains("API") == true || ex.message?.contains("Google") == true)) {
                return@withContext Result.failure(ex)
            }
            preferredModelList
        }

        Log.d("GeminiOutfitService", "Modelos candidatos a probar en orden: $candidateModels")

        // 2. Prepare the prompt
        val systemInstruction = """
            Eres un estilista personal de moda de alta costura y moda cotidiana.
            Tu misión es seleccionar prendas exclusivas del armario real del usuario para crear un outfit completo, armónico y adecuado para las actividades planificadas y el estilo deseado.
            
            REGLAS OBLIGATORIAS:
            1. Selecciona ÚNICAMENTE prendas de la lista proporcionada. Utiliza exclusivamente sus IDs numéricos.
            2. El conjunto debe ser coherente y ponible:
               - Opción A: Prenda superior (Camiseta, Camisa, Top, Sudadera) + Prenda inferior (Pantalón, Vaqueros, Falda, Shorts) + Calzado (y opcionalmente abrigo/chaqueta, bolso o accesorios).
               - Opción B: Prenda de cuerpo entero (Vestido o Mono) + Calzado (y opcionalmente abrigo/chaqueta, bolso o accesorios).
            3. Debes responder SIEMPRE con un JSON con esta estructura exacta:
               {
                 "title": "Nombre creativo y atractivo del outfit en español",
                 "selectedItemIds": [1, 2, 3],
                 "styleTag": "Etiqueta principal (ej. Oficina, Gym, Fiesta, Casual, Elegante, Streetwear)",
                 "matchPercentage": 95,
                 "matchReason": "Explicación detallada en español de por qué este conjunto es perfecto para su actividad y cómo logra el aspecto deseado (y si aplica, cómo se inspira en su lookbook).",
                 "styleAdvice": "Consejo práctico de estilismo (ej. meter la camisa por dentro, remangar mangas, añadir complementos)."
               }
        """.trimIndent()

        val itemsDescription = request.availableItems.joinToString("\n") { item ->
            "- ID ${item.id}: [${item.category.displayName}] Color: ${item.color}, Estilos: ${item.styleTags.joinToString()}, Descripción: ${item.description.ifBlank { "Sin descripción" }}"
        }

        val inspirationsPrompt = if (request.useInspirations && request.inspirationItems.isNotEmpty()) {
            val inspList = request.inspirationItems.asSequence().take(10).joinToString("\n") { insp ->
                "- ${insp.title} (Estilos: ${insp.styleTags.joinToString()})"
            }
            """
            
            TABLERO DE INSPIRACIONES GUARDADAS POR EL USUARIO:
            (Toma estas referencias estéticas como inspiración clave al elegir la combinación de prendas de su armario)
            $inspList
            """.trimIndent()
        } else ""

        val prompt = """
            $systemInstruction

            PRENDAS DISPONIBLES EN EL ARMARIO DEL USUARIO:
            $itemsDescription
            $inspirationsPrompt

            PETICIÓN DEL DÍA:
            - Actividad / Lo que va a hacer hoy: "${request.activityPlan.ifBlank { "Día habitual o casual" }}"
            - Cómo se quiere ver hoy: "${request.desiredVibe.ifBlank { "Con estilo y cómodo" }}"
            - ¿Tener en cuenta inspiraciones?: ${if (request.useInspirations) "SÍ, alinea la selección con su lookbook" else "NO"}

            Por favor, analiza las prendas y elige el mejor conjunto que cumpla con los requisitos. Devuelve solo el JSON válido.
        """.trimIndent()

        // 3. Try models in candidate list
        var lastError: Exception? = null

        for (modelName in candidateModels) {
            try {
                Log.d("GeminiOutfitService", "Llamando a Gemini con modelo: $modelName")
                val result = callGeminiGenerateContent(modelName, apiKey, prompt)
                Log.d("GeminiOutfitService", "¡Respuesta exitosa de Gemini con $modelName!")
                return@withContext Result.success(result)
            } catch (e: Exception) {
                Log.w("GeminiOutfitService", "Fallo con modelo $modelName: ${e.message}")
                lastError = e
                val msg = e.message.orEmpty()
                if (msg.contains("API Key no es válida", ignoreCase = true)) {
                    return@withContext Result.failure(e)
                }
            }
        }

        val fallbackErrorMsg = lastError?.message ?: "No se pudo conectar con Gemini."
        if (fallbackErrorMsg.contains("not found", ignoreCase = true) || fallbackErrorMsg.contains("404")) {
            Result.failure(
                IllegalStateException(
                    "Tu API Key no tiene acceso a los modelos de Gemini (Error 404).\n" +
                    "Asegúrate de generar tu clave directamente en Google AI Studio (aistudio.google.com/app/apikey)."
                )
            )
        } else {
            Result.failure(lastError ?: Exception("No se pudo obtener respuesta de los modelos de Gemini."))
        }
    }

    private fun fetchAvailableModels(apiKey: String): Result<List<String>> {
        val versions = listOf("v1beta", "v1")
        var lastHttpError: String? = null

        for (version in versions) {
            try {
                val url = "https://generativelanguage.googleapis.com/$version/models?key=$apiKey"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("x-goog-api-key", apiKey)
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    val bodyString = response.body.string()
                    if (!response.isSuccessful) {
                        lastHttpError = parseErrorMessage(response.code, bodyString)
                        return@use
                    }

                    val json = JSONObject(bodyString)
                    val modelsArray = json.optJSONArray("models") ?: return@use
                    val supported = mutableListOf<String>()
                    for (i in 0 until modelsArray.length()) {
                        val modelObj = modelsArray.optJSONObject(i) ?: continue
                        val name = modelObj.optString("name").removePrefix("models/")
                        val methods = modelObj.optJSONArray("supportedGenerationMethods")
                        val canGenerate = if (methods != null) {
                            (0 until methods.length()).any { methods.optString(it) == "generateContent" }
                        } else false

                        val modalities = modelObj.optJSONArray("responseModalities")
                        val supportsText = if (modalities != null) {
                            (0 until modalities.length()).any { modalities.optString(it).equals("TEXT", ignoreCase = true) }
                        } else true

                        val isExcluded = name.contains("tts", ignoreCase = true) ||
                            name.contains("embed", ignoreCase = true) ||
                            name.contains("imagen", ignoreCase = true) ||
                            name.contains("audio", ignoreCase = true) ||
                            name.contains("realtime", ignoreCase = true)

                        if (canGenerate && supportsText && !isExcluded) {
                            supported.add(name)
                        }
                    }

                    if (supported.isNotEmpty()) {
                        Log.d("GeminiOutfitService", "Modelos de texto detectados: $supported")
                        return Result.success(supported)
                    }
                }
            } catch (_: Exception) {
                // Try next version on network or parsing error
            }
        }

        if (lastHttpError != null) {
            return Result.failure(IllegalArgumentException(lastHttpError))
        }

        return Result.failure(
            IllegalStateException(
                "No se encontraron modelos de texto de Gemini disponibles para esta API Key. " +
                "Verifica que tu clave haya sido creada en Google AI Studio (aistudio.google.com/app/apikey)."
            )
        )
    }

    private fun callGeminiGenerateContent(
        modelName: String,
        apiKey: String,
        prompt: String
    ): GeminiRecommendationResult {
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
                                put(
                                    JSONObject().apply {
                                        put("text", prompt)
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
                        put("temperature", 0.7)
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
                        return parseGeminiResponse(bodyString)
                    }

                    val errorMsg = parseErrorMessage(response.code, bodyString)

                    if (response.code == 400 && bodyString.contains("responseMimeType", ignoreCase = true)) {
                        return callGeminiGenerateContentWithoutMimeType(version, modelName, apiKey, prompt)
                    }

                    if (response.code == 400 && errorMsg.contains("API Key no es válida", ignoreCase = true)) {
                        throw IllegalArgumentException(errorMsg)
                    }

                    if (response.code == 429) {
                        throw IOException("Límite de cuota alcanzado. Espera unos segundos y vuelve a probar.")
                    }

                    lastException = IOException("Error con $modelName ($version): $errorMsg")
                }
            } catch (e: Exception) {
                if (e is IllegalArgumentException) throw e
                lastException = e
            }
        }

        throw lastException ?: IOException("No se pudo obtener respuesta del modelo $modelName")
    }

    private fun callGeminiGenerateContentWithoutMimeType(
        version: String,
        modelName: String,
        apiKey: String,
        prompt: String
    ): GeminiRecommendationResult {
        val url = "https://generativelanguage.googleapis.com/$version/models/$modelName:generateContent?key=$apiKey"

        val requestJson = JSONObject().apply {
            val contentsArray = JSONArray().apply {
                put(
                    JSONObject().apply {
                        put("role", "user")
                        val partsArray = JSONArray().apply {
                            put(
                                JSONObject().apply {
                                    put("text", prompt)
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
                    put("temperature", 0.7)
                }
            )
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
                val errorMsg = parseErrorMessage(response.code, bodyString)
                throw IOException("Error ($modelName): $errorMsg")
            }
            return parseGeminiResponse(bodyString)
        }
    }

    private fun parseErrorMessage(code: Int, bodyString: String): String {
        val rawMsg = try {
            val json = JSONObject(bodyString)
            val errObj = json.optJSONObject("error")
            errObj?.optString("message") ?: "HTTP $code"
        } catch (_: Exception) {
            "HTTP $code: $bodyString"
        }

        return when {
            rawMsg.contains("API key not valid", ignoreCase = true) || (code == 400 && rawMsg.contains("API key", ignoreCase = true)) -> {
                "La API Key no es válida. Asegúrate de copiar la clave completa generada en Google AI Studio (aistudio.google.com/app/apikey)."
            }
            rawMsg.contains("Generative Language API", ignoreCase = true) || code == 403 -> {
                "La API 'Generative Language API' no está activada en tu proyecto de Google Cloud o la clave tiene restricciones. Te recomendamos generar una API Key nueva y directa desde Google AI Studio (aistudio.google.com/app/apikey)."
            }
            rawMsg.contains("location", ignoreCase = true) || rawMsg.contains("region", ignoreCase = true) -> {
                "Tu región o red actual no tiene soporte directo para Gemini. Si estás usando una VPN o proxy, prueba a desactivarlo."
            }
            rawMsg.contains("quota", ignoreCase = true) || code == 429 -> {
                "Has alcanzado el límite de peticiones de Gemini temporalmente. Espera unos segundos y vuelve a probar."
            }
            else -> rawMsg
        }
    }

    private fun parseGeminiResponse(bodyString: String): GeminiRecommendationResult {
        val json = JSONObject(bodyString)
        val candidates = json.optJSONArray("candidates")
            ?: throw IOException("Gemini no devolvió candidatos válidos.")

        if (candidates.length() == 0) {
            throw IOException("Gemini devolvió una respuesta vacía.")
        }

        val firstCandidate = candidates.getJSONObject(0)
        val content = firstCandidate.optJSONObject("content")
            ?: throw IOException("No se encontró contenido en la respuesta de Gemini.")

        val parts = content.optJSONArray("parts")
            ?: throw IOException("No se encontraron partes de texto en la respuesta.")

        val rawText = parts.getJSONObject(0).optString("text")
        val cleanJson = cleanJsonResponse(rawText)
        val outfitJson = JSONObject(cleanJson)

        val title = outfitJson.optString("title", "Outfit Recomendado por Gemini")
        val styleTag = outfitJson.optString("styleTag", "Personalizado")
        val matchPercentage = outfitJson.optInt("matchPercentage", 92).coerceIn(60, 99)
        val matchReason = outfitJson.optString(
            "matchReason",
            "Conjunto seleccionado especialmente para tu actividad de hoy."
        )
        val styleAdvice = outfitJson.optString("styleAdvice", "").ifBlank { null }

        val selectedIdsJson = outfitJson.optJSONArray("selectedItemIds") ?: JSONArray()
        val selectedIds = mutableListOf<Long>()
        for (i in 0 until selectedIdsJson.length()) {
            selectedIds.add(selectedIdsJson.getLong(i))
        }

        if (selectedIds.isEmpty()) {
            throw IOException("Gemini no pudo encontrar prendas adecuadas en tu armario para este plan.")
        }

        return GeminiRecommendationResult(
            title = title,
            selectedItemIds = selectedIds,
            styleTag = styleTag,
            matchPercentage = matchPercentage,
            matchReason = matchReason,
            styleAdvice = styleAdvice
        )
    }

    private fun cleanJsonResponse(raw: String): String {
        var trimmed = raw.trim()
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.removePrefix("```json").trim()
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.removePrefix("```").trim()
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.removeSuffix("```").trim()
        }
        val firstBrace = trimmed.indexOf('{')
        val lastBrace = trimmed.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            trimmed = trimmed.substring(firstBrace, lastBrace + 1)
        }
        return trimmed
    }
}
