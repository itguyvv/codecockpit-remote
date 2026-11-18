package io.github.itguyvv.codecockpitremote.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.itguyvv.codecockpitremote.R
import io.github.itguyvv.codecockpitremote.net.ConnError
import io.github.itguyvv.codecockpitremote.net.ConnState
import io.github.itguyvv.codecockpitremote.net.FingerprintTls
import io.github.itguyvv.codecockpitremote.net.ServerProfile
import io.github.itguyvv.codecockpitremote.net.UpdateInfo
import io.github.itguyvv.codecockpitremote.ui.theme.Accent
import io.github.itguyvv.codecockpitremote.ui.theme.Danger
import io.github.itguyvv.codecockpitremote.ui.theme.Ok
import io.github.itguyvv.codecockpitremote.ui.theme.TextMid
import io.github.itguyvv.codecockpitremote.vm.AppViewModel

/**
 * Saved desktops. Tapping one connects; pairing and the TOFU fingerprint prompt
 * overlay on top. This is also the "disconnected" home screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServersScreen(vm: AppViewModel) {
    val servers by vm.servers.collectAsStateWithLifecycle()
    val connState by vm.connState.collectAsStateWithLifecycle()
    val connError by vm.connError.collectAsStateWithLifecycle()
    val fingerprint by vm.fingerprintPrompt.collectAsStateWithLifecycle()
    val activeServerId by vm.activeServerId.collectAsStateWithLifecycle()
    val updateState by vm.updateState.collectAsStateWithLifecycle()
    val updateDownloading by vm.updateDownloading.collectAsStateWithLifecycle()
    val appVersion by vm.appVersion.collectAsStateWithLifecycle()

    var showAdd by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf<ServerProfile?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.servers_title)) },
                actions = {
                    IconButton(onClick = { vm.goSettings() }) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.cd_settings))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.servers_add))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            val update = updateState
            if (update is AppViewModel.UpdateState.Available) {
                UpdateBanner(
                    info = update.info,
                    currentVersion = appVersion,
                    downloading = updateDownloading,
                    onDownload = { vm.downloadUpdate(update.info) }
                )
            }
            if (servers.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        stringResource(R.string.servers_empty_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.servers_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMid
                    )
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = { showGuide = true }) {
                        Text(stringResource(R.string.guide_show))
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                items(servers, key = { it.id }) { server ->
                    ServerCard(
                        server = server,
                        isActive = server.id == activeServerId && connState != null,
                        connState = connState,
                        onClick = { vm.connect(server) },
                        onRemove = { confirmRemove = server }
                    )
                }
                item {
                    connError?.let { err ->
                        Text(
                            errorText(err, activeHost(servers, activeServerId)),
                            color = Danger,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                }
            }
        }
    }

    if (showAdd) {
        AddServerDialog(
            onDismiss = { showAdd = false },
            onSave = { name, host, port, secret, linkId ->
                showAdd = false
                vm.addServer(name, host, port, secret, linkId) { vm.connect(it) }
            }
        )
    }

    confirmRemove?.let { server ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text(stringResource(R.string.servers_remove)) },
            text = { Text(stringResource(R.string.servers_remove_confirm, server.name)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.removeServer(server.id)
                    confirmRemove = null
                }) { Text(stringResource(R.string.servers_remove), color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    fingerprint?.let { fp ->
        FingerprintDialog(
            host = activeHost(servers, activeServerId),
            fingerprint = fp,
            onTrust = { vm.answerFingerprint(true) },
            onReject = { vm.answerFingerprint(false) }
        )
    }

    if (showGuide) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxSize()
        ) {
            OnboardingScreen(onFinished = { showGuide = false })
        }
    }
}

private fun activeHost(servers: List<ServerProfile>, activeId: String?): String =
    servers.find { it.id == activeId }?.host ?: ""

/** Shown on the home screen while a newer GitHub release exists; tapping downloads the APK */
@Composable
private fun UpdateBanner(
    info: UpdateInfo,
    currentVersion: String,
    downloading: Boolean,
    onDownload: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                stringResource(R.string.update_title),
                style = MaterialTheme.typography.titleMedium,
                color = Accent
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.update_body, info.tag, currentVersion),
                style = MaterialTheme.typography.bodySmall,
                color = TextMid
            )
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onDownload, enabled = !downloading) {
                Text(
                    stringResource(
                        if (downloading) R.string.update_downloading else R.string.update_download
                    )
                )
            }
        }
    }
}

