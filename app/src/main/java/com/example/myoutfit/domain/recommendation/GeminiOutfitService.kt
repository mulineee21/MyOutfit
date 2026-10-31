package com.example.myoutfit.domain.recommendation

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
    val apiKey: String
)

data class GeminiRecommendationResult(
    val title: String,
    val selectedItemIds: List<Long>,
    val styleTag: String,
    val matchPercentage: Int,
    val matchReason: String,
    val styleAdvice: String? = null
)

class GeminiOutfitService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()
) {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val preferredModelList = listOf(
        "gemini-1.5-flash-latest",
        "gemini-2.0-flash",
        "gemini-1.5-flash",
        "gemini-1.5-flash-002",
        "gemini-1.5-flash-001",
        "gemini-1.5-pro-latest",
        "gemini-1.5-pro",
        "gemini-pro"
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

        // 1. Resolve which models are supported for this API key
        val targetModel = resolveModel(apiKey)

        // 2. Prepare the prompt
        val systemInstruction = """
            Eres un estilista personal de moda de alta costura y moda cotidiana.
            Tu misión es seleccionar prendas exclusivas del armario real del usuario para crear un outfit completo, armónico y adecuado para las actividades planificadas y el estilo deseado.
            
            REGLAS OBLIGATORIAS:
            1. Selecciona ÚNICAMENTE prendas de la lista proporcionada. Utiliza exclusivamente sus IDs numéricos.
            2. El conjunto debe ser coherente y ponible:
               - Opción A: Prenda superior (Camiseta, Camisa, Top, Sudadera) + Prenda inferior (Pantalón, Falda, Shorts) + Calzado (y opcionalmente abrigo/chaqueta, bolso o accesorios).
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
            val inspList = request.inspirationItems.take(10).joinToString("\n") { insp ->
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

        // 3. Try target model, and fallback models if 404 occurs
        val candidateModels = linkedSetOf<String>().apply {
            add(targetModel)
            addAll(preferredModelList)
        }.toList()

        var lastError: Exception? = null

        for (modelName in candidateModels) {
            try {
                val result = callGeminiGenerateContent(modelName, apiKey, prompt)
                return@withContext Result.success(result)
            } catch (e: Exception) {
                lastError = e
                // If the error was an invalid API key, fail fast without trying other models
                if (e is IllegalArgumentException) {
                    return@withContext Result.failure(e)
                }
            }
        }

        Result.failure(lastError ?: Exception("No se pudo obtener respuesta de los modelos de Gemini."))
    }

    private fun resolveModel(apiKey: String): String {
        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return preferredModelList.first()
                val bodyString = response.body.string()
                val json = JSONObject(bodyString)
                val modelsArray = json.optJSONArray("models") ?: return preferredModelList.first()

                val supportedModels = mutableListOf<String>()
                for (i in 0 until modelsArray.length()) {
                    val modelObj = modelsArray.optJSONObject(i) ?: continue
                    val name = modelObj.optString("name").removePrefix("models/")
                    val methods = modelObj.optJSONArray("supportedGenerationMethods")
                    val canGenerate = if (methods != null) {
                        (0 until methods.length()).any { methods.optString(it) == "generateContent" }
                    } else false

                    if (canGenerate) {
                        supportedModels.add(name)
                    }
                }

                for (preferred in preferredModelList) {
                    if (supportedModels.contains(preferred)) {
                        return preferred
                    }
                }
                return supportedModels.firstOrNull() ?: preferredModelList.first()
            }
        } catch (_: Exception) {
            return preferredModelList.first()
        }
    }

    private fun callGeminiGenerateContent(
        modelName: String,
        apiKey: String,
        prompt: String
    ): GeminiRecommendationResult {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"

        val requestJson = JSONObject().apply {
            val contentsArray = JSONArray().apply {
                put(JSONObject().apply {
                    val partsArray = JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    }
                    put("parts", partsArray)
                })
            }
            put("contents", contentsArray)

            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
                put("responseMimeType", "application/json")
            })
        }

        val requestBody = requestJson.toString().toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            val bodyString = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                val errorMsg = try {
                    val errJson = JSONObject(bodyString)
                    errJson.optJSONObject("error")?.optString("message") ?: "HTTP ${response.code}"
                } catch (_: Exception) {
                    "HTTP ${response.code}: $bodyString"
                }

                if (response.code == 400 && errorMsg.contains("API key", ignoreCase = true)) {
                    throw IllegalArgumentException("La API Key introducida no es válida. Asegúrate de haberla copiado correctamente de Google AI Studio (aistudio.google.com).")
                }

                if (response.code == 404) {
                    throw IOException("Modelo $modelName no disponible: $errorMsg")
                }

                if (response.code == 429) {
                    throw IOException("Límite de solicitudes de Gemini alcanzado temporalmente. Espera unos segundos.")
                }

                // If responseMimeType is not supported on this model, retry without it
                if (errorMsg.contains("responseMimeType", ignoreCase = true)) {
                    return callGeminiGenerateContentWithoutMimeType(modelName, apiKey, prompt)
                }

                throw IOException("Error de Gemini ($modelName): $errorMsg")
            }

            return parseGeminiResponse(bodyString)
        }
    }

    private fun callGeminiGenerateContentWithoutMimeType(
        modelName: String,
        apiKey: String,
        prompt: String
    ): GeminiRecommendationResult {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"

        val requestJson = JSONObject().apply {
            val contentsArray = JSONArray().apply {
                put(JSONObject().apply {
                    val partsArray = JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    }
                    put("parts", partsArray)
                })
            }
            put("contents", contentsArray)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
            })
        }

        val requestBody = requestJson.toString().toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            val bodyString = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("Error HTTP ${response.code}: $bodyString")
            }
            return parseGeminiResponse(bodyString)
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
