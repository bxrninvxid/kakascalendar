import Foundation

// MARK: - Dates

let isoDay: DateFormatter = {
    let f = DateFormatter()
    f.calendar = Calendar(identifier: .gregorian)
    f.locale = Locale(identifier: "en_US_POSIX")
    f.dateFormat = "yyyy-MM-dd"
    return f
}()

func dayKey(_ d: Date) -> String { isoDay.string(from: d) }

func dayLabel(_ d: Date) -> String {
    let cal = Calendar.current
    if cal.isDateInToday(d) { return "Today" }
    if cal.isDateInYesterday(d) { return "Yesterday" }
    return d.formatted(.dateTime.weekday(.abbreviated).month(.abbreviated).day())
}

func timeString(_ d: Date, use24h: Bool) -> String {
    let f = DateFormatter()
    f.dateFormat = use24h ? "HH:mm" : "h:mm a"
    return f.string(from: d)
}

func formatDuration(_ sec: Int) -> String {
    if sec <= 0 { return "–" }
    if sec < 60 { return "\(sec)s" }
    return "\(sec / 60)m \(String(format: "%02d", sec % 60))s"
}

func formatClock(_ sec: Int) -> String { "\(sec / 60):\(String(format: "%02d", sec % 60))" }

func timeAgo(_ ms: Int64) -> String {
    let m = Int((Date.now.timeIntervalSince1970 * 1000 - Double(ms)) / 60_000)
    switch m {
    case ..<1: return "just now"
    case ..<60: return "\(m)m ago"
    case ..<(60 * 24): return "\(m / 60)h ago"
    case ..<(60 * 24 * 7): return "\(m / 1440)d ago"
    default:
        return Date(timeIntervalSince1970: Double(ms) / 1000).formatted(.dateTime.month(.abbreviated).day())
    }
}

// MARK: - Streaks

func daysOf(_ e: [Entry]) -> Set<Date> { Set(e.map(\.day)) }

/// Days in the current streak. It stays alive if you logged yesterday but not yet today.
func streakDays(_ days: Set<Date>) -> Set<Date> {
    let cal = Calendar.current
    var d = cal.startOfDay(for: .now)
    if !days.contains(d) { d = cal.date(byAdding: .day, value: -1, to: d)! }
    var out = Set<Date>()
    while days.contains(d) { out.insert(d); d = cal.date(byAdding: .day, value: -1, to: d)! }
    return out
}

func longestStreak(_ days: Set<Date>) -> Int {
    let cal = Calendar.current
    var best = 0, run = 0
    var prev: Date?
    for d in days.sorted() {
        if let p = prev, cal.date(byAdding: .day, value: 1, to: p) == d { run += 1 } else { run = 1 }
        best = max(best, run)
        prev = d
    }
    return best
}

func longestTypeRun(_ e: [Entry], type: Int) -> Int {
    var best = 0, run = 0
    for x in e.sorted(by: { $0.timestamp < $1.timestamp }) {
        run = x.type == type ? run + 1 : 0
        best = max(best, run)
    }
    return best
}

// MARK: - Stats

enum Period: Int, CaseIterable, Identifiable {
    case week, month, quarter, all
    var id: Int { rawValue }
    var label: String { ["7D", "30D", "90D", "All"][rawValue] }
    var days: Int? { [7, 30, 90, nil][rawValue] }
}

func inPeriod(_ e: [Entry], _ p: Period) -> [Entry] {
    guard let n = p.days else { return e }
    let start = Calendar.current.date(byAdding: .day, value: -(n - 1), to: Calendar.current.startOfDay(for: .now))!
    return e.filter { $0.timestamp >= start }
}

struct Bar: Identifiable {
    let id = UUID()
    let label: String
    let date: Date
    let value: Int
}

func activityBars(_ e: [Entry], _ p: Period) -> [Bar] {
    let cal = Calendar.current
    let today = cal.startOfDay(for: .now)
    let byDay = Dictionary(grouping: e, by: \.day).mapValues(\.count)
    switch p {
    case .week, .month:
        let n = p == .week ? 7 : 30
        return (0..<n).map { i in
            let d = cal.date(byAdding: .day, value: i - (n - 1), to: today)!
            return Bar(label: d.formatted(.dateTime.day()), date: d, value: byDay[d] ?? 0)
        }
    case .quarter:
        return (0..<13).map { i in
            let end = cal.date(byAdding: .day, value: -7 * (12 - i), to: today)!
            let start = cal.date(byAdding: .day, value: -6, to: end)!
            let n = byDay.filter { $0.key >= start && $0.key <= end }.values.reduce(0, +)
            return Bar(label: start.formatted(.dateTime.day().month(.abbreviated)), date: start, value: n)
        }
    case .all:
        return (0..<12).map { i in
            let m = cal.date(byAdding: .month, value: i - 11, to: today)!
            let n = e.filter { cal.isDate($0.timestamp, equalTo: m, toGranularity: .month) }.count
            return Bar(label: m.formatted(.dateTime.month(.narrow)), date: m, value: n)
        }
    }
}

