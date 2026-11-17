package ai.openonion.oochat.ui.navigation

import ai.openonion.oochat.R
import ai.openonion.oochat.domain.model.AgentStatus
import ai.openonion.oochat.ui.components.AgentInitialsAvatar
import ai.openonion.oochat.ui.components.AgentStatusChip
import ai.openonion.oochat.ui.components.AppIcons
import ai.openonion.oochat.ui.components.ConfirmationSheet
import ai.openonion.oochat.ui.components.DangerConfirmActions
import ai.openonion.oochat.ui.theme.ButtonToken
import ai.openonion.oochat.ui.theme.ConnectOnionTheme
import ai.openonion.oochat.ui.theme.DrawerShape
import ai.openonion.oochat.ui.theme.Elevation
import ai.openonion.oochat.ui.theme.MenuShape
import ai.openonion.oochat.ui.theme.chatBody
import ai.openonion.oochat.ui.theme.hairline
import ai.openonion.oochat.ui.theme.sectionLabel
import ai.openonion.oochat.ui.theme.spacing
import ai.openonion.oochat.util.truncateMiddle
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Width of the drawer panel. Shared with ChatScreen, which reserves the same
 * width for the placeholder it shows before the drawer's first open — the
 * open anchor must be identical either way or the first slide-in is wrong.
 */
val NavDrawerWidth = 324.dp

/** A single selectable session row nested under its agent. */
data class DrawerEntry(
    val id: String,
    val title: String
)

/**
 * One agent-grouped section of the drawer's agent list, emitted directly by
 * [ai.openonion.oochat.ui.chat.ChatViewModel.drawerAgents]. Deliberately
 * holds only what the drawer draws, so a persisted message — which rewrites a
 * session row's timestamp, count and preview — leaves this value unchanged.
 */
data class DrawerAgentSection(
    val agentId: String,
    val agentAddress: String,
    val name: String,
    val status: AgentStatus,
    val sessions: List<DrawerEntry>
)

/**
 * The navigation drawer (W16AAPPLE-114): every saved agent with its status said in words, that
 * agent's chats under it, and a labelled New chat at the end of each list.
 *
 * Self-contained and fully parameterized so it can be dropped into a
 * `ModalNavigationDrawer(drawerContent = { NavDrawer(...) })`.
 *
 * Layout (top → bottom):
 * - Brand header (logo, wordmark, Close)
 * - Scrollable "Agents" section: one row per agent, expandable to its own chats
 * - Footer rows: Add agent (→ AgentListScreen), Settings, and the connected agent's address
 *
 * A chat's rare actions (Rename, Clear chat, Delete) are behind the three dots on its row, so
 * the row itself is only its title, and Delete cannot be hit by a thumb reaching for the chat.
 * Clear chat and Delete each ask first, in a sheet owned by this composable.
 *
 * @param agentSections Every saved agent (already in the shared drag-to-reorder
 *   order — see [ai.openonion.oochat.data.repository.AgentRepository.reorderAgents])
 *   with its own live [AgentStatus] and session list
 * @param activeEntryId Currently selected session id, highlighted in primary color
 * @param connectedAgentAddress Address of the agent this session is connected to, or null if none yet
 * @param sessionTimes When each chat was last used, already in words ("4m ago"), by session id. Kept
 *   apart from [agentSections], which must not change with every message (see [DrawerAgentSection]);
 *   a chat with no entry shows its title alone
 * @param onNewChatForAgent Invoked with an agent's address when its New chat row is tapped
 * @param onSelectAgentSession Invoked with (agentAddress, sessionId) when a session row is tapped
 * @param onDeleteSession Invoked with a sessionId once the user confirms deleting that chat
 * @param onClearSession Invoked with a sessionId once the user confirms clearing that chat's messages
 * @param onRenameSession Invoked with (sessionId, newTitle) once the user confirms a rename
 * @param onOpenAgentList Invoked when the footer "Add agent" row is tapped
 * @param onOpenSettings Invoked when the footer Settings row is tapped
 * @param onClose Invoked when the header's Close button is tapped
 * @param modifier Optional modifier for the drawer container
 * @param brandTitle Wordmark shown next to the brand icon
 */
