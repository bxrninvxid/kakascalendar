import SwiftUI

struct SquadView: View {
    @Environment(AppSettings.self) private var settings
    @Environment(SocialStore.self) private var social
    let dash: Dash
    @State var section = 0
    @State var viewing: Int?
    @State var composing = false

    var body: some View {
        NavigationStack {
            Group {
                if !settings.signedIn {
                    SignInView()
                } else {
                    ScrollView {
                        VStack(spacing: 12) {
                            StoriesRow(onOpen: { viewing = $0 }, onAdd: { composing = true })
                            Picker("Section", selection: $section) {
                                Text("📰 Feed").tag(0)
                                Text("🏆 Ranks").tag(1)
                                Text(social.friends.incoming.isEmpty ? "🤝 Friends" : "🤝 Friends · \(social.friends.incoming.count)").tag(2)
                            }
                            .pickerStyle(.segmented)
                            if let err = social.error {
                                HStack {
                                    Text("⚠️ \(err)").font(.subheadline)
                                    Spacer()
                                    Button("Retry") { Task { await social.refresh() } }
                                }
                                .padding(14).card(tint: .red)
                            }
                            switch section {
                            case 0: FeedSection()
                            case 1: RanksSection()
                            default: FriendsSection()
                            }
                        }
                        .padding()
                    }
                    .refreshable { await social.refresh() }
                    .fullScreenCover(item: Binding(get: { viewing.map { StoryStart(index: $0) } }, set: { viewing = $0?.index })) { s in
                        StoryViewer(startGroup: s.index)
                    }
                    .sheet(isPresented: $composing) { StoryComposer(dash: dash) }
                    .task {
                        while !Task.isCancelled {
                            await social.refresh()
                            try? await Task.sleep(for: .seconds(45))
                        }
                    }
                }
            }
            .background(AppBackground())
            .navigationTitle("Squad")
            .navigationSubtitle("Friends, feed & bragging rights")
        }
    }
}

struct StoryStart: Identifiable {
    let index: Int
    var id: Int { index }
}

// MARK: - Sign in

private struct SignInView: View {
    @Environment(AppSettings.self) private var settings
    @Environment(SocialStore.self) private var social
    @Environment(UIEvents.self) private var ui
    @State var register = true
    @State var server = ""
    @State var username = ""
    @State var password = ""
    @State var busy = false
    @State var error: String?

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                VStack(spacing: 8) {
                    Text("👑🔥💩").font(.system(size: 40))
                    Text("Join the Squad").font(.title2.bold())
                    Text("Compete on streaks, react to your friends' victories and nudge the slackers. You choose what's shared: comments never leave your phone.")
                        .font(.subheadline).multilineTextAlignment(.center).opacity(0.92)
                }
                .foregroundStyle(.white)
                .padding(22)
                .frame(maxWidth: .infinity)
                .background(LinearGradient(colors: deepened(palette(settings).colors), startPoint: .topLeading, endPoint: .bottomTrailing),
                            in: .rect(cornerRadius: 30))

                VStack(alignment: .leading, spacing: 12) {
                    Picker("Mode", selection: $register) {
                        Text("Create account").tag(true)
                        Text("Sign in").tag(false)
                    }
                    .pickerStyle(.segmented)
                    field("Username", text: $username, prefix: "@")
                        .onChange(of: username) { _, v in
                            let clean = String(v.lowercased().filter { $0.isLetter || $0.isNumber || $0 == "_" }.prefix(20))
                            if clean != v { username = clean }
                        }
                    if register { Text("3–20 characters: a-z, 0-9, _").font(.caption).foregroundStyle(.secondary) }
                    SecureField("Password", text: $password)
                        .padding(14).background(.background.opacity(0.5), in: .rect(cornerRadius: 16))
                    if register { Text("At least 6 characters").font(.caption).foregroundStyle(.secondary) }
                    if Flavor.fixedServer == nil {
                        field("Squad server", text: $server, prefix: nil)
                            .keyboardType(.URL)
                        Text("Simulator → http://localhost:8000 · Phone → http://<your computer's IP>:8000")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                    if let error { Text("⚠️ \(error)").font(.subheadline).foregroundStyle(.red) }
                    GradientButton(title: busy ? "Hold on…" : register ? "Create account" : "Sign in",
                                   enabled: !busy && username.count >= 3 && !password.isEmpty) {
                        busy = true; error = nil
                        Task {
                            let err = await social.authenticate(server: server, username: username, password: password, register: register)
                            busy = false
                            error = err
                            if err == nil { ui.show(register ? "🎉 Welcome to the Squad!" : "👋 Welcome back!") }
                        }
                    }
                }
                .padding(16)
                .card(radius: 28)

                if Flavor.fixedServer == nil {
                    Text("Need a server? It's one Python file in the repo's server/ folder. Run: python3 server.py")
                        .font(.caption).foregroundStyle(.secondary).multilineTextAlignment(.center)
                }
            }
            .padding()
        }
        .onAppear { if server.isEmpty { server = settings.storedServer } }
    }

    private func field(_ title: String, text: Binding<String>, prefix: String?) -> some View {
        HStack(spacing: 2) {
            if let prefix { Text(prefix).foregroundStyle(.secondary) }
            TextField(title, text: text).textInputAutocapitalization(.never).autocorrectionDisabled()
        }
        .padding(14)
        .background(.background.opacity(0.5), in: .rect(cornerRadius: 16))
    }
}

