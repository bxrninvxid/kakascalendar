import SwiftUI

// MARK: - Layout helpers

struct FlowLayout: Layout {
    var spacing: CGFloat = 6

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let w = proposal.width ?? .infinity
        var x: CGFloat = 0, y: CGFloat = 0, rowH: CGFloat = 0, maxW: CGFloat = 0
        for s in subviews {
            let sz = s.sizeThatFits(.unspecified)
            if x + sz.width > w && x > 0 { x = 0; y += rowH + spacing; rowH = 0 }
            x += sz.width + spacing
            rowH = max(rowH, sz.height)
            maxW = max(maxW, x - spacing)
        }
        return CGSize(width: maxW, height: y + rowH)
    }

    func placeSubviews(in b: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = b.minX, y = b.minY, rowH: CGFloat = 0
        for s in subviews {
            let sz = s.sizeThatFits(.unspecified)
            if x + sz.width > b.maxX && x > b.minX { x = b.minX; y += rowH + spacing; rowH = 0 }
            s.place(at: CGPoint(x: x, y: y), proposal: .unspecified)
            x += sz.width + spacing
            rowH = max(rowH, sz.height)
        }
    }
}

struct SectionTitle: View {
    @Environment(AppSettings.self) private var settings
    let text: String
    var body: some View {
        HStack(spacing: 8) {
            Capsule().fill(LinearGradient(colors: palette(settings).colors, startPoint: .top, endPoint: .bottom))
                .frame(width: 4, height: 18)
            Text(text).font(.headline)
            Spacer()
        }
        .padding(.leading, 4)
        .padding(.top, 4)
    }
}

struct StatTile: View {
    let label: String
    let value: String
    var emoji: String? = nil
    var size: CGFloat = 24

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            if let emoji { Text(emoji).font(.callout) }
            Text(value).font(.system(size: size, weight: .heavy)).minimumScaleFactor(0.5).lineLimit(1)
            Text(label).font(.caption).foregroundStyle(.secondary).lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .card()
    }
}

struct Pill: View {
    let text: String
    var body: some View {
        Text(text).font(.caption2.weight(.medium))
            .padding(.horizontal, 8).padding(.vertical, 3)
            .background(Color.accentColor.opacity(0.2), in: .capsule)
    }
}

struct GradientButton: View {
    @Environment(AppSettings.self) private var settings
    let title: String
    var enabled = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title).font(.headline).foregroundStyle(.white)
                .frame(maxWidth: .infinity).frame(height: 54)
                .background(
                    enabled ? AnyShapeStyle(LinearGradient(colors: deepened(palette(settings).colors), startPoint: .leading, endPoint: .trailing))
                            : AnyShapeStyle(Color.gray),
                    in: .rect(cornerRadius: 18)
                )
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}

/// Slightly darker accent colors so white text stays readable on them.
func deepened(_ c: [Color]) -> [Color] { c.map { $0.mix(with: .black, by: 0.15) } }

struct ProgressRing<Content: View>: View {
    let fraction: Double
    var lineWidth: CGFloat = 10
    var colors: [Color]
    var track: Color = .primary.opacity(0.08)
    @ViewBuilder var content: Content

