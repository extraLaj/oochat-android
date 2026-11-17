package ai.openonion.oochat.ui.agent

import ai.openonion.oochat.ui.theme.ConnectOnionTheme
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * W16AAPPLE-124: the keyboard's Done on Discover's Server URL searches only when the Discover
 * button would: never an empty URL, and never on top of a search already running.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AgentDiscoveryDoneComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val searched = mutableListOf<String>()

    private fun showDiscover(isLoading: Boolean) {
        composeRule.setContent {
            ConnectOnionTheme {
                AgentDiscoveryScreen(
                    discoveredAgents = emptyList(),
                    isLoading = isLoading,
                    onDiscover = { searched += it },
                    onAddAgent = {},
                    onBack = {}
                )
            }
        }
    }

    private fun serverUrl() = composeRule.onNode(hasSetTextAction() and hasContentDescription("Server URL"))

    @Test
    fun `Done searches the URL typed`() {
        showDiscover(isLoading = false)

        serverUrl().performTextReplacement("https://relay.example.com")
        serverUrl().performImeAction()

        assertEquals(listOf("https://relay.example.com"), searched)
    }

    @Test
    fun `Done on an empty field searches nothing`() {
        showDiscover(isLoading = false)

        serverUrl().performTextReplacement("")
        serverUrl().performImeAction()

        assertEquals(emptyList<String>(), searched)
    }

    @Test
    fun `Done while a search is running does not start a second`() {
        showDiscover(isLoading = true)

        serverUrl().performImeAction()

        assertEquals(emptyList<String>(), searched)
    }
}
