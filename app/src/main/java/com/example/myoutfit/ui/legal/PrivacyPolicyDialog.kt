package com.example.myoutfit.ui.legal

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myoutfit.BuildConfig

private data class LegalClause(val title: String, val body: String)

private val RGPD_CLAUSES = listOf(
    LegalClause(
        title = "1. Responsable del Tratamiento (Art. 13 RGPD)",
        body = "Responsable: Joel Molina — Proyecto MyOutfit.\nContacto de privacidad y ejercicio de derechos: jomenesgussy@gmail.com.\nInfraestructura de almacenamiento alojada en España (Unión Europea) con cifrado TLS en tránsito."
    ),
    LegalClause(
        title = "2. Minimización de Datos y Limpieza EXIF/GPS (Art. 5.1.c y Art. 25 RGPD)",
        body = "Solo tratamos los datos imprescindibles para el servicio: @usuario (modificable 1 vez al mes), correo electrónico, credenciales cifradas, foto de perfil opcional y outfits.\nTodas las fotos subidas pasan por un filtro automático en el servidor que elimina coordenadas GPS y metadatos EXIF de cámara antes de guardarlas en formato WebP. Las direcciones IP en registros técnicos se anonimizan truncando el último octeto."
    ),
    LegalClause(
        title = "3. Base Jurídica y Privacidad por Defecto (Art. 6.1 y Art. 25.2 RGPD)",
        body = "• Ejecución del servicio solicitado (Art. 6.1.b): cuenta social 24h y calendario privado.\n• Privacidad por defecto (Art. 25.2): las cuentas nuevas se configuran como Privadas por defecto (solo seguidores aprobados ven tus outfits).\n• Consentimiento explícito (Art. 6.1.a): análisis opcional de prendas con IA (Google Gemini), desactivable en cualquier momento en Mi Perfil."
    ),
    LegalClause(
        title = "4. Edad Mínima Legal en España: 14 años (Art. 7 LOPDGDD)",
        body = "De acuerdo con el artículo 7.1 de la Ley Orgánica 3/2018 (LOPDGDD), el registro en la comunidad MyOutfit está permitido únicamente a mayores de 14 años (o con autorización verificable de sus tutores legales)."
    ),
    LegalClause(
        title = "5. Plazos de Conservación y Purga Automática (Art. 5.1.e RGPD)",
        body = "• Publicaciones de Outfits 24h (reacciones y comentarios): se purgan automáticamente de la tabla social pasadas exactamente 24 horas.\n• Calendario Privado: accesible solo por ti mientras mantengas activa tu cuenta.\n• Códigos de recuperación y tokens caducados: eliminados automáticamente."
    ),
    LegalClause(
        title = "6. Sin Publicidad ni Venta de Datos · Encargados Técnicos",
        body = "MyOutfit no vende datos ni incorpora SDKs publicitarios o de rastreo. Solo intervienen Google Ireland Ltd. cuando eliges 'Continuar con Google' o usas el etiquetado IA opcional con Gemini, y Gmail SMTP exclusivamente al enviar códigos de recuperación de contraseña."
    ),
    LegalClause(
        title = "7. Tus Derechos RGPD y LOPDGDD (Acceso, Portabilidad y Olvido)",
        body = "Desde 'Mi Perfil → Privacidad y Derechos RGPD' puedes ejercer en cualquier momento:\n• Portabilidad y Acceso (Art. 15 y 20 RGPD): descargar un archivo JSON completo con todos tus datos del servidor.\n• Derecho de Supresión / Olvido (Art. 17 RGPD): eliminar definitivamente tu cuenta y borrar físicamente todas tus fotografías WebP del servidor.\nTambién puedes reclamar ante la Agencia Española de Protección de Datos (www.aepd.es)."
    )
)

@Composable
fun PrivacyPolicyDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val webPolicyUrl = BuildConfig.SOCIAL_API_BASE_URL
        .ifBlank { "https://myoutfit.duckdns.org" }
        .trimEnd('/') + "/privacy"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.GppGood,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Privacidad RGPD / LOPDGDD",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Versión 2026.1-ES · Reglamento (UE) 2016/679 (RGPD) y Ley Orgánica 3/2018 (LOPDGDD España)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                HorizontalDivider()
                RGPD_CLAUSES.forEach { clause ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = clause.title,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = clause.body,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Entendido")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webPolicyUrl)))
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Ver versión web")
            }
        }
    )
}
