package com.example.kakascalendar.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kakascalendar.data.Entry
import com.example.kakascalendar.domain.*
import java.time.LocalDate
import kotlin.random.Random

// ───────────────────────── Surfaces ─────────────────────────

/** Slowly drifting colour blobs behind everything. */
@Composable
fun AppBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    val t = rememberInfiniteTransition(label = "bg")
    val shift by t.animateFloat(
        0f, 1f, infiniteRepeatable(tween(16_000, easing = LinearEasing), RepeatMode.Reverse), label = "shift"
    )
    Box(
        modifier
            .fillMaxSize()
            .background(cs.background)
            .drawBehind {
                val w = size.width
                val h = size.height
                fun blob(c: Color, a: Float, x: Float, y: Float, r: Float) {
                    val center = Offset(x, y)
                    drawCircle(Brush.radialGradient(listOf(c.copy(alpha = a), Color.Transparent), center, r), r, center)
                }
                blob(cs.primary, 0.20f, w * (0.05f + 0.35f * shift), h * 0.06f, w * 0.95f)
                blob(cs.secondary, 0.16f, w * (1f - 0.3f * shift), h * 0.42f, w * 0.8f)
                blob(cs.tertiary, 0.12f, w * (0.2f + 0.2f * shift), h * 0.92f, w * 0.9f)
            },
        content = content
    )
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = 16.dp,
    tint: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier
            .clip(shape)
            .background(tint ?: cs.surfaceContainerLow.copy(alpha = 0.78f))
            .border(1.dp, cs.onSurface.copy(alpha = 0.07f), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content
    )
}

@Composable
fun accentBrush(): Brush {
    val cs = MaterialTheme.colorScheme
    return Brush.linearGradient(listOf(cs.primary, cs.secondary, cs.tertiary))
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(start = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
fun Avatar(emoji: String, size: Dp = 44.dp, ring: Boolean = false, onClick: (() -> Unit)? = null) {
    val cs = MaterialTheme.colorScheme
    val brush = accentBrush()
    Box(
        Modifier
            .size(size)
            .then(if (ring) Modifier.border(2.5.dp, brush, CircleShape).padding(4.dp) else Modifier)
            .clip(CircleShape)
            .background(cs.primaryContainer)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(emoji, fontSize = (size.value * if (ring) 0.42f else 0.5f).sp)
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, emoji: String? = null, valueSize: TextUnit = 22.sp) {
    GlassCard(modifier, padding = 14.dp) {
        if (emoji != null) Text(emoji, fontSize = 18.sp)
        Text(value, fontSize = valueSize, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primaryContainer,
         textColor: Color = MaterialTheme.colorScheme.onPrimaryContainer) {
    Text(
        text, fontSize = 11.sp, color = textColor, fontWeight = FontWeight.Medium, maxLines = 1,
        modifier = modifier.clip(RoundedCornerShape(50)).background(color).padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@Composable
fun GradientButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val brush = accentBrush()
    Box(
        modifier
            .height(54.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled) brush else Brush.linearGradient(listOf(Color.Gray, Color.Gray)))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

// ───────────────────────── Progress ─────────────────────────

@Composable
fun ProgressRing(
    fraction: Float, modifier: Modifier = Modifier, stroke: Dp = 10.dp,
    track: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
    brush: Brush = accentBrush(), content: @Composable BoxScope.() -> Unit = {}
) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(900, easing = FastOutSlowInEasing), label = "ring")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val s = stroke.toPx()
            val arcSize = Size(size.width - s, size.height - s)
            val tl = Offset(s / 2, s / 2)
            drawArc(track, 0f, 360f, false, tl, arcSize, style = Stroke(s))
            if (f > 0f) drawArc(brush, -90f, 360f * f, false, tl, arcSize, style = Stroke(s, cap = StrokeCap.Round))
        }
        content()
    }
}

@Composable
fun SlimProgress(fraction: Float, modifier: Modifier = Modifier, height: Dp = 8.dp,
                 track: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), brush: Brush = accentBrush()) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(800), label = "bar")
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(track)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(f).clip(CircleShape).background(brush))
    }
}

