package io.github.itguyvv.codecockpitremote.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.itguyvv.codecockpitremote.data.ConnectionStore
import io.github.itguyvv.codecockpitremote.net.ConnError
import io.github.itguyvv.codecockpitremote.net.ConnState
import io.github.itguyvv.codecockpitremote.net.RemoteClient
import io.github.itguyvv.codecockpitremote.net.ServerProfile
import io.github.itguyvv.codecockpitremote.net.UpdateChecker
import io.github.itguyvv.codecockpitremote.net.UpdateInfo
import io.github.itguyvv.codecockpitremote.net.UpdateResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.itguyvv.codecockpitremote.protocol.RemoteMessage
import io.github.itguyvv.codecockpitremote.protocol.ServerEvent
import io.github.itguyvv.codecockpitremote.protocol.SessionStatus
import io.github.itguyvv.codecockpitremote.protocol.SessionSummary
import io.github.itguyvv.codecockpitremote.protocol.attachRequest
import io.github.itguyvv.codecockpitremote.protocol.detachRequest
import io.github.itguyvv.codecockpitremote.protocol.inputRequest
import io.github.itguyvv.codecockpitremote.protocol.listRequest
import io.github.itguyvv.codecockpitremote.protocol.modeRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Chat prompt terminator: the desktop writes straight into the PTY, so this is the Enter key */
private const val PROMPT_SUFFIX = "\r"

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val store = ConnectionStore(application)

    val servers: StateFlow<List<ServerProfile>> = store.servers.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )

    /** Null until first load — gates onboarding/lock so the UI never flashes the wrong state */
    val onboardingDone: StateFlow<Boolean?> = store.onboardingDone
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val appLockEnabled: StateFlow<Boolean> = store.appLockEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setOnboardingDone() {
        viewModelScope.launch { store.setOnboardingDone() }
    }

    fun setAppLockEnabled(enabled: Boolean) {
        viewModelScope.launch { store.setAppLockEnabled(enabled) }
    }

    // ── Self-update (GitHub releases) ──
    sealed interface UpdateState {
        data object None : UpdateState
        data class Available(val info: UpdateInfo) : UpdateState
        data object Latest : UpdateState
        data object Failed : UpdateState
    }

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.None)
    val updateState: StateFlow<UpdateState> = _updateState
    private val _updateDownloading = MutableStateFlow(false)
    val updateDownloading: StateFlow<Boolean> = _updateDownloading

    /** The running version name, shown in the update banner text */
    private val _appVersion = MutableStateFlow("")
    val appVersion: StateFlow<String> = _appVersion

    /** DownloadManager id of the in-flight APK; the activity matches completion broadcasts against it */
    var lastDownloadId: Long = -1
        private set
    private var downloadedTag: String? = null

    init {
        checkForUpdate(quiet = true)
    }

    /** quiet = startup check: failures and no-update stay silent; explicit checks report both */
    fun checkForUpdate(quiet: Boolean) {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val current = try {
                ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
            } catch (e: Exception) {
                ""
            }
            _appVersion.value = current
            val res = withContext(Dispatchers.IO) { UpdateChecker.check(current) }
            _updateState.value = when (res) {
                is UpdateResult.Available -> UpdateState.Available(res.info)
                UpdateResult.Latest -> UpdateState.Latest
                UpdateResult.Failed -> if (quiet) UpdateState.None else UpdateState.Failed
            }
        }
    }

    fun downloadUpdate(info: UpdateInfo) {
        lastDownloadId = UpdateChecker.download(getApplication(), info)
        downloadedTag = info.tag
        _updateDownloading.value = true
    }

    /** Called by the activity when DownloadManager broadcasts the download finished */
    fun onDownloadComplete(id: Long) {
        if (id != lastDownloadId) return
        _updateDownloading.value = false
        downloadedTag?.let { UpdateChecker.openApk(getApplication(), it) }
    }

    // ── Navigation ──
    sealed interface Screen {
        data object Servers : Screen
        data object Sessions : Screen
        data object Settings : Screen
        data class Chat(val sessionId: String) : Screen
    }

    private val _screen = MutableStateFlow<Screen>(Screen.Servers)
    val screen: StateFlow<Screen> = _screen

    // ── Connection ──
    private var client: RemoteClient? = null
    private var active: ServerProfile? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempt = 0

    /** Null = no connection attempt running */
    private val _connState = MutableStateFlow<ConnState?>(null)
    val connState: StateFlow<ConnState?> = _connState

    /** The desktop a connection attempt is (or was last) running against */
    private val _activeServerId = MutableStateFlow<String?>(null)
    val activeServerId: StateFlow<String?> = _activeServerId

    /** Last terminal error; UI maps it to a localized message */
    private val _connError = MutableStateFlow<ConnError?>(null)
    val connError: StateFlow<ConnError?> = _connError

    /** Set while a TOFU decision is pending */
    private val _fingerprintPrompt = MutableStateFlow<String?>(null)
    val fingerprintPrompt: StateFlow<String?> = _fingerprintPrompt
    private var fingerprintDeferred: CompletableDeferred<Boolean>? = null

    // ── Sessions ──
    private val _sessions = MutableStateFlow<List<SessionSummary>>(emptyList())
    val sessions: StateFlow<List<SessionSummary>> = _sessions

    // ── Chat (attached session) ──
    private var attachedId: String? = null
    private val _chatMessages = MutableStateFlow<List<RemoteMessage>>(emptyList())
    val chatMessages: StateFlow<List<RemoteMessage>> = _chatMessages
    private val _chatStatus = MutableStateFlow<SessionStatus?>(null)
    val chatStatus: StateFlow<SessionStatus?> = _chatStatus
    private val _chatBusy = MutableStateFlow(false)
    val chatBusy: StateFlow<Boolean> = _chatBusy
    private val _chatBg = MutableStateFlow(0)
    val chatBg: StateFlow<Int> = _chatBg

    fun addServer(name: String, host: String, port: Int, secret: String, onDone: (ServerProfile) -> Unit) {
        viewModelScope.launch {
            // The same host:port twice would only ever race one connection
            if (servers.value.any { it.host == host.trim() && it.port == port }) return@launch
            val profile = store.add(name, host, port, secret)
            onDone(profile)
        }
    }

    fun updateServerSecret(id: String, secret: String) {
        viewModelScope.launch {
            servers.value.find { it.id == id }?.let {
                store.updateProfile(it.copy(secret = secret))
            }
        }
    }

    fun removeServer(id: String) {
        viewModelScope.launch {
            if (active?.id == id) disconnect()
            store.remove(id)
        }
    }

    /** Connect (and keep reconnecting) to the given desktop */
    fun connect(profile: ServerProfile) {
        disconnect()
        active = profile
        _activeServerId.value = profile.id
        reconnectAttempt = 0
        openConnection(profile)
    }

    fun disconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        attachedId = null
        _activeServerId.value = null
        _chatMessages.value = emptyList()
        _chatStatus.value = null
        _chatBusy.value = false
        _chatBg.value = 0
        client?.close()
        client = null
        active = null
        _connState.value = null
        _connError.value = null
        _sessions.value = emptyList()
        if (_screen.value !is Screen.Servers) _screen.value = Screen.Servers
    }

    private fun openConnection(profile: ServerProfile) {
        _connError.value = null
        _connState.value = ConnState.Connecting
        val c = RemoteClient(
            scope = viewModelScope,
            server = profile,
            onFingerprintTrusted = { fp -> persistActive { it.copy(fingerprint = fp) } },
            onTokenIssued = { token -> persistActive { it.copy(token = token) } },
            decideFingerprint = { fp ->
                fingerprintDeferred = CompletableDeferred()
                _fingerprintPrompt.value = fp
                val ok = fingerprintDeferred!!.await()
                _fingerprintPrompt.value = null
                ok
            }
        ).connect(secret = profile.secret)
        client = c
        viewModelScope.launch {
            c.state.collect { st ->
                _connState.value = st
                if (st is ConnState.Ready) {
                    // Fresh (re)connection: refresh the list and re-attach an open chat
                    c.send(listRequest())
                    attachedId?.let { c.send(attachRequest(it)) }
                }
                if (st is ConnState.Failed) {
                    _connError.value = st.error
                    scheduleReconnect(st.error)
                }
            }
        }
        viewModelScope.launch {
            c.events.collect { onEvent(it) }
        }
    }

    /** Auto-repair loop: only transient failures retry; auth/fingerprint errors need the user */
    private fun scheduleReconnect(error: ConnError) {
        val profile = active ?: return
        val retryable = error == ConnError.NETWORK || error == ConnError.TLS
        if (!retryable) return
        reconnectJob?.cancel()
        reconnectJob = viewModelScope.launch {
            val delayMs = (3_000L * (1L shl reconnectAttempt.coerceAtMost(4))).coerceAtMost(30_000L)
            reconnectAttempt++
            delay(delayMs)
            if (active?.id == profile.id) openConnection(profile)
        }
    }

    private fun onEvent(event: ServerEvent) {
        when (event) {
            is ServerEvent.Sessions -> _sessions.value = event.list
            is ServerEvent.ConvSnapshot -> if (event.sessionId == attachedId) {
                _chatMessages.value = event.messages.sortedBy { it.ts }
            }
            is ServerEvent.ConvAppend -> if (event.sessionId == attachedId) {
                _chatMessages.value = upsert(_chatMessages.value, event.messages)
            }
            is ServerEvent.Busy -> if (event.sessionId == attachedId) {
                _chatBusy.value = event.busy
                _chatBg.value = event.bg
            }
            is ServerEvent.Status -> if (event.sessionId == attachedId) {
                _chatStatus.value = event.status
            }
            is ServerEvent.Exit -> {
                _sessions.value = _sessions.value.map {
                    if (it.id == event.sessionId) it.copy(status = "exited") else it
                }
            }
            is ServerEvent.SessionId -> Unit
            is ServerEvent.Error -> Unit
            ServerEvent.Pong -> Unit
            else -> Unit
        }
    }

    private fun upsert(current: List<RemoteMessage>, incoming: List<RemoteMessage>): List<RemoteMessage> {
        if (incoming.isEmpty()) return current
        val map = current.associateByTo(LinkedHashMap()) { it.id }
        for (m in incoming) map[m.id] = m
        return map.values.sortedBy { it.ts }
    }

    private fun persistActive(patch: (ServerProfile) -> ServerProfile) {
        val current = active ?: return
        val updated = patch(current)
        active = updated
        viewModelScope.launch { store.updateProfile(updated) }
    }

    // ── Requests ──

    fun refreshSessions() {
        client?.send(listRequest())
    }

    fun answerFingerprint(trusted: Boolean) {
        fingerprintDeferred?.complete(trusted)
    }

    fun enterChat(summary: SessionSummary) {
        attachedId = summary.id
        _chatMessages.value = emptyList()
        _chatStatus.value = null
        _chatBusy.value = summary.busy
        client?.send(attachRequest(summary.id))
        _screen.value = Screen.Chat(summary.id)
    }

    fun leaveChat() {
        attachedId?.let { client?.send(detachRequest(it)) }
        attachedId = null
        _chatMessages.value = emptyList()
        _chatStatus.value = null
        _screen.value = Screen.Sessions
    }

    fun sendPrompt(text: String) {
        val id = attachedId ?: return
        client?.send(inputRequest(id, text + PROMPT_SUFFIX))
    }

    fun setMode(mode: String) {
        val id = attachedId ?: return
        client?.send(modeRequest(id, mode))
    }

    fun goSessions() {
        if (_screen.value is Screen.Chat) leaveChat() else _screen.value = Screen.Sessions
    }

    fun goServers() {
        disconnect()
    }

    fun goSettings() {
        _screen.value = Screen.Settings
    }

    /** From Settings there is no disconnect — just go back home */
    fun closeSettings() {
        _screen.value = Screen.Servers
    }

    override fun onCleared() {
        client?.close()
        client = null
        super.onCleared()
    }
}
