package ai.openonion.oochat.ui.home

import ai.openonion.oochat.domain.model.AgentSkill
import ai.openonion.oochat.domain.model.ApprovalMode
import ai.openonion.oochat.domain.model.ConnectionState
import ai.openonion.oochat.domain.model.SessionActivity
import ai.openonion.oochat.ui.chat.components.ChatTopBar
import ai.openonion.oochat.ui.components.AgentInitialsAvatar
import ai.openonion.oochat.ui.theme.Radii
import ai.openonion.oochat.ui.theme.spacing
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** A conversation as the dashboard's Latest list draws it. */
@Immutable
internal data class DashboardChat(val id: String, val title: String, val updatedAt: Long, val active: Boolean = false)

/**
 * Everything [AgentDashboardScreen] draws, read off the chat it belongs to.
 *
 * @param activity What the agent is doing on the open conversation; null while not connected.
 * @param chats The agent's conversations, newest first.
 * @param agentPageLabel What the agent's own published page is called, or null when it sent none.
 * @param now The time [chats] are aged against.
 */
@Immutable
internal data class AgentDashboardState(
    val agentName: String,
    val connectionState: ConnectionState,
    val activity: SessionActivity? = null,
    val awaitingOnboardCode: Boolean = false,
    val model: String? = null,
    val toolCount: Int = 0,
    val approvalMode: ApprovalMode = ApprovalMode.DEFAULT,
    val skills: List<AgentSkill> = emptyList(),
    val chats: List<DashboardChat> = emptyList(),
    val agentAddress: String? = null,
    val sessionId: String? = null,
    val agentPageLabel: String? = null,
    val now: Long = 0L
) {
    val isConnected: Boolean get() = connectionState is ConnectionState.Connected
}

/** What the dashboard can ask for. Each is something another part of the app already does. */
@Immutable
internal data class AgentDashboardActions(
    val onOpenMenu: () -> Unit = {},
    val onNewChat: () -> Unit = {},
    val onOpenChat: (sessionId: String) -> Unit = {},
    val onSeeAllChats: () -> Unit = {},
    val onChangeTrust: () -> Unit = {},
    val onOpenAgentPage: () -> Unit = {},
    val onNavigateToLogs: () -> Unit = {},
    val onOpenRem: () -> Unit = {}
)

/**
 * The agent's Dashboard: the page the app opens on (W16AAPPLE-115). It says who the agent is and
 * what it is doing, and is the way into a new chat, the recent ones, what the agent can do, how
 * much it may do unasked, and the connection's details.
 *
 * Drawn by the app from what the chat already knows, so every agent has one. A page the agent
 * published itself (its `dashboard.html`, or a Control Center app) is opened from a row here.
 *
 * The card shapes are rounder than the rest of the app's (see [Radii]): they are the design's,
 * which sets this page apart as the place the app starts. The chips keep the scale's shape, as
 * every chip with a label does.
 */
