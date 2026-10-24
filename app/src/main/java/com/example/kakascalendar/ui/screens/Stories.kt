package com.example.kakascalendar.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.kakascalendar.MainVm
import com.example.kakascalendar.data.Prefs
import com.example.kakascalendar.domain.timeAgo
import com.example.kakascalendar.social.StoryDto
import com.example.kakascalendar.social.StoryGroup
import com.example.kakascalendar.social.UserDto
import com.example.kakascalendar.ui.components.*

/** Background gradients for text stories (index = server "bg" field). */
val STORY_BACKGROUNDS: List<List<Color>> = listOf(
    listOf(Color(0xFF9A5B34), Color(0xFFF2C66D)),
    listOf(Color(0xFF6A4CC2), Color(0xFFF08AC6)),
    listOf(Color(0xFF2465B0), Color(0xFF86DEC4)),
    listOf(Color(0xFFC0364A), Color(0xFFFFC46B)),
    listOf(Color(0xFF23806A), Color(0xFFB6DE6A)),
    listOf(Color(0xFF1D3B8F), Color(0xFFD62839)),
    listOf(Color(0xFF111111), Color(0xFF444444)),
    listOf(Color(0xFFFF6F91), Color(0xFFFFC75F))
)
val STORY_EMOJIS = listOf("💩", "🔥", "👑", "🍌", "🚽", "🧻", "😂", "😎", "🤯", "💪", "🎉", "🙏", "🌋", "👻", "💦", "🏆")

fun storyBrush(bg: Int) = Brush.linearGradient(STORY_BACKGROUNDS[bg.coerceIn(0, STORY_BACKGROUNDS.lastIndex)])

// ───────────────────────── Rings row ─────────────────────────

