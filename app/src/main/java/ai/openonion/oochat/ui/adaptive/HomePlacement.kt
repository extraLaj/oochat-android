package ai.openonion.oochat.ui.adaptive

import androidx.window.core.layout.WindowSizeClass

/** Where the agent's Home is shown. */
internal enum class HomePlacement {
    OWN_SCREEN,

    /** Next to the chat, so both can be used at once. */
    BESIDE_CHAT,

    /** A tab next to the chat's, so each gets the whole screen. */
    TABBED
}

/**
 * Where Home goes in a window of [windowSize]. Beside the chat from the Medium width class up
 * (600dp: a tablet, or a foldable unfolded) as long as the window is tall enough for two panes,
 * which keeps a phone on its side out, and only for an agent that published a page of its own
 * ([hasHome]): with none there is nothing to put beside the chat. In a tab otherwise, so a phone,
 * even turned on its side, gets the chat and Home a whole screen each. A phone has the tab for
 * every agent, because its Home is the Dashboard the app draws itself (W16AAPPLE-115).
 */
internal fun homePlacementFor(windowSize: WindowSizeClass, hasHome: Boolean): HomePlacement {
    val roomForTwoPanes = windowSize.isAtLeastBreakpoint(
        WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND,
        WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND
    )
    return when {
        !roomForTwoPanes -> HomePlacement.TABBED
        hasHome -> HomePlacement.BESIDE_CHAT
        else -> HomePlacement.OWN_SCREEN
    }
}
