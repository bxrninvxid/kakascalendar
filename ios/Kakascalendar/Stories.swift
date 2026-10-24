import SwiftUI
import PhotosUI

// MARK: - Shared bits

/// Text-story backgrounds. The index is the server's "bg" field and matches Android.
let storyBackgrounds: [[Color]] = [
    [Color(hex: 0x9A5B34), Color(hex: 0xF2C66D)],
    [Color(hex: 0x6A4CC2), Color(hex: 0xF08AC6)],
    [Color(hex: 0x2465B0), Color(hex: 0x86DEC4)],
    [Color(hex: 0xC0364A), Color(hex: 0xFFC46B)],
    [Color(hex: 0x23806A), Color(hex: 0xB6DE6A)],
    [Color(hex: 0x1D3B8F), Color(hex: 0xD62839)],
    [Color(hex: 0x111111), Color(hex: 0x444444)],
    [Color(hex: 0xFF6F91), Color(hex: 0xFFC75F)]
]
let storyEmojis = ["💩", "🔥", "👑", "🍌", "🚽", "🧻", "😂", "😎", "🤯", "💪", "🎉", "🙏", "🌋", "👻", "💦", "🏆"]

func storyGradient(_ bg: Int) -> LinearGradient {
    LinearGradient(colors: storyBackgrounds[min(max(bg, 0), storyBackgrounds.count - 1)],
                   startPoint: .topLeading, endPoint: .bottomTrailing)
}

enum StoryImage {
    /// JPEG, longest side ≤ 1280 px, under ~550 KB (server limit is 600 KB).
    static func base64(_ img: UIImage) -> String? {
        let maxSide: CGFloat = 1280
        let scale = min(1, maxSide / max(img.size.width, img.size.height))
        let size = CGSize(width: (img.size.width * scale).rounded(), height: (img.size.height * scale).rounded())
        let fmt = UIGraphicsImageRendererFormat.default()
        fmt.scale = 1
        let scaled = UIGraphicsImageRenderer(size: size, format: fmt).image { _ in img.draw(in: CGRect(origin: .zero, size: size)) }
        var q: CGFloat = 0.85
        while q > 0.3 {
            if let d = scaled.jpegData(compressionQuality: q), d.count < 550_000 { return d.base64EncodedString() }
            q -= 0.1
        }
        return nil
    }
}

struct TextStoryCard: View {
    let text: String
    let bg: Int
    let emoji: String

    var body: some View {
        ZStack {
            storyGradient(bg)
            VStack(spacing: 16) {
                if !emoji.isEmpty { Text(emoji).font(.system(size: 96)) }
                if !text.isEmpty {
                    Text(text).font(.system(size: 26, weight: .heavy)).foregroundStyle(.white)
                        .multilineTextAlignment(.center)
                }
            }
            .padding(32)
        }
    }
}

// MARK: - Rings row

struct StoriesRow: View {
    @Environment(SocialStore.self) private var social
    var onOpen: (Int) -> Void
    var onAdd: () -> Void

    var body: some View {
        let groups = social.stories
        let mineIdx = groups.firstIndex { $0.user.id == social.me?.id }
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 14) {
                VStack(spacing: 4) {
                    ZStack(alignment: .bottomTrailing) {
                        Button { if let mineIdx { onOpen(mineIdx) } else { onAdd() } } label: {
                            StoryRing(active: mineIdx != nil, seen: false) { MyAvatar(size: 60) }
                        }
                        .buttonStyle(.plain)
                        Button(action: onAdd) {
                            Image(systemName: "plus").font(.caption.bold()).foregroundStyle(.white)
                                .frame(width: 24, height: 24).background(Color.accentColor, in: .circle)
                                .overlay(Circle().strokeBorder(.background, lineWidth: 2))
                        }
                        .buttonStyle(.plain)
                    }
                    Text("Your story").font(.caption2).lineLimit(1)
                }
                .frame(width: 72)
                ForEach(Array(groups.enumerated()).filter { $0.element.user.id != social.me?.id }, id: \.element.id) { i, g in
                    Button { onOpen(i) } label: {
                        VStack(spacing: 4) {
                            StoryRing(active: true, seen: g.allSeen) {
                                Avatar(emoji: g.user.avatar, size: 60, remoteUserId: g.user.id, remoteVersion: g.user.photoVersion)
                            }
                            Text(g.user.displayName).font(.caption2).lineLimit(1)
                        }
                        .frame(width: 72)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 2)
        }
    }
}

