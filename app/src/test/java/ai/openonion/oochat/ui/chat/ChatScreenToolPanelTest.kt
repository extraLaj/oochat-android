package ai.openonion.oochat.ui.chat

import ai.openonion.oochat.data.local.IgnoredIdsManager
import ai.openonion.oochat.domain.model.AgentLiveProfile
import ai.openonion.oochat.domain.model.AgentProfile
import ai.openonion.oochat.domain.model.AgentTool
import ai.openonion.oochat.domain.model.AgentToolParameter
import ai.openonion.oochat.domain.usecase.ConversationHistoryUseCase
import ai.openonion.oochat.domain.usecase.RecentChatsSync
import ai.openonion.oochat.domain.usecase.RecentChatsSyncResult
import ai.openonion.oochat.network.AgentDiscoveryService
import ai.openonion.oochat.ui.theme.ConnectOnionTheme
import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Regression coverage for the tool form's normal-user path through [ChatScreen]. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatScreenToolPanelTest {

    @get:Rule
    val composeRule = createComposeRule()

    private companion object {
        const val AGENT_ADDRESS = "0x1a2b3c4d5e6f7a8b"
        const val AGENT_ID = "agent-1"
    }

    private lateinit var connectUseCase: RecompFakeConnectUseCase
    private lateinit var agentRepository: RecompFakeAgentRepository
    private lateinit var sessionRepository: RecompFakeSessionRepository
    private lateinit var messageRepository: RecompFakeMessageRepository
    private lateinit var viewModel: ChatViewModel

    @Before
    fun setUp() {
        connectUseCase = RecompFakeConnectUseCase()
        agentRepository = RecompFakeAgentRepository()
        sessionRepository = RecompFakeSessionRepository()
        messageRepository = RecompFakeMessageRepository()
        agentRepository.seed(
            AgentProfile(
                id = AGENT_ID,
                name = "Tool Agent",
                address = AGENT_ADDRESS,
                serverUrl = "wss://relay.test",
                createdAt = 0L,
                isActive = true
            )
        )

        viewModel = ChatViewModel(
            application = ApplicationProvider.getApplicationContext<Application>(),
            agentDiscovery = AgentDiscoveryService(),
            connectUseCase = connectUseCase,
            agentRepository = agentRepository,
            configRepository = RecompFakeConfigRepository(),
            ignoredIdsManager = IgnoredIdsManager(RecompFakeIgnoredIdsStorage()),
            appSettings = RecompFakeAppSettings(),
            messageRepository = messageRepository,
            conversationHistory = ConversationHistoryUseCase(
                agentRepository,
                sessionRepository,
                messageRepository,
                FakePersistenceTransaction(messageRepository, sessionRepository)
            ),
            recentChatsSync = RecentChatsSync { _, _, _, _ -> RecentChatsSyncResult.Unsupported },
            initialMyAddress = "0xTEST",
            networkMonitor = RecompFakeNetworkMonitor(),
        )
    }

    @Test
    fun `normal users see structured tool fields in the real chat screen`() {
        connectUseCase.liveAgentProfile.value = AgentLiveProfile(
            sessionId = "session-1",
            name = "Tool Agent",
            address = AGENT_ADDRESS,
            model = "test-model",
            tools = listOf("read_file"),
            toolDefinitions = listOf(
                AgentTool(
                    name = "read_file",
                    description = "Read a file from the agent.",
                    parameters = listOf(
                        AgentToolParameter(
                            name = "path",
                            type = "string",
                            description = "The file path to read.",
                            required = true
                        )
                    )
                )
            )
        )
        connectUseCase.markConnected(AGENT_ADDRESS)

        composeRule.setContent {
            ConnectOnionTheme {
                ChatScreen(
                    connectToAgentId = AGENT_ADDRESS,
                    viewModel = viewModel
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Actions").assertExists()
        composeRule.onNodeWithText("Show").performClick()
        composeRule.onNodeWithTag(EXEC_TOOL_SUBMIT_TAG).assertExists()
        composeRule.onNodeWithText("Path (required)").assertExists()
        composeRule.onNodeWithText("Arguments JSON").assertDoesNotExist()
        composeRule.onNodeWithText("Use JSON").assertDoesNotExist()
    }
}
