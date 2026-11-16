package ai.openonion.oochat.ui.screenshot

import ai.openonion.oochat.domain.model.AgentTool
import ai.openonion.oochat.domain.model.AgentToolParameter
import ai.openonion.oochat.ui.chat.ExecToolPanel
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Light and dark evidence for the structured, non-developer tool form. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExecToolPanelScreenshotTest {

    @get:Rule(order = 0)
    val localeRule = DeterministicLocaleRule()

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    private val tool = AgentTool(
        name = "search_files",
        description = "Find files by name or content.",
        metadataAvailable = true,
        parameters = listOf(
            AgentToolParameter(
                name = "query",
                type = "string",
                description = "Words to search for.",
                required = true
            ),
            AgentToolParameter(
                name = "result_limit",
                type = "integer",
                description = "Maximum number of files to return."
            ),
            AgentToolParameter(
                name = "scope",
                type = "string",
                description = "Choose where to search.",
                choices = listOf("workspace", "recent_files")
            ),
            AgentToolParameter(
                name = "include_archived",
                type = "boolean",
                description = "Include hidden files."
            )
        )
    )

    private fun capture(name: String, palette: Palette) =
        composeRule.captureThemed(name, palette, interact = {
            onNodeWithText("Show").performClick()
        }) {
            Surface(modifier = Modifier.fillMaxSize()) {
                ExecToolPanel(
                    tools = listOf(tool),
                    allowRawJson = false,
                    onExecute = { _, _ -> }
                )
            }
        }

    @Test
    fun `normal user tool form - light`() = capture("exec_tool_panel_normal_user", Palette.Light)

    @Test
    fun `normal user tool form - dark`() = capture("exec_tool_panel_normal_user", Palette.Dark)
}
