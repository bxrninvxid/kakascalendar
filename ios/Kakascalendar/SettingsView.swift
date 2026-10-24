import SwiftUI
import SwiftData
import PhotosUI
import UniformTypeIdentifiers
import LocalAuthentication

struct SettingsView: View {
    @Environment(AppSettings.self) private var settings
    @Environment(SocialStore.self) private var social
    @Environment(UIEvents.self) private var ui
    @Environment(\.modelContext) private var ctx
    let dash: Dash

    @State var photoItem: PhotosPickerItem?
    @State var showAvatar = false
    @State var newTag = ""
    @State var exportDoc: BackupDocument?
    @State var importing = false
    @State var confirmWipe = false

    var body: some View {
        @Bindable var s = settings
        Form {
            Section("Profile") {
                HStack(spacing: 14) {
                    MyAvatar(size: 64, ring: true)
                    VStack(alignment: .leading) {
                        TextField("Your name", text: $s.name).font(.title3.bold())
                            .onSubmit { Task { _ = await social.updateProfile(["display_name": settings.name]) } }
                        if let u = settings.username { Text("@\(u)").foregroundStyle(.secondary) }
                    }
                }
                PhotosPicker(selection: $photoItem, matching: .images) { Label("Choose a photo", systemImage: "photo") }
                Button { showAvatar = true } label: { Label("Pick an emoji avatar", systemImage: "face.smiling") }
                if settings.photoVersion > 0 {
                    Button(role: .destructive) { removePhoto() } label: { Label("Remove photo", systemImage: "trash") }
                }
            }

            Section("Appearance") {
                Picker("Theme", selection: $s.themeMode) {
                    Text("System").tag(0); Text("Light").tag(1); Text("Dark").tag(2)
                }
                .pickerStyle(.segmented)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 14) {
                        ForEach(accentPresets.indices, id: \.self) { i in
                            swatch(colors: accentPresets[i].colors, label: accentPresets[i].label, on: settings.accent == i) { settings.accent = i }
                        }
                        swatch(colors: customPalette(settings.customHue).colors, label: "Custom", on: settings.accent == customAccentIndex) {
                            settings.accent = customAccentIndex
                        }
                    }
                    .padding(.vertical, 4)
                }
                if settings.accent == customAccentIndex {
                    VStack(alignment: .leading) {
                        LinearGradient(colors: (0...6).map { Color(hue: Double($0) / 6, saturation: 0.7, brightness: 0.85) },
                                       startPoint: .leading, endPoint: .trailing)
                            .frame(height: 10).clipShape(.capsule)
                        Slider(value: $s.customHue, in: 0...359)
                    }
                }
                Picker("Font", selection: $s.font) {
                    ForEach(AppFont.allCases) { f in Text(f.label).fontDesign(f.design).fontWidth(f.width).tag(f.rawValue) }
                }
                Picker("Cards", selection: $s.cardStyle) {
                    Text("Glass").tag(0); Text("Solid").tag(1); Text("Outline").tag(2)
                }
                .pickerStyle(.segmented)
                Toggle("Animated background", isOn: $s.animatedBg)
            }

            Section("Home screen") {
                Toggle("⚡ Quick actions", isOn: binding(.quick))
                Toggle("💧 Water tracker", isOn: binding(.water))
                Toggle("📊 Stat tiles", isOn: binding(.stats))
                Toggle("🧠 Insight of the day", isOn: binding(.insight))
                Stepper("🎯 Water goal: \(settings.waterGoal) glasses", value: $s.waterGoal, in: 1...20)
            }

