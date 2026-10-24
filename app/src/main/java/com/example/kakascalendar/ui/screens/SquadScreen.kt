package com.example.kakascalendar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kakascalendar.MainVm
import com.example.kakascalendar.data.Prefs
import com.example.kakascalendar.domain.REACTIONS
import com.example.kakascalendar.domain.StoolType
import com.example.kakascalendar.domain.timeAgo
import com.example.kakascalendar.social.FeedItem
import com.example.kakascalendar.social.SocialState
import com.example.kakascalendar.social.UserDto
import com.example.kakascalendar.ui.components.*
import kotlinx.coroutines.delay

@Composable
fun SquadScreen(vm: MainVm, prefs: Prefs) {
    if (!prefs.signedIn) {
        SignInScreen(vm, prefs)
        return
    }
    val state by vm.socialState.collectAsStateWithLifecycle()
    var section by rememberSaveable { mutableIntStateOf(0) }

    // light polling while the tab is visible
    LaunchedEffect(Unit) {
        while (true) {
            vm.refreshSocial(quiet = true)
            delay(45_000)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val incoming = state.friends.incoming.size
            listOf("📰 Feed", "🏆 Ranks", if (incoming > 0) "🤝 Friends · $incoming" else "🤝 Friends").forEachIndexed { i, label ->
                OutlineChip(label, section == i, { section = i })
            }
        }
        when (section) {
            0 -> FeedSection(vm, state)
            1 -> RanksSection(vm, state)
            else -> FriendsSection(vm, state, prefs)
        }
    }
}

// ───────────────────────── sign in ─────────────────────────