@Composable
fun StoriesRow(groups: List<StoryGroup>, me: UserDto?, prefs: Prefs, onOpen: (Int) -> Unit, onAdd: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val mineIdx = groups.indexOfFirst { it.user.id == me?.id }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "me") {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(70.dp)) {
                Box {
                    StoryRing(active = mineIdx >= 0, seen = false, onClick = { if (mineIdx >= 0) onOpen(mineIdx) else onAdd() }) {
                        MyAvatar(prefs, size = 60.dp)
                    }
                    Box(
                        Modifier.align(Alignment.BottomEnd).size(24.dp).clip(CircleShape)
                            .background(cs.primary).border(2.dp, cs.background, CircleShape).clickable(onClick = onAdd),
                        contentAlignment = Alignment.Center
                    ) { Text("+", color = cs.onPrimary, fontWeight = FontWeight.Black, fontSize = 15.sp) }
                }
                Text("Your story", style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
        items(groups.withIndex().filter { it.value.user.id != me?.id }, key = { it.value.user.id }) { (i, g) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(70.dp)) {
                StoryRing(active = true, seen = g.allSeen, onClick = { onOpen(i) }) {
                    Avatar(g.user.avatar, 60.dp, photo = rememberUserPhoto(g.user.id, g.user.photoVersion))
                }
                Text(g.user.displayName, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}

@Composable
private fun StoryRing(active: Boolean, seen: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val brush = when {
        !active -> Brush.linearGradient(listOf(cs.outlineVariant, cs.outlineVariant))
        seen -> Brush.linearGradient(listOf(cs.outline.copy(alpha = 0.5f), cs.outline.copy(alpha = 0.5f)))
        else -> Brush.linearGradient(listOf(Color(0xFFFFC75F), Color(0xFFFF6F91), Color(0xFF9A5B34)))
    }
    Box(
        Modifier.size(70.dp).clip(CircleShape).border(3.dp, brush, CircleShape).padding(5.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

// ───────────────────────── Viewer ─────────────────────────

private const val STORY_MS = 5_000

@Composable
fun StoryViewer(vm: MainVm, groups: List<StoryGroup>, startGroup: Int, meId: Long?, onClose: () -> Unit) {
    var gi by remember { mutableIntStateOf(startGroup.coerceIn(0, (groups.size - 1).coerceAtLeast(0))) }
    var si by remember(gi) {
        // jump to the first unseen story of the group
        mutableIntStateOf(groups.getOrNull(gi)?.stories?.indexOfFirst { !it.seen }?.takeIf { it >= 0 } ?: 0)
    }
    val group = groups.getOrNull(gi)
    val story = group?.stories?.getOrNull(si)
    if (group == null || story == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val mine = group.user.id == meId
    var paused by remember { mutableStateOf(false) }
    var viewers by remember(story.id) { mutableStateOf<List<UserDto>?>(null) }
    val progress = remember(story.id) { Animatable(0f) }

    fun next() {
        when {
            si < group.stories.lastIndex -> si++
            gi < groups.lastIndex -> gi++
            else -> onClose()
        }
    }
    fun prev() {
        when {
            si > 0 -> si--
            gi > 0 -> gi--
        }
    }

    LaunchedEffect(story.id) { vm.viewStory(story) }
    LaunchedEffect(story.id, paused, viewers) {
        if (paused || viewers != null) return@LaunchedEffect
        val remaining = ((1f - progress.value) * STORY_MS).toInt()
        progress.animateTo(1f, tween(remaining, easing = LinearEasing))
        next()
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(
            Modifier.fillMaxSize().background(Color.Black)
                .pointerInput(story.id) {
                    detectTapGestures(
                        onPress = {
                            paused = true
                            tryAwaitRelease()
                            paused = false
                        },
                        onTap = { offset -> if (offset.x < size.width / 3) prev() else next() }
                    )
                }
        ) {
            StoryContent(story, Modifier.fillMaxSize())

            // top: progress bars + header
            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    group.stories.forEachIndexed { i, _ ->
                        val f = when {
                            i < si -> 1f
                            i == si -> progress.value
                            else -> 0f
                        }
                        Box(Modifier.weight(1f).height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.35f))) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(f).background(Color.White))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(group.user.avatar, 36.dp, photo = rememberUserPhoto(group.user.id, group.user.photoVersion))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (mine) "Your story" else group.user.displayName, color = Color.White, fontWeight = FontWeight.Bold)
                        Text(timeAgo(story.createdAt), color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(onClick = onClose) { Text("✕", color = Color.White, fontSize = 22.sp) }
                }
            }

            // bottom: own story tools
            if (mine) {
                Row(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { vm.storyViewers(story.id) { viewers = it } }) {
                        Text("👁 ${story.views ?: 0} ${if ((story.views ?: 0) == 1) "view" else "views"}", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { vm.deleteStory(story.id); onClose() }) { Text("🗑 Delete", color = Color.White) }
                }
            }
        }

        viewers?.let { list ->
            AlertDialog(
                onDismissRequest = { viewers = null },
                title = { Text("Seen by ${list.size}") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (list.isEmpty()) Text("Nobody yet. They're missing out.")
                        list.forEach { u ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Avatar(u.avatar, 34.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(u.displayName, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { viewers = null }) { Text("Close") } }
            )
        }
    }
}

@Composable
private fun StoryContent(story: StoryDto, modifier: Modifier) {
    if (story.kind == "photo") {
        val auth = LocalPhotoAuth.current
        val ctx = LocalContext.current
        val req = remember(story.id, auth) {
            ImageRequest.Builder(ctx)
                .data("${auth.server.trimEnd('/')}/stories/${story.id}/image")
                .crossfade(true)
                .apply { auth.token?.let { addHeader("Authorization", "Bearer $it") } }
                .build()
        }
        Box(modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White.copy(alpha = 0.5f))
            AsyncImage(model = req, contentDescription = "Story", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            if (story.text.isNotBlank()) {
                Text(
                    story.text, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 110.dp, start = 20.dp, end = 20.dp)
                        .clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.45f)).padding(12.dp)
                )
            }
        }
    } else {
        TextStoryCard(story.text, story.bg, story.emoji, modifier)
    }
}

@Composable
fun TextStoryCard(text: String, bg: Int, emoji: String, modifier: Modifier) {
    Column(
        modifier.background(storyBrush(bg)).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (emoji.isNotBlank()) Text(emoji, fontSize = 96.sp)
        if (text.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Text(text, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, lineHeight = 32.sp)
        }
    }
}

// ───────────────────────── Composer ─────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StoryComposer(vm: MainVm, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var mode by remember { mutableIntStateOf(0) }          // 0 text, 1 photo, 2 today's log
    var text by remember { mutableStateOf("") }
    var bg by remember { mutableIntStateOf(0) }
    var emoji by remember { mutableStateOf("💩") }
    var photo by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) photo = uri }

    LaunchedEffect(mode) {
        if (mode == 2) { text = vm.todayStoryText(); emoji = vm.todayStoryEmoji(); bg = 0 }
        if (mode == 1 && photo == null) picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = cs.surfaceContainerLow) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("New story 📖", style = MaterialTheme.typography.headlineSmall)
            Text("Visible to your squad for 24 hours.", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("✍️ Text", "📸 Photo", "💩 Today").forEachIndexed { i, label ->
                    SegmentedButton(selected = mode == i, onClick = { mode = i }, shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text(label) }
                }
            }

            // live preview
            Box(Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(24.dp)).background(Color.Black)) {
                if (mode == 1) {
                    if (photo != null) {
                        AsyncImage(model = photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Text("Tap “Choose photo”", color = Color.White, modifier = Modifier.align(Alignment.Center))
                    }
                    if (text.isNotBlank()) Text(
                        text, color = Color.White, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
                            .clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.45f)).padding(10.dp)
                    )
                } else {
                    TextStoryCard(text, bg, emoji, Modifier.fillMaxSize())
                }
            }

            if (mode == 1) {
                OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.fillMaxWidth()) { Text(if (photo == null) "📸 Choose photo" else "🔄 Change photo") }
            }

            OutlinedTextField(
                text, { text = it.take(300) },
                label = { Text(if (mode == 1) "Caption (optional)" else "What's on your mind?") },
                modifier = Modifier.fillMaxWidth(), minLines = 2, shape = RoundedCornerShape(16.dp)
            )

            if (mode != 1) {
                Text("Background", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    STORY_BACKGROUNDS.indices.forEach { i ->
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(storyBrush(i))
                                .then(if (bg == i) Modifier.border(3.dp, cs.onSurface, CircleShape) else Modifier)
                                .clickable { bg = i }
                        )
                    }
                }
                Text("Sticker", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    (listOf("") + STORY_EMOJIS).forEach { e ->
                        Box(
                            Modifier.size(42.dp).clip(CircleShape)
                                .background(if (emoji == e) cs.primaryContainer else cs.surfaceContainerHigh)
                                .clickable { emoji = e },
                            contentAlignment = Alignment.Center
                        ) { Text(e.ifEmpty { "∅" }, fontSize = 20.sp) }
                    }
                }
            }

            val canPost = !busy && if (mode == 1) photo != null else (text.isNotBlank() || emoji.isNotBlank())
            GradientButton(if (busy) "Posting…" else "Share story", Modifier.fillMaxWidth(), enabled = canPost) {
                busy = true
                val done: (Boolean) -> Unit = { ok -> busy = false; if (ok) onDismiss() }
                if (mode == 1) photo?.let { vm.postPhotoStory(it, text, done) } else vm.postTextStory(text, bg, emoji, done)
            }
        }
    }
}
