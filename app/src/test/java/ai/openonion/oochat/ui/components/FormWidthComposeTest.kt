package ai.openonion.oochat.ui.components

import ai.openonion.oochat.ui.theme.ConnectOnionTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** W16AAPPLE-117: settings and forms stop at a comfortable width on a tablet. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FormWidthComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** A screen the way Settings and the forms build theirs: a scrolling column, then [formWidth]. */
    private fun showScreen() {
        composeRule.setContent {
            ConnectOnionTheme {
                Box(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.fillMaxSize().testTag(SCROLL).verticalScroll(rememberScrollState()).formWidth()
                    ) {
                        Box(Modifier.fillMaxWidth().height(50.dp).testTag(ROW))
                    }
                }
            }
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h800dp")
    fun `on a tablet the content is 640dp wide and in the middle`() {
        showScreen()

        val row = composeRule.onNodeWithTag(ROW).getBoundsInRoot()

        assertEquals(640.dp, row.width)
        assertEquals(320.dp, row.left)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h800dp")
    fun `the scrolling area keeps the whole width, so a swipe in the margin still scrolls`() {
        showScreen()

        val scroll = composeRule.onNodeWithTag(SCROLL).getBoundsInRoot()

        assertEquals(1280.dp, scroll.width)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h800dp")
    fun `on a phone the content takes the whole width`() {
        showScreen()

        val row = composeRule.onNodeWithTag(ROW).getBoundsInRoot()

        assertEquals(360.dp, row.width)
        assertEquals(0.dp, row.left)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w500dp-h800dp")
    fun `a window narrower than the cap is not narrowed further`() {
        showScreen()

        assertEquals(500.dp, composeRule.onNodeWithTag(ROW).getBoundsInRoot().width)
    }

    private companion object {
        const val SCROLL = "scroll"
        const val ROW = "row"
    }
}