@Composable
private fun SignInScreen(vm: MainVm, prefs: Prefs) {
    var register by rememberSaveable { mutableStateOf(true) }
    var server by rememberSaveable { mutableStateOf(prefs.serverUrl) }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val cs = MaterialTheme.colorScheme

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(accentBrush()).padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("👑🔥💩", fontSize = 40.sp)
                Spacer(Modifier.height(8.dp))
                Text("Join the Squad", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Compete on streaks, react to your friends' victories and nudge the slackers. " +
                        "You choose what's shared: comments never leave your phone.",
                    color = Color.White.copy(alpha = 0.92f), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        item {
            GlassCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlineChip("Create account", register, { register = true })
                    OutlineChip("Sign in", !register, { register = false })
                }
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    username, { username = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' }.take(20) },
                    label = { Text("Username") }, prefix = { Text("@") }, singleLine = true,
                    supportingText = { if (register) Text("3–20 characters: a-z, 0-9, _") },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)
                )
                OutlinedTextField(
                    password, { password = it }, label = { Text("Password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    supportingText = { if (register) Text("At least 6 characters") },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)
                )
                OutlinedTextField(
                    server, { server = it.trim() }, label = { Text("Squad server") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    supportingText = { Text("Emulator → http://10.0.2.2:8000 · Phone → http://<your PC's IP>:8000") },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)
                )
                error?.let {
                    Text("⚠️ $it", color = cs.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 6.dp))
                }
                Spacer(Modifier.height(8.dp))
                GradientButton(
                    if (busy) "Hold on…" else if (register) "Create account" else "Sign in",
                    Modifier.fillMaxWidth(), enabled = !busy && username.length >= 3 && password.isNotEmpty()
                ) {
                    busy = true; error = null
                    vm.authenticate(server, username, password, register) { err -> busy = false; error = err }
                }
            }
        }
        item {
            Text(
                "Need a server? It's one Python file in the repo's server/ folder. Run: python3 server.py",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ───────────────────────── feed ─────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedSection(vm: MainVm, state: SocialState) {
    PullToRefreshBox(isRefreshing = state.loading, onRefresh = { vm.refreshSocial() }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 130.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            state.error?.let { err -> item { ErrorCard(err) { vm.refreshSocial() } } }
            if (state.loaded && state.feed.size <= 1) item {
                EmptyState("📭", "Quiet in here", "Add friends in the Friends tab. Their wins, streaks and level-ups show up here.")
            }
            items(state.feed, key = { it.id }) { item ->
                FeedCard(item, state.me?.id, onReact = { vm.react(item, it) })
            }
        }
    }
}

private fun describe(item: FeedItem, meId: Long?): Pair<String, String> {
    val p = item.payload
    return when (item.kind) {
        "log" -> {
            val t = StoolType.of(p["type"]?.toIntOrNull() ?: 4)
            t.emoji to "logged a Type ${t.n} · ${t.label}"
        }
        "achievement" -> (p["emoji"] ?: "🏆") to "unlocked ${p["title"] ?: "an achievement"}"
        "level" -> "⬆️" to "reached Level ${p["level"] ?: "?"} · ${p["title"] ?: ""}"
        "nudge" -> "👉" to if (item.user.id == meId) "nudged ${p["target_name"] ?: "a friend"}" else "nudged you. Time to go? 🚽"
        "joined" -> "🎉" to "joined Kakascalendar"
        else -> "✨" to item.kind
    }
}

@Composable
private fun FeedCard(item: FeedItem, meId: Long?, onReact: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val (emoji, text) = describe(item, meId)
    val highlight = item.kind == "nudge" && item.user.id != meId
    GlassCard(padding = 14.dp, tint = if (highlight) cs.secondaryContainer.copy(alpha = 0.8f) else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(item.user.avatar, 44.dp, photo = rememberUserPhoto(item.user.id, item.user.photoVersion))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    (if (item.user.id == meId) "You" else item.user.displayName) + " " + text,
                    fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium
                )
                Text("@${item.user.username} · ${timeAgo(item.createdAt)}", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
            Text(emoji, fontSize = 28.sp)
        }
        if (item.kind != "nudge") {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                REACTIONS.forEach { r ->
                    val mine = r in item.myReactions
                    val n = item.reactions[r] ?: 0
                    Row(
                        Modifier.clip(RoundedCornerShape(50))
                            .background(if (mine) cs.primaryContainer else cs.onSurface.copy(alpha = 0.05f))
                            .then(if (mine) Modifier.border(1.dp, cs.primary, RoundedCornerShape(50)) else Modifier)
                            .clickable { onReact(r) }
                            .padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(r, fontSize = 15.sp)
                        if (n > 0) Text(" $n", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(text: String, onRetry: () -> Unit) {
    GlassCard(tint = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)) {
        Text("⚠️ $text", color = MaterialTheme.colorScheme.onErrorContainer)
        TextButton(onClick = onRetry) { Text("Retry") }
    }
}

// ───────────────────────── ranks ─────────────────────────

private enum class Metric(val label: String, val emoji: String, val value: (UserDto) -> Int, val unit: String) {
    STREAK("Streak", "🔥", { it.currentStreak }, "d"),
    WEEK("This week", "📅", { it.weekCount }, ""),
    XP("XP", "⭐", { it.xp }, ""),
    BEST("Best streak", "👑", { it.bestStreak }, "d")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RanksSection(vm: MainVm, state: SocialState) {
    var metric by rememberSaveable { mutableIntStateOf(0) }
    val m = Metric.entries[metric]
    val ranked = state.board.sortedByDescending { m.value(it) }
    PullToRefreshBox(isRefreshing = state.loading, onRefresh = { vm.refreshSocial() }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 130.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Metric.entries.forEachIndexed { i, x ->
                        FilterChip(selected = metric == i, onClick = { metric = i }, label = { Text("${x.emoji} ${x.label}") })
                    }
                }
            }
            if (ranked.isNotEmpty()) item { Podium(ranked.take(3), m, state.me?.id) }
            if (ranked.size <= 1 && state.loaded) item {
                EmptyState("🏟️", "A podium needs rivals", "Invite friends to see who really rules the throne.")
            }
            items(ranked.drop(3)) { u ->
                val rank = ranked.indexOf(u) + 1
                RankRow(rank, u, "${m.value(u)}${m.unit}", u.id == state.me?.id)
            }
        }
    }
}

@Composable
private fun Podium(top: List<UserDto>, m: Metric, meId: Long?) {
    val order = listOfNotNull(top.getOrNull(1), top.getOrNull(0), top.getOrNull(2))
    GlassCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
            order.forEach { u ->
                val place = top.indexOf(u) + 1
                val height: Dp = when (place) { 1 -> 110.dp; 2 -> 80.dp; else -> 60.dp }
                val medal = when (place) { 1 -> "🥇"; 2 -> "🥈"; else -> "🥉" }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    if (place == 1) Text("👑", fontSize = 22.sp)
                    Avatar(u.avatar, if (place == 1) 64.dp else 52.dp, ring = place == 1, photo = rememberUserPhoto(u.id, u.photoVersion))
                    Text(if (u.id == meId) "You" else u.displayName, fontWeight = FontWeight.Bold, maxLines = 1,
                        style = MaterialTheme.typography.bodyMedium)
                    Text("${m.emoji} ${m.value(u)}${m.unit}", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier.fillMaxWidth(0.8f).height(height)
                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                            .background(
                                if (place == 1) accentBrush()
                                else androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primaryContainer)
                            ),
                        contentAlignment = Alignment.TopCenter
                    ) { Text(medal, fontSize = 26.sp, modifier = Modifier.padding(top = 8.dp)) }
                }
            }
        }
    }
}