/** 12345678 → "1234 5678", matching how the desktop displays it */
private fun formatLinkId(id: String): String =
    if (id.length == 8) "${id.slice(0..3)} ${id.slice(4..7)}" else id

@Composable
private fun ServerCard(
    server: ServerProfile,
    isActive: Boolean,
    connState: ConnState?,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = if (isActive) {
            Modifier.border(1.dp, Accent, MaterialTheme.shapes.medium)
        } else {
            Modifier
        }
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    server.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Default.LinkOff,
                        contentDescription = stringResource(R.string.servers_remove),
                        tint = TextMid
                    )
                }
            }
            Text(
                if (server.linkId.isNotBlank()) formatLinkId(server.linkId) else "${server.host}:${server.port}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = TextMid
            )
            Spacer(Modifier.height(6.dp))
            StatusChip(server, connState)
        }
    }
}

@Composable
private fun StatusChip(server: ServerProfile, connState: ConnState?) {
    val (textRes, color) = when {
        connState is ConnState.Ready -> R.string.servers_connected to Ok
        connState is ConnState.Connecting || connState is ConnState.Authing ||
            connState is ConnState.Verifying -> R.string.servers_connecting to TextMid
        connState is ConnState.Failed -> R.string.servers_offline to Danger
        server.token.isNotEmpty() -> R.string.servers_paired to TextMid
        else -> R.string.servers_needs_pairing to TextMid
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(Modifier.size(6.dp))
        Text(stringResource(textRes), style = MaterialTheme.typography.labelSmall, color = TextMid)
    }
}

/** Accepts `host`, or a pasted `host:port` which fills both fields */
private fun onAddressInput(value: String, setHost: (String) -> Unit, setPort: (String) -> Unit) {
    val idx = value.lastIndexOf(':')
    if (idx > 0) {
        val tail = value.substring(idx + 1)
        val port = tail.toIntOrNull()
        if (tail.isNotEmpty() && tail.all { c -> c.isDigit() }) {
            setHost(value.substring(0, idx))
            if (port != null && port in 1024..65535) setPort(tail)
            return
        }
    }
    setHost(value)
}

