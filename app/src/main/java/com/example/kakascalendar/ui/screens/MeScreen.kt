package com.example.kakascalendar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.ui.graphics.toArgb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kakascalendar.Dash
import com.example.kakascalendar.MainVm
import com.example.kakascalendar.data.Prefs
import com.example.kakascalendar.domain.Achievement
import com.example.kakascalendar.domain.Tier
import com.example.kakascalendar.domain.RANKS
import com.example.kakascalendar.domain.XP_LOGS_PER_DAY
import com.example.kakascalendar.domain.MAX_LOGS_PER_DAY
import com.example.kakascalendar.ui.components.*

@Composable
fun TopBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
        actions()
    }
}

private fun tierColor(t: Tier) = when (t) {
    Tier.BRONZE -> Color(0xFFCD8B5A)
    Tier.SILVER -> Color(0xFF9EA7B3)
    Tier.GOLD -> Color(0xFFE8B931)
    Tier.LEGEND -> Color(0xFFB06BFF)
}

@Composable
fun MeScreen(vm: MainVm, prefs: Prefs, dash: Dash, onBack: () -> Unit, onSettings: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var detail by remember { mutableStateOf<Achievement?>(null) }
    val done = dash.achievements.count { it.unlocked }
    val sorted = remember(dash.achievements) {
        dash.achievements.sortedWith(compareByDescending<Achievement> { it.unlocked }.thenByDescending { it.fraction })
    }

    Column(Modifier.fillMaxSize()) {
        val shareColors = listOf(cs.primary.toArgb(), cs.secondary.toArgb(), cs.tertiary.toArgb())
        TopBar("Profile", onBack) {
            IconButton(onClick = { vm.shareBrag(shareColors) }) { Icon(Icons.Rounded.Share, "Share") }
            IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Settings") }
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    ProgressRing(dash.level.fraction, Modifier.size(128.dp), stroke = 8.dp) {
                        MyAvatar(prefs, 100.dp, onClick = onSettings)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(prefs.name.ifBlank { "Champion" }, style = MaterialTheme.typography.headlineMedium)
                    prefs.username?.let { Text("@$it", color = cs.onSurfaceVariant) }
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.clip(RoundedCornerShape(50)).background(accentBrush()).padding(horizontal = 14.dp, vertical = 6.dp)) {
                        Text("Level ${dash.level.level} · ${dash.level.title}", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("${dash.level.into} / ${dash.level.need} XP to level ${dash.level.level + 1} · ${dash.level.xp} XP total",
                        style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Entries", "${dash.entries.size}", Modifier.weight(1f), "💩")
                    StatTile("Best streak", "${dash.best}d", Modifier.weight(1f), "👑")
                    StatTile("Trophies", "$done/${dash.achievements.size}", Modifier.weight(1f), "🏆")
                }
            }
            item {
                var expanded by remember { mutableStateOf(false) }
                GlassCard(onClick = { expanded = !expanded }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Rank ladder", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(if (expanded) "Hide ▲" else "Show all ▼", style = MaterialTheme.typography.labelLarge, color = cs.primary)
                    }
                    Text("~18 XP per logged day (first $XP_LOGS_PER_DAY logs count) + trophy XP. Max $MAX_LOGS_PER_DAY logs a day.",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    val current = dash.level.level.coerceAtMost(RANKS.size)
                    val shown = if (expanded) RANKS.indices.toList()
                    else listOf(current - 2, current - 1, current).filter { it in RANKS.indices }
                    Spacer(Modifier.height(8.dp))
                    shown.forEach { i ->
                        val r = RANKS[i]
                        val reached = dash.level.xp >= r.minXp
                        val isCurrent = i == current - 1
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                .background(if (isCurrent) cs.primaryContainer else Color.Transparent)
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(r.emoji, fontSize = 20.sp, modifier = Modifier.width(32.dp).alpha(if (reached) 1f else 0.4f))
                            Text("${i + 1}. ${r.title}", modifier = Modifier.weight(1f).alpha(if (reached) 1f else 0.55f),
                                fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Normal)
                            Text(if (reached) "✓" else "${r.minXp} XP", style = MaterialTheme.typography.labelMedium,
                                color = if (reached) cs.primary else cs.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                GlassCard {
                    Text("Trophy shelf", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    SlimProgress(done.toFloat() / dash.achievements.size.coerceAtLeast(1))
                    Spacer(Modifier.height(6.dp))
                    Text("Tap any trophy for details. XP from trophies stacks with XP from logging.",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
            items(sorted.chunked(3)) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { a -> AchievementTile(a, Modifier.weight(1f)) { detail = a } }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }

    detail?.let { a ->
        AlertDialog(
            onDismissRequest = { detail = null },
            icon = { Text(if (a.unlocked) a.emoji else "🔒", fontSize = 42.sp) },
            title = { Text(a.title, textAlign = TextAlign.Center) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(a.desc, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    SlimProgress(a.fraction)
                    Spacer(Modifier.height(6.dp))
                    Text("${a.progress.coerceAtMost(a.goal)} / ${a.goal} · ${a.tier.label} · +${a.xp} XP",
                        style = MaterialTheme.typography.labelMedium)
                }
            },
            confirmButton = { TextButton(onClick = { detail = null }) { Text(if (a.unlocked) "Glorious" else "On it") } }
        )
    }
}

@Composable
private fun AchievementTile(a: Achievement, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val tc = tierColor(a.tier)
    GlassCard(
        modifier.alpha(if (a.unlocked) 1f else 0.62f), onClick = onClick, padding = 10.dp,
        tint = if (a.unlocked) tc.copy(alpha = 0.18f) else null
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (a.unlocked) a.emoji else "🔒", fontSize = 30.sp)
            Spacer(Modifier.height(4.dp))
            Text(a.title, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2, minLines = 2)
            Spacer(Modifier.height(6.dp))
            SlimProgress(a.fraction, height = 5.dp, brush = androidx.compose.ui.graphics.SolidColor(if (a.unlocked) tc else cs.primary))
            Spacer(Modifier.height(2.dp))
            Text(a.tier.label, fontSize = 10.sp, color = tc, fontWeight = FontWeight.Bold)
        }
    }
}
