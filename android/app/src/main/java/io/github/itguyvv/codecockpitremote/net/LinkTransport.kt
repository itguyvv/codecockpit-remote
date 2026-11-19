package io.github.itguyvv.codecockpitremote.net

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Any-network transport: WebRTC DataChannel hole-punched through the signaling
 * relay. This phone is the offerer. Carries the same JSON text frames as the WSS
 * path, wrapped in 4-byte big-endian length framing split into <=16 KiB chunks.
 */
class LinkTransport(
    private val scope: CoroutineScope,
    private val serverBase: String,
    private val linkId: String,
    private val appContext: Context
) : Transport {

    private val _channel = MutableStateFlow<ChannelState>(ChannelState.Connecting)
    override val channel: StateFlow<ChannelState> = _channel

    private val _frames = MutableSharedFlow<String>(extraBufferCapacity = 128)
    override val frames: SharedFlow<String> = _frames

    private var signaling: SignalingClient? = null
    private var pc: PeerConnection? = null
    private var dc: DataChannel? = null
    private var closed = false
    private var budget: Job? = null

    override fun start() {
        if (serverBase.isBlank()) {
            fail(ConnError.NETWORK)
            return
        }
        // 30 s budget: signaling + hole punching + TURN fallback all fit in here
        budget = scope.launch {
            delay(CONNECT_BUDGET_MS)
            fail(ConnError.NETWORK)
        }
        scope.launch {
            val iceServers = withContext(Dispatchers.IO) { fetchIceServers() }
            if (closed) return@launch
            initFactory(appContext)
            val sig = SignalingClient(scope, serverBase, linkId)
            signaling = sig
            createPeer(iceServers)
            observeSignals(sig)
            sig.join()
        }
    }

    override fun send(text: String): Boolean {
        val channel = dc ?: return false
        if (channel.state() != DataChannel.State.OPEN) return false
        return try {
            for (chunk in frameChunks(text)) {
                channel.send(DataChannel.Buffer(ByteBuffer.wrap(chunk), true))
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun close() {
        if (closed) return
        // User-initiated teardown: no error state, the owner (RemoteClient) decides
        closed = true
        budget?.cancel()
        signaling?.sendClose()
        signaling?.close()
        signaling = null
        try {
            pc?.close()
        } catch (e: Exception) {
            // already closed
        }
        pc = null
        dc = null
    }

    // ── Setup ──

    private fun observeSignals(sig: SignalingClient) {
        scope.launch {
            sig.events.collect { signal ->
                if (closed) return@collect
                when (signal) {
                    is Signal.Joined -> scope.launch { makeOffer() }
                    is Signal.Answer -> scope.launch { applyAnswer(signal.sdp) }
                    is Signal.Ice -> scope.launch {
                        try {
                            pc?.addIceCandidate(IceCandidate(signal.candidate, signal.sdpMLineIndex, signal.sdpMid))
                        } catch (e: Exception) {
                            // candidate for a torn-down peer — ignore
                        }
                    }
                    Signal.DeviceGone -> fail(ConnError.OFFLINE)
                    is Signal.Refused -> fail(
                        if (signal.httpCode == 404) ConnError.OFFLINE else ConnError.NETWORK
                    )
                    Signal.Dead -> fail(ConnError.NETWORK)
                }
            }
        }
    }

    private fun createPeer(iceServers: List<PeerConnection.IceServer>) {
        val config = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        val peer = getFactory().createPeerConnection(config, PeerObserver())
        if (peer == null) {
            fail(ConnError.NETWORK)
            return
        }
        pc = peer
        val channel = peer.createDataChannel(DC_LABEL, DataChannel.Init())
        channel.registerObserver(ChannelObserver(channel))
        dc = channel
    }

    private suspend fun makeOffer() {
        val peer = pc ?: return
        val desc = createOfferDesc(peer) ?: return fail(ConnError.NETWORK)
        if (!setLocalDesc(peer, desc)) return fail(ConnError.NETWORK)
        val local = peer.localDescription ?: desc
        signaling?.sendOffer(local.description)
    }

    private suspend fun applyAnswer(sdp: String) {
        val peer = pc ?: return
        val ok = setRemoteAnswer(peer, sdp)
        if (!ok) fail(ConnError.NETWORK)
    }

    private suspend fun setRemoteAnswer(peer: PeerConnection, sdp: String): Boolean =
        suspendCancellableCoroutine { cont ->
            peer.setRemoteDescription(
                object : SdpObserver {
                    override fun onSetSuccess() {
                        cont.resume(true)
                    }

                    override fun onCreateSuccess(sd: SessionDescription) {}

                    override fun onCreateFailure(error: String?) {
                        cont.resume(false)
                    }

                    override fun onSetFailure(error: String?) {
                        cont.resume(false)
                    }
                },
                SessionDescription(SessionDescription.Type.ANSWER, sdp)
            )
        }

    // ── WebRTC plumbing ──

    private suspend fun createOfferDesc(peer: PeerConnection): SessionDescription? =
        suspendCancellableCoroutine { cont ->
            peer.createOffer(
                object : SdpObserver {
                    override fun onCreateSuccess(desc: SessionDescription) {
                        cont.resume(desc)
                    }

                    override fun onSetSuccess() {}

                    override fun onCreateFailure(error: String?) {
                        cont.resume(null)
                    }

                    override fun onSetFailure(error: String?) {}
                },
                MediaConstraints()
            )
        }

    private suspend fun setLocalDesc(peer: PeerConnection, desc: SessionDescription): Boolean =
        suspendCancellableCoroutine { cont ->
            peer.setLocalDescription(
                object : SdpObserver {
                    override fun onSetSuccess() {
                        cont.resume(true)
                    }

                    override fun onCreateSuccess(sd: SessionDescription) {}

                    override fun onCreateFailure(error: String?) {
                        cont.resume(false)
                    }

                    override fun onSetFailure(error: String?) {
                        cont.resume(false)
                    }
                },
                desc
            )
        }

    private inner class PeerObserver : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate) {
            if (closed) return
            signaling?.sendIce(candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex)
        }

        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
            if (closed) return
            if (newState == PeerConnection.PeerConnectionState.FAILED ||
                newState == PeerConnection.PeerConnectionState.CLOSED
            ) {
                fail(ConnError.NETWORK)
            }
        }

        override fun onSignalingChange(state: PeerConnection.SignalingState) {}
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {}
        override fun onIceConnectionReceivingChange(receiving: Boolean) {}
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
        override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) {}
        override fun onAddStream(stream: org.webrtc.MediaStream) {}
        override fun onRemoveStream(stream: org.webrtc.MediaStream) {}
        override fun onDataChannel(channel: DataChannel) {}
        override fun onRenegotiationNeeded() {}
        override fun onAddTrack(receiver: RtpReceiver, streams: Array<org.webrtc.MediaStream>) {}
    }

    private inner class ChannelObserver(private val watched: DataChannel) : DataChannel.Observer {
        override fun onBufferedAmountChange(previousAmount: Long) {}

        override fun onStateChange() {
            if (closed) return
            when (watched.state()) {
                DataChannel.State.OPEN -> {
                    budget?.cancel()
                    budget = null
                    _channel.value = ChannelState.Open
                }
                DataChannel.State.CLOSED -> fail(ConnError.NETWORK)
                else -> Unit
            }
        }

        override fun onMessage(buf: DataChannel.Buffer) {
            if (closed || !buf.binary) return
            val bytes = ByteArray(buf.data.remaining())
            buf.data.get(bytes)
            val msg = feedChunk(bytes) ?: return
            scope.launch { _frames.emit(msg) }
        }
    }

    private fun fail(error: ConnError) {
        if (closed) return
        closed = true
        budget?.cancel()
        budget = null
        signaling?.close()
        signaling = null
        try {
            pc?.close()
        } catch (e: Exception) {
            // already closed
        }
        pc = null
        dc = null
        _channel.value = ChannelState.Closed(error)
    }

    // ── Framing (symmetric with the desktop bridge) ──

    private fun frameChunks(msg: String): List<ByteArray> {
        val payload = msg.toByteArray(Charsets.UTF_8)
        val full = ByteArray(4 + payload.size)
        full[0] = (payload.size ushr 24).toByte()
        full[1] = (payload.size ushr 16).toByte()
        full[2] = (payload.size ushr 8).toByte()
        full[3] = payload.size.toByte()
        System.arraycopy(payload, 0, full, 4, payload.size)
        val chunks = ArrayList<ByteArray>()
        var offset = 0
        while (offset < full.size) {
            val end = minOf(offset + CHUNK_SIZE, full.size)
            chunks.add(full.copyOfRange(offset, end))
            offset = end
        }
        return chunks
    }

    private var acc = ByteArray(0)

    /** Returns the reassembled JSON frame, or null while incomplete */
    private fun feedChunk(chunk: ByteArray): String? {
        val merged = ByteArray(acc.size + chunk.size)
        System.arraycopy(acc, 0, merged, 0, acc.size)
        System.arraycopy(chunk, 0, merged, acc.size, chunk.size)
        if (merged.size < 4) {
            acc = merged
            return null
        }
        val want = ((merged[0].toInt() and 0xFF) shl 24) or
            ((merged[1].toInt() and 0xFF) shl 16) or
            ((merged[2].toInt() and 0xFF) shl 8) or
            (merged[3].toInt() and 0xFF)
        if (merged.size < 4 + want) {
            acc = merged
            return null
        }
        val msg = String(merged, 4, want, Charsets.UTF_8)
        acc = if (merged.size > 4 + want) merged.copyOfRange(4 + want, merged.size) else ByteArray(0)
        return msg
    }

    // ── ICE config from the relay (public config only) ──

    private suspend fun fetchIceServers(): List<PeerConnection.IceServer> {
        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(6, TimeUnit.SECONDS)
                .readTimeout(6, TimeUnit.SECONDS)
                .build()
            val base = serverBase.trimEnd('/')
            val body = client.newCall(
                Request.Builder().url("$base/ice").build()
            ).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                resp.body?.string() ?: return emptyList()
            }
            val obj = Json.parseToJsonElement(body).jsonObject
            val servers = obj["iceServers"]?.jsonArray ?: return emptyList()
            servers.flatMap { entry ->
                val s = entry as? JsonObject ?: return@flatMap emptyList()
                val urls: List<String> = when (val u = s["urls"]) {
                    is JsonArray -> u.mapNotNull { (it as? JsonPrimitive)?.content }
                    is JsonPrimitive -> u.content.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                    else -> emptyList()
                }
                if (urls.isEmpty()) {
                    emptyList()
                } else {
                    val b = PeerConnection.IceServer.builder(urls)
                    (s["username"] as? JsonPrimitive)?.let { b.setUsername(it.content) }
                    (s["credential"] as? JsonPrimitive)?.let { b.setPassword(it.content) }
                    listOf(b.createIceServer())
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val DC_LABEL = "cc"
        private const val CHUNK_SIZE = 16384
        private const val CONNECT_BUDGET_MS = 30_000L

        @Volatile
        private var factory: PeerConnectionFactory? = null

        private fun initFactory(context: Context) {
            if (factory != null) return
            synchronized(this) {
                if (factory != null) return
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                        .setEnableInternalTracer(false)
                        .createInitializationOptions()
                )
                factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
            }
        }

        private fun getFactory(): PeerConnectionFactory =
            factory ?: throw IllegalStateException("webrtc factory not initialized")
    }
}
