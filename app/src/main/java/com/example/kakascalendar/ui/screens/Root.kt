package com.example.kakascalendar.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kakascalendar.MainVm
import com.example.kakascalendar.UiEvent
import com.example.kakascalendar.data.Entry
import com.example.kakascalendar.data.Prefs
import com.example.kakascalendar.domain.formatClock
import com.example.kakascalendar.ui.components.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime

private data class Banner(val emoji: String, val title: String, val subtitle: String)

@Composable
fun AppRoot(vm: MainVm, prefs: Prefs) {
    if (!prefs.onboarded) {
        Onboarding(onDone = vm::finishOnboarding)
        return
    }

    val dash by vm.dash.collectAsStateWithLifecycle()
    var route by rememberSaveable { mutableStateOf("tabs") }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Entry?>(null) }
    var prefillDuration by rememberSaveable { mutableIntStateOf(0) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var confetti by remember { mutableIntStateOf(0) }
    var banner by remember { mutableStateOf<Banner?>(null) }
    val hapticsOn by rememberUpdatedState(prefs.haptics)

    LaunchedEffect(Unit) {
        vm.events.collect { ev ->
            when (ev) {
                is UiEvent.Unlocked -> {
                    confetti++
                    if (hapticsOn) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    banner = Banner(ev.achievement.emoji, "Achievement unlocked!", "${ev.achievement.title} · +${ev.achievement.xp} XP")
                }
                is UiEvent.LevelUp -> {
                    confetti++
                    banner = Banner(ev.level.emoji, "Rank up! Level ${ev.level.level}", "You are now a ${ev.level.title}")
                }
                is UiEvent.Message -> scope.launch {
                    snack.currentSnackbarData?.dismiss()
                    snack.showSnackbar(ev.text)
                }
                is UiEvent.Deleted -> scope.launch {
                    snack.currentSnackbarData?.dismiss()
                    if (snack.showSnackbar("Entry deleted", "Undo", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) {
                        vm.restore(ev.entry)
                    }
                }
            }
        }
    }
    LaunchedEffect(banner) {
        if (banner != null) { delay(3500); banner = null }
    }

    val openAdd: (Entry?) -> Unit = { e -> editing = e; prefillDuration = 0; sheetOpen = true }

    BackHandler(enabled = route != "tabs") {
        route = if (route == "settings") "me" else "tabs"
    }

    AppBackground {
        AnimatedContent(
            targetState = route,
            transitionSpec = {
                (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 10 })
                    .togetherWith(fadeOut(tween(160)))
            },
            label = "route"
        ) { r ->
            when (r) {
                "me" -> MeScreen(vm, prefs, dash, onBack = { route = "tabs" }, onSettings = { route = "settings" })
                "settings" -> SettingsScreen(vm, prefs, dash, onBack = { route = "me" })
                "timer" -> TimerScreen(
                    start = prefs.timerStart,
                    onDone = {
                        val sec = vm.stopTimer()
                        route = "tabs"
                        editing = null; prefillDuration = sec; sheetOpen = true
                    },
                    onCancel = { vm.cancelTimer(); route = "tabs" }
                )
                else -> Box(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().statusBarsPadding()) {
                        Header(prefs, dash.level.level, tab, onAvatar = { route = "me" })
                        Box(Modifier.weight(1f)) {
                            AnimatedContent(
                                targetState = tab,
                                transitionSpec = { fadeIn(tween(200)).togetherWith(fadeOut(tween(120))) },
                                label = "tab"
                            ) { t ->
                                when (t) {
                                    0 -> HomeScreen(
                                        dash = dash,
                                        prefs = prefs,
                                        onWater = { d ->
                                            if (prefs.haptics) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            vm.addWater(d)
                                        },
                                        onShare = { vm.shareBrag(it) },
                                        onEdit = { openAdd(it) },
                                        onDelete = vm::delete,
                                        onQuickLog = { if (prefs.haptics) haptics.performHapticFeedback(HapticFeedbackType.LongPress); vm.quickLog() },
                                        onTimer = { if (prefs.timerStart == 0L) vm.startTimer(); route = "timer" },
                                        onInsights = { tab = 2 }
                                    )
                                    1 -> CalendarScreen(dash, onEdit = { openAdd(it) }, onDelete = vm::delete)
                                    2 -> StatsScreen(dash)
                                    else -> SquadScreen(vm, prefs)
                                }
                            }
                        }
                    }
                    if (prefs.timerStart > 0L) {
                        TimerChip(prefs.timerStart, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp)) {
                            route = "timer"
                        }
                    }
                    FloatingNav(
                        tab = tab,
                        onTab = { tab = it },
                        onAdd = { if (prefs.haptics) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); openAdd(null) },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }

        SnackbarHost(
            snack,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = if (route == "tabs") 92.dp else 12.dp)
        )
        AchievementBanner(banner, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp))
        Confetti(confetti)
    }

    if (sheetOpen) {
        AddEntrySheet(
            initial = editing,
            customTags = prefs.customTags,
            prefillDuration = prefillDuration,
            onDismiss = { sheetOpen = false; editing = null },
            onSave = { if (vm.save(it)) { sheetOpen = false; editing = null } }
        )
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Good morning ☀️"
    in 12..17 -> "Good afternoon 🌤️"
    in 18..22 -> "Good evening 🌙"
    else -> "Up late? 🦉"
}

