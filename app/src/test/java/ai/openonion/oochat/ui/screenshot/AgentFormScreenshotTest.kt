package ai.openonion.oochat.ui.screenshot

import ai.openonion.oochat.domain.model.AgentProfile
import ai.openonion.oochat.ui.agent.AgentFormScreen
import ai.openonion.oochat.ui.agent.DiscoveryPhase
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Edit Agent and Add Agent, the two forms W16AAPPLE-124 made consistent: every field a fixed
 * label over a bordered field, no second heading under the top bar, no line under Save, no
 * character counter, and one Rescan on an empty relay. Add Agent shares its discovery panel
 * with Setup, so it stands for Setup's too.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AgentFormScreenshotTest {

    @get:Rule(order = 0)
    val localeRule = DeterministicLocaleRule()

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    private val agent = AgentProfile(
        id = "a1",
        address = "0x3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c",
        name = "Research assistant",
        description = "Reads papers and drafts summaries for the team.",
        serverUrl = "https://oo.openonion.ai",
        createdAt = 0L
    )

    private fun captureEdit(palette: Palette) = composeRule.captureThemed("agent_edit_form", palette) {
        AgentFormScreen(existingAgent = agent, onSave = {}, onCancel = {})
    }

    private fun captureAdd(palette: Palette) = composeRule.captureThemed("agent_add_form_empty_relay", palette) {
        AgentFormScreen(
            existingAgent = null,
            discoveryPhase = DiscoveryPhase.Done,
            discoveredAgents = emptyList(),
            onSave = {},
            onCancel = {}
        )
    }

    @Test
    fun `edit agent form - light`() = captureEdit(Palette.Light)

    @Test
    fun `edit agent form - dark`() = captureEdit(Palette.Dark)

    @Test
    fun `add agent form on an empty relay - light`() = captureAdd(Palette.Light)

    @Test
    fun `add agent form on an empty relay - dark`() = captureAdd(Palette.Dark)
}
