package ai.openonion.oochat.ui.chat.components

import ai.openonion.oochat.domain.model.ConnectionState
import ai.openonion.oochat.domain.model.SessionActivity
import ai.openonion.oochat.ui.components.AppIcons
import ai.openonion.oochat.ui.components.ConfirmationSheet
import ai.openonion.oochat.ui.components.ConnectionStatusDot
import ai.openonion.oochat.ui.components.DangerConfirmActions
import ai.openonion.oochat.ui.theme.Elevation
import ai.openonion.oochat.ui.theme.MenuShape
import ai.openonion.oochat.ui.theme.PillShape
import ai.openonion.oochat.ui.theme.hairline
import ai.openonion.oochat.ui.theme.spacing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Header mirrors the design: hamburger opens the drawer, the title with the agent's name, the
 * session status and the model quietly underneath, New chat, agent-home button, overflow menu
 * for logs/clear. Settings only lives in the drawer footer — don't duplicate it here.
 *
 * New chat is on the bar itself, labelled, so the thing a user most often does next is one tap
 * from the conversation rather than inside the drawer (W16AAPPLE-114).
 *
 * Fixed 64dp and never wraps: title and model name are each one line with an
 * ellipsis, and there is nothing else left in the row that can grow it — the
 * old three-line "Connected · model · $balance" subtitle is why this used to
 * grow past 200dp on a real title. The balance moved to the drawer's account
 * area; see [ai.openonion.oochat.ui.navigation.NavDrawer].
 */
