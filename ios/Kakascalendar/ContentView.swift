import SwiftUI
import SwiftData

/// Everything the screens need, computed from the stored data.
struct Dash {
    var entries: [Entry] = []
    var days: Set<Date> = []
    var streakDays: Set<Date> = []
    var streak = 0
    var best = 0
    var todayCount = 0
    var weekCount = 0
    var achievements: [Achievement] = []
    var level = levelFor(0)
    var insights: [Insight] = []
    var water: [Date: Int] = [:]
    var waterGoal = 8

    var loggedToday: Bool { days.contains(Calendar.current.startOfDay(for: .now)) }
    var waterToday: Int { water[Calendar.current.startOfDay(for: .now)] ?? 0 }

    init() {}

    init(entries e: [Entry], water rows: [WaterDay], friends: Int, waterGoal: Int) {
        let cal = Calendar.current
        let today = cal.startOfDay(for: .now)
        entries = e
        days = daysOf(e)
        streakDays = Kakascalendar.streakDays(days)
        streak = streakDays.count
        best = longestStreak(days)
        todayCount = e.filter { $0.day == today }.count
        let weekAgo = cal.date(byAdding: .day, value: -6, to: today)!
        weekCount = e.filter { $0.timestamp >= weekAgo }.count
        var w: [Date: Int] = [:]
        for r in rows { if let d = isoDay.date(from: r.date) { w[cal.startOfDay(for: d)] = r.glasses } }
        water = w
        self.waterGoal = waterGoal
        achievements = Kakascalendar.achievements(e, friends: friends, waterGoalDays: w.values.filter { $0 >= waterGoal }.count)
        level = levelFor(xpFor(e, achievements))
        insights = Kakascalendar.insights(e, water: w, waterGoal: waterGoal)
    }
}

enum AppTab: Hashable { case home, calendar, stats, squad }

struct ContentView: View {
    @Environment(AppSettings.self) private var settings
    @Environment(SocialStore.self) private var social
    @Environment(UIEvents.self) private var ui
    @Environment(\.modelContext) private var ctx
    @Query(sort: \Entry.timestamp, order: .reverse) private var entries: [Entry]
    @Query private var waterRows: [WaterDay]

    @State var tab: AppTab = .home
    @State var addSheet: AddSheetState?
    @State var showProfile = false
    @State var showTimer = false

