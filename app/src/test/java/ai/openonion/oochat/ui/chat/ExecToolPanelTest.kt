package ai.openonion.oochat.ui.chat

import ai.openonion.oochat.domain.model.AgentTool
import ai.openonion.oochat.domain.model.AgentToolParameter
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExecToolPanelTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `execute sends selected tool and arguments`() {
        var receivedTool = ""
        var receivedArgs = ""

        composeRule.setContent {
            ExecToolPanel(
                tools = listOf(AgentTool("read_file"), AgentTool("delete_file")),
                onExecute = { tool, argsJson ->
                    receivedTool = tool
                    receivedArgs = argsJson
                }
            )
        }

        composeRule.onNodeWithText("Show").performClick()
        composeRule.onNodeWithTag(EXEC_TOOL_SELECTOR_TAG).performClick()
        composeRule.onNodeWithTag("$EXEC_TOOL_SELECTOR_TAG:read_file").performClick()
        composeRule.onNodeWithTag(EXEC_TOOL_SELECTOR_TAG).performClick()
        composeRule.onNodeWithTag("$EXEC_TOOL_SELECTOR_TAG:delete_file").performClick()

        val fields = composeRule.onAllNodes(hasSetTextAction())
        fields[0].performTextReplacement("""{"path":"README.md"}""")

        composeRule.onNodeWithTag(EXEC_TOOL_SUBMIT_TAG).performClick()

        assertEquals("delete_file", receivedTool)
        assertEquals("""{"path":"README.md"}""", receivedArgs)
    }

    @Test
    fun `panel starts collapsed`() {
        composeRule.setContent {
            ExecToolPanel(
                tools = listOf(AgentTool("read_file")),
                onExecute = { _, _ -> }
            )
        }

        composeRule.onNodeWithText("Show").assertExists()
        composeRule.onNodeWithText("Arguments JSON").assertDoesNotExist()
    }

    @Test
    fun `a consumed open request can open the panel again`() {
        composeRule.setContent {
            var openRequested by remember { mutableStateOf(false) }
            Column {
                Button(onClick = { openRequested = true }) {
                    Text("Open actions")
                }
                ExecToolPanel(
                    tools = listOf(AgentTool("read_file")),
                    openRequested = openRequested,
                    onOpened = { openRequested = false },
                    onExecute = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithText("Open actions").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Hide").assertExists()
        composeRule.onNodeWithText("Open actions").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Hide").assertExists()
        composeRule.onNodeWithText("Hide").performClick()
        composeRule.onNodeWithText("Show").assertExists()

        composeRule.onNodeWithText("Open actions").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Hide").assertExists()
    }

    @Test
    fun `show reveals available tools and arguments fields`() {
        composeRule.setContent {
            ExecToolPanel(
                tools = listOf(AgentTool("read_file"), AgentTool("delete_file")),
                onExecute = { _, _ -> }
            )
        }

        composeRule.onNodeWithText("Show").performClick()

        composeRule.onNodeWithTag(EXEC_TOOL_SELECTOR_TAG).assertExists()
        composeRule.onNodeWithText("Arguments JSON").assertExists()
        composeRule.onNodeWithText("Enter a JSON object, for example: {\"command\":\"pwd\"}").assertExists()
    }

    @Test
    fun `invalid arguments cannot be executed`() {
        composeRule.setContent {
            ExecToolPanel(
                tools = listOf(AgentTool("shell")),
                onExecute = { _, _ -> error("invalid JSON should not be sent") }
            )
        }

        composeRule.onNodeWithText("Show").performClick()
        composeRule.onAllNodes(hasSetTextAction())[0].performTextReplacement("{invalid")

        composeRule.onNodeWithText("Arguments must be valid JSON").assertExists()
        composeRule.onNodeWithTag(EXEC_TOOL_SUBMIT_TAG).assertIsNotEnabled()

        composeRule.onAllNodes(hasSetTextAction())[0].performTextReplacement("{}")
        composeRule.onNodeWithTag(EXEC_TOOL_SUBMIT_TAG).assertIsEnabled()
    }

    @Test
    fun `structured metadata renders fields and builds typed arguments`() {
        var receivedTool = ""
        var receivedArgs = ""
        composeRule.setContent {
            ExecToolPanel(
                tools = listOf(
                    AgentTool(
                        name = "read_file",
                        description = "Read a file from the agent.",
                        parameters = listOf(
                            AgentToolParameter(
                                name = "file_path",
                                type = "string",
                                description = "Path to the file.",
                                required = true
                            ),
                            AgentToolParameter(
                                name = "line_limit",
                                type = "integer",
                                description = "Maximum lines to read."
                            )
                        )
                    )
                ),
                onExecute = { tool, argsJson ->
                    receivedTool = tool
                    receivedArgs = argsJson
                }
            )
        }

        composeRule.onNodeWithText("Show").performClick()
        composeRule.onNodeWithTag(EXEC_TOOL_SELECTOR_TAG).assertExists()
        composeRule.onNodeWithText("Read a file from the agent.").assertExists()
        composeRule.onNodeWithText("File Path (required)").assertExists()
        composeRule.onNodeWithText("More options").assertExists()
        composeRule.onNodeWithTag(EXEC_TOOL_SUBMIT_TAG).assertIsEnabled()

        val fields = composeRule.onAllNodes(hasSetTextAction())
        fields[0].performTextReplacement("README.md")
        composeRule.onNodeWithText("Path to the file.", substring = true).assertExists()
        composeRule.onNodeWithText("More options").performClick()
        composeRule.onNodeWithText("Maximum lines to read.", substring = true).assertExists()
        composeRule.onNodeWithText("Line Limit").assertExists()
        val fieldsWithOptional = composeRule.onAllNodes(hasSetTextAction())
        fieldsWithOptional[1].performTextReplacement("20")

        composeRule.onNodeWithTag(EXEC_TOOL_SUBMIT_TAG).assertIsEnabled()
        composeRule.onNodeWithTag(EXEC_TOOL_SUBMIT_TAG).performClick()

        assertEquals("read_file", receivedTool)
        assertEquals("{\"file_path\":\"README.md\",\"line_limit\":20}", receivedArgs)
    }

    @Test
    fun `host defaults are included even when optional fields stay collapsed`() {
        var receivedArgs = ""
        composeRule.setContent {
            ExecToolPanel(
                tools = listOf(
                    AgentTool(
                        name = "search",
                        parameters = listOf(
                            AgentToolParameter(
                                name = "limit",
                                type = "integer",
                                defaultValue = "10"
                            )
                        )
                    )
                ),
                onExecute = { _, argsJson -> receivedArgs = argsJson }
            )
        }

        composeRule.onNodeWithText("Show").performClick()
        composeRule.onNodeWithTag(EXEC_TOOL_SUBMIT_TAG).performClick()

        assertEquals("{\"limit\":10}", receivedArgs)
    }

    @Test
    fun `an untouched optional switch leaves the Host default unchanged`() {
        var receivedArgs = ""
        composeRule.setContent {
            ExecToolPanel(
                tools = listOf(
                    AgentTool(
                        name = "search",
                        parameters = listOf(
                            AgentToolParameter("include_archived", type = "boolean")
                        )
                    )
                ),
                allowRawJson = false,
                onExecute = { _, argsJson -> receivedArgs = argsJson }
            )
        }

        composeRule.onNodeWithText("Show").performClick()
        composeRule.onNodeWithTag(EXEC_TOOL_SUBMIT_TAG).performClick()

        assertEquals("{}", receivedArgs)
    }

    @Test
    fun `required and invalid field errors are shown beside the field`() {
        composeRule.setContent {
            ExecToolPanel(
                tools = listOf(
                    AgentTool(
                        name = "search",
                        parameters = listOf(
                            AgentToolParameter("query", type = "string", required = true),
                            AgentToolParameter("limit", type = "integer")
                        )
                    )
                ),
                onExecute = { _, _ -> error("invalid form should not be sent") }
            )
        }

        composeRule.onNodeWithText("Show").performClick()
        composeRule.onNodeWithTag(EXEC_TOOL_SUBMIT_TAG).performClick()
        composeRule.onNodeWithText("This field is required").assertExists()

        val fields = composeRule.onAllNodes(hasSetTextAction())
        fields[0].performTextReplacement("query")
        composeRule.onNodeWithText("More options").performClick()
        val fieldsWithOptional = composeRule.onAllNodes(hasSetTextAction())
        fieldsWithOptional[1].performTextReplacement("not-a-number")

        composeRule.onNodeWithText("Enter a whole number").assertExists()
        composeRule.onNodeWithTag(EXEC_TOOL_SUBMIT_TAG).assertIsEnabled()
    }

    @Test
    fun `boolean and choice fields use their available options`() {
        var receivedArgs = ""
        composeRule.setContent {
            ExecToolPanel(
                tools = listOf(
                    AgentTool(
                        name = "configure",
                        parameters = listOf(
                            AgentToolParameter("enabled", type = "boolean", required = true),
                            AgentToolParameter(
                                "mode",
                                type = "choice",
                                choices = listOf("fast_mode", "safe_mode"),
                                required = true
                            )
                        )
                    )
                ),
                onExecute = { _, argsJson -> receivedArgs = argsJson }
            )
        }

        composeRule.onNodeWithText("Show").performClick()
        composeRule.onNodeWithText("Enabled (required)").assertExists()
        composeRule.onNode(isToggleable() and hasContentDescription("Enabled (required)")).performClick()
        composeRule.onNodeWithText("Choose Mode (required)").performClick()
        composeRule.onNodeWithText("Safe Mode").performClick()
        composeRule.onNodeWithTag(EXEC_TOOL_SUBMIT_TAG).performClick()

        assertEquals("{\"enabled\":true,\"mode\":\"safe_mode\"}", receivedArgs)
    }

    @Test
    fun `an untouched boolean switch sends its visible off state`() {
        var receivedArgs = ""
        composeRule.setContent {
            ExecToolPanel(
                tools = listOf(
                    AgentTool(
                        name = "configure",
                        parameters = listOf(
                            AgentToolParameter("enabled", type = "boolean", required = true)
                        )
                    )
                ),
                allowRawJson = false,
                onExecute = { _, argsJson -> receivedArgs = argsJson }
            )
        }

        composeRule.onNodeWithText("Show").performClick()
        composeRule.onNode(isToggleable() and hasContentDescription("Enabled (required)")).assertExists()
        composeRule.onNodeWithTag(EXEC_TOOL_SUBMIT_TAG).performClick()

        assertEquals("{\"enabled\":false}", receivedArgs)
    }

    @Test
    fun `structured tools can switch to the developer JSON editor`() {
        composeRule.setContent {
            ExecToolPanel(
                tools = listOf(
                    AgentTool(
                        name = "read_file",
                        parameters = listOf(AgentToolParameter("path", type = "string"))
                    )
                ),
                onExecute = { _, _ -> }
            )
        }

        composeRule.onNodeWithText("Show").performClick()
        composeRule.onNodeWithText("Use JSON").performClick()
        composeRule.onNodeWithText("Arguments JSON").assertExists()
        composeRule.onNodeWithText("Use fields").assertExists()
    }
    @Test
    fun `normal users see fields without the developer JSON mode`() {
        composeRule.setContent {
            ExecToolPanel(
                tools = listOf(
                    AgentTool(
                        name = "read_file",
                        parameters = listOf(AgentToolParameter("path", type = "string"))
                    )
                ),
                allowRawJson = false,
                onExecute = { _, _ -> }
            )
        }

        composeRule.onNodeWithText("Show").performClick()
        composeRule.onNodeWithText("More options").performClick()
        composeRule.onNodeWithText("Path").assertExists()
        composeRule.onNodeWithText("Arguments JSON").assertDoesNotExist()
        composeRule.onNodeWithText("Use JSON").assertDoesNotExist()
    }

    @Test
    fun `a normal-user action list stays hidden until shown and can be hidden again`() {
        composeRule.setContent {
            ExecToolPanel(
                tools = listOf(
                    AgentTool(
                        name = "search_files",
                        parameters = listOf(AgentToolParameter("query", type = "string"))
                    ),
                    AgentTool(
                        name = "read_file",
                        parameters = listOf(AgentToolParameter("path", type = "string"))
                    )
                ),
                allowRawJson = false,
                onExecute = { _, _ -> }
            )
        }

        composeRule.onNodeWithText("Search Files").assertDoesNotExist()
        composeRule.onNodeWithText("Show").performClick()
        composeRule.onNodeWithText("Search Files").assertExists()
        composeRule.onNodeWithText("Hide").performClick()

        composeRule.onNodeWithText("Search Files").assertDoesNotExist()
        composeRule.onNodeWithText("Show").assertExists()
    }

    @Test
    fun `typed form values survive state restoration`() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            ExecToolPanel(
                tools = listOf(
                    AgentTool(
                        name = "read_file",
                        parameters = listOf(AgentToolParameter("path", type = "string", required = true))
                    )
                ),
                allowRawJson = false,
                onExecute = { _, _ -> }
            )
        }

        composeRule.onNodeWithText("Show").performClick()
        composeRule.onAllNodes(hasSetTextAction())[0].performTextReplacement("README.md")

        restoration.emulateSavedInstanceStateRestore()

        composeRule.onAllNodes(hasSetTextAction())[0]
            .assertTextEquals("Path (required)", "README.md")
    }

    @Test
    fun `submitting an invalid hidden optional field opens its options`() {
        composeRule.setContent {
            ExecToolPanel(
                tools = listOf(
                    AgentTool(
                        name = "search_files",
                        parameters = listOf(
                            AgentToolParameter("query", type = "string", required = true),
                            AgentToolParameter("limit", type = "integer")
                        )
                    )
                ),
                allowRawJson = false,
                onExecute = { _, _ -> error("invalid form should not execute") }
            )
        }

        composeRule.onNodeWithText("Show").performClick()
        composeRule.onAllNodes(hasSetTextAction())[0].performTextReplacement("notes")
        composeRule.onNodeWithText("More options").performClick()
        composeRule.onAllNodes(hasSetTextAction())[1].performTextReplacement("not-a-number")
        composeRule.onNodeWithText("Fewer options").performClick()
        composeRule.onNodeWithTag(EXEC_TOOL_SUBMIT_TAG).performClick()

        composeRule.onNodeWithText("Fewer options").assertExists()
        composeRule.onNodeWithText("Enter a whole number").assertExists()
    }

    @Test
    fun `incomplete metadata starts in developer JSON mode`() {
        composeRule.setContent {
            ExecToolPanel(
                tools = listOf(
                    AgentTool(
                        name = "read_file",
                        parameters = listOf(AgentToolParameter("path", type = "string")),
                        metadataComplete = false
                    )
                ),
                onExecute = { _, _ -> }
            )
        }

        composeRule.onNodeWithText("Show").performClick()

        composeRule.onNodeWithText("Arguments JSON").assertExists()
        composeRule.onNodeWithText("Use fields").assertDoesNotExist()
    }

    @Test
    fun `technical identifiers are converted to readable labels`() {
        assertEquals("API Key", "APIKey".toReadableToolLabel())
        assertEquals("Read URL Value", "readURLValue".toReadableToolLabel())
        assertEquals("Search Records", "search_records".toReadableToolLabel())
    }

    @Test
    fun `legacy tools stay hidden from normal users`() {
        composeRule.setContent {
            ExecToolPanel(
                tools = listOf(AgentTool("legacy_tool")),
                allowRawJson = false,
                onExecute = { _, _ -> error("metadata-less tool should not execute") }
            )
        }

        composeRule.onNodeWithText("Actions").assertDoesNotExist()
    }

    @Test
    fun `unsupported tool fields stay hidden from normal users`() {
        composeRule.setContent {
            ExecToolPanel(
                tools = listOf(
                    AgentTool(
                        name = "manage_records",
                        parameters = listOf(AgentToolParameter("filter", type = "object"))
                    )
                ),
                allowRawJson = false,
                onExecute = { _, _ -> error("incomplete metadata should not execute") }
            )
        }

        composeRule.onNodeWithText("Actions").assertDoesNotExist()
    }

    @Test
    fun `structured no argument actions are directly executable`() {
        var sent = false
        composeRule.setContent {
            ExecToolPanel(
                tools = listOf(
                    AgentTool(
                        name = "refresh_index",
                        displayName = "Refresh index",
                        actionLabel = "Refresh",
                        metadataAvailable = true
                    )
                ),
                allowRawJson = false,
                onExecute = { tool, args ->
                    sent = tool == "refresh_index" && args == "{}"
                }
            )
        }

        composeRule.onNodeWithText("Show").performClick()
        composeRule.onNodeWithText("Nothing else is needed for this action.").assertExists()
        composeRule.onNodeWithTag(EXEC_TOOL_SUBMIT_TAG).performClick()
        assertTrue(sent)
    }

    @Test
    fun `normal users choose an action before seeing its form`() {
        composeRule.setContent {
            ExecToolPanel(
                tools = listOf(
                    AgentTool(
                        name = "search_records",
                        displayName = "Search records",
                        actionLabel = "Search",
                        parameters = listOf(
                            AgentToolParameter("query", type = "string", required = true)
                        )
                    ),
                    AgentTool(
                        name = "create_record",
                        displayName = "Create record",
                        actionLabel = "Create",
                        parameters = listOf(
                            AgentToolParameter("name", type = "string", required = true)
                        )
                    )
                ),
                allowRawJson = false,
                onExecute = { _, _ -> }
            )
        }

        composeRule.onNodeWithText("Show").assertExists()
        composeRule.onNodeWithText("Choose an action").assertDoesNotExist()
        composeRule.onNodeWithText("Show").performClick()
        composeRule.onNodeWithText("Choose an action").assertExists()
        composeRule.onNodeWithText("Search records").performClick()
        composeRule.onNodeWithText("Query (required)").assertExists()
        composeRule.onNodeWithText("Arguments JSON").assertDoesNotExist()
        composeRule.onNodeWithText("Search").assertExists()
    }

    @Test
    fun `destructive actions ask for confirmation before sending`() {
        var sent = false
        composeRule.setContent {
            ExecToolPanel(
                tools = listOf(
                    AgentTool(
                        name = "delete_file",
                        displayName = "Delete file",
                        actionLabel = "Delete file",
                        requiresConfirmation = true,
                        parameters = listOf(
                            AgentToolParameter("path", type = "string", required = true)
                        )
                    )
                ),
                allowRawJson = false,
                onExecute = { _, _ -> sent = true }
            )
        }

        composeRule.onNodeWithText("Show").performClick()
        composeRule.onAllNodes(hasSetTextAction())[0].performTextReplacement("README.md")
        composeRule.onNodeWithTag(EXEC_TOOL_SUBMIT_TAG).performClick()

        composeRule.onNodeWithText("This action may change data. Do you want to continue?").assertExists()
        assertFalse(sent)

        composeRule.onNodeWithText("Confirm Delete file").performClick()
        assertTrue(sent)
    }

    @Test
    fun `destructive actions stay guarded in the developer JSON editor`() {
        var sent = false
        composeRule.setContent {
            ExecToolPanel(
                tools = listOf(
                    AgentTool(
                        name = "delete_file",
                        displayName = "Delete file",
                        actionLabel = "Delete file",
                        requiresConfirmation = true,
                        parameters = listOf(AgentToolParameter("path", type = "string", required = true))
                    )
                ),
                onExecute = { _, _ -> sent = true }
            )
        }

        composeRule.onNodeWithText("Show").performClick()
        composeRule.onNodeWithText("Use JSON").performClick()
        composeRule.onAllNodes(hasSetTextAction())[0].performTextReplacement("{\"path\":\"README.md\"}")
        composeRule.onNodeWithTag(EXEC_TOOL_SUBMIT_TAG).performClick()

        composeRule.onNodeWithText("This action may change data. Do you want to continue?").assertExists()
        assertFalse(sent)
        composeRule.onNodeWithText("Confirm Delete file").performClick()
        assertTrue(sent)
    }
}
