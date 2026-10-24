package com.example.kakascalendar.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
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

data class Prefs(
    val onboarded: Boolean = false,
    val name: String = "",
    val avatar: String = "💩",
    val themeMode: Int = 0,              // 0 system, 1 light, 2 dark
    val accent: Int = 0,
    val dynamicColor: Boolean = false,
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
    val timerStart: Long = 0L,
    val friendsCount: Int = 0
) {
    val signedIn get() = token != null
}

class PrefsRepo(private val ctx: Context) {
    private object K {
        val onboarded = booleanPreferencesKey("onboarded")
        val name = stringPreferencesKey("name")
        val avatar = stringPreferencesKey("avatar")
        val themeMode = intPreferencesKey("theme_mode")
        val accent = intPreferencesKey("accent")
        val dynamic = booleanPreferencesKey("dynamic_color")
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
                themeMode = p[K.themeMode] ?: 0,
                accent = p[K.accent] ?: 0,
                dynamicColor = p[K.dynamic] ?: false,
                reminder = p[K.reminder] ?: false,
                reminderHour = p[K.reminderHour] ?: 20,
                reminderMinute = p[K.reminderMinute] ?: 0,
                lock = p[K.lock] ?: false,
                serverUrl = p[K.server] ?: DEFAULT_SERVER,
                token = p[K.token],
                username = p[K.username],
                shareLevel = p[K.share] ?: "stats",
                seenAchievements = p[K.seen] ?: emptySet(),
                achievementsInit = p[K.achInit] ?: false,
                lastLevel = p[K.lastLevel] ?: 0,
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

    suspend fun setProfile(name: String? = null, avatar: String? = null) = edit {
        name?.let { n -> it[K.name] = n }
        avatar?.let { a -> it[K.avatar] = a }
    }

    suspend fun setAppearance(mode: Int? = null, accent: Int? = null, dynamic: Boolean? = null) = edit {
        mode?.let { m -> it[K.themeMode] = m }
        accent?.let { a -> it[K.accent] = a }
        dynamic?.let { d -> it[K.dynamic] = d }
    }

    suspend fun setReminder(enabled: Boolean, hour: Int, minute: Int) = edit {
        it[K.reminder] = enabled; it[K.reminderHour] = hour; it[K.reminderMinute] = minute
    }

    suspend fun setLock(enabled: Boolean) = edit { it[K.lock] = enabled }
    suspend fun setServer(url: String) = edit { it[K.server] = url }
    suspend fun setShareLevel(level: String) = edit { it[K.share] = level }
    suspend fun setTimer(start: Long) = edit { it[K.timer] = start }
    suspend fun setFriendsCount(n: Int) = edit { it[K.friends] = n }
    suspend fun setLastLevel(level: Int) = edit { it[K.lastLevel] = level }

    suspend fun setSeen(ids: Set<String>) = edit {
        it[K.seen] = ids; it[K.achInit] = true
    }

    suspend fun setSession(server: String, token: String?, username: String?) = edit {
        it[K.server] = server
        it.put(K.token, token)
        it.put(K.username, username)
    }
}
