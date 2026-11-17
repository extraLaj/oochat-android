package ai.openonion.oochat.ui.navigation

import ai.openonion.oochat.domain.model.AgentStatus
import ai.openonion.oochat.ui.theme.ConnectOnionTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.isNotSelected
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The drawer's own parts (W16AAPPLE-114): its footer, the count of agents online, each agent's
 * status in words, and Close. The footer names the agent this session is connected to and carries
 * no balance, which is only said when it is low enough to act on, beside the composer — see
 * [ai.openonion.oochat.ui.chat.components.LowBalanceBar]. The chats under each agent are
 * [SessionDrawerComposeTest]'s.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavDrawerComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var closes = 0

    private fun setDrawer(sections: List<DrawerAgentSection> = emptyList()) {
        composeRule.setContent {
            ConnectOnionTheme {
                NavDrawer(
                    agentSections = sections,
                    activeEntryId = null,
                    onNewChatForAgent = {},
                    onSelectAgentSession = { _, _ -> },
                    onDeleteSession = {},
                    onRenameSession = { _, _ -> },
                    onOpenAgentList = {},
                    onOpenSettings = {},
                    onClearSession = {},
                    onClose = { closes++ },
                    connectedAgentAddress = "0x1a2b3c4d5e6f",
                )
            }
        }
    }

    @Test
    fun `the footer names the connected agent's address`() {
        setDrawer()

        composeRule.onNodeWithText("Agent address").assertExists()
        composeRule.onNodeWithText("0x1a2b…5e6f").assertExists()
    }

    @Test
    fun `the footer carries no balance row at all`() {
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

    // ── W16AAPPLE-114 ───────────────────────────────────────────────────

    @Test
    fun `each agent's status is said in words, and its row is read as name then status`() {
        setDrawer(
            listOf(
                section("a", AgentStatus.Active),
                section("b", AgentStatus.Connected),
                section("c", AgentStatus.Connecting),
                section("d", AgentStatus.Error("no route")),
                section("e", AgentStatus.Disabled)
            )
        )

        val read = listOf("a", "b", "c", "d", "e").map { name ->
            val row = composeRule.onNodeWithText(name).fetchSemanticsNode()
            // What TalkBack reads for the row: its texts, then its descriptions, in order.
            (row.config[SemanticsProperties.Text].map { it.text } + row.config[SemanticsProperties.ContentDescription])
                .joinToString(", ")
        }

        assertEquals(listOf("a, Active", "b, Connected", "c, Connecting", "d, Error", "e, Disabled"), read)
    }

    @Test
    fun `an agent's row says whether its chats are showing, and hides them when tapped`() {
        setDrawer(listOf(DrawerAgentSection("a", "0x" + "a".repeat(32), "Aria", AgentStatus.Connected, listOf(DrawerEntry("c1", "Logs")))))
        composeRule.onNodeWithText("Aria").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))

        composeRule.onNodeWithText("Aria").performClick()

        composeRule.onNodeWithText("Logs").assertDoesNotExist()
        composeRule.onNodeWithText("Aria").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
    }

    @Test
    fun `Close in the header closes the drawer`() {
        setDrawer()

        composeRule.onNodeWithContentDescription("Close menu").assertHasClickAction().performClick()

        assertEquals(1, closes)
    }

    @Test
    fun `everything that can be tapped is at least 48dp tall`() {
        setDrawer(listOf(DrawerAgentSection("a", "0x" + "a".repeat(32), "Aria", AgentStatus.Connected, listOf(DrawerEntry("c1", "Logs")))))

        listOf(
            composeRule.onNodeWithContentDescription("Close menu"),
            composeRule.onNodeWithText("Aria"),
            composeRule.onNodeWithText("Logs"),
            composeRule.onNodeWithContentDescription("More options for \"Logs\""),
            composeRule.onNodeWithContentDescription("New chat with Aria"),
            composeRule.onNodeWithText("Add agent"),
            composeRule.onNodeWithText("Settings")
        ).forEach { it.assertHasClickAction().assertHeightIsAtLeast(48.dp) }
        composeRule.onNodeWithContentDescription("More options for \"Logs\"").assertWidthIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("Close menu").assertWidthIsAtLeast(48.dp)
    }

    @Test
    fun `a chat says when it was last used, and the open one is marked selected`() {
        composeRule.setContent {
            ConnectOnionTheme {
                NavDrawer(
                    agentSections = listOf(
                        DrawerAgentSection(
                            "a", "0x" + "a".repeat(32), "Aria", AgentStatus.Connected,
                            listOf(DrawerEntry("c1", "Logs"), DrawerEntry("c2", "Warnings"))
                        )
                    ),
                    activeEntryId = "c1",
                    onNewChatForAgent = {},
                    onSelectAgentSession = { _, _ -> },
                    onDeleteSession = {},
                    onClearSession = {},
                    onRenameSession = { _, _ -> },
                    onOpenAgentList = {},
                    onOpenSettings = {},
                    onClose = {},
                    sessionTimes = mapOf("c1" to "4m ago")
                )
            }
        }

        composeRule.onNodeWithText("4m ago", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Logs").assert(isSelected())
        composeRule.onNodeWithText("Warnings").assert(isNotSelected())
    }
}
