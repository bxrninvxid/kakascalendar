package com.example.kakascalendar.social

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// ───────── DTOs (match server/server.py) ─────────

@Serializable
data class UserDto(
    val id: Long,
    val username: String,
    @SerialName("display_name") val displayName: String,
    val avatar: String = "💩",
    @SerialName("share_level") val shareLevel: String = "stats",
    @SerialName("current_streak") val currentStreak: Int = 0,
    @SerialName("best_streak") val bestStreak: Int = 0,
    val total: Int = 0,
    @SerialName("week_count") val weekCount: Int = 0,
    val xp: Int = 0,
    val level: Int = 1,
    @SerialName("photo_version") val photoVersion: Int = 0
)

@Serializable
data class AuthReq(
    val username: String,
    val password: String,
    @SerialName("display_name") val displayName: String? = null,
    val avatar: String? = null,
    @SerialName("invite_code") val inviteCode: String? = null
)

@Serializable
data class AuthRes(val token: String, val user: UserDto)

@Serializable
data class ProfileReq(
    @SerialName("display_name") val displayName: String? = null,
    val avatar: String? = null,
    @SerialName("share_level") val shareLevel: String? = null
)

@Serializable
data class StatsReq(
    @SerialName("current_streak") val currentStreak: Int,
    @SerialName("best_streak") val bestStreak: Int,
    val total: Int,
    @SerialName("week_count") val weekCount: Int,
    val xp: Int,
    val level: Int
)

@Serializable
data class EventReq(val kind: String, val payload: Map<String, String> = emptyMap())

@Serializable
data class FeedItem(
    val id: Long,
    val user: UserDto,
    val kind: String,
    val payload: Map<String, String> = emptyMap(),
    @SerialName("created_at") val createdAt: Long,
    val reactions: Map<String, Int> = emptyMap(),
    @SerialName("my_reactions") val myReactions: List<String> = emptyList()
)

@Serializable
data class FriendRequest(val id: Long, val user: UserDto)

@Serializable
data class FriendsRes(
    val friends: List<UserDto> = emptyList(),
    val incoming: List<FriendRequest> = emptyList(),
    val outgoing: List<FriendRequest> = emptyList()
)

@Serializable
data class UsernameReq(val username: String)

@Serializable
data class UserIdReq(@SerialName("user_id") val userId: Long)

/** One person in the server's people directory. status: none | outgoing | incoming | friend */
@Serializable
data class DirectoryEntry(
    val user: UserDto,
    val status: String = "none",
    @SerialName("request_id") val requestId: Long? = null
)

@Serializable
data class ReactReq(val emoji: String)

@Serializable
data class PhotoReq(val data: String)

@Serializable
data class StatusRes(val status: String = "", val active: Boolean = false, val ok: Boolean = false)

// ───────── Client ─────────

val ApiJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

class Api(base: String, private val token: String?) {
    private val base = base.trim().trimEnd('/')

    companion object {
        val client = HttpClient(Android) {
            expectSuccess = true
            install(ContentNegotiation) { json(ApiJson) }
            install(HttpTimeout) {
                requestTimeoutMillis = 12_000
                connectTimeoutMillis = 8_000
            }
        }
    }

    private fun HttpRequestBuilder.auth() {
        token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    }

    private inline fun <reified T> HttpRequestBuilder.jsonBody(body: T) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    suspend fun register(r: AuthReq): AuthRes = client.post("$base/auth/register") { jsonBody(r) }.body()
    suspend fun login(r: AuthReq): AuthRes = client.post("$base/auth/login") { jsonBody(r) }.body()
    suspend fun logout() { client.post("$base/auth/logout") { auth() } }

    suspend fun me(): UserDto = client.get("$base/me") { auth() }.body()
    suspend fun updateMe(r: ProfileReq): UserDto = client.patch("$base/me") { auth(); jsonBody(r) }.body()
    suspend fun pushStats(r: StatsReq) { client.post("$base/me/stats") { auth(); jsonBody(r) } }

    suspend fun postEvent(r: EventReq) { client.post("$base/events") { auth(); jsonBody(r) } }
    suspend fun feed(): List<FeedItem> = client.get("$base/feed?limit=60") { auth() }.body()
    suspend fun react(id: Long, emoji: String): StatusRes =
        client.post("$base/events/$id/react") { auth(); jsonBody(ReactReq(emoji)) }.body()

    suspend fun friends(): FriendsRes = client.get("$base/friends") { auth() }.body()
    suspend fun requestFriend(username: String): StatusRes =
        client.post("$base/friends/request") { auth(); jsonBody(UsernameReq(username)) }.body()
    suspend fun requestFriendById(userId: Long): StatusRes =
        client.post("$base/friends/request") { auth(); jsonBody(UserIdReq(userId)) }.body()
    suspend fun directory(): List<DirectoryEntry> = client.get("$base/users") { auth() }.body()
    suspend fun accept(requestId: Long) { client.post("$base/friends/requests/$requestId/accept") { auth() } }
    suspend fun decline(requestId: Long) { client.post("$base/friends/requests/$requestId/decline") { auth() } }
    suspend fun unfriend(userId: Long) { client.delete("$base/friends/$userId") { auth() } }
    suspend fun nudge(userId: Long) { client.post("$base/nudge/$userId") { auth() } }

    suspend fun uploadPhoto(base64Jpeg: String): UserDto =
        client.post("$base/me/photo") { auth(); jsonBody(PhotoReq(base64Jpeg)) }.body()
    suspend fun deletePhoto(): UserDto = client.delete("$base/me/photo") { auth() }.body()

    suspend fun leaderboard(): List<UserDto> = client.get("$base/leaderboard") { auth() }.body()
}

/** Turns any network failure into something a human can read. */
suspend fun friendlyError(t: Throwable): String = when (t) {
    is ResponseException -> runCatching {
        ApiJson.parseToJsonElement(t.response.bodyAsText()).jsonObject["detail"]?.jsonPrimitive?.content
    }.getOrNull() ?: "Server error (${t.response.status.value})"
    is HttpRequestTimeoutException -> "The server took too long to answer"
    is java.net.ConnectException, is java.net.UnknownHostException, is java.net.SocketTimeoutException ->
        "Can't reach the server. Check the server address."
    else -> t.message ?: "Something went wrong"
}

fun isAuthError(t: Throwable) = t is ResponseException && t.response.status.value == 401
