import SwiftUI
import SwiftData

struct HomeView: View {
    @Environment(AppSettings.self) private var settings
    let dash: Dash
    var onEdit: (Entry) -> Void
    var onDelete: (Entry) -> Void
    var onProfile: () -> Void
    var onTimer: () -> Void
    var onQuickLog: () -> Void
    var onInsights: () -> Void

    var body: some View {
        let grouped = Dictionary(grouping: dash.entries.prefix(40), by: \.day).sorted { $0.key > $1.key }
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: 14) {
                    HeroCard(dash: dash)
                    if settings.shows(.quick) {
                        HStack(spacing: 12) {
                            QuickAction(emoji: "🚽", title: "Throne timer", subtitle: "Time your session", action: onTimer)
                            QuickAction(emoji: "⚡", title: "Quick log", subtitle: "One-tap perfect 🍌", action: onQuickLog)
                        }
                    }
                    if settings.shows(.water) { WaterCard(glasses: dash.waterToday, goal: dash.waterGoal) }
                    if settings.shows(.stats) {
                        HStack(spacing: 12) {
                            StatTile(label: "Today", value: "\(dash.todayCount)", emoji: "☀️")
                            StatTile(label: "This week", value: "\(dash.weekCount)", emoji: "📆")
                            StatTile(label: "Total", value: "\(dash.entries.count)", emoji: "💩")
                        }
                    }
                    if settings.shows(.insight), let ins = dash.insights.first {
                        Button(action: onInsights) { InsightCard(insight: ins, chevron: true) }.buttonStyle(.plain)
                    }
                    if dash.entries.isEmpty {
                        EmptyState(emoji: "🚽", title: "Your throne awaits",
                                   message: "Tap “Log a poop” to add your first visit.\nGreatness starts with a single drop.")
                    }
                    ForEach(grouped, id: \.key) { date, list in
                        SectionTitle(text: dayLabel(date))
                        ForEach(list) { EntryRow(entry: $0, onEdit: onEdit, onDelete: onDelete) }
                    }
                }
                .padding()
            }
            .background(AppBackground())
            .navigationTitle(settings.name.isEmpty ? "Kakascalendar" : settings.name)
            .navigationSubtitle(greeting())
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(action: onProfile) { MyAvatar(size: 34) }.buttonStyle(.plain)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button(action: onProfile) {
                        Text("Lv \(dash.level.level)").font(.subheadline.weight(.heavy))
                    }
                }
            }
        }
    }
}

func greeting() -> String {
    switch Calendar.current.component(.hour, from: .now) {
    case 5...11: return "Good morning ☀️"
    case 12...17: return "Good afternoon 🌤️"
    case 18...22: return "Good evening 🌙"
    default: return "Up late? 🦉"
    }
}

struct HeroCard: View {
    @Environment(AppSettings.self) private var settings
    let dash: Dash

    var body: some View {
        let cal = Calendar.current
        let today = cal.startOfDay(for: .now)
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .center) {
                VStack(alignment: .leading, spacing: 0) {
                    HStack(alignment: .lastTextBaseline, spacing: 4) {
                        Text("🔥").font(.system(size: 44))
                        Text("\(dash.streak)")
                            .font(.system(size: 60, weight: .black))
                            .contentTransition(.numericText())
                            .animation(.smooth, value: dash.streak)
                    }
                    Text("day streak").font(.headline).opacity(0.9)
                    Text(motivation).font(.subheadline).opacity(0.9).padding(.top, 4)
                }
                Spacer()
                VStack(spacing: 8) {
                    ProgressRing(fraction: dash.loggedToday ? 1 : 0, lineWidth: 8, colors: [.white, .white], track: .white.opacity(0.25)) {
                        VStack(spacing: 0) {
                            Text(dash.loggedToday ? "✅" : "⏳").font(.title2)
                            Text(dash.loggedToday ? "Done" : "Today").font(.caption.bold())
                        }
                    }
                    .frame(width: 88, height: 88)
                    BragButton(dash: dash)
                }
            }
            HStack {
                ForEach(0..<7, id: \.self) { i in
                    let d = cal.date(byAdding: .day, value: i - 6, to: today)!
                    let logged = dash.days.contains(d)
                    VStack(spacing: 4) {
                        Text(d.formatted(.dateTime.weekday(.narrow))).font(.caption2.weight(.semibold)).opacity(0.85)
                        ZStack {
                            Circle().fill(logged ? .white : .white.opacity(0.18))
                            if logged { Image(systemName: "checkmark").font(.caption.bold()).foregroundStyle(palette(settings).primary) }
                            if i == 6 && !logged { Circle().strokeBorder(.white, lineWidth: 2) }
                        }
                        .frame(width: 34, height: 34)
                    }
                    .frame(maxWidth: .infinity)
                }
            }
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Text("Lv \(dash.level.level)").font(.headline.weight(.black))
                    Text(dash.level.title).font(.subheadline.weight(.semibold)).opacity(0.9).lineLimit(1)
                    Spacer()
                    Text("\(dash.level.into)/\(dash.level.need) XP").font(.caption.weight(.semibold)).opacity(0.85)
                }
                ProgressView(value: dash.level.fraction).tint(.white)
            }
        }
        .foregroundStyle(.white)
        .padding(20)
        .background(LinearGradient(colors: deepened(palette(settings).colors), startPoint: .topLeading, endPoint: .bottomTrailing),
                    in: .rect(cornerRadius: 32))
    }

    private var motivation: String {
        if dash.streak == 0 { return "Log today to start a streak" }
        if !dash.loggedToday { return "Log today to keep it alive!" }
        if dash.streak >= dash.best { return "Personal best. Unstoppable. 👑" }
        return "\(dash.best - dash.streak) more to beat your best (\(dash.best))"
    }
}

