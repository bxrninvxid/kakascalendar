import SwiftUI
import Charts

struct StatsView: View {
    let dash: Dash
    @State var period: Period = .month

    var body: some View {
        let e = inPeriod(dash.entries, period)
        let counts = typeCounts(e)
        let places = placeCounts(e)
        let wipes = avgWipes(e)
        let rating = avgRating(e)

        NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    Picker("Period", selection: $period) {
                        ForEach(Period.allCases) { Text($0.label).tag($0) }
                    }
                    .pickerStyle(.segmented)

                    Grid(horizontalSpacing: 12, verticalSpacing: 12) {
                        GridRow {
                            StatTile(label: "Entries", value: "\(e.count)", emoji: "💩")
                            StatTile(label: "Per day", value: String(format: "%.1f", avgPerDay(e, period)), emoji: "📊")
                            StatTile(label: "Perfect", value: "\(perfectRate(e))%", emoji: "🍌")
                        }
                        GridRow {
                            StatTile(label: "Avg time", value: formatDuration(avgDuration(e)), emoji: "⏱", size: 18)
                            StatTile(label: "Avg feel", value: rating == 0 ? "–" : "\(ratingEmojis[min(max(Int(rating) - 1, 0), 4)]) \(String(format: "%.1f", rating))", emoji: "💭", size: 18)
                            StatTile(label: "Best streak", value: "\(dash.best)d", emoji: "👑", size: 18)
                        }
                    }

                    chartCard(title: "Activity", subtitle: subtitle) {
                        Chart(activityBars(dash.entries, period)) { b in
                            BarMark(x: .value("When", b.label), y: .value("Entries", b.value))
                                .foregroundStyle(Color.accentColor.gradient)
                                .cornerRadius(5)
                        }
                        .chartXAxis(period == .week || period == .all ? .automatic : .hidden)
                        .frame(height: 160)
                    }

                    chartCard(title: "Type breakdown", subtitle: nil) {
                        HStack(spacing: 16) {
                            Chart(StoolType.allCases) { t in
                                SectorMark(angle: .value("Count", counts[t.rawValue] ?? 0), innerRadius: .ratio(0.62), angularInset: 2)
                                    .foregroundStyle(t.color)
                                    .cornerRadius(4)
                            }
                            .chartBackground { _ in
                                VStack(spacing: 0) {
                                    Text("\(e.count)").font(.title.weight(.black))
                                    Text("total").font(.caption2).foregroundStyle(.secondary)
                                }
                            }
                            .frame(width: 140, height: 140)
                            VStack(alignment: .leading, spacing: 4) {
                                ForEach(StoolType.allCases) { t in
                                    let c = counts[t.rawValue] ?? 0
                                    HStack(spacing: 6) {
                                        Circle().fill(t.color).frame(width: 10, height: 10)
                                        Text("\(t.emoji) \(t.label)").font(.caption).lineLimit(1)
                                        Spacer()
                                        Text(e.isEmpty ? "0%" : "\(100 * c / e.count)%").font(.caption.weight(.semibold))
                                    }
                                }
                            }
                        }
                    }

                    chartCard(title: "Throne map", subtitle: wipes == 0 ? "🧻 –" : "🧻 \(String(format: "%.1f", wipes)) avg wipes") {
                        if places.isEmpty {
                            Text("Pick a place when logging to see where you rule.").font(.caption).foregroundStyle(.secondary)
                        } else {
                            let maxP = places.map(\.1).max() ?? 1
                            VStack(spacing: 6) {
                                ForEach(Array(places.enumerated()), id: \.offset) { _, item in
                                    let (p, n) = item
                                    HStack {
                                        Text("\(p.emoji) \(p.label)").font(.caption).frame(width: 96, alignment: .leading)
                                        ProgressView(value: Double(n), total: Double(maxP))
                                        Text("\(n)").font(.caption.weight(.semibold)).frame(width: 30, alignment: .trailing)
                                    }
                                }
                            }
                        }
                    }

                    chartCard(title: "Time of day", subtitle: "When nature calls") {
                        Chart(hourBars(e)) { b in
                            BarMark(x: .value("Hour", b.label), y: .value("Entries", b.value))
                                .foregroundStyle(Color.orange.gradient).cornerRadius(3)
                        }
                        .chartXAxis {
                            AxisMarks(values: ["0", "6", "12", "18"]) { _ in AxisValueLabel() }
                        }
                        .frame(height: 120)
                    }

                    chartCard(title: "Day of week", subtitle: nil) {
                        Chart(weekdayBars(e)) { b in
                            BarMark(x: .value("Day", b.label), y: .value("Entries", b.value))
                                .foregroundStyle(Color.purple.gradient).cornerRadius(5)
                        }
                        .frame(height: 120)
                    }

                    chartCard(title: "Last 6 months", subtitle: "Every square is a day. Darker = busier.") {
                        Heatmap(counts: heatCounts(dash.entries))
                    }

                    SectionTitle(text: "Smart insights")
                    ForEach(dash.insights) { InsightCard(insight: $0) }
                    Text("Insights are friendly patterns, not medical advice. If something feels off, talk to a doctor.")
                        .font(.caption2).foregroundStyle(.secondary)
                }
                .padding()
            }
            .background(AppBackground())
            .navigationTitle("Insights")
            .navigationSubtitle("Stronger. Smarter. Better.")
        }
    }

    private var subtitle: String {
        switch period {
        case .week: "Entries per day, last 7 days"
        case .month: "Entries per day, last 30 days"
        case .quarter: "Entries per week, last 13 weeks"
        case .all: "Entries per month, last 12 months"
        }
    }

    private func chartCard<C: View>(title: String, subtitle: String?, @ViewBuilder content: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(title).font(.headline)
                Spacer()
                if let subtitle { Text(subtitle).font(.caption).foregroundStyle(.secondary) }
            }
            content()
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card(radius: 28)
    }
}
