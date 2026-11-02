package com.example.myoutfit.domain.wishlist

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class ExtractedProductMetadata(
    val originalUrl: String,
    val finalUrl: String,
    val title: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val price: String = "",
    val storeName: String = "",
    val rawSnippet: String = ""
)

class ProductMetadataExtractor(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {

    suspend fun extractMetadata(urlInput: String): ExtractedProductMetadata = withContext(Dispatchers.IO) {
        val sanitizedUrl = sanitizeUrl(urlInput)
        val detectedStore = detectStoreFromUrl(sanitizedUrl)

        if (sanitizedUrl.isBlank()) {
            return@withContext ExtractedProductMetadata(
                originalUrl = urlInput,
                finalUrl = urlInput,
                storeName = detectedStore
            )
        }

        try {
            val request = Request.Builder()
                .url(sanitizedUrl)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                )
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
                .header("Upgrade-Insecure-Requests", "1")
                .build()

            client.newCall(request).execute().use { response ->
                val finalUrl = response.request.url.toString()
                val html = response.body.string()
                val finalStore = detectStoreFromUrl(finalUrl).ifBlank { detectedStore }

                val title = extractTitle(html).ifBlank { extractOgTag(html, "og:title") }
                    .ifBlank { extractTwitterTag(html, "twitter:title") }
                val image = extractOgTag(html, "og:image").ifBlank { extractTwitterTag(html, "twitter:image") }
                    .ifBlank { extractJsonLdImage(html) }
                val resolvedImage = resolveRelativeUrl(finalUrl, image)
                val description = extractOgTag(html, "og:description")
                    .ifBlank { extractTwitterTag(html, "twitter:description") }
                    .ifBlank { extractMetaTag(html, "description") }
                val price = extractPrice(html)

                ExtractedProductMetadata(
                    originalUrl = urlInput,
                    finalUrl = finalUrl,
                    title = cleanText(title),
                    description = cleanText(description),
                    imageUrl = resolvedImage,
                    price = price,
                    storeName = finalStore,
                    rawSnippet = html.take(1500)
                )
            }
        } catch (e: Exception) {
            // In case of network / bot check error, return what we can infer from the URL
            ExtractedProductMetadata(
                originalUrl = urlInput,
                finalUrl = sanitizedUrl,
                title = inferTitleFromUrl(sanitizedUrl),
                storeName = detectedStore
            )
        }
    }

    private fun sanitizeUrl(input: String): String {
        val trimmed = input.trim()
        val urlRegex = Regex("""https?://[^\s<>"{}|\\^`]+""")
        val match = urlRegex.find(trimmed)
        val extracted = match?.value ?: trimmed
        return if (!extracted.startsWith("http://") && !extracted.startsWith("https://")) {
            "https://$extracted"
        } else {
            extracted
        }
    }

    fun detectStoreFromUrl(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.contains("shein.") -> "SHEIN"
            lower.contains("amazon.") || lower.contains("amzn.to") || lower.contains("a.co/") -> "Amazon"
            lower.contains("zara.com") -> "Zara"
            lower.contains("pullandbear.com") -> "Pull&Bear"
            lower.contains("bershka.com") -> "Bershka"
            lower.contains("stradivarius.com") -> "Stradivarius"
            lower.contains("aliexpress.") -> "AliExpress"
            lower.contains("asos.") -> "ASOS"
            lower.contains("hm.com") -> "H&M"
            lower.contains("mango.com") -> "Mango"
            lower.contains("nike.com") -> "Nike"
            lower.contains("adidas.") -> "Adidas"
            lower.contains("zalando.") -> "Zalando"
            lower.contains("uniqlo.") -> "Uniqlo"
            lower.contains("temu.") -> "Temu"
            else -> {
                try {
                    val host = URI(url).host?.removePrefix("www.")?.removePrefix("m.") ?: ""
                    host.substringBefore(".").replaceFirstChar { it.uppercase() }
                } catch (_: Exception) {
                    "Tienda Online"
                }
            }
        }
    }

    private fun extractOgTag(html: String, property: String): String {
        val pattern1 = Pattern.compile(
            """<meta\s+[^>]*property=["']$property["'][^>]*content=["']([^"']+)["']""",
            Pattern.CASE_INSENSITIVE
        )
        val matcher1 = pattern1.matcher(html)
        if (matcher1.find()) return matcher1.group(1).orEmpty()

        val pattern2 = Pattern.compile(
            """<meta\s+[^>]*content=["']([^"']+)["'][^>]*property=["']$property["']""",
            Pattern.CASE_INSENSITIVE
        )
        val matcher2 = pattern2.matcher(html)
        if (matcher2.find()) return matcher2.group(1).orEmpty()

        return ""
    }

    private fun extractTwitterTag(html: String, name: String): String {
        val pattern1 = Pattern.compile(
            """<meta\s+[^>]*name=["']$name["'][^>]*content=["']([^"']+)["']""",
            Pattern.CASE_INSENSITIVE
        )
        val matcher1 = pattern1.matcher(html)
        if (matcher1.find()) return matcher1.group(1).orEmpty()

        val pattern2 = Pattern.compile(
            """<meta\s+[^>]*content=["']([^"']+)["'][^>]*name=["']$name["']""",
            Pattern.CASE_INSENSITIVE
        )
        val matcher2 = pattern2.matcher(html)
        if (matcher2.find()) return matcher2.group(1).orEmpty()

        return ""
    }

    private fun extractMetaTag(html: String, name: String): String {
        val pattern = Pattern.compile(
            """<meta\s+[^>]*name=["']$name["'][^>]*content=["']([^"']+)["']""",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = pattern.matcher(html)
        return if (matcher.find()) matcher.group(1).orEmpty() else ""
    }

    private fun extractTitle(html: String): String {
        val pattern = Pattern.compile("""<title[^>]*>([^<]+)</title>""", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(html)
        return if (matcher.find()) matcher.group(1).orEmpty() else ""
    }

    private fun extractJsonLdImage(html: String): String {
        try {
            val scriptPattern = Pattern.compile(
                """<script\s+[^>]*type=["']application/ld\+json["'][^>]*>(.*?)</script>""",
                Pattern.CASE_INSENSITIVE or Pattern.DOTALL
            )
            val matcher = scriptPattern.matcher(html)
            while (matcher.find()) {
                val jsonText = matcher.group(1)?.trim() ?: continue
                if (jsonText.startsWith("{")) {
                    val obj = JSONObject(jsonText)
                    val img = obj.optString("image")
                    if (img.isNotBlank()) return img
                }
            }
        } catch (_: Exception) {}
        return ""
    }

    private fun extractPrice(html: String): String {
        val ogPrice = extractOgTag(html, "product:price:amount").ifBlank { extractOgTag(html, "og:price:amount") }
        val currency = extractOgTag(html, "product:price:currency").ifBlank { "€" }
        if (ogPrice.isNotBlank()) {
            return "$ogPrice $currency".trim()
        }

        // Try regex pattern for price in HTML
        val priceRegex = Regex("""(\d+[.,]\d{2})\s*(?:€|\$|EUR)""")
        val match = priceRegex.find(html)
        return match?.value ?: ""
    }

    private fun resolveRelativeUrl(baseUrl: String, relativeUrl: String): String {
        if (relativeUrl.isBlank()) return ""
        return try {
            if (relativeUrl.startsWith("//")) {
                "https:$relativeUrl"
            } else if (relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://")) {
                relativeUrl
            } else {
                val base = URI(baseUrl)
                base.resolve(relativeUrl).toString()
            }
        } catch (_: Exception) {
            relativeUrl
        }
    }

    private fun inferTitleFromUrl(url: String): String {
        return try {
            val uri = Uri.parse(url)
            val lastSegment = uri.lastPathSegment ?: return ""
            lastSegment.substringBeforeLast(".")
                .replace("-", " ")
                .replace("_", " ")
                .replaceFirstChar { it.uppercase() }
        } catch (_: Exception) {
            ""
        }
    }

    private fun cleanText(text: String): String {
        return text.replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}
