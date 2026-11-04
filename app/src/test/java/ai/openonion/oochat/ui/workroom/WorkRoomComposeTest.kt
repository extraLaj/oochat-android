package ai.openonion.oochat.ui.workroom

import ai.openonion.oochat.domain.model.ChatItem
import ai.openonion.oochat.domain.model.ProviderActivity
import ai.openonion.oochat.domain.model.ProviderActivityStatus
import ai.openonion.oochat.domain.model.ProviderInvocationStatus
import ai.openonion.oochat.ui.chat.components.ChatItemBubble
import ai.openonion.oochat.ui.theme.ConnectOnionTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** W16AAPPLE-33: a Claude Code or Codex run's card in the chat, and its Work Room. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WorkRoomComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val running = ChatItem.ProviderInvocation(
        id = "codex:call-1",
        parentToolCallId = "call-1",
        provider = "codex",
        taskTitle = "Add a login page",
        status = ProviderInvocationStatus.RUNNING,
        currentSummary = "Working in the selected workspace",
        activities = listOf(
            ProviderActivity("a", 1, "file_change", ProviderActivityStatus.COMPLETED, "Update workspace files", "Changed 2 files", listOf("Login.kt", "login.xml")),
            ProviderActivity("b", 2, "command", ProviderActivityStatus.RUNNING, "Run the requested tests", "Running the tests")
        )
    )

    @Test
    fun `the chat shows the run as its provider's Work Room and opens it by its id`() {
        var opened: String? = null
        composeRule.setContent {
            ConnectOnionTheme {
                ChatItemBubble(
                    item = running,
                    timestamp = null,
                    onRespond = {},
                    onApprove = { _, _, _, _ -> },
                    onOnboard = { _, _, _ -> },
                    onPlanReviewResponse = {},
                    onUlwResponse = { _, _, _ -> },
                    onOpenWorkRoom = { opened = it }
                )
            }
        }

        composeRule.onNodeWithText("Codex · Work Room").assertIsDisplayed()
        composeRule.onNodeWithText("Add a login page").assertIsDisplayed()
        composeRule.onNodeWithText("Working").assertIsDisplayed()
        composeRule.onNodeWithText("Working in the selected workspace").assertIsDisplayed()
        composeRule.onNodeWithText("2 steps").assertIsDisplayed()

        composeRule.onNodeWithText("Open Work Room").performClick()
        assertEquals("codex:call-1", opened)
    }

    @Test
    fun `the Work Room lists each step with its files`() {
        composeRule.setContent { ConnectOnionTheme { WorkRoomScreen(run = running, onNavigateBack = {}) } }

        composeRule.onNodeWithText("Codex Work Room").assertIsDisplayed()
        composeRule.onNodeWithText("Update workspace files").assertIsDisplayed()
        composeRule.onNodeWithText("Changed 2 files").assertIsDisplayed()
        composeRule.onNodeWithText("Login.kt, login.xml").assertIsDisplayed()
        composeRule.onNodeWithText("Run the requested tests").assertIsDisplayed()
    }

    @Test
    fun `a finished run says how it ended`() {
        val failed = running.copy(
            status = ProviderInvocationStatus.FAILED,
            errorSummary = "The provider reported an error",
            elapsedMs = 65_000L
        )
        composeRule.setContent { ConnectOnionTheme { WorkRoomScreen(run = failed, onNavigateBack = {}) } }

        composeRule.onNodeWithText("Failed").assertIsDisplayed()
        composeRule.onNodeWithText("The provider reported an error").assertIsDisplayed()
        composeRule.onNodeWithText("Took 1 min 5 s").assertIsDisplayed()
    }

    @Test
    fun `a run the chat no longer holds says so instead of a blank screen`() {
        composeRule.setContent { ConnectOnionTheme { WorkRoomScreen(run = null, onNavigateBack = {}) } }

        composeRule.onNodeWithText("This Work Room is no longer in the chat.").assertIsDisplayed()
    }
}
