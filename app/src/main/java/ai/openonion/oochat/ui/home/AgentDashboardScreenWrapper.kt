package ai.openonion.oochat.ui.home

import ai.openonion.oochat.ui.chat.ChatViewModel
import ai.openonion.oochat.ui.chat.components.ApprovalModeSheet
import ai.openonion.oochat.util.SensitiveText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * Where the Dashboard's taps lead. The Dashboard is a tab beside the chat's, so most of them end
 * on the Chat tab.
 *
 * @param agentPageLabel What the agent's own published page is called, or null when it has none.
 * @param onOpenChat Shows the Chat tab.
 * @param onOpenChatList Shows the Chat tab with its drawer of conversations open.
 * @param onOpenAgentPage Shows the agent's own page in place of the Dashboard.
 */
internal class AgentDashboardNavigation(
    val agentPageLabel: String? = null,
    val onOpenChat: () -> Unit = {},
    val onOpenChatList: () -> Unit = {},
    val onOpenAgentPage: () -> Unit = {},
    val onNavigateToLogs: () -> Unit = {},
    val onNavigateToRem: () -> Unit = {}
)

/**
 * Binds [AgentDashboardScreen] to the chat it sits beside: [viewModel] is that chat's own, so a
 * new chat, a chosen conversation and a changed approval mode are the same ones the Chat tab shows.
 */
@Composable
internal fun AgentDashboardScreenWrapper(viewModel: ChatViewModel, navigation: AgentDashboardNavigation) {
    val profile by viewModel.agentProfile.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val activity by viewModel.sessionActivity.collectAsState()
    val awaitingOnboardCode by viewModel.awaitingOnboardCode.collectAsState()
    val approvalMode by viewModel.approvalMode.collectAsState()
    val sessionModes by viewModel.sessionModes.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val activeSessionId by viewModel.activeSessionId.collectAsState()
    val targetAddress by viewModel.targetAgentAddress.collectAsState()
    val drawerAgents by viewModel.drawerAgents.collectAsState()

    var showTrustSheet by rememberSaveable { mutableStateOf(false) }
    // Until the agent says its own name, the one it was saved under.
    val savedName = drawerAgents.firstOrNull { it.agentAddress == targetAddress }?.name
    val chats = remember(sessions, activeSessionId) {
        sessions.sortedByDescending { it.updatedAt }.map { session ->
            DashboardChat(
                id = session.id,
                title = SensitiveText.redactWithoutPaths(session.title),
                updatedAt = session.updatedAt,
                active = session.id == activeSessionId
            )
        }
    }

    AgentDashboardScreen(
        state = AgentDashboardState(
            agentName = profile?.name?.takeIf { it.isNotBlank() } ?: savedName?.takeIf { it.isNotBlank() } ?: "Agent",
            connectionState = connectionState,
            activity = activity,
            awaitingOnboardCode = awaitingOnboardCode,
            model = profile?.model,
            toolCount = profile?.let { maxOf(it.toolDefinitions.size, it.tools.size) } ?: 0,
            approvalMode = approvalMode,
            skills = profile?.skills.orEmpty(),
            chats = chats,
            agentAddress = profile?.address ?: targetAddress,
            sessionId = activeSessionId,
            agentPageLabel = navigation.agentPageLabel,
            // Aged against the moment the list last changed; a chat used since is at the top anyway.
            now = remember(chats) { System.currentTimeMillis() }
        ),
        actions = AgentDashboardActions(
            onOpenMenu = navigation.onOpenChatList,
            onNewChat = {
                viewModel.startNewSession()
                navigation.onOpenChat()
            },
            onOpenChat = { sessionId ->
                if (sessionId != activeSessionId) viewModel.switchToSession(sessionId)
                navigation.onOpenChat()
            },
            onSeeAllChats = navigation.onOpenChatList,
            onChangeTrust = { showTrustSheet = true },
            onOpenAgentPage = navigation.onOpenAgentPage,
            onNavigateToLogs = navigation.onNavigateToLogs,
            onOpenRem = navigation.onNavigateToRem
        )
    )

    if (showTrustSheet) {
        ApprovalModeSheet(
            current = approvalMode,
            onSelect = viewModel::setApprovalMode,
            onDismiss = { showTrustSheet = false },
            modes = sessionModes
        )
    }
}
