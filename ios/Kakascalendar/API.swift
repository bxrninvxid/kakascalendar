import Foundation

// MARK: - DTOs (match server/server.py)

struct UserDTO: Codable, Identifiable, Hashable {
    var id: Int
    var username: String
    var displayName: String
    var avatar: String = "💩"
    var shareLevel: String = "stats"
    var currentStreak: Int = 0
    var bestStreak: Int = 0
    var total: Int = 0
    var weekCount: Int = 0
    var xp: Int = 0
    var level: Int = 1
    var photoVersion: Int = 0

    enum CodingKeys: String, CodingKey {
        case id, username, avatar, total, xp, level
        case displayName = "display_name"
        case shareLevel = "share_level"
        case currentStreak = "current_streak"
        case bestStreak = "best_streak"
        case weekCount = "week_count"
        case photoVersion = "photo_version"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(Int.self, forKey: .id)
        username = try c.decode(String.self, forKey: .username)
        displayName = try c.decodeIfPresent(String.self, forKey: .displayName) ?? username
        avatar = try c.decodeIfPresent(String.self, forKey: .avatar) ?? "💩"
        shareLevel = try c.decodeIfPresent(String.self, forKey: .shareLevel) ?? "stats"
        currentStreak = try c.decodeIfPresent(Int.self, forKey: .currentStreak) ?? 0
        bestStreak = try c.decodeIfPresent(Int.self, forKey: .bestStreak) ?? 0
        total = try c.decodeIfPresent(Int.self, forKey: .total) ?? 0
        weekCount = try c.decodeIfPresent(Int.self, forKey: .weekCount) ?? 0
        xp = try c.decodeIfPresent(Int.self, forKey: .xp) ?? 0
        level = try c.decodeIfPresent(Int.self, forKey: .level) ?? 1
        photoVersion = try c.decodeIfPresent(Int.self, forKey: .photoVersion) ?? 0
    }
}

struct AuthRes: Decodable {
    let token: String
    let user: UserDTO
}

struct FeedItem: Decodable, Identifiable {
    let id: Int
    let user: UserDTO
    let kind: String
    var payload: [String: String]
    let createdAt: Int64
    var reactions: [String: Int]
    var myReactions: [String]

    enum CodingKeys: String, CodingKey {
        case id, user, kind, payload, reactions
        case createdAt = "created_at"
        case myReactions = "my_reactions"
    }
}

struct FriendRequest: Decodable, Identifiable {
    let id: Int
    let user: UserDTO
}

struct FriendsRes: Decodable {
    var friends: [UserDTO] = []
    var incoming: [FriendRequest] = []
    var outgoing: [FriendRequest] = []
}

/// One person in the server's people directory. status: none | outgoing | incoming | friend
struct DirectoryEntry: Decodable, Identifiable {
    let user: UserDTO
    let status: String
    let requestId: Int?
    var id: Int { user.id }

    enum CodingKeys: String, CodingKey {
        case user, status
        case requestId = "request_id"
    }
}

struct StatusRes: Decodable {
    var status: String?
    var active: Bool?
}

struct Empty: Decodable {}

// MARK: - Client

struct APIError: LocalizedError {
    let status: Int
    let message: String
    var errorDescription: String? { message }
    var isAuth: Bool { status == 401 }
}

struct API {
    let base: String
    let token: String?

    init(_ base: String, token: String?) {
        self.base = base.trimmingCharacters(in: .whitespaces).trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        self.token = token
    }

    func send<T: Decodable>(_ method: String, _ path: String, _ body: [String: Any]? = nil, as: T.Type = T.self) async throws -> T {
        guard let url = URL(string: base + path) else { throw APIError(status: 0, message: "That server address doesn't look right") }
        var req = URLRequest(url: url, timeoutInterval: 12)
        req.httpMethod = method
        if let token { req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        if let body {
            req.setValue("application/json", forHTTPHeaderField: "Content-Type")
            req.httpBody = try JSONSerialization.data(withJSONObject: body)
        }
        let data: Data
        let resp: URLResponse
        do {
            (data, resp) = try await URLSession.shared.data(for: req)
        } catch let e as URLError where e.code == .timedOut {
            throw APIError(status: 0, message: "The server took too long to answer")
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            throw APIError(status: 0, message: "Can't reach the server. Check the server address.")
        }
        let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(code) else {
            let detail = (try? JSONSerialization.jsonObject(with: data) as? [String: Any])?["detail"] as? String
            throw APIError(status: code, message: detail ?? "Server error (\(code))")
        }
        if T.self == Empty.self { return Empty() as! T }
        return try JSONDecoder().decode(T.self, from: data)
    }

    // auth
    func register(_ username: String, _ password: String, name: String, avatar: String) async throws -> AuthRes {
        var b: [String: Any] = ["username": username, "password": password, "display_name": name, "avatar": avatar]
        if !Flavor.inviteCode.isEmpty { b["invite_code"] = Flavor.inviteCode }
        return try await send("POST", "/auth/register", b)
    }
    func login(_ username: String, _ password: String) async throws -> AuthRes {
        try await send("POST", "/auth/login", ["username": username, "password": password])
    }
    func logout() async throws { _ = try await send("POST", "/auth/logout", as: Empty.self) }

    // me
    func me() async throws -> UserDTO { try await send("GET", "/me") }
    func updateMe(_ fields: [String: Any]) async throws -> UserDTO { try await send("PATCH", "/me", fields) }
    func pushStats(_ s: [String: Any]) async throws { _ = try await send("POST", "/me/stats", s, as: Empty.self) }
    func uploadPhoto(_ b64: String) async throws -> UserDTO { try await send("POST", "/me/photo", ["data": b64]) }
    func deletePhoto() async throws -> UserDTO { try await send("DELETE", "/me/photo") }

    // social
    func postEvent(_ kind: String, _ payload: [String: String]) async throws {
        _ = try await send("POST", "/events", ["kind": kind, "payload": payload], as: Empty.self)
    }
    func feed() async throws -> [FeedItem] { try await send("GET", "/feed?limit=60") }
    func react(_ id: Int, _ emoji: String) async throws -> StatusRes { try await send("POST", "/events/\(id)/react", ["emoji": emoji]) }
    func friends() async throws -> FriendsRes { try await send("GET", "/friends") }
    func requestFriend(username: String) async throws -> StatusRes { try await send("POST", "/friends/request", ["username": username]) }
    func requestFriend(userId: Int) async throws -> StatusRes { try await send("POST", "/friends/request", ["user_id": userId]) }
    func accept(_ rid: Int) async throws { _ = try await send("POST", "/friends/requests/\(rid)/accept", as: Empty.self) }
    func decline(_ rid: Int) async throws { _ = try await send("POST", "/friends/requests/\(rid)/decline", as: Empty.self) }
    func unfriend(_ uid: Int) async throws { _ = try await send("DELETE", "/friends/\(uid)", as: Empty.self) }
    func nudge(_ uid: Int) async throws { _ = try await send("POST", "/nudge/\(uid)", as: Empty.self) }
    func leaderboard() async throws -> [UserDTO] { try await send("GET", "/leaderboard") }
    func directory() async throws -> [DirectoryEntry] { try await send("GET", "/users") }
}
