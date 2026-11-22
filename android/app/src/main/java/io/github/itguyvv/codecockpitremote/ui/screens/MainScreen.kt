package io.github.itguyvv.codecockpitremote.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.view.HapticFeedbackConstants
import io.github.itguyvv.codecockpitremote.R
import io.github.itguyvv.codecockpitremote.net.ConnState
import io.github.itguyvv.codecockpitremote.protocol.NativeSessionInfo
import io.github.itguyvv.codecockpitremote.protocol.SessionStatus
import io.github.itguyvv.codecockpitremote.protocol.SessionSummary
import io.github.itguyvv.codecockpitremote.ui.theme.Accent
import io.github.itguyvv.codecockpitremote.ui.theme.Busy
import io.github.itguyvv.codecockpitremote.ui.theme.Danger
import io.github.itguyvv.codecockpitremote.ui.theme.Idle
import io.github.itguyvv.codecockpitremote.ui.theme.Ok
import io.github.itguyvv.codecockpitremote.ui.theme.Surface2
import io.github.itguyvv.codecockpitremote.ui.theme.TextMid
import io.github.itguyvv.codecockpitremote.vm.AppViewModel
import kotlinx.coroutines.launch

private val ENGINES = listOf("claude", "codex", "opencode")
private val MODES = listOf("default", "acceptEdits", "plan", "bypassPermissions")