@Composable
fun NavDrawer(
    agentSections: List<DrawerAgentSection>,
    activeEntryId: String?,
    onNewChatForAgent: (agentAddress: String) -> Unit,
    onSelectAgentSession: (agentAddress: String, sessionId: String) -> Unit,
    onDeleteSession: (sessionId: String) -> Unit,
    onClearSession: (sessionId: String) -> Unit,
    onRenameSession: (sessionId: String, newTitle: String) -> Unit,
    onOpenAgentList: () -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    connectedAgentAddress: String? = null,
    sessionTimes: Map<String, String> = emptyMap(),
    brandTitle: String = "ConnectOnion"
) {
    // Tracks COLLAPSED agent ids (inverted, matching the Figma reference) so
    // any agent not yet seen here — including ones added after first
    // composition — defaults to expanded without needing to be seeded.
    var collapsedAgentIds by remember { mutableStateOf(emptySet<String>()) }

    // The chat a sheet is open for. One piece of state each (not per row), so
    // at most one sheet can ever be showing.
    var pendingDeleteEntry by remember { mutableStateOf<DrawerEntry?>(null) }
    var pendingClearEntry by remember { mutableStateOf<DrawerEntry?>(null) }
    var pendingRenameEntry by remember { mutableStateOf<DrawerEntry?>(null) }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(NavDrawerWidth)
            // Round on the side it opens towards; the blur of what is behind
            // it is applied one level up, in ChatScreen.kt.
            .clip(DrawerShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            // ModalNavigationDrawer hands its drawerContent the full window
            // height and applies no insets of its own (this is a raw Column,
            // not a ModalDrawerSheet), so the panel's fill above still spans
            // edge to edge while the rows inset themselves.
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // ── Brand ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = MaterialTheme.spacing.xl,
                    end = MaterialTheme.spacing.sm,
                    top = MaterialTheme.spacing.lg,
                    bottom = MaterialTheme.spacing.lg
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)
        ) {
            // Real brand mark — an independent multi-color logo, shown
            // as-is rather than tinted into a themed chip (the app's
            // green accent and the logo's purple are deliberately not
            // forced to match).
            Image(
                painter = painterResource(R.drawable.ic_onion_logo),
                contentDescription = null,
                modifier = Modifier.size(28.dp)
            )
            Text(
                text = brandTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close menu",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        DrawerDivider()

        // ── Scrollable agent list ──
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = MaterialTheme.spacing.sm)
        ) {
            val onlineCount = agentSections.count {
                it.status == AgentStatus.Active || it.status == AgentStatus.Connected
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = DrawerEdge,
                        end = DrawerEdge,
                        top = MaterialTheme.spacing.lg,
                        bottom = MaterialTheme.spacing.sm
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "AGENTS",
                    style = MaterialTheme.typography.sectionLabel,
                    color = MaterialTheme.colorScheme.outline
                )
                // One count, in words: "2 of 5 online", or just the total when none are.
                val total = agentSections.size
                Text(
                    text = if (onlineCount > 0) "$onlineCount of $total online" else "$total",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (onlineCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            }

            if (agentSections.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = MaterialTheme.spacing.xxl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)
                ) {
                    Text(
                        text = "No agents yet",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Add one below to start chatting",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            agentSections.forEach { section ->
                val expanded = section.agentId !in collapsedAgentIds
                AgentSectionRow(
                    section = section,
                    expanded = expanded,
                    onToggleExpanded = {
                        collapsedAgentIds = if (expanded) {
                            collapsedAgentIds + section.agentId
                        } else {
                            collapsedAgentIds - section.agentId
                        }
                    },
                    onNewChat = { onNewChatForAgent(section.agentAddress) },
                    activeEntryId = activeEntryId,
                    sessionTimes = sessionTimes,
                    onSelectSession = { sessionId -> onSelectAgentSession(section.agentAddress, sessionId) },
                    onRequestRenameSession = { entry -> pendingRenameEntry = entry },
                    onRequestClearSession = { entry -> pendingClearEntry = entry },
                    onRequestDeleteSession = { entry -> pendingDeleteEntry = entry }
                )
            }
        }

        DrawerDivider()

        // ── Footer: Add agent, Settings and the connected agent, one list of rows ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = MaterialTheme.spacing.sm, bottom = MaterialTheme.spacing.md)
        ) {
            DrawerFooterRow(icon = Icons.Outlined.AddCircleOutline, label = "Add agent", onClick = onOpenAgentList)
            DrawerFooterRow(icon = Icons.Outlined.Settings, label = "Settings", onClick = onOpenSettings)
            // The address this session is connected to, or an em dash before any
            // connection. No balance: a low one is said beside the composer (see
            // ai.openonion.oochat.ui.chat.components.LowBalanceBar).
            DrawerFooterRow(icon = Icons.Filled.Link, label = "Agent address") {
                Text(
                    text = connectedAgentAddress?.truncateMiddle(6, 4) ?: "—",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }

    pendingDeleteEntry?.let { entry ->
        ConfirmSessionSheet(
            title = "Delete this chat?",
            body = "“${entry.title}” and all its messages will be deleted. This can't be undone.",
            confirmLabel = "Delete chat",
            onConfirm = {
                onDeleteSession(entry.id)
                pendingDeleteEntry = null
            },
            onDismiss = { pendingDeleteEntry = null }
        )
    }

    pendingClearEntry?.let { entry ->
        ConfirmSessionSheet(
            title = "Clear this chat?",
            body = "All the messages in “${entry.title}” will be deleted. The chat itself is kept. This can't be undone.",
            confirmLabel = "Clear chat",
            onConfirm = {
                onClearSession(entry.id)
                pendingClearEntry = null
            },
            onDismiss = { pendingClearEntry = null }
        )
    }

    pendingRenameEntry?.let { entry ->
        RenameSessionSheet(
            currentTitle = entry.title,
            onConfirm = { newTitle ->
                onRenameSession(entry.id, newTitle)
                pendingRenameEntry = null
            },
            onDismiss = { pendingRenameEntry = null }
        )
    }
}

/** How far the drawer's own content sits in from its leading edge. */
private val DrawerEdge = 20.dp

/** The sheet that asks before a chat is deleted or cleared, naming the chat. */
@Composable
private fun ConfirmSessionSheet(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ConfirmationSheet(
        title = title,
        body = body,
        onDismiss = onDismiss,
        actions = {
            DangerConfirmActions(confirmLabel = confirmLabel, onConfirm = onConfirm, onDismiss = onDismiss)
        }
    )
}

/** Rename sheet for a chat's Rename action: pre-filled, Save disabled until the title actually changes. */
@Composable
private fun RenameSessionSheet(currentTitle: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var input by remember { mutableStateOf(currentTitle) }
    val trimmed = input.trim()
    val canSave = trimmed.isNotEmpty() && trimmed != currentTitle

    ConfirmationSheet(
        // A statement, not a question: the sheet asks for a name rather than
        // for a yes, so it follows ImportKeySheet rather than its own
        // delete-sibling two functions up.
        title = "Rename chat",
        onDismiss = onDismiss,
        content = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        actions = {
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(ButtonToken.FullWidth),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = { onConfirm(trimmed) },
                    enabled = canSave,
                    modifier = Modifier.weight(1f).height(ButtonToken.FullWidth),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            }
        }
    )
}

