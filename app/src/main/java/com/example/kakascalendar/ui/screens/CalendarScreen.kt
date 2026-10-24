package com.example.kakascalendar.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kakascalendar.Dash
import com.example.kakascalendar.data.Entry
import com.example.kakascalendar.domain.*
import com.example.kakascalendar.ui.components.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

@Composable
fun CalendarScreen(dash: Dash, onEdit: (Entry) -> Unit, onDelete: (Entry) -> Unit) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf(LocalDate.now()) }
    val byDay = remember(dash.entries) { dash.entries.groupBy { it.date() } }
    val cs = MaterialTheme.colorScheme
    val monthEntries = dash.entries.filter { YearMonth.from(it.date()) == month }
    val dayEntries = byDay[selected].orEmpty()

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GlassCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = { month = month.minusMonths(1) }) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous month")
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${month.month.getDisplayName(JTextStyle.FULL, Locale.getDefault())} ${month.year}",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            "${monthEntries.size} entries · ${monthEntries.map { it.date() }.toSet().size} days",
                            style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant
                        )
                    }
                    FilledTonalIconButton(onClick = { month = month.plusMonths(1) }, enabled = month < YearMonth.now()) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next month")
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row {
                    DayOfWeek.values().forEach {
                        Text(
                            it.getDisplayName(JTextStyle.NARROW, Locale.getDefault()), Modifier.weight(1f),
                            textAlign = TextAlign.Center, color = cs.onSurfaceVariant, style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                AnimatedContent(
                    targetState = month,
                    transitionSpec = {
                        val dir = if (targetState > initialState) 1 else -1
                        (slideInHorizontally(tween(260)) { it / 4 * dir } + fadeIn(tween(200)))
                            .togetherWith(slideOutHorizontally(tween(200)) { -it / 4 * dir } + fadeOut(tween(150)))
                    },
                    label = "month"
                ) { m ->
                    MonthGrid(m, byDay, dash.streakDays, selected) { selected = it }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Legend(cs.secondary.copy(alpha = 0.35f), "Current streak")
                    Spacer(Modifier.width(14.dp))
                    Legend(cs.primary.copy(alpha = 0.16f), "Logged")
                    Spacer(Modifier.width(14.dp))
                    Legend(StoolType.T4.color, "Type dot")
                }
            }
        }
        item {
            SectionTitle("${dayLabel(selected)} · ${dayEntries.size} ${if (dayEntries.size == 1) "entry" else "entries"}")
        }
        if (dayEntries.isEmpty()) item {
            Text("Nothing logged this day.", color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
        }
        items(dayEntries, key = { it.id }) { EntryCard(it, onEdit, onDelete) }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    byDay: Map<LocalDate, List<Entry>>,
    streak: Set<LocalDate>,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit
) {
    val offset = month.atDay(1).dayOfWeek.value - 1  // Monday first
    val len = month.lengthOfMonth()
    val rows = (offset + len + 6) / 7
    Column {
        repeat(rows) { r ->
            Row {
                repeat(7) { c ->
                    val n = r * 7 + c - offset + 1
                    if (n in 1..len) {
                        val date = month.atDay(n)
                        DayCell(date, byDay[date].orEmpty(), date in streak, date == selected) { onSelect(date) }
                    } else Spacer(Modifier.weight(1f).aspectRatio(1f))
                }
            }
        }
    }
}

@Composable
private fun RowScope.DayCell(date: LocalDate, entries: List<Entry>, inStreak: Boolean, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val today = date == LocalDate.now()
    val future = date.isAfter(LocalDate.now())
    val bg = when {
        inStreak -> cs.secondary.copy(alpha = 0.35f)
        entries.isNotEmpty() -> cs.primary.copy(alpha = 0.16f)
        else -> Color.Transparent
    }
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier.weight(1f).aspectRatio(1f).padding(2.dp).clip(shape).background(bg)
            .then(
                when {
                    selected -> Modifier.border(2.dp, cs.primary, shape)
                    today -> Modifier.border(1.dp, cs.outline, shape)
                    else -> Modifier
                }
            )
            .clickable(enabled = !future, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${date.dayOfMonth}", fontSize = 14.sp,
                fontWeight = if (today || entries.isNotEmpty()) FontWeight.ExtraBold else FontWeight.Normal,
                color = when {
                    future -> cs.onSurface.copy(alpha = 0.3f)
                    today -> cs.primary
                    else -> cs.onSurface
                }
            )
            if (entries.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                entries.take(3).forEach {
                    Box(Modifier.size(5.dp).clip(CircleShape).background(it.stool().color))
                }
            }
        }
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