@Composable
internal fun AgentDashboardScreen(
    state: AgentDashboardState,
    actions: AgentDashboardActions,
    modifier: Modifier = Modifier
) {
    var showCapabilities by rememberSaveable { mutableStateOf(false) }
    var showHostDetails by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)) {
        // The chat's own bar, so the menu, the name and the status are where they are on the Chat tab.
        ChatTopBar(
            title = state.agentName,
            connectionState = state.connectionState,
            isConnected = state.isConnected,
            onClearChat = {},
            showClearChat = false,
            onNavigateToLogs = actions.onNavigateToLogs,
            onMenuClick = actions.onOpenMenu,
            awaitingOnboardCode = state.awaitingOnboardCode,
            modelName = state.model,
            sessionActivity = state.activity,
            onOpenRem = actions.onOpenRem
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(MaterialTheme.spacing.lg)
                .testTag("agent_dashboard"),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)
        ) {
            AgentHero(state, onNewChat = actions.onNewChat)

            // As tall as the taller of the pair, so a row of tiles has one bottom edge.
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)
            ) {
                DashboardTile(
                    icon = Icons.AutoMirrored.Outlined.Chat,
                    iconBackground = MaterialTheme.colorScheme.primaryContainer,
                    iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                    value = state.chats.size.toString(),
                    label = if (state.chats.size == 1) "Recent chat" else "Recent chats",
                    bigValue = true,
                    onClick = actions.onSeeAllChats,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                DashboardTile(
                    icon = Icons.Outlined.GridView,
                    iconBackground = MaterialTheme.colorScheme.secondaryContainer,
                    iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                    value = capabilitiesSummary(state.skills.size),
                    label = "Capabilities",
                    onClick = { showCapabilities = true },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)
            ) {
                DashboardTile(
                    icon = Icons.Outlined.Shield,
                    iconBackground = MaterialTheme.colorScheme.tertiaryContainer,
                    iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                    value = state.approvalMode.trustLabel,
                    label = "Trust level",
                    // The mode is the live session's, so there is none to change until connected.
                    onClick = actions.onChangeTrust.takeIf { state.isConnected },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                DashboardTile(
                    icon = Icons.Outlined.MonitorHeart,
                    iconBackground = MaterialTheme.colorScheme.surfaceContainerHigh,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    value = "Host details",
                    label = "Diagnostics",
                    onClick = { showHostDetails = true },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }

            state.agentPageLabel?.let { label ->
                DashboardRow(
                    icon = Icons.Outlined.Dashboard,
                    title = label,
                    subtitle = "A page this agent published",
                    onClick = actions.onOpenAgentPage
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = MaterialTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Latest",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).padding(start = MaterialTheme.spacing.xs).semantics { heading() }
                )
                if (state.chats.isNotEmpty()) {
                    TextButton(onClick = actions.onSeeAllChats) { Text("See all") }
                }
            }
            if (state.chats.isEmpty()) {
                Text(
                    text = "No chats yet. Start one with New chat.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = MaterialTheme.spacing.xs)
                )
            }
            state.chats.take(LATEST_SHOWN).forEach { chat ->
                DashboardRow(
                    icon = Icons.Outlined.CheckCircle,
                    title = chat.title,
                    subtitle = chatSubtitle(chat, state.activity, state.now),
                    onClick = { actions.onOpenChat(chat.id) }
                )
            }
        }
    }

    if (showCapabilities) CapabilitiesSheet(state.skills, state.toolCount, onDismiss = { showCapabilities = false })
    if (showHostDetails) {
        HostDetailsSheet(
            state = state,
            onViewLogs = {
                showHostDetails = false
                actions.onNavigateToLogs()
            },
            onDismiss = { showHostDetails = false }
        )
    }
}

/** How many conversations the Latest list shows before "See all". */
private const val LATEST_SHOWN = 3

private val HeroShape = RoundedCornerShape(Radii.dashboardHero)
private val TileShape = RoundedCornerShape(Radii.dashboardTile)

/** The green card at the top: the agent, what it is doing, and the way into a new chat. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AgentHero(state: AgentDashboardState, onNewChat: () -> Unit) {
    val container = MaterialTheme.colorScheme.primary
    val content = MaterialTheme.colorScheme.onPrimary
    val ring = content.copy(alpha = 0.07f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(HeroShape)
            .background(container)
            // The design's two faint rings, off the card's top right corner.
            .drawBehind {
                val centre = Offset(size.width * 0.92f, size.height * 0.28f)
                drawCircle(ring, radius = size.height * 0.55f, center = centre)
                drawCircle(ring, radius = size.height * 0.82f, center = centre, style = Stroke(width = 14.dp.toPx()))
            }
            .padding(MaterialTheme.spacing.lg + MaterialTheme.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.lg)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)) {
            AgentInitialsAvatar(
                name = state.agentName,
                size = 48.dp,
                textStyle = MaterialTheme.typography.titleLarge,
                maxInitials = 1,
                backgroundColor = content,
                contentColor = container
            )
            Column {
                Text(
                    text = state.agentName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = heroStatus(state.connectionState, state.activity, state.awaitingOnboardCode),
                    style = MaterialTheme.typography.bodyMedium,
                    color = content.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("agent_dashboard_status")
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
        ) {
            HeroChip(state.approvalMode.trustLabel, content, icon = Icons.Outlined.Shield)
            if (state.toolCount > 0) HeroChip(toolsSummary(state.toolCount), content)
            state.model?.takeIf { it.isNotBlank() }?.let { HeroChip(it, content) }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(HeroShape)
                .background(content)
                .clickable(role = Role.Button, onClick = onNewChat),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = container, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(MaterialTheme.spacing.sm))
            Text("New chat", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = container)
        }
    }
}

@Composable
private fun HeroChip(text: String, content: Color, icon: ImageVector? = null) {
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .background(content.copy(alpha = 0.16f))
            .padding(horizontal = MaterialTheme.spacing.sm2, vertical = MaterialTheme.spacing.xs2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** One of the four squares. A null [onClick] is a tile that only says something. */
