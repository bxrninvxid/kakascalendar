import SwiftUI
import Observation

/// Default server: localhost works in the iOS Simulator; on a phone use your computer's LAN IP.
let defaultServer = "http://localhost:8000"

enum HomeSection: String, CaseIterable {
    case quick, water, stats, insight
}

/// All user preferences, persisted in UserDefaults.
@Observable
final class AppSettings {
    @ObservationIgnored private let d = UserDefaults.standard

    var onboarded: Bool { didSet { d.set(onboarded, forKey: "onboarded") } }
    var name: String { didSet { d.set(name, forKey: "name") } }
    var avatar: String { didSet { d.set(avatar, forKey: "avatar") } }
    var avatarBg: Int { didSet { d.set(avatarBg, forKey: "avatarBg") } }
    /// Bumped whenever the local photo changes; 0 = no photo.
    var photoVersion: Int { didSet { d.set(photoVersion, forKey: "photoVersion") } }

    var themeMode: Int { didSet { d.set(themeMode, forKey: "themeMode") } }       // 0 system, 1 light, 2 dark
    var accent: Int { didSet { d.set(accent, forKey: "accent") } }
    var customHue: Double { didSet { d.set(customHue, forKey: "customHue") } }
    var font: Int { didSet { d.set(font, forKey: "font") } }
    var cardStyle: Int { didSet { d.set(cardStyle, forKey: "cardStyle") } }       // 0 glass, 1 solid, 2 outline
    var animatedBg: Bool { didSet { d.set(animatedBg, forKey: "animatedBg") } }
    var haptics: Bool { didSet { d.set(haptics, forKey: "haptics") } }
    var use24h: Bool { didSet { d.set(use24h, forKey: "use24h") } }
    var weekStartsSunday: Bool { didSet { d.set(weekStartsSunday, forKey: "weekStartsSunday") } }
    var hiddenSections: [String] { didSet { d.set(hiddenSections, forKey: "hiddenSections") } }
    var customTags: [String] { didSet { d.set(customTags, forKey: "customTags") } }
    var waterGoal: Int { didSet { d.set(waterGoal, forKey: "waterGoal") } }

    var reminder: Bool { didSet { d.set(reminder, forKey: "reminder") } }
    var reminderHour: Int { didSet { d.set(reminderHour, forKey: "reminderHour") } }
    var reminderMinute: Int { didSet { d.set(reminderMinute, forKey: "reminderMinute") } }
    var lock: Bool { didSet { d.set(lock, forKey: "lock") } }

    var storedServer: String { didSet { d.set(storedServer, forKey: "serverUrl") } }
    var token: String? { didSet { d.set(token, forKey: "token") } }
    var username: String? { didSet { d.set(username, forKey: "username") } }
    var shareLevel: String { didSet { d.set(shareLevel, forKey: "shareLevel") } }

    var seenAchievements: [String] { didSet { d.set(seenAchievements, forKey: "seenAchievements") } }
    var achievementsInit: Bool { didSet { d.set(achievementsInit, forKey: "achievementsInit") } }
    var lastLevel: Int { didSet { d.set(lastLevel, forKey: "lastLevel") } }
    var ranksV2: Bool { didSet { d.set(ranksV2, forKey: "ranksV2") } }
    var timerStart: Double { didSet { d.set(timerStart, forKey: "timerStart") } }     // epoch seconds, 0 = off
    var friendsCount: Int { didSet { d.set(friendsCount, forKey: "friendsCount") } }

    init() {
        let d = UserDefaults.standard
        func int(_ k: String, _ def: Int) -> Int { d.object(forKey: k) == nil ? def : d.integer(forKey: k) }
        func bool(_ k: String, _ def: Bool) -> Bool { d.object(forKey: k) == nil ? def : d.bool(forKey: k) }
        onboarded = bool("onboarded", false)
        name = d.string(forKey: "name") ?? ""
        avatar = d.string(forKey: "avatar") ?? "💩"
        avatarBg = int("avatarBg", 0)
        photoVersion = int("photoVersion", 0)
        themeMode = int("themeMode", 0)
        accent = int("accent", 0)
        customHue = d.object(forKey: "customHue") == nil ? 28 : d.double(forKey: "customHue")
        font = int("font", 0)
        cardStyle = int("cardStyle", 0)
        animatedBg = bool("animatedBg", true)
        haptics = bool("haptics", true)
        use24h = bool("use24h", false)
        weekStartsSunday = bool("weekStartsSunday", false)
        hiddenSections = d.stringArray(forKey: "hiddenSections") ?? []
        customTags = d.stringArray(forKey: "customTags") ?? []
        waterGoal = int("waterGoal", 8)
        reminder = bool("reminder", false)
        reminderHour = int("reminderHour", 20)
        reminderMinute = int("reminderMinute", 0)
        lock = bool("lock", false)
        storedServer = d.string(forKey: "serverUrl") ?? defaultServer
        token = d.string(forKey: "token")
        username = d.string(forKey: "username")
        shareLevel = d.string(forKey: "shareLevel") ?? "stats"
        seenAchievements = d.stringArray(forKey: "seenAchievements") ?? []
        achievementsInit = bool("achievementsInit", false)
        lastLevel = int("lastLevel", 0)
        ranksV2 = bool("ranksV2", false)
        timerStart = d.double(forKey: "timerStart")
        friendsCount = int("friendsCount", 0)
    }

    var serverURL: String { Flavor.fixedServer ?? storedServer }
    var signedIn: Bool { token != nil }
    func shows(_ s: HomeSection) -> Bool { !hiddenSections.contains(s.rawValue) }
    func setShows(_ s: HomeSection, _ on: Bool) {
        hiddenSections.removeAll { $0 == s.rawValue }
        if !on { hiddenSections.append(s.rawValue) }
    }
    var tagOptions: [String] { allTags(customTags) }
}

// MARK: - Local avatar photo

enum AvatarStore {
    static var url: URL {
        FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("avatar.jpg")
    }

    static func load() -> UIImage? { UIImage(contentsOfFile: url.path) }

    static func save(_ img: UIImage) { try? img.jpegData(compressionQuality: 0.9)?.write(to: url) }

    static func delete() { try? FileManager.default.removeItem(at: url) }

    /// Center-crops to a square of at most `size` px.
    static func square(_ img: UIImage, size: CGFloat = 512) -> UIImage {
        let side = min(img.size.width, img.size.height)
        let target = min(side, size)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        return UIGraphicsImageRenderer(size: CGSize(width: target, height: target), format: format).image { _ in
            let scale = target / side
            let w = img.size.width * scale, h = img.size.height * scale
            img.draw(in: CGRect(x: (target - w) / 2, y: (target - h) / 2, width: w, height: h))
        }
    }

    static func uploadBase64(_ img: UIImage) -> String? {
        square(img, size: 256).jpegData(compressionQuality: 0.82)?.base64EncodedString()
    }
}
