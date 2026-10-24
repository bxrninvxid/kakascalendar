import Foundation

enum Tone { case good, neutral, warn }

struct Insight: Identifiable {
    let id = UUID()
    let emoji: String
    let title: String
    let body: String
    var tone: Tone = .neutral
}

private func hourLabel(_ h: Int) -> String {
    switch h {
    case 0: return "midnight"
    case 1..<12: return "\(h) AM"
    case 12: return "noon"
    default: return "\(h - 12) PM"
    }
}

/// Rule-based "smart" insights. Friendly nudges, not medical advice.
func insights(_ all: [Entry], water: [Date: Int] = [:], waterGoal: Int = 8) -> [Insight] {
    if all.count < 5 {
        return [Insight(emoji: "🧠", title: "Insights are warming up",
                        body: "Log \(5 - all.count) more to unlock personal patterns and predictions.")]
    }
    let cal = Calendar.current
    let today = cal.startOfDay(for: .now)
    var out: [Insight] = []
    let twoWeeksAgo = cal.date(byAdding: .day, value: -13, to: today)!
    let recent = all.filter { $0.timestamp >= twoWeeksAgo }

    if let last = all.map(\.day).max() {
        let gap = cal.dateComponents([.day], from: last, to: today).day ?? 0
        if gap >= 3 {
            out.append(Insight(emoji: "🕵️", title: "\(gap) days without a log",
                               body: "Everything moving okay? If you just forgot, you can backdate entries.", tone: .warn))
        }
    }

    if recent.count >= 4 {
        let hard = recent.filter { $0.type <= 2 }.count * 100 / recent.count
        let loose = recent.filter { $0.type >= 6 }.count * 100 / recent.count
        let ideal = recent.filter { (3...5).contains($0.type) }.count * 100 / recent.count
        if hard >= 40 {
            out.append(Insight(emoji: "💧", title: "Running on the hard side",
                               body: "\(hard)% of the last two weeks were Type 1–2. More water, fiber and movement usually help.", tone: .warn))
        } else if loose >= 40 {
            out.append(Insight(emoji: "🩺", title: "Lots of loose ones lately",
                               body: "\(loose)% were Type 6–7. If it lasts more than a few days or comes with pain or fever, check with a doctor.", tone: .warn))
        } else if ideal >= 70 {
            out.append(Insight(emoji: "🏆", title: "Textbook consistency",
                               body: "\(ideal)% of recent entries landed in the healthy Type 3–5 zone. Elite stuff.", tone: .good))
        }
    }

    // Hydration
    let week = (0..<7).map { water[cal.date(byAdding: .day, value: -$0, to: today)!] ?? 0 }
    if week.contains(where: { $0 > 0 }) {
        let avg = Double(week.reduce(0, +)) / 7
        let hardRecent = recent.filter { $0.type <= 2 }.count
        if avg < Double(waterGoal) * 0.6 && hardRecent >= 2 {
            out.append(Insight(emoji: "🚰", title: "Drink up, legend",
                               body: "You're averaging \(String(format: "%.1f", avg)) glasses a day and had \(hardRecent) hard ones lately. Water is the cheapest fix.", tone: .warn))
        } else if avg >= Double(waterGoal) {
            out.append(Insight(emoji: "💧", title: "Hydration hero",
                               body: "\(String(format: "%.1f", avg)) glasses a day this week. Your gut sends its regards.", tone: .good))
        }
    }

    // Wipes
    let wiped = recent.filter { $0.wipes > 0 }
    if wiped.count >= 3 {
        let avg = avgOf(wiped.map(\.wipes))
        if avg <= 2 {
            out.append(Insight(emoji: "👻", title: "Clean machine", body: "Averaging \(String(format: "%.1f", avg)) wipes. Textbook formation.", tone: .good))
        } else if avg >= 7 {
            out.append(Insight(emoji: "🧻", title: "Paper heavy lately",
                               body: "Averaging \(String(format: "%.1f", avg)) wipes per session. More fiber usually means fewer wipes. A bidet doesn't hurt either.", tone: .warn))
        }
    }

    // Places
    let placed = all.compactMap { Place.of($0.place) }
    if placed.count >= 5 {
        let work = placed.filter { $0 == .work }.count * 100 / placed.count
        if work >= 30 {
            out.append(Insight(emoji: "💼", title: "Getting paid for it", body: "\(work)% of your sessions happen at work. Efficient time management."))
        }
    }

    // Perfect run
    let lastFive = all.sorted { $0.timestamp > $1.timestamp }.prefix(5)
    if lastFive.count == 5 && lastFive.allSatisfy({ $0.type == 4 }) {
        out.append(Insight(emoji: "🍌", title: "Five perfect in a row", body: "Whatever you're eating, keep eating it.", tone: .good))
    }

    // Routine
    let latest = Array(all.sorted { $0.timestamp > $1.timestamp }.prefix(60))
    let hours = latest.map { Double($0.hour) }
    let mean = hours.reduce(0, +) / Double(hours.count)
    let sd = sqrt(hours.map { ($0 - mean) * ($0 - mean) }.reduce(0, +) / Double(hours.count))
    let typical = Dictionary(grouping: latest, by: \.hour).max { $0.value.count < $1.value.count }?.key ?? Int(mean)
    if sd < 2.5 {
        out.append(Insight(emoji: "⏰", title: "Clockwork", body: "You usually go around \(hourLabel(typical)). Your gut runs on a schedule.", tone: .good))
    } else {
        out.append(Insight(emoji: "🎲", title: "Free spirit", body: "Your timing varies a lot. Most common hour: \(hourLabel(typical))."))
    }
    if !daysOf(all).contains(today) {
        out.append(Insight(emoji: "🔮", title: "Prediction", body: "Based on your history, today's visit is most likely around \(hourLabel(typical))."))
    }

    // Week over week
    let weekAgo = cal.date(byAdding: .day, value: -6, to: today)!
    let thisWeek = all.filter { $0.timestamp >= weekAgo }.count
    let lastWeek = all.filter { $0.timestamp >= twoWeeksAgo && $0.timestamp < weekAgo }.count
    if lastWeek > 0 && abs(thisWeek - lastWeek) >= 2 {
        let diff = thisWeek - lastWeek
        out.append(Insight(emoji: diff > 0 ? "📈" : "📉", title: "Week over week",
                           body: "\(thisWeek) this week vs \(lastWeek) last week (\(diff > 0 ? "+" : "")\(diff))."))
    }

    // Coffee
    let coffee = all.filter { $0.tags.contains(coffeeTag) }
    if coffee.count >= 3 {
        let morning = coffee.filter { (5...11).contains($0.hour) }.count * 100 / coffee.count
        if morning >= 60 {
            out.append(Insight(emoji: "☕", title: "The coffee effect is real", body: "\(morning)% of your coffee-tagged visits happen before noon."))
        }
    }

    // Session length
    let timed = all.filter { $0.durationSec > 0 }
    if timed.count >= 3 {
        let avg = Int(avgOf(timed.map(\.durationSec)))
        if avg >= 600 {
            out.append(Insight(emoji: "📱", title: "Phone down, champ",
                               body: "Average session is \(formatDuration(avg)). Long sits aren't great for your veins down there.", tone: .warn))
        } else if avg <= 240 {
            out.append(Insight(emoji: "⚡", title: "In and out", body: "Average session \(formatDuration(avg)). Efficient. Respect.", tone: .good))
        }
    }

    // Busiest weekday
    let byDow = Dictionary(grouping: all) { cal.component(.weekday, from: $0.timestamp) }
    if let (dow, list) = byDow.max(by: { $0.value.count < $1.value.count }), list.count >= 3 {
        out.append(Insight(emoji: "📆", title: "Busiest day",
                           body: "\(cal.weekdaySymbols[dow - 1])s are your most productive. \(list.count) visits and counting."))
    }

    // Feelings
    let rated = recent.filter { $0.rating > 0 }
    if rated.count >= 3 {
        let avg = avgOf(rated.map(\.rating))
        if avg >= 4 {
            out.append(Insight(emoji: "🤩", title: "Feeling great", body: "Recent sessions average \(String(format: "%.1f", avg))/5. Living the dream.", tone: .good))
        } else if avg <= 2.2 {
            out.append(Insight(emoji: "😣", title: "Not much fun lately",
                               body: "Recent sessions average \(String(format: "%.1f", avg))/5. Hydration and fiber are cheap upgrades.", tone: .warn))
        }
    }
    return out
}
