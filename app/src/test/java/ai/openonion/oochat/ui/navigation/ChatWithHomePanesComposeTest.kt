package ai.openonion.oochat.ui.navigation

import ai.openonion.oochat.domain.model.ControlCenterApp
import ai.openonion.oochat.ui.chat.ChatViewModel
import ai.openonion.oochat.ui.chat.RecompFakeConnectUseCase
import ai.openonion.oochat.ui.chat.chatViewModelOver
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Which Home the chat gets with it — see [ChatWithHomePanes]. On a phone every agent has a
 * Dashboard tab, which is where the app opens (W16AAPPLE-115); on a tablet the page an agent
 * published sits beside the chat, as before. The panes are stand-ins that say which one was asked
 * for; the view model is a real [ChatViewModel] over fakes, as in
 * [ai.openonion.oochat.ui.chat.ChatScreenRecompositionTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatWithHomePanesComposeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val app = ControlCenterApp(url = "https://r-1.apps.example.net/index.html", revision = "sha256:" + "a".repeat(64))
    private lateinit var connectUseCase: RecompFakeConnectUseCase
    private lateinit var viewModel: ChatViewModel

    @Before
    fun setUp() {
        connectUseCase = RecompFakeConnectUseCase()
        viewModel = chatViewModelOver(connectUseCase)
    }

    @Composable
    private fun PanesUnderTest(deviceHostsApps: Boolean, opensOnHome: Boolean = true) {
        ChatWithHomePanes(
            viewModel = viewModel,
            hostsApps = { deviceHostsApps },
            opensOnHome = opensOnHome,
            chat = { _, homeInLayout, openDrawerRequest ->
                Column {
                    Text("chat, Home in layout: $homeInLayout")
                    Text("drawer requests: $openDrawerRequest")
                }
            },
            dashboard = { _, navigation ->
                Column {
                    Text("Dashboard pane")
                    Text("agent page: ${navigation.agentPageLabel}")
                    Text("open the chat", Modifier.clickable(onClick = navigation.onOpenChat))
                    Text("open the chat list", Modifier.clickable(onClick = navigation.onOpenChatList))
                    Text("open the agent page", Modifier.clickable(onClick = navigation.onOpenAgentPage))
                }
            },
            agentPage = { opensControlCenter, besideChat, onNavigateBack, onOpenChat ->
                Column {
                    Text(if (opensControlCenter) "Control Center page" else "Agent page")
                    Text("page beside: $besideChat")
                    Text("back from the page", Modifier.clickable(onClick = onNavigateBack))
                    Text("page opens the chat", Modifier.clickable(onClick = onOpenChat))
                }
            }
        )
    }

    private fun show(deviceHostsApps: Boolean = true, opensOnHome: Boolean = true) {
        composeRule.setContent { PanesUnderTest(deviceHostsApps, opensOnHome) }
        composeRule.waitForIdle()
    }

    private fun pressBack() {
        composeRule.waitForIdle()
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    // ── Tablet: unchanged. The agent's own page beside the chat, and no Dashboard tab. ──

    @Test
    @Config(sdk = [34], qualifiers = "w900dp-h800dp")
    fun `on a tablet an agent with a page gets it beside the chat`() {
        connectUseCase.dashboard.value = "<p>home</p>"
        show()

        composeRule.onNodeWithText("Agent page").assertIsDisplayed()
        composeRule.onNodeWithText("page beside: true").assertIsDisplayed()
        composeRule.onNodeWithText("chat, Home in layout: true").assertIsDisplayed()
        composeRule.onNodeWithText("Dashboard pane").assertDoesNotExist()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w900dp-h800dp")
    fun `on a tablet an agent with a Control Center the device can host gets it beside the chat`() {
        connectUseCase.controlCenter.value = app
        show(deviceHostsApps = true)

        composeRule.onNodeWithText("Control Center page").assertIsDisplayed()
        composeRule.onNodeWithText("chat, Home in layout: true").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w900dp-h800dp")
    fun `on a tablet an agent with no page gets nothing beside the chat`() {
        show()

        composeRule.onNodeWithText("Dashboard pane").assertDoesNotExist()
        composeRule.onNodeWithText("Agent page").assertDoesNotExist()
        composeRule.onNodeWithText("Control Center page").assertDoesNotExist()
        composeRule.onNodeWithText("chat, Home in layout: false").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w900dp-h800dp")
    fun `on a tablet a Control Center the device cannot host stays behind the Home button`() {
        connectUseCase.controlCenter.value = app
        show(deviceHostsApps = false)

        composeRule.onNodeWithText("Control Center page").assertDoesNotExist()
        composeRule.onNodeWithText("chat, Home in layout: false").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w900dp-h800dp")
    fun `on a tablet an agent with both gets its page when the device cannot host the Control Center`() {
        connectUseCase.dashboard.value = "<p>home</p>"
        connectUseCase.controlCenter.value = app
        show(deviceHostsApps = false)

        composeRule.onNodeWithText("Agent page").assertIsDisplayed()
        composeRule.onNodeWithText("Control Center page").assertDoesNotExist()
    }

    // ── Phone: the app opens on the Dashboard tab. ──

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h800dp")
    fun `on a phone the app opens on the Dashboard tab, for an agent with no page of its own too`() {
        show()

        composeRule.onNodeWithText("Chat").assertIsDisplayed()
        composeRule.onNodeWithText("Dashboard").assertIsDisplayed()
        composeRule.onNodeWithText("Dashboard pane").assertIsDisplayed()
        composeRule.onNodeWithText("agent page: null").assertIsDisplayed()
        composeRule.onNodeWithText("chat, Home in layout: true").assertDoesNotExist()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h800dp")
    fun `a chat opened for a conversation starts on the Chat tab`() {
        show(opensOnHome = false)

        composeRule.onNodeWithText("chat, Home in layout: true").assertIsDisplayed()
        composeRule.onNodeWithText("Dashboard").assertIsDisplayed()
        composeRule.onNodeWithText("Dashboard pane").assertDoesNotExist()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h800dp")
    fun `the Chat tab shows the chat in place of the Dashboard`() {
        show()

        composeRule.onNodeWithText("Chat").performClick()

        composeRule.onNodeWithText("chat, Home in layout: true").assertIsDisplayed()
        composeRule.onNodeWithText("Dashboard pane").assertDoesNotExist()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h800dp")
    fun `the Dashboard opening a chat shows the Chat tab without asking for the drawer`() {
        show()

        composeRule.onNodeWithText("open the chat").performClick()

        composeRule.onNodeWithText("chat, Home in layout: true").assertIsDisplayed()
        composeRule.onNodeWithText("drawer requests: 0").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h800dp")
    fun `the Dashboard asking for the chat list shows the Chat tab and asks it for its drawer`() {
        show()

        composeRule.onNodeWithText("open the chat list").performClick()

        composeRule.onNodeWithText("chat, Home in layout: true").assertIsDisplayed()
        composeRule.onNodeWithText("drawer requests: 1").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h800dp")
    fun `the Dashboard names the page an agent published and opens it in its place`() {
        connectUseCase.dashboard.value = "<p>home</p>"
        show()
        composeRule.onNodeWithText("agent page: Agent's page").assertIsDisplayed()

        composeRule.onNodeWithText("open the agent page").performClick()

        composeRule.onNodeWithText("Agent page").assertIsDisplayed()
        composeRule.onNodeWithText("page beside: false").assertIsDisplayed()
        composeRule.onNodeWithText("Dashboard pane").assertDoesNotExist()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h800dp")
    fun `a Control Center app is named as one and opened the same way`() {
        connectUseCase.controlCenter.value = app
        show(deviceHostsApps = true)
        composeRule.onNodeWithText("agent page: Control Center").assertIsDisplayed()

        composeRule.onNodeWithText("open the agent page").performClick()

        composeRule.onNodeWithText("Control Center page").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h800dp")
    fun `going back from the agent's page returns to the Dashboard`() {
        connectUseCase.dashboard.value = "<p>home</p>"
        show()
        composeRule.onNodeWithText("open the agent page").performClick()

        composeRule.onNodeWithText("back from the page").performClick()

        composeRule.onNodeWithText("Dashboard pane").assertIsDisplayed()
        composeRule.onNodeWithText("Agent page").assertDoesNotExist()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h800dp")
    fun `the back button returns from the agent's page to the Dashboard`() {
        connectUseCase.dashboard.value = "<p>home</p>"
        show()
        composeRule.onNodeWithText("open the agent page").performClick()

        pressBack()

        composeRule.onNodeWithText("Dashboard pane").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h800dp")
    fun `the agent's page showing the chat, as a skill run does, goes to the Chat tab`() {
        connectUseCase.dashboard.value = "<p>home</p>"
        show()
        composeRule.onNodeWithText("open the agent page").performClick()

        composeRule.onNodeWithText("page opens the chat").performClick()

        composeRule.onNodeWithText("chat, Home in layout: true").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h800dp")
    fun `an agent's page that goes while it is open leaves the Dashboard`() {
        connectUseCase.dashboard.value = "<p>home</p>"
        show()
        composeRule.onNodeWithText("open the agent page").performClick()

        // The agent reconnects: its page is cleared before it is sent again.
        composeRule.runOnIdle { connectUseCase.dashboard.value = null }

        composeRule.onNodeWithText("Dashboard pane").assertIsDisplayed()
        composeRule.onNodeWithText("agent page: null").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h800dp")
    fun `the tab showing is kept when the screen is recreated, as it is by a rotation`() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent { PanesUnderTest(deviceHostsApps = true) }
        composeRule.onNodeWithText("Chat").performClick()
        composeRule.onNodeWithText("chat, Home in layout: true").assertIsDisplayed()

        restoration.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText("chat, Home in layout: true").assertIsDisplayed()
        composeRule.onNodeWithText("Dashboard pane").assertDoesNotExist()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h800dp")
    fun `a request for the drawer is not repeated when the screen is recreated`() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent { PanesUnderTest(deviceHostsApps = true) }
        composeRule.onNodeWithText("open the chat list").performClick()
        composeRule.onNodeWithText("drawer requests: 1").assertIsDisplayed()

        restoration.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText("drawer requests: 0").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h800dp")
    fun `on a phone a Control Center the device cannot host is still opened from the Dashboard, where it says why`() {
        connectUseCase.controlCenter.value = app
        show(deviceHostsApps = false)

        composeRule.onNodeWithText("agent page: Control Center").assertIsDisplayed()
    }
}
