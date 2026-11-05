package com.example.myoutfit.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.myoutfit.data.remote.social.YearlyRecapDto
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

data class CalendarDayOutfitUi(
    val date: String,
    val thumbnailModel: String?,
    val fullImageModel: String?,
    val caption: String = "",
    val styleTags: List<String> = emptyList(),
    val isSyncedOnServer: Boolean = false
)

private val WEEK_DAYS_ES = listOf("L", "M", "X", "J", "V", "S", "D")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OutfitCalendarSection(
    year: Int,
    month: Int,
    dayEntries: Map<Int, CalendarDayOutfitUi>,
    yearlyRecap: YearlyRecapDto?,
    isCloudConnected: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedDayOutfit by remember { mutableStateOf<CalendarDayOutfitUi?>(null) }

    val yearMonth = remember(year, month) { YearMonth.of(year, month) }
    val monthTitle = remember(yearMonth) {
        val rawName = yearMonth.month.getDisplayName(TextStyle.FULL, Locale("es", "ES"))
        "${rawName.replaceFirstChar { it.uppercase() }} $year"
    }
    val today = remember { LocalDate.now() }

    // Lunes = 0 .. Domingo = 6
    val leadingEmptySlots = remember(yearMonth) {
        yearMonth.atDay(1).dayOfWeek.value - 1
    }
    val daysInMonth = yearMonth.lengthOfMonth()
    val totalCells = ((leadingEmptySlots + daysInMonth + 6) / 7) * 7

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Cabecera del Calendario Privado
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Calendario Privado",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isCloudConnected) Icons.Default.CloudDone else Icons.Default.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isCloudConnected) {
                                        "Sincronizado en tu cuenta privada"
                                    } else {
                                        "Solo visible para ti · ${dayEntries.size} días este mes"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Selector de Mes (< Mes Año >)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onPreviousMonth) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Mes anterior"
                        )
                    }
                    Text(
                        text = monthTitle,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    IconButton(onClick = onNextMonth) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Mes siguiente"
                        )
                    }
                }

                // Cabecera L M X J V S D
                Row(modifier = Modifier.fillMaxWidth()) {
                    WEEK_DAYS_ES.forEach { label ->
                        Text(
                            text = label,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Cuadrícula mensual de miniaturas 3:4
                val rowsCount = totalCells / 7
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (week in 0 until rowsCount) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            for (dow in 0 until 7) {
                                val cellIndex = week * 7 + dow
                                val dayNum = cellIndex - leadingEmptySlots + 1
                                if (dayNum in 1..daysInMonth) {
                                    val entry = dayEntries[dayNum]
                                    val isToday = today.year == year &&
                                        today.monthValue == month &&
                                        today.dayOfMonth == dayNum

                                    CalendarDayCell(
                                        dayNumber = dayNum,
                                        entry = entry,
                                        isToday = isToday,
                                        onClick = { if (entry != null) selectedDayOutfit = entry },
                                        modifier = Modifier.weight(1f)
                                    )
                                } else {
                                    Spacer(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(0.78f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Tarjeta de Avance del "Yearly Recap"
        if (yearlyRecap != null && yearlyRecap.uniqueDaysLogged > 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Yearly Recap ${yearlyRecap.year} · ${yearlyRecap.uniqueDaysLogged} días registrados",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }

                    if (yearlyRecap.topStyles.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            yearlyRecap.topStyles.forEach { stat ->
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                                ) {
                                    Text(
                                        text = "${stat.style} (${stat.count})",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (selectedDayOutfit != null) {
        val outfit = selectedDayOutfit!!
        AlertDialog(
            onDismissRequest = { selectedDayOutfit = null },
            title = {
                Text(
                    text = "Outfit del ${outfit.date}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val imgModel = outfit.fullImageModel ?: outfit.thumbnailModel
                    if (imgModel != null) {
                        AsyncImage(
                            model = imgModel,
                            contentDescription = outfit.caption,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.75f)
                                .clip(RoundedCornerShape(16.dp))
                        )
                    }
                    if (outfit.caption.isNotBlank()) {
                        Text(
                            text = outfit.caption,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (outfit.styleTags.isNotEmpty()) {
                        Text(
                            text = outfit.styleTags.joinToString(" · ") { "#$it" },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedDayOutfit = null }) {
                    Text("Cerrar")
                }
            }
        )
    }
}

@Composable
private fun CalendarDayCell(
    dayNumber: Int,
    entry: CalendarDayOutfitUi?,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(0.78f)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (entry != null) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                }
            )
            .then(
                if (isToday) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(10.dp)
                    )
                } else Modifier
            )
            .clickable(enabled = entry != null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (entry?.thumbnailModel != null) {
            AsyncImage(
                model = entry.thumbnailModel,
                contentDescription = "Outfit día $dayNumber",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(3.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$dayNumber",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        } else {
            Text(
                text = "$dayNumber",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = if (entry != null || isToday) FontWeight.Bold else FontWeight.Normal
                ),
                color = if (entry != null) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}
