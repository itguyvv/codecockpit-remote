package io.github.itguyvv.codecockpitremote.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.itguyvv.codecockpitremote.R
import io.github.itguyvv.codecockpitremote.net.ConnState
import io.github.itguyvv.codecockpitremote.protocol.RemoteMessage
import io.github.itguyvv.codecockpitremote.protocol.SessionStatus
import io.github.itguyvv.codecockpitremote.protocol.SessionSummary
import io.github.itguyvv.codecockpitremote.ui.theme.Border
import io.github.itguyvv.codecockpitremote.ui.theme.Danger
import io.github.itguyvv.codecockpitremote.ui.theme.Ok
import io.github.itguyvv.codecockpitremote.ui.theme.Surface2
import io.github.itguyvv.codecockpitremote.ui.theme.TextMid
import io.github.itguyvv.codecockpitremote.vm.AppViewModel

/**
 * Chat-style monitor for one attached session: streamed conversation, live stats,
 * permission-mode switch and a prompt input that the desktop writes into the PTY.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: AppViewModel, sessionId: String) {
    val messages by vm.chatMessages.collectAsStateWithLifecycle()
    val status by vm.chatStatus.collectAsStateWithLifecycle()
    val busy by vm.chatBusy.collectAsStateWithLifecycle()
    val bg by vm.chatBg.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val connState by vm.connState.collectAsStateWithLifecycle()
    val summary = sessions.find { it.id == sessionId }

    BackHandler { vm.goSessions() }

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    var input by remember { mutableStateOf("") }
    var modeMenuOpen by remember { mutableStateOf(false) }
    val exited = summary?.status == "exited"
    val canSend = input.isNotBlank() && !exited && connState is ConnState.Ready

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { vm.goSessions() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                title = {
                    Column {
                        Text(
                            summary?.title ?: stringResource(R.string.sessions_title),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            busyLine(busy, exited),
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                exited -> Danger
                                busy -> Ok
                                else -> TextMid
                            }
                        )
                    }
                },
                actions = {
                    Box {
                        TextButton(onClick = { modeMenuOpen = true }) {
                            Text(
                                modeLabel(summary?.permissionMode ?: ""),
                                style = MaterialTheme.typography.labelMedium
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = stringResource(R.string.chat_mode_label))
                        }
                        DropdownMenu(expanded = modeMenuOpen, onDismissRequest = { modeMenuOpen = false }) {
                            listOf("default", "acceptEdits", "plan", "bypassPermissions").forEach { mode ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            modeLabel(mode),
                                            color = if (mode == summary?.permissionMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = {
                                        modeMenuOpen = false
                                        vm.setMode(mode)
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text(stringResource(R.string.chat_input_hint), color = TextMid) },
                    modifier = Modifier.weight(1f),
                    maxLines = 4
                )
                Spacer(Modifier.size(8.dp))
                IconButton(onClick = {
                    vm.sendPrompt(input.trim())
                    input = ""
                }, enabled = canSend) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.chat_send),
                        tint = if (canSend) MaterialTheme.colorScheme.primary else TextMid
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (exited) {
                Text(
                    stringResource(R.string.chat_exited),
                    color = Danger,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
            StatsRow(status, bg)
            HorizontalDivider(color = Border)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(message)
                }
            }
        }
    }
}

@Composable
private fun StatsRow(status: SessionStatus?, bg: Int) {
    val tokens = status?.let {
        stringResource(R.string.chat_stats_tokens, compact(it.tokensIn), compact(it.tokensOut))
    } ?: ""
    val turns = status?.turns?.takeIf { it > 0 }
        ?.let { stringResource(R.string.chat_stats_turns, it) } ?: ""
    val bgLabel = if (bg > 0) stringResource(R.string.sessions_bg, bg) else ""
    val parts = buildList {
        status?.model?.takeIf { it.isNotEmpty() }?.let { add(it) }
        if (tokens.isNotEmpty()) add(tokens)
        status?.costUsd?.takeIf { it > 0 }?.let { add("$%.2f".format(it)) }
        if (turns.isNotEmpty()) add(turns)
        if (bgLabel.isNotEmpty()) add(bgLabel)
    }
    if (parts.isEmpty()) return
    Text(
        parts.joinToString("  ·  "),
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        color = TextMid,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    )
}

@Composable
private fun MessageBubble(message: RemoteMessage) {
    if (message.role == "tool") {
        ToolRow(message)
        return
    }
    val isUser = message.role == "user"
    Box(Modifier.fillMaxWidth()) {
        Surface(
            color = if (isUser) Surface2 else MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(
                topStart = 14.dp, topEnd = 14.dp,
                bottomStart = if (isUser) 14.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 14.dp
            ),
            modifier = Modifier
                .widthIn(max = 320.dp)
                .align(if (isUser) Alignment.CenterEnd else Alignment.CenterStart)
                .then(
                    if (!isUser) Modifier.border(1.dp, Border, MaterialTheme.shapes.large) else Modifier
                )
        ) {
            Text(
                message.text.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun ToolRow(message: RemoteMessage) {
    var expanded by remember(message.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp)
        ) {
            Icon(
                if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = TextMid,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.size(6.dp))
            Text(
                message.toolName ?: stringResource(R.string.chat_tool),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = TextMid,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (expanded && !message.toolInput.isNullOrBlank()) {
            Surface(
                color = Surface2,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    message.toolInput,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = TextMid,
                    maxLines = 16,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
    }
}

@Composable
private fun modeLabel(mode: String): String = when (mode) {
    "default" -> stringResource(R.string.mode_default)
    "acceptEdits" -> stringResource(R.string.mode_acceptEdits)
    "plan" -> stringResource(R.string.mode_plan)
    "bypassPermissions" -> stringResource(R.string.mode_bypassPermissions)
    else -> mode
}

@Composable
private fun busyLine(busy: Boolean, exited: Boolean): String = when {
    exited -> stringResource(R.string.chat_exited)
    busy -> stringResource(R.string.sessions_busy)
    else -> stringResource(R.string.sessions_running)
}

private fun compact(n: Long): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1fk".format(n / 1_000.0)
    else -> n.toString()
}
