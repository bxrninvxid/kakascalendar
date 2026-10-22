package com.example.kakascalendar

import androidx.compose.ui.graphics.Color
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Bristol stool scale */
enum class StoolType(val n: Int, val emoji: String, val label: String, val desc: String, val color: Color) {
    T1(1, "🌰", "Rabbit pellets", "Separate hard lumps", Color(0xFFB85C38)),
    T2(2, "🥜", "Lumpy", "Sausage-shaped but lumpy", Color(0xFFD9A441)),
    T3(3, "🌽", "Cracked", "Sausage with cracks on the surface", Color(0xFF9CB84F)),
    T4(4, "🍌", "Perfect", "Smooth, soft sausage or snake", Color(0xFF4CAF7A)),
    T5(5, "☁️", "Soft blobs", "Soft blobs with clear-cut edges", Color(0xFF9CB84F)),
    T6(6, "🍮", "Mushy", "Fluffy pieces with ragged edges", Color(0xFFD9A441)),
    T7(7, "💧", "Liquid", "Watery, no solid pieces", Color(0xFF5B9BD5));

    companion object {
        fun of(n: Int) = entries.first { it.n == n }
    }
}

const val COFFEE = "☕ Coffee"
val TAGS = listOf("😌 Easy", "😣 Strained", "⚡ Urgent", "📖 Long sit", "🌶️ Spicy", COFFEE)

private val zone: ZoneId get() = ZoneId.systemDefault()

fun Entry.dateTime() = Instant.ofEpochMilli(timestamp).atZone(zone)
fun Entry.date(): LocalDate = dateTime().toLocalDate()
fun Entry.tagList(): List<String> = tags.split(",").filter { it.isNotBlank() }

fun daysOf(entries: List<Entry>): Set<LocalDate> = entries.map { it.date() }.toSet()

fun dayLabel(d: LocalDate, today: LocalDate = LocalDate.now()): String = when (d) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> d.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
}

/** Days in the current streak. A streak stays alive if you logged yesterday but not yet today. */
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

// ───────── Stats helpers ─────────

fun lastDaysCounts(e: List<Entry>, n: Int, today: LocalDate = LocalDate.now()): List<Pair<LocalDate, Int>> {
    val by = e.groupingBy { it.date() }.eachCount()
    return (n - 1 downTo 0).map { val d = today.minusDays(it.toLong()); d to (by[d] ?: 0) }
}

fun typeCounts(e: List<Entry>): Map<Int, Int> = e.groupingBy { it.type }.eachCount()

fun avgPerDay(e: List<Entry>, today: LocalDate = LocalDate.now()): Double {
    if (e.isEmpty()) return 0.0
    val first = e.minOf { it.date() }
    return e.size.toDouble() / (ChronoUnit.DAYS.between(first, today) + 1)
}

fun perfectRate(e: List<Entry>): Int = if (e.isEmpty()) 0 else 100 * e.count { it.type == 4 } / e.size

private fun period(h: Int) = when (h) {
    in 5..11 -> "Morning"
    in 12..16 -> "Afternoon"
    in 17..21 -> "Evening"
    else -> "Night"
}

fun busiestPeriod(e: List<Entry>): String? =
    e.groupingBy { period(it.dateTime().hour) }.eachCount().maxByOrNull { it.value }?.key

fun toCsv(e: List<Entry>): String {
    fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
    val d = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val t = DateTimeFormatter.ofPattern("HH:mm")
    return buildString {
        appendLine("date,time,type,type_name,tags,comment")
        e.sortedBy { it.timestamp }.forEach {
            val dt = it.dateTime()
            appendLine("${dt.format(d)},${dt.format(t)},${it.type},${q(StoolType.of(it.type).label)},${q(it.tagList().joinToString("; "))},${q(it.comment)}")
        }
    }
}

// ───────── Achievements ─────────

data class Achievement(
    val emoji: String,
    val title: String,
    val desc: String,
    val progress: Int,
    val goal: Int
) {
    val unlocked get() = progress >= goal
}

fun achievements(e: List<Entry>): List<Achievement> {
    val best = longestStreak(daysOf(e))
    val kinds = e.map { it.type }.toSet().size
    return listOf(
        Achievement("🎉", "First Drop", "Log your very first entry", e.size, 1),
        Achievement("🔥", "Warming Up", "Reach a 3-day streak", best, 3),
        Achievement("📅", "Week Warrior", "Reach a 7-day streak", best, 7),
        Achievement("🏃", "Fortnight Fighter", "Reach a 14-day streak", best, 14),
        Achievement("👑", "Streak Royalty", "Reach a 30-day streak", best, 30),
        Achievement("📈", "Regular", "Log 10 entries", e.size, 10),
        Achievement("🏅", "Veteran", "Log 50 entries", e.size, 50),
        Achievement("💯", "Centurion", "Log 100 entries", e.size, 100),
        Achievement("🌈", "Variety Pack", "Log 4 different types", kinds, 4),
        Achievement("🌟", "Full Spectrum", "Log all 7 types", kinds, 7),
        Achievement("🥇", "Gold Standard", "Log 5 perfect (Type 4) entries", e.count { it.type == 4 }, 5),
        Achievement("🐦", "Early Bird", "Log 3 entries before 7 AM", e.count { it.dateTime().hour < 7 }, 3),
        Achievement("🦉", "Night Owl", "Log 3 entries after 11 PM", e.count { it.dateTime().hour >= 23 }, 3),
        Achievement("☕", "Coffee Effect", "Tag 3 entries with $COFFEE", e.count { COFFEE in it.tagList() }, 3),
        Achievement("✍️", "Storyteller", "Add a comment to 5 entries", e.count { it.comment.isNotBlank() }, 5)
    )
}
