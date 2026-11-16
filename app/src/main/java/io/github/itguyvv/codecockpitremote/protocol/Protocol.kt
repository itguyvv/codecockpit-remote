package io.github.itguyvv.codecockpitremote.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Wire contract with the CodeCockpit desktop remote server.
 * See docs/REMOTE_PROTOCOL.md in the CodeCockpit repository.
 */

val protocolJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
}

// ── Payload models ──

/** One conversation entry. The desktop re-sends an id with newer content while streaming: upsert. */
@Serializable
data class RemoteMessage(
    val id: String,
    val ts: Long,
    val role: String,
    val text: String? = null,
    val toolName: String? = null,
    val toolInput: String? = null,
    val tokensIn: Long? = null,
    val tokensOut: Long? = null,
    val costUsd: Double? = null
)

@Serializable
data class SessionSummary(
    val id: String,
    val engine: String,
    val title: String,
    val cwd: String,
    val claudeSessionId: String? = null,
    val status: String,
    val permissionMode: String,
    val startedAt: Long,
    val busy: Boolean = false,
    val bg: Int = 0,
    val model: String = "",
    val tokensIn: Long = 0,
    val tokensOut: Long = 0,
    val costUsd: Double = 0.0,
    val turns: Int = 0
)

@Serializable
data class SessionStatus(
    val model: String = "",
    val tool: String? = null,
    val toolDetail: String = "",
    val tokensIn: Long = 0,
    val tokensOut: Long = 0,
    val cacheRead: Long = 0,
    val cacheWrite: Long = 0,
    val costUsd: Double = 0.0,
    val turns: Int = 0,
    val toolCalls: Int = 0,
    val toolCounts: Map<String, Int> = emptyMap(),
    val durationMs: Long = 0,
    val lastActivity: Long = 0,
    val eventCount: Int = 0,
    val backgroundTasks: Int = 0
)

// ── Client → Server ──

fun authRequest(secret: String, name: String): JsonObject = buildJsonObject {
    put("type", "auth")
    put("secret", secret)
    put("name", name)
}

fun authRequest(token: String): JsonObject = buildJsonObject {
    put("type", "auth")
    put("token", token)
}

fun listRequest(): JsonObject = buildJsonObject { put("type", "list") }

fun attachRequest(sessionId: String): JsonObject = buildJsonObject {
    put("type", "attach")
    put("id", sessionId)
}

fun detachRequest(sessionId: String): JsonObject = buildJsonObject {
    put("type", "detach")
    put("id", sessionId)
}

fun inputRequest(sessionId: String, data: String): JsonObject = buildJsonObject {
    put("type", "input")
    put("id", sessionId)
    put("data", data)
}

fun modeRequest(sessionId: String, mode: String): JsonObject = buildJsonObject {
    put("type", "mode")
    put("id", sessionId)
    put("mode", mode)
}

fun pingRequest(): JsonObject = buildJsonObject { put("type", "ping") }

// ── Server → Client (parsed form) ──

sealed interface ServerEvent {
    data class AuthOk(val token: String, val fingerprint: String) : ServerEvent
    data class AuthFail(val reason: String) : ServerEvent
    data class Sessions(val list: List<SessionSummary>) : ServerEvent
    data class ConvSnapshot(val sessionId: String, val messages: List<RemoteMessage>) : ServerEvent
    data class ConvAppend(val sessionId: String, val messages: List<RemoteMessage>) : ServerEvent
    data class Busy(val sessionId: String, val busy: Boolean, val bg: Int) : ServerEvent
    data class Status(val sessionId: String, val status: SessionStatus) : ServerEvent
    data class Exit(val sessionId: String, val code: Int) : ServerEvent
    data class SessionId(val sessionId: String, val nativeId: String) : ServerEvent
    data object Pong : ServerEvent
    data class Error(val message: String) : ServerEvent
}

/** Parse one server text frame; unknown types are ignored (forward compatible). */
fun parseServerEvent(frame: String): ServerEvent? {
    val obj = protocolJson.decodeFromString<JsonObject>(frame)
    val type = obj["type"]?.jsonPrimitive?.content ?: return null
    return when (type) {
        "auth_ok" -> ServerEvent.AuthOk(
            token = str(obj, "token"),
            fingerprint = str(obj, "fp")
        )
        "auth_fail" -> ServerEvent.AuthFail(str(obj, "reason"))
        "sessions" -> ServerEvent.Sessions(
            protocolJson.decodeFromJsonElement<List<SessionSummary>>(obj["sessions"] ?: JsonArray(emptyList()))
        )
        "conv_snapshot" -> ServerEvent.ConvSnapshot(
            sessionId = str(obj, "id"),
            messages = decodeMessages(obj["messages"])
        )
        "conv_append" -> ServerEvent.ConvAppend(
            sessionId = str(obj, "id"),
            messages = decodeMessages(obj["messages"])
        )
        "busy" -> ServerEvent.Busy(
            sessionId = str(obj, "id"),
            busy = obj["busy"]?.jsonPrimitive?.boolean ?: false,
            bg = intOr(obj, "bg")
        )
        "status" -> obj["status"]?.let {
            ServerEvent.Status(str(obj, "id"), protocolJson.decodeFromJsonElement<SessionStatus>(it))
        }
        "exit" -> ServerEvent.Exit(str(obj, "id"), intOr(obj, "code"))
        "session_id" -> ServerEvent.SessionId(str(obj, "id"), str(obj, "claudeSessionId"))
        "pong" -> ServerEvent.Pong
        "error" -> ServerEvent.Error(str(obj, "message"))
        else -> null
    }
}

private fun str(obj: JsonObject, key: String): String =
    obj[key]?.jsonPrimitive?.content ?: ""

private fun intOr(obj: JsonObject, key: String): Int = obj[key]?.jsonPrimitive?.int ?: 0

private fun decodeMessages(raw: JsonElement?): List<RemoteMessage> =
    if (raw == null) emptyList()
    else protocolJson.decodeFromJsonElement<List<RemoteMessage>>(raw)