// MARK: - Feed

private struct FeedSection: View {
    @Environment(SocialStore.self) private var social

    var body: some View {
        if social.loaded && social.feed.count <= 1 {
            EmptyState(emoji: "📭", title: "Quiet in here", message: "Add friends in the Friends tab. Their wins, streaks and level-ups show up here.")
        }
        ForEach(social.feed) { item in FeedCard(item: item) }
    }
}

private struct FeedCard: View {
    @Environment(SocialStore.self) private var social
    @Environment(UIEvents.self) private var ui
    let item: FeedItem

    var body: some View {
        let mine = item.user.id == social.me?.id
        let (emoji, text) = describe(mine: mine)
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 12) {
                Avatar(emoji: item.user.avatar, size: 44, remoteUserId: item.user.id, remoteVersion: item.user.photoVersion)
                VStack(alignment: .leading, spacing: 2) {
                    Text("\(mine ? "You" : item.user.displayName) \(text)").font(.subheadline.weight(.semibold))
                    Text("@\(item.user.username) · \(timeAgo(item.createdAt))").font(.caption2).foregroundStyle(.secondary)
                }
                Spacer()
                Text(emoji).font(.title)
            }
            if item.kind != "nudge" {
                HStack(spacing: 6) {
                    ForEach(reactionEmojis, id: \.self) { r in
                        let on = item.myReactions.contains(r)
                        let n = item.reactions[r] ?? 0
                        Button {
                            Task { if let e = await social.react(item, r) { ui.show(e) } }
                        } label: {
                            HStack(spacing: 2) {
                                Text(r)
                                if n > 0 { Text("\(n)").font(.caption.bold()) }
                            }
                            .padding(.horizontal, 9).padding(.vertical, 5)
                        }
                        .buttonStyle(.plain)
                        .glassEffect(on ? .regular.tint(Color.accentColor.opacity(0.5)).interactive() : .regular.interactive(), in: .capsule)
                    }
                }
            }
        }
        .padding(14)
        .card(tint: item.kind == "nudge" && !mine ? .orange : nil)
    }

    private func describe(mine: Bool) -> (String, String) {
        let p = item.payload
        switch item.kind {
        case "log":
            let t = StoolType.of(Int(p["type"] ?? "4") ?? 4)
            return (t.emoji, "logged a Type \(t.rawValue) · \(t.label)")
        case "achievement": return (p["emoji"] ?? "🏆", "unlocked \(p["title"] ?? "an achievement")")
        case "level": return ("⬆️", "reached Level \(p["level"] ?? "?") · \(p["title"] ?? "")")
        case "nudge": return ("👉", mine ? "nudged \(p["target_name"] ?? "a friend")" : "nudged you. Time to go? 🚽")
        case "joined": return ("🎉", "joined Kakascalendar")
        default: return ("✨", item.kind)
        }
    }
}

// MARK: - Ranks

private struct RanksSection: View {
    @Environment(SocialStore.self) private var social
    @State var metric = 0
    private let metrics: [(String, String, (UserDTO) -> Int, String)] = [
        ("Streak", "🔥", { $0.currentStreak }, "d"),
        ("This week", "📅", { $0.weekCount }, ""),
        ("XP", "⭐", { $0.xp }, ""),
        ("Best", "👑", { $0.bestStreak }, "d")
    ]

