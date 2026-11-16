package ai.openonion.oochat.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * W16AAPPLE-38: a message sent to a finished Claude Code or Codex run resumes the
 * same session on the Host (`--resume`, `thread/resume`) as a new run, which the
 * Host puts in the same Work Room with `workroomId` (network/host/provider_workroom.py,
 * plugins/coding_agents.py `_provider_workroom_fields`). The room is shown as one,
 * with what the earlier runs said and did above the newest.
 */
class WorkRoomResumeTest {

    private fun reduce(current: List<ChatItem>, item: ChatItem) =
        ChatEventReducer.reduce(current, ChatEvent.ChatItemUpdated(item), ignoredIds = emptySet()).chatItems

    private fun message(id: String, role: ProviderMessageRole, text: String) = ProviderMessage(id, role, text)

    private fun step(id: String, sequence: Int, title: String) =
        ProviderActivity(id, sequence, "command", ProviderActivityStatus.COMPLETED, title, "")

    private val first = ChatItem.ProviderInvocation(
        id = "codex:call-1",
        parentToolCallId = "call-1",
        workroomId = "codex:call-1",
        provider = "codex",
        taskTitle = "Fix the login test",
        status = ProviderInvocationStatus.COMPLETED,
        resultSummary = "Fixed it",
        stateRevision = 3,
        messages = listOf(
            message("user:1", ProviderMessageRole.USER, "Fix the login test"),
            message("agent:1", ProviderMessageRole.ASSISTANT, "Fixed it")
        ),
        activities = listOf(step("cmd-1", 1, "Run the requested tests"))
    )

    private val resumed = ChatItem.ProviderInvocation(
        id = "codex:call-2",
        parentToolCallId = "call-2",
        workroomId = "codex:call-1",
        provider = "codex",
        taskTitle = "Now add a test for logout",
        status = ProviderInvocationStatus.RUNNING,
        stateRevision = 1,
        messages = listOf(
            // The Host echoes the message that resumed the run in the new run too.
            message("user:1", ProviderMessageRole.USER, "Fix the login test"),
            message("user:2", ProviderMessageRole.USER, "Now add a test for logout")
        ),
        activities = listOf(step("cmd-2", 1, "Update workspace files"))
    )

    private val other = ChatItem.ProviderInvocation(id = "claude_code:call-9", provider = "claude_code", stateRevision = 1)
    private val user = ChatItem.User(id = "u-1", content = "fix the login test")
    private val later = ChatItem.Agent(id = "a-1", content = "Codex is on it")

    @Test
    fun `a run's room is the Host's workroomId, or the run itself when it names none`() {
        assertEquals("codex:call-1", WorkRooms.roomOf(resumed))
        assertEquals("claude_code:call-9", WorkRooms.roomOf(other))
        assertEquals("claude_code:call-9", WorkRooms.roomOf(other.copy(workroomId = "")))
    }

    @Test
    fun `either run's id finds the room, and its newest run is the resumed one`() {
        val items = listOf(user, first, later, other, resumed)

        assertEquals(listOf("codex:call-1", "codex:call-2"), WorkRooms.runsInRoom(items, "codex:call-1").map { it.id })
        assertEquals(listOf("codex:call-1", "codex:call-2"), WorkRooms.runsInRoom(items, "codex:call-2").map { it.id })
        assertEquals("codex:call-2", WorkRooms.latestRun(items, "codex:call-1")?.id)
        assertEquals("claude_code:call-9", WorkRooms.latestRun(items, "claude_code:call-9")?.id)
        assertNull(WorkRooms.latestRun(items, "codex:gone"))
    }

    @Test
    fun `the room shows the newest run's state with the earlier conversation and steps above its own`() {
        val room = requireNotNull(WorkRooms.room(listOf(first, later, resumed), "codex:call-1"))

        assertEquals("codex:call-2", room.id)
        assertEquals(ProviderInvocationStatus.RUNNING, room.status)
        assertEquals("Now add a test for logout", room.taskTitle)
        assertEquals(listOf("user:1", "agent:1", "user:2"), room.messages.map { it.id })
        assertEquals(listOf("cmd-1", "cmd-2"), room.activities.map { it.id })
    }

    @Test
    fun `a resumed run's step with an id the earlier run used is kept`() {
        val sameId = resumed.copy(activities = listOf(step("cmd-1", 1, "Update workspace files")))

        val room = requireNotNull(WorkRooms.room(listOf(first, sameId), "codex:call-1"))

        assertEquals(listOf("Run the requested tests", "Update workspace files"), room.activities.map { it.title })
    }

    @Test
    fun `a room of one run is that run`() {
        assertSame(other, WorkRooms.room(listOf(first, other), "claude_code:call-9"))
    }

    @Test
    fun `the chat shows one card for the room, where it started, opening the same room`() {
        val shown = WorkRooms.oneCardPerRoom(listOf(user, first, later, other, resumed))

        assertEquals(listOf("u-1", "codex:call-1", "a-1", "claude_code:call-9"), shown.map { it.id })
        val card = shown[1] as ChatItem.ProviderInvocation
        assertEquals(ProviderInvocationStatus.RUNNING, card.status)
        assertEquals("call-1", card.parentToolCallId)
        assertEquals(WorkRooms.room(shown, card.id)?.id, card.id)
    }

    @Test
    fun `a chat with no resumed run is shown as it is`() {
        val items = listOf(user, first, other)

        assertSame(items, WorkRooms.oneCardPerRoom(items))
    }

    @Test
    fun `a resumed run's message that arrives before the run itself still joins the room`() {
        val items = reduce(
            listOf(first),
            ChatItem.ProviderMessageUpdate(
                invocationId = "codex:call-2",
                provider = "codex",
                message = message("user:2", ProviderMessageRole.USER, "Now add a test for logout"),
                workroomId = "codex:call-1"
            )
        )

        assertEquals(listOf("codex:call-1", "codex:call-2"), WorkRooms.runsInRoom(items, "codex:call-1").map { it.id })
        assertEquals(1, WorkRooms.oneCardPerRoom(items).size)
    }

    @Test
    fun `the run frame that follows keeps the room its message gave it`() {
        val started = reduce(
            listOf(first),
            ChatItem.ProviderMessageUpdate(
                invocationId = "codex:call-2",
                provider = "codex",
                message = message("user:2", ProviderMessageRole.USER, "Now add a test for logout"),
                workroomId = "codex:call-1"
            )
        )

        val items = reduce(started, resumed.copy(workroomId = null, messages = emptyList(), activities = emptyList()))

        assertEquals("codex:call-1", (items.last() as ChatItem.ProviderInvocation).workroomId)
    }
}
