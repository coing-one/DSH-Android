package io.github.jackwener.dsh.feature.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.jackwener.dsh.R
import io.github.jackwener.dsh.core.designsystem.DIcon
import io.github.jackwener.dsh.core.designsystem.DMenu
import io.github.jackwener.dsh.core.designsystem.Dsh
import io.github.jackwener.dsh.core.designsystem.DshType
import io.github.jackwener.dsh.core.designsystem.MenuDivider
import io.github.jackwener.dsh.core.designsystem.MenuItem
import io.github.jackwener.dsh.core.designsystem.MenuSectionTitle
import io.github.jackwener.dsh.core.designsystem.Radius
import io.github.jackwener.dsh.core.designsystem.pressable
import io.github.jackwener.dsh.core.remote.RemoteHost
import io.github.jackwener.dsh.core.remote.RemoteState
import io.github.jackwener.dsh.feature.settings.RemoteStateDot
import io.github.jackwener.dsh.feature.settings.remoteStateText

/**
 * The drawer's workspace switcher (#51): 本机 or one of the remote hosts. The session list below shows that
 * workspace's conversations, and a new conversation runs there.
 */
@Composable
fun WorkspaceSwitcher(current: String?, hosts: List<RemoteHost>, states: Map<String, RemoteState>, onSelect: (String?) -> Unit, onManage: () -> Unit) {
    val c = Dsh.colors
    var open by remember { mutableStateOf(false) }
    val host = hosts.firstOrNull { it.id == current }
    Box {
        // Miuix preference row (BasicComponent spec), so the drawer card's first row matches
        // the ArrowPreference rows below it; the switcher keeps its up/down chevron instead
        // of an arrow, and the menu anchor / behavior are unchanged.
        top.yukonga.miuix.kmp.basic.BasicComponent(
            title = host?.name ?: stringResource(R.string.remote_this_phone),
            startAction = { DIcon(R.drawable.dsw_workspace_tree_outline, size = 20.dp, tint = c.labelSecondary) },
            endActions = {
                if (host != null) RemoteStateDot(states[host.id] ?: RemoteState.Idle)
                DIcon(R.drawable.dsw_chevrons_up_down_outline, size = 12.dp, tint = c.labelTertiary, contentDescription = stringResource(R.string.remote_workspace))
            },
            onClick = { open = true },
        )
        DMenu(open, { open = false }) {
            MenuSectionTitle(stringResource(R.string.remote_workspace))
            MenuItem(stringResource(R.string.remote_this_phone), { open = false; onSelect(null) }, R.drawable.dsw_whale,
                stringResource(R.string.remote_this_phone_desc), selected = current == null)
            hosts.forEach { h ->
                val st = remoteStateText(states[h.id] ?: RemoteState.Idle)
                MenuItem(h.name, { open = false; onSelect(h.id) }, R.drawable.dsw_workspace_tree_outline,
                    listOf(h.address, st).filter { it.isNotEmpty() }.joinToString(" · "), selected = current == h.id)
            }
            MenuDivider()
            MenuItem(stringResource(if (hosts.isEmpty()) R.string.remote_connect_computer else R.string.remote_manage), { open = false; onManage() }, R.drawable.dsw_settings_outline)
        }
    }
}

/** Top bar of a remote conversation (#51): 远程 · host and its link; tap for disconnect / host settings. */
@Composable
fun RemoteBadge(remote: RemoteView, onDisconnect: () -> Unit, onHostSettings: () -> Unit) {
    val c = Dsh.colors
    var open by remember { mutableStateOf(false) }
    Box {
        Row(Modifier.widthIn(max = 150.dp).heightIn(min = 32.dp).pressable({ open = true }, Radius.sm).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            RemoteStateDot(remote.state)
            Text(stringResource(R.string.remote_badge, remote.host?.name ?: "?"), style = DshType.xxs12m, color = c.labelSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DMenu(open, { open = false }) {
            MenuSectionTitle(listOfNotNull(remote.host?.address, remoteStateText(remote.state).ifEmpty { null }).joinToString(" · "))
            if (remote.state is RemoteState.Connected) MenuItem(stringResource(R.string.remote_disconnect), { open = false; onDisconnect() }, R.drawable.dsw_logout_outline,
                stringResource(R.string.remote_disconnect_desc))
            MenuItem(stringResource(R.string.remote_host_settings), { open = false; onHostSettings() }, R.drawable.dsw_settings_outline)
        }
    }
}
