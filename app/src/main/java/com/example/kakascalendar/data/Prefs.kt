package com.example.kakascalendar.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

/** Default server: 10.0.2.2 is "your computer" as seen from the Android emulator. */
const val DEFAULT_SERVER = "http://10.0.2.2:8000"

/** Private builds bake in their server; then the address can't be changed in the app. */
val FIXED_SERVER: String? = com.example.kakascalendar.BuildConfig.SERVER_URL.ifBlank { null }

/** Index of the "Custom" accent (uses [Prefs.customHue]). */
const val CUSTOM_ACCENT = 6

/** Home sections that can be hidden. */
object HomeSection {
    const val QUICK = "quick"
    const val STATS = "stats"
    const val WATER = "water"
    const val INSIGHT = "insight"
}

data class Prefs(
    val onboarded: Boolean = false,
    val name: String = "",
    val avatar: String = "💩",
    val avatarPhoto: String? = null,     // absolute path to the local photo, null = use emoji
    val avatarBg: Int = 0,               // index into AVATAR_BGS, 0 = theme color
    val themeMode: Int = 0,              // 0 system, 1 light, 2 dark
    val accent: Int = 0,
    val customHue: Float = 28f,
    val dynamicColor: Boolean = false,
    val font: Int = 0,                   // index into AppFont
    val cardStyle: Int = 0,              // 0 glass, 1 solid, 2 outline
    val animatedBg: Boolean = true,
    val haptics: Boolean = true,
    val use24h: Boolean = false,
    val weekStartsSunday: Boolean = false,
    val hiddenSections: Set<String> = emptySet(),
    val customTags: List<String> = emptyList(),
    val waterGoal: Int = 8,
    val reminder: Boolean = false,
    val reminderHour: Int = 20,
    val reminderMinute: Int = 0,
    val lock: Boolean = false,
    val serverUrl: String = DEFAULT_SERVER,
    val token: String? = null,
    val username: String? = null,
    val shareLevel: String = "stats",    // full | stats | private
    val seenAchievements: Set<String> = emptySet(),
    val achievementsInit: Boolean = false,
    val lastLevel: Int = 0,
    val ranksV2: Boolean = false,
    val timerStart: Long = 0L,
    val friendsCount: Int = 0
) {
    val signedIn get() = token != null
    fun shows(section: String) = section !in hiddenSections
}

class PrefsRepo(private val ctx: Context) {
    private object K {
        val onboarded = booleanPreferencesKey("onboarded")
        val name = stringPreferencesKey("name")
        val avatar = stringPreferencesKey("avatar")
        val avatarPhoto = stringPreferencesKey("avatar_photo")
        val avatarBg = intPreferencesKey("avatar_bg")
        val themeMode = intPreferencesKey("theme_mode")
        val accent = intPreferencesKey("accent")
        val customHue = floatPreferencesKey("custom_hue")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val font = intPreferencesKey("font")
        val cardStyle = intPreferencesKey("card_style")
        val animatedBg = booleanPreferencesKey("animated_bg")
        val haptics = booleanPreferencesKey("haptics")
        val use24h = booleanPreferencesKey("use_24h")
        val weekSunday = booleanPreferencesKey("week_sunday")
        val hidden = stringSetPreferencesKey("hidden_sections")
        val customTags = stringPreferencesKey("custom_tags")
        val waterGoal = intPreferencesKey("water_goal")
        val reminder = booleanPreferencesKey("reminder")
        val reminderHour = intPreferencesKey("reminder_hour")
        val reminderMinute = intPreferencesKey("reminder_minute")
        val lock = booleanPreferencesKey("lock")
        val server = stringPreferencesKey("server_url")
        val token = stringPreferencesKey("token")
        val username = stringPreferencesKey("username")
        val share = stringPreferencesKey("share_level")
        val seen = stringSetPreferencesKey("seen_achievements")
        val achInit = booleanPreferencesKey("achievements_init")
        val lastLevel = intPreferencesKey("last_level")
        val ranksV2 = booleanPreferencesKey("ranks_v2")
        val timer = longPreferencesKey("timer_start")
        val friends = intPreferencesKey("friends_count")
    }

