import Foundation

enum Tier: String {
    case bronze = "Bronze", silver = "Silver", gold = "Gold", legend = "Legendary"
}

struct Achievement: Identifiable {
    let id: String
    let emoji: String
    let title: String
    let desc: String
    let progress: Int
    let goal: Int
    var xp: Int = 50
    var tier: Tier = .bronze

    var unlocked: Bool { progress >= goal }
    var fraction: Double { min(Double(progress) / Double(goal), 1) }
}

func achievements(_ e: [Entry], friends: Int = 0, waterGoalDays: Int = 0) -> [Achievement] {
    let days = daysOf(e)
    let best = longestStreak(days)
    let kinds = Set(e.map(\.type)).count
    let maxPerDay = Dictionary(grouping: e, by: \.day).values.map(\.count).max() ?? 0
    let cal = Calendar.current
    let weekendDays = days.filter { cal.isDateInWeekend($0) }.count
    func tagged(_ t: String) -> Int { e.filter { $0.tags.contains(t) }.count }
    let places = Set(e.map(\.place).filter { !$0.isEmpty }).count
    return [
        Achievement(id: "first", emoji: "🎉", title: "First Drop", desc: "Log your very first entry", progress: e.count, goal: 1, xp: 25),
        Achievement(id: "streak3", emoji: "🔥", title: "Warming Up", desc: "Reach a 3-day streak", progress: best, goal: 3),
        Achievement(id: "streak7", emoji: "📅", title: "Week Warrior", desc: "Reach a 7-day streak", progress: best, goal: 7, xp: 100, tier: .silver),
        Achievement(id: "streak14", emoji: "🏃", title: "Fortnight Fighter", desc: "Reach a 14-day streak", progress: best, goal: 14, xp: 150, tier: .silver),
        Achievement(id: "streak30", emoji: "👑", title: "Streak Royalty", desc: "Reach a 30-day streak", progress: best, goal: 30, xp: 300, tier: .gold),
        Achievement(id: "streak100", emoji: "🦸", title: "Stronger. Smarter. Better.", desc: "Reach a 100-day streak", progress: best, goal: 100, xp: 1000, tier: .legend),
        Achievement(id: "total10", emoji: "📈", title: "Regular", desc: "Log 10 entries", progress: e.count, goal: 10),
        Achievement(id: "total50", emoji: "🏅", title: "Veteran", desc: "Log 50 entries", progress: e.count, goal: 50, xp: 150, tier: .silver),
        Achievement(id: "total100", emoji: "💯", title: "Centurion", desc: "Log 100 entries", progress: e.count, goal: 100, xp: 300, tier: .gold),
        Achievement(id: "total500", emoji: "🗿", title: "Living Legend", desc: "Log 500 entries", progress: e.count, goal: 500, xp: 1000, tier: .legend),
        Achievement(id: "variety4", emoji: "🌈", title: "Variety Pack", desc: "Log 4 different types", progress: kinds, goal: 4),
        Achievement(id: "variety7", emoji: "🌟", title: "Full Spectrum", desc: "Log all 7 types", progress: kinds, goal: 7, xp: 150, tier: .silver),
        Achievement(id: "perfect5", emoji: "🥇", title: "Gold Standard", desc: "Log 5 perfect (Type 4) entries", progress: e.filter { $0.type == 4 }.count, goal: 5, xp: 75),
        Achievement(id: "perfectrun7", emoji: "🍌", title: "Perfect Seven", desc: "7 Type 4s in a row", progress: longestTypeRun(e, type: 4), goal: 7, xp: 200, tier: .gold),
        Achievement(id: "early", emoji: "🐦", title: "Early Bird", desc: "Log 3 entries before 7 AM", progress: e.filter { $0.hour < 7 }.count, goal: 3),
        Achievement(id: "night", emoji: "🦉", title: "Night Owl", desc: "Log 3 entries after 11 PM", progress: e.filter { $0.hour >= 23 }.count, goal: 3),
        Achievement(id: "coffee", emoji: "☕", title: "Coffee Effect", desc: "Tag 3 entries with \(coffeeTag)", progress: tagged(coffeeTag), goal: 3),
        Achievement(id: "story", emoji: "✍️", title: "Storyteller", desc: "Add a comment to 5 entries", progress: e.filter { !$0.comment.isEmpty }.count, goal: 5),
        Achievement(id: "critic", emoji: "🧐", title: "Critic", desc: "Rate 10 entries", progress: e.filter { $0.rating > 0 }.count, goal: 10, xp: 75, tier: .silver),
        Achievement(id: "speed", emoji: "⚡", title: "Speedrunner", desc: "5 timed sessions under 2 minutes", progress: e.filter { (1..<120).contains($0.durationSec) }.count, goal: 5, xp: 75, tier: .silver),
        Achievement(id: "marathon", emoji: "📚", title: "Marathoner", desc: "One timed session of 15+ minutes", progress: e.contains { $0.durationSec >= 900 } ? 1 : 0, goal: 1),
        Achievement(id: "triple", emoji: "🎯", title: "Hat Trick", desc: "3 entries in a single day", progress: maxPerDay, goal: 3, xp: 75, tier: .silver),
        Achievement(id: "weekend", emoji: "🏖️", title: "Weekend Warrior", desc: "Log on 8 weekend days", progress: weekendDays, goal: 8, xp: 100, tier: .silver),
        Achievement(id: "ghost", emoji: "👻", title: "Ghost Wipe", desc: "5 entries with a single wipe", progress: e.filter { $0.wipes == 1 }.count, goal: 5, xp: 100, tier: .silver),
        Achievement(id: "paper", emoji: "🧻", title: "Paper Trail", desc: "Count wipes on 20 entries", progress: e.filter { $0.wipes > 0 }.count, goal: 20, xp: 75),
        Achievement(id: "shower", emoji: "🚿", title: "Fresh Prince", desc: "Shower after 5 sessions", progress: tagged(showerTag), goal: 5),
        Achievement(id: "bidet", emoji: "💦", title: "Bidet Baron", desc: "Use a bidet 10 times", progress: tagged(bidetTag), goal: 10, xp: 100, tier: .silver),
        Achievement(id: "clog", emoji: "🪠", title: "Plumber's Nightmare", desc: "Survive a clog", progress: tagged(clogTag), goal: 1),
        Achievement(id: "paid", emoji: "💼", title: "Paid to Poop", desc: "10 sessions at work. Boss makes a dollar…", progress: e.filter { $0.place == Place.work.rawValue }.count, goal: 10, xp: 150, tier: .gold),
        Achievement(id: "globe", emoji: "🌍", title: "Globetrotter", desc: "Go in 4 different places", progress: places, goal: 4, xp: 100, tier: .silver),
        Achievement(id: "nature", emoji: "🌲", title: "One With Nature", desc: "Answer the call in nature", progress: e.filter { $0.place == Place.nature.rawValue }.count, goal: 1, xp: 75, tier: .silver),
        Achievement(id: "hydro3", emoji: "💧", title: "Hydrated", desc: "Hit your water goal on 3 days", progress: waterGoalDays, goal: 3),
        Achievement(id: "hydro14", emoji: "🌊", title: "Hydro Homie", desc: "Hit your water goal on 14 days", progress: waterGoalDays, goal: 14, xp: 200, tier: .gold),
        Achievement(id: "squad1", emoji: "🤝", title: "Squad Up", desc: "Add your first friend", progress: friends, goal: 1, xp: 75),
        Achievement(id: "squad5", emoji: "📣", title: "Influencer", desc: "Have 5 friends in your squad", progress: friends, goal: 5, xp: 200, tier: .gold)
    ]
}

