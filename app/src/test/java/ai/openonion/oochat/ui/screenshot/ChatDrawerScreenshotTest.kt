package ai.openonion.oochat.ui.screenshot

import ai.openonion.oochat.ui.chat.ChatScreen
import ai.openonion.oochat.ui.chat.RecompFakeConnectUseCase
import ai.openonion.oochat.ui.chat.chatViewModelOver
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The whole chat screen on a tablet in landscape, where its drawer stays open beside the chat
 * (W16AAPPLE-127), and the chat column stops at the one shared content width.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.MediumTablet)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatDrawerScreenshotTest {

    @get:Rule(order = 0)
    val localeRule = DeterministicLocaleRule()

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    private fun capture(palette: Palette) = composeRule.captureThemed("chat_tablet_drawer", palette) {
        ChatScreen(
            connectToAgentId = "0x1a2b3c4d5e6f7a8b",
            viewModel = chatViewModelOver(RecompFakeConnectUseCase())
        )
    }

    @Test
    fun `chat on a tablet with the drawer beside it - light`() = capture(Palette.Light)

    @Test
    fun `chat on a tablet with the drawer beside it - dark`() = capture(Palette.Dark)
}
