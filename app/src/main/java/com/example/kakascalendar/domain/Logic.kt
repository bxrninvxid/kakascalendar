package com.example.kakascalendar.domain

import androidx.compose.ui.graphics.Color
import com.example.kakascalendar.data.Entry
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Bristol stool scale */
enum class StoolType(val n: Int, val emoji: String, val label: String, val desc: String, val color: Color) {
    T1(1, "🌰", "Rabbit pellets", "Separate hard lumps", Color(0xFFB85C38)),
    T2(2, "🥜", "Lumpy", "Sausage-shaped but lumpy", Color(0xFFD9A441)),
    T3(3, "🌽", "Cracked", "Sausage with cracks on the surface", Color(0xFF9CB84F)),
    T4(4, "🍌", "Perfect", "Smooth, soft sausage or snake", Color(0xFF3FAF7A)),
    T5(5, "☁️", "Soft blobs", "Soft blobs with clear-cut edges", Color(0xFF8DB86A)),
    T6(6, "🍮", "Mushy", "Fluffy pieces with ragged edges", Color(0xFFE0A85A)),
    T7(7, "💧", "Liquid", "Watery, no solid pieces", Color(0xFF4F8FD6));

    companion object {
        fun of(n: Int) = entries.firstOrNull { it.n == n } ?: T4
    }
}

const val COFFEE = "☕ Coffee"
val TAGS = listOf(
    "😌 Easy", "😣 Strained", "⚡ Urgent", "📖 Long sit", "📱 Phone",
    "🌶️ Spicy", COFFEE, "🍺 Night out", "💊 Meds", "✈️ Travel"
)
val RATINGS = listOf("😖", "😕", "😐", "🙂", "🤩")
val RATING_LABELS = listOf("Rough", "Meh", "Okay", "Good", "Legendary")
val AVATARS = listOf("💩", "🦸", "🦊", "🐻", "🐼", "🐸", "🦄", "🐙", "👑", "🤖", "👽", "🦁", "🐯", "🐧", "🦖", "🐵")
val REACTIONS = listOf("💩", "🔥", "👑", "😂", "👏")

// ───────── Entry helpers ─────────

fun zone(): ZoneId = ZoneId.systemDefault()
fun Entry.dateTime(): ZonedDateTime = Instant.ofEpochMilli(timestamp).atZone(zone())
fun Entry.date(): LocalDate = dateTime().toLocalDate()
fun Entry.hour(): Int = dateTime().hour
fun Entry.tagList(): List<String> = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
fun Entry.stool() = StoolType.of(type)

fun daysOf(entries: List<Entry>): Set<LocalDate> = entries.map { it.date() }.toSet()

fun dayLabel(d: LocalDate, today: LocalDate = LocalDate.now()): String = when (d) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> d.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
}

fun formatDuration(sec: Int): String = when {
    sec <= 0 -> "–"
    sec < 60 -> "${sec}s"
    else -> "${sec / 60}m ${(sec % 60).toString().padStart(2, '0')}s"
}

fun formatClock(sec: Long): String = "%d:%02d".format(sec / 60, sec % 60)

fun timeAgo(epochMs: Long, now: Long = System.currentTimeMillis()): String {
    val m = (now - epochMs) / 60_000
    return when {
        m < 1 -> "just now"
        m < 60 -> "${m}m ago"
        m < 60 * 24 -> "${m / 60}h ago"
        m < 60 * 24 * 7 -> "${m / 1440}d ago"
        else -> Instant.ofEpochMilli(epochMs).atZone(zone()).format(DateTimeFormatter.ofPattern("MMM d"))
    }
}

// ───────── Streaks ─────────

/** Days in the current streak. It stays alive if you logged yesterday but not yet today. */
fun streakDates(days: Set<LocalDate>, today: LocalDate = LocalDate.now()): Set<LocalDate> {
    var d = if (today in days) today else today.minusDays(1)
    val out = mutableSetOf<LocalDate>()
    while (d in days) { out += d; d = d.minusDays(1) }
    return out
}

fun longestStreak(days: Set<LocalDate>): Int {
    var best = 0
    var run = 0
    var prev: LocalDate? = null
    for (d in days.sorted()) {
        run = if (prev != null && prev.plusDays(1) == d) run + 1 else 1
        best = maxOf(best, run)
        prev = d
    }
    return best
}

/** Longest run of consecutive entries (by time) with the given type. */
fun longestTypeRun(entries: List<Entry>, type: Int): Int {
    var best = 0
    var run = 0
    for (e in entries.sortedBy { it.timestamp }) {
        run = if (e.type == type) run + 1 else 0
        best = maxOf(best, run)
    }
    return best
}

