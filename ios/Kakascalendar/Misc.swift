import SwiftUI
import UserNotifications

// MARK: - Onboarding

struct OnboardingView: View {
    @Environment(AppSettings.self) private var settings
    @State var name = ""
    @State var avatar = "💩"

    var body: some View {
        ScrollView {
            VStack(spacing: 18) {
                Text(avatar).font(.system(size: 96)).padding(.top, 40)
                Text("Kakascalendar").font(.largeTitle.weight(.black)).foregroundStyle(Color.accentColor)
                Text("The greatest bowel tracker ever built.\nStreaks. Trophies. A squad. Zero shame.")
                    .multilineTextAlignment(.center).foregroundStyle(.secondary)
                VStack(alignment: .leading, spacing: 12) {
                    Text("What should we call you?").font(.headline)
                    TextField("Champion", text: $name)
                        .padding(14).background(.background.opacity(0.5), in: .rect(cornerRadius: 16))
                    Text("Pick your avatar").font(.headline)
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 6), spacing: 10) {
                        ForEach(avatarEmojis, id: \.self) { e in
                            Button { withAnimation(.snappy) { avatar = e } } label: {
                                Text(e).font(.title).frame(width: 46, height: 46)
                                    .background(avatar == e ? Color.accentColor.opacity(0.3) : .clear, in: .circle)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    Text("Prefer your own photo? Add one later in Settings.").font(.caption).foregroundStyle(.secondary)
                }
                .padding(16)
                .card(radius: 28)
                GradientButton(title: "Let's go 🚀") {
                    settings.name = name.trimmingCharacters(in: .whitespaces).isEmpty ? "Champion" : name.trimmingCharacters(in: .whitespaces)
                    settings.avatar = avatar
                    settings.onboarded = true
                }
                Text("Everything stays on your phone unless you join a Squad.").font(.caption2).foregroundStyle(.secondary)
            }
            .padding()
        }
        .background(AppBackground())
    }
}

// MARK: - Lock

struct LockView: View {
    let unlock: () -> Void
    var body: some View {
        VStack(spacing: 16) {
            Spacer()
            Text("🔒").font(.system(size: 72))
            Text("Kakascalendar is locked").font(.title2.bold())
            Text("Your throne, your secrets.").foregroundStyle(.secondary)
            GradientButton(title: "Unlock", action: unlock).frame(maxWidth: 260).padding(.top)
            Spacer()
        }
        .padding()
        .frame(maxWidth: .infinity)
        .background(AppBackground())
        .onAppear(perform: unlock)
    }
}

// MARK: - Throne timer

private let quotes = [
    "Greatness can't be rushed.", "Breathe in. Breathe out. Let go.", "Every legend sits here eventually.",
    "Your streak believes in you.", "Hydrate later. Concentrate now.", "Phone down? We won't tell.",
    "This is your throne. Rule it."
]

struct TimerView: View {
    @Environment(AppSettings.self) private var settings
    let start: Double
    let onDone: () -> Void
    let onCancel: () -> Void

    var body: some View {
        TimelineView(.animation(minimumInterval: 0.25)) { ctx in
            let elapsed = max(Int(ctx.date.timeIntervalSince1970 - start), 0)
            let angle = ctx.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 4) / 4 * 360
            VStack(spacing: 20) {
                Text("Throne timer").font(.title2.bold()).padding(.top, 30)
                Text("Take your time, champ.").foregroundStyle(.secondary)
                Spacer()
                ZStack {
                    Circle().stroke(.primary.opacity(0.07), lineWidth: 14)
                    Circle().trim(from: 0, to: 0.3)
                        .stroke(LinearGradient(colors: palette(settings).colors, startPoint: .top, endPoint: .bottom),
                                style: StrokeStyle(lineWidth: 14, lineCap: .round))
                        .rotationEffect(.degrees(angle - 90))
                    VStack {
                        Text("🚽").font(.system(size: 44))
                        Text(formatClock(elapsed)).font(.system(size: 56, weight: .black)).monospacedDigit()
                    }
                }
                .frame(width: 280, height: 280)
                Text("“\(quotes[(elapsed / 8) % quotes.count])”")
                    .font(.headline).multilineTextAlignment(.center).foregroundStyle(.secondary)
                    .contentTransition(.opacity)
                    .animation(.easeInOut, value: elapsed / 8)
                Spacer()
                GradientButton(title: "Done — log it 💩", action: onDone)
                Button("Cancel timer", action: onCancel).padding(.bottom)
            }
            .padding()
        }
        .background(AppBackground())
    }
}

// MARK: - Brag card

struct BragCardView: View {
    let name: String
    let avatar: String
    let photo: UIImage?
    let dash: Dash
    let colors: [Color]

