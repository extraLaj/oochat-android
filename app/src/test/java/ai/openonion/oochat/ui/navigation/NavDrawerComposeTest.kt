package ai.openonion.oochat.ui.navigation

import ai.openonion.oochat.domain.model.AgentStatus
import ai.openonion.oochat.domain.model.ThemeMode
import ai.openonion.oochat.ui.theme.ConnectOnionTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The drawer's account card names the agent this session is connected to and
 * nothing else. The balance it used to carry as a permanent read-only row is
 * now only said when it is low enough to act on, beside the composer — see
 * [ai.openonion.oochat.ui.chat.components.LowBalanceBar].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavDrawerComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setDrawer(sections: List<DrawerAgentSection> = emptyList()) {
        composeRule.setContent {
            ConnectOnionTheme {
                NavDrawer(
                    agentSections = sections,
                    activeEntryId = null,
                    themeMode = ThemeMode.SYSTEM,
                    onNewChatForAgent = {},
                    onSelectAgentSession = { _, _ -> },
                    onDeleteSession = {},
                    onRenameSession = { _, _ -> },
                    onOpenAgentList = {},
                    onOpenSettings = {},
                    onThemeModeChange = {},
                    connectedAgentAddress = "0x1a2b3c4d5e6f",
                )
            }
        }
    }

    @Test
    fun `the account card names the connected agent`() {
        setDrawer()

        composeRule.onNodeWithText("Agent").assertExists()
        composeRule.onNodeWithText("0x1a2b…5e6f").assertExists()
    }

    @Test
    fun `the account card carries no balance row at all`() {
        setDrawer()

        composeRule.onNodeWithText("Balance").assertDoesNotExist()
    }

    private fun section(id: String, status: AgentStatus) =
        DrawerAgentSection(id, "0x" + id.repeat(32), id, status, emptyList())

    @Test
    fun `the agent count is one label that says how many are online`() {
        setDrawer(listOf(section("a", AgentStatus.Active), section("b", AgentStatus.Connected), section("c", AgentStatus.Disabled)))

        composeRule.onNodeWithText("2 of 3 online").assertExists()
        composeRule.onAllNodesWithText("3").assertCountEquals(0)
    }

    @Test
    fun `with none online the header shows only the total`() {
        setDrawer(listOf(section("a", AgentStatus.Disabled), section("b", AgentStatus.Disabled)))

        composeRule.onNodeWithText("2").assertExists()
        composeRule.onAllNodesWithText("0 of 2 online").assertCountEquals(0)
    }

    @Test
    fun `each agent's status is said in words, not by its dot's colour alone`() {
        setDrawer(listOf(section("a", AgentStatus.Active), section("b", AgentStatus.Connected), section("c", AgentStatus.Disabled)))

        // The Agents screen's words, so the two screens agree (W16AAPPLE-126).
        listOf("Active", "Connected", "Disabled").forEach { composeRule.onNodeWithText(it).assertExists() }
    }

    @Test
    fun `the footer is one list of rows, its labels lined up`() {
        setDrawer()

        // Add agent, Settings and the connected agent share one row style (W16AAPPLE-126), so their
        // labels start at the same edge, where a centred button or an indented card would not.
        val lefts = listOf("Add agent", "Settings", "Agent").map {
            composeRule.onNodeWithText(it, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.left
        }
        assertEquals("label lefts $lefts", 1, lefts.distinct().size)
    }
}