    var body: some View {
        ZStack {
            Circle().stroke(track, lineWidth: lineWidth)
            Circle()
                .trim(from: 0, to: max(0.001, min(fraction, 1)))
                .stroke(AngularGradient(colors: colors, center: .center), style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
                .rotationEffect(.degrees(-90))
                .animation(.smooth(duration: 0.9), value: fraction)
            content
        }
    }
}

struct EmptyState: View {
    let emoji: String
    let title: String
    let message: String
    var body: some View {
        VStack(spacing: 6) {
            Text(emoji).font(.system(size: 56))
            Text(title).font(.headline)
            Text(message).font(.subheadline).foregroundStyle(.secondary).multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 24)
    }
}

// MARK: - Avatars

/// Fetches Squad avatar photos (they need the auth header, so AsyncImage won't do).
actor PhotoCache {
    static let shared = PhotoCache()
    private var images: [String: UIImage] = [:]

    func clear() { images.removeAll() }

    func image(url: String, token: String?) async -> UIImage? {
        if let img = images[url] { return img }
        guard let u = URL(string: url) else { return nil }
        var req = URLRequest(url: u)
        if let token { req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        guard let (data, resp) = try? await URLSession.shared.data(for: req),
              (resp as? HTTPURLResponse)?.statusCode == 200, let img = UIImage(data: data) else { return nil }
        images[url] = img
        return img
    }
}

struct Avatar: View {
    @Environment(AppSettings.self) private var settings
    let emoji: String
    var size: CGFloat = 44
    var ring = false
    var photo: UIImage? = nil
    var remoteUserId: Int? = nil
    var remoteVersion: Int = 0
    var bg: Color? = nil

    @State var remote: UIImage?

    var body: some View {
        let shown = photo ?? remote
        ZStack {
            Circle().fill(bg ?? Color.accentColor.opacity(0.22))
            if let shown {
                Image(uiImage: shown).resizable().scaledToFill()
            } else {
                Text(emoji).font(.system(size: size * (ring ? 0.42 : 0.5)))
            }
        }
        .frame(width: ring ? size - 8 : size, height: ring ? size - 8 : size)
        .clipShape(Circle())
        .padding(ring ? 4 : 0)
        .overlay {
            if ring {
                Circle().strokeBorder(LinearGradient(colors: palette(settings).colors, startPoint: .topLeading, endPoint: .bottomTrailing), lineWidth: 2.5)
            }
        }
        .task(id: "\(remoteUserId ?? -1)-\(remoteVersion)") {
            guard let uid = remoteUserId, remoteVersion > 0 else { remote = nil; return }
            remote = await PhotoCache.shared.image(
                url: "\(settings.serverURL.trimmingCharacters(in: CharacterSet(charactersIn: "/")))/users/\(uid)/photo?v=\(remoteVersion)",
                token: settings.token)
        }
    }
}

struct MyAvatar: View {
    @Environment(AppSettings.self) private var settings
    var size: CGFloat = 44
    var ring = false

    var body: some View {
        let photo = settings.photoVersion > 0 ? AvatarStore.load() : nil
        Avatar(emoji: settings.avatar, size: size, ring: ring, photo: photo,
               bg: avatarBackgrounds[min(max(settings.avatarBg, 0), avatarBackgrounds.count - 1)])
            .id(settings.photoVersion)
    }
}

// MARK: - Entries

struct EntryRow: View {
    @Environment(AppSettings.self) private var settings
    let entry: Entry
    var onEdit: (Entry) -> Void
    var onDelete: (Entry) -> Void

    var body: some View {
        let t = entry.stool
        Button { onEdit(entry) } label: {
            HStack(alignment: .top, spacing: 12) {
                Text(t.emoji).font(.title)
                    .frame(width: 52, height: 52)
                    .background(t.color.opacity(0.28), in: .rect(cornerRadius: 16))
                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Text("Type \(t.rawValue) · \(t.label)").font(.headline)
                        if entry.rating > 0 { Text(ratingEmojis[entry.rating - 1]) }
                    }
                    Text(meta).font(.caption).foregroundStyle(.secondary)
                    if !entry.tags.isEmpty {
                        FlowLayout(spacing: 6) { ForEach(entry.tags, id: \.self) { Pill(text: $0) } }
                    }
                    if !entry.comment.isEmpty {
                        Text(entry.comment).font(.subheadline).lineLimit(3)
                    }
                }
                Spacer(minLength: 0)
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .card()
        .contextMenu {
            Button("Edit", systemImage: "pencil") { onEdit(entry) }
            Button("Delete", systemImage: "trash", role: .destructive) { onDelete(entry) }
        }
    }

    private var meta: String {
        var s = timeString(entry.timestamp, use24h: settings.use24h)
        if entry.durationSec > 0 { s += " · ⏱ \(formatDuration(entry.durationSec))" }
        if entry.wipes > 0 { s += " · 🧻 \(entry.wipes)" }
        if let p = Place.of(entry.place) { s += " · \(p.emoji) \(p.label)" }
        return s
    }
}

// MARK: - Heatmap

struct Heatmap: View {
    let counts: [Date: Int]
    var weeks = 26

    var body: some View {
        let cal = Calendar.current
        let today = cal.startOfDay(for: .now)
        let weekdayIndex = (cal.component(.weekday, from: today) + 5) % 7   // Monday = 0
        let start = cal.date(byAdding: .day, value: -(weekdayIndex + 7 * (weeks - 1)), to: today)!
        GeometryReader { geo in
            let gap: CGFloat = 3
            let cell = (geo.size.width - gap * CGFloat(weeks - 1)) / CGFloat(weeks)
            Canvas { ctx, _ in
                for w in 0..<weeks {
                    for d in 0..<7 {
                        let date = cal.date(byAdding: .day, value: w * 7 + d, to: start)!
                        if date > today { continue }
                        let n = counts[date] ?? 0
                        let color: Color = n == 0 ? .primary.opacity(0.08) : Color.accentColor.opacity(n == 1 ? 0.4 : n == 2 ? 0.7 : 1)
                        let rect = CGRect(x: CGFloat(w) * (cell + gap), y: CGFloat(d) * (cell + gap), width: cell, height: cell)
                        ctx.fill(Path(roundedRect: rect, cornerRadius: cell * 0.3), with: .color(color))
                    }
                }
            }
        }
        .aspectRatio(CGFloat(weeks) / 7.4, contentMode: .fit)
    }
}

// MARK: - Confetti

struct ConfettiView: View {
    let trigger: Int

    private struct Particle {
        let x: Double, vx: Double, vy: Double, rot: Double, vr: Double, color: Color, w: Double, h: Double
    }

    @State var particles: [Particle] = []
    @State var start = Date.distantPast

    var body: some View {
        TimelineView(.animation(paused: Date.now.timeIntervalSince(start) > 3)) { ctx in
            Canvas { g, size in
                let t = ctx.date.timeIntervalSince(start)
                guard t < 3 else { return }
                let alpha = max(0, min(1, (3 - t) * 0.8))
                for p in particles {
                    let x = (p.x + p.vx * t) * size.width
                    let y = size.height * 0.3 + (p.vy * t + 0.75 * t * t) * size.height * 0.5
                    var c = g
                    c.translateBy(x: x, y: y)
                    c.rotate(by: .degrees(p.rot + p.vr * t))
                    c.fill(Path(CGRect(x: -p.w / 2, y: -p.h / 2, width: p.w, height: p.h)), with: .color(p.color.opacity(alpha)))
                }
            }
        }
        .allowsHitTesting(false)
        .ignoresSafeArea()
        .onChange(of: trigger) {
            let colors: [Color] = [.orange, .yellow, .pink, .green, .blue, .purple, .red]
            particles = (0..<140).map { _ in
                Particle(x: 0.5 + Double.random(in: -0.1...0.1), vx: Double.random(in: -0.55...0.55),
                         vy: -Double.random(in: 0.5...1.4), rot: .random(in: 0...360), vr: .random(in: -450...450),
                         color: colors.randomElement()!, w: .random(in: 5...11), h: .random(in: 8...16))
            }
            start = .now
        }
    }
}
