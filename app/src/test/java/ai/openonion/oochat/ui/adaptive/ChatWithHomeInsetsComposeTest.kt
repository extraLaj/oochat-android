package ai.openonion.oochat.ui.adaptive

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The tabs are along the bottom (W16AAPPLE-114) and take the navigation bar's inset, so the page
 * above them must not leave room for it a second time. A page pads its own foot for the
 * navigation bar, as the stand-in here does, and would otherwise leave a gap as tall as that bar
 * between itself and the tabs. The status bar's inset is left to the page, whose top bar takes it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatWithHomeInsetsComposeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `the page ends directly above the tabs, with no second gap for the navigation bar`() {
        WindowCompat.setDecorFitsSystemWindows(composeRule.activity.window, false)
        composeRule.setContent {
            ChatWithHome(
                placement = HomePlacement.TABBED,
                tab = HomeTab.HOME,
                onTabChange = {},
                homeLabel = "Home",
                chat = {},
                home = { modifier ->
                    Box(modifier.fillMaxHeight().windowInsetsPadding(WindowInsets.systemBars), contentAlignment = Alignment.BottomStart) {
                        Box(Modifier.fillMaxWidth().height(40.dp).testTag("foot"))
                        Box(Modifier.fillMaxWidth().height(40.dp).align(Alignment.TopStart).testTag("head"))
                    }
                }
            )
        }
        composeRule.runOnUiThread {
            val bars = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 100, 0, 0))
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 100))
                .build()
            ViewCompat.dispatchApplyWindowInsets(composeRule.activity.window.decorView, bars)
        }
        composeRule.waitForIdle()

        val tab = composeRule.onAllNodes(isSelectable())[0].getBoundsInRoot()
        val root = composeRule.onRoot().getBoundsInRoot()
        val foot = composeRule.onNodeWithTag("foot").getBoundsInRoot()
        val head = composeRule.onNodeWithTag("head").getBoundsInRoot()
        assertTrue("the navigation bar's inset reached the tabs", root.bottom - tab.bottom > 10.dp)
        assertTrue("the page ends where the tabs begin, not a navigation bar higher", tab.top - foot.bottom < 24.dp)
        assertTrue("the status bar's inset is still the page's to take", head.top > 10.dp)
    }
}
