package ai.openonion.oochat.ui.adaptive

import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [currentHomePlacement] reads the real window size: the same rule as [homePlacementFor], fed by
 * the window the app is actually in. Each test sets the window with Robolectric qualifiers.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomePlacementWindowComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun placement(): HomePlacement {
        var seen: HomePlacement? = null
        composeRule.setContent { seen = currentHomePlacement() }
        composeRule.waitForIdle()
        return checkNotNull(seen)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w900dp-h800dp")
    fun `a tablet puts Home beside the chat`() {
        assertEquals(HomePlacement.BESIDE_CHAT, placement())
    }

    @Test
    @Config(sdk = [34], qualifiers = "w700dp-h840dp")
    fun `a foldable unfolded in portrait puts Home beside the chat`() {
        assertEquals(HomePlacement.BESIDE_CHAT, placement())
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h800dp")
    fun `a phone puts Home in a tab`() {
        assertEquals(HomePlacement.TABBED, placement())
    }

    @Test
    @Config(sdk = [34], qualifiers = "w915dp-h412dp")
    fun `a phone on its side puts Home in a tab`() {
        assertEquals(HomePlacement.TABBED, placement())
    }
}
