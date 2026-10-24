package com.example.kakascalendar

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.kakascalendar.data.Entry
import com.example.kakascalendar.data.Prefs
import com.example.kakascalendar.domain.*
import com.example.kakascalendar.social.FeedItem
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
    val loaded: Boolean = false
) {
    val loggedToday get() = LocalDate.now() in days
}

sealed interface UiEvent {
    data class Unlocked(val achievement: Achievement) : UiEvent
    data class LevelUp(val level: LevelInfo) : UiEvent
    data class Message(val text: String) : UiEvent
    data class Deleted(val entry: Entry) : UiEvent
}

private fun buildDash(e: List<Entry>, friends: Int): Dash {
    val today = LocalDate.now()
    val days = daysOf(e)
    val streak = streakDates(days, today)
    val ach = achievements(e, friends)
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
        insights = insights(e, today),
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

    val dash: StateFlow<Dash> = combine(
        dao.all(),
        prefsRepo.flow.map { it.friendsCount }.distinctUntilChanged()
    ) { e, f -> buildDash(e, f) }
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
                if (d.level.level > p.lastLevel) {
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

    fun save(entry: Entry) = viewModelScope.launch {
        val isNew = entry.id == 0L
        dao.upsert(entry)
        StreakWidget.refresh(getApplication())
        if (isNew && System.currentTimeMillis() - entry.timestamp < 6 * 3_600_000L) {
            launch { social.postEvent("log", mapOf("type" to "${entry.type}")) }
        }
    }

    fun quickLog() {
        save(Entry(timestamp = System.currentTimeMillis(), type = 4, tags = ""))
        msg("🍌 Quick-logged a perfect Type 4")
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

    fun setProfile(name: String? = null, avatar: String? = null) = viewModelScope.launch {
        prefsRepo.setProfile(name?.trim()?.ifEmpty { null }, avatar)
        social.updateProfile(displayName = name?.trim()?.ifEmpty { null }, avatar = avatar)
    }

    fun setAppearance(mode: Int? = null, accent: Int? = null, dynamic: Boolean? = null) = viewModelScope.launch {
        prefsRepo.setAppearance(mode, accent, dynamic)
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
