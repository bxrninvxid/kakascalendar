package com.example.kakascalendar.domain

import com.example.kakascalendar.data.Entry
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class Tone { GOOD, NEUTRAL, WARN }

data class Insight(val emoji: String, val title: String, val body: String, val tone: Tone = Tone.NEUTRAL)

private fun hourLabel(h: Int): String = when {
    h == 0 -> "midnight"
    h < 12 -> "$h AM"
    h == 12 -> "noon"
    else -> "${h - 12} PM"
}

/**
 * Rule-based "smart" insights. Friendly nudges, not medical advice.
 * Ordered roughly by importance.
 */
fun insights(all: List<Entry>, today: LocalDate = LocalDate.now()): List<Insight> {
    if (all.size < 5) {
        return listOf(
            Insight("🧠", "Insights are warming up", "Log ${5 - all.size} more to unlock personal patterns and predictions.")
        )
    }
    val out = mutableListOf<Insight>()
    val recent = all.filter { !it.date().isBefore(today.minusDays(13)) }
    val last = all.maxOf { it.date() }
    val gap = java.time.temporal.ChronoUnit.DAYS.between(last, today)

    // Gap warning
    if (gap >= 3) {
        out += Insight("🕵️", "$gap days without a log", "Everything moving okay? If you just forgot, you can backdate entries.", Tone.WARN)
    }

    // Consistency of stool type
    if (recent.size >= 4) {
        val hard = recent.count { it.type <= 2 } * 100 / recent.size
        val loose = recent.count { it.type >= 6 } * 100 / recent.size
        val ideal = recent.count { it.type in 3..5 } * 100 / recent.size
        when {
            hard >= 40 -> out += Insight("💧", "Running on the hard side",
                "$hard% of the last two weeks were Type 1–2. More water, fiber and movement usually help.", Tone.WARN)
            loose >= 40 -> out += Insight("🩺", "Lots of loose ones lately",
                "$loose% were Type 6–7. If it lasts more than a few days or comes with pain or fever, check with a doctor.", Tone.WARN)
            ideal >= 70 -> out += Insight("🏆", "Textbook consistency",
                "$ideal% of recent entries landed in the healthy Type 3–5 zone. Elite stuff.", Tone.GOOD)
        }
    }

    // Perfect run
    val lastFive = all.sortedByDescending { it.timestamp }.take(5)
    if (lastFive.size == 5 && lastFive.all { it.type == 4 }) {
        out += Insight("🍌", "Five perfect in a row", "Whatever you're eating, keep eating it.", Tone.GOOD)
    }

    // Routine: typical hour and how regular it is
    val latest = all.sortedByDescending { it.timestamp }.take(60)
    val hours = latest.map { it.hour().toDouble() }
    val mean = hours.average()
    val sd = sqrt(hours.map { (it - mean) * (it - mean) }.average())
    val typical = latest.groupingBy { it.hour() }.eachCount().maxByOrNull { it.value }?.key ?: mean.roundToInt()
    out += if (sd < 2.5) {
        Insight("⏰", "Clockwork", "You usually go around ${hourLabel(typical)}. Your gut runs on a schedule.", Tone.GOOD)
    } else {
        Insight("🎲", "Free spirit", "Your timing varies a lot. Most common hour: ${hourLabel(typical)}.")
    }

    // Prediction for today
    if (today !in daysOf(all)) {
        out += Insight("🔮", "Prediction", "Based on your history, today's visit is most likely around ${hourLabel(typical)}.")
    }

    // Week-over-week trend
    val thisWeek = all.count { !it.date().isBefore(today.minusDays(6)) }
    val lastWeek = all.count { val d = it.date(); !d.isBefore(today.minusDays(13)) && d.isBefore(today.minusDays(6)) }
    if (lastWeek > 0) {
        val diff = thisWeek - lastWeek
        if (abs(diff) >= 2) {
            out += Insight(if (diff > 0) "📈" else "📉", "Week over week",
                "$thisWeek this week vs $lastWeek last week (${if (diff > 0) "+" else ""}$diff).")
        }
    }

    // Coffee effect
    val coffee = all.filter { COFFEE in it.tagList() }
    if (coffee.size >= 3) {
        val coffeeMorning = coffee.count { it.hour() in 5..11 } * 100 / coffee.size
        if (coffeeMorning >= 60) {
            out += Insight("☕", "The coffee effect is real", "$coffeeMorning% of your coffee-tagged visits happen before noon.")
        }
    }

    // Phone / long sits
    val timed = all.filter { it.durationSec > 0 }
    if (timed.size >= 3) {
        val avg = timed.map { it.durationSec }.average().toInt()
        if (avg >= 10 * 60) {
            out += Insight("📱", "Phone down, champ",
                "Average session is ${formatDuration(avg)}. Long sits aren't great for your veins down there.", Tone.WARN)
        } else if (avg <= 4 * 60) {
            out += Insight("⚡", "In and out", "Average session ${formatDuration(avg)}. Efficient. Respect.", Tone.GOOD)
        }
    }

    // Favourite weekday
    val byDow = all.groupingBy { it.date().dayOfWeek }.eachCount()
    byDow.maxByOrNull { it.value }?.let { (dow, n) ->
        if (n >= 3) out += Insight("📆", "Busiest day",
            "${dow.getDisplayName(TextStyle.FULL, Locale.getDefault())}s are your most productive. $n visits and counting.")
    }

    // Feelings
    val rated = recent.filter { it.rating > 0 }
    if (rated.size >= 3) {
        val avg = rated.map { it.rating }.average()
        if (avg >= 4) out += Insight("🤩", "Feeling great", "Recent sessions average ${"%.1f".format(avg)}/5. Living the dream.", Tone.GOOD)
        else if (avg <= 2.2) out += Insight("😣", "Not much fun lately",
            "Recent sessions average ${"%.1f".format(avg)}/5. Hydration and fiber are cheap upgrades.", Tone.WARN)
    }

    return out
}
