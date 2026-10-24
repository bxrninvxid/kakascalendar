package com.example.kakascalendar.domain

import com.example.kakascalendar.data.Entry
import java.time.DayOfWeek

data class Achievement(
    val id: String,
    val emoji: String,
    val title: String,
    val desc: String,
    val progress: Int,
    val goal: Int,
    val xp: Int = 50,
    val tier: Tier = Tier.BRONZE
) {
    val unlocked get() = progress >= goal
    val fraction get() = (progress.coerceAtMost(goal).toFloat() / goal).coerceIn(0f, 1f)
}

enum class Tier(val label: String) { BRONZE("Bronze"), SILVER("Silver"), GOLD("Gold"), LEGEND("Legendary") }

fun achievements(e: List<Entry>, friends: Int = 0): List<Achievement> {
    val days = daysOf(e)
    val best = longestStreak(days)
    val kinds = e.map { it.type }.toSet().size
    val maxPerDay = e.groupingBy { it.date() }.eachCount().values.maxOrNull() ?: 0
    val weekendDays = days.count { it.dayOfWeek == DayOfWeek.SATURDAY || it.dayOfWeek == DayOfWeek.SUNDAY }
    val B = Tier.BRONZE; val S = Tier.SILVER; val G = Tier.GOLD; val L = Tier.LEGEND
    return listOf(
        Achievement("first", "🎉", "First Drop", "Log your very first entry", e.size, 1, 25, B),
        Achievement("streak3", "🔥", "Warming Up", "Reach a 3-day streak", best, 3, 50, B),
        Achievement("streak7", "📅", "Week Warrior", "Reach a 7-day streak", best, 7, 100, S),
        Achievement("streak14", "🏃", "Fortnight Fighter", "Reach a 14-day streak", best, 14, 150, S),
        Achievement("streak30", "👑", "Streak Royalty", "Reach a 30-day streak", best, 30, 300, G),
        Achievement("streak100", "🦸", "Stronger. Smarter. Better.", "Reach a 100-day streak", best, 100, 1000, L),
        Achievement("total10", "📈", "Regular", "Log 10 entries", e.size, 10, 50, B),
        Achievement("total50", "🏅", "Veteran", "Log 50 entries", e.size, 50, 150, S),
        Achievement("total100", "💯", "Centurion", "Log 100 entries", e.size, 100, 300, G),
        Achievement("total500", "🗿", "Living Legend", "Log 500 entries", e.size, 500, 1000, L),
        Achievement("variety4", "🌈", "Variety Pack", "Log 4 different types", kinds, 4, 50, B),
        Achievement("variety7", "🌟", "Full Spectrum", "Log all 7 types", kinds, 7, 150, S),
        Achievement("perfect5", "🥇", "Gold Standard", "Log 5 perfect (Type 4) entries", e.count { it.type == 4 }, 5, 75, B),
        Achievement("perfectrun7", "🍌", "Perfect Seven", "7 Type 4s in a row", longestTypeRun(e, 4), 7, 200, G),
        Achievement("early", "🐦", "Early Bird", "Log 3 entries before 7 AM", e.count { it.hour() < 7 }, 3, 50, B),
        Achievement("night", "🦉", "Night Owl", "Log 3 entries after 11 PM", e.count { it.hour() >= 23 }, 3, 50, B),
        Achievement("coffee", "☕", "Coffee Effect", "Tag 3 entries with $COFFEE", e.count { COFFEE in it.tagList() }, 3, 50, B),
        Achievement("story", "✍️", "Storyteller", "Add a comment to 5 entries", e.count { it.comment.isNotBlank() }, 5, 50, B),
        Achievement("critic", "🧐", "Critic", "Rate 10 entries", e.count { it.rating > 0 }, 10, 75, S),
        Achievement("speed", "⚡", "Speedrunner", "5 timed sessions under 2 minutes",
            e.count { it.durationSec in 1 until 120 }, 5, 75, S),
        Achievement("marathon", "📚", "Marathoner", "One timed session of 15+ minutes",
            if (e.any { it.durationSec >= 15 * 60 }) 1 else 0, 1, 50, B),
        Achievement("triple", "🎯", "Hat Trick", "3 entries in a single day", maxPerDay, 3, 75, S),
        Achievement("weekend", "🏖️", "Weekend Warrior", "Log on 8 weekend days", weekendDays, 8, 100, S),
        Achievement("squad1", "🤝", "Squad Up", "Add your first friend", friends, 1, 75, B),
        Achievement("squad5", "📣", "Influencer", "Have 5 friends in your squad", friends, 5, 200, G)
    )
}

data class LevelInfo(val level: Int, val title: String, val xp: Int, val into: Int, val need: Int) {
    val fraction get() = if (need == 0) 1f else into.toFloat() / need
}

fun xpFor(e: List<Entry>, ach: List<Achievement>): Int =
    e.sumOf { 10 + (if (it.comment.isNotBlank()) 3 else 0) + (if (it.rating > 0) 2 else 0) + (if (it.tags.isNotBlank()) 2 else 0) } +
        ach.filter { it.unlocked }.sumOf { it.xp }

private fun xpToNext(level: Int) = 100 + (level - 1) * 60

fun levelFor(xp: Int): LevelInfo {
    var level = 1
    var rem = xp
    while (rem >= xpToNext(level)) {
        rem -= xpToNext(level)
        level++
    }
    return LevelInfo(level, levelTitle(level), xp, rem, xpToNext(level))
}

fun levelTitle(level: Int): String = when {
    level >= 30 -> "The Homelander of Hygiene"
    level >= 25 -> "Porcelain God"
    level >= 20 -> "Supreme Flusher"
    level >= 16 -> "Throne Lord"
    level >= 12 -> "Porcelain Knight"
    level >= 8 -> "Flush Master"
    level >= 5 -> "Regular Regular"
    level >= 3 -> "Bowl Apprentice"
    else -> "Toilet Trainee"
}