    val flow: Flow<Prefs> = ctx.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { p ->
            Prefs(
                onboarded = p[K.onboarded] ?: false,
                name = p[K.name] ?: "",
                avatar = p[K.avatar] ?: "💩",
                avatarPhoto = p[K.avatarPhoto],
                avatarBg = p[K.avatarBg] ?: 0,
                themeMode = p[K.themeMode] ?: 0,
                accent = p[K.accent] ?: 0,
                customHue = p[K.customHue] ?: 28f,
                dynamicColor = p[K.dynamic] ?: false,
                font = p[K.font] ?: 0,
                cardStyle = p[K.cardStyle] ?: 0,
                animatedBg = p[K.animatedBg] ?: true,
                haptics = p[K.haptics] ?: true,
                use24h = p[K.use24h] ?: false,
                weekStartsSunday = p[K.weekSunday] ?: false,
                hiddenSections = p[K.hidden] ?: emptySet(),
                customTags = (p[K.customTags] ?: "").split("\n").filter { it.isNotBlank() },
                waterGoal = p[K.waterGoal] ?: 8,
                reminder = p[K.reminder] ?: false,
                reminderHour = p[K.reminderHour] ?: 20,
                reminderMinute = p[K.reminderMinute] ?: 0,
                lock = p[K.lock] ?: false,
                serverUrl = FIXED_SERVER ?: p[K.server] ?: DEFAULT_SERVER,
                token = p[K.token],
                username = p[K.username],
                shareLevel = p[K.share] ?: "stats",
                seenAchievements = p[K.seen] ?: emptySet(),
                achievementsInit = p[K.achInit] ?: false,
                lastLevel = p[K.lastLevel] ?: 0,
                ranksV2 = p[K.ranksV2] ?: false,
                timerStart = p[K.timer] ?: 0L,
                friendsCount = p[K.friends] ?: 0
            )
        }

    suspend fun current(): Prefs = flow.first()

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        ctx.dataStore.edit { block(it) }
    }

    private fun <T> MutablePreferences.put(key: Preferences.Key<T>, value: T?) {
        if (value == null) remove(key) else set(key, value)
    }

    suspend fun finishOnboarding(name: String, avatar: String) = edit {
        it[K.name] = name; it[K.avatar] = avatar; it[K.onboarded] = true
    }

    suspend fun setProfile(name: String? = null, avatar: String? = null, avatarBg: Int? = null) = edit {
        name?.let { n -> it[K.name] = n }
        avatar?.let { a -> it[K.avatar] = a; it.remove(K.avatarPhoto) }
        avatarBg?.let { b -> it[K.avatarBg] = b }
    }

    suspend fun setAvatarPhoto(path: String?) = edit { it.put(K.avatarPhoto, path) }

    suspend fun setAppearance(
        mode: Int? = null, accent: Int? = null, dynamic: Boolean? = null, hue: Float? = null,
        font: Int? = null, cardStyle: Int? = null, animatedBg: Boolean? = null
    ) = edit {
        mode?.let { m -> it[K.themeMode] = m }
        accent?.let { a -> it[K.accent] = a }
        dynamic?.let { d -> it[K.dynamic] = d }
        hue?.let { h -> it[K.customHue] = h }
        font?.let { f -> it[K.font] = f }
        cardStyle?.let { c -> it[K.cardStyle] = c }
        animatedBg?.let { b -> it[K.animatedBg] = b }
    }

    suspend fun setBehavior(haptics: Boolean? = null, use24h: Boolean? = null, weekSunday: Boolean? = null, waterGoal: Int? = null) = edit {
        haptics?.let { h -> it[K.haptics] = h }
        use24h?.let { u -> it[K.use24h] = u }
        weekSunday?.let { w -> it[K.weekSunday] = w }
        waterGoal?.let { g -> it[K.waterGoal] = g }
    }

    suspend fun setSectionVisible(section: String, visible: Boolean) = edit {
        val cur = it[K.hidden] ?: emptySet()
        it[K.hidden] = if (visible) cur - section else cur + section
    }

    suspend fun setCustomTags(tags: List<String>) = edit { it[K.customTags] = tags.joinToString("\n") }

    suspend fun setReminder(enabled: Boolean, hour: Int, minute: Int) = edit {
        it[K.reminder] = enabled; it[K.reminderHour] = hour; it[K.reminderMinute] = minute
    }

    suspend fun setLock(enabled: Boolean) = edit { it[K.lock] = enabled }
    suspend fun setServer(url: String) = edit { it[K.server] = url }
    suspend fun setShareLevel(level: String) = edit { it[K.share] = level }
    suspend fun setTimer(start: Long) = edit { it[K.timer] = start }
    suspend fun setFriendsCount(n: Int) = edit { it[K.friends] = n }
    suspend fun setLastLevel(level: Int) = edit { it[K.lastLevel] = level; it[K.ranksV2] = true }

    suspend fun setSeen(ids: Set<String>) = edit {
        it[K.seen] = ids; it[K.achInit] = true
    }

    suspend fun setSession(server: String, token: String?, username: String?) = edit {
        it[K.server] = server
        it.put(K.token, token)
        it.put(K.username, username)
    }
}