    var body: some View {
        VStack(spacing: 18) {
            Text("KAKASCALENDAR").font(.system(size: 22, weight: .heavy)).opacity(0.9)
            Group {
                if let photo {
                    Image(uiImage: photo).resizable().scaledToFill().frame(width: 130, height: 130).clipShape(Circle())
                } else {
                    Text(avatar).font(.system(size: 100))
                }
            }
            Text(name).font(.system(size: 40, weight: .bold))
            Text("Level \(dash.level.level) · \(dash.level.title)").font(.title3).opacity(0.9)
            Text("🔥").font(.system(size: 70)).padding(.top, 20)
            Text("\(dash.streak)").font(.system(size: 140, weight: .black))
            Text("day streak").font(.system(size: 30, weight: .semibold)).opacity(0.9)
            HStack {
                stat("👑 \(dash.best)d", "best")
                stat("💩 \(dash.entries.count)", "total")
                stat("🏆 \(dash.achievements.filter(\.unlocked).count)/\(dash.achievements.count)", "trophies")
            }
            .padding(.vertical, 18)
            .background(.white.opacity(0.2), in: .rect(cornerRadius: 28))
            Text("Stronger. Smarter. Better.").font(.headline).opacity(0.8)
        }
        .foregroundStyle(.white)
        .padding(40)
        .frame(width: 540, height: 900)
        .background(LinearGradient(colors: colors, startPoint: .topLeading, endPoint: .bottomTrailing))
    }

    private func stat(_ v: String, _ l: String) -> some View {
        VStack { Text(v).font(.title2.bold()); Text(l).font(.subheadline).opacity(0.85) }.frame(maxWidth: .infinity)
    }
}

/// Renders the brag card and offers it through the share sheet.
struct BragButton: View {
    @Environment(AppSettings.self) private var settings
    let dash: Dash
    var compact = false
    @State var image: Image?

    var body: some View {
        Group {
            if let image {
                ShareLink(item: image, message: Text("🔥 \(dash.streak)-day streak on Kakascalendar. Beat that."),
                          preview: SharePreview("My Kakascalendar streak", image: image)) {
                    label
                }
            } else {
                label.opacity(0.6)
            }
        }
        .task(id: "\(dash.streak)-\(dash.best)-\(dash.entries.count)-\(dash.level.level)-\(settings.accent)-\(settings.photoVersion)") {
            render()
        }
    }

    @ViewBuilder private var label: some View {
        if compact {
            Image(systemName: "square.and.arrow.up")
        } else {
            Label("Brag", systemImage: "square.and.arrow.up")
                .font(.caption.bold()).foregroundStyle(.white)
                .padding(.horizontal, 12).padding(.vertical, 6)
                .background(.white.opacity(0.22), in: .capsule)
        }
    }

    @MainActor private func render() {
        let card = BragCardView(name: settings.name.isEmpty ? "Champion" : settings.name, avatar: settings.avatar,
                                photo: settings.photoVersion > 0 ? AvatarStore.load() : nil, dash: dash,
                                colors: deepened(palette(settings).colors))
        let r = ImageRenderer(content: card)
        r.scale = 2
        if let ui = r.uiImage { image = Image(uiImage: ui) }
    }
}

// MARK: - Reminders

enum Reminders {
    static func requestPermission() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { _, _ in }
    }

    /// Schedules one notification per day for the next two weeks, skipping days already logged.
    static func reschedule(settings: AppSettings, loggedDays: Set<Date>) {
        let center = UNUserNotificationCenter.current()
        let cal = Calendar.current
        let today = cal.startOfDay(for: .now)
        center.removePendingNotificationRequests(withIdentifiers: (0..<15).map {
            "reminder-\(dayKey(cal.date(byAdding: .day, value: $0, to: today)!))"
        })
        guard settings.reminder else { return }
        let streak = streakDays(loggedDays).count
        for i in 0..<14 {
            let day = cal.date(byAdding: .day, value: i, to: today)!
            if loggedDays.contains(day) { continue }
            guard let fire = cal.date(bySettingHour: settings.reminderHour, minute: settings.reminderMinute, second: 0, of: day),
                  fire > .now else { continue }
            let content = UNMutableNotificationContent()
            if i == 0 && streak > 0 {
                content.title = "🔥 Your \(streak)-day streak is on the line"
                content.body = "Don't let it go down the drain. Log today's visit."
            } else {
                content.title = "💩 Nothing logged today"
                content.body = "Champions track. Tap to log your throne time."
            }
            content.sound = .default
            let comps = cal.dateComponents([.year, .month, .day, .hour, .minute], from: fire)
            let req = UNNotificationRequest(identifier: "reminder-\(dayKey(day))", content: content,
                                            trigger: UNCalendarNotificationTrigger(dateMatching: comps, repeats: false))
            center.add(req)
        }
    }
}
