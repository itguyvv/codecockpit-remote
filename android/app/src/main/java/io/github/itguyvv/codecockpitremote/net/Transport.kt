package io.github.itguyvv.codecockpitremote.net

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** Connection progress of the underlying transport, before/independent of auth */
sealed interface ChannelState {
    data object Connecting : ChannelState
    /** Channel up, fingerprint captured — waiting for the user's TOFU decision (WSS only) */
    data class Verifying(val fingerprint: String) : ChannelState
    data object Open : ChannelState
    data class Closed(val error: ConnError) : ChannelState
}

/**
 * Byte transport between the phone and the desktop. Both implementations (WSS for
 * Same network, WebRTC DataChannel for Any network) carry the same JSON text
 * frames, so [RemoteClient]'s auth and protocol logic is transport-agnostic.
 */
interface Transport {
    /** Lifecycle of the raw channel; reaches [ChannelState.Open] once frames can flow */
    val channel: StateFlow<ChannelState>

    /** Complete JSON text frames, reassembled (DataChannel chunking is transparent here) */
    val frames: SharedFlow<String>

    /** Open the channel. Non-blocking; progress arrives on [channel]. */
    fun start()

    /**
     * Send one complete JSON text frame. Returns false when the channel is not
     * open (the message is dropped; caller may treat this as a dead connection).
     */
    fun send(text: String): Boolean

    /** Tear down the channel and release resources. Idempotent. */
    fun close()
}
