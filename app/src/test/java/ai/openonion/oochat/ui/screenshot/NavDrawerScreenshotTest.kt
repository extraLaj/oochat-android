package ai.openonion.oochat.ui.screenshot

import ai.openonion.oochat.domain.model.AgentStatus
import ai.openonion.oochat.ui.components.AgentInitialsAvatar
import ai.openonion.oochat.ui.components.AgentStatusChip
import ai.openonion.oochat.ui.components.AppCard
import ai.openonion.oochat.ui.navigation.DrawerAgentSection
import ai.openonion.oochat.ui.navigation.DrawerEntry
import ai.openonion.oochat.ui.navigation.NavDrawer
import ai.openonion.oochat.ui.theme.spacing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The navigation drawer as its design draws it (W16AAPPLE-114): the panel, a chat's three-dot
 * menu, the sheet that asks before a chat is deleted, and the five agent statuses.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class NavDrawerScreenshotTest {

    @get:Rule(order = 0)
    val localeRule = DeterministicLocaleRule()

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    private companion object {
        const val ADDRESS = "0x5773a1b2c3d4e5f60718293a4b5c6d7e8f900be5"
        const val CHAT = "What's the weather like in Sydney today?"
    }

    private fun capture(name: String, palette: Palette, interact: (ComposeContentTestRule.() -> Unit)? = null) =
        composeRule.captureThemed(name, palette, interact) {
            // The page behind an open drawer, dimmed as the drawer's scrim dims it.
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))) {
                NavDrawer(
                    agentSections = listOf(
                        DrawerAgentSection(
                            agentId = "agent-1",
                            agentAddress = ADDRESS,
                            name = "oo",
                            status = AgentStatus.Connected,
                            sessions = listOf(DrawerEntry("c1", CHAT))
                        )
                    ),
                    activeEntryId = "c1",
                    onNewChatForAgent = {},
                    onSelectAgentSession = { _, _ -> },
                    onDeleteSession = {},
                    onClearSession = {},
                    onRenameSession = { _, _ -> },
                    onOpenAgentList = {},
                    onOpenSettings = {},
                    onClose = {},
                    connectedAgentAddress = ADDRESS,
                    sessionTimes = mapOf("c1" to "4m ago")
                )
            }
        }

    private fun ComposeContentTestRule.openMenu() {
        onNodeWithContentDescription("More options for \"$CHAT\"").performClick()
    }

    @Test
    fun `drawer - light`() = capture("nav_drawer", Palette.Light)

    @Test
    fun `drawer - dark`() = capture("nav_drawer", Palette.Dark)

    @Test
    fun `a chat's menu - light`() = capture("nav_drawer_chat_menu", Palette.Light) { openMenu() }

    @Test
    fun `a chat's menu - dark`() = capture("nav_drawer_chat_menu", Palette.Dark) { openMenu() }

    @Test
    fun `delete a chat - light`() = capture("nav_drawer_delete_sheet", Palette.Light) {
        openMenu()
        onNodeWithText("Delete…").performClick()
    }

    @Test
    fun `delete a chat - dark`() = capture("nav_drawer_delete_sheet", Palette.Dark) {
        openMenu()
        onNodeWithText("Delete…").performClick()
    }

    /** Every status an agent can have. Connecting turns for ever, so it is left out, as elsewhere. */
    private fun captureStatuses(palette: Palette) = composeRule.captureThemed("agent_status_chips", palette) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(MaterialTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm2)
        ) {
            listOf(
                AgentStatus.Active to "Working on a task",
                AgentStatus.Connected to "Online and idle",
                AgentStatus.Error("no route") to "Can't connect",
                AgentStatus.Disabled to "Turned off by you"
            ).forEach { (status, meaning) ->
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(MaterialTheme.spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)
                        ) {
                            AgentInitialsAvatar(
                                name = "oo",
                                size = 40.dp,
                                shape = MaterialTheme.shapes.extraLarge,
                                maxInitials = 1,
                                textStyle = MaterialTheme.typography.titleMedium,
                                backgroundColor = if (status == AgentStatus.Disabled) {
                                    MaterialTheme.colorScheme.outline
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                contentColor = MaterialTheme.colorScheme.surface
                            )
                            Text(
                                "oo",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            AgentStatusChip(status)
                        }
                        Text(meaning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    @Test
    fun `agent statuses - light`() = captureStatuses(Palette.Light)

    @Test
    fun `agent statuses - dark`() = captureStatuses(Palette.Dark)
}
