package ai.openonion.oochat.ui.navigation

import ai.openonion.oochat.ui.adaptive.ChatWithHome
import ai.openonion.oochat.ui.adaptive.HomePlacement
import ai.openonion.oochat.ui.adaptive.HomeTab
import ai.openonion.oochat.ui.adaptive.Pane
import ai.openonion.oochat.ui.adaptive.currentHomePlacement
import ai.openonion.oochat.ui.chat.ChatViewModel
import ai.openonion.oochat.ui.common.appViewModel
import ai.openonion.oochat.ui.controlcenter.ControlCenterScreenWrapper
import ai.openonion.oochat.ui.controlcenter.controlCenterSupported
import ai.openonion.oochat.ui.dashboard.DashboardScreenWrapper
import ai.openonion.oochat.ui.home.AgentDashboardNavigation
import ai.openonion.oochat.ui.home.AgentDashboardScreenWrapper
import ai.openonion.oochat.ui.home.AgentHome
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavBackStackEntry

/**
 * A chat destination: the chat, and the agent's Home with it (see
 * [ai.openonion.oochat.ui.adaptive.homePlacementFor]). Home is the Dashboard the app draws for
 * every agent (W16AAPPLE-115): a tab beside the chat's on a phone, and a pane beside the chat
 * itself, with no tabs, where the window has room for both (a tablet, or a foldable unfolded).
 *
 * [chat] gets the [ChatViewModel] of [chatEntry], the one Home reads from too, whether Home is
 * already part of the layout, and a count of the times Home has asked for the chat's drawer of
 * conversations. It is always shown in the same place, so folding or unfolding does not rebuild
 * it; see [ChatWithHome].
 *
 * @param opensOnHome The phone layout starts on the Dashboard tab. False when the destination was
 *   opened for a conversation (a drawer row, a link, a notification), which starts on the chat.
 */
@Composable
internal fun ChatWithHomeRoute(
    chatEntry: NavBackStackEntry,
    opensOnHome: Boolean,
    onNavigateToLogs: () -> Unit,
    onNavigateToRem: () -> Unit,
    chat: @Composable (viewModel: ChatViewModel, homeInLayout: Boolean, openDrawerRequest: Int) -> Unit
) {
    ChatWithHomePanes(
        viewModel = appViewModel(),
        hostsApps = ::controlCenterSupported,
        opensOnHome = opensOnHome,
        chat = chat,
        dashboard = { viewModel, navigation ->
            AgentDashboardScreenWrapper(
                viewModel = viewModel,
                navigation = AgentDashboardNavigation(
                    agentPageLabel = navigation.agentPageLabel,
                    onOpenChat = navigation.onOpenChat,
                    onOpenChatList = navigation.onOpenChatList,
                    onOpenAgentPage = navigation.onOpenAgentPage,
                    showsMenu = navigation.showsMenu,
                    onNavigateToLogs = onNavigateToLogs,
                    onNavigateToRem = onNavigateToRem
                )
            )
        },
        agentPage = { opensControlCenter, besideChat, onNavigateBack, onOpenChat ->
            if (opensControlCenter) {
                ControlCenterScreenWrapper(chatEntry, onNavigateBack = onNavigateBack, besideChat = besideChat, onOpenChat = onOpenChat)
            } else {
                DashboardScreenWrapper(chatEntry, onNavigateBack = onNavigateBack, besideChat = besideChat, onOpenChat = onOpenChat)
            }
        }
    )
}

/**
 * The panes of [ChatWithHomeRoute], with the parts that need a navigation back stack and a WebView
 * passed in. [hostsApps] says whether the device can host a Control Center app.
 *
 * [dashboard] draws the app's own Dashboard, which is what Home shows: the Home tab on a phone,
 * the pane beside the chat where there is room. [agentPage] draws the page the agent published, as
 * the Control Center when its first argument is true and as its `dashboard.html` otherwise, in
 * place of the Dashboard once it is opened from there. Its second argument says whether the chat
 * is in view beside it, its third goes back from it to the Dashboard, and its fourth shows the chat.
 */
@Composable
internal fun ChatWithHomePanes(
    viewModel: ChatViewModel,
    hostsApps: () -> Boolean,
    opensOnHome: Boolean,
    chat: @Composable (viewModel: ChatViewModel, homeInLayout: Boolean, openDrawerRequest: Int) -> Unit,
    dashboard: @Composable (viewModel: ChatViewModel, navigation: AgentDashboardNavigation) -> Unit,
    agentPage: @Composable (opensControlCenter: Boolean, besideChat: Boolean, onNavigateBack: () -> Unit, onOpenChat: () -> Unit) -> Unit
) {
    val dashboardHtml by viewModel.dashboardHtml.collectAsState()
    val controlCenterApp by viewModel.controlCenterApp.collectAsState()
    // Lazy: asking loads the WebView, which only an agent with both kinds of Home page needs.
    val webViewHostsApps = remember { lazy { hostsApps() } }
    val agentHome = AgentHome(dashboardHtml != null, controlCenterApp != null) { webViewHostsApps.value }
    val placement = currentHomePlacement()
    var tab by rememberSaveable { mutableStateOf(if (opensOnHome) HomeTab.HOME else HomeTab.CHAT) }
    // The agent's own page, opened from the Dashboard and shown in its place until gone back from.
    var agentPageOpen by rememberSaveable { mutableStateOf(false) }
    // Not saved: a request is acted on once, and a recreated screen must not open the drawer again.
    var openDrawerRequest by remember { mutableIntStateOf(0) }
    val showsAgentPage = agentPageOpen && agentHome.hasPage
    val besideChat = placement == HomePlacement.BESIDE_CHAT
    // Back from the agent's page is back to the Dashboard it was opened from, whenever that page
    // is in view: beside the chat, or on the Home tab. The tabs themselves are peers, so back from
    // either leaves the screen as it does anywhere else.
    BackHandler(enabled = showsAgentPage && (besideChat || tab == HomeTab.HOME)) {
        agentPageOpen = false
    }

    ChatWithHome(
        placement = placement,
        tab = tab,
        onTabChange = { tab = it },
        homeLabel = "Dashboard",
        chat = { modifier ->
            Pane(modifier) { chat(viewModel, placement != HomePlacement.OWN_SCREEN, openDrawerRequest) }
        },
        home = { modifier ->
            Pane(modifier) {
                when {
                    showsAgentPage ->
                        agentPage(agentHome.opensControlCenter, besideChat, { agentPageOpen = false }, { tab = HomeTab.CHAT })
                    else -> dashboard(
                        viewModel,
                        AgentDashboardNavigation(
                            // Named for what the agent sent, as the page it opens is.
                            agentPageLabel = when {
                                !agentHome.hasPage -> null
                                agentHome.opensControlCenter -> "Control Center"
                                else -> "Agent's page"
                            },
                            onOpenChat = { tab = HomeTab.CHAT },
                            onOpenChatList = {
                                tab = HomeTab.CHAT
                                openDrawerRequest++
                            },
                            onOpenAgentPage = { agentPageOpen = true },
                            // Beside the chat, the chat's own menu is right next to this one.
                            showsMenu = !besideChat
                        )
                    )
                }
            }
        }
    )
}