// ───────────────────────── Entries ─────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryCard(e: Entry, onClick: (Entry) -> Unit, onDelete: (Entry) -> Unit) {
    val t = e.stool()
    val cs = MaterialTheme.colorScheme
    GlassCard(onClick = { onClick(e) }, padding = 14.dp) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(50.dp).clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(t.color.copy(alpha = 0.35f), t.color.copy(alpha = 0.15f)))),
                contentAlignment = Alignment.Center
            ) { Text(t.emoji, fontSize = 24.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Type ${t.n} · ${t.label}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
                    if (e.rating > 0) Text("  ${RATINGS[e.rating - 1]}", fontSize = 15.sp)
                }
                val meta = buildString {
                    append(e.dateTime().format(java.time.format.DateTimeFormatter.ofPattern("h:mm a")))
                    if (e.durationSec > 0) append(" · ⏱ ${formatDuration(e.durationSec)}")
                }
                Text(meta, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                val tags = e.tagList()
                if (tags.isNotEmpty()) FlowRow(
                    Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) { tags.forEach { Pill(it) } }
                if (e.comment.isNotBlank()) Text(
                    e.comment, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp),
                    maxLines = 3, overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = { onDelete(e) }, modifier = Modifier.size(36.dp)) {
                Text("🗑️", fontSize = 16.sp)
            }
        }
    }
}

// ───────────────────────── Charts ─────────────────────────

