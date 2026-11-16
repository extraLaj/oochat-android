package ai.openonion.oochat.ui.screenshot

import ai.openonion.oochat.domain.model.AgentTool
import ai.openonion.oochat.domain.model.AgentToolParameter
import ai.openonion.oochat.domain.model.ExecOutcome
import ai.openonion.oochat.ui.chat.ExecResultList
import ai.openonion.oochat.ui.chat.ExecToolPanel
import ai.openonion.oochat.ui.chat.ExecUiItem
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Five light-mode states that explain the normal-user EXEC journey. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExecToolJourneyScreenshotTest {

    @get:Rule(order = 0)
    val localeRule = DeterministicLocaleRule()

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    private val searchTool = AgentTool(
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

    private val readTool = AgentTool(
        name = "read_file",
        description = "Read a file from the workspace.",
        metadataAvailable = true,
        parameters = listOf(
            AgentToolParameter(
                name = "path",
                type = "string",
                required = true
            )
        )
    )

    private fun capture(
        name: String,
        interact: (ComposeContentTestRule.() -> Unit)? = null,
        content: @Composable () -> Unit
    ) = composeRule.captureThemed(name, Palette.Light, interact, content)

    @Test
    fun `01 collapsed actions`() = capture("exec_tool_journey_01_collapsed") {
        Surface(modifier = Modifier.fillMaxSize()) {
            ExecToolPanel(
                tools = listOf(searchTool, readTool),
                allowRawJson = false,
                onExecute = { _, _ -> }
            )
        }
    }

    @Test
    fun `02 action list`() = capture(
        name = "exec_tool_journey_02_actions",
        interact = {
            onNodeWithText("Show").performClick()
        }
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            ExecToolPanel(
                tools = listOf(searchTool, readTool),
                allowRawJson = false,
                onExecute = { _, _ -> }
            )
        }
    }

    @Test
    fun `03 form with more options`() = capture(
        name = "exec_tool_journey_03_form_options",
        interact = {
            onNodeWithText("Show").performClick()
            onNodeWithText("Search Files").performClick()
            onNode(hasSetTextAction()).performTextInput("quarterly expenses")
            onNodeWithText("More options").performClick()
        }
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            ExecToolPanel(
                tools = listOf(searchTool, readTool),
                allowRawJson = false,
                onExecute = { _, _ -> }
            )
        }
    }

    @Test
    fun `04 choice menu`() = capture(
        name = "exec_tool_journey_04_choice_menu",
        interact = {
            onNodeWithText("Show").performClick()
            onNodeWithText("Search Files").performClick()
            onNodeWithText("More options").performClick()
            onNodeWithText("Choose Scope").performClick()
        }
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            ExecToolPanel(
                tools = listOf(searchTool, readTool),
                allowRawJson = false,
                onExecute = { _, _ -> }
            )
        }
    }

    @Test
    fun `05 completed result`() = capture(
        name = "exec_tool_journey_05_result",
        interact = {
            onNodeWithText("View result").performClick()
        }
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            ExecResultList(
                items = listOf(
                    ExecUiItem(
                        execId = "exec-1",
                        tool = "search_files",
                        outcome = ExecOutcome.Success(
                            result = "3 files found\nquarterly-expenses.md\nopen-invoices.csv\narchived-reports.txt",
                            durationMs = 842.0
                        )
                    )
                ),
                onDismiss = {},
                toolLabels = mapOf("search_files" to "Search Files"),
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
