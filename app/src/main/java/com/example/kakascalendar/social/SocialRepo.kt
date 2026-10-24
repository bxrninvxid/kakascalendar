package com.example.kakascalendar.social

import com.example.kakascalendar.data.PrefsRepo
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class SocialState(
    val me: UserDto? = null,
    val feed: List<FeedItem> = emptyList(),
    val friends: FriendsRes = FriendsRes(),
    val board: List<UserDto> = emptyList(),
    val directory: List<DirectoryEntry>? = null,   // null = server has no directory
    val stories: List<StoryGroup> = emptyList(),
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null
)

/**
 * Everything Squad-related. Every public call returns null on success
 * or a human-readable error message.
 */
class SocialRepo(private val prefs: PrefsRepo) {
    private val _state = MutableStateFlow(SocialState())
    val state: StateFlow<SocialState> = _state

    private suspend fun api(): Api? {
        val p = prefs.current()
        return p.token?.let { Api(p.serverUrl, it) }
    }

    private suspend fun <T> call(block: suspend (Api) -> T): Result<T> {
        val api = api() ?: return Result.failure(IllegalStateException("Sign in to your Squad first"))
        return try {
            Result.success(block(api))
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            if (isAuthError(t)) signOut(remote = false)
            Result.failure(t)
        }
    }

    private suspend fun Result<*>.errorText(): String? = exceptionOrNull()?.let {
        if (it is IllegalStateException) it.message else friendlyError(it)
    }

    // ───────── account ─────────

    suspend fun authenticate(
        server: String, username: String, password: String,
        register: Boolean, displayName: String, avatar: String, shareLevel: String
    ): String? {
        val url = server.trim().trimEnd('/').ifEmpty { return "Enter a server address" }
        if (!url.startsWith("http://") && !url.startsWith("https://")) return "Server address must start with http:// or https://"
        return try {
            val api = Api(url, null)
            val invite = com.example.kakascalendar.BuildConfig.INVITE_CODE.ifBlank { null }
            val req = AuthReq(username.trim().lowercase(), password, displayName.ifBlank { null }, avatar, invite)
            val res = if (register) api.register(req) else api.login(req)
            prefs.setSession(url, res.token, res.user.username)
            // keep the server in sync with local privacy choice
            runCatching { Api(url, res.token).updateMe(ProfileReq(shareLevel = shareLevel)) }
            _state.value = SocialState(me = res.user)
            refresh()
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            friendlyError(t)
        }
    }

    suspend fun signOut(remote: Boolean = true) {
        if (remote) runCatching { api()?.logout() }
        val p = prefs.current()
        prefs.setSession(p.serverUrl, null, null)
        _state.value = SocialState()
    }

    suspend fun updateProfile(displayName: String? = null, avatar: String? = null, shareLevel: String? = null): String? {
        if (api() == null) return null
        val r = call { it.updateMe(ProfileReq(displayName, avatar, shareLevel)) }
        r.getOrNull()?.let { me -> _state.update { it.copy(me = me) } }
        return r.errorText()
    }

    /** Uploads (or with null, removes) the profile photo. Silently skipped when signed out. */
    suspend fun syncPhoto(base64Jpeg: String?): String? {
        if (api() == null) return null
        val r = call { if (base64Jpeg != null) it.uploadPhoto(base64Jpeg) else it.deletePhoto() }
        r.getOrNull()?.let { me -> _state.update { it.copy(me = me) } }
        return r.errorText()
    }

    // ───────── data ─────────

    suspend fun refresh(): String? {
        if (api() == null) return null
        _state.update { it.copy(loading = true, error = null) }
        val r = call { api ->
            coroutineScope {
                val me = api.me()
                val feed = api.feed()
                val friends = api.friends()
                val board = api.leaderboard()
                val directory = try {
                    api.directory()
                } catch (t: Throwable) {
                    if (t is kotlinx.coroutines.CancellationException || isAuthError(t)) throw t
                    null
                }
                val stories = try {
                    api.stories()
                } catch (t: Throwable) {
                    if (t is kotlinx.coroutines.CancellationException || isAuthError(t)) throw t
                    emptyList()        // older server without stories
                }
                SocialState(me = me, feed = feed, friends = friends, board = board, directory = directory,
                    stories = stories, loaded = true)
            }
        }
        val err = r.errorText()
        r.getOrNull()?.let { s ->
            _state.value = s
            prefs.setFriendsCount(s.friends.friends.size)
        }
        _state.update { it.copy(loading = false, error = err) }
        return err
    }

    suspend fun react(item: FeedItem, emoji: String): String? {
        // optimistic toggle
        val mine = emoji in item.myReactions
        _state.update { s ->
            s.copy(feed = s.feed.map { f ->
                if (f.id != item.id) f else {
                    val count = (f.reactions[emoji] ?: 0) + if (mine) -1 else 1
                    f.copy(
                        reactions = if (count <= 0) f.reactions - emoji else f.reactions + (emoji to count),
                        myReactions = if (mine) f.myReactions - emoji else f.myReactions + emoji
                    )
                }
            })
        }
        val r = call { it.react(item.id, emoji) }
        return r.errorText()?.also { refresh() }
    }

    suspend fun addFriend(username: String): String? {
        if (username.isBlank()) return "Enter a username"
        val r = call { it.requestFriend(username.trim()) }
        refresh()
        return r.errorText()
    }

    suspend fun addFriendById(userId: Long): String? {
        val r = call { it.requestFriendById(userId) }
        refresh()
        return r.errorText()
    }

    suspend fun accept(requestId: Long) = call { it.accept(requestId) }.errorText().also { refresh() }
    suspend fun decline(requestId: Long) = call { it.decline(requestId) }.errorText().also { refresh() }
    suspend fun unfriend(userId: Long) = call { it.unfriend(userId) }.errorText().also { refresh() }
    suspend fun nudge(userId: Long) = call { it.nudge(userId) }.errorText().also { if (it == null) refresh() }

    // ───────── stories ─────────

    suspend fun postStory(req: PostStoryReq): String? {
        val r = call { it.postStory(req) }
        if (r.isSuccess) refresh()
        return r.errorText()
    }

    /** Marks a story as seen locally right away, then tells the server. */
    suspend fun viewStory(story: StoryDto) {
        if (story.seen) return
        _state.update { s ->
            s.copy(stories = s.stories.map { g ->
                val list = g.stories.map { if (it.id == story.id) it.copy(seen = true) else it }
                g.copy(stories = list, allSeen = list.all { it.seen })
            })
        }
        call { it.viewStory(story.id) }
    }

    suspend fun storyViewers(id: Long): Result<List<UserDto>> = call { it.storyViewers(id) }

    suspend fun deleteStory(id: Long): String? {
        val r = call { it.deleteStory(id) }
        refresh()
        return r.errorText()
    }

    // ───────── silent background sync ─────────

    suspend fun pushStats(stats: StatsReq) {
        call { it.pushStats(stats) }
    }

    suspend fun postEvent(kind: String, payload: Map<String, String>) {
        call { it.postEvent(EventReq(kind, payload)) }
    }
}