@Composable
fun BarChart(bars: List<Bar>, modifier: Modifier = Modifier, height: Dp = 140.dp, color: Color = MaterialTheme.colorScheme.primary) {
    val max = (bars.maxOfOrNull { it.value } ?: 0).coerceAtLeast(1)
    val anim = remember(bars) { Animatable(0f) }
    LaunchedEffect(bars) { anim.animateTo(1f, tween(750, easing = FastOutSlowInEasing)) }
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    val top = MaterialTheme.colorScheme.secondary
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            if (bars.isEmpty()) return@Canvas
            val slot = size.width / bars.size
            val bw = (slot * 0.62f).coerceAtMost(26.dp.toPx())
            bars.forEachIndexed { i, b ->
                val x = i * slot + (slot - bw) / 2
                drawRoundRect(track, Offset(x, 0f), Size(bw, size.height), CornerRadius(bw / 2))
                if (b.value > 0) {
                    val h = (size.height * b.value / max * anim.value).coerceAtLeast(bw)
                    drawRoundRect(
                        Brush.verticalGradient(listOf(top, color), startY = size.height - h, endY = size.height),
                        Offset(x, size.height - h), Size(bw, h), CornerRadius(bw / 2)
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            bars.forEach {
                Text(
                    it.label, Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1, softWrap = false,
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun Donut(slices: List<Pair<Color, Int>>, modifier: Modifier = Modifier, stroke: Dp = 18.dp, center: @Composable BoxScope.() -> Unit = {}) {
    val total = slices.sumOf { it.second }
    val anim = remember(slices) { Animatable(0f) }
    LaunchedEffect(slices) { anim.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val s = stroke.toPx()
            val tl = Offset(s / 2, s / 2)
            val arc = Size(size.width - s, size.height - s)
            drawArc(track, 0f, 360f, false, tl, arc, style = Stroke(s))
            if (total == 0) return@Canvas
            val nonZero = slices.count { it.second > 0 }
            val gap = if (nonZero > 1) 6f else 0f
            var start = -90f
            slices.forEach { (c, v) ->
                if (v == 0) return@forEach
                val sweep = 360f * v / total
                drawArc(c, start + gap / 2, ((sweep - gap) * anim.value).coerceAtLeast(0.1f), false, tl, arc,
                    style = Stroke(s, cap = StrokeCap.Round))
                start += sweep
            }
        }
        center()
    }
}

/** GitHub-style activity grid for the last [weeks] weeks (rows = Mon..Sun). */
@Composable
fun Heatmap(counts: Map<LocalDate, Int>, modifier: Modifier = Modifier, weeks: Int = 26) {
    val cs = MaterialTheme.colorScheme
    val today = LocalDate.now()
    val start = today.minusDays(today.dayOfWeek.value - 1L).minusWeeks(weeks - 1L)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val gap = 3.dp
        val cell = (maxWidth - gap * (weeks - 1)) / weeks
        Canvas(Modifier.fillMaxWidth().height(cell * 7 + gap * 6)) {
            val c = cell.toPx()
            val g = gap.toPx()
            for (w in 0 until weeks) for (d in 0..6) {
                val date = start.plusDays(w * 7L + d)
                if (date.isAfter(today)) continue
                val n = counts[date] ?: 0
                val color = when {
                    n == 0 -> cs.onSurface.copy(alpha = 0.07f)
                    n == 1 -> cs.primary.copy(alpha = 0.4f)
                    n == 2 -> cs.primary.copy(alpha = 0.65f)
                    else -> cs.primary
                }
                drawRoundRect(color, Offset(w * (c + g), d * (c + g)), Size(c, c), CornerRadius(c * 0.3f))
                if (date == today) drawRoundRect(cs.secondary, Offset(w * (c + g), d * (c + g)), Size(c, c),
                    CornerRadius(c * 0.3f), style = Stroke(1.5.dp.toPx()))
            }
        }
    }
}

// ───────────────────────── Celebration ─────────────────────────

private class Particle(
    val x: Float, val vx: Float, val vy: Float, val rot: Float, val vr: Float,
    val color: Color, val w: Float, val h: Float
)

/** Confetti burst. Increment [trigger] to fire again. */
@Composable
fun Confetti(trigger: Int, modifier: Modifier = Modifier) {
    if (trigger == 0) return
    val cs = MaterialTheme.colorScheme
    val palette = listOf(cs.primary, cs.secondary, cs.tertiary, Color(0xFFFFD54F), Color(0xFF3FAF7A), Color(0xFF4F8FD6))
    val particles = remember(trigger) {
        List(140) {
            Particle(
                x = 0.5f + (Random.nextFloat() - 0.5f) * 0.2f,
                vx = (Random.nextFloat() - 0.5f) * 1.1f,
                vy = -(0.5f + Random.nextFloat() * 0.9f),
                rot = Random.nextFloat() * 360f,
                vr = (Random.nextFloat() - 0.5f) * 900f,
                color = palette.random(),
                w = 5f + Random.nextFloat() * 6f,
                h = 8f + Random.nextFloat() * 8f
            )
        }
    }
    val progress = remember(trigger) { Animatable(0f) }
    LaunchedEffect(trigger) { progress.animateTo(1f, tween(2800, easing = LinearEasing)) }
    if (progress.value >= 1f) return
    Canvas(modifier.fillMaxSize()) {
        val t = progress.value * 2.8f
        val alpha = ((1f - progress.value) * 1.8f).coerceIn(0f, 1f)
        particles.forEach { p ->
            val x = (p.x + p.vx * t) * size.width
            val y = size.height * 0.32f + (p.vy * t + 0.75f * t * t) * size.height * 0.5f
            val pw = p.w.dp.toPx()
            val ph = p.h.dp.toPx()
            rotate(p.rot + p.vr * t, Offset(x, y)) {
                drawRect(p.color.copy(alpha = alpha), Offset(x - pw / 2, y - ph / 2), Size(pw, ph))
            }
        }
    }
}

@Composable
fun PulsingEmoji(emoji: String, size: TextUnit, modifier: Modifier = Modifier, active: Boolean = true) {
    val t = rememberInfiniteTransition(label = "pulse")
    val s by t.animateFloat(1f, if (active) 1.12f else 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "s")
    val r by t.animateFloat(-4f, if (active) 4f else -4f, infiniteRepeatable(tween(1300), RepeatMode.Reverse), label = "r")
    Text(emoji, fontSize = size, modifier = modifier.scale(s).rotate(r))
}

@Composable
fun EmptyState(emoji: String, title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        PulsingEmoji(emoji, 56.sp)
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun OutlineChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick, modifier = modifier, shape = RoundedCornerShape(50),
        color = if (selected) cs.primary else cs.surfaceContainerHigh,
        contentColor = if (selected) cs.onPrimary else cs.onSurface,
        border = if (selected) null else BorderStroke(1.dp, cs.outlineVariant)
    ) {
        Text(text, Modifier.padding(horizontal = 14.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
    }
}
