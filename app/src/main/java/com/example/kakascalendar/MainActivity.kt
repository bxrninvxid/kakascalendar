package com.example.kakascalendar

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { KakascalendarTheme { App() } }
    }
}

private val cardShape = RoundedCornerShape(24.dp)
private val timeFmt = DateTimeFormatter.ofPattern("h:mm a")

// ───────────────────────── App shell ─────────────────────────

@Composable
fun App(vm: MainVm = viewModel()) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    val list = entries.orEmpty()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var unlocked by remember { mutableStateOf<Set<String>?>(null) }
    val cs = MaterialTheme.colorScheme

    // Celebrate newly unlocked achievements
    LaunchedEffect(entries) {
        val cur = entries ?: return@LaunchedEffect
        val now = achievements(cur).filter { it.unlocked }.map { it.title }.toSet()
        val prev = unlocked
        unlocked = now
        if (prev != null) (now - prev).firstOrNull()?.let {
            snack.currentSnackbarData?.dismiss()
            snack.showSnackbar("🏆 Achievement unlocked: $it")
        }
    }

    val onDelete: (Entry) -> Unit = { e ->
        vm.delete(e)
        scope.launch {
            snack.currentSnackbarData?.dismiss()
            if (snack.showSnackbar("Entry deleted", "Undo") == SnackbarResult.ActionPerformed) vm.restore(e)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        floatingActionButton = {
            if (tab != 3) ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                containerColor = cs.primary, contentColor = cs.onPrimary,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Log", fontWeight = FontWeight.SemiBold) }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = cs.surfaceVariant) {
                listOf("🏠" to "Home", "📅" to "Calendar", "📊" to "Stats", "🏆" to "Awards")
                    .forEachIndexed { i, (emoji, label) ->
                        NavigationBarItem(
                            selected = tab == i, onClick = { tab = i },
                            icon = { Text(emoji, fontSize = 22.sp) }, label = { Text(label) }
                        )
                    }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).statusBarsPadding()) {
            Header(tab)
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> HomeScreen(list, onDelete)
                    1 -> CalendarScreen(list, onDelete)
                    2 -> StatsScreen(list)
                    else -> AchievementsScreen(list)
                }
            }
        }
    }

    if (showAdd) AddSheet(
        onDismiss = { showAdd = false },
        onSave = { type, comment, tags, time -> vm.add(type, comment, tags, time); showAdd = false }
    )
}

@Composable
fun Header(tab: Int) {
    val cs = MaterialTheme.colorScheme
    val sub = when (tab) {
        0 -> when (LocalTime.now().hour) {
            in 5..11 -> "Good morning ☀️"
            in 12..17 -> "Good afternoon 🌤️"
            in 18..22 -> "Good evening 🌙"
            else -> "Up late? 🦉"
        }
        1 -> "Your month at a glance"
        2 -> "Insights & trends"
        else -> "Your trophy shelf"
    }
    Row(Modifier.padding(20.dp, 16.dp, 20.dp, 0.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(cs.primary, cs.secondary))),
            contentAlignment = Alignment.Center
        ) { Text("💩", fontSize = 24.sp) }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("Kakascalendar", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold, color = cs.primary)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}

// ───────────────────────── Home ─────────────────────────

@Composable
fun HomeScreen(entries: List<Entry>, onDelete: (Entry) -> Unit) {
    val days = remember(entries) { daysOf(entries) }
    val today = LocalDate.now()
    val streak = streakDates(days).size
    val best = longestStreak(days)
    val thisWeek = entries.count { !it.date().isBefore(today.minusDays(6)) }
    val todayCount = entries.count { it.date() == today }
    val grouped = remember(entries) { entries.take(40).groupBy { it.date() } }
    val cs = MaterialTheme.colorScheme

    LazyColumn(
        contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Hero(streak, best, days, today) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Today", "$todayCount", Modifier.weight(1f))
                StatCard("This week", "$thisWeek", Modifier.weight(1f))
                StatCard("Total", "${entries.size}", Modifier.weight(1f))
            }
        }
        if (entries.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🚽", fontSize = 56.sp)
                Text("Nothing logged yet", fontWeight = FontWeight.SemiBold)
                Text("Tap Log to add your first one", color = cs.onSurfaceVariant)
            }
        }
        grouped.forEach { (date, dayList) ->
            item(key = "h$date") {
                Text(dayLabel(date), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                    color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
            }
            items(dayList, key = { it.id }) { EntryCard(it, onDelete) }
        }
    }
}