@Composable
private fun Header(prefs: Prefs, level: Int, tab: Int, onAvatar: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val title = when (tab) {
        0 -> prefs.name.ifBlank { "Kakascalendar" }
        1 -> "Calendar"
        2 -> "Insights"
        else -> "Squad"
    }
    val sub = when (tab) {
        0 -> greeting()
        1 -> "Your month at a glance"
        2 -> "Stronger. Smarter. Better."
        else -> "Friends, feed & bragging rights"
    }
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        MyAvatar(prefs, size = 48.dp, ring = true, onClick = onAvatar)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(sub, style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
            AnimatedContent(title, label = "title") {
                Text(it, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
            }
        }
        Box(
            Modifier.clip(RoundedCornerShape(50)).background(accentBrush()).clickable(onClick = onAvatar)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text("Lv $level", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun FloatingNav(tab: Int, onTab: (Int) -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val items: List<Pair<ImageVector, String>> = listOf(
        Icons.Rounded.Home to "Home",
        Icons.Rounded.CalendarMonth to "Calendar",
        Icons.Rounded.Insights to "Stats",
        Icons.Rounded.Groups to "Squad"
    )
    val shape = RoundedCornerShape(32.dp)
    Row(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .fillMaxWidth()
            .height(68.dp)
            .shadow(20.dp, shape, ambientColor = cs.primary, spotColor = cs.primary)
            .clip(shape)
            .background(cs.surfaceContainerHigh)
            .border(1.dp, cs.onSurface.copy(alpha = 0.06f), shape)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { i, (icon, label) ->
            if (i == 2) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(54.dp).shadow(10.dp, CircleShape, spotColor = cs.primary).clip(CircleShape)
                            .background(accentBrush()).clickable(onClick = onAdd),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.Add, "Log", tint = Color.White, modifier = Modifier.size(30.dp)) }
                }
            }
            val selected = tab == i
            val pad by animateDpAsState(if (selected) 14.dp else 0.dp, spring(), label = "pad")
            Box(Modifier.weight(1f).fillMaxHeight().clickable { onTab(i) }, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.clip(RoundedCornerShape(50))
                            .background(if (selected) cs.primaryContainer else Color.Transparent)
                            .padding(horizontal = pad, vertical = 4.dp)
                    ) {
                        Icon(icon, label, tint = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant)
                    }
                    Text(
                        label, fontSize = 11.sp, maxLines = 1,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) cs.onSurface else cs.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun AchievementBanner(banner: Banner?, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = banner != null, modifier = modifier,
        enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()
    ) {
        var last by remember { mutableStateOf(banner) }
        if (banner != null) last = banner
        val b = last ?: return@AnimatedVisibility
        Row(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                .shadow(16.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp))
                .background(accentBrush()).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                PulsingEmoji(b.emoji, 26.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(b.title, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                Text(b.subtitle, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun TimerChip(start: Long, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(start) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val cs = MaterialTheme.colorScheme
    Row(
        modifier.shadow(12.dp, RoundedCornerShape(50)).clip(RoundedCornerShape(50)).background(cs.inverseSurface)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PulsingEmoji("🚽", 16.sp)
        Spacer(Modifier.width(8.dp))
        Text("On the throne · ${formatClock((now - start) / 1000)}", color = cs.inverseOnSurface, fontWeight = FontWeight.Bold)
    }
}