/** A quiet rule between the drawer's three parts, inset from both edges. */
@Composable
private fun DrawerDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = MaterialTheme.spacing.lg),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    )
}

/**
 * One agent: its avatar, name and address, its status as a chip, and a chevron. The whole row
 * opens and closes that agent's chats, and is read as "name, status".
 */
@Composable
private fun AgentSectionRow(
    section: DrawerAgentSection,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onNewChat: () -> Unit,
    activeEntryId: String?,
    sessionTimes: Map<String, String>,
    onSelectSession: (String) -> Unit,
    onRequestRenameSession: (DrawerEntry) -> Unit,
    onRequestClearSession: (DrawerEntry) -> Unit,
    onRequestDeleteSession: (DrawerEntry) -> Unit
) {
    val disabled = section.status == AgentStatus.Disabled
    Column(modifier = Modifier.animateContentSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clickable(
                    onClickLabel = if (expanded) "Hide chats" else "Show chats",
                    onClick = onToggleExpanded
                )
                .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                .padding(start = DrawerEdge, end = MaterialTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AgentInitialsAvatar(
                name = section.name,
                // The name is said beside it, so the initial is only decoration.
                modifier = Modifier.clearAndSetSemantics {},
                size = 40.dp,
                shape = MaterialTheme.shapes.extraLarge,
                maxInitials = 1,
                textStyle = MaterialTheme.typography.titleMedium,
                backgroundColor = if (disabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                contentColor = MaterialTheme.colorScheme.surface
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = MaterialTheme.spacing.md)
            ) {
                Text(
                    text = section.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (disabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // Which agent this is when two share a name. Not read out: a
                // string of hex is noise, and the footer has the open agent's.
                Text(
                    text = section.agentAddress.truncateMiddle(6, 4),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    modifier = Modifier.clearAndSetSemantics {}
                )
            }
            AgentStatusChip(status = section.status)
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (expanded) {
            Column(
                modifier = Modifier.padding(start = DrawerEdge, end = MaterialTheme.spacing.sm),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)
            ) {
                if (section.sessions.isEmpty()) {
                    Text(
                        text = "No chats yet",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(start = SessionTextInset, top = MaterialTheme.spacing.xs)
                    )
                }
                section.sessions.forEach { entry ->
                    DrawerEntryRow(
                        entry = entry,
                        time = sessionTimes[entry.id],
                        active = entry.id == activeEntryId,
                        onClick = { onSelectSession(entry.id) },
                        onRenameClick = { onRequestRenameSession(entry) },
                        onClearClick = { onRequestClearSession(entry) },
                        onDeleteClick = { onRequestDeleteSession(entry) }
                    )
                }
                NewChatRow(agentName = section.name, onClick = onNewChat)
                Spacer(modifier = Modifier.height(MaterialTheme.spacing.sm))
            }
        }
    }
}

/** Where a chat's title starts inside its row: past the row's padding, its icon and the gap. */
private val SessionTextInset = 46.dp // 16 + 20 + 10

/**
 * A chat: its title and when it was last used, with the open one tinted. Tapping it opens the
 * chat; everything else that can be done to it is behind the three dots at its end.
 */
@Composable
private fun DrawerEntryRow(
    entry: DrawerEntry,
    time: String?,
    active: Boolean,
    onClick: () -> Unit,
    onRenameClick: () -> Unit,
    onClearClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val accent = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .semantics { selected = active }
            .padding(start = MaterialTheme.spacing.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.ChatBubbleOutline,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(20.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = MaterialTheme.spacing.sm2, top = MaterialTheme.spacing.xs2, bottom = MaterialTheme.spacing.xs2)
        ) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.chatBody,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (time != null) {
                Text(
                    text = time,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "More options for \"${entry.title}\"",
                    tint = accent
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                shape = MenuShape,
                // Flat, set apart by a hairline, as every menu is.
                containerColor = MaterialTheme.colorScheme.surface,
                shadowElevation = Elevation.hairline,
                border = MaterialTheme.hairline
            ) {
                SessionMenuItem(Icons.Outlined.DriveFileRenameOutline, "Rename") {
                    menuOpen = false
                    onRenameClick()
                }
                SessionMenuItem(AppIcons.ClearChat, "Clear chat") {
                    menuOpen = false
                    onClearClick()
                }
                // The rule sets the one that removes the chat apart from the two that keep it.
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = MaterialTheme.spacing.xs),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )
                // The dots say there is a question to come before anything is deleted.
                SessionMenuItem(Icons.Outlined.Delete, "Delete…", destructive = true) {
                    menuOpen = false
                    onDeleteClick()
                }
            }
        }
    }
}

