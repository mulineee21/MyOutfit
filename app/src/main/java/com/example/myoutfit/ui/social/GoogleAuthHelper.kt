package com.example.myoutfit.ui.social

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.myoutfit.BuildConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

object GoogleAuthHelper {

    private const val TAG = "GoogleAuthHelper"

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    /**
     * Solicita el inicio de sesión oficial con Google mediante Android Credential Manager
     * y devuelve el `idToken` firmado criptográficamente para verificarlo en el backend.
     */
    suspend fun requestGoogleIdToken(context: Context): Result<String?> {
        val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID.trim()
        if (webClientId.isBlank()) {
            return Result.failure(
                IllegalStateException(
                    "Falta configurar GOOGLE_WEB_CLIENT_ID para el inicio de sesión con Google."
                )
            )
        }

        val activityContext = context.findActivity() ?: context
        val credentialManager = CredentialManager.create(activityContext)

        return try {
            val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(
                serverClientId = webClientId
            ).build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInWithGoogleOption)
                .build()

            val result = credentialManager.getCredential(
                context = activityContext,
                request = request
            )
            extractGoogleIdToken(result.credential)
        } catch (e: GetCredentialCancellationException) {
            val rawMsg = e.message.orEmpty()
            Log.w(TAG, "GetCredentialCancellationException: $rawMsg", e)

            if (isDisguisedConfigurationError(rawMsg)) {
                // Intentamos fallback con GetGoogleIdOption por si el módulo de Play Services falló en el botón directo
                tryFallbackGoogleIdOption(credentialManager, activityContext, webClientId, rawMsg)
            } else {
                // El usuario cerró el selector voluntariamente
                Result.success(null)
            }
        } catch (e: NoCredentialException) {
            Log.w(TAG, "NoCredentialException: ${e.message}", e)
            Result.failure(
                IllegalStateException("No se encontró ninguna cuenta de Google activa en este dispositivo.")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error en Google Sign-In (${e::class.java.simpleName}): ${e.message}", e)
            Result.failure(
                IllegalStateException(
                    mapFriendlyGoogleError(e.message),
                    e
                )
            )
        }
    }

    private suspend fun tryFallbackGoogleIdOption(
        credentialManager: CredentialManager,
        activityContext: Context,
        webClientId: String,
        originalError: String
    ): Result<String?> {
        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setServerClientId(webClientId)
                .setFilterByAuthorizedAccounts(false)
                .setAutoSelectEnabled(false)
                .build()

            val fallbackRequest = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                context = activityContext,
                request = fallbackRequest
            )
            extractGoogleIdToken(result.credential)
        } catch (fallbackEx: GetCredentialCancellationException) {
            val msg = fallbackEx.message.orEmpty()
            Log.e(TAG, "Fallback GetGoogleIdOption cancelado/fallido: $msg", fallbackEx)
            if (isDisguisedConfigurationError(msg) || isDisguisedConfigurationError(originalError)) {
                Result.failure(IllegalStateException(mapFriendlyGoogleError(msg.ifBlank { originalError })))
            } else {
                Result.success(null)
            }
        } catch (fallbackEx: Exception) {
            Log.e(TAG, "Fallback GetGoogleIdOption error: ${fallbackEx.message}", fallbackEx)
            Result.failure(
                IllegalStateException(
                    mapFriendlyGoogleError(fallbackEx.message ?: originalError),
                    fallbackEx
                )
            )
        }
    }

    private fun extractGoogleIdToken(credential: androidx.credentials.Credential): Result<String?> {
        return if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            Result.success(googleIdTokenCredential.idToken)
        } else {
            Result.failure(IllegalStateException("Credencial de Google no compatible."))
        }
    }

    private fun isDisguisedConfigurationError(message: String): Boolean {
        val lower = message.lowercase()
        return lower.contains("[16]") ||
            lower.contains("[28444]") ||
            lower.contains("reauth failed") ||
            lower.contains("developer console") ||
            lower.contains("unregistered") ||
            lower.contains("cannot find a matching credential")
    }

    private fun mapFriendlyGoogleError(rawMessage: String?): String {
        val msg = rawMessage.orEmpty()
        val lower = msg.lowercase()
        return when {
            lower.contains("28444") || lower.contains("unregistered") || lower.contains("reauth failed") || lower.contains("[16]") ->
                "Google rechazó la verificación OAuth de la app (revisa que tu correo esté en Usuarios de prueba / Audiencia en Google Cloud o espera unos minutos a que propague)."
            msg.isNotBlank() ->
                "No se pudo iniciar sesión con Google: $msg"
            else ->
                "No se pudo completar el inicio de sesión con Google."
        }
    }
}
