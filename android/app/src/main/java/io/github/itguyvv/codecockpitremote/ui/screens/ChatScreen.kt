package io.github.itguyvv.codecockpitremote.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.itguyvv.codecockpitremote.R
import io.github.itguyvv.codecockpitremote.net.ConnState
import io.github.itguyvv.codecockpitremote.protocol.RemoteMessage
import io.github.itguyvv.codecockpitremote.protocol.SessionStatus
import io.github.itguyvv.codecockpitremote.ui.theme.Border
import io.github.itguyvv.codecockpitremote.ui.theme.Busy
import io.github.itguyvv.codecockpitremote.ui.theme.Danger
import io.github.itguyvv.codecockpitremote.ui.theme.Ok
import io.github.itguyvv.codecockpitremote.ui.theme.Surface2
import io.github.itguyvv.codecockpitremote.ui.theme.TextMid
import io.github.itguyvv.codecockpitremote.vm.AppViewModel

/**
 * Conversation pane for one attached session: streamed messages, tool cards with
 * expandable input + output, live stats and the prompt input. The workbench owns
 * the top bar and back handling; this pane fills the rest.
 */
@Composable
fun ChatScreen(vm: AppViewModel, sessionId: String) {
    val messages by vm.chatMessages.collectAsStateWithLifecycle()
    val status by vm.chatStatus.collectAsStateWithLifecycle()
    val busy by vm.chatBusy.collectAsStateWithLifecycle()
    val bg by vm.chatBg.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val connState by vm.connState.collectAsStateWithLifecycle()
    val summary = sessions.find { it.id == sessionId }

    // Usage-only assistant rows carry no text and would render as empty bubbles
    val visible = messages.filter { !(it.role == "assistant" && it.text.isNullOrBlank()) }

    val listState = rememberLazyListState()
    LaunchedEffect(visible.size) {
        if (visible.isNotEmpty()) listState.animateScrollToItem(visible.size - 1)
    }

    var input by remember { mutableStateOf("") }
    val exited = summary?.status == "exited"
    val canSend = input.isNotBlank() && !exited && connState is ConnState.Ready

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        if (exited) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
            ) {
                Text(
                    stringResource(R.string.chat_exited),
                    color = Danger,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f)
                )
                summary?.claudeSessionId?.let { nativeId ->
                    TextButton(onClick = {
                        vm.createSession(
                            cwd = summary.cwd,
                            mode = summary.permissionMode.ifBlank { "default" },
                            engine = summary.engine.ifBlank { "claude" },
                            title = "",
                            resumeId = nativeId
                        )
                    }) { Text(stringResource(R.string.wb_history_resume)) }
                }
            }
        }
        StatsRow(status, busy, bg)
        HorizontalDivider(color = Border)
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(visible, key = { it.id }) { message ->
                MessageBubble(message)
            }
        }
        HorizontalDivider(color = Border)
        Row(
            modifier = Modifier
                .fillMaxWidth()
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
}

@Composable
private fun StatsRow(status: SessionStatus?, busy: Boolean, bg: Int) {
    val parts = buildList {
        status?.model?.takeIf { it.isNotEmpty() }?.let { add(it) }
        status?.let {
            add(stringResource(R.string.chat_stats_tokens, compact(it.tokensIn), compact(it.tokensOut)))
        }
        status?.costUsd?.takeIf { it > 0 }?.let { add("$%.2f".format(it)) }
        status?.toolCalls?.takeIf { it > 0 }?.let { add(stringResource(R.string.chat_stats_tools, it)) }
        status?.durationMs?.takeIf { it > 0 }
            ?.let { add(stringResource(R.string.chat_stats_time, compactDuration(it))) }
        status?.turns?.takeIf { it > 0 }?.let { add(stringResource(R.string.chat_stats_turns, it)) }
        if (bg > 0) add(stringResource(R.string.sessions_bg, bg))
    }
    if (parts.isEmpty() && !busy) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        if (busy) PulseDot()
        Text(
            parts.joinToString("  ·  "),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = TextMid,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PulseDot() {
    val alpha = rememberInfiniteTransition(label = "busy").animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "busy-alpha"
    )
    Box(
        Modifier
            .padding(end = 6.dp)
            .size(8.dp)
            .alpha(alpha.value)
            .background(Busy, CircleShape)
    )
}

@Composable
private fun MessageBubble(message: RemoteMessage) {
    if (message.role == "tool") {
        ToolCard(message)
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

/** Expandable card: summary line of the command, full input, and the tool result when captured */
@Composable
private fun ToolCard(message: RemoteMessage) {
    var expanded by remember(message.id) { mutableStateOf(false) }
    val hasDetails = !message.toolInput.isNullOrBlank() || !message.toolOutput.isNullOrBlank()
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Border, RoundedCornerShape(10.dp))
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (hasDetails) Modifier.clickable { expanded = !expanded } else Modifier)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                if (hasDetails) {
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = TextMid,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.size(6.dp))
                }
                Text(
                    message.toolName ?: stringResource(R.string.chat_tool),
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    toolSummary(message.toolInput),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = TextMid,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            if (expanded) {
                HorizontalDivider(color = Border)
                SelectionContainer(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp)) {
                        if (!message.toolInput.isNullOrBlank()) {
                            Text(
                                message.toolInput,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = TextMid
                            )
                        }
                        if (!message.toolOutput.isNullOrBlank()) {
                            if (!message.toolInput.isNullOrBlank()) Spacer(Modifier.size(8.dp))
                            Surface(
                                color = Surface2,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(8.dp)) {
                                    Text(
                                        stringResource(R.string.chat_tool_output),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextMid
                                    )
                                    Spacer(Modifier.size(4.dp))
                                    Text(
                                        message.toolOutput,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** First line of the tool arguments, clamped for the collapsed header */
private fun toolSummary(input: String?): String {
    if (input.isNullOrBlank()) return ""
    val line = input.lineSequence().firstOrNull { it.isNotBlank() } ?: ""
    val clean = line.replace(Regex("\\s+"), " ").trim()
    return if (clean.length > 120) clean.take(120) + "…" else clean
}

private fun compact(n: Long): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1fk".format(n / 1_000.0)
    else -> n.toString()
}

private fun compactDuration(ms: Long): String = when {
    ms >= 3_600_000 -> "%.1fh".format(ms / 3_600_000.0)
    ms >= 60_000 -> "%.0fm".format(ms / 60_000.0)
    else -> "%.0fs".format(ms / 1_000.0)
}
