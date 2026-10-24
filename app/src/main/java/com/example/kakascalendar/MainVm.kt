package com.example.kakascalendar

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.kakascalendar.data.Entry
import com.example.kakascalendar.data.Prefs
import com.example.kakascalendar.data.WaterDay
import com.example.kakascalendar.system.BragCard
import com.example.kakascalendar.system.Photos
import com.example.kakascalendar.system.ShareCard
import com.example.kakascalendar.domain.*
import com.example.kakascalendar.social.FeedItem
import com.example.kakascalendar.social.PostStoryReq
import com.example.kakascalendar.social.StoryDto
import com.example.kakascalendar.social.UserDto
import com.example.kakascalendar.social.friendlyError
import com.example.kakascalendar.social.StatsReq
import com.example.kakascalendar.system.Reminders
import com.example.kakascalendar.system.StreakWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class Dash(
    val entries: List<Entry> = emptyList(),
    val days: Set<LocalDate> = emptySet(),
    val streakDays: Set<LocalDate> = emptySet(),
    val streak: Int = 0,
    val best: Int = 0,
    val todayCount: Int = 0,
    val weekCount: Int = 0,
    val achievements: List<Achievement> = emptyList(),
    val level: LevelInfo = levelFor(0),
    val insights: List<Insight> = emptyList(),
    val water: Map<LocalDate, Int> = emptyMap(),
    val waterGoal: Int = 8,
    val loaded: Boolean = false
) {
    val loggedToday get() = LocalDate.now() in days
    val waterToday get() = water[LocalDate.now()] ?: 0
}

sealed interface UiEvent {
    data class Unlocked(val achievement: Achievement) : UiEvent
    data class LevelUp(val level: LevelInfo) : UiEvent
    data class Message(val text: String) : UiEvent
    data class Deleted(val entry: Entry) : UiEvent
}

private fun buildDash(e: List<Entry>, friends: Int, waterRows: List<WaterDay>, waterGoal: Int): Dash {
    val today = LocalDate.now()
    val days = daysOf(e)
    val streak = streakDates(days, today)
    val water = waterRows.mapNotNull { w -> runCatching { LocalDate.parse(w.date) to w.glasses }.getOrNull() }.toMap()
    val ach = achievements(e, friends, water.count { it.value >= waterGoal })
    return Dash(
        entries = e,
        days = days,
        streakDays = streak,
        streak = streak.size,
        best = longestStreak(days),
        todayCount = e.count { it.date() == today },
        weekCount = e.count { !it.date().isBefore(today.minusDays(6)) },
        achievements = ach,
        level = levelFor(xpFor(e, ach)),
        insights = insights(e, today, water, waterGoal),
        water = water,
        waterGoal = waterGoal,
        loaded = true
    )
}

@OptIn(FlowPreview::class)
class MainVm(app: Application) : AndroidViewModel(app) {
    private val kapp = app as KakaApp
    private val dao = kapp.db.dao()
    private val prefsRepo = kapp.prefs
    private val social = kapp.social

    val prefs: StateFlow<Prefs?> = prefsRepo.flow.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val waterDao = kapp.db.water()

    val dash: StateFlow<Dash> = combine(
        dao.all(),
        waterDao.all(),
        prefsRepo.flow.map { it.friendsCount to it.waterGoal }.distinctUntilChanged()
    ) { e, w, (f, goal) -> buildDash(e, f, w, goal) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, Dash())

    val socialState = social.state

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<UiEvent> = _events

    init {
        // Achievement + level-up detection (persisted, so it fires exactly once per unlock)
        viewModelScope.launch {
            dash.filter { it.loaded }.collect { d ->
                val p = prefsRepo.current()
                val unlocked = d.achievements.filter { it.unlocked }.map { it.id }.toSet()
                if (!p.ranksV2) {
                    // New rank ladder: re-baseline silently so nobody gets a fake "rank up"
                    prefsRepo.setLastLevel(d.level.level)
                }
                if (!p.achievementsInit) {
                    prefsRepo.setSeen(unlocked)
                    prefsRepo.setLastLevel(d.level.level)
                    return@collect
                }
                val fresh = d.achievements.filter { it.unlocked && it.id !in p.seenAchievements }
                if (fresh.isNotEmpty()) {
                    prefsRepo.setSeen(p.seenAchievements + fresh.map { it.id })
                    fresh.forEach { a ->
                        _events.emit(UiEvent.Unlocked(a))
                        launch { social.postEvent("achievement", mapOf("title" to a.title, "emoji" to a.emoji)) }
                    }
                }
                if (p.ranksV2 && d.level.level > p.lastLevel) {
                    prefsRepo.setLastLevel(d.level.level)
                    if (p.lastLevel > 0) {
                        _events.emit(UiEvent.LevelUp(d.level))
                        launch { social.postEvent("level", mapOf("level" to "${d.level.level}", "title" to d.level.title)) }
                    }
                }
            }
        }
        // Keep the Squad leaderboard fresh
        viewModelScope.launch {
            dash.filter { it.loaded }
                .map { StatsReq(it.streak, it.best, it.entries.size, it.weekCount, it.level.xp, it.level.level) }
                .distinctUntilChanged()
                .debounce(2_000)
                .collect { if (prefsRepo.current().signedIn) social.pushStats(it) }
        }
        viewModelScope.launch { if (prefsRepo.current().signedIn) social.refresh() }
    }

