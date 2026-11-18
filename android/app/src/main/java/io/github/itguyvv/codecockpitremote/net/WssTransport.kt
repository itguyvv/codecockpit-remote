package io.github.itguyvv.codecockpitremote.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * Direct TLS WebSocket to the desktop (Same network / Tailscale). Owns the TOFU
 * certificate flow: first contact pauses in [ChannelState.Verifying] until the
 * user decides, later contacts pin the stored fingerprint.
 */
class WssTransport(
    private val scope: CoroutineScope,
    private val server: ServerProfile,
    private val onFingerprintTrusted: (fp: String) -> Unit,
    private val decideFingerprint: suspend (fp: String) -> Boolean
) : Transport {

    private val _channel = MutableStateFlow<ChannelState>(ChannelState.Connecting)
    override val channel: StateFlow<ChannelState> = _channel

    private val _frames = MutableSharedFlow<String>(extraBufferCapacity = 128)
    override val frames: SharedFlow<String> = _frames

    private var ws: WebSocket? = null
    private var http: OkHttpClient? = null
    private var closed = false

    override fun start() {
        val tls = FingerprintTls()
        val client = OkHttpClient.Builder()
            .sslSocketFactory(tls.sslContext().socketFactory, tls.trustManager)
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
        ws = client.newWebSocket(request, Listener(tls))
    }

    override fun send(text: String): Boolean {
        val socket = ws ?: return false
        return socket.send(text)
    }

    override fun close() {
        if (closed) return
        closed = true
        ws?.close(1000, "bye")
        ws = null
        http?.dispatcher?.executorService?.shutdown()
        http = null
    }

    private fun fail(error: ConnError) {
        if (closed) return
        closed = true
        ws?.close(1000, "error")
        ws = null
        http?.dispatcher?.executorService?.shutdown()
        http = null
        _channel.value = ChannelState.Closed(error)
    }

    private inner class Listener(private val tls: FingerprintTls) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            val seen = tls.seenFingerprint.get()
            scope.launch {
                if (server.fingerprint.isEmpty()) {
                    // First contact: show the fingerprint and let the user decide
                    _channel.value = ChannelState.Verifying(seen)
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
                _channel.value = ChannelState.Open
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            scope.launch { _frames.emit(text) }
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
