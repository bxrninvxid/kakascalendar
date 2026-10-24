import SwiftUI
import SwiftData

// MARK: - Stored data

@Model
final class Entry {
    var timestamp: Date = Date.now
    var type: Int = 4                 // Bristol stool scale 1...7
    var comment: String = ""
    var tags: [String] = []
    var durationSec: Int = 0
    var rating: Int = 0               // 0 = not rated, 1...5
    var wipes: Int = 0                // 0 = not counted
    var place: String = ""            // Place.rawValue, "" = not set

    init(timestamp: Date = .now, type: Int = 4, comment: String = "", tags: [String] = [],
         durationSec: Int = 0, rating: Int = 0, wipes: Int = 0, place: String = "") {
        self.timestamp = timestamp
        self.type = type
        self.comment = comment
        self.tags = tags
        self.durationSec = durationSec
        self.rating = rating
        self.wipes = wipes
        self.place = place
    }

    var day: Date { Calendar.current.startOfDay(for: timestamp) }
    var hour: Int { Calendar.current.component(.hour, from: timestamp) }
    var stool: StoolType { StoolType.of(type) }
}

@Model
final class WaterDay {
    @Attribute(.unique) var date: String = ""   // yyyy-MM-dd
    var glasses: Int = 0

    init(date: String, glasses: Int) {
        self.date = date
        self.glasses = glasses
    }
}

// MARK: - Bristol scale

enum StoolType: Int, CaseIterable, Identifiable {
    case t1 = 1, t2, t3, t4, t5, t6, t7
    var id: Int { rawValue }
    static func of(_ n: Int) -> StoolType { StoolType(rawValue: n) ?? .t4 }

    var emoji: String { ["🌰", "🥜", "🌽", "🍌", "☁️", "🍮", "💧"][rawValue - 1] }
    var label: String {
        ["Rabbit pellets", "Lumpy", "Cracked", "Perfect", "Soft blobs", "Mushy", "Liquid"][rawValue - 1]
    }
    var desc: String {
        ["Separate hard lumps", "Sausage-shaped but lumpy", "Sausage with cracks on the surface",
         "Smooth, soft sausage or snake", "Soft blobs with clear-cut edges",
         "Fluffy pieces with ragged edges", "Watery, no solid pieces"][rawValue - 1]
    }
    var color: Color {
        [Color(hex: 0xB85C38), Color(hex: 0xD9A441), Color(hex: 0x9CB84F), Color(hex: 0x3FAF7A),
         Color(hex: 0x8DB86A), Color(hex: 0xE0A85A), Color(hex: 0x4F8FD6)][rawValue - 1]
    }
}

// MARK: - Places

enum Place: String, CaseIterable, Identifiable {
    case home, work, school, friend, `public`, nature, travel
    var id: String { rawValue }
    var emoji: String {
        switch self {
        case .home: "🏠"; case .work: "🏢"; case .school: "🎓"; case .friend: "🛋️"
        case .public: "🚻"; case .nature: "🌲"; case .travel: "✈️"
        }
    }
    var label: String {
        switch self {
        case .home: "Home"; case .work: "Work"; case .school: "School"; case .friend: "Friend's"
        case .public: "Public"; case .nature: "Nature"; case .travel: "Travel"
        }
    }
    static func of(_ key: String) -> Place? { Place(rawValue: key) }
}

// MARK: - Tags, ratings, avatars

let coffeeTag = "☕ Coffee"
let showerTag = "🚿 Shower after"
let bidetTag = "💦 Bidet"
let clogTag = "🪠 Clogged"
let builtInTags = [
    "😌 Easy", "😣 Strained", "⚡ Urgent", "📖 Long sit", "📱 Phone",
    "🌶️ Spicy", coffeeTag, "🍺 Night out", "💊 Meds",
    showerTag, bidetTag, "💨 Gassy", "🔇 Silent", "🌋 Eruption", clogTag, "🧘 Zen"
]
func allTags(_ custom: [String]) -> [String] { builtInTags + custom.filter { !builtInTags.contains($0) } }

let ratingEmojis = ["😖", "😕", "😐", "🙂", "🤩"]
let ratingLabels = ["Rough", "Meh", "Okay", "Good", "Legendary"]
let reactionEmojis = ["💩", "🔥", "👑", "😂", "👏"]
let avatarEmojis = [
    "💩", "🦸", "🦊", "🐻", "🐼", "🐸", "🦄", "🐙", "👑", "🤖", "👽", "🦁", "🐯", "🐧", "🦖", "🐵",
    "😎", "🥷", "🧙", "🧛", "🤠", "👻", "💀", "🎃", "🐲", "🦅", "🐺", "🦈", "🍌", "🔥", "⚡", "🚽"
]
let avatarBackgrounds: [Color?] = [
    nil, Color(hex: 0xFFD8A8), Color(hex: 0xFFB4A2), Color(hex: 0xF7A1C4), Color(hex: 0xCDB4FF),
    Color(hex: 0xA0C4FF), Color(hex: 0x9BF6FF), Color(hex: 0xB9FBC0), Color(hex: 0xFDFFB6), Color(hex: 0x2B2D42)
]

func wipeLabel(_ n: Int) -> String {
    switch n {
    case ...0: return "Not counted"
    case 1: return "Ghost wipe 👻"
    case 2...3: return "Clean finish"
    case 4...6: return "Standard"
    case 7...9: return "Paper heavy"
    default: return "Call for backup 🧻"
    }
}

extension Color {
    init(hex: UInt32, alpha: Double = 1) {
        self.init(.sRGB,
                  red: Double((hex >> 16) & 0xFF) / 255,
                  green: Double((hex >> 8) & 0xFF) / 255,
                  blue: Double(hex & 0xFF) / 255,
                  opacity: alpha)
    }
}
