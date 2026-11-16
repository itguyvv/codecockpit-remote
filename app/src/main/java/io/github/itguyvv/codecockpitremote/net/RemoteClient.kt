package io.github.itguyvv.codecockpitremote.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/** Desktop endpoint as persisted on the phone */
data class ServerProfile(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    /** Pairing secret — reused automatically if the desktop revokes the token */
    val secret: String = "",
    /** Empty until the first pairing succeeds */
    val token: String = "",
    /** Empty until the user confirms the certificate fingerprint (TOFU) */
    val fingerprint: String = ""
)

enum class ConnError {
    NETWORK,
    TLS,
    BAD_SECRET,
    BAD_TOKEN,
    BANNED,
    TOO_MANY_CLIENTS,
    FINGERPRINT_MISMATCH
}

sealed interface ConnState {
    data object Idle : ConnState
    data object Connecting : ConnState
    /** Handshake done, fingerprint captured — waiting for the user's TOFU decision */
    data class Verifying(val fingerprint: String) : ConnState
    data object Authing : ConnState
    data object Ready : ConnState
    data class Failed(val error: ConnError) : ConnState
}

/**
 * One WebSocket connection to a CodeCockpit desktop.
 * Lifecycle: [connect] → (TOFU) → auth → Ready; [close] tears everything down.
 * Server frames are re-emitted on [events]; auth frames are intercepted here.
 */
class RemoteClient(
    private val scope: CoroutineScope,
    private val server: ServerProfile,
    /** Called once the user accepted a first-seen fingerprint (persist it) */
    private val onFingerprintTrusted: (fp: String) -> Unit,
    /** Called with the freshly minted device token after secret pairing (persist it) */
    private val onTokenIssued: (token: String) -> Unit,
    /** Suspends until the user made the TOFU decision; true = trust */
    private val decideFingerprint: suspend (fp: String) -> Boolean
) {
    private val _state = MutableStateFlow<ConnState>(ConnState.Connecting)
    val state: StateFlow<ConnState> = _state

    private val _events = MutableSharedFlow<ServerEvent>(extraBufferCapacity = 128)
    val events: SharedFlow<ServerEvent> = _events

    private var ws: WebSocket? = null
    private var http: OkHttpClient? = null
    /** Guards against a second terminal transition overwriting the real cause */
    private var finished = false

    /**
     * Open the connection. [secret] is the pairing secret used when the profile has
     * no stored token yet; ignored on token reconnects.
     */
    fun connect(secret: String): RemoteClient {
        val tls = FingerprintTls()
        val client = OkHttpClient.Builder()
            .sslSocketFactory(tls.sslContext(), tls.trustManager)
            // Identity is the pinned fingerprint, not a CA-signed hostname
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
        http = client
        val request = Request.Builder()
            .url("wss://${server.host}:${server.port}/")
            .build()
        ws = client.newWebSocket(request, Listener(tls, secret))
        return this
    }

    fun send(msg: JsonObject): Boolean {
        val socket = ws ?: return false
        return socket.send(msg.toString())
    }

    fun close() {
        finished = true
        ws?.close(1000, "bye")
        ws = null
        http?.dispatcher?.executorService?.shutdown()
        http = null
        if (_state.value !is ConnState.Failed) _state.value = ConnState.Idle
    }

    private fun fail(error: ConnError) {
        if (finished) return
        finished = true
        _state.value = ConnState.Failed(error)
        ws?.close(1000, "error")
        ws = null
        http?.dispatcher?.executorService?.shutdown()
        http = null
    }

    private inner class Listener(
        private val tls: FingerprintTls,
        private val secret: String
    ) : WebSocketListener() {
        /** One automatic re-pair per connection when the desktop revoked our token */
        private var rePaired = false
        override fun onOpen(webSocket: WebSocket, response: Response) {
            val seen = tls.seenFingerprint.get()
            scope.launch {
                if (server.fingerprint.isEmpty()) {
                    // First contact: show the fingerprint and let the user decide
                    _state.value = ConnState.Verifying(seen)
                    val trusted = decideFingerprint(seen)
                    if (!trusted) {
                        fail(ConnError.FINGERPRINT_MISMATCH)
                        return@launch
                    }
                    onFingerprintTrusted(seen)
                } else if (!seen.equals(server.fingerprint, ignoreCase = true)) {
                    fail(ConnError.FINGERPRINT_MISMATCH)
                    return@launch
                }
                _state.value = ConnState.Authing
                val auth = if (server.token.isNotEmpty()) {
                    authRequest(server.token)
                } else {
                    authRequest(secret, server.name)
                }
                webSocket.send(auth.toString())
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val event = try {
                parseServerEvent(text)
            } catch (e: Exception) {
                null
            } ?: return
            when (event) {
                is ServerEvent.AuthOk -> {
                    if (event.token.isNotEmpty()) onTokenIssued(event.token)
                    _state.value = ConnState.Ready
                }
                is ServerEvent.AuthFail -> {
                    // Desktop regenerated its secret and un-paired us: retry with the secret once
                    if (event.reason == "bad_token" && secret.isNotEmpty() && !rePaired) {
                        rePaired = true
                        webSocket.send(authRequest(secret, server.name).toString())
                        return
                    }
                    fail(
                        when (event.reason) {
                            "bad_secret" -> ConnError.BAD_SECRET
                            "bad_token" -> ConnError.BAD_TOKEN
                            "banned" -> ConnError.BANNED
                            "too_many_clients" -> ConnError.TOO_MANY_CLIENTS
                            else -> ConnError.NETWORK
                        }
                    )
                    return
                }
                else -> Unit
            }
            scope.launch { _events.emit(event) }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            fail(
                if (tls.seenFingerprint.get().isEmpty()) ConnError.NETWORK else ConnError.TLS
            )
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            // Server-initiated close after a successful session (revoked device etc.)
            fail(ConnError.NETWORK)
        }
    }
}
