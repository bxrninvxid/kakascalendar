import SwiftUI

/// Editable copy of an entry (so Cancel doesn't touch stored data).
struct EntryDraft {
    var timestamp = Date.now
    var type = 4
    var comment = ""
    var tags: [String] = []
    var durationSec = 0
    var rating = 0
    var wipes = 0
    var place = ""

    init() {}

    init(_ e: Entry) {
        timestamp = e.timestamp; type = e.type; comment = e.comment; tags = e.tags
        durationSec = e.durationSec; rating = e.rating; wipes = e.wipes; place = e.place
    }

    func apply(to e: Entry) {
        e.timestamp = min(timestamp, .now)
        e.type = type
        e.comment = comment.trimmingCharacters(in: .whitespacesAndNewlines)
        e.tags = tags
        e.durationSec = durationSec
        e.rating = rating
        e.wipes = wipes
        e.place = place
    }
}

struct AddEntryView: View {
    @Environment(AppSettings.self) private var settings
    @Environment(\.dismiss) private var dismiss
    let initial: Entry?
    var prefillDuration = 0
    let onSave: (EntryDraft, Entry?) -> Bool
    @State var capHit = false

    @State var d = EntryDraft()
    @State var loaded = false

    var body: some View {
        let options = settings.tagOptions + (initial?.tags.filter { !settings.tagOptions.contains($0) } ?? [])
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    DatePicker("When", selection: $d.timestamp, in: ...Date.now, displayedComponents: [.date, .hourAndMinute])
                        .environment(\.locale, settings.use24h ? Locale(identifier: "en_GB") : Locale.current)
                        .padding(14)
                        .card(radius: 22)

                    label("What came out?")
                    ScrollView(.horizontal, showsIndicators: false) {
                        GlassEffectContainer(spacing: 10) {
                            HStack(spacing: 10) {
                                ForEach(StoolType.allCases) { t in
                                    Button { withAnimation(.snappy) { d.type = t.rawValue } } label: {
                                        VStack(spacing: 2) {
                                            Text(t.emoji).font(.largeTitle)
                                            Text("\(t.rawValue)").font(.caption.weight(.black))
                                        }
                                        .padding(.horizontal, 16).padding(.vertical, 10)
                                    }
                                    .buttonStyle(.plain)
                                    .glassEffect(d.type == t.rawValue ? .regular.tint(t.color.opacity(0.6)).interactive() : .regular.interactive(),
                                                 in: .rect(cornerRadius: 20))
                                    .scaleEffect(d.type == t.rawValue ? 1.08 : 1)
                                }
                            }
                            .padding(.vertical, 6)
                        }
                    }
                    let st = StoolType.of(d.type)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Type \(st.rawValue) · \(st.label)").font(.headline).foregroundStyle(st.color)
                        Text(st.desc).font(.subheadline).foregroundStyle(.secondary)
                    }

                    label("How did it feel?")
                    HStack {
                        ForEach(1...5, id: \.self) { v in
                            Button { d.rating = d.rating == v ? 0 : v } label: {
                                VStack(spacing: 2) {
                                    Text(ratingEmojis[v - 1]).font(.system(size: 30)).scaleEffect(d.rating == v ? 1.25 : 1)
                                    Text(ratingLabels[v - 1]).font(.caption2.weight(d.rating == v ? .bold : .regular))
                                        .foregroundStyle(d.rating == v ? Color.accentColor : .secondary)
                                }
                                .frame(maxWidth: .infinity)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .animation(.snappy, value: d.rating)

                    label("Session length")
                    FlowLayout(spacing: 8) {
                        let presets = [60, 180, 300, 600, 900]
                        if d.durationSec > 0 && !presets.contains(d.durationSec) {
                            chip("⏱ \(formatDuration(d.durationSec))", on: true) { d.durationSec = 0 }
                        }
                        ForEach(presets, id: \.self) { s in
                            chip("\(s / 60) min", on: d.durationSec == s) { d.durationSec = d.durationSec == s ? 0 : s }
                        }
                    }

                    label("Where?")
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(Place.allCases) { p in
                                chip("\(p.emoji) \(p.label)", on: d.place == p.rawValue) { d.place = d.place == p.rawValue ? "" : p.rawValue }
                            }
                        }
                    }

                    label("Wipe count")
                    HStack(spacing: 14) {
                        Button { d.wipes = max(d.wipes - 1, 0) } label: { Image(systemName: "minus") }
                            .buttonStyle(.glass).disabled(d.wipes == 0)
                        Text(d.wipes == 0 ? "🧻 –" : "🧻 \(d.wipes)").font(.title2.weight(.black)).contentTransition(.numericText())
                        Button { d.wipes = min(d.wipes + 1, 99) } label: { Image(systemName: "plus") }
                            .buttonStyle(.glass)
                        Text(wipeLabel(d.wipes)).font(.subheadline).foregroundStyle(.secondary)
                    }
                    .animation(.snappy, value: d.wipes)

                    label("Tags")
                    FlowLayout(spacing: 8) {
                        ForEach(options, id: \.self) { tag in
                            chip(tag, on: d.tags.contains(tag)) {
                                if let i = d.tags.firstIndex(of: tag) { d.tags.remove(at: i) } else { d.tags.append(tag) }
                            }
                        }
                    }

                    TextField("Comment (stays on your phone)", text: $d.comment, axis: .vertical)
                        .lineLimit(2...5)
                        .padding(14)
                        .card(radius: 22)
                }
                .padding()
            }
            .background(AppBackground())
            .navigationTitle(initial == nil ? "Log a poop 💩" : "Edit entry ✏️")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel", systemImage: "xmark", role: .cancel) { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save", systemImage: "checkmark", role: .confirm) {
                        var out = d
                        out.tags = options.filter { d.tags.contains($0) }
                        if onSave(out, initial) { dismiss() } else { capHit = true }
                    }
                }
            }
        }
        .alert("Daily limit reached 🚫", isPresented: $capHit) {
            Button("Fair enough") {}
        } message: {
            Text("That day already has \(maxLogsPerDay) logs. Even legends have limits. Pick another day or edit an existing entry.")
        }
        .onAppear {
            guard !loaded else { return }
            loaded = true
            if let initial { d = EntryDraft(initial) } else { d.durationSec = prefillDuration }
        }
    }

    private func label(_ s: String) -> some View {
        Text(s).font(.subheadline.weight(.semibold)).foregroundStyle(.secondary)
    }

    private func chip(_ text: String, on: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(text).font(.subheadline).padding(.horizontal, 12).padding(.vertical, 7)
        }
        .buttonStyle(.plain)
        .glassEffect(on ? .regular.tint(Color.accentColor.opacity(0.55)).interactive() : .regular.interactive(), in: .capsule)
    }
}