    private fun msg(text: String) { _events.tryEmit(UiEvent.Message(text)) }

    // ───────── entries ─────────

    /** Entries already on that calendar day (excluding the one being edited). */
    private fun countOnDay(entry: Entry): Int {
        val day = entry.date()
        return dash.value.entries.count { it.id != entry.id && it.date() == day }
    }

    /** Saves the entry. Returns false (and explains why) if that day is already full. */
    fun save(entry: Entry): Boolean {
        if (countOnDay(entry) >= MAX_LOGS_PER_DAY) {
            msg("🚫 That day already has $MAX_LOGS_PER_DAY logs. Even legends have limits.")
            return false
        }
        viewModelScope.launch {
            val isNew = entry.id == 0L
            dao.upsert(entry)
            StreakWidget.refresh(getApplication())
            if (isNew && System.currentTimeMillis() - entry.timestamp < 6 * 3_600_000L) {
                launch { social.postEvent("log", mapOf("type" to "${entry.type}")) }
            }
        }
        return true
    }

    fun quickLog() {
        if (save(Entry(timestamp = System.currentTimeMillis(), type = 4, tags = ""))) msg("🍌 Quick-logged a perfect Type 4")
    }

    fun delete(entry: Entry) = viewModelScope.launch {
        dao.delete(entry)
        StreakWidget.refresh(getApplication())
        _events.emit(UiEvent.Deleted(entry))
    }

    fun restore(entry: Entry) = viewModelScope.launch {
        dao.upsert(entry)
        StreakWidget.refresh(getApplication())
    }

    fun deleteAll() = viewModelScope.launch {
        dao.clear()
        StreakWidget.refresh(getApplication())
        msg("All entries deleted")
    }

    // ───────── throne timer ─────────

    fun startTimer() = viewModelScope.launch { prefsRepo.setTimer(System.currentTimeMillis()) }
    fun cancelTimer() = viewModelScope.launch { prefsRepo.setTimer(0L) }

    /** Stops the timer and returns the elapsed seconds. */
    fun stopTimer(): Int {
        val start = prefs.value?.timerStart ?: 0L
        viewModelScope.launch { prefsRepo.setTimer(0L) }
        return if (start <= 0) 0 else ((System.currentTimeMillis() - start) / 1000).toInt().coerceIn(1, 3 * 3600)
    }

    // ───────── settings ─────────

    fun finishOnboarding(name: String, avatar: String) = viewModelScope.launch {
        prefsRepo.finishOnboarding(name.trim().ifEmpty { "Champion" }, avatar)
    }

    fun setProfile(name: String? = null, avatar: String? = null, avatarBg: Int? = null) = viewModelScope.launch {
        val hadPhoto = prefsRepo.current().avatarPhoto != null
        prefsRepo.setProfile(name?.trim()?.ifEmpty { null }, avatar, avatarBg)
        if (avatar != null && hadPhoto) {
            withContext(Dispatchers.IO) { Photos.deleteAvatar(getApplication()) }
            social.syncPhoto(null)
        }
        if (name != null || avatar != null) social.updateProfile(displayName = name?.trim()?.ifEmpty { null }, avatar = avatar)
    }