struct StoryRing<Content: View>: View {
    let active: Bool
    let seen: Bool
    @ViewBuilder var content: Content

    var body: some View {
        let style: AnyShapeStyle = !active ? AnyShapeStyle(Color.secondary.opacity(0.25))
            : seen ? AnyShapeStyle(Color.secondary.opacity(0.45))
            : AnyShapeStyle(AngularGradient(colors: [Color(hex: 0xFFC75F), Color(hex: 0xFF6F91), Color(hex: 0x9A5B34), Color(hex: 0xFFC75F)], center: .center))
        content
            .frame(width: 60, height: 60)
            .clipShape(Circle())
            .padding(5)
            .overlay(Circle().strokeBorder(style, lineWidth: 3))
    }
}

// MARK: - Viewer

struct StoryViewer: View {
    @Environment(SocialStore.self) private var social
    @Environment(AppSettings.self) private var settings
    @Environment(UIEvents.self) private var ui
    @Environment(\.dismiss) private var dismiss
    let startGroup: Int

    @State var gi = 0
    @State var si = 0
    @State var progress: Double = 0
    @State var paused = false
    @State var started = false
    @State var viewers: [UserDTO]?
    @State var image: UIImage?

    private let duration: Double = 5

    var body: some View {
        let groups = social.stories
        let group = groups.indices.contains(gi) ? groups[gi] : nil
        let story = group.flatMap { $0.stories.indices.contains(si) ? $0.stories[si] : nil }
        ZStack {
            Color.black.ignoresSafeArea()
            if let story, let group {
                content(story).ignoresSafeArea()
                GeometryReader { geo in
                    Color.clear.contentShape(.rect)
                        .onTapGesture { loc in if loc.x < geo.size.width / 3 { prev() } else { next() } }
                        .gesture(DragGesture(minimumDistance: 30).onEnded { v in
                            if v.translation.height > 100 { dismiss() }
                        })
                }
                .ignoresSafeArea()
                VStack(spacing: 10) {
                    HStack(spacing: 4) {
                        ForEach(group.stories.indices, id: \.self) { i in
                            GeometryReader { geo in
                                Capsule().fill(.white.opacity(0.35))
                                    .overlay(alignment: .leading) {
                                        Capsule().fill(.white)
                                            .frame(width: geo.size.width * (i < si ? 1 : i == si ? progress : 0))
                                    }
                            }
                            .frame(height: 3)
                        }
                    }
                    HStack(spacing: 10) {
                        Avatar(emoji: group.user.avatar, size: 36, remoteUserId: group.user.id, remoteVersion: group.user.photoVersion)
                        VStack(alignment: .leading, spacing: 0) {
                            Text(isMine(group) ? "Your story" : group.user.displayName).font(.subheadline.bold())
                            Text(timeAgo(story.createdAt)).font(.caption2).opacity(0.75)
                        }
                        Spacer()
                        Button { dismiss() } label: { Image(systemName: "xmark").font(.title3.bold()) }
                            .buttonStyle(.glass)
                    }
                    .foregroundStyle(.white)
                    Spacer()
                    if isMine(group) {
                        HStack {
                            Button {
                                paused = true
                                Task {
                                    switch await social.storyViewers(story.id) {
                                    case .success(let list): viewers = list
                                    case .failure(let e): ui.show(e.localizedDescription); paused = false
                                    }
                                }
                            } label: {
                                Label("\(story.views ?? 0) \((story.views ?? 0) == 1 ? "view" : "views")", systemImage: "eye")
                            }
                            .buttonStyle(.glass)
                            Spacer()
                            Button(role: .destructive) {
                                Task {
                                    let err = await social.deleteStory(story.id)
                                    ui.show(err ?? "Story deleted")
                                }
                                dismiss()
                            } label: { Label("Delete", systemImage: "trash") }
                                .buttonStyle(.glass)
                        }
                        .foregroundStyle(.white)
                    }
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
            }
        }
        .statusBarHidden()
        .onAppear {
            guard !started else { return }
            started = true
            gi = min(max(startGroup, 0), max(groups.count - 1, 0))
            si = firstUnseen(gi)
        }
        .task(id: story?.id) {
            guard let story else { if started { dismiss() }; return }
            progress = 0
            image = nil
            Task { await social.viewStory(story) }
            if story.kind == "photo" {
                image = await PhotoCache.shared.image(url: "\(base)/stories/\(story.id)/image", token: settings.token)
            }
            let step = 0.05
            while progress < 1 {
                try? await Task.sleep(for: .seconds(step))
                if Task.isCancelled { return }
                if !paused && viewers == nil { progress = min(1, progress + step / duration) }
            }
            next()
        }
        .sheet(isPresented: Binding(get: { viewers != nil }, set: { if !$0 { viewers = nil; paused = false } })) {
            NavigationStack {
                List {
                    if viewers?.isEmpty ?? true { Text("Nobody yet. They're missing out.").foregroundStyle(.secondary) }
                    ForEach(viewers ?? []) { u in
                        HStack(spacing: 10) {
                            Avatar(emoji: u.avatar, size: 34, remoteUserId: u.id, remoteVersion: u.photoVersion)
                            Text(u.displayName).font(.body.weight(.semibold))
                        }
                    }
                }
                .navigationTitle("Seen by \(viewers?.count ?? 0)")
                .navigationBarTitleDisplayMode(.inline)
            }
            .presentationDetents([.medium])
        }
    }

    private var base: String { settings.serverURL.trimmingCharacters(in: CharacterSet(charactersIn: "/")) }

    private func isMine(_ g: StoryGroup) -> Bool { g.user.id == social.me?.id }

    private func firstUnseen(_ g: Int) -> Int {
        guard social.stories.indices.contains(g) else { return 0 }
        return social.stories[g].stories.firstIndex { !$0.seen } ?? 0
    }

    @ViewBuilder private func content(_ story: StoryDTO) -> some View {
        if story.kind == "photo" {
            ZStack(alignment: .bottom) {
                if let image {
                    Image(uiImage: image).resizable().scaledToFit().frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    ProgressView().tint(.white).frame(maxWidth: .infinity, maxHeight: .infinity)
                }
                if !story.text.isEmpty {
                    Text(story.text).font(.title3.weight(.semibold)).foregroundStyle(.white)
                        .multilineTextAlignment(.center)
                        .padding(12).background(.black.opacity(0.45), in: .rect(cornerRadius: 14))
                        .padding(.horizontal, 20).padding(.bottom, 120)
                }
            }
        } else {
            TextStoryCard(text: story.text, bg: story.bg, emoji: story.emoji)
        }
    }

    private func next() {
        let groups = social.stories
        guard groups.indices.contains(gi) else { dismiss(); return }
        if si < groups[gi].stories.count - 1 { si += 1 }
        else if gi < groups.count - 1 { gi += 1; si = firstUnseen(gi) }
        else { dismiss() }
    }

    private func prev() {
        if si > 0 { si -= 1 }
        else if gi > 0 { gi -= 1; si = 0 }
        else { progress = 0 }
    }
}

// MARK: - Composer

struct StoryComposer: View {
    @Environment(SocialStore.self) private var social
    @Environment(UIEvents.self) private var ui
    @Environment(\.dismiss) private var dismiss
    let dash: Dash