            Section {
                FlowLayout(spacing: 8) {
                    ForEach(builtInTags, id: \.self) { Pill(text: $0) }
                    ForEach(settings.customTags, id: \.self) { tag in
                        Button { settings.customTags.removeAll { $0 == tag } } label: {
                            HStack(spacing: 4) { Text(tag); Image(systemName: "xmark.circle.fill") }
                                .font(.caption.weight(.medium))
                                .padding(.horizontal, 8).padding(.vertical, 3)
                                .background(Color.accentColor.opacity(0.35), in: .capsule)
                        }
                        .buttonStyle(.plain)
                    }
                }
                HStack {
                    TextField("🏋️ Gym day", text: $newTag).onSubmit(addTag)
                    Button("Add", action: addTag).disabled(newTag.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            } header: { Text("Your tags") } footer: { Text("Start with an emoji. Tap a custom tag to remove it.") }

            Section("Behavior") {
                Toggle("📳 Haptics", isOn: $s.haptics)
                Toggle("🕐 24-hour clock", isOn: $s.use24h)
                Toggle("📅 Week starts on Sunday", isOn: $s.weekStartsSunday)
            }

            Section {
                Toggle("⏰ Daily reminder", isOn: $s.reminder)
                    .onChange(of: settings.reminder) { _, on in
                        if on { Reminders.requestPermission() }
                        Reminders.reschedule(settings: settings, loggedDays: dash.days)
                    }
                DatePicker("Reminder time", selection: reminderTime, displayedComponents: .hourAndMinute)
            } header: { Text("Reminders") } footer: { Text("Only fires on days you haven't logged yet.") }

            Section {
                Toggle("🔒 App lock (Face ID / passcode)", isOn: $s.lock)
                Picker("What your Squad sees", selection: $s.shareLevel) {
                    Text("Everything").tag("full"); Text("Stats only").tag("stats"); Text("Nothing").tag("private")
                }
                .onChange(of: settings.shareLevel) { _, v in Task { _ = await social.updateProfile(["share_level": v]) } }
            } header: { Text("Privacy") } footer: { Text(shareFooter) }

            Section("Squad server") {
                if let fixed = Flavor.fixedServer {
                    LabeledContent("🔐 Private server", value: fixed.replacingOccurrences(of: "https://", with: ""))
                } else {
                    TextField("Server address", text: $s.storedServer)
                        .keyboardType(.URL).textInputAutocapitalization(.never).autocorrectionDisabled()
                }
                if settings.signedIn {
                    Button("Sign out (@\(settings.username ?? ""))", role: .destructive) { Task { await social.signOut() } }
                }
            }

            Section("Your data") {
                ShareLink(item: csv(dash.entries), preview: SharePreview("Kakascalendar export")) {
                    Label("Export CSV", systemImage: "tablecells")
                }
                Button { exportDoc = BackupDocument(entries: dash.entries) } label: { Label("Back up (JSON)", systemImage: "externaldrive") }
                Button { importing = true } label: { Label("Restore from backup", systemImage: "square.and.arrow.down") }
                Button(role: .destructive) { confirmWipe = true } label: { Label("Delete all entries", systemImage: "trash") }
            }

            Section {
                VStack(spacing: 4) {
                    Text("💩").font(.largeTitle)
                    Text("Kakascalendar 2.4").font(.headline)
                    Text("Stronger. Smarter. Better.").font(.caption).foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity)
            }
        }
        .scrollContentBackground(.hidden)
        .background(AppBackground())
        .navigationTitle("Settings")
        .sheet(isPresented: $showAvatar) { AvatarPickerSheet() }
        .onChange(of: photoItem) { _, item in loadPhoto(item) }
        .fileExporter(isPresented: Binding(get: { exportDoc != nil }, set: { if !$0 { exportDoc = nil } }),
                      document: exportDoc, contentType: .json,
                      defaultFilename: "kakascalendar-backup-\(dayKey(.now)).json") { result in
            if case .success = result { ui.show("💾 Backup saved (\(dash.entries.count) entries)") }
        }
        .fileImporter(isPresented: $importing, allowedContentTypes: [.json, .plainText]) { result in
            if case .success(let url) = result { restore(url) }
        }
        .alert("Delete all \(dash.entries.count) entries?", isPresented: $confirmWipe) {
            Button("Delete everything", role: .destructive) {
                try? ctx.delete(model: Entry.self)
                try? ctx.save()
                ui.show("All entries deleted")
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Your streaks, stats and history will be gone. Make a backup first if you might want them back.")
        }
    }

    private var shareFooter: String {
        let base: String
        switch settings.shareLevel {
        case "full": base = "Friends see each log's type, plus your streak, level and trophies."
        case "stats": base = "Friends see your streak, level and trophies. Individual logs stay private."
        default: base = "You're invisible to the Squad: no feed posts, not on leaderboards."
        }
        return base + " Comments, tags and water never leave your phone. Your photo is only visible to friends."
    }

    private func binding(_ s: HomeSection) -> Binding<Bool> {
        Binding(get: { settings.shows(s) }, set: { settings.setShows(s, $0) })
    }

    private var reminderTime: Binding<Date> {
        Binding(
            get: { Calendar.current.date(bySettingHour: settings.reminderHour, minute: settings.reminderMinute, second: 0, of: .now) ?? .now },
            set: { d in
                let c = Calendar.current.dateComponents([.hour, .minute], from: d)
                settings.reminderHour = c.hour ?? 20
                settings.reminderMinute = c.minute ?? 0
                Reminders.reschedule(settings: settings, loggedDays: dash.days)
            }
        )
    }

    private func swatch(colors: [Color], label: String, on: Bool, tap: @escaping () -> Void) -> some View {
        Button(action: tap) {
            VStack(spacing: 4) {
                Circle().fill(LinearGradient(colors: colors, startPoint: .topLeading, endPoint: .bottomTrailing))
                    .frame(width: 42, height: 42)
                    .overlay { if on { Image(systemName: "checkmark").font(.headline.bold()).foregroundStyle(.white) } }
                    .overlay { Circle().strokeBorder(on ? Color.primary : .clear, lineWidth: 3) }
                Text(label).font(.caption2).foregroundStyle(.secondary)
            }
        }
        .buttonStyle(.plain)
    }

    private func addTag() {
        let tag = String(newTag.trimmingCharacters(in: .whitespaces).replacingOccurrences(of: ",", with: " ").prefix(24))
        guard !tag.isEmpty else { return }
        if builtInTags.contains(tag) || settings.customTags.contains(tag) { ui.show("You already have that tag"); return }
        settings.customTags.append(tag)
        newTag = ""
    }

    private func loadPhoto(_ item: PhotosPickerItem?) {
        guard let item else { return }
        Task {
            guard let data = try? await item.loadTransferable(type: Data.self), let img = UIImage(data: data) else {
                ui.show("Couldn't read that image. Try another one.")
                return
            }
            let square = AvatarStore.square(img)
            AvatarStore.save(square)
            settings.photoVersion += 1
            await PhotoCache.shared.clear()
            ui.show("📸 Looking good")
            if let err = await social.syncPhoto(square) { ui.show("Saved locally, but Squad upload failed: \(err)") }
            photoItem = nil
        }
    }

    private func removePhoto() {
        AvatarStore.delete()
        settings.photoVersion = 0
        Task { _ = await social.syncPhoto(nil) }
    }

    private func restore(_ url: URL) {
        let ok = url.startAccessingSecurityScopedResource()
        defer { if ok { url.stopAccessingSecurityScopedResource() } }
        guard let data = try? Data(contentsOf: url), let backup = try? JSONDecoder().decode(Backup.self, from: data) else {
            ui.show("Restore failed: not a Kakascalendar backup")
            return
        }
        let existing = Set(dash.entries.map { "\(Int64($0.timestamp.timeIntervalSince1970 * 1000))-\($0.type)" })
        var added = 0
        for b in backup.entries where !existing.contains("\(b.timestamp)-\(b.type)") {
            ctx.insert(b.toEntry())
            added += 1
        }
        try? ctx.save()
        ui.show("📥 Restored \(added) new entries")
    }
}

struct AvatarPickerSheet: View {
    @Environment(AppSettings.self) private var settings
    @Environment(SocialStore.self) private var social
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    HStack { Spacer(); MyAvatar(size: 90, ring: true); Spacer() }
                    Text("Emoji").font(.headline)
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 6), spacing: 10) {
                        ForEach(avatarEmojis, id: \.self) { e in
                            Button {
                                let hadPhoto = settings.photoVersion > 0
                                settings.avatar = e
                                if hadPhoto { AvatarStore.delete(); settings.photoVersion = 0 }
                                Task {
                                    if hadPhoto { _ = await social.syncPhoto(nil) }
                                    _ = await social.updateProfile(["avatar": e])
                                }
                            } label: {
                                Text(e).font(.title)
                                    .frame(width: 48, height: 48)
                                    .background(settings.avatar == e && settings.photoVersion == 0 ? Color.accentColor.opacity(0.3) : .clear, in: .circle)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    Text("Background").font(.headline)
                    HStack {
                        ForEach(avatarBackgrounds.indices, id: \.self) { i in
                            Circle().fill(avatarBackgrounds[i] ?? Color.accentColor.opacity(0.22))
                                .frame(width: 30, height: 30)
                                .overlay { Circle().strokeBorder(settings.avatarBg == i ? Color.primary : .clear, lineWidth: 2.5) }
                                .onTapGesture { settings.avatarBg = i }
                        }
                    }
                }
                .padding()
            }
            .navigationTitle("Your avatar")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done", systemImage: "checkmark") { dismiss() } } }
        }
        .presentationDetents([.medium, .large])
    }
}

struct BackupDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.json] }
    var data: Data

    init(entries: [Entry]) {
        let enc = JSONEncoder()
        enc.outputFormatting = [.prettyPrinted]
        data = (try? enc.encode(Backup(entries: entries.map { BackupEntry($0) }))) ?? Data()
    }

    init(configuration: ReadConfiguration) throws {
        data = configuration.file.regularFileContents ?? Data()
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: data)
    }
}
