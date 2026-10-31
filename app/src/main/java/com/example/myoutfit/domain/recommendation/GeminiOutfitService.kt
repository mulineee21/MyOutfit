package com.example.myoutfit.domain.recommendation

import com.example.myoutfit.data.local.entity.ClothingItem
import com.example.myoutfit.data.local.entity.InspirationItem
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

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

class GeminiOutfitService {

    suspend fun recommendOutfit(request: GeminiRecommendationRequest): Result<GeminiRecommendationResult> = withContext(Dispatchers.IO) {
        try {
            if (request.apiKey.isBlank()) {
                return@withContext Result.failure(
                    IllegalStateException("Configura tu API Key de Gemini en el recomendador para obtener sugerencias con IA.")
                )
            }

            if (request.availableItems.isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException("Tu armario está vacío. Agrega prendas antes de pedir recomendaciones con IA.")
                )
            }

            val model = GenerativeModel(
                modelName = "gemini-1.5-flash",
                apiKey = request.apiKey.trim(),
                generationConfig = generationConfig {
                    temperature = 0.7f
                    responseMimeType = "application/json"
                },
                systemInstruction = content {
                    text(
                        """
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
                    )
                }
            )

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
                PRENDAS DISPONIBLES EN EL ARMARIO DEL USUARIO:
                $itemsDescription
                $inspirationsPrompt

                PETICIÓN DEL DÍA:
                - Actividad / Lo que va a hacer hoy: "${request.activityPlan.ifBlank { "Día habitual o casual" }}"
                - Cómo se quiere ver hoy: "${request.desiredVibe.ifBlank { "Con estilo y cómodo" }}"
                - ¿Tener en cuenta inspiraciones?: ${if (request.useInspirations) "SÍ, alinea la selección con su lookbook" else "NO"}

                Por favor, analiza las prendas y elige el mejor conjunto que cumpla con los requisitos. Devuelve solo el JSON.
            """.trimIndent()

            val response = model.generateContent(prompt)
            val responseText = response.text
                ?: return@withContext Result.failure(Exception("Gemini no devolvió ninguna respuesta."))

            val cleanJson = cleanJsonResponse(responseText)
            val json = JSONObject(cleanJson)

            val title = json.optString("title", "Outfit Recomendado por Gemini")
            val styleTag = json.optString("styleTag", "Personalizado")
            val matchPercentage = json.optInt("matchPercentage", 92).coerceIn(60, 99)
            val matchReason = json.optString(
                "matchReason",
                "Conjunto seleccionado especialmente para tu actividad de hoy."
            )
            val styleAdvice = json.optString("styleAdvice", "").ifBlank { null }

            val selectedIdsJson = json.optJSONArray("selectedItemIds") ?: JSONArray()
            val selectedIds = mutableListOf<Long>()
            for (i in 0 until selectedIdsJson.length()) {
                selectedIds.add(selectedIdsJson.getLong(i))
            }

            if (selectedIds.isEmpty()) {
                return@withContext Result.failure(Exception("Gemini no pudo encontrar una combinación adecuada con las prendas actuales."))
            }

            Result.success(
                GeminiRecommendationResult(
                    title = title,
                    selectedItemIds = selectedIds,
                    styleTag = styleTag,
                    matchPercentage = matchPercentage,
                    matchReason = matchReason,
                    styleAdvice = styleAdvice
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
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
        return trimmed
    }
}
