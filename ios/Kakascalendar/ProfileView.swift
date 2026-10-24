import SwiftUI

struct ProfileView: View {
    @Environment(AppSettings.self) private var settings
    @Environment(\.dismiss) private var dismiss
    let dash: Dash
    @State var detail: Achievement?

    var body: some View {
        let done = dash.achievements.filter(\.unlocked).count
        let sorted = dash.achievements.sorted { a, b in
            a.unlocked != b.unlocked ? a.unlocked : a.fraction > b.fraction
        }
        NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    VStack(spacing: 8) {
                        ProgressRing(fraction: dash.level.fraction, lineWidth: 8, colors: palette(settings).colors) {
                            MyAvatar(size: 100)
                        }
                        .frame(width: 128, height: 128)
                        Text(settings.name.isEmpty ? "Champion" : settings.name).font(.title.bold())
                        if let u = settings.username { Text("@\(u)").foregroundStyle(.secondary) }
                        Text("Level \(dash.level.level) · \(dash.level.title)")
                            .font(.subheadline.bold()).foregroundStyle(.white)
                            .padding(.horizontal, 14).padding(.vertical, 6)
                            .background(LinearGradient(colors: deepened(palette(settings).colors), startPoint: .leading, endPoint: .trailing), in: .capsule)
                        Text("\(dash.level.into) / \(dash.level.need) XP to level \(dash.level.level + 1) · \(dash.level.xp) XP total")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                    HStack(spacing: 12) {
                        StatTile(label: "Entries", value: "\(dash.entries.count)", emoji: "💩")
                        StatTile(label: "Best streak", value: "\(dash.best)d", emoji: "👑")
                        StatTile(label: "Trophies", value: "\(done)/\(dash.achievements.count)", emoji: "🏆")
                    }
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Trophy shelf").font(.headline)
                        ProgressView(value: Double(done), total: Double(max(dash.achievements.count, 1)))
                        Text("Tap any trophy for details. XP from trophies stacks with XP from logging.")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                    .padding(16).card(radius: 28)

                    LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 10), count: 3), spacing: 10) {
                        ForEach(sorted) { a in
                            Button { detail = a } label: { TrophyTile(a: a) }.buttonStyle(.plain)
                        }
                    }
                }
                .padding()
            }
            .background(AppBackground())
            .navigationTitle("Profile")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Close", systemImage: "xmark") { dismiss() } }
                ToolbarItem(placement: .primaryAction) { BragButton(dash: dash, compact: true) }
                ToolbarItem(placement: .primaryAction) {
                    NavigationLink { SettingsView(dash: dash) } label: { Image(systemName: "gearshape") }
                }
            }
            .alert(detail?.title ?? "", isPresented: Binding(get: { detail != nil }, set: { if !$0 { detail = nil } })) {
                Button(detail?.unlocked == true ? "Glorious" : "On it") { detail = nil }
            } message: {
                if let a = detail {
                    Text("\(a.unlocked ? a.emoji : "🔒") \(a.desc)\n\(min(a.progress, a.goal)) / \(a.goal) · \(a.tier.rawValue) · +\(a.xp) XP")
                }
            }
        }
    }
}

private struct TrophyTile: View {
    let a: Achievement
    var body: some View {
        let tc = tierColor(a.tier)
        VStack(spacing: 4) {
            Text(a.unlocked ? a.emoji : "🔒").font(.system(size: 30))
            Text(a.title).font(.caption2.weight(.semibold)).multilineTextAlignment(.center).lineLimit(2, reservesSpace: true)
            ProgressView(value: a.fraction).tint(a.unlocked ? tc : .accentColor)
            Text(a.tier.rawValue).font(.system(size: 10, weight: .bold)).foregroundStyle(tc)
        }
        .padding(10)
        .frame(maxWidth: .infinity)
        .card(tint: a.unlocked ? tc : nil, radius: 20)
        .opacity(a.unlocked ? 1 : 0.65)
    }
}