/** Desktop-grade workbench: hamburger-toggled sidebar drawer, chat pane main. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: AppViewModel) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val selectedId by vm.selectedId.collectAsStateWithLifecycle()
    val connState by vm.connState.collectAsStateWithLifecycle()
    val opError by vm.lastOpError.collectAsStateWithLifecycle()
    val chatStatus by vm.chatStatus.collectAsStateWithLifecycle()
    val servers by vm.servers.collectAsStateWithLifecycle()
    val activeServerId by vm.activeServerId.collectAsStateWithLifecycle()
    val reconnectAttempt by vm.reconnectAttempt.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showNew by remember { mutableStateOf(false) }
    var newCwd by remember { mutableStateOf("") }
    var showHistory by remember { mutableStateOf(false) }
    var confirmKill by remember { mutableStateOf<SessionSummary?>(null) }
    var renaming by remember { mutableStateOf<SessionSummary?>(null) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    val selected = sessions.find { it.id == selectedId }
    val ctx = LocalContext.current
    val view = LocalView.current
    val createdMsg = stringResource(R.string.wb_created)
    val renamedMsg = stringResource(R.string.wb_renamed)
    val killedMsg = stringResource(R.string.wb_killed)

    LaunchedEffect(opError) {
        opError?.let {
            snackbar.showSnackbar(ctx.getString(R.string.wb_op_failed, it))
            vm.clearOpError()
        }
    }

    BackHandler {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            selectedId != null -> vm.deselect()
            else -> vm.goServers()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                SidebarPanel(
                    vm = vm,
                    sessions = sessions,
                    onOpen = { s ->
                        vm.selectSession(s)
                        scope.launch { drawerState.close() }
                    },
                    onRename = { renaming = it },
                    onKill = { confirmKill = it },
                    onNewInCwd = {
                        newCwd = it
                        showNew = true
                        scope.launch { drawerState.close() }
                    },
                    onNew = {
                        newCwd = ""
                        showNew = true
                        scope.launch { drawerState.close() }
                    },
                    onHistory = {
                        showHistory = true
                        scope.launch { drawerState.close() }
                    },
                    onClose = { scope.launch { drawerState.close() } }
                )
            }
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.wb_open_sidebar))
                        }
                    },
                    title = {
                        Column {
                            Text(
                                selected?.let { it.title.ifBlank { folderName(it.cwd) } }
                                    ?: stringResource(R.string.sessions_title),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (selected != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        Modifier
                                            .size(7.dp)
                                            .background(dotColor(selected), CircleShape)
                                    )
                                    Text(
                                        stateLine(selected, chatStatus),
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = stateColor(selected),
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Text("·", style = MaterialTheme.typography.labelSmall, color = TextMid)
                                    ModeMenu(vm, selected.permissionMode)
                                }
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { vm.refreshSessions() }) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.sessions_refresh))
                        }
                        IconButton(onClick = { vm.goServers() }) {
                            Icon(Icons.Default.LinkOff, contentDescription = stringResource(R.string.sessions_disconnect))
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                if (connState !is ConnState.Ready) {
                    val host = servers.find { it.id == activeServerId }?.let { it.name.ifBlank { it.host } } ?: ""
                    ReconnectBanner(host, reconnectAttempt)
                }
                if (selected == null) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Outlined.Terminal,
                            contentDescription = null,
                            tint = TextMid,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.wb_select_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMid,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(20.dp))
                        Button(onClick = {
                            newCwd = ""
                            showNew = true
                        }) { Text(stringResource(R.string.wb_new_session)) }
                    }
                } else {
                    ChatScreen(vm, selected.id)
                }
            }
        }
    }

    if (showNew) {
        NewSessionDialog(
            vm = vm,
            initialCwd = newCwd,
            onDismiss = { showNew = false },
            onCreated = {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                scope.launch { snackbar.showSnackbar(createdMsg) }
            }
        )
    }

    if (showHistory) {
        HistorySheet(vm = vm, onDismiss = { showHistory = false })
    }

    confirmKill?.let { s ->
        AlertDialog(
            onDismissRequest = { confirmKill = null },
            title = { Text(stringResource(R.string.wb_kill)) },
            text = { Text(stringResource(R.string.wb_kill_confirm, s.title.ifBlank { s.cwd })) },
            confirmButton = {
                TextButton(onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    vm.killSession(s.id)
                    scope.launch { snackbar.showSnackbar(killedMsg) }
                    confirmKill = null
                }) { Text(stringResource(R.string.wb_kill), color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmKill = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    renaming?.let { s ->
        RenameDialog(
            initial = s.title,
            onDismiss = { renaming = null },
            onSave = {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                vm.renameSession(s.id, it)
                scope.launch { snackbar.showSnackbar(renamedMsg) }
                renaming = null
            }
        )
    }
}

/** Shown on the workbench while the client backs off between reconnect attempts */
@Composable
private fun ReconnectBanner(host: String, attempt: Int) {
    Surface(
        color = Surface2,
        contentColor = TextMid,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 1.5.dp
            )
            Spacer(Modifier.size(10.dp))
            Text(
                stringResource(R.string.sessions_reconnecting, host, attempt),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ModeMenu(vm: AppViewModel, current: String) {
    var open by remember { mutableStateOf(false) }
    var confirmBypass by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { open = true }
        ) {
            Text(
                modeLabel(current),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                color = if (current == "plan") Accent else TextMid
            )
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = stringResource(R.string.chat_mode_label),
                tint = TextMid,
                modifier = Modifier.size(14.dp)
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MODES.forEach { mode ->
                DropdownMenuItem(
                    text = {
                        Text(
                            modeLabel(mode),
                            color = if (mode == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        open = false
                        // Same danger gate as the desktop: one tap must not arm the agent
                        if (mode == "bypassPermissions" && current != "bypassPermissions") confirmBypass = true
                        else vm.setMode(mode)
                    }
                )
            }
        }
    }
    if (confirmBypass) {
        AlertDialog(
            onDismissRequest = { confirmBypass = false },
            title = { Text(stringResource(R.string.mode_bypassPermissions)) },
            text = { Text(stringResource(R.string.mode_bypass_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmBypass = false
                    vm.setMode("bypassPermissions")
                }) {
                    Text(stringResource(R.string.common_ok), color = Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmBypass = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

/** Grouped sidebar: cwd folders with collapsible headers, per-session row menu, top actions */
@Composable
private fun SidebarPanel(
    vm: AppViewModel,
    sessions: List<SessionSummary>,
    onOpen: (SessionSummary) -> Unit,
    onRename: (SessionSummary) -> Unit,
    onKill: (SessionSummary) -> Unit,
    onNewInCwd: (String) -> Unit,
    onNew: () -> Unit,
    onHistory: () -> Unit,
    onClose: () -> Unit
) {
    val groups = remember(sessions) {
        sessions.groupBy { folderName(it.cwd) }.toSortedMap()
    }
    val collapsed = remember { mutableStateMapOf<String, Boolean>() }
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                stringResource(R.string.sessions_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_cancel))
            }
        }
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = onNew) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(4.dp))
                Text(stringResource(R.string.wb_new_session))
            }
            TextButton(onClick = onHistory) {
                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(4.dp))
                Text(stringResource(R.string.wb_history))
            }
        }
        Spacer(Modifier.height(4.dp))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            if (groups.isEmpty()) {
                item(key = "empty") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            stringResource(R.string.sessions_empty_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.sessions_empty_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMid,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            groups.forEach { (folder, list) ->
                item(key = "g:$folder") {
                    val isCollapsed = collapsed[folder] ?: false
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { collapsed[folder] = !isCollapsed }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            folder,
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            list.size.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMid
                        )
                        IconButton(onClick = { onNewInCwd(list.firstOrNull()?.cwd ?: folder) }) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = stringResource(R.string.wb_new_session),
                                tint = TextMid,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = stringResource(R.string.wb_toggle_group),
                            tint = TextMid,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (!(collapsed[folder] ?: false)) {
                    items(list, key = { it.id }) { s ->
                        SessionRow(
                            session = s,
                            onOpen = { onOpen(s) },
                            onRename = { onRename(s) },
                            onKill = { onKill(s) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionRow(
    session: SessionSummary,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onKill: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = 24.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)
    ) {
        Box(Modifier.size(8.dp).background(dotColor(session), CircleShape))
        Spacer(Modifier.size(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                session.title.ifBlank { folderName(session.cwd) },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                session.engine,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = TextMid
            )
        }
        Text(
            when {
                session.status == "exited" -> stringResource(R.string.wb_state_exited)
                session.busy -> stringResource(R.string.sessions_busy)
                session.bg > 0 -> stringResource(R.string.wb_state_bg)
                else -> stringResource(R.string.wb_state_idle)
            },
            style = MaterialTheme.typography.labelSmall,
            color = stateColor(session)
        )
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = null, tint = TextMid)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.wb_open_sidebar)) },
                    onClick = {
                        menuOpen = false
                        onOpen()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.wb_rename)) },
                    onClick = {
                        menuOpen = false
                        onRename()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.wb_kill), color = Danger) },
                    onClick = {
                        menuOpen = false
                        onKill()
                    }
                )
            }
        }
    }
}

