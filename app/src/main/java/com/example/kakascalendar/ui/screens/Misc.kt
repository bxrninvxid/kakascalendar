package com.example.kakascalendar.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kakascalendar.domain.AVATARS
import com.example.kakascalendar.domain.formatClock
import com.example.kakascalendar.ui.components.*
import kotlinx.coroutines.delay

// ───────────────────────── Onboarding ─────────────────────────

@Composable
fun Onboarding(onDone: (String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var avatar by rememberSaveable { mutableStateOf(AVATARS.first()) }
    val cs = MaterialTheme.colorScheme
    AppBackground {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
                .verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))
            PulsingEmoji(avatar, 96.sp)
            Spacer(Modifier.height(12.dp))
            Text("Kakascalendar", style = MaterialTheme.typography.displaySmall, color = cs.primary)
            Text(
                "The greatest bowel tracker ever built.\nStreaks. Trophies. A squad. Zero shame.",
                textAlign = TextAlign.Center, color = cs.onSurfaceVariant
            )
            Spacer(Modifier.height(28.dp))
            GlassCard(Modifier.fillMaxWidth()) {
                Text("What should we call you?", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    name, { name = it.take(30) }, placeholder = { Text("Champion") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text("Pick your avatar", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                AvatarPicker(avatar) { avatar = it }
            }
            Spacer(Modifier.height(20.dp))
            GradientButton("Let's go 🚀", Modifier.fillMaxWidth()) { onDone(name, avatar) }
            Spacer(Modifier.height(10.dp))
            Text("Everything stays on your phone unless you join a Squad.", style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

// ───────────────────────── Lock ─────────────────────────

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    LaunchedEffect(Unit) { onUnlock() }
    AppBackground {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            PulsingEmoji("🔒", 72.sp)
            Spacer(Modifier.height(16.dp))
            Text("Kakascalendar is locked", style = MaterialTheme.typography.headlineSmall)
            Text("Your throne, your secrets.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(28.dp))
            GradientButton("Unlock", Modifier.fillMaxWidth(0.7f), onClick = onUnlock)
        }
    }
}

// ───────────────────────── Throne timer ─────────────────────────

private val QUOTES = listOf(
    "Greatness can't be rushed.",
    "Breathe in. Breathe out. Let go.",
    "Every legend sits here eventually.",
    "Your streak believes in you.",
    "Hydrate later. Concentrate now.",
    "Phone down? We won't tell.",
    "This is your throne. Rule it."
)

@Composable
fun TimerScreen(start: Long, onDone: () -> Unit, onCancel: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(start) { while (true) { now = System.currentTimeMillis(); delay(250) } }
    val elapsed = if (start > 0) (now - start) / 1000 else 0L
    val quote = QUOTES[((elapsed / 8) % QUOTES.size).toInt()]
    val cs = MaterialTheme.colorScheme
    val brush = accentBrush()
    val spin = rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "angle")
    val breathe by spin.animateFloat(0.92f, 1f, infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Throne timer", style = MaterialTheme.typography.titleLarge)
        Text("Take your time, champ.", color = cs.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(280.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val s = 14.dp.toPx()
                val r = Size(size.width - s, size.height - s) * breathe
                val tl = Offset((size.width - r.width) / 2, (size.height - r.height) / 2)
                drawArc(cs.onSurface.copy(alpha = 0.07f), 0f, 360f, false, tl, r, style = Stroke(s))
                drawArc(brush, angle - 90f, 110f, false, tl, r, style = Stroke(s, cap = StrokeCap.Round))
                drawArc(brush, angle + 90f, 40f, false, tl, r, alpha = 0.5f, style = Stroke(s, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PulsingEmoji("🚽", 44.sp)
                Text(formatClock(elapsed), fontSize = 56.sp, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.height(24.dp))
        AnimatedContent(quote, transitionSpec = { fadeIn(tween(500)).togetherWith(fadeOut(tween(300))) }, label = "quote") {
            Text("“$it”", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, color = cs.onSurfaceVariant)
        }
        Spacer(Modifier.weight(1f))
        GradientButton("Done — log it 💩", Modifier.fillMaxWidth(), onClick = onDone)
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel timer") }
    }
}