struct QuickAction: View {
    let emoji: String
    let title: String
    let subtitle: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                Text(emoji).font(.title2)
                    .frame(width: 42, height: 42)
                    .background(Color.accentColor.opacity(0.18), in: .rect(cornerRadius: 14))
                VStack(alignment: .leading) {
                    Text(title).font(.subheadline.bold()).lineLimit(1)
                    Text(subtitle).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                }
                Spacer(minLength: 0)
            }
            .padding(12)
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .card()
    }
}

struct WaterCard: View {
    @Environment(\.modelContext) private var ctx
    @Environment(AppSettings.self) private var settings
    @Environment(UIEvents.self) private var ui
    let glasses: Int
    let goal: Int
    private let water = Color(hex: 0x4F9DE8)

    var body: some View {
        VStack(spacing: 10) {
            HStack(spacing: 10) {
                Text(glasses >= goal ? "🌊" : "💧").font(.title2)
                    .frame(width: 42, height: 42)
                    .background(water.opacity(0.18), in: .rect(cornerRadius: 14))
                VStack(alignment: .leading) {
                    Text("Hydration").font(.subheadline.bold())
                    Text(glasses >= goal ? "Goal reached. Your gut thanks you." : "\(glasses) of \(goal) glasses today")
                        .font(.caption).foregroundStyle(.secondary)
                }
                Spacer()
                Button { change(-1) } label: { Image(systemName: "minus") }
                    .buttonStyle(.glass).disabled(glasses == 0)
                Text("\(glasses)").font(.title2.weight(.black)).frame(minWidth: 28)
                Button { change(1) } label: { Image(systemName: "plus") }
                    .buttonStyle(.glassProminent).tint(water)
            }
            HStack(spacing: 4) {
                ForEach(0..<min(goal, 16), id: \.self) { i in
                    Capsule().fill(i < glasses ? water : Color.primary.opacity(0.08)).frame(height: 8)
                }
            }
        }
        .padding(14)
        .card()
        .sensoryFeedback(.increase, trigger: glasses) { _, _ in settings.haptics }
    }

    private func change(_ delta: Int) {
        let key = dayKey(.now)
        let fd = FetchDescriptor<WaterDay>(predicate: #Predicate { $0.date == key })
        let row = (try? ctx.fetch(fd))?.first
        let next = min(max((row?.glasses ?? 0) + delta, 0), 40)
        if let row { row.glasses = next } else { ctx.insert(WaterDay(date: key, glasses: next)) }
        try? ctx.save()
        if delta > 0 && glasses < goal && next >= goal { ui.show("💧 Water goal smashed!") }
    }
}

struct InsightCard: View {
    let insight: Insight
    var chevron = false

    var body: some View {
        let accent: Color = insight.tone == .good ? Color(hex: 0x3FAF7A) : insight.tone == .warn ? Color(hex: 0xE0913A) : .accentColor
        HStack(spacing: 12) {
            Text(insight.emoji).font(.title2)
                .frame(width: 44, height: 44)
                .background(accent.opacity(0.18), in: .rect(cornerRadius: 14))
            VStack(alignment: .leading, spacing: 2) {
                Text(insight.title).font(.subheadline.bold())
                Text(insight.body).font(.caption).foregroundStyle(.secondary)
            }
            Spacer(minLength: 0)
            if chevron { Image(systemName: "chevron.right").foregroundStyle(.secondary) }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card()
    }
}