@Composable
private fun SessionMenuItem(icon: ImageVector, label: String, destructive: Boolean = false, onClick: () -> Unit) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.bodyLarge, color = color) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (destructive) color else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        onClick = onClick,
        modifier = Modifier.width(196.dp)
    )
}

/** New chat with this agent: a labelled row at the end of its chats, lined up with their titles. */
@Composable
private fun NewChatRow(agentName: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .clickable(role = Role.Button, onClick = onClick)
            // Which agent, for TalkBack: every agent in the list has a row that says New chat.
            .semantics { contentDescription = "New chat with $agentName" }
            .padding(horizontal = MaterialTheme.spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm2)
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = "New chat",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * One drawer footer row: an icon, a label, and an optional value at the end. Add agent, Settings
 * and the connected agent's address all use it, so the footer reads as one list.
 */
@Composable
private fun DrawerFooterRow(
    icon: ImageVector,
    label: String,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = DrawerEdge),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md2)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}

@Preview(showBackground = true, widthDp = 324, heightDp = 720)
@Composable
private fun NavDrawerPreview() {
    ConnectOnionTheme {
        NavDrawer(
            agentSections = listOf(
                DrawerAgentSection(
                    agentId = "1",
                    agentAddress = "0x1a2b3c4d5e6f7a8b",
                    name = "Research Assistant",
                    status = AgentStatus.Connected,
                    sessions = listOf(
                        DrawerEntry("1", "Quantum entanglement basics"),
                        DrawerEntry("2", "React Server Components")
                    )
                ),
                DrawerAgentSection(
                    agentId = "2",
                    agentAddress = "0x9a2b3c4d5e6f7a8b",
                    name = "Code Copilot",
                    status = AgentStatus.Connecting,
                    sessions = listOf(
                        DrawerEntry("3", "Refactor auth module")
                    )
                ),
                DrawerAgentSection(
                    agentId = "3",
                    agentAddress = "0x55aa66bb77cc88dd",
                    name = "Data Analyst",
                    status = AgentStatus.Disabled,
                    sessions = emptyList()
                )
            ),
            activeEntryId = "1",
            onNewChatForAgent = {},
            onSelectAgentSession = { _, _ -> },
            onDeleteSession = {},
            onClearSession = {},
            onRenameSession = { _, _ -> },
            onOpenAgentList = {},
            onOpenSettings = {},
            onClose = {},
            connectedAgentAddress = "0x1a2b3c4d5e6f7a8b",
            sessionTimes = mapOf("1" to "4m ago", "2" to "2h ago", "3" to "3d ago")
        )
    }
}
