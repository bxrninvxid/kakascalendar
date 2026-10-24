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

fun achievements(e: List<Entry>, friends: Int = 0, waterGoalDays: Int = 0): List<Achievement> {
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
        Achievement("ghost", "👻", "Ghost Wipe", "5 entries with a single wipe", e.count { it.wipes == 1 }, 5, 100, Tier.SILVER),
        Achievement("paper", "🧻", "Paper Trail", "Count wipes on 20 entries", e.count { it.wipes > 0 }, 20, 75, Tier.BRONZE),
        Achievement("shower", "🚿", "Fresh Prince", "Shower after 5 sessions", e.count { SHOWER in it.tagList() }, 5, 50, Tier.BRONZE),
        Achievement("bidet", "💦", "Bidet Baron", "Use a bidet 10 times", e.count { BIDET in it.tagList() }, 10, 100, Tier.SILVER),
        Achievement("clog", "🪠", "Plumber's Nightmare", "Survive a clog", e.count { CLOG in it.tagList() }, 1, 50, Tier.BRONZE),
        Achievement("paid", "💼", "Paid to Poop", "10 sessions at work. Boss makes a dollar…", e.count { it.place == Place.WORK.key }, 10, 150, Tier.GOLD),
        Achievement("globe", "🌍", "Globetrotter", "Go in 4 different places", e.map { it.place }.filter { it.isNotEmpty() }.toSet().size, 4, 100, Tier.SILVER),
        Achievement("nature", "🌲", "One With Nature", "Answer the call in nature", e.count { it.place == Place.NATURE.key }, 1, 75, Tier.SILVER),
        Achievement("hydro3", "💧", "Hydrated", "Hit your water goal on 3 days", waterGoalDays, 3, 50, Tier.BRONZE),
        Achievement("hydro14", "🌊", "Hydro Homie", "Hit your water goal on 14 days", waterGoalDays, 14, 200, Tier.GOLD),
        Achievement("squad1", "🤝", "Squad Up", "Add your first friend", friends, 1, 75, B),
        Achievement("squad5", "📣", "Influencer", "Have 5 friends in your squad", friends, 5, 200, G)
    )
}

data class LevelInfo(val level: Int, val title: String, val emoji: String, val xp: Int, val into: Int, val need: Int) {
    val fraction get() = if (need == 0) 1f else into.toFloat() / need
}

/** One rung of the ladder: title, emoji and the total XP needed to reach it. */
data class Rank(val title: String, val emoji: String, val minXp: Int)

/**
 * The rank ladder. A normal day (1 log + a rating or tag) is worth ~18 XP, so:
 * ~1 week → Bowl Apprentice, ~1 month → Regular Regular, ~6 months → Lord of the Rolls,
 * ~1 year (with trophies) → Emperor of Evacuation. The top takes real dedication.
 */
val RANKS = listOf(
    Rank("Toilet Trainee", "🧷", 0),
    Rank("Potty Rookie", "🐣", 60),
    Rank("Bowl Apprentice", "🥣", 150),
    Rank("Flush Cadet", "🎖️", 300),
    Rank("Seat Warmer", "🔥", 500),
    Rank("Regular Regular", "📅", 750),
    Rank("Paper Squire", "🧻", 1050),
    Rank("Porcelain Scout", "🧭", 1400),
    Rank("Flush Master", "🌀", 1800),
    Rank("Throne Knight", "🛡️", 2300),
    Rank("Plumbing Baron", "🔧", 2900),
    Rank("Bidet Duke", "💦", 3600),
    Rank("Lord of the Rolls", "📜", 4400),
    Rank("Porcelain Prince", "🤴", 5300),
    Rank("Throne King", "👑", 6300),
    Rank("Sultan of Squat", "🕌", 7500),
    Rank("Emperor of Evacuation", "🏛️", 9000),
    Rank("Porcelain God", "⚡", 11000),
    Rank("Legend of the Loo", "🌟", 13500),
    Rank("The Homelander of Hygiene", "🦸", 16500)
)
private const val PRESTIGE_STEP = 3000

/** Logs beyond this per day still count for stats, but earn no XP. */
const val XP_LOGS_PER_DAY = 3

/**
 * XP rules (anti-spam):
 * - first 3 logs of a day: 10 XP each, +2 for a rating, +2 for a comment, +1 for tags
 * - +5 for every day you log at all (consistency beats volume)
 * - plus the XP of every unlocked trophy
 */
fun xpFor(e: List<Entry>, ach: List<Achievement>): Int {
    val byDay = e.groupBy { it.date() }
    val logXp = byDay.values.sumOf { day ->
        5 + day.sortedBy { it.timestamp }.take(XP_LOGS_PER_DAY).sumOf {
            10 + (if (it.rating > 0) 2 else 0) + (if (it.comment.isNotBlank()) 2 else 0) + (if (it.tags.isNotBlank()) 1 else 0)
        }
    }
    return logXp + ach.filter { it.unlocked }.sumOf { it.xp }
}

fun levelFor(xp: Int): LevelInfo {
    val idx = RANKS.indexOfLast { xp >= it.minXp }.coerceAtLeast(0)
    val rank = RANKS[idx]
    if (idx < RANKS.lastIndex) {
        val next = RANKS[idx + 1]
        return LevelInfo(idx + 1, rank.title, rank.emoji, xp, xp - rank.minXp, next.minXp - rank.minXp)
    }
    // Prestige: every 3000 XP past the top adds a star
    val over = xp - rank.minXp
    val stars = over / PRESTIGE_STEP
    val title = if (stars == 0) rank.title else "${rank.title} ${"★".repeat(stars.coerceAtMost(5))}${if (stars > 5) "+$stars" else ""}"
    return LevelInfo(RANKS.size + stars, title, rank.emoji, xp, over % PRESTIGE_STEP, PRESTIGE_STEP)
}

fun levelTitle(level: Int): String = RANKS.getOrNull(level - 1)?.title ?: RANKS.last().title
