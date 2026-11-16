package io.github.itguyvv.codecockpitremote.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.itguyvv.codecockpitremote.R
import io.github.itguyvv.codecockpitremote.net.ConnState
import io.github.itguyvv.codecockpitremote.protocol.SessionSummary
import io.github.itguyvv.codecockpitremote.ui.theme.Busy
import io.github.itguyvv.codecockpitremote.ui.theme.Danger
import io.github.itguyvv.codecockpitremote.ui.theme.Ok
import io.github.itguyvv.codecockpitremote.ui.theme.Surface2
import io.github.itguyvv.codecockpitremote.ui.theme.TextMid
import io.github.itguyvv.codecockpitremote.vm.AppViewModel

/** Live session list for the connected desktop. Tap a session to open its chat. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(vm: AppViewModel) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val connState by vm.connState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sessions_title)) },
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
            if (connState != null && connState !is ConnState.Ready) {
                Text(
                    stringResource(R.string.sessions_disconnected),
                    color = Busy,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
            if (sessions.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        stringResource(R.string.sessions_empty_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.sessions_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMid
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(sessions, key = { it.id }) { session ->
                        SessionCard(session = session, onClick = { vm.enterChat(session) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionCard(session: SessionSummary, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    session.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.size(8.dp))
                EngineBadge(session.engine)
            }
            Text(
                session.cwd,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = TextMid,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(session)
                Spacer(Modifier.size(6.dp))
                Text(
                    statusLabel(session),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMid
                )
                if (session.bg > 0) {
                    Spacer(Modifier.size(8.dp))
                    Text(
                        stringResource(R.string.sessions_bg, session.bg),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMid
                    )
                }
                Spacer(Modifier.weight(1f))
                val meta = sessionMeta(session)
                if (meta.isNotEmpty()) {
                    Text(
                        meta,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = TextMid
                    )
                }
            }
        }
    }
}

@Composable
private fun EngineBadge(engine: String) {
    Surface(
        color = Surface2,
        contentColor = TextMid,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            engine,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun StatusDot(session: SessionSummary) {
    val color = when {
        session.status == "exited" -> Danger
        session.busy -> Ok
        else -> TextMid
    }
    Box(
        Modifier
            .size(8.dp)
            .background(color, CircleShape)
    )
}

@Composable
private fun statusLabel(session: SessionSummary): String = when {
    session.status == "exited" -> stringResource(R.string.sessions_exited)
    session.busy -> stringResource(R.string.sessions_busy)
    else -> stringResource(R.string.sessions_running)
}

private fun sessionMeta(session: SessionSummary): String = buildList {
    if (session.model.isNotEmpty()) add(session.model)
    if (session.costUsd > 0) add("$%.2f".format(session.costUsd))
}.joinToString(" · ")
