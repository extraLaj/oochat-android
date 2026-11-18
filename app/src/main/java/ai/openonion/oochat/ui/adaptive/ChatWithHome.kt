package ai.openonion.oochat.ui.adaptive

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

/** The two tabs of a phone's chat: the conversation, and the agent's Home. */
internal enum class HomeTab { CHAT, HOME }

/**
 * The chat, and the agent's Home beside it when [placement] says so.
 *
 * The chat is always the first child of the same Row, whether or not Home is shown, so folding or
 * unfolding a device adds and removes Home without moving the chat in the composition. A chat that
 * moved would be rebuilt from nothing and lose its typed text and scroll position.
 *
 * With [HomePlacement.TABBED] a bar of tabs goes under them (W16AAPPLE-114), where a thumb is, and
 * [tab] says which one is showing; [onTabChange] is asked for the other when its tab is chosen,
 * and [homeLabel] is the name on Home's. The tab not showing is shrunk to nothing rather than
 * taken out, so what it holds (the transcript's place, a draft, a page's state) is still there
 * when it is chosen again. Home is only built the first time its tab is opened, so an agent's
 * page is not loaded for someone who never looks at it.
 *
 * The bar steps aside while the keyboard is up: the keyboard covers where it was, and a bar kept
 * above it would sit between the message box and the keys.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChatWithHome(
    placement: HomePlacement,
    tab: HomeTab,
    onTabChange: (HomeTab) -> Unit,
    homeLabel: String,
    chat: @Composable (Modifier) -> Unit,
    home: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabbed = placement == HomePlacement.TABBED
    val beside = placement == HomePlacement.BESIDE_CHAT
    var homeOpened by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(tabbed, tab) {
        if (tabbed && tab == HomeTab.HOME) homeOpened = true
    }
    val showTabs = tabbed && !WindowInsets.isImeVisible
    val tabs: @Composable () -> Unit = { HomeTabs(tab, homeLabel, onTabChange) }
    // On the Chat tab the chat draws the bar itself, at the foot of its own screen, so that its
    // drawer of conversations and the scrim behind it cover the bar too. Under Home it is drawn here.
    val tabsUnderHome = showTabs && tab == HomeTab.HOME

    Column(modifier = modifier.fillMaxSize()) {
        // The tabs take the navigation bar's inset, so Home above them must not leave room for it too.
        val aboveTabs = if (tabsUnderHome) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier
        Row(modifier = Modifier.weight(1f).fillMaxWidth().then(aboveTabs)) {
            CompositionLocalProvider(LocalChatBottomBar provides tabs.takeIf { showTabs && tab == HomeTab.CHAT }) {
                chat(if (tabbed && tab == HomeTab.HOME) OutOfSight else Modifier.weight(1f))
            }
            if (beside) VerticalDivider()
            if (beside || (tabbed && (tab == HomeTab.HOME || homeOpened))) {
                home(if (tabbed && tab == HomeTab.CHAT) OutOfSight else Modifier.weight(1f))
            }
        }
        if (tabsUnderHome) tabs()
    }
}

/**
 * The bar the chat screen puts along its own bottom edge, under the message box: the Chat and
 * Home tabs, when the chat is one of two tabs and is the one showing. Null anywhere else. The
 * bar takes the navigation bar's inset, so a screen that draws it leaves that inset to it.
 */
internal val LocalChatBottomBar = staticCompositionLocalOf<(@Composable () -> Unit)?> { null }

/**
 * A pane that takes no room: it is still composed, so it keeps its state, but nothing of it shows
 * or is read out by a screen reader. It is still measured at the size it would have, because a
 * pane squeezed to nothing would let things that work out their place from their size, like the
 * chat's navigation drawer, lose it. The clip comes first so that it is to the empty box the pane
 * takes, not to its content's size: where that content lands depends on which way the row runs,
 * and right to left it lands over the other tab.
 */
private val OutOfSight: Modifier = Modifier
    .clipToBounds()
    .clearAndSetSemantics {}
    .layout { measurable, constraints ->
        val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, constraints.maxHeight))
        layout(0, 0) { placeable.place(0, 0) }
    }

/**
 * The Chat and Home tabs, along the bottom of a phone's screen (W16AAPPLE-114). Each is an icon
 * over its name; the one showing is the brand green on a tinted pill, the other grey. The bar
 * takes the navigation bar's inset itself.
 */
@Composable
internal fun HomeTabs(tab: HomeTab, homeLabel: String, onTabChange: (HomeTab) -> Unit) {
    // A chat shrunk to nothing keeps the focus, and with it the keyboard, unless it is let go.
    val focusManager = LocalFocusManager.current
    val choose = { chosen: HomeTab ->
        focusManager.clearFocus()
        onTabChange(chosen)
    }
    Column {
        // A hairline, not a shadow, sets the bar off from the page above it.
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp
        ) {
            HomeTabItem(tab == HomeTab.CHAT, Icons.Outlined.ChatBubbleOutline, "Chat") { choose(HomeTab.CHAT) }
            HomeTabItem(tab == HomeTab.HOME, Icons.Outlined.Dashboard, homeLabel) { choose(HomeTab.HOME) }
        }
    }
}

@Composable
private fun RowScope.HomeTabItem(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(imageVector = icon, contentDescription = null) },
        label = { Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

/** Where Home goes in the window this is composed in; see [homePlacementFor]. */
@Composable
internal fun currentHomePlacement(): HomePlacement =
    homePlacementFor(currentWindowAdaptiveInfo().windowSizeClass)