@Composable
private fun RenameDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wb_rename_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(text.trim()) },
                enabled = text.isNotBlank()
            ) { Text(stringResource(R.string.common_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

/** Launch a new desktop session: engine chip row, mode + folder pickers, optional title */
@Composable
private fun NewSessionDialog(
    vm: AppViewModel,
    initialCwd: String,
    onDismiss: () -> Unit,
    onCreated: () -> Unit
) {
    var engine by remember { mutableStateOf("claude") }
    // Default to full permissions (desktop parity: new sessions work out of the box)
    var mode by remember { mutableStateOf("bypassPermissions") }
    var cwd by remember { mutableStateOf(initialCwd) }
    var title by remember { mutableStateOf("") }
    var modeOpen by remember { mutableStateOf(false) }
    var cwdOpen by remember { mutableStateOf(false) }
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()

    // Folder suggestions come from everywhere the desktop has seen: live sessions
    // plus the engine's native history (the user asked for history dirs, not a
    // whole-computer browser)
    LaunchedEffect(engine) { vm.loadHistory(engine) }
    val knownCwds = remember(sessions, history) {
        (sessions.map { it.cwd } + (history?.map { it.cwd } ?: emptyList()))
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wb_new_session_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.wb_engine),
                    style = MaterialTheme.typography.labelLarge,
                    color = TextMid
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ENGINES.forEach { e ->
                        FilterChip(
                            selected = engine == e,
                            onClick = { engine = e },
                            label = {
                                // "opencode" just fits three chips; one size down stops it wrapping
                                Text(
                                    e,
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1
                                )
                            }
                        )
                    }
                }
                Box {
                    OutlinedTextField(
                        value = modeLabel(mode),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.chat_mode_label)) },
                        trailingIcon = {
                            IconButton(onClick = { modeOpen = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(expanded = modeOpen, onDismissRequest = { modeOpen = false }) {
                        MODES.forEach { m ->
                            DropdownMenuItem(text = { Text(modeLabel(m)) }, onClick = {
                                mode = m
                                modeOpen = false
                            })
                        }
                    }
                }
                Box {
                    OutlinedTextField(
                        value = cwd,
                        onValueChange = { cwd = it },
                        label = { Text(stringResource(R.string.wb_cwd)) },
                        placeholder = { Text(stringResource(R.string.wb_cwd_hint), color = TextMid) },
                        trailingIcon = if (knownCwds.isNotEmpty()) {
                            {
                                IconButton(onClick = { cwdOpen = true }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            }
                        } else {
                            null
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(expanded = cwdOpen, onDismissRequest = { cwdOpen = false }) {
                        knownCwds.forEach { c ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            folderName(c),
                                            fontFamily = FontFamily.Monospace,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            c,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextMid,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                },
                                onClick = {
                                    cwd = c
                                    cwdOpen = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.pair_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    vm.createSession(cwd.trim(), mode, engine, title.trim())
                    onCreated()
                    onDismiss()
                },
                enabled = cwd.isNotBlank()
            ) { Text(stringResource(R.string.wb_create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

/** Engine history browser: pick engine, grouped by folder, resume or delete entries */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistorySheet(vm: AppViewModel, onDismiss: () -> Unit) {
    // Skip the partial anchor: the sheet opens full-height and content swaps (loading →
    // list → grouped) never re-anchor it back down to half screen
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val engine by vm.historyEngine.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val view = LocalView.current
    var confirmResume by remember { mutableStateOf<NativeSessionInfo?>(null) }
    var confirmDelete by remember { mutableStateOf<NativeSessionInfo?>(null) }

    LaunchedEffect(engine) { vm.loadHistory(engine) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.wb_history),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ENGINES.forEach { e ->
                    FilterChip(
                        selected = engine == e,
                        onClick = { vm.loadHistory(e) },
                        label = {
                            Text(
                                e,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                        }
                    )
                }
            }
            val list = history
            if (list == null) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (list.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.wb_history_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMid
                    )
                }
            } else {
                // Two-level tree: directory nodes collapse their sessions (desktop parity)
                val groups = remember(list) {
                    list.groupBy { folderName(it.cwd) }.toSortedMap()
                }
                val expanded = remember { mutableStateMapOf<String, Boolean>() }
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    groups.forEach { (folder, items) ->
                        item(key = "h:$folder") {
                            val open = expanded[folder] ?: false
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expanded[folder] = !open }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = stringResource(R.string.wb_toggle_group),
                                    tint = TextMid,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .rotate(if (open) 0f else -90f)
                                )
                                Spacer(Modifier.size(4.dp))
                                Text(
                                    folder,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    items.size.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMid
                                )
                            }
                        }
                        if (expanded[folder] ?: false) {
                            items(items, key = { it.sessionId }) { info ->
                                HistoryRow(
                                    info = info,
                                    onResume = { confirmResume = info },
                                    onDelete = { confirmDelete = info }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    confirmResume?.let { info ->
        AlertDialog(
            onDismissRequest = { confirmResume = null },
            title = { Text(stringResource(R.string.wb_history_resume)) },
            text = { Text(info.title.ifBlank { folderName(info.cwd) }) },
            confirmButton = {
                TextButton(onClick = {
                    vm.createSession(
                        cwd = info.cwd,
                        mode = info.permissionMode.ifBlank { "default" },
                        engine = info.engine.ifBlank { "claude" },
                        title = "",
                        resumeId = info.sessionId
                    )
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    confirmResume = null
                    onDismiss()
                }) { Text(stringResource(R.string.wb_history_resume)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmResume = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    confirmDelete?.let { info ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.wb_history_delete)) },
            text = { Text(stringResource(R.string.wb_history_delete_confirm, info.title.ifBlank { folderName(info.cwd) })) },
            confirmButton = {
                TextButton(onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    vm.deleteHistory(info.sessionId)
                    confirmDelete = null
                }) { Text(stringResource(R.string.wb_history_delete), color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
private fun HistoryRow(info: NativeSessionInfo, onResume: () -> Unit, onDelete: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onResume)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                info.title.ifBlank { folderName(info.cwd) },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                info.cwd,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = TextMid,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.wb_history_delete), tint = TextMid)
        }
    }
}

/** Permission mode display name for menus and pickers */
@Composable
internal fun modeLabel(mode: String): String = when (mode) {
    "default" -> stringResource(R.string.mode_default)
    "acceptEdits" -> stringResource(R.string.mode_acceptEdits)
    "plan" -> stringResource(R.string.mode_plan)
    "bypassPermissions" -> stringResource(R.string.mode_bypassPermissions)
    else -> mode
}

/** Top-bar state line: the desktop badge text, plus the running tool while working */
@Composable
private fun stateLine(s: SessionSummary, status: SessionStatus?): String {
    val label = when {
        s.status == "exited" -> stringResource(R.string.wb_state_exited)
        s.busy -> stringResource(R.string.sessions_busy)
        s.bg > 0 -> stringResource(R.string.wb_state_bg)
        else -> stringResource(R.string.wb_state_idle)
    }
    val tool = status?.tool?.takeIf { s.busy && it.isNotBlank() } ?: return label
    return stringResource(R.string.wb_state_tool, label, tool)
}

/** Text color for the state label (idle stays neutral gray) */
@Composable
internal fun stateColor(s: SessionSummary): Color = when {
    s.status == "exited" -> Danger
    s.busy -> Ok
    s.bg > 0 -> Accent
    else -> TextMid
}

/**
 * Dot semantics copied from the desktop sidebar: busy = green pulse, exited = red,
 * background shells = accent, idle live session = gray (desktop #6e7681).
 */
private fun dotColor(s: SessionSummary): Color = when {
    s.status == "exited" -> Danger
    s.busy -> Ok
    s.bg > 0 -> Accent
    else -> Idle
}

/** Basename of either path separator; empty cwd falls back to a dash */
private fun folderName(cwd: String): String =
    cwd.trim().substringAfterLast('/').substringAfterLast('\\').ifBlank { "-" }