@Composable
private fun AddServerDialog(onDismiss: () -> Unit, onSave: (name: String, host: String, port: Int, secret: String, linkId: String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("47315") }
    var deviceId by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var showSecret by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var hostErr by remember { mutableStateOf(false) }
    var portErr by remember { mutableStateOf(false) }
    var idErr by remember { mutableStateOf(false) }
    var secretErr by remember { mutableStateOf(false) }
    // 0 = same network (any direct address), 1 = any network (device ID + secret)
    var scenario by remember { mutableStateOf(0) }
    val anyNetwork = scenario == 1

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pair_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    stringResource(R.string.pair_where_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = TextMid
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = scenario == 0,
                        onClick = { scenario = 0 },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text(stringResource(R.string.pair_where_same))
                    }
                    SegmentedButton(
                        selected = scenario == 1,
                        onClick = { scenario = 1 },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text(stringResource(R.string.pair_where_any))
                    }
                }
                Text(
                    stringResource(if (anyNetwork) R.string.pair_hint_any else R.string.pair_hint_same),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMid,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.pair_name)) },
                    placeholder = { Text(stringResource(R.string.pair_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!anyNetwork) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { onAddressInput(it, { host = it }, { port = it }) },
                        label = { Text(stringResource(R.string.pair_host)) },
                        placeholder = { Text(stringResource(R.string.pair_host_hint_same)) },
                        isError = hostErr,
                        supportingText = if (hostErr) {
                            { Text(stringResource(R.string.pair_invalid_host)) }
                        } else {
                            null
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.filter { c -> c.isDigit() }.take(5) },
                        label = { Text(stringResource(R.string.pair_port)) },
                        isError = portErr,
                        supportingText = if (portErr) {
                            { Text(stringResource(R.string.pair_invalid_port)) }
                        } else {
                            null
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = deviceId,
                        onValueChange = {
                            deviceId = it.filter { c -> c.isDigit() }.take(8)
                            idErr = false
                        },
                        label = { Text(stringResource(R.string.pair_id)) },
                        placeholder = { Text(stringResource(R.string.pair_id_hint)) },
                        isError = idErr,
                        supportingText = if (idErr) {
                            { Text(stringResource(R.string.pair_invalid_id)) }
                        } else {
                            null
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = secret,
                    onValueChange = {
                        secret = it
                        secretErr = false
                    },
                    label = { Text(stringResource(R.string.pair_secret)) },
                    placeholder = { Text(stringResource(R.string.pair_secret_hint)) },
                    isError = secretErr,
                    supportingText = if (secretErr) {
                        { Text(stringResource(R.string.pair_invalid_secret)) }
                    } else {
                        null
                    },
                    visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showSecret = !showSecret }) {
                            Icon(
                                if (showSecret) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = stringResource(R.string.pair_show_secret)
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = { showHelp = !showHelp }) {
                    Text(stringResource(R.string.add_help_title))
                }
                if (showHelp) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HelpLine(if (anyNetwork) R.string.add_help_address_any else R.string.add_help_address_same)
                        if (!anyNetwork) HelpLine(R.string.add_help_port)
                        HelpLine(R.string.add_help_secret)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                secretErr = secret.isBlank()
                var ok = !secretErr
                var saveHost = ""
                var savePort = 0
                var saveId = ""
                if (anyNetwork) {
                    idErr = deviceId.length != 8
                    ok = ok && !idErr
                    saveId = deviceId
                } else {
                    val p = port.toIntOrNull() ?: 0
                    hostErr = host.isBlank()
                    portErr = p !in 1024..65535
                    ok = ok && !hostErr && !portErr
                    saveHost = host.trim()
                    savePort = p
                }
                if (!ok) return@TextButton
                val fallbackName = saveId.ifBlank { saveHost }
                onSave(name.ifBlank { fallbackName }, saveHost, savePort, secret.trim(), saveId)
            }) { Text(stringResource(R.string.pair_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

@Composable
private fun HelpLine(textRes: Int) {
    Text(
        stringResource(textRes),
        style = MaterialTheme.typography.bodySmall,
        color = TextMid,
        modifier = Modifier.padding(start = 4.dp)
    )
}

@Composable
private fun FingerprintDialog(host: String, fingerprint: String, onTrust: () -> Unit, onReject: () -> Unit) {
    AlertDialog(
        onDismissRequest = onReject,
        title = { Text(stringResource(R.string.fp_title)) },
        text = {
            Column {
                Text(stringResource(R.string.fp_body, host), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                Text(
                    FingerprintTls.grouped(fingerprint),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onTrust) { Text(stringResource(R.string.fp_trust)) }
        },
        dismissButton = {
            TextButton(onClick = onReject) { Text(stringResource(R.string.fp_reject)) }
        }
    )
}

@Composable
fun errorText(err: ConnError, host: String): String = when (err) {
    ConnError.NETWORK -> stringResource(R.string.err_network, host)
    ConnError.TLS -> stringResource(R.string.err_tls)
    ConnError.OFFLINE -> stringResource(R.string.err_offline)
    ConnError.BAD_SECRET -> stringResource(R.string.err_auth_secret)
    ConnError.BAD_TOKEN -> stringResource(R.string.err_auth_token)
    ConnError.BANNED -> stringResource(R.string.err_banned)
    ConnError.TOO_MANY_CLIENTS -> stringResource(R.string.err_too_many_clients)
    ConnError.FINGERPRINT_MISMATCH -> stringResource(R.string.fp_mismatch, host)
}
