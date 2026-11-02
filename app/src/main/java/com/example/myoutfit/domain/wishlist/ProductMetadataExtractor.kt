package com.example.myoutfit.domain.wishlist

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
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
        val unwrappedUrl = unwrapShareUrl(sanitizedUrl)
        val detectedStore = detectStoreFromUrl(unwrappedUrl)

        if (unwrappedUrl.isBlank()) {
            return@withContext ExtractedProductMetadata(
                originalUrl = urlInput,
                finalUrl = urlInput,
                storeName = detectedStore
            )
        }

        // Fast path for Amazon: extract ASIN and CDN image directly to avoid Amazon bot-protection
        val amazonAsin = if (detectedStore.equals("Amazon", ignoreCase = true)) {
            extractAmazonAsin(unwrappedUrl)
        } else {
            ""
        }
        val amazonCdnImage = if (amazonAsin.isNotBlank()) {
            "https://images-na.ssl-images-amazon.com/images/P/$amazonAsin.01._SCLZZZZZZZ_.jpg"
        } else {
            ""
        }

        try {
            val request = Request.Builder()
                .url(unwrappedUrl)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
                )
                .header(
                    "Accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8"
                )
                .header("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
                .header("Cache-Control", "max-age=0")
                .header("Sec-Ch-Ua", "\"Not/A)Brand\";v=\"8\", \"Chromium\";v=\"126\", \"Google Chrome\";v=\"126\"")
                .header("Sec-Ch-Ua-Mobile", "?0")
                .header("Sec-Ch-Ua-Platform", "\"Windows\"")
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", "none")
                .header("Sec-Fetch-User", "?1")
                .header("Upgrade-Insecure-Requests", "1")
                .build()

            client.newCall(request).execute().use { response ->
                val finalUrl = response.request.url.toString()
                val html = response.body.string()
                val finalStore = detectStoreFromUrl(finalUrl).ifBlank { detectedStore }

                var title = extractTitle(html)
                if (isJunkTitle(title)) {
                    title = extractOgTag(html, "og:title")
                }
                if (isJunkTitle(title)) {
                    title = extractTwitterTag(html, "twitter:title")
                }
                if (isJunkTitle(title)) {
                    title = inferTitleFromUrl(finalUrl)
                }
                if (isJunkTitle(title)) {
                    title = inferTitleFromUrl(unwrappedUrl)
                }
                if (isJunkTitle(title)) {
                    title = "Prenda de $finalStore"
                }

                // Image extraction hierarchy
                var image = extractOgTag(html, "og:image")
                    .ifBlank { extractOgTag(html, "og:image:secure_url") }
                    .ifBlank { extractOgTag(html, "og:image:url") }
                    .ifBlank { extractTwitterTag(html, "twitter:image") }
                    .ifBlank { extractTwitterTag(html, "twitter:image:src") }
                    .ifBlank { extractMetaTag(html, "itemprop", "image") }
                    .ifBlank { extractLinkTag(html, "image_src") }
                    .ifBlank { extractJsonLdImage(html) }
                    .ifBlank { extractStoreSpecificImage(html, finalUrl, finalStore) }

                // Discard site logos or generic icons
                if (isLogoOrJunkImage(image)) {
                    image = extractStoreSpecificImage(html, finalUrl, finalStore)
                }

                // If Amazon bot blocked HTML or image is still blank, use the ASIN CDN image
                if (image.isBlank() && amazonCdnImage.isNotBlank()) {
                    image = amazonCdnImage
                } else if (image.isBlank() && finalStore.equals("Amazon", ignoreCase = true)) {
                    val asinFromFinal = extractAmazonAsin(finalUrl)
                    if (asinFromFinal.isNotBlank()) {
                        image = "https://images-na.ssl-images-amazon.com/images/P/$asinFromFinal.01._SCLZZZZZZZ_.jpg"
                    }
                }

                val resolvedImage = resolveRelativeUrl(finalUrl, image)

                val description = extractOgTag(html, "og:description")
                    .ifBlank { extractTwitterTag(html, "twitter:description") }
                    .ifBlank { extractMetaTag(html, "name", "description") }

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
            // In case of bot detection or network issues, construct metadata from URL
            val fallbackImage = if (amazonCdnImage.isNotBlank()) {
                amazonCdnImage
            } else {
                inferStoreImageFromUrl(unwrappedUrl, detectedStore)
            }

            ExtractedProductMetadata(
                originalUrl = urlInput,
                finalUrl = unwrappedUrl,
                title = inferTitleFromUrl(unwrappedUrl).ifBlank { "Prenda de $detectedStore" },
                imageUrl = fallbackImage,
                storeName = detectedStore
            )
        }
    }

    /**
     * Extracts and unwraps URLs shared from mobile apps (such as SHEIN app jump links or query parameters).
     */
    private fun unwrapShareUrl(url: String): String {
        return try {
            if (url.contains("shein", ignoreCase = true) && url.contains("link=")) {
                val linkRegex = Regex("""[?&]link=([^&]+)""")
                val match = linkRegex.find(url)
                if (match != null) {
                    val rawParam = match.groupValues[1]
                    val decoded = URLDecoder.decode(rawParam, "UTF-8")
                    if (decoded.startsWith("http://") || decoded.startsWith("https://")) {
                        return decoded
                    }
                }
            }
            url
        } catch (_: Exception) {
            url
        }
    }

    private fun sanitizeUrl(input: String): String {
        val trimmed = input.trim()
        val urlRegex = Regex("""https?://[^\s<>"{}|\\^`]+""")
        val match = urlRegex.find(trimmed)
        val extracted = match?.value ?: trimmed
        val clean = extracted.trimEnd('.', ',', ')', ']', ';', '!', '?', '\'', '"')
        return if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            "https://$clean"
        } else {
            clean
        }
    }

    fun detectStoreFromUrl(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.contains("shein.") || lower.contains("shein.top") -> "SHEIN"
            lower.contains("amazon.") || lower.contains("amzn.to") || lower.contains("a.co/") || lower.contains("amzn.eu") -> "Amazon"
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

    /**
     * Extracts the 10-character Amazon ASIN from any standard Amazon URL format.
     */
    fun extractAmazonAsin(url: String): String {
        val patterns = listOf(
            Regex("""/(?:dp|gp/product|gp/aw/d|d)/([A-Za-z0-9]{10})"""),
            Regex("""[?&]asin=([A-Za-z0-9]{10})""", RegexOption.IGNORE_CASE),
            Regex("""/([A-Z0-9]{10})(?:[/?#]|$)""")
        )
        for (pattern in patterns) {
            val match = pattern.find(url)
            if (match != null) {
                val candidate = match.groupValues[1]
                if (candidate.length == 10 && candidate.any { it.isDigit() }) {
                    return candidate
                }
            }
        }
        return ""
    }

    private fun extractStoreSpecificImage(html: String, url: String, store: String): String {
        // SHEIN: check for ltwebstatic.com images
        if (store.equals("SHEIN", ignoreCase = true) || html.contains("ltwebstatic.com")) {
            val sheinRegexes = listOf(
                Regex("""["'](?:goods_img|original_img|mainImage|goods_thumb)["']\s*:\s*["']([^"']+)["']"""),
                Regex("""https?://img\.ltwebstatic\.com/images[^"'\s<>]+\.(?:jpg|jpeg|png|webp)"""),
                Regex("""//img\.ltwebstatic\.com/images[^"'\s<>]+\.(?:jpg|jpeg|png|webp)""")
            )
            for (regex in sheinRegexes) {
                val match = regex.find(html)
                if (match != null) {
                    val raw = if (match.groupValues.size > 1 && match.groupValues[1].isNotBlank()) {
                        match.groupValues[1]
                    } else {
                        match.value
                    }
                    if (!isLogoOrJunkImage(raw)) {
                        return if (raw.startsWith("//")) "https:$raw" else raw
                    }
                }
            }
        }

        // Amazon: check for data-old-hires, landingImageUrl, or media-amazon URLs
        if (store.equals("Amazon", ignoreCase = true) || html.contains("media-amazon.com")) {
            val amazonRegexes = listOf(
                Regex("""data-old-hires=["']([^"']+)["']"""),
                Regex("""["']landingImageUrl["']\s*:\s*["']([^"']+)["']"""),
                Regex("""https?://m\.media-amazon\.com/images/I/[A-Za-z0-9+_-]+\.(?:jpg|jpeg|png)"""),
                Regex("""https?://images-na\.ssl-images-amazon\.com/images/I/[A-Za-z0-9+_-]+\.(?:jpg|jpeg|png)""")
            )
            for (regex in amazonRegexes) {
                val match = regex.find(html)
                if (match != null) {
                    val raw = if (match.groupValues.size > 1 && match.groupValues[1].isNotBlank()) {
                        match.groupValues[1]
                    } else {
                        match.value
                    }
                    if (!isLogoOrJunkImage(raw)) return raw
                }
            }
        }

        // Zara, Bershka, Pull&Bear, Stradivarius (Inditex CDN)
        val inditexMatch = Regex("""https?://static\.(?:zara|pullandbear|bershka|stradivarius)\.net/[^"'\s<>]+\.(?:jpg|jpeg|png|webp)""").find(html)
        if (inditexMatch != null && !isLogoOrJunkImage(inditexMatch.value)) {
            return inditexMatch.value
        }

        // ASOS CDN
        val asosMatch = Regex("""https?://images\.asos-media\.com/products/[^"'\s<>]+\.(?:jpg|jpeg|png|webp)""").find(html)
        if (asosMatch != null && !isLogoOrJunkImage(asosMatch.value)) {
            return asosMatch.value
        }

        // AliExpress CDN
        val aliMatch = Regex("""https?://ae01\.alicdn\.com/kf/[^"'\s<>]+\.(?:jpg|jpeg|png|webp)""").find(html)
        if (aliMatch != null && !isLogoOrJunkImage(aliMatch.value)) {
            return aliMatch.value
        }

        // Generic fallback: first prominent product image tag
        val imgTagRegex = Regex("""<img\s+[^>]*src=["']([^"']+\.(?:jpg|jpeg|png|webp))["'][^>]*>""", RegexOption.IGNORE_CASE)
        val matches = imgTagRegex.findAll(html)
        for (m in matches) {
            val tagText = m.value.lowercase()
            val src = m.groupValues[1]
            if ((tagText.contains("product") || tagText.contains("main") || tagText.contains("detail") || tagText.contains("hero") || tagText.contains("primary"))
                && !isLogoOrJunkImage(src)
            ) {
                return src
            }
        }

        return ""
    }

    private fun inferStoreImageFromUrl(url: String, store: String): String {
        if (store.equals("Amazon", ignoreCase = true)) {
            val asin = extractAmazonAsin(url)
            if (asin.isNotBlank()) {
                return "https://images-na.ssl-images-amazon.com/images/P/$asin.01._SCLZZZZZZZ_.jpg"
            }
        }
        return ""
    }

    private fun isLogoOrJunkImage(imageUrl: String): Boolean {
        if (imageUrl.isBlank()) return true
        val lower = imageUrl.lowercase()
        return lower.contains("logo") ||
            lower.contains("favicon") ||
            lower.contains("pixel") ||
            lower.contains("icon") ||
            lower.contains("avatar") ||
            lower.contains("badge") ||
            lower.contains("192.png") ||
            lower.endsWith(".svg") ||
            lower.endsWith(".gif")
    }

    private fun isJunkTitle(title: String): Boolean {
        if (title.isBlank()) return true
        val lower = title.lowercase()
        return lower.contains("documento no encontrado") ||
            lower.contains("robot check") ||
            lower.contains("captcha") ||
            lower.contains("page not found") ||
            lower.contains("404") ||
            lower.contains("access denied") ||
            lower.contains("attention required") ||
            lower.contains("security check") ||
            lower.contains("interstitial") ||
            lower == "amazon.es" ||
            lower == "amazon.com" ||
            lower == "shein" ||
            lower == "zara" ||
            lower == "&nbsp;"
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

        // Some websites mistakenly use name="og:image"
        val pattern3 = Pattern.compile(
            """<meta\s+[^>]*name=["']$property["'][^>]*content=["']([^"']+)["']""",
            Pattern.CASE_INSENSITIVE
        )
        val matcher3 = pattern3.matcher(html)
        if (matcher3.find()) return matcher3.group(1).orEmpty()

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

    private fun extractMetaTag(html: String, attribute: String, value: String): String {
        val pattern = Pattern.compile(
            """<meta\s+[^>]*$attribute=["']$value["'][^>]*content=["']([^"']+)["']""",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = pattern.matcher(html)
        if (matcher.find()) return matcher.group(1).orEmpty()

        val pattern2 = Pattern.compile(
            """<meta\s+[^>]*content=["']([^"']+)["'][^>]*$attribute=["']$value["']""",
            Pattern.CASE_INSENSITIVE
        )
        val matcher2 = pattern2.matcher(html)
        return if (matcher2.find()) matcher2.group(1).orEmpty() else ""
    }

    private fun extractLinkTag(html: String, rel: String): String {
        val pattern = Pattern.compile(
            """<link\s+[^>]*rel=["']$rel["'][^>]*href=["']([^"']+)["']""",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = pattern.matcher(html)
        return if (matcher.find()) matcher.group(1).orEmpty() else ""
    }

    private fun extractTitle(html: String): String {
        val pattern = Pattern.compile("""<title[^>]*>([^<]+)</title>""", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(html)
        if (matcher.find()) {
            val raw = matcher.group(1).orEmpty().trim()
            if (raw.isNotBlank() && !raw.equals("&nbsp;", ignoreCase = true)) {
                return raw
            }
        }
        return ""
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
                    val img = findImageInJsonObject(obj)
                    if (img.isNotBlank() && !isLogoOrJunkImage(img)) return img
                } else if (jsonText.startsWith("[")) {
                    val array = JSONArray(jsonText)
                    for (i in 0 until array.length()) {
                        val item = array.optJSONObject(i) ?: continue
                        val img = findImageInJsonObject(item)
                        if (img.isNotBlank() && !isLogoOrJunkImage(img)) return img
                    }
                }
            }
        } catch (_: Exception) {}
        return ""
    }

    private fun findImageInJsonObject(obj: JSONObject): String {
        // Direct string image
        val directImg = obj.optString("image")
        if (directImg.isNotBlank() && !isLogoOrJunkImage(directImg)) return directImg

        // Array of images
        val arrayImg = obj.optJSONArray("image")
        if (arrayImg != null && arrayImg.length() > 0) {
            val first = arrayImg.optString(0)
            if (first.isNotBlank() && !isLogoOrJunkImage(first)) return first
        }

        // ImageObject
        val imgObj = obj.optJSONObject("image")
        if (imgObj != null) {
            val url = imgObj.optString("url").ifBlank { imgObj.optString("contentUrl") }
            if (url.isNotBlank() && !isLogoOrJunkImage(url)) return url
        }

        // Check @graph array
        val graph = obj.optJSONArray("@graph")
        if (graph != null) {
            for (i in 0 until graph.length()) {
                val item = graph.optJSONObject(i) ?: continue
                val res = findImageInJsonObject(item)
                if (res.isNotBlank()) return res
            }
        }

        return ""
    }

    private fun extractPrice(html: String): String {
        val ogPrice = extractOgTag(html, "product:price:amount").ifBlank { extractOgTag(html, "og:price:amount") }
        val currency = extractOgTag(html, "product:price:currency").ifBlank { "€" }
        if (ogPrice.isNotBlank()) {
            return "$ogPrice $currency".trim()
        }

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
            val path = if (url.contains("?")) url.substringBefore("?") else url
            val segments = path.split("/").filter {
                it.isNotBlank() && !it.startsWith("http", ignoreCase = true) && !it.contains(":") && !it.contains(".") && !it.equals("dp", ignoreCase = true)
            }
            val lastSegment = segments.lastOrNull() ?: return ""
            val cleaned = lastSegment.substringBeforeLast(".")
                .replace("-", " ")
                .replace("_", " ")
                .replace(Regex("""\b(p|c0p|dp)\d+\b""", RegexOption.IGNORE_CASE), "")
                .trim()
            if (cleaned.length > 3) {
                cleaned.replaceFirstChar { it.uppercase() }
            } else {
                ""
            }
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
