package ai.openonion.oochat.ui.chat

import ai.openonion.oochat.ui.theme.ConnectOnionTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * W16AAPPLE-127: the drawer stays open beside a chat that is at least 840dp wide (M3's Expanded
 * width), with no menu button, and slides over a narrower one. The chat is the real [ChatScreen]
 * over a real [ChatViewModel], as in [ChatHomeButtonComposeTest].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatDrawerPlacementComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var viewModel: ChatViewModel

    @Before
    fun setUp() {
        viewModel = chatViewModelOver(RecompFakeConnectUseCase())
    }

    /** The chat alone, or in a pane [paneWidth] wide as it is beside an agent's Home. */
    private fun showChat(paneWidth: Dp? = null) {
        composeRule.setContent {
            ConnectOnionTheme {
                val chat: @Composable () -> Unit = {
                    ChatScreen(connectToAgentId = "0x1a2b3c4d5e6f7a8b", viewModel = viewModel)
                }
                // The pane sits in a full-window Box, as ChatWithHome's Row holds it: the window's
                // own root would otherwise stretch it to the full width.
                if (paneWidth == null) {
                    chat()
                } else {
                    Box(Modifier.fillMaxSize()) { Box(Modifier.width(paneWidth).fillMaxHeight()) { chat() } }
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h800dp")
    fun `a tablet in landscape keeps the drawer open beside the chat, with no menu button`() {
        showChat()

        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Open menu").assertDoesNotExist()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp")
    fun `a phone keeps the drawer behind the menu button`() {
        showChat()

        composeRule.onNodeWithContentDescription("Open menu").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").assertDoesNotExist()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h800dp")
    fun `a chat in a half-width pane beside Home keeps the sliding drawer, so the chat keeps its room`() {
        showChat(paneWidth = 640.dp)

        composeRule.onNodeWithContentDescription("Open menu").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").assertDoesNotExist()
    }
}
