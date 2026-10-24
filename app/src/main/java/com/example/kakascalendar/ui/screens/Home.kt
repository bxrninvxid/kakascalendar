package com.example.kakascalendar.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kakascalendar.Dash
import com.example.kakascalendar.data.Entry
import com.example.kakascalendar.domain.*
import com.example.kakascalendar.ui.components.*
import java.time.LocalDate
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

@Composable
fun HomeScreen(
    dash: Dash,
    onEdit: (Entry) -> Unit,
    onDelete: (Entry) -> Unit,
    onQuickLog: () -> Unit,
    onTimer: () -> Unit,
    onInsights: () -> Unit
) {
    val grouped = remember(dash.entries) { dash.entries.take(40).groupBy { it.date() } }
    val today = LocalDate.now()

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Hero(dash, today) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickAction("🚽", "Throne timer", "Time your session", Modifier.weight(1f), onTimer)
                QuickAction("⚡", "Quick log", "One-tap perfect 🍌", Modifier.weight(1f), onQuickLog)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Today", "${dash.todayCount}", Modifier.weight(1f), "☀️")
                StatTile("This week", "${dash.weekCount}", Modifier.weight(1f), "📆")
                StatTile("Total", "${dash.entries.size}", Modifier.weight(1f), "💩")
            }
        }
        dash.insights.firstOrNull()?.let { ins ->
            item { InsightCard(ins, onClick = onInsights, showMore = true) }
        }
        if (dash.loaded && dash.entries.isEmpty()) item {
            EmptyState("🚽", "Your throne awaits", "Tap the + button to log your first visit.\nGreatness starts with a single drop.")
        }
        grouped.forEach { (date, list) ->
            item(key = "h$date") { SectionTitle(dayLabel(date)) }
            items(list, key = { it.id }) { EntryCard(it, onEdit, onDelete) }
        }
    }
}

@Composable
private fun Hero(dash: Dash, today: LocalDate) {
    val t = rememberInfiniteTransition(label = "hero")
    val drift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Reverse), label = "drift")
    val cs = MaterialTheme.colorScheme
    val shown by animateIntAsState(dash.streak, tween(1000, easing = FastOutSlowInEasing), label = "streak")
    val brush = Brush.linearGradient(
        listOf(cs.primary, cs.secondary, cs.tertiary),
        start = Offset(0f, 600f * drift),
        end = Offset(1200f, 600f * (1f - drift))
    )
    val white = Color.White
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(brush).padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    PulsingEmoji("🔥", 44.sp, active = dash.streak > 0)
                    Spacer(Modifier.width(6.dp))
                    Text("$shown", fontSize = 60.sp, lineHeight = 60.sp, fontWeight = FontWeight.Black, color = white)
                }
                Text(if (dash.streak == 1) "day streak" else "day streak", color = white.copy(alpha = 0.9f), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        dash.streak == 0 -> "Log today to start a streak"
                        !dash.loggedToday -> "Log today to keep it alive!"
                        dash.streak >= dash.best -> "Personal best. Unstoppable. 👑"
                        else -> "${dash.best - dash.streak} more to beat your best (${dash.best})"
                    },
                    color = white.copy(alpha = 0.92f), style = MaterialTheme.typography.bodyMedium
                )
            }
            ProgressRing(
                fraction = if (dash.loggedToday) 1f else 0f,
                modifier = Modifier.size(92.dp),
                stroke = 9.dp,
                track = white.copy(alpha = 0.25f),
                brush = Brush.linearGradient(listOf(white, white))
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (dash.loggedToday) "✅" else "⏳", fontSize = 26.sp)
                    Text(if (dash.loggedToday) "Done" else "Today", color = white, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            (6 downTo 0).forEach { off ->
                val d = today.minusDays(off.toLong())
                WeekDot(d, d in dash.days, d == today)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Lv ${dash.level.level}", color = white, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(8.dp))
            Text(dash.level.title, color = white.copy(alpha = 0.9f), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), maxLines = 1)
            Text("${dash.level.into}/${dash.level.need} XP", color = white.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(6.dp))
        SlimProgress(dash.level.fraction, track = white.copy(alpha = 0.25f), brush = Brush.linearGradient(listOf(white, white.copy(alpha = 0.85f))))
    }
}

@Composable
private fun WeekDot(date: LocalDate, logged: Boolean, isToday: Boolean) {
    val white = Color.White
    val cs = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(date.dayOfWeek.getDisplayName(JTextStyle.NARROW, Locale.getDefault()), fontSize = 12.sp,
            color = white.copy(alpha = 0.85f), fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier.size(34.dp).clip(CircleShape)
                .background(if (logged) white else white.copy(alpha = 0.18f))
                .then(if (isToday && !logged) Modifier.border(2.dp, white, CircleShape) else Modifier),
            contentAlignment = Alignment.Center
        ) { if (logged) Text("✓", color = cs.primary, fontWeight = FontWeight.Black) }
    }
}

@Composable
private fun QuickAction(emoji: String, title: String, sub: String, modifier: Modifier, onClick: () -> Unit) {
    GlassCard(modifier, onClick = onClick, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) { Text(emoji, fontSize = 22.sp) }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable
fun InsightCard(ins: Insight, onClick: (() -> Unit)? = null, showMore: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    val accent = when (ins.tone) {
        Tone.GOOD -> Color(0xFF3FAF7A)
        Tone.WARN -> Color(0xFFE0913A)
        Tone.NEUTRAL -> cs.primary
    }
    GlassCard(onClick = onClick, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) { Text(ins.emoji, fontSize = 22.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(ins.title, fontWeight = FontWeight.Bold)
                Text(ins.body, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            if (showMore) Text("›", fontSize = 24.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp))
        }
    }
}