func hourBars(_ e: [Entry]) -> [Bar] {
    let by = Dictionary(grouping: e, by: \.hour).mapValues(\.count)
    return (0..<24).map { Bar(label: "\($0)", date: .now, value: by[$0] ?? 0) }
}

func weekdayBars(_ e: [Entry]) -> [Bar] {
    let cal = Calendar.current
    let by = Dictionary(grouping: e) { cal.component(.weekday, from: $0.timestamp) }.mapValues(\.count)
    let order = [2, 3, 4, 5, 6, 7, 1]
    let symbols = cal.shortWeekdaySymbols
    return order.map { Bar(label: String(symbols[$0 - 1].prefix(2)), date: .now, value: by[$0] ?? 0) }
}

func typeCounts(_ e: [Entry]) -> [Int: Int] { Dictionary(grouping: e, by: \.type).mapValues(\.count) }

func avgPerDay(_ e: [Entry], _ p: Period) -> Double {
    guard let first = e.map(\.day).min() else { return 0 }
    let span: Int
    if let n = p.days { span = n } else {
        span = (Calendar.current.dateComponents([.day], from: first, to: Calendar.current.startOfDay(for: .now)).day ?? 0) + 1
    }
    return Double(e.count) / Double(max(span, 1))
}

func perfectRate(_ e: [Entry]) -> Int { e.isEmpty ? 0 : 100 * e.filter { $0.type == 4 }.count / e.count }

func avgOf(_ values: [Int]) -> Double { values.isEmpty ? 0 : Double(values.reduce(0, +)) / Double(values.count) }
func avgDuration(_ e: [Entry]) -> Int { Int(avgOf(e.map(\.durationSec).filter { $0 > 0 })) }
func avgRating(_ e: [Entry]) -> Double { avgOf(e.map(\.rating).filter { $0 > 0 }) }
func avgWipes(_ e: [Entry]) -> Double { avgOf(e.map(\.wipes).filter { $0 > 0 }) }

func placeCounts(_ e: [Entry]) -> [(Place, Int)] {
    let by = Dictionary(grouping: e.compactMap { Place.of($0.place) }, by: { $0 }).mapValues(\.count)
    return by.sorted { $0.value > $1.value }.map { ($0.key, $0.value) }
}

func heatCounts(_ e: [Entry]) -> [Date: Int] { Dictionary(grouping: e, by: \.day).mapValues(\.count) }

func csv(_ e: [Entry]) -> String {
    func q(_ s: String) -> String { "\"" + s.replacingOccurrences(of: "\"", with: "\"\"") + "\"" }
    let df = DateFormatter()
    df.dateFormat = "yyyy-MM-dd,HH:mm"
    var out = "date,time,type,type_name,rating,duration_sec,wipes,place,tags,comment\n"
    for x in e.sorted(by: { $0.timestamp < $1.timestamp }) {
        out += "\(df.string(from: x.timestamp)),\(x.type),\(q(x.stool.label)),\(x.rating),\(x.durationSec),\(x.wipes),"
        out += "\(q(Place.of(x.place)?.label ?? "")),\(q(x.tags.joined(separator: "; "))),\(q(x.comment))\n"
    }
    return out
}

// MARK: - Backup (same JSON format as the Android app)

struct BackupEntry: Codable {
    var timestamp: Int64
    var type: Int
    var comment: String = ""
    var tags: String = ""
    var durationSec: Int = 0
    var rating: Int = 0
    var wipes: Int = 0
    var place: String = ""

    init(_ e: Entry) {
        timestamp = Int64(e.timestamp.timeIntervalSince1970 * 1000)
        type = e.type; comment = e.comment; tags = e.tags.joined(separator: ",")
        durationSec = e.durationSec; rating = e.rating; wipes = e.wipes; place = e.place
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        timestamp = try c.decode(Int64.self, forKey: .timestamp)
        type = try c.decode(Int.self, forKey: .type)
        comment = try c.decodeIfPresent(String.self, forKey: .comment) ?? ""
        tags = try c.decodeIfPresent(String.self, forKey: .tags) ?? ""
        durationSec = try c.decodeIfPresent(Int.self, forKey: .durationSec) ?? 0
        rating = try c.decodeIfPresent(Int.self, forKey: .rating) ?? 0
        wipes = try c.decodeIfPresent(Int.self, forKey: .wipes) ?? 0
        place = try c.decodeIfPresent(String.self, forKey: .place) ?? ""
    }

    func toEntry() -> Entry {
        Entry(timestamp: Date(timeIntervalSince1970: Double(timestamp) / 1000), type: min(max(type, 1), 7),
              comment: comment, tags: tags.split(separator: ",").map { String($0) }.filter { !$0.isEmpty },
              durationSec: max(durationSec, 0), rating: min(max(rating, 0), 5), wipes: min(max(wipes, 0), 99),
              place: String(place.prefix(16)))
    }
}

struct Backup: Codable {
    var app = "Kakascalendar"
    var version = 3
    var entries: [BackupEntry]
}