    var body: some View {
        let m = metrics[metric]
        let ranked = social.board.sorted { m.2($0) > m.2($1) }
        Picker("Metric", selection: $metric) {
            ForEach(0..<metrics.count, id: \.self) { i in Text("\(metrics[i].1) \(metrics[i].0)").tag(i) }
        }
        .pickerStyle(.segmented)
        if !ranked.isEmpty { Podium(top: Array(ranked.prefix(3)), value: { "\(m.1) \(m.2($0))\(m.3)" }) }
        if ranked.count <= 1 && social.loaded {
            EmptyState(emoji: "🏟️", title: "A podium needs rivals", message: "Invite friends to see who really rules the throne.")
        }
        ForEach(Array(ranked.dropFirst(3).enumerated()), id: \.element.id) { i, u in
            HStack(spacing: 10) {
                Text("#\(i + 4)").font(.headline.weight(.black)).frame(width: 40)
                Avatar(emoji: u.avatar, size: 38, remoteUserId: u.id, remoteVersion: u.photoVersion)
                VStack(alignment: .leading) {
                    Text(u.id == social.me?.id ? "You" : u.displayName).font(.subheadline.bold())
                    Text("Lv \(u.level) · @\(u.username)").font(.caption2).foregroundStyle(.secondary)
                }
                Spacer()
                Text("\(m.2(u))\(m.3)").font(.headline.weight(.heavy))
            }
            .padding(12)
            .card(tint: u.id == social.me?.id ? .accentColor : nil)
        }
    }
}

private struct Podium: View {
    @Environment(SocialStore.self) private var social
    @Environment(AppSettings.self) private var settings
    let top: [UserDTO]
    let value: (UserDTO) -> String

    var body: some View {
        let order = [1, 0, 2].filter { top.indices.contains($0) }
        HStack(alignment: .bottom) {
            ForEach(order, id: \.self) { place in
                let u = top[place]
                VStack(spacing: 4) {
                    if place == 0 { Text("👑").font(.title3) }
                    Avatar(emoji: u.avatar, size: place == 0 ? 64 : 52, ring: place == 0, remoteUserId: u.id, remoteVersion: u.photoVersion)
                    Text(u.id == social.me?.id ? "You" : u.displayName).font(.subheadline.bold()).lineLimit(1)
                    Text(value(u)).font(.caption).foregroundStyle(.secondary)
                    ZStack(alignment: .top) {
                        UnevenRoundedRectangle(topLeadingRadius: 16, topTrailingRadius: 16)
                            .fill(place == 0 ? AnyShapeStyle(LinearGradient(colors: deepened(palette(settings).colors), startPoint: .top, endPoint: .bottom))
                                             : AnyShapeStyle(Color.accentColor.opacity(0.25)))
                        Text(["🥇", "🥈", "🥉"][place]).font(.title).padding(.top, 8)
                    }
                    .frame(height: [110, 80, 60][place])
                    .padding(.horizontal, 8)
                }
                .frame(maxWidth: .infinity)
            }
        }
        .padding(16)
        .card(radius: 28)
    }
}

// MARK: - Friends

