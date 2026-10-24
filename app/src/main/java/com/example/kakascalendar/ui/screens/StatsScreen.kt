package com.example.kakascalendar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kakascalendar.Dash
import com.example.kakascalendar.domain.*
import com.example.kakascalendar.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(dash: Dash) {
    var periodIdx by rememberSaveable { mutableIntStateOf(1) }
    val period = Period.entries[periodIdx]
    val cs = MaterialTheme.colorScheme
    val e = remember(dash.entries, period) { dash.entries.inPeriod(period) }
    val bars = remember(dash.entries, period) { activityBars(dash.entries, period) }
    val counts = remember(e) { typeCounts(e) }
    val hours = remember(e) { hourBars(e) }
    val weekdays = remember(e) { weekdayBars(e) }
    val heat = remember(dash.entries) { heatCounts(dash.entries) }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Period.entries.forEachIndexed { i, p ->
                    SegmentedButton(
                        selected = i == periodIdx,
                        onClick = { periodIdx = i },
                        shape = SegmentedButtonDefaults.itemShape(i, Period.entries.size)
                    ) { Text(p.label) }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Entries", "${e.size}", Modifier.weight(1f), "💩")
                    StatTile("Per day", "%.1f".format(avgPerDay(e, period)), Modifier.weight(1f), "📊")
                    StatTile("Perfect", "${perfectRate(e)}%", Modifier.weight(1f), "🍌")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Avg time", formatDuration(avgDuration(e)), Modifier.weight(1f), "⏱", valueSize = 18.sp)
                    val r = avgRating(e)
                    StatTile("Avg feel", if (r == 0.0) "–" else "${RATINGS[(r.toInt() - 1).coerceIn(0, 4)]} %.1f".format(r),
                        Modifier.weight(1f), "💭", valueSize = 18.sp)
                    StatTile("Best streak", "${dash.best}d", Modifier.weight(1f), "👑", valueSize = 18.sp)
                }
            }
        }
        item {
            GlassCard {
                Text("Activity", style = MaterialTheme.typography.titleMedium)
                Text(
                    when (period) {
                        Period.WEEK -> "Entries per day, last 7 days"
                        Period.MONTH -> "Entries per day, last 30 days"
                        Period.QUARTER -> "Entries per week, last 13 weeks"
                        Period.ALL -> "Entries per month, last 12 months"
                    },
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                BarChart(bars, height = 150.dp)
            }
        }
        item {
            GlassCard {
                Text("Type breakdown", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Donut(
                        StoolType.entries.map { it.color to (counts[it.n] ?: 0) },
                        Modifier.size(140.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${e.size}", fontSize = 26.sp, fontWeight = FontWeight.Black)
                            Text("total", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        StoolType.entries.forEach { t ->
                            val c = counts[t.n] ?: 0
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(t.color))
                                Spacer(Modifier.width(6.dp))
                                Text("${t.emoji} ${t.label}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1)
                                Text(if (e.isEmpty()) "0%" else "${100 * c / e.size}%", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
        item {
            val places = placeCounts(e)
            val wipesAvg = avgWipes(e)
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Throne map", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(if (wipesAvg == 0.0) "🧻 –" else "🧻 %.1f avg wipes".format(wipesAvg),
                        style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                }
                Spacer(Modifier.height(10.dp))
                if (places.isEmpty()) {
                    Text("Pick a place when logging to see where you rule.", style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant)
                }
                val maxP = places.maxOfOrNull { it.second } ?: 1
                places.forEach { (pl, n) ->
                    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${pl.emoji} ${pl.label}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(96.dp), maxLines = 1)
                        Box(Modifier.weight(1f)) { SlimProgress(n.toFloat() / maxP, height = 10.dp) }
                        Text("$n", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(36.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End)
                    }
                }
            }
        }
        item {
            GlassCard {
                Text("Time of day", style = MaterialTheme.typography.titleMedium)
                Text("When nature calls", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(14.dp))
                BarChart(hours, height = 110.dp, color = cs.secondary)
            }
        }
        item {
            GlassCard {
                Text("Day of week", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(14.dp))
                BarChart(weekdays, height = 110.dp, color = cs.tertiary)
            }
        }
        item {
            GlassCard {
                Text("Last 6 months", style = MaterialTheme.typography.titleMedium)
                Text("Every square is a day. Darker = busier.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Heatmap(heat)
            }
        }
        item { SectionTitle("Smart insights") }
        items(dash.insights) { InsightCard(it) }
        item {
            Text(
                "Insights are friendly patterns, not medical advice. If something feels off, talk to a doctor.",
                style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
        }
    }
}
