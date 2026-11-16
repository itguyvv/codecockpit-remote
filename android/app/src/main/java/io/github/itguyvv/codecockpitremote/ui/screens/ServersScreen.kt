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
        if (servers.isEmpty()) {
            Column(
                modifier = Modifier
                    .padding(padding)
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
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
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

    if (showAdd) {
        AddServerDialog(
            onDismiss = { showAdd = false },
            onSave = { name, host, port, secret ->
                showAdd = false
                vm.addServer(name, host, port, secret) { vm.connect(it) }
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
                "${server.host}:${server.port}",
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
private fun AddServerDialog(onDismiss: () -> Unit, onSave: (name: String, host: String, port: Int, secret: String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("47315") }
    var secret by remember { mutableStateOf("") }
    var showSecret by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var hostErr by remember { mutableStateOf(false) }
    var portErr by remember { mutableStateOf(false) }
    var secretErr by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pair_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.pair_name)) },
                    placeholder = { Text(stringResource(R.string.pair_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = host,
                    onValueChange = { onAddressInput(it, { host = it }, { port = it }) },
                    label = { Text(stringResource(R.string.pair_host)) },
                    placeholder = { Text(stringResource(R.string.pair_host_hint)) },
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
                        HelpLine(R.string.add_help_address)
                        HelpLine(R.string.add_help_port)
                        HelpLine(R.string.add_help_secret)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val p = port.toIntOrNull() ?: 0
                hostErr = host.isBlank()
                portErr = p !in 1024..65535
                secretErr = secret.isBlank()
                if (hostErr || portErr || secretErr) return@TextButton
                onSave(name.ifBlank { host.trim() }, host.trim(), p, secret.trim())
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
    ConnError.BAD_SECRET -> stringResource(R.string.err_auth_secret)
    ConnError.BAD_TOKEN -> stringResource(R.string.err_auth_token)
    ConnError.BANNED -> stringResource(R.string.err_banned)
    ConnError.TOO_MANY_CLIENTS -> stringResource(R.string.err_too_many_clients)
    ConnError.FINGERPRINT_MISMATCH -> stringResource(R.string.fp_mismatch, host)
}
