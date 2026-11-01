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
        "gemini-3.8-flash",
        "gemini-3.7-flash",
        "gemini-3.6-flash",
        "gemini-3.5-flash",
        "gemini-3-flash-preview",
        "gemini-flash-latest",
        "gemini-flash-lite-latest",
        "gemini-2.5-flash",
        "gemini-2.0-flash",
        "gemini-1.5-flash",
        "gemini-2.5-pro",
        "gemini-1.5-pro",
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
            Eres un estilista personal de moda inteligente y empático.
            Tu misión es crear un conjunto de ropa (outfit) combinando EXCLUSIVAMENTE prendas que el usuario tiene registradas en su armario real.
            
            REGLAS CRÍTICAS DE SELECCIÓN:
            1. SELECCIONA OBLIGATORIAMENTE prendas que existan en la lista 'PRENDAS DISPONIBLES EN EL ARMARIO DEL USUARIO' proporcionada a continuación.
            2. En el campo 'selectedItemIds', incluye SIEMPRE los IDs numéricos exactos de las prendas que has elegido (ejemplo: [1, 2]). NUNCA devuelvas una lista vacía ni inventes IDs.
            3. Selecciona la mejor combinación posible con las prendas que el usuario TIENE en su armario:
               - Si tiene prendas superiores e inferiores, combina al menos 1 prenda superior (camiseta, camisa, sudadera) y 1 prenda inferior (pantalón, vaqueros, falda, shorts).
               - Si tiene vestidos o monos, selecciona 1 prenda de cuerpo entero.
               - Añade calzado, abrigo o accesorios si el usuario los tiene registrados en su lista.
               - Si al usuario le falta alguna categoría (por ejemplo, no tiene calzado registrado), crea el conjunto con las prendas que SÍ tiene registradas.
            4. En 'matchReason', explica detalladamente por qué has elegido ESTAS prendas del armario del usuario, mencionando cada prenda seleccionada por su categoría y color.
            5. En 'styleAdvice', ofrece consejos prácticos sobre cómo combinar y llevar estas prendas (ej. remangar, meter por dentro, tipo de calzado recomendado si no lo tiene, complementos, etc.).
            
            FORMATO DE RESPUESTA:
            Debes responder ÚNICAMENTE con un JSON con esta estructura exacta:
            {
              "title": "Nombre creativo del outfit en español (ej. Look Deportivo Cómodo para Gym)",
              "selectedItemIds": [1, 2],
              "styleTag": "Estilo principal (ej. Gym, Oficina, Casual, Elegante, De fiesta)",
              "matchPercentage": 96,
              "matchReason": "Explicación detallada mencionando las prendas concretas de su armario seleccionadas y por qué son ideales para la actividad.",
              "styleAdvice": "Consejo de estilo práctico para lucir el conjunto."
            }
        """.trimIndent()

        val itemsDescription = request.availableItems.joinToString("\n") { item ->
            "- ID ${item.id}: [Categoría: ${item.category.displayName}] Color: ${item.color.ifBlank { "No especificado" }}, Estilos: ${item.styleTags.joinToString().ifBlank { "General" }}, Descripción: ${item.description.ifBlank { "Sin descripción adicional" }}"
        }

        val inspirationsPrompt = if (request.useInspirations && request.inspirationItems.isNotEmpty()) {
            val inspList = request.inspirationItems.asSequence().take(10).joinToString("\n") { insp ->
                "- ${insp.title} (Estilos: ${insp.styleTags.joinToString().ifBlank { "General" }})"
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
            - Actividad / Plan: "${request.activityPlan.ifBlank { "Día habitual / Casual" }}"
            - Cómo se quiere ver hoy: "${request.desiredVibe.ifBlank { "Con estilo y cómodo" }}"
            - ¿Inspirarse en Lookbook?: ${if (request.useInspirations) "SÍ, ten en cuenta sus inspiraciones guardadas" else "NO"}

            Elige las mejores prendas de la lista anterior y devuelve el JSON correspondiente.
        """.trimIndent()

        Log.d("GeminiOutfitService", "Prompt enviado a Gemini:\n$prompt")

        // 3. Try models in candidate list
        var lastError: Exception? = null

        for (modelName in candidateModels) {
            try {
                Log.d("GeminiOutfitService", "Llamando a Gemini con modelo: $modelName")
                val result = callGeminiGenerateContent(modelName, apiKey, prompt)
                Log.d("GeminiOutfitService", "¡Respuesta exitosa de Gemini con $modelName! Selected IDs: ${result.selectedItemIds}")
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
        Log.d("GeminiOutfitService", "Respuesta cruda de Gemini:\n$bodyString")
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
        Log.d("GeminiOutfitService", "Texto limpio de partes de Gemini:\n$rawText")
        val cleanJson = cleanJsonResponse(rawText)
        val outfitJson = JSONObject(cleanJson)

        val title = outfitJson.optString("title", "Outfit Recomendado por Gemini")
        val styleTag = outfitJson.optString("styleTag", "Personalizado")
        val matchPercentage = outfitJson.optInt("matchPercentage", 95).coerceIn(60, 99)
        val matchReason = outfitJson.optString(
            "matchReason",
            "Conjunto seleccionado especialmente para tu actividad de hoy."
        )
        val styleAdvice = outfitJson.optString("styleAdvice", "").ifBlank { null }

        // Robust parsing of selectedItemIds (arrays of numbers, strings, or objects)
        val itemsArray = outfitJson.optJSONArray("selectedItemIds")
            ?: outfitJson.optJSONArray("selected_item_ids")
            ?: outfitJson.optJSONArray("selectedItems")
            ?: outfitJson.optJSONArray("items")
            ?: JSONArray()

        val selectedIds = mutableListOf<Long>()
        for (i in 0 until itemsArray.length()) {
            val optLong = itemsArray.optLong(i, -1L)
            if (optLong != -1L) {
                selectedIds.add(optLong)
            } else {
                val optObj = itemsArray.optJSONObject(i)
                if (optObj != null) {
                    val idFromObj = optObj.optLong("id", optObj.optLong("selectedItemId", -1L))
                    if (idFromObj != -1L) selectedIds.add(idFromObj)
                } else {
                    val strVal = itemsArray.optString(i)
                    val extractedId = strVal.filter { it.isDigit() }.toLongOrNull()
                    if (extractedId != null) selectedIds.add(extractedId)
                }
            }
        }

        Log.d("GeminiOutfitService", "IDs extraídos de prendas: $selectedIds")

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
