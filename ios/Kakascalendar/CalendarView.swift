import SwiftUI

struct CalendarView: View {
    @Environment(AppSettings.self) private var settings
    let dash: Dash
    var onEdit: (Entry) -> Void
    var onDelete: (Entry) -> Void

    @State var month = Calendar.current.dateInterval(of: .month, for: .now)!.start
    @State var selected = Calendar.current.startOfDay(for: .now)

    var body: some View {
        let cal = Calendar.current
        let byDay = Dictionary(grouping: dash.entries, by: \.day)
        let firstWeekday = settings.weekStartsSunday ? 1 : 2
        let offset = (cal.component(.weekday, from: month) - firstWeekday + 7) % 7
        let length = cal.range(of: .day, in: .month, for: month)!.count
        let cells: [Date?] = Array(repeating: nil, count: offset) + (0..<length).map { cal.date(byAdding: .day, value: $0, to: month)! }
        let symbols = cal.veryShortWeekdaySymbols
        let ordered = Array(symbols[(firstWeekday - 1)...] + symbols[..<(firstWeekday - 1)])
        let monthEntries = dash.entries.filter { cal.isDate($0.timestamp, equalTo: month, toGranularity: .month) }
        let dayEntries = byDay[selected] ?? []
        let isCurrentMonth = cal.isDate(month, equalTo: .now, toGranularity: .month)

        NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    VStack(spacing: 10) {
                        HStack {
                            Button { shift(-1) } label: { Image(systemName: "chevron.left") }.buttonStyle(.glass)
                            Spacer()
                            VStack(spacing: 0) {
                                Text(month.formatted(.dateTime.month(.wide).year())).font(.title3.bold())
                                Text("\(monthEntries.count) entries · \(Set(monthEntries.map(\.day)).count) days")
                                    .font(.caption).foregroundStyle(.secondary)
                            }
                            Spacer()
                            Button { shift(1) } label: { Image(systemName: "chevron.right") }
                                .buttonStyle(.glass).disabled(isCurrentMonth)
                        }
                        HStack {
                            ForEach(Array(ordered.enumerated()), id: \.offset) { _, s in
                                Text(s).font(.caption).foregroundStyle(.secondary).frame(maxWidth: .infinity)
                            }
                        }
                        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 4), count: 7), spacing: 4) {
                            ForEach(Array(cells.enumerated()), id: \.offset) { _, date in
                                if let date {
                                    DayCell(date: date, entries: byDay[date] ?? [], inStreak: dash.streakDays.contains(date),
                                            selected: date == selected) { withAnimation(.snappy) { selected = date } }
                                } else {
                                    Color.clear.aspectRatio(1, contentMode: .fit)
                                }
                            }
                        }
                        .id(month)
                        .transition(.opacity)
                        HStack(spacing: 14) {
                            legend(Color.accentColor.opacity(0.45), "Current streak")
                            legend(Color.accentColor.opacity(0.18), "Logged")
                            legend(StoolType.t4.color, "Type dot")
                        }
                    }
                    .padding(16)
                    .card(radius: 30)

                    SectionTitle(text: "\(dayLabel(selected)) · \(dayEntries.count) \(dayEntries.count == 1 ? "entry" : "entries")")
                    if dayEntries.isEmpty {
                        Text("Nothing logged this day.").foregroundStyle(.secondary).frame(maxWidth: .infinity, alignment: .leading).padding(.leading, 6)
                    }
                    ForEach(dayEntries) { EntryRow(entry: $0, onEdit: onEdit, onDelete: onDelete) }
                }
                .padding()
            }
            .background(AppBackground())
            .navigationTitle("Calendar")
            .navigationSubtitle("Your month at a glance")
        }
    }

    private func shift(_ n: Int) {
        withAnimation(.snappy) { month = Calendar.current.date(byAdding: .month, value: n, to: month)! }
    }

    private func legend(_ c: Color, _ text: String) -> some View {
        HStack(spacing: 6) {
            Circle().fill(c).frame(width: 10, height: 10)
            Text(text).font(.caption2).foregroundStyle(.secondary)
        }
    }
}

private struct DayCell: View {
    let date: Date
    let entries: [Entry]
    let inStreak: Bool
    let selected: Bool
    let tap: () -> Void

    var body: some View {
        let cal = Calendar.current
        let today = cal.isDateInToday(date)
        let future = date > .now
        Button(action: tap) {
            VStack(spacing: 2) {
                Text("\(cal.component(.day, from: date))")
                    .font(.callout.weight(today || !entries.isEmpty ? .heavy : .regular))
                    .foregroundStyle(future ? AnyShapeStyle(.tertiary) : today ? AnyShapeStyle(Color.accentColor) : AnyShapeStyle(.primary))
                if !entries.isEmpty {
                    HStack(spacing: 2) {
                        ForEach(entries.prefix(3)) { Circle().fill($0.stool.color).frame(width: 5, height: 5) }
                    }
                }
            }
            .frame(maxWidth: .infinity)
            .aspectRatio(1, contentMode: .fit)
            .background(inStreak ? Color.accentColor.opacity(0.45) : entries.isEmpty ? .clear : Color.accentColor.opacity(0.18),
                        in: .rect(cornerRadius: 12))
            .overlay {
                if selected { RoundedRectangle(cornerRadius: 12).strokeBorder(Color.accentColor, lineWidth: 2) }
                else if today { RoundedRectangle(cornerRadius: 12).strokeBorder(.secondary, lineWidth: 1) }
            }
        }
        .buttonStyle(.plain)
        .disabled(future)
    }
}