    @State var mode = 0            // 0 text, 1 photo, 2 today's log
    @State var text = ""
    @State var bg = 0
    @State var emoji = "💩"
    @State var pick: PhotosPickerItem?
    @State var photo: UIImage?
    @State var busy = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text("Visible to your squad for 24 hours.").font(.footnote).foregroundStyle(.secondary)
                    Picker("Kind", selection: $mode) {
                        Text("✍️ Text").tag(0)
                        Text("📸 Photo").tag(1)
                        Text("💩 Today").tag(2)
                    }
                    .pickerStyle(.segmented)

                    ZStack(alignment: .bottom) {
                        if mode == 1 {
                            Color.black
                            if let photo {
                                Image(uiImage: photo).resizable().scaledToFill()
                            } else {
                                Text("Choose a photo below").foregroundStyle(.white).frame(maxHeight: .infinity)
                            }
                            if !text.isEmpty {
                                Text(text).font(.subheadline.weight(.semibold)).foregroundStyle(.white)
                                    .padding(10).background(.black.opacity(0.45), in: .rect(cornerRadius: 12)).padding(14)
                            }
                        } else {
                            TextStoryCard(text: text, bg: bg, emoji: emoji)
                        }
                    }
                    .frame(height: 340)
                    .frame(maxWidth: .infinity)
                    .clipShape(.rect(cornerRadius: 26))

