package io.github.itguyvv.codecockpitremote.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/** Frames relayed by the signaling worker; the pairing secret never travels here */
sealed interface Signal {
    /** The desktop is online and this client was accepted; cid identifies the rendezvous */
    data class Joined(val cid: String) : Signal
    data class Answer(val sdp: String, val cid: String) : Signal
    data class Ice(val candidate: String, val sdpMid: String?, val sdpMLineIndex: Int, val cid: String) : Signal
    /** Desktop socket dropped mid-negotiation */
    data object DeviceGone : Signal
    /** The server refused the upgrade: 404 = offline/nonexistent, 429 = busy or rate-limited */
    data class Refused(val httpCode: Int) : Signal
    /** Connection lost without a refusal code (network unreachable etc.) */
    data object Dead : Signal
}

/**
 * WebSocket to the signaling relay for one link session. Only SDP/ICE envelopes
 * flow here; authentication happens end-to-end inside the resulting DataChannel.
 */
class SignalingClient(
    private val scope: CoroutineScope,
    private val serverBase: String,
    private val linkId: String
) {
    private val _events = MutableSharedFlow<Signal>(extraBufferCapacity = 32)
    val events: SharedFlow<Signal> = _events

    private var ws: WebSocket? = null
    private var http: OkHttpClient? = null
    private var closed = false

    fun join() {
        val client = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
        http = client
        val base = serverBase.trimEnd('/').replaceFirst("http", "ws")
        val request = Request.Builder()
            .url("$base/ws?role=client&id=${linkId.trim()}")
            .build()
        ws = client.newWebSocket(request, Listener())
    }

    fun sendOffer(sdp: String) {
        val data = JsonObject(
            mapOf(
                "type" to JsonPrimitive("offer"),
                "sdp" to JsonPrimitive(sdp)
            )
        )
        ws?.send(frame("offer", data))
    }

    fun sendIce(candidate: String, sdpMid: String?, sdpMLineIndex: Int) {
        val data = JsonObject(
            mapOf(
                "candidate" to JsonPrimitive(candidate),
                "sdpMid" to (sdpMid?.let { JsonPrimitive(it) } ?: JsonNull),
                "sdpMLineIndex" to JsonPrimitive(sdpMLineIndex)
            )
        )
        ws?.send(frame("ice", data))
    }

    fun sendClose() {
        ws?.send("""{"t":"close"}""")
    }

    fun close() {
        if (closed) return
        closed = true
        ws?.close(1000, "bye")
        ws = null
        http?.dispatcher?.executorService?.shutdown()
        http = null
    }

    private fun frame(t: String, data: JsonObject): String =
        JsonObject(mapOf("t" to JsonPrimitive(t), "data" to data)).toString()

    private fun emit(signal: Signal) {
        scope.launch { _events.emit(signal) }
    }

    private inner class Listener : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            val obj = try {
                Json.parseToJsonElement(text).jsonObject
            } catch (e: Exception) {
                null
            } ?: return
            val t = obj["t"]?.jsonPrimitive?.content ?: return
            val cid = obj["cid"]?.jsonPrimitive?.content ?: ""
            when (t) {
                "joined" -> emit(Signal.Joined(cid))
                "answer" -> {
                    val data = obj["data"] as? JsonObject ?: return
                    val sdp = data["sdp"]?.jsonPrimitive?.content ?: return
                    emit(Signal.Answer(sdp, cid))
                }
                "ice" -> {
                    val data = obj["data"] as? JsonObject ?: return
                    val candidate = data["candidate"]?.jsonPrimitive?.content ?: return
                    val mid = data["sdpMid"]?.jsonPrimitive?.content
                    val line = data["sdpMLineIndex"]?.jsonPrimitive?.int ?: 0
                    emit(Signal.Ice(candidate, mid, line, cid))
                }
                "offline" -> emit(Signal.DeviceGone)
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (closed) return
            emit(
                if (response != null) Signal.Refused(response.code) else Signal.Dead
            )
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (closed) return
            emit(
                if (code == 4002) Signal.DeviceGone else Signal.Dead
            )
        }
    }
}
