package ai.openonion.oochat.ui.home

import ai.openonion.oochat.domain.model.ApprovalMode
import ai.openonion.oochat.domain.model.ConnectionState
import ai.openonion.oochat.domain.model.SessionActivity
import org.junit.Assert.assertEquals
import org.junit.Test

/** The words the agent's Dashboard chooses: see [AgentDashboardScreen]. */
class AgentDashboardTextTest {

    private val minute = 60_000L
    private val connected = ConnectionState.Connected(address = "0xagent")

    @Test
    fun `every approval mode has a trust level, by how much runs unasked`() {
        val expected = mapOf(
            ApprovalMode.SAFE to "Careful",
            ApprovalMode.PLAN to "Careful",
            ApprovalMode.READ_ONLY to "Careful",
            ApprovalMode.ACCEPT_EDITS to "Balanced",
            ApprovalMode.AUTO to "Balanced",
            ApprovalMode.ULW to "Unattended",
            ApprovalMode.FULL_ACCESS to "Unattended"
        )
        ApprovalMode.entries.forEach { mode -> assertEquals(mode.name, expected[mode], mode.trustLabel) }
    }

    @Test
    fun `an age is given in its largest whole unit`() {
        val now = 10_000 * minute
        assertEquals("Just now", relativeAge(now, now - 59_000))
        assertEquals("45m ago", relativeAge(now, now - 45 * minute))
        assertEquals("1h ago", relativeAge(now, now - 119 * minute))
        assertEquals("23h ago", relativeAge(now, now - 23 * 60 * minute))
        assertEquals("3d ago", relativeAge(now, now - 3 * 24 * 60 * minute))
    }

    @Test
    fun `a time ahead of the clock, as after a clock change, reads as just now`() {
        assertEquals("Just now", relativeAge(now = 0, then = 5 * minute))
    }

    @Test
    fun `the open conversation says what it is doing, and any other how long ago it was used`() {
        val now = 100 * minute
        val open = DashboardChat("a", "Open", updatedAt = now - 5 * minute, active = true)
        val other = DashboardChat("b", "Other", updatedAt = now - 5 * minute)

        assertEquals("Running now", chatSubtitle(open, SessionActivity.RUNNING, now))
        assertEquals("Waiting for you", chatSubtitle(open, SessionActivity.AWAITING_USER, now))
        assertEquals("5m ago", chatSubtitle(open, SessionActivity.IDLE, now))
        assertEquals("5m ago", chatSubtitle(other, SessionActivity.RUNNING, now))
    }

    @Test
    fun `the hero says what the agent is doing once connected, and the connection until then`() {
        assertEquals("Idle · ready for a new task", heroStatus(connected, SessionActivity.IDLE, false))
        assertEquals("Working on a task", heroStatus(connected, SessionActivity.RUNNING, false))
        assertEquals("Waiting for you", heroStatus(connected, SessionActivity.AWAITING_USER, false))
        assertEquals("Connecting…", heroStatus(ConnectionState.Connecting, null, false))
        assertEquals("Not connected", heroStatus(ConnectionState.Disconnected, null, false))
        assertEquals("Waiting for your invite code", heroStatus(ConnectionState.Connecting, null, true))
    }
}