// ───────── Stats ─────────

enum class Period(val label: String, val days: Int?) {
    WEEK("7D", 7), MONTH("30D", 30), QUARTER("90D", 90), ALL("All", null)
}

fun List<Entry>.inPeriod(p: Period, today: LocalDate = LocalDate.now()): List<Entry> =
    if (p.days == null) this else filter { !it.date().isBefore(today.minusDays(p.days - 1L)) }

data class Bar(val label: String, val value: Int)

fun activityBars(e: List<Entry>, p: Period, today: LocalDate = LocalDate.now()): List<Bar> {
    val byDay = e.groupingBy { it.date() }.eachCount()
    return when (p) {
        Period.WEEK -> (6 downTo 0).map { off ->
            val d = today.minusDays(off.toLong())
            Bar(d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()), byDay[d] ?: 0)
        }
        Period.MONTH -> (29 downTo 0).map { off ->
            val d = today.minusDays(off.toLong())
            Bar(if (off % 7 == 0) d.dayOfMonth.toString() else "", byDay[d] ?: 0)
        }
        Period.QUARTER -> (12 downTo 0).map { w ->
            val end = today.minusDays(7L * w)
            val start = end.minusDays(6)
            val n = byDay.filterKeys { !it.isBefore(start) && !it.isAfter(end) }.values.sum()
            Bar(if (w % 4 == 0) start.format(DateTimeFormatter.ofPattern("d MMM")) else "", n)
        }
        Period.ALL -> (11 downTo 0).map { m ->
            val ym = YearMonth.from(today).minusMonths(m.toLong())
            val n = e.count { YearMonth.from(it.date()) == ym }
            Bar(ym.month.getDisplayName(TextStyle.NARROW, Locale.getDefault()), n)
        }
    }
}

fun hourBars(e: List<Entry>): List<Bar> {
    val by = e.groupingBy { it.hour() }.eachCount()
    return (0..23).map { h -> Bar(if (h % 6 == 0) "$h" else "", by[h] ?: 0) }
}

fun weekdayBars(e: List<Entry>): List<Bar> {
    val by = e.groupingBy { it.date().dayOfWeek }.eachCount()
    return DayOfWeek.values().map { Bar(it.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2), by[it] ?: 0) }
}

fun typeCounts(e: List<Entry>): Map<Int, Int> = e.groupingBy { it.type }.eachCount()

fun avgPerDay(e: List<Entry>, p: Period, today: LocalDate = LocalDate.now()): Double {
    if (e.isEmpty()) return 0.0
    val span = p.days?.toLong() ?: (ChronoUnit.DAYS.between(e.minOf { it.date() }, today) + 1)
    return e.size.toDouble() / span.coerceAtLeast(1)
}

fun perfectRate(e: List<Entry>): Int = if (e.isEmpty()) 0 else 100 * e.count { it.type == 4 } / e.size

fun avgDuration(e: List<Entry>): Int = e.filter { it.durationSec > 0 }.map { it.durationSec }.average()
    .let { if (it.isNaN()) 0 else it.toInt() }

fun avgRating(e: List<Entry>): Double = e.filter { it.rating > 0 }.map { it.rating }.average()
    .let { if (it.isNaN()) 0.0 else it }

fun heatCounts(e: List<Entry>): Map<LocalDate, Int> = e.groupingBy { it.date() }.eachCount()

fun toCsv(e: List<Entry>): String {
    fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
    val d = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val t = DateTimeFormatter.ofPattern("HH:mm")
    return buildString {
        appendLine("date,time,type,type_name,rating,duration_sec,tags,comment")
        e.sortedBy { it.timestamp }.forEach {
            val dt = it.dateTime()
            appendLine(
                "${dt.format(d)},${dt.format(t)},${it.type},${q(it.stool().label)},${it.rating}," +
                    "${it.durationSec},${q(it.tagList().joinToString("; "))},${q(it.comment)}"
            )
        }
    }
}

// ───────── Backup ─────────

@Serializable
data class BackupEntry(
    val timestamp: Long,
    val type: Int,
    val comment: String = "",
    val tags: String = "",
    val durationSec: Int = 0,
    val rating: Int = 0
)

@Serializable
data class Backup(val app: String = "Kakascalendar", val version: Int = 2, val entries: List<BackupEntry>)

fun Entry.toBackup() = BackupEntry(timestamp, type, comment, tags, durationSec, rating)
fun BackupEntry.toEntry() = Entry(
    timestamp = timestamp, type = type.coerceIn(1, 7), comment = comment, tags = tags,
    durationSec = durationSec.coerceAtLeast(0), rating = rating.coerceIn(0, 5)
)
