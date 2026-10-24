import SwiftUI
import SwiftData
import LocalAuthentication

@main
struct KakascalendarApp: App {
    @State var settings: AppSettings
    @State var social: SocialStore
    @State var ui = UIEvents()

    init() {
        let s = AppSettings()
        _settings = State(initialValue: s)
        _social = State(initialValue: SocialStore(settings: s))
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(settings)
                .environment(social)
                .environment(ui)
                .modifier(AppStyle(settings: settings))
        }
        .modelContainer(for: [Entry.self, WaterDay.self])
    }
}

// MARK: - Toasts, banners, confetti

struct Toast: Identifiable {
    let id = UUID()
    let message: String
    var actionTitle: String? = nil
    var action: (() -> Void)? = nil
}

struct Banner: Identifiable, Equatable {
    let id = UUID()
    let emoji: String
    let title: String
    let subtitle: String
}

@MainActor
@Observable
final class UIEvents {
    var toast: Toast?
    var banner: Banner?
    var confetti = 0
    var haptic = 0

    func show(_ message: String, actionTitle: String? = nil, action: (() -> Void)? = nil) {
        let t = Toast(message: message, actionTitle: actionTitle, action: action)
        toast = t
        Task {
            try? await Task.sleep(for: .seconds(4))
            if toast?.id == t.id { toast = nil }
        }
    }

    func celebrate(_ b: Banner) {
        banner = b
        confetti += 1
        haptic += 1
        let id = b.id
        Task {
            try? await Task.sleep(for: .seconds(3.5))
            if banner?.id == id { banner = nil }
        }
    }
}

// MARK: - Root: onboarding, lock, app

struct RootView: View {
    @Environment(AppSettings.self) private var settings
    @Environment(\.scenePhase) private var phase
    @State var locked = true
    @State var backgroundedAt: Date?

    var body: some View {
        Group {
            if !settings.onboarded {
                OnboardingView()
            } else if settings.lock && locked {
                LockView(unlock: authenticate)
            } else {
                ContentView()
            }
        }
        .onChange(of: phase) { _, p in
            if p == .background { backgroundedAt = .now }
            if p == .active, let t = backgroundedAt, Date.now.timeIntervalSince(t) > 30 { locked = true }
        }
        .onChange(of: settings.lock) { _, on in if on { locked = false } }
    }

    private func authenticate() {
        let ctx = LAContext()
        var err: NSError?
        guard ctx.canEvaluatePolicy(.deviceOwnerAuthentication, error: &err) else { locked = false; return }
        ctx.evaluatePolicy(.deviceOwnerAuthentication, localizedReason: "Your throne, your secrets") { ok, _ in
            Task { @MainActor in if ok { locked = false } }
        }
    }
}
