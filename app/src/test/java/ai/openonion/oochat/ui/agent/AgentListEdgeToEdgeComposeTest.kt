package ai.openonion.oochat.ui.agent

import ai.openonion.oochat.domain.model.AgentProfile
import ai.openonion.oochat.ui.theme.ConnectOnionTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * W16AAPPLE-127: the agent list is drawn edge to edge. It runs to the bottom of the window, behind
 * the navigation bar, and its last row can still scroll clear of the bar instead of being cut off
 * above a blank strip.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AgentListEdgeToEdgeComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val navigationBar = 48.dp

    private val agents = (1..20).map {
        AgentProfile(id = "a$it", address = "0x%064x".format(it), name = "Agent $it", serverUrl = "https://oo.openonion.ai", createdAt = 0L)
    }

    @Test
    fun `the list reaches the bottom of the window and its last row scrolls clear of the navigation bar`() {
        composeRule.setContent {
            ConnectOnionTheme {
                AgentListScreen(
                    agents = agents,
                    selectedAgentId = null,
                    defaultAgentId = null,
                    onAgentSelect = {},
                    onCreateAgent = {},
                    onEditAgent = {},
                    onDeleteAgent = {},
                    onSetDefault = {},
                    bottomInset = navigationBar,
                    modifier = Modifier.fillMaxSize().testTag(LIST)
                )
            }
        }
        val window = composeRule.onRoot().getBoundsInRoot()

        assertEquals("the list stops short of the window", window.bottom, composeRule.onNodeWithTag(LIST).getBoundsInRoot().bottom)

        composeRule.onNodeWithTag(LIST).performScrollToIndex(agents.lastIndex)
        val lastRow = composeRule.onNodeWithText("Agent 20").getBoundsInRoot()
        assertTrue("the last row ends at ${lastRow.bottom}, under the bar", lastRow.bottom <= window.bottom - navigationBar)
    }

    private companion object {
        const val LIST = "agent-list"
    }
}