@Composable
private fun DashboardTile(
    icon: ImageVector,
    iconBackground: Color,
    iconTint: Color,
    value: String,
    label: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    bigValue: Boolean = false
) {
    Column(
        modifier = modifier
            .heightIn(min = 124.dp)
            .clip(TileShape)
            .background(MaterialTheme.colorScheme.surface)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(MaterialTheme.spacing.lg)
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(MaterialTheme.shapes.extraLarge).background(iconBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(MaterialTheme.spacing.md))
        Text(
            text = value,
            style = if (bigValue) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** A full-width row that opens something: a recent chat, or the agent's own page. */
@Composable
private fun DashboardRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TileShape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = MaterialTheme.spacing.lg, vertical = MaterialTheme.spacing.md2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardSheet(title: String, body: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MaterialTheme.spacing.lg)
                .padding(bottom = MaterialTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)
        ) {
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}

@Composable
private fun CapabilitiesSheet(skills: List<AgentSkill>, toolCount: Int, onDismiss: () -> Unit) {
    DashboardSheet(
        title = "Capabilities",
        body = "Skills this agent has published. Run one from the chat by typing / and its name.",
        onDismiss = onDismiss
    ) {
        if (skills.isEmpty()) {
            Text(
                text = "This agent hasn't published any skills yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        skills.forEach { skill ->
            Column {
                Text("/${skill.name}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                skill.description?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (toolCount > 0) {
            Text(
                text = "It can also use ${toolsSummary(toolCount)} while it works.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HostDetailsSheet(state: AgentDashboardState, onViewLogs: () -> Unit, onDismiss: () -> Unit) {
    DashboardSheet(
        title = "Host details",
        body = "What this app knows about the agent it is talking to.",
        onDismiss = onDismiss
    ) {
        DetailLine("Agent", state.agentName)
        DetailLine("Connection", connectionWords(state.connectionState, state.awaitingOnboardCode))
        state.model?.takeIf { it.isNotBlank() }?.let { DetailLine("Model", it) }
        if (state.toolCount > 0) DetailLine("Tools", state.toolCount.toString())
        state.agentAddress?.let { DetailLine("Address", it) }
        state.sessionId?.let { DetailLine("Conversation", it) }
        OutlinedButton(onClick = onViewLogs, shape = MaterialTheme.shapes.medium) { Text("View logs") }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // An address is something to copy into a bug report.
        SelectionContainer { Text(value, style = MaterialTheme.typography.bodyMedium) }
    }
}

/**
 * The approval mode in the words a non-technical owner would use for it: how far the agent is
 * trusted to act before asking. The mode's own name is on the picker this tile opens.
 */
internal val ApprovalMode.trustLabel: String
    get() = when (this) {
        ApprovalMode.SAFE, ApprovalMode.PLAN, ApprovalMode.READ_ONLY -> "Careful"
        ApprovalMode.ACCEPT_EDITS, ApprovalMode.AUTO -> "Balanced"
        ApprovalMode.ULW, ApprovalMode.FULL_ACCESS -> "Unattended"
    }

/** The hero's line under the agent's name. */
internal fun heroStatus(connection: ConnectionState, activity: SessionActivity?, awaitingOnboardCode: Boolean): String = when {
    awaitingOnboardCode -> "Waiting for your invite code"
    connection is ConnectionState.Connected -> when (activity) {
        SessionActivity.RUNNING -> "Working on a task"
        SessionActivity.AWAITING_USER -> "Waiting for you"
        else -> "Idle · ready for a new task"
    }
    else -> connectionWords(connection, awaitingOnboardCode = false)
}

private fun connectionWords(connection: ConnectionState, awaitingOnboardCode: Boolean): String = when {
    awaitingOnboardCode -> "Waiting for your invite code"
    connection is ConnectionState.Connected -> "Connected"
    connection is ConnectionState.Connecting -> "Connecting…"
    connection is ConnectionState.Reconnecting -> "Reconnecting…"
    connection is ConnectionState.Error -> "Can't reach this agent"
    else -> "Not connected"
}

private fun capabilitiesSummary(skills: Int): String = when (skills) {
    0 -> "None yet"
    1 -> "1 skill"
    else -> "$skills skills"
}

private fun toolsSummary(tools: Int): String = if (tools == 1) "1 tool" else "$tools tools"

/** A Latest row's second line: what the open conversation is doing, or how long ago a chat was used. */
internal fun chatSubtitle(chat: DashboardChat, activity: SessionActivity?, now: Long): String = when {
    chat.active && activity == SessionActivity.RUNNING -> "Running now"
    chat.active && activity == SessionActivity.AWAITING_USER -> "Waiting for you"
    else -> relativeAge(now, chat.updatedAt)
}

/** How long before [now] something happened, in the largest whole unit: "45m ago". */
internal fun relativeAge(now: Long, then: Long): String {
    val minutes = (now - then).coerceAtLeast(0L) / 60_000L
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 60 * 24 -> "${minutes / 60}h ago"
        else -> "${minutes / (60 * 24)}d ago"
    }
}