    var body: some View {
        let dash = Dash(entries: entries, water: waterRows, friends: settings.friendsCount, waterGoal: settings.waterGoal)
        TabView(selection: $tab) {
            Tab("Home", systemImage: "house.fill", value: AppTab.home) {
                HomeView(dash: dash, onEdit: edit, onDelete: delete, onProfile: { showProfile = true },
                         onTimer: startTimer, onQuickLog: quickLog, onInsights: { tab = .stats })
            }
            Tab("Calendar", systemImage: "calendar", value: AppTab.calendar) {
                CalendarView(dash: dash, onEdit: edit, onDelete: delete)
            }
            Tab("Stats", systemImage: "chart.bar.xaxis", value: AppTab.stats) {
                StatsView(dash: dash)
            }
            Tab("Squad", systemImage: "person.3.fill", value: AppTab.squad) {
                SquadView(dash: dash)
            }
        }
        .tabBarMinimizeBehavior(.onScrollDown)
        .tabViewBottomAccessory {
            HStack {
                if settings.timerStart > 0 {
                    Button { showTimer = true } label: { TimerChipLabel(start: settings.timerStart) }
                        .buttonStyle(.plain)
                } else {
                    Button { addSheet = AddSheetState(entry: nil, duration: 0) } label: {
                        Label("Log a poop 💩", systemImage: "plus.circle.fill").font(.headline).frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .sheet(item: $addSheet) { s in AddEntryView(initial: s.entry, prefillDuration: s.duration, onSave: save) }
        .sheet(isPresented: $showProfile) { ProfileView(dash: dash) }
        .fullScreenCover(isPresented: $showTimer) {
            TimerView(start: settings.timerStart, onDone: finishTimer, onCancel: {
                settings.timerStart = 0
                showTimer = false
            })
        }
        .overlay(alignment: .bottom) { ToastView().padding(.bottom, 120) }
        .overlay(alignment: .top) { BannerView() }
        .overlay { ConfettiView(trigger: ui.confetti) }
        .sensoryFeedback(.success, trigger: ui.haptic) { _, _ in settings.haptics }
        .background(AppBackground())
        .task { await social.refresh() }
        .onChange(of: progressSignature(dash), initial: true) { detectProgress(dash) }
        .onChange(of: statsSignature(dash)) {
            guard settings.signedIn else { return }
            Task {
                await social.pushStats(streak: dash.streak, best: dash.best, total: dash.entries.count,
                                       week: dash.weekCount, xp: dash.level.xp, level: dash.level.level)
            }
        }
        .onChange(of: dash.loggedToday, initial: true) { Reminders.reschedule(settings: settings, loggedDays: dash.days) }
    }

    // MARK: actions

    private func edit(_ e: Entry) { addSheet = AddSheetState(entry: e, duration: 0) }

    /// How many logs already exist on that calendar day (optionally ignoring one entry being edited).
    private func countOnDay(_ date: Date, excluding: Entry? = nil) -> Int {
        let day = Calendar.current.startOfDay(for: date)
        return entries.filter { $0.day == day && $0 !== excluding }.count
    }

    private func overCap(_ date: Date, excluding: Entry? = nil) -> Bool {
        guard countOnDay(date, excluding: excluding) >= maxLogsPerDay else { return false }
        ui.show("🚫 That day already has \(maxLogsPerDay) logs. Even legends have limits.")
        return true
    }

    @discardableResult
    private func save(_ draft: EntryDraft, _ existing: Entry?) -> Bool {
        if overCap(draft.timestamp, excluding: existing) { return false }
        if let e = existing {
            draft.apply(to: e)
        } else {
            let e = Entry()
            draft.apply(to: e)
            ctx.insert(e)
            if Date.now.timeIntervalSince(e.timestamp) < 6 * 3600 {
                Task { await social.postEvent("log", ["type": "\(e.type)"]) }
            }
        }
        try? ctx.save()
        if settings.haptics { ui.haptic += 1 }
        return true
    }

    private func delete(_ e: Entry) {
        let copy = EntryDraft(e)
        ctx.delete(e)
        try? ctx.save()
        ui.show("Entry deleted", actionTitle: "Undo") {
            let n = Entry()
            copy.apply(to: n)
            ctx.insert(n)
            try? ctx.save()
        }
    }

    private func quickLog() {
        if overCap(.now) { return }
        ctx.insert(Entry(timestamp: .now, type: 4))
        try? ctx.save()
        Task { await social.postEvent("log", ["type": "4"]) }
        if settings.haptics { ui.haptic += 1 }
        ui.show("🍌 Quick-logged a perfect Type 4")
    }

    private func startTimer() {
        if settings.timerStart == 0 { settings.timerStart = Date.now.timeIntervalSince1970 }
        showTimer = true
    }

    private func finishTimer() {
        let sec = settings.timerStart > 0 ? Int(Date.now.timeIntervalSince1970 - settings.timerStart) : 0
        settings.timerStart = 0
        showTimer = false
        addSheet = AddSheetState(entry: nil, duration: min(max(sec, 1), 3 * 3600))
    }

    // MARK: achievements & levels

    private func progressSignature(_ d: Dash) -> String {
        d.achievements.filter(\.unlocked).map(\.id).joined(separator: ",") + "|\(d.level.level)"
    }

    private func statsSignature(_ d: Dash) -> String {
        "\(d.streak)-\(d.best)-\(d.entries.count)-\(d.weekCount)-\(d.level.xp)"
    }

    private func detectProgress(_ d: Dash) {
        let unlocked = d.achievements.filter(\.unlocked)
        if !settings.achievementsInit {
            settings.seenAchievements = unlocked.map(\.id)
            settings.lastLevel = d.level.level
            settings.achievementsInit = true
            settings.ranksV2 = true
            return
        }
        if !settings.ranksV2 {
            // new rank ladder: re-baseline quietly instead of firing a fake level-up
            settings.lastLevel = d.level.level
            settings.ranksV2 = true
        }
        let fresh = unlocked.filter { !settings.seenAchievements.contains($0.id) }
        if !fresh.isEmpty {
            settings.seenAchievements += fresh.map(\.id)
            if let a = fresh.first {
                ui.celebrate(Banner(emoji: a.emoji, title: "Achievement unlocked!", subtitle: "\(a.title) · +\(a.xp) XP"))
            }
            for a in fresh { Task { await social.postEvent("achievement", ["title": a.title, "emoji": a.emoji]) } }
        }
        if d.level.level > settings.lastLevel {
            let first = settings.lastLevel == 0
            settings.lastLevel = d.level.level
            if !first {
                ui.celebrate(Banner(emoji: d.level.emoji, title: "Rank up! Level \(d.level.level)", subtitle: "You are now a \(d.level.title)"))
                Task { await social.postEvent("level", ["level": "\(d.level.level)", "title": d.level.title]) }
            }
        }
    }
}

struct AddSheetState: Identifiable {
    let id = UUID()
    let entry: Entry?
    let duration: Int
}

// MARK: - Overlays

struct ToastView: View {
    @Environment(UIEvents.self) private var ui
    var body: some View {
        Group {
            if let t = ui.toast {
                HStack(spacing: 14) {
                    Text(t.message).font(.subheadline)
                    if let title = t.actionTitle {
                        Button(title) { t.action?(); ui.toast = nil }.fontWeight(.bold)
                    }
                }
                .padding(.horizontal, 18).padding(.vertical, 12)
                .glassEffect(.regular, in: .capsule)
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .animation(.snappy, value: ui.toast?.id)
    }
}

struct BannerView: View {
    @Environment(UIEvents.self) private var ui
    @Environment(AppSettings.self) private var settings
    var body: some View {
        Group {
            if let b = ui.banner {
                HStack(spacing: 12) {
                    Text(b.emoji).font(.largeTitle)
                        .frame(width: 52, height: 52)
                        .background(.white.opacity(0.25), in: .circle)
                    VStack(alignment: .leading) {
                        Text(b.title).font(.headline)
                        Text(b.subtitle).font(.subheadline).opacity(0.9)
                    }
                    Spacer()
                }
                .foregroundStyle(.white)
                .padding(14)
                .background(LinearGradient(colors: deepened(palette(settings).colors), startPoint: .leading, endPoint: .trailing),
                            in: .rect(cornerRadius: 24))
                .padding(.horizontal)
                .transition(.move(edge: .top).combined(with: .opacity))
            }
        }
        .animation(.spring(duration: 0.5), value: ui.banner)
    }
}

struct TimerChipLabel: View {
    let start: Double
    var body: some View {
        TimelineView(.periodic(from: .now, by: 1)) { ctx in
            let sec = Int(ctx.date.timeIntervalSince1970 - start)
            Label("On the throne · \(formatClock(max(sec, 0)))", systemImage: "timer")
                .font(.headline).frame(maxWidth: .infinity)
        }
    }
}
