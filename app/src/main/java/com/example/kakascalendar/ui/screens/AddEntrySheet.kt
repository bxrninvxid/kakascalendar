package com.example.kakascalendar.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kakascalendar.data.Entry
import com.example.kakascalendar.domain.*
import com.example.kakascalendar.ui.components.GradientButton
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEntrySheet(initial: Entry?, prefillDuration: Int, onDismiss: () -> Unit, onSave: (Entry) -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val start = initial?.let { Instant.ofEpochMilli(it.timestamp).atZone(zone()).toLocalDateTime() }
        ?: LocalDateTime.now().withSecond(0).withNano(0)

    var type by remember { mutableIntStateOf(initial?.type ?: 4) }
    var comment by remember { mutableStateOf(initial?.comment ?: "") }
    var tags by remember { mutableStateOf(initial?.tagList()?.toSet() ?: emptySet()) }
    var date by remember { mutableStateOf(start.toLocalDate()) }
    var time by remember { mutableStateOf(start.toLocalTime()) }
    var rating by remember { mutableIntStateOf(initial?.rating ?: 0) }
    var duration by remember { mutableIntStateOf(initial?.durationSec ?: prefillDuration) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = cs.surfaceContainerLow
    ) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(if (initial == null) "Log a poop 💩" else "Edit entry ✏️", style = MaterialTheme.typography.headlineSmall)

            // When
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = {
                        DatePickerDialog(ctx, { _, y, m, d -> date = LocalDate.of(y, m + 1, d) },
                            date.year, date.monthValue - 1, date.dayOfMonth
                        ).apply { datePicker.maxDate = System.currentTimeMillis() }.show()
                    },
                    label = { Text("📅  ${dayLabel(date)}") }
                )
                AssistChip(
                    onClick = { TimePickerDialog(ctx, { _, h, m -> time = LocalTime.of(h, m) }, time.hour, time.minute, false).show() },
                    label = { Text("🕒  ${time.format(DateTimeFormatter.ofPattern("h:mm a"))}") }
                )
            }

            // Type
            FieldLabel("What came out?")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StoolType.entries.forEach { t ->
                    val sel = t.n == type
                    val scale by animateFloatAsState(if (sel) 1.08f else 1f, label = "tile")
                    val shape = RoundedCornerShape(20.dp)
                    Column(
                        Modifier.padding(vertical = 4.dp).scale(scale).clip(shape)
                            .background(if (sel) t.color.copy(alpha = 0.28f) else cs.surfaceContainerHigh)
                            .then(if (sel) Modifier.border(2.dp, t.color, shape) else Modifier)
                            .clickable { type = t.n }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(t.emoji, fontSize = 30.sp)
                        Text("${t.n}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
                    }
                }
            }
            val sel = StoolType.of(type)
            Column {
                Text("Type ${sel.n} · ${sel.label}", fontWeight = FontWeight.Bold, color = sel.color)
                Text(sel.desc, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }

            // Feel
            FieldLabel("How did it feel?")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                RATINGS.forEachIndexed { i, r ->
                    val v = i + 1
                    val on = rating == v
                    val scale by animateFloatAsState(if (on) 1.25f else 1f, label = "rate")
                    Column(
                        Modifier.clip(RoundedCornerShape(16.dp)).clickable { rating = if (on) 0 else v }.padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(r, fontSize = 30.sp, modifier = Modifier.scale(scale))
                        Text(RATING_LABELS[i], style = MaterialTheme.typography.labelSmall,
                            color = if (on) cs.primary else cs.onSurfaceVariant, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }

            // Duration
            FieldLabel("Session length")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val presets = listOf(60, 180, 300, 600, 900)
                if (duration > 0 && duration !in presets) {
                    FilterChip(selected = true, onClick = { duration = 0 }, label = { Text("⏱ ${formatDuration(duration)}") })
                }
                presets.forEach { s ->
                    FilterChip(selected = duration == s, onClick = { duration = if (duration == s) 0 else s },
                        label = { Text("${s / 60} min") })
                }
            }

            // Tags
            FieldLabel("Tags")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TAGS.forEach { tg ->
                    FilterChip(
                        selected = tg in tags,
                        onClick = { tags = if (tg in tags) tags - tg else tags + tg },
                        label = { Text(tg) }
                    )
                }
            }

            OutlinedTextField(
                value = comment, onValueChange = { comment = it.take(500) },
                label = { Text("Comment (stays on your phone)") },
                modifier = Modifier.fillMaxWidth(), minLines = 2, shape = RoundedCornerShape(16.dp)
            )

            GradientButton(if (initial == null) "Save 💩" else "Update", Modifier.fillMaxWidth()) {
                val ts = LocalDateTime.of(date, time).atZone(zone()).toInstant().toEpochMilli()
                    .coerceAtMost(System.currentTimeMillis())
                onSave(
                    Entry(
                        id = initial?.id ?: 0,
                        timestamp = ts,
                        type = type,
                        comment = comment.trim(),
                        tags = TAGS.filter { it in tags }.joinToString(","),
                        durationSec = duration,
                        rating = rating
                    )
                )
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
