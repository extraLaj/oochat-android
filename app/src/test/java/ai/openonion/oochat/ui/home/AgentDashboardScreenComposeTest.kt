package ai.openonion.oochat.ui.home

import ai.openonion.oochat.domain.model.AgentSkill
import ai.openonion.oochat.domain.model.ApprovalMode
import ai.openonion.oochat.domain.model.ConnectionState
import ai.openonion.oochat.domain.model.SessionActivity
import ai.openonion.oochat.ui.theme.ConnectOnionTheme
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The agent's Dashboard, the page the app opens on (W16AAPPLE-115): what it says and where its taps go. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AgentDashboardScreenComposeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val minute = 60_000L
    private val now = 1_000 * minute

    private val connected = AgentDashboardState(
        agentName = "oo",
        connectionState = ConnectionState.Connected(address = "0xagent"),
        activity = SessionActivity.IDLE,
        model = "gemini-flash",
        toolCount = 23,
        approvalMode = ApprovalMode.SAFE,
        chats = listOf(
            DashboardChat("c1", "Build a text file", updatedAt = now - 45 * minute, active = true),
            DashboardChat("c2", "Plan the week", updatedAt = now - 3 * 60 * minute),
            DashboardChat("c3", "Third", updatedAt = now - 2 * 24 * 60 * minute),
            DashboardChat("c4", "Fourth", updatedAt = now - 9 * 24 * 60 * minute)
        ),
        agentAddress = "0xagent",
        sessionId = "c1",
        now = now
    )

    private val events = mutableListOf<String>()
    private val actions = AgentDashboardActions(
        onOpenMenu = { events += "menu" },
        onNewChat = { events += "new" },
        onOpenChat = { events += "chat:$it" },
        onSeeAllChats = { events += "all" },
        onChangeTrust = { events += "trust" },
        onOpenAgentPage = { events += "page" },
        onNavigateToLogs = { events += "logs" },
        onOpenRem = { events += "rem" }
    )

    private fun show(state: AgentDashboardState = connected) {
        composeRule.setContent { ConnectOnionTheme { AgentDashboardScreen(state, actions) } }
        composeRule.waitForIdle()
    }

    @Test
    fun `it says who the agent is, what it is doing, and what it runs on`() {
        show()

        composeRule.onNodeWithTag("agent_dashboard_status").assertIsDisplayed()
        composeRule.onNodeWithText("Idle · ready for a new task").assertIsDisplayed()
        composeRule.onNodeWithText("23 tools").assertIsDisplayed()
        composeRule.onNodeWithText("gemini-flash").assertIsDisplayed()
        composeRule.onNodeWithText("Recent chats").assertIsDisplayed()
        composeRule.onNodeWithText("4").assertIsDisplayed()
        composeRule.onNodeWithText("None yet").assertIsDisplayed()
    }

    @Test
    fun `New chat asks for a new chat`() {
        show()

        composeRule.onNodeWithText("New chat").performClick()

        assertEquals(listOf("new"), events)
    }

    @Test
    fun `the menu button, the Recent chats tile and See all each ask for the list of chats`() {
        show()

        composeRule.onNodeWithContentDescription("Open menu").performClick()
        composeRule.onNodeWithText("Recent chats").performClick()
        composeRule.onNodeWithText("See all").performScrollTo().performClick()

        assertEquals(listOf("menu", "all", "all"), events)
    }

    @Test
    fun `Latest lists the newest chats and a row opens its chat`() {
        show()

        composeRule.onNodeWithText("45m ago").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Third").assertExists()
        composeRule.onNodeWithText("Fourth").assertDoesNotExist()

        composeRule.onNodeWithText("Plan the week").performScrollTo().performClick()

        assertEquals(listOf("chat:c2"), events)
    }

    @Test
    fun `the open chat's row says the agent is working on it`() {
        show(connected.copy(activity = SessionActivity.RUNNING))

        composeRule.onNodeWithText("Working on a task").assertIsDisplayed()
        composeRule.onNodeWithText("Running now").assertExists()
    }

    @Test
    fun `an agent with no chats is told how to start one, with nothing to see all of`() {
        show(connected.copy(chats = emptyList()))

        composeRule.onNodeWithText("0").assertIsDisplayed()
        composeRule.onNodeWithText("No chats yet. Start one with New chat.").assertExists()
        composeRule.onNodeWithText("See all").assertDoesNotExist()
    }

    @Test
    fun `the Trust level tile names the mode in plain words and asks to change it`() {
        show(connected.copy(approvalMode = ApprovalMode.AUTO))

        composeRule.onNodeWithText("Trust level").performClick()

        assertEquals(listOf("trust"), events)
    }

    @Test
    fun `the trust level cannot be changed while not connected`() {
        show(connected.copy(connectionState = ConnectionState.Disconnected, activity = null))

        composeRule.onNodeWithText("Not connected").assertIsDisplayed()
        composeRule.onNodeWithText("Trust level").assertHasNoClickAction()
        composeRule.onNodeWithText("Host details").assertHasClickAction()
    }

    @Test
    fun `Capabilities counts the agent's skills and lists them`() {
        show(connected.copy(skills = listOf(AgentSkill("review", "Review a change"), AgentSkill("standup"))))

        composeRule.onNodeWithText("2 skills").performClick()

        composeRule.onNodeWithText("/review").assertIsDisplayed()
        composeRule.onNodeWithText("Review a change").assertIsDisplayed()
        composeRule.onNodeWithText("/standup").assertIsDisplayed()
    }

    @Test
    fun `Host details shows the connection and leads to the logs`() {
        show()

        composeRule.onNodeWithText("Host details").performClick()

        composeRule.onNodeWithText("Connected").assertIsDisplayed()
        composeRule.onNodeWithText("0xagent").assertIsDisplayed()

        composeRule.onNodeWithText("View logs").performClick()

        assertEquals(listOf("logs"), events)
    }

    @Test
    fun `an agent that published a page of its own gets a row that opens it`() {
        show(connected.copy(agentPageLabel = "Control Center"))

        composeRule.onNodeWithText("Control Center").performScrollTo().performClick()

        assertEquals(listOf("page"), events)
    }

    @Test
    fun `an agent with no page of its own has no such row`() {
        show()

        composeRule.onNodeWithText("A page this agent published").assertDoesNotExist()
    }

    @Test
    fun `the menu has no Clear chat, as there is no conversation here to clear`() {
        show()

        composeRule.onNodeWithContentDescription("More options").performClick()

        composeRule.onNodeWithText("View logs").assertIsDisplayed()
        composeRule.onNodeWithText("Clear chat").assertDoesNotExist()
    }
}
