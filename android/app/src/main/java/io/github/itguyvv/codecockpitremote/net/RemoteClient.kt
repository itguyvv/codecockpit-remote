package io.github.itguyvv.codecockpitremote.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import io.github.itguyvv.codecockpitremote.protocol.ServerEvent
import io.github.itguyvv.codecockpitremote.protocol.authRequest
import io.github.itguyvv.codecockpitremote.protocol.parseServerEvent

/** Desktop endpoint as persisted on the phone */
@Serializable
data class ServerProfile(
    val id: String,
    val name: String,
    /** Direct address — LAN, Tailscale, or a bracketed IPv6 literal like [2408:…]:port */
    val host: String = "",
    val port: Int = 0,
    /** Pairing secret — reused automatically if the desktop revokes the token */
    val secret: String = "",
    /** Empty until the first pairing succeeds */
    val token: String = "",
    /** Empty until the user confirms the certificate fingerprint (TOFU, WSS only) */
    val fingerprint: String = ""
)

enum class ConnError {
    NETWORK,
    TLS,
    OFFLINE,
    BAD_SECRET,
    BAD_TOKEN,
    BANNED,
    TOO_MANY_CLIENTS,
    FINGERPRINT_MISMATCH
}

sealed interface ConnState {
    data object Idle : ConnState
    data object Connecting : ConnState
    /** Handshake done, fingerprint captured — waiting for the user's TOFU decision (WSS only) */
    data class Verifying(val fingerprint: String) : ConnState
    data object Authing : ConnState
    data object Ready : ConnState
    data class Failed(val error: ConnError) : ConnState
}

/**
 * One connection to a CodeCockpit desktop over WSS ([WssTransport]). Lifecycle:
 * [connect] → (TOFU) → auth → Ready; [close] tears everything down. Server frames
 * are re-emitted on [events]; auth frames are intercepted here.
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

    private var transport: Transport? = null
    /** Guards against a second terminal transition overwriting the real cause */
    private var finished = false
    /** One automatic re-pair per connection when the desktop revoked our token */
    private var rePaired = false

    /**
     * Open the connection. [secret] is the pairing secret used when the profile has
     * no stored token yet; ignored on token reconnects.
     */
    fun connect(secret: String): RemoteClient {
        val t: Transport = WssTransport(scope, server, onFingerprintTrusted, decideFingerprint)
        transport = t
        observe(t, secret)
        t.start()
        return this
    }

    fun send(msg: JsonObject): Boolean {
        val t = transport ?: return false
        return t.send(msg.toString())
    }

    fun close() {
        finished = true
        transport?.close()
        transport = null
        if (_state.value !is ConnState.Failed) _state.value = ConnState.Idle
    }

    private fun observe(t: Transport, secret: String) {
        scope.launch {
            t.channel.collect { cs ->
                when (cs) {
                    ChannelState.Connecting -> if (!finished) _state.value = ConnState.Connecting
                    is ChannelState.Verifying ->
                        if (!finished) _state.value = ConnState.Verifying(cs.fingerprint)
                    ChannelState.Open -> {
                        if (finished) return@collect
                        _state.value = ConnState.Authing
                        val auth = if (server.token.isNotEmpty()) {
                            authRequest(server.token)
                        } else {
                            authRequest(secret, server.name)
                        }
                        t.send(auth.toString())
                    }
                    is ChannelState.Closed -> {
                        if (finished) return@collect
                        fail(cs.error)
                    }
                }
            }
        }
        scope.launch {
            t.frames.collect { text -> onFrame(t, secret, text) }
        }
    }

    private fun onFrame(t: Transport, secret: String, text: String) {
        if (finished) return
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
                    t.send(authRequest(secret, server.name).toString())
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

    private fun fail(error: ConnError) {
        if (finished) return
        finished = true
        _state.value = ConnState.Failed(error)
        transport?.close()
        transport = null
    }
}
