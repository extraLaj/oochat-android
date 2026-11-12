package ai.openonion.oochat.ui.chat

import ai.openonion.oochat.domain.model.ChatItem
import ai.openonion.oochat.domain.model.ThinkingStatus
import ai.openonion.oochat.domain.model.ToolStatus
import ai.openonion.oochat.ui.chat.components.TURN_SUMMARY_TAG
import ai.openonion.oochat.ui.chat.components.WORKING_HEADER_TAG
import ai.openonion.oochat.ui.theme.ConnectOnionTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A turn's thoughts and tool calls show while it works and fold into one
 * summary row once it is done (W16AAPPLE-112). Driven through [MessageList],
 * which owns the open/closed choice, because that choice has to outlive the
 * block's row: each new step rebuilds it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TurnStepsComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val user = ChatItem.User(id = "u1", content = "build it")
    private val thought = ChatItem.Thinking(id = "t1", status = ThinkingStatus.DONE, content = "First idea", kind = "reflect")
    private fun bash(id: String, command: String, status: ToolStatus = ToolStatus.DONE) =
        ChatItem.ToolCall(id = id, name = "bash", args = mapOf("command" to command), status = status)
    private val running = ChatItem.Turn(id = "l1", thinking = ChatItem.Thinking(id = "l1", status = ThinkingStatus.RUNNING))
    private val answer = ChatItem.Turn(
        id = "r1",
        thinking = ChatItem.Thinking(id = "r1", status = ThinkingStatus.DONE),
        agent = ChatItem.Agent(id = "r1", content = "All done")
    )

    private fun show(initial: List<ChatItem>, timestamps: Map<String, Long> = emptyMap()): (List<ChatItem>) -> Unit {
        var items by mutableStateOf(initial)
        composeRule.setContent {
            ConnectOnionTheme {
                MessageList(
                    items = items,
                    timestamps = timestamps,
                    wasCleared = false,
                    onRespond = { _, _ -> },
                    onApprove = { _, _, _, _, _ -> },
                    onOnboard = { _, _, _ -> },
                    onPlanReviewResponse = { _, _ -> },
                    onUlwResponse = { _, _, _ -> },
                    renderMarkdown = false,
                    conversationKey = "conv-1",
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        composeRule.waitForIdle()
        return { items = it }
    }

    @Test
    fun `a running turn shows its progress`() {
        show(listOf(user, thought, bash("c1", "ls"), running))

        composeRule.onNodeWithTag(WORKING_HEADER_TAG)
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Agent is working, step 2")))
        composeRule.onNodeWithText("First idea").assertIsDisplayed()
        composeRule.onNodeWithText("ls").assertIsDisplayed()
        composeRule.onNodeWithTag(TURN_SUMMARY_TAG).assertDoesNotExist()
    }

    @Test
    fun `a turn waiting on the model before any step says it is working`() {
        show(listOf(user, running))

        composeRule.onNodeWithTag(WORKING_HEADER_TAG)
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Agent is working")))
    }

    @Test
    fun `a running turn folds its older steps behind a toggle`() {
        show(listOf(user, bash("c1", "one"), bash("c2", "two"), bash("c3", "three"), bash("c4", "four"), running))

        composeRule.onNodeWithText("one").assertDoesNotExist()
        composeRule.onNodeWithText("four").assertIsDisplayed()

        composeRule.onNodeWithText("1 earlier step").performClick()

        composeRule.onNodeWithText("one").assertIsDisplayed()
    }

    @Test
    fun `a finished turn collapses to a summary above its answer`() {
        val update = show(listOf(user, thought, bash("c1", "ls"), running))

        update(listOf(user, thought, bash("c1", "ls"), answer))
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(WORKING_HEADER_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("ls").assertDoesNotExist()
        composeRule.onNodeWithText("All done").assertIsDisplayed()
        composeRule.onNodeWithTag(TURN_SUMMARY_TAG)
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Worked, 2 steps")))
    }

    @Test
    fun `a turn loaded from history shows its duration and starts collapsed`() {
        show(
            listOf(user, bash("c1", "ls"), bash("c2", "pwd", ToolStatus.ERROR), answer),
            timestamps = mapOf("u1" to 1_000L, "r1" to 17_000L)
        )

        composeRule.onNodeWithText("pwd").assertDoesNotExist()
        // The row reads as one sentence, so its words are checked there rather
        // than as separate text nodes.
        composeRule.onNodeWithTag(TURN_SUMMARY_TAG).assertIsDisplayed().assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ContentDescription,
                listOf("Worked for 16 seconds, 2 steps, 1 failed")
            )
        )
    }

    @Test
    fun `tapping the summary opens the steps, and the choice survives new rows`() {
        val update = show(listOf(user, thought, bash("c1", "ls"), answer))

        composeRule.onNodeWithTag(TURN_SUMMARY_TAG).performClick()
        composeRule.onNodeWithText("ls").assertIsDisplayed()
        composeRule.onNodeWithTag(TURN_SUMMARY_TAG)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))

        update(listOf(user, thought, bash("c1", "ls"), answer, ChatItem.User(id = "u2", content = "thanks")))
        composeRule.waitForIdle()

        composeRule.onNodeWithText("ls").assertIsDisplayed()
    }

    @Test
    fun `the summary names its expand action for TalkBack`() {
        show(listOf(user, bash("c1", "ls"), answer))

        val node = composeRule.onNodeWithTag(TURN_SUMMARY_TAG).fetchSemanticsNode()
        assertEquals("Show steps", node.config[SemanticsActions.OnClick].label)
        assertEquals("Collapsed", node.config[SemanticsProperties.StateDescription])
    }

    @Test
    fun `a turn that ended without an answer keeps its steps on screen`() {
        show(listOf(user, bash("c1", "ls"), bash("c2", "rm -rf build", ToolStatus.ERROR)))

        composeRule.onNodeWithText("rm -rf build").assertIsDisplayed()
        composeRule.onNodeWithText("Failed").assertIsDisplayed()
    }

    @Test
    fun `an approval waiting on the user is never collapsed away`() {
        val approval = ChatItem.ApprovalNeeded(id = "a1", tool = "bash", arguments = mapOf("command" to "deploy"))
        show(listOf(user, bash("c1", "ls"), answer, approval))

        composeRule.onNodeWithText("deploy", substring = true).assertIsDisplayed()
    }
}
