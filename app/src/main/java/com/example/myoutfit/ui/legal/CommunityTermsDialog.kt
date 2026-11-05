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
import androidx.compose.material.icons.filled.Gavel
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

private data class TermsClause(val title: String, val body: String)

private val DSA_COMMUNITY_CLAUSES = listOf(
    TermsClause(
        title = "1. Objeto y Ámbito Legal (Art. 14 DSA)",
        body = "Estas Condiciones de Uso y Normas Comunitarias regulan la participación en la red social efímera MyOutfit en cumplimiento del Reglamento (UE) 2022/2065 (Ley de Servicios Digitales DSA) y de la Ley 34/2002 (LSSI-CE)."
    ),
    TermsClause(
        title = "2. Política de Tolerancia Cero frente al Contenido Abusivo (UGC Policy)",
        body = "Queda terminantemente prohibido publicar o transmitir:\n• Desnudez, pornografía o contenido sexual explícito.\n• Acoso, intimidación, hostigamiento o amenazas personales.\n• Discurso de odio, racismo, xenofobia o discriminación.\n• Apología de la violencia o autolesiones.\n• Vulneración de la propia imagen de terceros sin su consentimiento o infracción de copyright.\n• Spam, fraudes o estafas."
    ),
    TermsClause(
        title = "3. Herramientas Integradas de Denuncia y Bloqueo",
        body = "• Denuncia de Contenidos (Art. 16 DSA): puedes reportar de forma inmediata cualquier publicación ilícita desde el menú (⋮). Las publicaciones denunciadas se ocultan preventivamente de tu feed.\n• Bloqueo de Usuarios: puedes bloquear a cualquier usuario para impedir que vea tus outfits o interactúe contigo."
    ),
    TermsClause(
        title = "4. Moderación de Contenidos y Sanciones (Art. 17 DSA)",
        body = "Las denuncias son revisadas por el equipo de MyOutfit en un plazo máximo de 24 horas. Nos reservamos la facultad de retirar de inmediato los contenidos infractores y suspender o cancelar permanentemente las cuentas de usuarios reincidentes o que cometan infracciones graves."
    ),
    TermsClause(
        title = "5. Carácter Efímero y Derecho de Supresión (Art. 17 RGPD)",
        body = "Los outfits compartidos caducan y se eliminan automáticamente del feed a las 24 horas. Además, tienes el derecho a eliminar y suprimir tu fotografía en cualquier momento desde el menú de la publicación."
    ),
    TermsClause(
        title = "6. Ley Aplicable y Resolución de Litigios (ODR Unión Europea)",
        body = "Rige la legislación española y comunitaria europea. En cumplimiento del Reglamento UE 524/2013, la plataforma de resolución de litigios en línea de la Comisión Europea está disponible en: https://ec.europa.eu/consumers/odr."
    )
)

@Composable
fun CommunityTermsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val webTermsUrl = BuildConfig.SOCIAL_API_BASE_URL
        .ifBlank { "https://myoutfit.duckdns.org" }
        .trimEnd('/') + "/terms"

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Gavel,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = "Términos y Normas (DSA)",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
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
                    text = "Versión 2026.1-EU · Reglamento (UE) 2022/2065 (Ley de Servicios Digitales DSA) y LSSI-CE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                HorizontalDivider()
                DSA_COMMUNITY_CLAUSES.forEach { clause ->
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
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webTermsUrl)))
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