struct LevelInfo {
    let level: Int
    let title: String
    let xp: Int
    let into: Int
    let need: Int
    var fraction: Double { need == 0 ? 1 : Double(into) / Double(need) }
}

func xpFor(_ e: [Entry], _ ach: [Achievement]) -> Int {
    e.reduce(0) { sum, x in
        sum + 10 + (x.comment.isEmpty ? 0 : 3) + (x.rating > 0 ? 2 : 0) + (x.tags.isEmpty ? 0 : 2)
    } + ach.filter(\.unlocked).reduce(0) { $0 + $1.xp }
}

private func xpToNext(_ level: Int) -> Int { 100 + (level - 1) * 60 }

func levelFor(_ xp: Int) -> LevelInfo {
    var level = 1, rem = xp
    while rem >= xpToNext(level) { rem -= xpToNext(level); level += 1 }
    return LevelInfo(level: level, title: levelTitle(level), xp: xp, into: rem, need: xpToNext(level))
}

func levelTitle(_ level: Int) -> String {
    switch level {
    case 30...: return "The Homelander of Hygiene"
    case 25...: return "Porcelain God"
    case 20...: return "Supreme Flusher"
    case 16...: return "Throne Lord"
    case 12...: return "Porcelain Knight"
    case 8...: return "Flush Master"
    case 5...: return "Regular Regular"
    case 3...: return "Bowl Apprentice"
    default: return "Toilet Trainee"
    }
}
