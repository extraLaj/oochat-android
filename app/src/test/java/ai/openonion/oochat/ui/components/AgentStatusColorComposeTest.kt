package ai.openonion.oochat.ui.components

import ai.openonion.oochat.domain.model.AgentStatus
import ai.openonion.oochat.ui.theme.ConnectOnionTheme
import ai.openonion.oochat.ui.theme.statusColors
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * An agent's status colour (W16AAPPLE-114): each state has a hue of its own, so the five cannot be
 * mistaken for one another, and [AgentStatusChip] says each in a word as well, so none is told by
 * its colour alone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AgentStatusColorComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val seen = mutableMapOf<String, Color>()

    private fun render() {
        composeRule.setContent {
            ConnectOnionTheme {
                seen["success"] = MaterialTheme.statusColors.success
                seen["error"] = MaterialTheme.colorScheme.error
                seen["active"] = agentStatusColor(AgentStatus.Active)
                seen["connecting"] = agentStatusColor(AgentStatus.Connecting)
                seen["connected"] = agentStatusColor(AgentStatus.Connected)
                seen["disabled"] = agentStatusColor(AgentStatus.Disabled)
                seen["failed"] = agentStatusColor(AgentStatus.Error("no route"))
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `each status has a colour of its own`() {
        render()

        val statuses = listOf("active", "connecting", "connected", "disabled", "failed").map { seen.getValue(it) }
        assertEquals("two statuses share a colour: $seen", statuses.size, statuses.distinct().size)
    }

    @Test
    fun `connected is the brand green and a failure the error red`() {
        render()

        assertEquals(seen["success"], seen["connected"])
        assertEquals(seen["error"], seen["failed"])
        assertNotEquals(seen["connected"], seen["connecting"])
    }

    @Test
    fun `the chip says its status in a word, and that word is all TalkBack reads of it`() {
        val statuses = listOf(
            AgentStatus.Active to "Active",
            AgentStatus.Connected to "Connected",
            AgentStatus.Connecting to "Connecting",
            AgentStatus.Error("no route") to "Error",
            AgentStatus.Disabled to "Disabled"
        )
        composeRule.setContent {
            ConnectOnionTheme {
                Column { statuses.forEach { (status, _) -> AgentStatusChip(status) } }
            }
        }

        statuses.forEach { (_, word) ->
            composeRule.onNodeWithText(word, useUnmergedTree = true).assertExists()
            composeRule.onNodeWithContentDescription(word).assertExists()
        }
    }
}