@Composable
fun ChatTopBar(
    title: String,
    connectionState: ConnectionState,
    isConnected: Boolean,
    onClearChat: () -> Unit,
    onNavigateToLogs: () -> Unit,
    // Null when the drawer is always on screen beside the chat (W16AAPPLE-127): there is
    // nothing to open, so the button is left out rather than shown doing nothing.
    onMenuClick: (() -> Unit)?,
    awaitingOnboardCode: Boolean = false,
    // See ConnectionStatusDot's own doc — surfaces the connected
    // agent's model once its AGENT_PROFILE frame lands.
    modelName: String? = null,
    // What the agent is doing on this session — see ChatViewModel.sessionActivity.
    // Null while not connected, where the status dot and banner speak instead.
    sessionActivity: SessionActivity? = null,
    // True once a DASHBOARD_SNAPSHOT has arrived. The button is absent, not
    // disabled, when it hasn't: most agents have no Home page, and a dead
    // control on every one of them is worse than no control.
    hasDashboard: Boolean = false,
    onOpenDashboard: () -> Unit = {},
    // The owner's Rem notebook (W16AAPPLE-69). Offered on any live connection:
    // only the Host knows who its owner is, and a non-owner is told so there.
    onOpenRem: () -> Unit = {},
    // False on the agent's Dashboard, which has this bar but no conversation to clear.
    showClearChat: Boolean = true,
    // The agent this conversation is with, leading the line under the title.
    agentName: String? = null,
    // Starts a new conversation with this agent. Null leaves the button out: the
    // Dashboard has none, and with no agent there is nothing to start a chat with.
    onNewChat: (() -> Unit)? = null,
    // False while this conversation is still empty: it already is a new chat,
    // and starting another would only mint a session nobody used.
    newChatEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showClearChatSheet by remember { mutableStateOf(false) }

    if (showClearChatSheet) {
        ClearChatSheet(
            onConfirm = {
                showClearChatSheet = false
                onClearChat()
            },
            onDismiss = { showClearChatSheet = false }
        )
    }

    val running = sessionActivity == SessionActivity.RUNNING
    // Green while the agent works, as the design draws it; otherwise quiet.
    val statusColor = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Column(
        modifier = modifier
            .testTag("chat_top_bar")
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            // TopAppBar applied this for us; a plain Row does not, and without
            // it the bar sits under the clock and the battery icon.
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        val hairline = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                // A hairline under the header, setting it off from the
                // transcript — drawn inside the bar, so it stays 64dp.
                .drawBehind {
                    val y = size.height - 0.5.dp.toPx()
                    drawLine(hairline, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                }
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onMenuClick != null) {
                IconButton(onClick = onMenuClick, modifier = Modifier.size(48.dp)) {
                    Icon(imageVector = Icons.Default.Menu, contentDescription = "Open menu")
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    // Without the menu button the title takes the screen's own gutter: this and the
                    // row's 4dp make the 16dp every screen's content starts at.
                    .padding(start = if (onMenuClick != null) 2.dp else MaterialTheme.spacing.md)
            ) {
                // Row 1: the title alone.
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // Row 2: dot, then session status and model — the dot carries the
                // connection, and once connected, whether the agent is working.
                // One line: the status leads, so a long model name is what the
                // ellipsis eats.
                val subtitle = listOfNotNull(agentName, sessionActivity?.label, modelName).joinToString(" · ")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Once the line says what the session is doing the dot has nothing to add.
                    // Until then it is the only word on the connection up here.
                    if (sessionActivity == null) {
                        ConnectionStatusDot(
                            connectionState = connectionState,
                            awaitingOnboardCode = awaitingOnboardCode
                        )
                    }
                    if (subtitle.isNotEmpty()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (running) FontWeight.Medium else null,
                            color = statusColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("chat_top_bar_subtitle")
                        )
                    }
                }
            }

            if (onNewChat != null) {
                NewChatButton(onClick = onNewChat, enabled = newChatEnabled)
            }

            if (hasDashboard) {
                IconButton(onClick = onOpenDashboard, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Dashboard,
                        contentDescription = "Open agent home"
                    )
                }
            }

            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options"
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    shape = MenuShape,
                    // A flat surface set apart by a hairline, not a shadow or a tint.
                    containerColor = MaterialTheme.colorScheme.surface,
                    shadowElevation = Elevation.hairline,
                    border = MaterialTheme.hairline
                ) {
                    if (isConnected) {
                        DropdownMenuItem(
                            text = { Text("Rem") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onOpenRem()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("View logs") },
                        leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onNavigateToLogs()
                        }
                    )
                    if (showClearChat) {
                        // Gap, then the rule. A hairline alone still leaves the
                        // destructive item one thumb-width from the safe one; the
                        // space is what stops a reach-through.
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider()
                        // Not red: it only opens the confirmation, whose Clear is the red one
                        // (W16AAPPLE-128). The gap and rule above still set it apart.
                        DropdownMenuItem(
                            text = { Text("Clear chat") },
                            leadingIcon = {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null)
                            },
                            onClick = {
                                menuExpanded = false
                                showClearChatSheet = true
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * New chat, as a tinted pill with its name on it. It is drawn 40dp tall and takes 48dp to touch.
 */
@Composable
private fun NewChatButton(onClick: () -> Unit, enabled: Boolean) {
    val content = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.38f)
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = PillShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 0.12f else 0.06f),
        contentColor = content,
        modifier = Modifier.padding(horizontal = 4.dp).semantics { role = Role.Button }
    ) {
        Row(
            modifier = Modifier.height(40.dp).padding(start = 12.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(imageVector = AppIcons.NewChat, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                text = "New chat",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

/** The top bar's words for a [SessionActivity]. */
internal val SessionActivity.label: String
    get() = when (this) {
        SessionActivity.RUNNING -> "Running"
        SessionActivity.AWAITING_USER -> "Waiting for you"
        SessionActivity.IDLE -> "Idle"
        SessionActivity.NOT_FOUND -> "Session not found"
    }

@Composable
private fun ClearChatSheet(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmationSheet(
        title = "Clear this chat?",
        body = "All the messages in this chat will be deleted. This can't be undone.",
        onDismiss = onDismiss,
        actions = {
            DangerConfirmActions(
                confirmLabel = "Clear chat",
                onConfirm = onConfirm,
                onDismiss = onDismiss
            )
        }
    )
}