@Composable
fun Hero(streak: Int, best: Int, days: Set<LocalDate>, today: LocalDate) {
    val cs = MaterialTheme.colorScheme
    val shown by animateIntAsState(streak, tween(900), label = "streak")
    val onP = cs.onPrimary
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(cs.primary, cs.secondary)))
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🔥", fontSize = 56.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("$shown", fontSize = 48.sp, lineHeight = 52.sp, fontWeight = FontWeight.ExtraBold, color = onP)
                    Text("day streak", color = onP.copy(alpha = 0.85f))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("BEST", style = MaterialTheme.typography.labelSmall, color = onP.copy(alpha = 0.7f))
                    Text("$best days", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = onP)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                when {
                    streak == 0 -> "Log today to start a streak"
                    today !in days -> "Log today to keep it going!"
                    else -> "Nice, you're on a roll!"
                },
                color = onP.copy(alpha = 0.9f)
            )
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                (6 downTo 0).forEach { off ->
                    val d = today.minusDays(off.toLong())
                    WeekDot(d, d in days, d == today)
                }
            }
        }
    }
}

@Composable
private fun WeekDot(date: LocalDate, logged: Boolean, isToday: Boolean) {
    val cs = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(date.dayOfWeek.getDisplayName(JTextStyle.NARROW, Locale.getDefault()),
            fontSize = 12.sp, color = cs.onPrimary.copy(alpha = 0.8f))
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier.size(34.dp).clip(CircleShape)
                .background(if (logged) cs.onPrimary else cs.onPrimary.copy(alpha = 0.18f))
                .then(if (isToday && !logged) Modifier.border(2.dp, cs.onPrimary, CircleShape) else Modifier),
            contentAlignment = Alignment.Center
        ) { if (logged) Text("✓", color = cs.primary, fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier, valueSize: TextUnit = 24.sp) {
    Card(modifier, shape = cardShape, colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = valueSize, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryCard(e: Entry, onDelete: (Entry) -> Unit) {
    val t = StoolType.of(e.type)
    val cs = MaterialTheme.colorScheme
    Card(shape = cardShape, colors = CardDefaults.cardColors(cs.surfaceVariant)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(t.color.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                Text(t.emoji, fontSize = 24.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Type ${t.n} · ${t.label}", fontWeight = FontWeight.SemiBold)
                Text(e.dateTime().format(timeFmt), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                val tags = e.tagList()
                if (tags.isNotEmpty()) FlowRow(
                    Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) { tags.forEach { TagPill(it) } }
                if (e.comment.isNotBlank()) Text(e.comment, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 6.dp))
            }
            IconButton(onClick = { onDelete(e) }) {
                Icon(Icons.Default.Delete, "Delete", tint = cs.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TagPill(text: String) {
    val cs = MaterialTheme.colorScheme
    Text(
        text, fontSize = 11.sp, color = cs.onPrimaryContainer,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(cs.primaryContainer).padding(8.dp, 3.dp)
    )
}

// ───────────────────────── Calendar ─────────────────────────

@Composable
fun CalendarScreen(entries: List<Entry>, onDelete: (Entry) -> Unit) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf(LocalDate.now()) }
    val byDay = remember(entries) { entries.groupBy { it.date() } }
    val streakDays = remember(entries) { streakDates(byDay.keys) }
    val today = LocalDate.now()
    val cs = MaterialTheme.colorScheme
    val monthCount = entries.count { YearMonth.from(it.date()) == month }

    LazyColumn(
        contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(shape = cardShape, colors = CardDefaults.cardColors(cs.surfaceVariant)) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton({ month = month.minusMonths(1) }) { Text("‹", fontSize = 28.sp) }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${month.month.getDisplayName(JTextStyle.FULL, Locale.getDefault())} ${month.year}",
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("$monthCount entries", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                        }
                        TextButton({ month = month.plusMonths(1) }) { Text("›", fontSize = 28.sp) }
                    }
                    Row {
                        listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                            Text(it, Modifier.weight(1f), textAlign = TextAlign.Center,
                                color = cs.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    val offset = month.atDay(1).dayOfWeek.value % 7
                    val len = month.lengthOfMonth()
                    repeat((offset + len + 6) / 7) { r ->
                        Row {
                            repeat(7) { c ->
                                val n = r * 7 + c - offset + 1
                                if (n in 1..len) {
                                    val date = month.atDay(n)
                                    DayCell(n, byDay[date]?.size ?: 0, date in streakDays,
                                        date == selected, date == today) { selected = date }
                                } else Spacer(Modifier.weight(1f).aspectRatio(1f))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Legend(cs.secondary, "Current streak"); Spacer(Modifier.width(16.dp))
                        Legend(cs.primary, "Logged")
                    }
                }
            }
        }
        val day = byDay[selected].orEmpty()
        item {
            Text("${dayLabel(selected)} · ${day.size} ${if (day.size == 1) "entry" else "entries"}",
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
        }
        items(day, key = { it.id }) { EntryCard(it, onDelete) }
    }
}

@Composable
private fun RowScope.DayCell(day: Int, count: Int, inStreak: Boolean, selected: Boolean, today: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val bg = when { count == 0 -> Color.Transparent; inStreak -> cs.secondary; else -> cs.primary }
    val border = when {
        selected -> Modifier.border(2.dp, cs.onSurface, CircleShape)
        today -> Modifier.border(1.dp, cs.primary, CircleShape)
        else -> Modifier
    }
    Box(
        Modifier.weight(1f).aspectRatio(1f).padding(3.dp).clip(CircleShape).background(bg)
            .then(border).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text("$day", fontSize = 14.sp, color = if (count > 0) cs.onPrimary else cs.onSurface)
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

// ───────────────────────── Stats ─────────────────────────

@Composable
fun StatsScreen(entries: List<Entry>) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val week = remember(entries) { lastDaysCounts(entries, 7) }
    val maxC = (week.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    val counts = remember(entries) { typeCounts(entries) }
    val total = entries.size

    LazyColumn(
        contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Avg / day", "%.1f".format(avgPerDay(entries)), Modifier.weight(1f))
                StatCard("Busiest time", busiestPeriod(entries) ?: "–", Modifier.weight(1f), valueSize = 18.sp)
                StatCard("Perfect rate", "${perfectRate(entries)}%", Modifier.weight(1f))
            }
        }
        item {
            Card(shape = cardShape, colors = CardDefaults.cardColors(cs.surfaceVariant)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Last 7 days", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth().height(140.dp), verticalAlignment = Alignment.Bottom) {
                        week.forEach { (d, c) ->
                            Column(
                                Modifier.weight(1f).fillMaxHeight(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                if (c > 0) Text("$c", style = MaterialTheme.typography.labelSmall)
                                Box(
                                    Modifier.padding(top = 4.dp).width(22.dp)
                                        .height((90f * c / maxC).dp.coerceAtLeast(4.dp))
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (c > 0) cs.primary else cs.surface)
                                )
                                Text(d.dayOfWeek.getDisplayName(JTextStyle.NARROW, Locale.getDefault()),
                                    style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
            }
        }
        item {
            Card(shape = cardShape, colors = CardDefaults.cardColors(cs.surfaceVariant)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Types", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    StoolType.entries.forEach { t ->
                        val c = counts[t.n] ?: 0
                        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(t.emoji, fontSize = 18.sp, modifier = Modifier.width(30.dp))
                            Text("T${t.n}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(30.dp))
                            Box(Modifier.weight(1f).height(10.dp).clip(CircleShape).background(cs.surface)) {
                                Box(Modifier.fillMaxHeight()
                                    .fillMaxWidth(if (total == 0) 0f else c.toFloat() / total)
                                    .clip(CircleShape).background(t.color))
                            }
                            Text("$c", Modifier.width(32.dp), textAlign = TextAlign.End,
                                style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
        item {
            OutlinedButton(
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Kakascalendar export")
                        putExtra(Intent.EXTRA_TEXT, toCsv(entries))
                    }
                    ctx.startActivity(Intent.createChooser(send, "Export data"))
                },
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)
            ) { Text("📤  Share CSV export") }
        }
    }
}

// ───────────────────────── Achievements ─────────────────────────

@Composable
fun AchievementsScreen(entries: List<Entry>) {
    val all = remember(entries) { achievements(entries) }
    val cs = MaterialTheme.colorScheme
    val done = all.count { it.unlocked }
    LazyColumn(
        contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(shape = cardShape, colors = CardDefaults.cardColors(cs.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("$done of ${all.size} unlocked", fontWeight = FontWeight.Bold, color = cs.onPrimaryContainer)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { done.toFloat() / all.size },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                        color = cs.primary, trackColor = cs.surface
                    )
                }
            }
        }
        items(all, key = { it.title }) { a ->
            Card(Modifier.alpha(if (a.unlocked) 1f else 0.6f), shape = cardShape,
                colors = CardDefaults.cardColors(if (a.unlocked) cs.primaryContainer else cs.surfaceVariant)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp).clip(CircleShape).background(cs.surface), contentAlignment = Alignment.Center) {
                        Text(if (a.unlocked) a.emoji else "🔒", fontSize = 26.sp)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.title, fontWeight = FontWeight.SemiBold)
                        Text(a.desc, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { minOf(a.progress, a.goal).toFloat() / a.goal },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                            color = cs.primary, trackColor = cs.surface
                        )
                        Text("${minOf(a.progress, a.goal)} / ${a.goal}", style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
    }
}

// ───────────────────────── Add entry ─────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddSheet(onDismiss: () -> Unit, onSave: (Int, String, List<String>, Long) -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var type by remember { mutableIntStateOf(4) }
    var comment by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf(setOf<String>()) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var time by remember { mutableStateOf(LocalTime.now().withSecond(0).withNano(0)) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = cs.surface
    ) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Log a poop 💩", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

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
                    onClick = {
                        TimePickerDialog(ctx, { _, h, m -> time = LocalTime.of(h, m) }, time.hour, time.minute, false).show()
                    },
                    label = { Text("🕒  ${time.format(timeFmt)}") }
                )
            }

            // Type tiles
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StoolType.entries.forEach { t ->
                    val sel = t.n == type
                    val shape = RoundedCornerShape(18.dp)
                    Column(
                        Modifier.clip(shape)
                            .background(if (sel) t.color.copy(alpha = 0.25f) else cs.surfaceVariant)
                            .then(if (sel) Modifier.border(2.dp, t.color, shape) else Modifier)
                            .clickable { type = t.n }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(t.emoji, fontSize = 28.sp)
                        Text("${t.n}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
            val sel = StoolType.of(type)
            Column {
                Text("Type ${sel.n} · ${sel.label}", fontWeight = FontWeight.SemiBold)
                Text(sel.desc, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }

            // Tags
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
                value = comment, onValueChange = { comment = it },
                label = { Text("Comment (optional)") },
                modifier = Modifier.fillMaxWidth(), minLines = 2, shape = RoundedCornerShape(16.dp)
            )
            Button(
                onClick = {
                    val ts = LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        .coerceAtMost(System.currentTimeMillis())
                    onSave(type, comment, TAGS.filter { it in tags }, ts)
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) { Text("Save", fontSize = 16.sp) }
        }
    }
}
