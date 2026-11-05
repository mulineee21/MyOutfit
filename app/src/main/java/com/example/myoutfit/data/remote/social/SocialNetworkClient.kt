package com.example.myoutfit.data.remote.social

import android.content.Context
import coil.ImageLoader
import com.example.myoutfit.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * Cliente de red de MyOutfit Social que:
 * 1. Conecta de forma transparente al dominio oficial HTTPS (`https://myoutfit.duckdns.org`).
 * 2. Resuelve automáticamente Hairpin NAT en la Wi-Fi local (192.168.1.159) manteniendo el
 *    certificado TLS oficial de Let's Encrypt cuando el router doméstico bloquea el loopback.
 * 3. Inyecta el JWT Bearer tanto en peticiones REST como en descargas de imágenes protegidas de Coil.
 * 4. Rota automáticamente el Refresh Token cuando el Access Token de 15 min caduca (HTTP 401).
 */
class SocialNetworkClient(
    context: Context,
    private val sessionManager: SocialSessionManager
) {
    private val placeholderHost = "myoutfit.placeholder.local"

    val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val refreshLock = Any()

    @Volatile
    private var cachedPreferLanUntilMs: Long = 0L

    @Volatile
    private var cachedPreferLanValue: Boolean = false

    private val smartSocialDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val targetHost = BuildConfig.SOCIAL_API_BASE_URL.toHttpUrlOrNull()?.host
            val lanIpRaw = BuildConfig.SOCIAL_LAN_FALLBACK_IP.trim()

            if (targetHost == null ||
                !hostname.equals(targetHost, ignoreCase = true) ||
                lanIpRaw.isEmpty()
            ) {
                return Dns.SYSTEM.lookup(hostname)
            }

            val systemAddresses = runCatching { Dns.SYSTEM.lookup(hostname) }.getOrElse { emptyList() }
            val lanBytes = parseIpv4Bytes(lanIpRaw) ?: return systemAddresses.ifEmpty {
                Dns.SYSTEM.lookup(hostname)
            }
            val lanAddress = InetAddress.getByAddress(hostname, lanBytes)

            if (systemAddresses.isEmpty()) {
                return listOf(lanAddress)
            }

            val now = System.currentTimeMillis()
            val preferLan = if (now < cachedPreferLanUntilMs) {
                cachedPreferLanValue
            } else {
                val publicReachable = isTcpReachable(systemAddresses.first(), 443, 1_200)
                val useLan = !publicReachable && isTcpReachable(lanAddress, 443, 800)
                cachedPreferLanValue = useLan
                cachedPreferLanUntilMs = now + 60_000L
                useLan
            }

            return if (preferLan) {
                listOf(lanAddress) + systemAddresses
            } else {
                systemAddresses + lanAddress
            }
        }
    }

    private val dynamicHostAndAuthInterceptor = Interceptor { chain ->
        val snapshot = sessionManager.currentSnapshot
        var request = chain.request()

        if (request.url.host == placeholderHost) {
            val targetBase = snapshot.serverBaseUrl.toHttpUrlOrNull()
            if (targetBase != null) {
                val basePath = targetBase.encodedPath.trimEnd('/')
                val reqPath = request.url.encodedPath
                val newUrl = request.url.newBuilder()
                    .scheme(targetBase.scheme)
                    .host(targetBase.host)
                    .port(targetBase.port)
                    .encodedPath(if (basePath.isEmpty()) reqPath else "$basePath$reqPath")
                    .build()
                request = request.newBuilder().url(newUrl).build()
            }
        }

        val serverHttpUrl = snapshot.serverBaseUrl.toHttpUrlOrNull()
        val isSameServer = serverHttpUrl != null &&
            request.url.host.equals(serverHttpUrl.host, ignoreCase = true) &&
            request.url.port == serverHttpUrl.port

        val isPublicAuthRoute = request.url.encodedPath.endsWith("/api/auth/login") ||
            request.url.encodedPath.endsWith("/api/auth/register") ||
            request.url.encodedPath.endsWith("/api/auth/google") ||
            request.url.encodedPath.endsWith("/api/auth/forgot-password") ||
            request.url.encodedPath.endsWith("/api/auth/reset-password") ||
            request.url.encodedPath.endsWith("/api/auth/refresh")

        if (isSameServer && !isPublicAuthRoute && snapshot.accessToken.isNotBlank()) {
            request = request.newBuilder()
                .header("Authorization", "Bearer ${snapshot.accessToken}")
                .build()
        }

        chain.proceed(request)
    }

    private val tokenAuthenticator = object : Authenticator {
        override fun authenticate(route: Route?, response: Response): Request? {
            if (responseCount(response) >= 2) return null

            val path = response.request.url.encodedPath
            if (path.endsWith("/api/auth/login") ||
                path.endsWith("/api/auth/register") ||
                path.endsWith("/api/auth/google") ||
                path.endsWith("/api/auth/forgot-password") ||
                path.endsWith("/api/auth/reset-password") ||
                path.endsWith("/api/auth/refresh")
            ) {
                return null
            }

            synchronized(refreshLock) {
                val currentSession = sessionManager.currentSnapshot
                if (currentSession.refreshToken.isBlank()) return null

                val requestAuthHeader = response.request.header("Authorization")
                val currentBearer = "Bearer ${currentSession.accessToken}"

                if (requestAuthHeader != null &&
                    requestAuthHeader != currentBearer &&
                    currentSession.accessToken.isNotBlank()
                ) {
                    return response.request.newBuilder()
                        .header("Authorization", currentBearer)
                        .build()
                }

                val refreshed = performSynchronousRefresh(
                    serverBaseUrl = currentSession.serverBaseUrl,
                    refreshToken = currentSession.refreshToken
                )

                return if (refreshed != null) {
                    runBlocking {
                        sessionManager.updateTokens(refreshed.accessToken, refreshed.refreshToken)
                    }
                    response.request.newBuilder()
                        .header("Authorization", "Bearer ${refreshed.accessToken}")
                        .build()
                } else {
                    runBlocking { sessionManager.clearAuthSession() }
                    null
                }
            }
        }
    }

    private val bareRefreshHttpClient = OkHttpClient.Builder()
        .dns(smartSocialDns)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .dns(smartSocialDns)
        .addInterceptor(dynamicHostAndAuthInterceptor)
        .authenticator(tokenAuthenticator)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    val apiService: SocialApiService = Retrofit.Builder()
        .baseUrl("https://$placeholderHost/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(SocialApiService::class.java)

    val authenticatedImageLoader: ImageLoader = ImageLoader.Builder(context.applicationContext)
        .okHttpClient(okHttpClient)
        .crossfade(true)
        .build()

    private fun performSynchronousRefresh(
        serverBaseUrl: String,
        refreshToken: String
    ): RefreshResponseDto? {
        return try {
            val reqAdapter = moshi.adapter(RefreshRequestDto::class.java)
            val resAdapter = moshi.adapter(RefreshResponseDto::class.java)
            val jsonPayload = reqAdapter.toJson(RefreshRequestDto(refreshToken = refreshToken))
            val req = Request.Builder()
                .url("${serverBaseUrl.trimEnd('/')}/api/auth/refresh")
                .post(jsonPayload.toRequestBody("application/json".toMediaType()))
                .build()

            bareRefreshHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val bodyStr = resp.body?.string() ?: return null
                resAdapter.fromJson(bodyStr)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun isTcpReachable(address: InetAddress, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(address, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun parseIpv4Bytes(ipv4: String): ByteArray? {
        val parts = ipv4.split('.')
        if (parts.size != 4) return null
        val bytes = ByteArray(4)
        for (i in 0..3) {
            val n = parts[i].toIntOrNull() ?: return null
            if (n !in 0..255) return null
            bytes[i] = n.toByte()
        }
        return bytes
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
