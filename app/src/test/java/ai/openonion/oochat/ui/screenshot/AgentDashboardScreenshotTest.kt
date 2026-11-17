package ai.openonion.oochat.ui.screenshot

import ai.openonion.oochat.domain.model.ApprovalMode
import ai.openonion.oochat.domain.model.ConnectionState
import ai.openonion.oochat.domain.model.SessionActivity
import ai.openonion.oochat.ui.adaptive.HomeTab
import ai.openonion.oochat.ui.adaptive.HomeTabs
import ai.openonion.oochat.ui.home.AgentDashboardActions
import ai.openonion.oochat.ui.home.AgentDashboardScreen
import ai.openonion.oochat.ui.home.AgentDashboardState
import ai.openonion.oochat.ui.home.DashboardChat
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The agent's Dashboard under its tab, as the app opens on a phone (W16AAPPLE-115). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AgentDashboardScreenshotTest {

    @get:Rule(order = 0)
    val localeRule = DeterministicLocaleRule()

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    private val minute = 60_000L
    private val now = 1_000 * minute

    private val state = AgentDashboardState(
        agentName = "oo",
        connectionState = ConnectionState.Connected(address = "0xagent"),
        activity = SessionActivity.IDLE,
        model = "gemini-3.8-flash",
        toolCount = 23,
        approvalMode = ApprovalMode.SAFE,
        chats = listOf(
            DashboardChat("c1", "use claude code to build a .txt file with a haiku in it", updatedAt = now - 45 * minute),
            DashboardChat("c2", "Summarise this week's invoices", updatedAt = now - 3 * 60 * minute),
            DashboardChat("c3", "Draft a reply to the landlord", updatedAt = now - 26 * 60 * minute),
            DashboardChat("c4", "Plan the offsite", updatedAt = now - 4 * 24 * 60 * minute)
        ),
        now = now
    )

    private fun capture(name: String, palette: Palette, state: AgentDashboardState) =
        composeRule.captureThemed(name, palette) {
            Column {
                AgentDashboardScreen(state, AgentDashboardActions(), modifier = Modifier.weight(1f))
                HomeTabs(tab = HomeTab.HOME, homeLabel = "Dashboard", onTabChange = {})
            }
        }

    @Test
    fun `dashboard - light`() = capture("agent_dashboard", Palette.Light, state)

    @Test
    fun `dashboard - dark`() = capture("agent_dashboard", Palette.Dark, state)

    @Test
    fun `dashboard for a new agent with a page of its own - light`() = capture(
        "agent_dashboard_new_agent",
        Palette.Light,
        state.copy(chats = emptyList(), agentPageLabel = "Control Center", activity = null, connectionState = ConnectionState.Connecting)
    )
}
