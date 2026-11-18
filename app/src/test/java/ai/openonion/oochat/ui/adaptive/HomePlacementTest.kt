package ai.openonion.oochat.ui.adaptive

import androidx.window.core.layout.WindowSizeClass
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Where the agent's Home goes in a window of a given size — see [homePlacementFor].
 */
class HomePlacementTest {

    private fun window(widthDp: Int, heightDp: Int = 800) = WindowSizeClass(widthDp, heightDp)

    @Test
    fun `a window 600dp wide or more, and tall enough, puts Home beside the chat`() {
        // 600 is where Medium starts, 700 is a foldable unfolded in portrait, the rest are tablets.
        listOf(600, 700, 840, 1280).forEach { width ->
            assertEquals("$width dp wide", HomePlacement.BESIDE_CHAT, homePlacementFor(window(width)))
        }
        assertEquals(
            "480dp tall is the shortest that still fits two panes",
            HomePlacement.BESIDE_CHAT,
            homePlacementFor(window(900, heightDp = 480))
        )
    }

    @Test
    fun `a narrower window puts Home in a tab`() {
        // A phone, or a foldable that is still folded.
        listOf(320, 360, 411, 599).forEach { width ->
            assertEquals("$width dp wide", HomePlacement.TABBED, homePlacementFor(window(width)))
        }
    }

    @Test
    fun `a wide but short window, a phone on its side, puts Home in a tab`() {
        assertEquals(HomePlacement.TABBED, homePlacementFor(window(915, heightDp = 412)))
        assertEquals(HomePlacement.TABBED, homePlacementFor(window(900, heightDp = 479)))
    }

    @Test
    fun `a wide window puts the Dashboard beside the chat even for an agent with no page of its own`() {
        // Home is the app's own Dashboard, so there is always something to put beside the chat.
        assertEquals(HomePlacement.BESIDE_CHAT, homePlacementFor(window(1280)))
    }
}
