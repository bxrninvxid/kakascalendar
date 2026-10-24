package com.example.kakascalendar.ui.screens

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kakascalendar.Dash
import com.example.kakascalendar.MainVm
import com.example.kakascalendar.data.CUSTOM_ACCENT
import com.example.kakascalendar.data.FIXED_SERVER
import com.example.kakascalendar.data.HomeSection
import com.example.kakascalendar.data.Prefs
import com.example.kakascalendar.domain.AVATARS
import com.example.kakascalendar.domain.TAGS
import com.example.kakascalendar.domain.toCsv
import com.example.kakascalendar.system.Reminders
import com.example.kakascalendar.ui.components.*
import com.example.kakascalendar.ui.theme.Accent
import com.example.kakascalendar.ui.theme.AppFont
import com.example.kakascalendar.ui.theme.customAccent
import java.time.LocalDate

private val LOCK_AUTH = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: MainVm, prefs: Prefs, dash: Dash, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var editName by remember { mutableStateOf(false) }
    var pickAvatar by remember { mutableStateOf(false) }
    var editServer by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }
    var newTag by remember { mutableStateOf("") }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.setReminder(granted, prefs.reminderHour, prefs.reminderMinute)
    }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(vm::exportBackup)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importBackup)
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(vm::setAvatarPhoto)
    }
    val pickPhoto = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    Column(Modifier.fillMaxSize()) {
        TopBar("Settings", onBack)
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Profile
            item { SectionTitle("Profile") }
            item {
                GlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MyAvatar(prefs, size = 72.dp, ring = true) { pickAvatar = true }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(prefs.name.ifBlank { "Champion" }, style = MaterialTheme.typography.titleLarge)
                            prefs.username?.let { Text("@$it", color = cs.onSurfaceVariant) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { pickPhoto() }) { Text("📸 Photo") }
                        FilledTonalButton(onClick = { pickAvatar = true }) { Text("🎭 Emoji") }
                        OutlinedButton(onClick = { editName = true }) { Text("✏️ Name") }
                    }
                }
            }

            // ── Appearance
            item { SectionTitle("Appearance") }
            item {
                GlassCard {
                    Label("Theme")
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf("System", "Light", "Dark").forEachIndexed { i, label ->
                            SegmentedButton(
                                selected = prefs.themeMode == i, onClick = { vm.setAppearance(mode = i) },
                                shape = SegmentedButtonDefaults.itemShape(i, 3)
                            ) { Text(label) }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Label("Accent")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Accent.entries.forEachIndexed { i, a ->
                            Swatch(
                                Brush.linearGradient(listOf(a.primary, a.secondary, a.tertiary)), a.label,
                                selected = prefs.accent == i && !prefs.dynamicColor
                            ) { vm.setAppearance(accent = i, dynamic = false) }
                        }
                        val c = customAccent(prefs.customHue)
                        Swatch(
                            Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)),
                            "Custom", selected = prefs.accent == CUSTOM_ACCENT && !prefs.dynamicColor,
                            inner = Brush.linearGradient(listOf(c.primary, c.secondary, c.tertiary))
                        ) { vm.setAppearance(accent = CUSTOM_ACCENT, dynamic = false) }
                    }
                    if (prefs.accent == CUSTOM_ACCENT && !prefs.dynamicColor) {
                        Spacer(Modifier.height(10.dp))
                        HueSlider(prefs.customHue) { vm.setAppearance(hue = it) }
                    }
                    if (Build.VERSION.SDK_INT >= 31) {
                        Spacer(Modifier.height(4.dp))
                        SwitchRow("🎨", "Material You", "Use colors from your wallpaper", prefs.dynamicColor) {
                            vm.setAppearance(dynamic = it)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Label("Font")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppFont.entries.forEachIndexed { i, f ->
                            FilterChip(
                                selected = prefs.font == i, onClick = { vm.setAppearance(font = i) },
                                label = { Text(f.label, fontFamily = f.family()) }
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Label("Cards")
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf("Glass", "Solid", "Outline").forEachIndexed { i, label ->
                            SegmentedButton(
                                selected = prefs.cardStyle == i, onClick = { vm.setAppearance(cardStyle = i) },
                                shape = SegmentedButtonDefaults.itemShape(i, 3)
                            ) { Text(label) }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    SwitchRow("🌈", "Animated background", "Slowly drifting color blobs", prefs.animatedBg) {
                        vm.setAppearance(animatedBg = it)
                    }
                }
            }

            // ── Home screen
            item { SectionTitle("Home screen") }
            item {
                GlassCard(padding = 4.dp) {
                    SwitchRow("⚡", "Quick actions", "Throne timer & quick log", prefs.shows(HomeSection.QUICK)) {
                        vm.setSectionVisible(HomeSection.QUICK, it)
                    }
                    SwitchRow("💧", "Water tracker", "Count glasses through the day", prefs.shows(HomeSection.WATER)) {
                        vm.setSectionVisible(HomeSection.WATER, it)
                    }
                    SwitchRow("📊", "Stat tiles", "Today, this week, total", prefs.shows(HomeSection.STATS)) {
                        vm.setSectionVisible(HomeSection.STATS, it)
                    }
                    SwitchRow("🧠", "Insight of the day", "Top smart insight", prefs.shows(HomeSection.INSIGHT)) {
                        vm.setSectionVisible(HomeSection.INSIGHT, it)
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("🎯", fontSize = 22.sp, modifier = Modifier.width(36.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Daily water goal", fontWeight = FontWeight.SemiBold)
                            Text("${prefs.waterGoal} glasses", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                        FilledTonalIconButton(onClick = { vm.setBehavior(waterGoal = prefs.waterGoal - 1) }, enabled = prefs.waterGoal > 1) {
                            Text("−", fontSize = 20.sp)
                        }
                        FilledTonalIconButton(onClick = { vm.setBehavior(waterGoal = prefs.waterGoal + 1) }, enabled = prefs.waterGoal < 20) {
                            Text("+", fontSize = 20.sp)
                        }
                    }
                }
            }

            // ── Tags
            item { SectionTitle("Your tags") }
            item {
                GlassCard {
                    Text("Built-in tags are always there. Add your own for anything you want to track.",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        TAGS.forEach { Pill(it) }
                        prefs.customTags.forEach { tag ->
                            InputChip(
                                selected = true, onClick = { vm.removeCustomTag(tag) },
                                label = { Text(tag) }, trailingIcon = { Text("✕", fontSize = 12.sp) }
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            newTag, { newTag = it.take(24) }, singleLine = true,
                            placeholder = { Text("🏋️ Gym day") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { vm.addCustomTag(newTag); newTag = "" })
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { vm.addCustomTag(newTag); newTag = "" }, enabled = newTag.isNotBlank()) { Text("Add") }
                    }
                    Text("Tip: start with an emoji. Tap a custom tag to remove it.",
                        style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                }
            }

            // ── Behavior
            item { SectionTitle("Behavior") }
            item {
                GlassCard(padding = 4.dp) {
                    SwitchRow("📳", "Haptics", "Little vibrations on taps and trophies", prefs.haptics) { vm.setBehavior(haptics = it) }
                    SwitchRow("🕐", "24-hour clock", "Show 18:30 instead of 6:30 PM", prefs.use24h) { vm.setBehavior(use24h = it) }
                    SwitchRow("📅", "Week starts on Sunday", "Affects the calendar", prefs.weekStartsSunday) { vm.setBehavior(weekSunday = it) }
                }
            }

            // ── Reminders
            item { SectionTitle("Reminders") }
            item {
                GlassCard(padding = 4.dp) {
                    SwitchRow("⏰", "Daily reminder", "Only if you haven't logged that day", prefs.reminder) { on ->
                        if (on && !Reminders.canNotify(ctx) && Build.VERSION.SDK_INT >= 33) {
                            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else vm.setReminder(on, prefs.reminderHour, prefs.reminderMinute)
                    }
                    SettingRow("🕗", "Reminder time", "%d:%02d".format(prefs.reminderHour, prefs.reminderMinute)) {
                        TimePickerDialog(ctx, { _, h, m -> vm.setReminder(true, h, m) }, prefs.reminderHour, prefs.reminderMinute, true).show()
                    }
                }
            }

            // ── Privacy
            item { SectionTitle("Privacy") }
            item {
                val canLock = remember { BiometricManager.from(ctx).canAuthenticate(LOCK_AUTH) == BiometricManager.BIOMETRIC_SUCCESS }
                GlassCard(padding = 4.dp) {
                    SwitchRow(
                        "🔒", "App lock",
                        if (canLock) "Fingerprint, face or PIN when opening" else "Set up a screen lock on your phone first",
                        prefs.lock, enabled = canLock
                    ) { vm.setLock(it) }
                }
            }
            item {
                GlassCard {
                    Text("What your Squad sees", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf("full" to "Everything", "stats" to "Stats only", "private" to "Nothing").forEachIndexed { i, (k, label) ->
                            SegmentedButton(
                                selected = prefs.shareLevel == k, onClick = { vm.setShareLevel(k) },
                                shape = SegmentedButtonDefaults.itemShape(i, 3)
                            ) { Text(label, maxLines = 1) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when (prefs.shareLevel) {
                            "full" -> "Friends see each log's type, plus your streak, level and trophies."
                            "stats" -> "Friends see your streak, level and trophies. Individual logs stay private."
                            else -> "You're invisible to the Squad: no feed posts, not on leaderboards."
                        } + " Comments, tags and water never leave your phone. Your photo is only visible to friends.",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                    )
                }
            }

            // ── Squad
            item { SectionTitle("Squad server") }
            item {
                GlassCard(padding = 4.dp) {
                    if (FIXED_SERVER == null) SettingRow("🌐", "Server address", prefs.serverUrl) { editServer = true }
                    else SettingRow("🔐", "Private server", FIXED_SERVER.removePrefix("https://")) {}
                    if (prefs.signedIn) SettingRow("🚪", "Sign out", "@${prefs.username}") { vm.signOut() }
                }
            }

            // ── Data
            item { SectionTitle("Your data") }
            item {
                GlassCard(padding = 4.dp) {
                    SettingRow("📤", "Export CSV", "Share a spreadsheet of every entry") {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Kakascalendar export")
                            putExtra(Intent.EXTRA_TEXT, toCsv(dash.entries))
                        }
                        ctx.startActivity(Intent.createChooser(send, "Export data"))
                    }
                    SettingRow("💾", "Back up", "Save everything to a JSON file") {
                        backupLauncher.launch("kakascalendar-backup-${LocalDate.now()}.json")
                    }
                    SettingRow("📥", "Restore", "Import a backup file (duplicates skipped)") {
                        restoreLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    }
                    SettingRow("🧨", "Delete all entries", "This can't be undone") { confirmWipe = true }
                }
            }

            // ── About
            item {
                Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("💩", fontSize = 40.sp)
                    Text("Kakascalendar 2.4", style = MaterialTheme.typography.titleMedium)
                    Text("Stronger. Smarter. Better.", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    if (editName) TextDialog("Your name", prefs.name, onDismiss = { editName = false }) { vm.setProfile(name = it); editName = false }
    if (editServer) TextDialog("Server address", prefs.serverUrl, onDismiss = { editServer = false }) { vm.setServer(it); editServer = false }
    if (pickAvatar) AvatarDialog(
        prefs = prefs,
        onDismiss = { pickAvatar = false },
        onPick = { vm.setProfile(avatar = it) },
        onBg = { vm.setProfile(avatarBg = it) },
        onPhoto = { pickAvatar = false; pickPhoto() },
        onRemovePhoto = { vm.removeAvatarPhoto() }
    )
    if (confirmWipe) AlertDialog(
        onDismissRequest = { confirmWipe = false },
        title = { Text("Delete all ${dash.entries.size} entries?") },
        text = { Text("Your streaks, stats and history will be gone. Make a backup first if you might want them back.") },
        confirmButton = { TextButton(onClick = { vm.deleteAll(); confirmWipe = false }) { Text("Delete everything", color = cs.error) } },
        dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Cancel") } }
    )
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun Swatch(brush: Brush, label: String, selected: Boolean, inner: Brush? = null, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(54.dp)) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(brush)
                .then(if (selected) Modifier.border(3.dp, cs.onSurface, CircleShape) else Modifier)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (inner != null) Box(Modifier.size(26.dp).clip(CircleShape).background(inner))
            if (selected) Text("✓", color = Color.White, fontWeight = FontWeight.Black)
        }
        Text(label, fontSize = 10.sp, color = cs.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun HueSlider(hue: Float, onChange: (Float) -> Unit) {
    var local by remember(hue) { mutableFloatStateOf(hue) }
    val rainbow = Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 0.7f, 0.85f) })
    Column {
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(rainbow))
        Slider(
            value = local, onValueChange = { local = it }, onValueChangeFinished = { onChange(local) },
            valueRange = 0f..359f,
            colors = SliderDefaults.colors(thumbColor = Color.hsv(local, 0.7f, 0.7f), activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent)
        )
    }
}

@Composable
private fun SettingRow(
    emoji: String, title: String, subtitle: String,
    trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 22.sp, modifier = Modifier.width(36.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        trailing?.invoke()
    }
}

@Composable
private fun SwitchRow(emoji: String, title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(enabled = enabled) { onChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 22.sp, modifier = Modifier.width(36.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun TextDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, singleLine = true, shape = RoundedCornerShape(16.dp)) },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AvatarDialog(
    prefs: Prefs,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    onBg: (Int) -> Unit,
    onPhoto: () -> Unit,
    onRemovePhoto: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your avatar") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MyAvatar(prefs, size = 64.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        FilledTonalButton(onClick = onPhoto) { Text("📸 Choose photo") }
                        if (prefs.avatarPhoto != null) TextButton(onClick = onRemovePhoto) { Text("Remove photo") }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("Emoji", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                AvatarPicker(if (prefs.avatarPhoto == null) prefs.avatar else "", onPick)
                Spacer(Modifier.height(14.dp))
                Text("Emoji background", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AVATAR_BGS.forEachIndexed { i, c ->
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(c ?: cs.primaryContainer)
                                .then(if (prefs.avatarBg == i) Modifier.border(3.dp, cs.primary, CircleShape) else Modifier)
                                .clickable { onBg(i) }
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AvatarPicker(current: String, onPick: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AVATARS.forEach { a ->
            Box(
                Modifier.size(44.dp).clip(CircleShape)
                    .background(if (a == current) cs.primaryContainer else cs.surfaceContainerHighest)
                    .then(if (a == current) Modifier.border(2.dp, cs.primary, CircleShape) else Modifier)
                    .clickable { onPick(a) },
                contentAlignment = Alignment.Center
            ) { Text(a, fontSize = 22.sp) }
        }
    }
}