    /** Crops, saves and (if signed in) uploads a photo picked from the gallery. */
    fun setAvatarPhoto(uri: Uri) = viewModelScope.launch {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val bmp = Photos.loadSquare(getApplication(), uri) ?: error("unreadable")
                val path = Photos.saveAvatar(getApplication(), bmp)
                val upload = Photos.toUploadBase64(bmp)
                bmp.recycle()
                path to upload
            }
        }
        result.onSuccess { (path, upload) ->
            prefsRepo.setAvatarPhoto(path)
            msg("📸 Looking good")
            social.syncPhoto(upload)?.let { msg("Saved locally, but Squad upload failed: $it") }
        }.onFailure {
            android.util.Log.e("KakasPhoto", "avatar failed", it)
            msg("Couldn't read that image (${it.javaClass.simpleName}: ${it.message}).")
        }
    }

    fun removeAvatarPhoto() = viewModelScope.launch {
        withContext(Dispatchers.IO) { Photos.deleteAvatar(getApplication()) }
        prefsRepo.setAvatarPhoto(null)
        social.syncPhoto(null)
    }

    fun setAppearance(
        mode: Int? = null, accent: Int? = null, dynamic: Boolean? = null, hue: Float? = null,
        font: Int? = null, cardStyle: Int? = null, animatedBg: Boolean? = null
    ) = viewModelScope.launch {
        prefsRepo.setAppearance(mode, accent, dynamic, hue, font, cardStyle, animatedBg)
    }

    fun setBehavior(haptics: Boolean? = null, use24h: Boolean? = null, weekSunday: Boolean? = null, waterGoal: Int? = null) =
        viewModelScope.launch { prefsRepo.setBehavior(haptics, use24h, weekSunday, waterGoal?.coerceIn(1, 20)) }

    fun setSectionVisible(section: String, visible: Boolean) = viewModelScope.launch {
        prefsRepo.setSectionVisible(section, visible)
    }

    fun addCustomTag(raw: String) = viewModelScope.launch {
        val tag = raw.trim().replace(",", " ").take(24)
        if (tag.isEmpty()) return@launch
        val cur = prefsRepo.current().customTags
        if (tag in cur || tag in TAGS) { msg("You already have that tag"); return@launch }
        prefsRepo.setCustomTags(cur + tag)
    }

    fun removeCustomTag(tag: String) = viewModelScope.launch {
        prefsRepo.setCustomTags(prefsRepo.current().customTags - tag)
    }

    // ───────── water ─────────

    fun addWater(delta: Int) = viewModelScope.launch {
        val key = LocalDate.now().toString()
        val cur = waterDao.get(key)?.glasses ?: 0
        val next = (cur + delta).coerceIn(0, 40)
        waterDao.upsert(WaterDay(key, next))
        val goal = prefsRepo.current().waterGoal
        if (delta > 0 && cur < goal && next >= goal) msg("💧 Water goal smashed!")
    }

    // ───────── brag card ─────────

    fun shareBrag(colors: List<Int>) = viewModelScope.launch {
        val d = dash.value
        val p = prefsRepo.current()
        val card = BragCard(
            name = p.name.ifBlank { "Champion" }, avatar = p.avatar, streak = d.streak, best = d.best,
            total = d.entries.size, level = d.level.level, levelTitle = d.level.title,
            trophies = d.achievements.count { it.unlocked }, trophiesTotal = d.achievements.size, colors = colors
        )
        runCatching { ShareCard.share(getApplication(), card) }
            .onFailure { msg("Couldn't create the image: ${it.message}") }
    }

    fun setReminder(enabled: Boolean, hour: Int, minute: Int) = viewModelScope.launch {
        prefsRepo.setReminder(enabled, hour, minute)
        if (enabled) {
            Reminders.schedule(getApplication(), hour, minute)
            msg("⏰ Daily reminder set for %d:%02d".format(hour, minute))
        } else Reminders.cancel(getApplication())
    }

    fun setLock(enabled: Boolean) = viewModelScope.launch { prefsRepo.setLock(enabled) }

    fun setShareLevel(level: String) = viewModelScope.launch {
        prefsRepo.setShareLevel(level)
        social.updateProfile(shareLevel = level)?.let { msg(it) }
    }

    fun setServer(url: String) = viewModelScope.launch { prefsRepo.setServer(url.trim().trimEnd('/')) }

    // ───────── data import / export ─────────

    fun exportBackup(uri: Uri) = viewModelScope.launch {
        val r = runCatching {
            val json = ApiJsonPretty.encodeToString(Backup.serializer(), Backup(entries = dash.value.entries.map { it.toBackup() }))
            withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                    ?: error("Couldn't open file")
            }
        }
        msg(if (r.isSuccess) "💾 Backup saved (${dash.value.entries.size} entries)" else "Backup failed: ${r.exceptionOrNull()?.message}")
    }

    fun importBackup(uri: Uri) = viewModelScope.launch {
        val r = runCatching {
            val text = withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                    ?: error("Couldn't open file")
            }
            val backup = ApiJsonPretty.decodeFromString(Backup.serializer(), text)
            val existing = dash.value.entries.map { it.timestamp to it.type }.toSet()
            val fresh = backup.entries.map { it.toEntry() }.filter { (it.timestamp to it.type) !in existing }
            dao.upsertAll(fresh)
            fresh.size
        }
        StreakWidget.refresh(getApplication())
        msg(r.fold({ "📥 Restored $it new entries" }, { "Restore failed: not a Kakascalendar backup" }))
    }

    // ───────── squad ─────────

    fun authenticate(server: String, username: String, password: String, register: Boolean, onDone: (String?) -> Unit) =
        viewModelScope.launch {
            val p = prefsRepo.current()
            val err = social.authenticate(server, username, password, register, p.name, p.avatar, p.shareLevel)
            onDone(err)
            if (err == null) p.avatarPhoto?.let { path ->
                withContext(Dispatchers.IO) {
                    runCatching {
                        android.graphics.BitmapFactory.decodeFile(path)?.let { bmp -> Photos.toUploadBase64(bmp).also { bmp.recycle() } }
                    }.getOrNull()
                }?.let { social.syncPhoto(it) }
            }
            if (err == null) msg(if (register) "🎉 Welcome to the Squad!" else "👋 Welcome back!")
        }

    fun signOut() = viewModelScope.launch { social.signOut(); msg("Signed out of your Squad") }

    fun refreshSocial(quiet: Boolean = false) = viewModelScope.launch {
        val err = social.refresh()
        if (err != null && !quiet) msg(err)
    }

    fun react(item: FeedItem, emoji: String) = viewModelScope.launch { social.react(item, emoji)?.let { msg(it) } }

    fun addFriend(username: String, onDone: (Boolean) -> Unit) = viewModelScope.launch {
        val err = social.addFriend(username)
        msg(err ?: "🤝 Friend request sent to @${username.trim().removePrefix("@")}")
        onDone(err == null)
    }

    fun addFriendById(id: Long, name: String) = viewModelScope.launch {
        msg(social.addFriendById(id) ?: "🤝 Request sent to $name")
    }

    // ───────── stories ─────────

    fun postTextStory(text: String, bg: Int, emoji: String, onDone: (Boolean) -> Unit) = viewModelScope.launch {
        val err = social.postStory(PostStoryReq(kind = "text", text = text.trim(), bg = bg, emoji = emoji))
        msg(err ?: "📖 Story posted. It disappears in 24 hours.")
        onDone(err == null)
    }

    fun postPhotoStory(uri: Uri, caption: String, onDone: (Boolean) -> Unit) = viewModelScope.launch {
        val b64 = withContext(Dispatchers.IO) {
            runCatching { Photos.loadScaled(getApplication(), uri)?.let { bmp -> Photos.storyBase64(bmp).also { bmp.recycle() } } }
                .onFailure { android.util.Log.e("KakasPhoto", "story failed", it) }.getOrNull()
        }
        if (b64 == null) { msg("Couldn't read that image. Try another one."); onDone(false); return@launch }
        val err = social.postStory(PostStoryReq(kind = "photo", text = caption.trim(), image = b64))
        msg(err ?: "📸 Story posted. It disappears in 24 hours.")
        onDone(err == null)
    }

    fun viewStory(story: StoryDto) = viewModelScope.launch { social.viewStory(story) }

    fun deleteStory(id: Long) = viewModelScope.launch { msg(social.deleteStory(id) ?: "Story deleted") }

    fun storyViewers(id: Long, onResult: (List<UserDto>) -> Unit) = viewModelScope.launch {
        val r = social.storyViewers(id)
        r.exceptionOrNull()?.let { msg(friendlyError(it)) } ?: onResult(r.getOrDefault(emptyList()))
    }

    /** Text for the "today's log" story template. */
    fun todayStoryText(): String {
        val d = dash.value
        val last = d.entries.firstOrNull { it.date() == java.time.LocalDate.now() } ?: d.entries.firstOrNull()
        val parts = mutableListOf<String>()
        if (last != null) {
            val t = last.stool()
            parts += "Type ${t.n} · ${t.label}"
            if (last.rating > 0) parts += "felt ${RATING_LABELS[last.rating - 1].lowercase()}"
            if (last.wipes > 0) parts += "${last.wipes} wipes"
        }
        if (d.streak > 0) parts += "🔥 ${d.streak}-day streak"
        parts += "Lv ${d.level.level} ${d.level.title}"
        return parts.joinToString(" · ")
    }

    fun todayStoryEmoji(): String = dash.value.entries.firstOrNull()?.stool()?.emoji ?: "💩"

    fun accept(id: Long) = viewModelScope.launch { social.accept(id)?.let { msg(it) } ?: msg("🤝 You're now friends") }
    fun decline(id: Long) = viewModelScope.launch { social.decline(id)?.let { msg(it) } }
    fun unfriend(id: Long) = viewModelScope.launch { social.unfriend(id)?.let { msg(it) } }
    fun nudge(id: Long, name: String) = viewModelScope.launch {
        msg(social.nudge(id) ?: "👉 Nudged $name. They'll see it in their feed.")
    }
}

val ApiJsonPretty = kotlinx.serialization.json.Json {
    ignoreUnknownKeys = true
    prettyPrint = true
    encodeDefaults = true
}