                    if mode == 1 {
                        PhotosPicker(selection: $pick, matching: .images) {
                            Label(photo == nil ? "Choose photo" : "Change photo", systemImage: "photo.on.rectangle")
                                .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.glass)
                    }

                    TextField(mode == 1 ? "Caption (optional)" : "What's on your mind?", text: $text, axis: .vertical)
                        .lineLimit(2...5)
                        .padding(14).card(radius: 18)
                        .onChange(of: text) { if text.count > 300 { text = String(text.prefix(300)) } }

                    if mode != 1 {
                        Text("Background").font(.subheadline.bold()).foregroundStyle(.secondary)
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 10) {
                                ForEach(storyBackgrounds.indices, id: \.self) { i in
                                    Circle().fill(storyGradient(i)).frame(width: 40, height: 40)
                                        .overlay(Circle().strokeBorder(.primary, lineWidth: bg == i ? 3 : 0))
                                        .onTapGesture { bg = i }
                                }
                            }
                            .padding(2)
                        }
                        Text("Sticker").font(.subheadline.bold()).foregroundStyle(.secondary)
                        FlowLayout(spacing: 6) {
                            ForEach([""] + storyEmojis, id: \.self) { e in
                                Text(e.isEmpty ? "∅" : e).font(.title3)
                                    .frame(width: 42, height: 42)
                                    .background(emoji == e ? Color.accentColor.opacity(0.3) : Color.primary.opacity(0.06), in: .circle)
                                    .onTapGesture { emoji = e }
                            }
                        }
                    }

                    GradientButton(title: busy ? "Posting…" : "Share story", enabled: canPost) { post() }
                }
                .padding()
            }
            .background(AppBackground())
            .navigationTitle("New story 📖")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel", systemImage: "xmark") { dismiss() } }
            }
            .onChange(of: mode) {
                if mode == 2 { text = todayText(); emoji = dash.entries.first?.stool.emoji ?? "💩"; bg = 0 }
            }
            .onChange(of: pick) {
                Task {
                    if let data = try? await pick?.loadTransferable(type: Data.self), let img = UIImage(data: data) { photo = img }
                }
            }
        }
    }

    private var canPost: Bool {
        !busy && (mode == 1 ? photo != nil : (!text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || !emoji.isEmpty))
    }

    private func todayText() -> String {
        let today = Calendar.current.startOfDay(for: .now)
        let last = dash.entries.first { $0.day == today } ?? dash.entries.first
        var parts: [String] = []
        if let last {
            parts.append("Type \(last.stool.rawValue) · \(last.stool.label)")
            if last.rating > 0 { parts.append("felt \(ratingLabels[last.rating - 1].lowercased())") }
            if last.wipes > 0 { parts.append("\(last.wipes) wipes") }
        }
        if dash.streak > 0 { parts.append("🔥 \(dash.streak)-day streak") }
        parts.append("Lv \(dash.level.level) \(dash.level.title)")
        return parts.joined(separator: " · ")
    }

    private func post() {
        busy = true
        Task {
            let err: String?
            if mode == 1 {
                err = await social.postStory(kind: "photo", text: text, image: photo)
            } else {
                err = await social.postStory(kind: "text", text: text, bg: bg, emoji: emoji)
            }
            busy = false
            if let err { ui.show(err) } else { ui.show("📖 Story posted. It disappears in 24 hours."); dismiss() }
        }
    }
}
