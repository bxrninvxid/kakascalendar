import SwiftUI
import Observation

/// Everything Squad-related. Actions return nil on success or a human-readable error.
@MainActor
@Observable
final class SocialStore {
    var me: UserDTO?
    var feed: [FeedItem] = []
    var friends = FriendsRes()
    var board: [UserDTO] = []
    var directory: [DirectoryEntry]?      // nil = server has no directory
    var loading = false
    var loaded = false
    var error: String?

    @ObservationIgnored let settings: AppSettings

    init(settings: AppSettings) { self.settings = settings }

    private var api: API? { settings.token.map { API(settings.serverURL, token: $0) } }

    private func run<T>(_ block: (API) async throws -> T) async -> Result<T, Error> {
        guard let api else { return .failure(APIError(status: 0, message: "Sign in to your Squad first")) }
        do { return .success(try await block(api)) } catch {
            if let e = error as? APIError, e.isAuth { reset() }
            return .failure(error)
        }
    }

    private func text(_ r: Result<some Any, Error>) -> String? {
        if case .failure(let e) = r { return e.localizedDescription }
        return nil
    }

    private func reset() {
        settings.token = nil
        settings.username = nil
        me = nil; feed = []; friends = FriendsRes(); board = []; directory = nil; loaded = false
    }

    // MARK: account

    func authenticate(server: String, username: String, password: String, register: Bool) async -> String? {
        let url = (Flavor.fixedServer ?? server).trimmingCharacters(in: .whitespaces)
        guard url.hasPrefix("http://") || url.hasPrefix("https://") else { return "Server address must start with http:// or https://" }
        do {
            let api = API(url, token: nil)
            let res = register
                ? try await api.register(username.lowercased(), password, name: settings.name.isEmpty ? username : settings.name, avatar: settings.avatar)
                : try await api.login(username.lowercased(), password)
            if Flavor.fixedServer == nil { settings.storedServer = url }
            settings.token = res.token
            settings.username = res.user.username
            me = res.user
            _ = try? await API(url, token: res.token).updateMe(["share_level": settings.shareLevel])
            if settings.photoVersion > 0, let img = AvatarStore.load() { _ = await syncPhoto(img) }
            await refresh()
            return nil
        } catch {
            return error.localizedDescription
        }
    }

    func signOut() async {
        if let api { try? await api.logout() }
        reset()
    }

    func updateProfile(_ fields: [String: Any]) async -> String? {
        guard api != nil else { return nil }
        let r = await run { try await $0.updateMe(fields) }
        if case .success(let u) = r { me = u }
        return text(r)
    }

    /// Uploads (or with nil, removes) the profile photo. Skipped when signed out.
    func syncPhoto(_ img: UIImage?) async -> String? {
        guard api != nil else { return nil }
        let r: Result<UserDTO, Error>
        if let img, let b64 = AvatarStore.uploadBase64(img) {
            r = await run { try await $0.uploadPhoto(b64) }
        } else {
            r = await run { try await $0.deletePhoto() }
        }
        if case .success(let u) = r { me = u }
        return text(r)
    }

    // MARK: data

    @discardableResult
    func refresh() async -> String? {
        guard api != nil else { return nil }
        loading = true
        defer { loading = false }
        let r = await run { api -> (UserDTO, [FeedItem], FriendsRes, [UserDTO], [DirectoryEntry]?) in
            async let me = api.me()
            async let feed = api.feed()
            async let friends = api.friends()
            async let board = api.leaderboard()
            let dir: [DirectoryEntry]?
            do { dir = try await api.directory() } catch let e as APIError where !e.isAuth { dir = nil }
            return try await (me, feed, friends, board, dir)
        }
        switch r {
        case .success(let (m, f, fr, b, d)):
            me = m; feed = f; friends = fr; board = b; directory = d; loaded = true; error = nil
            settings.friendsCount = fr.friends.count
        case .failure(let e):
            error = e.localizedDescription
        }
        return error
    }

    func react(_ item: FeedItem, _ emoji: String) async -> String? {
        if let i = feed.firstIndex(where: { $0.id == item.id }) {
            var f = feed[i]
            let mine = f.myReactions.contains(emoji)
            let n = (f.reactions[emoji] ?? 0) + (mine ? -1 : 1)
            f.reactions[emoji] = n <= 0 ? nil : n
            if mine { f.myReactions.removeAll { $0 == emoji } } else { f.myReactions.append(emoji) }
            feed[i] = f
        }
        let r = await run { try await $0.react(item.id, emoji) }
        if case .failure = r { await refresh() }
        return text(r)
    }

    func addFriend(username: String) async -> String? {
        let name = username.trimmingCharacters(in: .whitespaces).trimmingCharacters(in: CharacterSet(charactersIn: "@"))
        guard !name.isEmpty else { return "Enter a username" }
        let r = await run { try await $0.requestFriend(username: name) }
        await refresh()
        return text(r)
    }

    func addFriend(userId: Int) async -> String? {
        let r = await run { try await $0.requestFriend(userId: userId) }
        await refresh()
        return text(r)
    }

    func accept(_ rid: Int) async -> String? { let r = await run { try await $0.accept(rid) }; await refresh(); return text(r) }
    func decline(_ rid: Int) async -> String? { let r = await run { try await $0.decline(rid) }; await refresh(); return text(r) }
    func unfriend(_ uid: Int) async -> String? { let r = await run { try await $0.unfriend(uid) }; await refresh(); return text(r) }
    func nudge(_ uid: Int) async -> String? {
        let r = await run { try await $0.nudge(uid) }
        if case .success = r { await refresh() }
        return text(r)
    }

    // MARK: silent background sync

    func pushStats(streak: Int, best: Int, total: Int, week: Int, xp: Int, level: Int) async {
        _ = await run { try await $0.pushStats(["current_streak": streak, "best_streak": best, "total": total,
                                                "week_count": week, "xp": xp, "level": max(level, 1)]) }
    }

    func postEvent(_ kind: String, _ payload: [String: String]) async {
        _ = await run { try await $0.postEvent(kind, payload) }
    }
}