private struct FriendsSection: View {
    @Environment(AppSettings.self) private var settings
    @Environment(SocialStore.self) private var social
    @Environment(UIEvents.self) private var ui
    @State var add = ""
    @State var confirmRemove: UserDTO?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Your handle").font(.caption).foregroundStyle(.secondary)
            HStack {
                Text("@\(settings.username ?? social.me?.username ?? "")").font(.title2.bold())
                Spacer()
                Button { UIPasteboard.general.string = "@\(settings.username ?? "")"; ui.show("Copied") } label: {
                    Image(systemName: "doc.on.doc")
                }
            }
            if Flavor.autoSquad {
                Text("Everyone on this server is automatically in your squad. Invite friends by sending them the app.")
                    .font(.caption).foregroundStyle(.secondary)
            } else {
                Text("Share it so friends can add you.").font(.caption).foregroundStyle(.secondary)
                HStack {
                    HStack(spacing: 2) {
                        Text("@").foregroundStyle(.secondary)
                        TextField("friend_username", text: $add).textInputAutocapitalization(.never).autocorrectionDisabled()
                    }
                    .padding(12).background(.background.opacity(0.5), in: .rect(cornerRadius: 14))
                    Button {
                        Task {
                            let name = add
                            let err = await social.addFriend(username: name)
                            ui.show(err ?? "🤝 Friend request sent to @\(name)")
                            if err == nil { add = "" }
                        }
                    } label: { Image(systemName: "person.badge.plus") }
                    .buttonStyle(.glassProminent)
                    .disabled(add.count < 3)
                }
            }
        }
        .padding(16)
        .card(radius: 28)

        if !social.friends.incoming.isEmpty {
            SectionTitle(text: "Requests")
            ForEach(social.friends.incoming) { r in
                PersonRow(user: r.user, subtitle: "wants to join your squad") {
                    Button("Decline") { Task { _ = await social.decline(r.id) } }.buttonStyle(.glass)
                    Button("Accept") { Task { ui.show(await social.accept(r.id) ?? "🤝 You're now friends") } }.buttonStyle(.glassProminent)
                }
            }
        }

        let people = social.directory?.filter { $0.status == "none" || $0.status == "outgoing" }
        if let people {
            SectionTitle(text: "People on this server · \(people.count)")
            if people.isEmpty {
                Text((social.directory ?? []).isEmpty ? "You're the first one here. Send the app to your friends!" : "You've added everyone. Popular.")
                    .font(.caption).foregroundStyle(.secondary).padding(.leading, 6)
            }
            ForEach(people) { entry in
                let u = entry.user
                PersonRow(user: u, subtitle: entry.status == "outgoing" ? "request sent · waiting for them" : "@\(u.username) · Lv \(u.level)") {
                    if entry.status == "outgoing" {
                        Button("Requested ✓") { if let rid = entry.requestId { Task { _ = await social.decline(rid) } } }
                            .buttonStyle(.glass)
                    } else {
                        Button("➕ Add") {
                            Task { ui.show(await social.addFriend(userId: u.id) ?? "🤝 Request sent to \(u.displayName)") }
                        }
                        .buttonStyle(.glassProminent)
                    }
                }
            }
            Text("Their poops, streaks and photos stay hidden until they accept.").font(.caption2).foregroundStyle(.secondary).padding(.leading, 6)
        }

        if !social.friends.outgoing.isEmpty && people == nil {
            SectionTitle(text: "Pending")
            ForEach(social.friends.outgoing) { r in
                PersonRow(user: r.user, subtitle: "request sent") {
                    Button("Cancel") { Task { _ = await social.decline(r.id) } }.buttonStyle(.glass)
                }
            }
        }

        SectionTitle(text: "Squad · \(social.friends.friends.count)")
        if social.loaded && social.friends.friends.isEmpty {
            EmptyState(emoji: "🫂", title: Flavor.autoSquad ? "Just you so far" : "No friends yet",
                       message: Flavor.autoSquad ? "Send the app to your friends. Everyone who joins shows up here."
                                                 : "Add someone above. Greatness is better with witnesses.")
        }
        ForEach(social.friends.friends) { f in
            PersonRow(user: f, subtitle: "🔥 \(f.currentStreak)d streak · Lv \(f.level)", photo: true) {
                Button("👉 Nudge") {
                    Task { ui.show(await social.nudge(f.id) ?? "👉 Nudged \(f.displayName). They'll see it in their feed.") }
                }
                .buttonStyle(.glass)
            }
            .contextMenu {
                if !Flavor.autoSquad {
                    Button("Remove friend", systemImage: "person.badge.minus", role: .destructive) { confirmRemove = f }
                }
            }
        }
        if !social.friends.friends.isEmpty && !Flavor.autoSquad {
            Text("Tip: long-press a friend to remove them.").font(.caption2).foregroundStyle(.secondary).padding(.leading, 6)
        }
        Button("Sign out of Squad", role: .destructive) { Task { await social.signOut(); ui.show("Signed out of your Squad") } }
            .buttonStyle(.glass)
            .frame(maxWidth: .infinity)
            .padding(.top, 8)
            .alert("Remove \(confirmRemove?.displayName ?? "")?", isPresented: Binding(get: { confirmRemove != nil }, set: { if !$0 { confirmRemove = nil } })) {
                Button("Remove", role: .destructive) {
                    if let f = confirmRemove { Task { _ = await social.unfriend(f.id) } }
                    confirmRemove = nil
                }
                Button("Keep", role: .cancel) { confirmRemove = nil }
            } message: {
                Text("They'll disappear from your feed and leaderboard. You can add them again later.")
            }
    }
}

private struct PersonRow<Actions: View>: View {
    let user: UserDTO
    let subtitle: String
    var photo = false
    @ViewBuilder var actions: Actions

    var body: some View {
        HStack(spacing: 10) {
            Avatar(emoji: user.avatar, size: 42, remoteUserId: photo ? user.id : nil, remoteVersion: photo ? user.photoVersion : 0)
            VStack(alignment: .leading) {
                Text(user.displayName).font(.subheadline.bold()).lineLimit(1)
                Text(subtitle).font(.caption2).foregroundStyle(.secondary).lineLimit(1)
            }
            Spacer()
            actions
        }
        .padding(12)
        .card()
    }
}