@Composable
private fun RankRow(rank: Int, u: UserDto, value: String, me: Boolean) {
    val cs = MaterialTheme.colorScheme
    GlassCard(padding = 12.dp, tint = if (me) cs.primaryContainer.copy(alpha = 0.7f) else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("#$rank", fontWeight = FontWeight.Black, modifier = Modifier.width(40.dp))
            Avatar(u.avatar, 38.dp, photo = rememberUserPhoto(u.id, u.photoVersion))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(if (me) "You" else u.displayName, fontWeight = FontWeight.Bold)
                Text("Lv ${u.level} · @${u.username}", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
            Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        }
    }
}

// ───────────────────────── friends ─────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FriendsSection(vm: MainVm, state: SocialState, prefs: Prefs) {
    var add by rememberSaveable { mutableStateOf("") }
    var confirmRemove by remember { mutableStateOf<UserDto?>(null) }
    val clipboard = LocalClipboardManager.current
    val cs = MaterialTheme.colorScheme

    PullToRefreshBox(isRefreshing = state.loading, onRefresh = { vm.refreshSocial() }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 130.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                GlassCard {
                    Text("Your handle", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("@${prefs.username ?: state.me?.username ?: ""}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { clipboard.setText(AnnotatedString("@${prefs.username}")) }) {
                            Icon(Icons.Rounded.ContentCopy, "Copy")
                        }
                    }
                    Text("Share it so friends can add you.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            add, { add = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' }.take(20) },
                            placeholder = { Text("friend_username") }, prefix = { Text("@") }, singleLine = true,
                            modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        FilledIconButton(onClick = { vm.addFriend(add) { ok -> if (ok) add = "" } }, enabled = add.length >= 3,
                            modifier = Modifier.size(52.dp)) {
                            Icon(Icons.Rounded.PersonAdd, "Add friend")
                        }
                    }
                }
            }
            if (state.friends.incoming.isNotEmpty()) {
                item { SectionTitle("Requests") }
                items(state.friends.incoming, key = { "in${it.id}" }) { r ->
                    PersonRow(r.user, "wants to join your squad") {
                        TextButton(onClick = { vm.decline(r.id) }) { Text("Decline") }
                        Button(onClick = { vm.accept(r.id) }) { Text("Accept") }
                    }
                }
            }
            if (state.friends.outgoing.isNotEmpty()) {
                item { SectionTitle("Pending") }
                items(state.friends.outgoing, key = { "out${it.id}" }) { r ->
                    PersonRow(r.user, "request sent") {
                        TextButton(onClick = { vm.decline(r.id) }) { Text("Cancel") }
                    }
                }
            }
            item { SectionTitle("Squad · ${state.friends.friends.size}") }
            if (state.loaded && state.friends.friends.isEmpty()) item {
                EmptyState("🫂", "No friends yet", "Add someone by username. Greatness is better with witnesses.")
            }
            items(state.friends.friends, key = { "f${it.id}" }) { f ->
                PersonRow(f, "🔥 ${f.currentStreak}d streak · Lv ${f.level}", onLongClick = { confirmRemove = f }) {
                    FilledTonalButton(onClick = { vm.nudge(f.id, f.displayName) }) { Text("👉 Nudge") }
                }
            }
            if (state.friends.friends.isNotEmpty()) item {
                Text("Tip: tap a friend's name to remove them.", style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp))
            }
            item {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { vm.signOut() }, modifier = Modifier.fillMaxWidth()) { Text("Sign out of Squad") }
            }
        }
    }

    confirmRemove?.let { f ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Remove ${f.displayName}?") },
            text = { Text("They'll disappear from your feed and leaderboard. You can add them again later.") },
            confirmButton = { TextButton(onClick = { vm.unfriend(f.id); confirmRemove = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Keep") } }
        )
    }
}

@Composable
private fun PersonRow(u: UserDto, sub: String, onLongClick: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit) {
    GlassCard(padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(u.avatar, 42.dp, photo = rememberUserPhoto(u.id, u.photoVersion))
            Spacer(Modifier.width(10.dp))
            Column(
                Modifier.weight(1f).then(if (onLongClick != null) Modifier.clickable(onClick = onLongClick) else Modifier)
            ) {
                Text(u.displayName, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), content = actions)
        }
    }
}
