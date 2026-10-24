package com.example.kakascalendar.ui.screens

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kakascalendar.Dash
import com.example.kakascalendar.MainVm
import com.example.kakascalendar.data.Prefs
import com.example.kakascalendar.domain.AVATARS
import com.example.kakascalendar.domain.toCsv
import com.example.kakascalendar.system.Reminders
import com.example.kakascalendar.ui.components.*
import com.example.kakascalendar.ui.theme.Accent
import java.time.LocalDate

private val LOCK_AUTH = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainVm, prefs: Prefs, dash: Dash, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var editName by remember { mutableStateOf(false) }
    var pickAvatar by remember { mutableStateOf(false) }
    var editServer by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.setReminder(granted, prefs.reminderHour, prefs.reminderMinute)
    }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(vm::exportBackup)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importBackup)
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("Settings", onBack)
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Profile
            item { SectionTitle("Profile") }
            item {
                GlassCard(padding = 4.dp) {
                    SettingRow("🪪", "Name", prefs.name.ifBlank { "Not set" }) { editName = true }
                    SettingRow("🎭", "Avatar", "Tap to change", trailing = { Text(prefs.avatar, fontSize = 26.sp) }) { pickAvatar = true }
                }
            }

            // ── Appearance
            item { SectionTitle("Appearance") }
            item {
                GlassCard {
                    Text("Theme", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf("System", "Light", "Dark").forEachIndexed { i, label ->
                            SegmentedButton(
                                selected = prefs.themeMode == i, onClick = { vm.setAppearance(mode = i) },
                                shape = SegmentedButtonDefaults.itemShape(i, 3)
                            ) { Text(label) }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Accent", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Accent.entries.forEachIndexed { i, a ->
                            val sel = prefs.accent == i && !prefs.dynamicColor
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    Modifier.size(42.dp).clip(CircleShape)
                                        .background(Brush.linearGradient(listOf(a.primary, a.secondary, a.tertiary)))
                                        .then(if (sel) Modifier.border(3.dp, cs.onSurface, CircleShape) else Modifier)
                                        .clickable { vm.setAppearance(accent = i, dynamic = false) },
                                    contentAlignment = Alignment.Center
                                ) { if (sel) Text("✓", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Black) }
                                Text(a.label, fontSize = 10.sp, color = cs.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                    if (Build.VERSION.SDK_INT >= 31) {
                        Spacer(Modifier.height(8.dp))
                        SwitchRow("🎨", "Material You", "Use colors from your wallpaper", prefs.dynamicColor) {
                            vm.setAppearance(dynamic = it)
                        }
                    }
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
                        } + " Comments and tags never leave your phone.",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                    )
                }
            }

            // ── Squad
            item { SectionTitle("Squad server") }
            item {
                GlassCard(padding = 4.dp) {
                    SettingRow("🌐", "Server address", prefs.serverUrl) { editServer = true }
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
                    Text("Kakascalendar 2.0", style = MaterialTheme.typography.titleMedium)
                    Text("Stronger. Smarter. Better.", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    if (editName) TextDialog("Your name", prefs.name, onDismiss = { editName = false }) { vm.setProfile(name = it); editName = false }
    if (editServer) TextDialog("Server address", prefs.serverUrl, onDismiss = { editServer = false }) { vm.setServer(it); editServer = false }
    if (pickAvatar) AvatarDialog(prefs.avatar, onDismiss = { pickAvatar = false }) { vm.setProfile(avatar = it); pickAvatar = false }
    if (confirmWipe) AlertDialog(
        onDismissRequest = { confirmWipe = false },
        title = { Text("Delete all ${dash.entries.size} entries?") },
        text = { Text("Your streaks, stats and history will be gone. Make a backup first if you might want them back.") },
        confirmButton = { TextButton(onClick = { vm.deleteAll(); confirmWipe = false }) { Text("Delete everything", color = cs.error) } },
        dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Cancel") } }
    )
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
fun AvatarDialog(current: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick your avatar") },
        text = { AvatarPicker(current, onPick) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AvatarPicker(current: String, onPick: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AVATARS.forEach { a ->
            Box(
                Modifier.size(48.dp).clip(CircleShape)
                    .background(if (a == current) cs.primaryContainer else cs.surfaceContainerHigh)
                    .then(if (a == current) Modifier.border(2.dp, cs.primary, CircleShape) else Modifier)
                    .clickable { onPick(a) },
                contentAlignment = Alignment.Center
            ) { Text(a, fontSize = 24.sp) }
        }
    }
}
