package ai.openonion.oochat.ui.components

import ai.openonion.oochat.domain.model.ConnectionState
import ai.openonion.oochat.network.CleartextPolicy
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

/**
 * W16AAPPLE-12: the banner over a refused protocol version must agree with the
 * chat's message instead of calling it a failed connection, and still offer
 * the user's way back once the agent or the app has been updated.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ConnectionBannerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun show(state: ConnectionState, onEditAgent: () -> Unit = {}, onReconnect: () -> Unit = {}) {
        composeRule.setContent {
            ConnectOnionTheme {
                ConnectionBanner(connectionState = state, onReconnect = onReconnect, onEditAgent = onEditAgent)
            }
        }
    }

    @Test
    fun `a refused protocol version is named as a version problem, not a failed connection`() {
        show(ConnectionState.Error(message = "This agent uses a different version…", retryable = false))

        composeRule.onNodeWithText("Incompatible version").assertIsDisplayed()
        composeRule.onNodeWithText("Connection failed").assertDoesNotExist()
    }

    @Test
    fun `a refused protocol version still offers Reconnect for after an update`() {
        var reconnects = 0
        show(ConnectionState.Error(message = "This agent uses a different version…", retryable = false)) { reconnects++ }

        composeRule.onNodeWithText("Reconnect").performClick()

        assertEquals(1, reconnects)
    }

    private val unencryptedRefused = ConnectionState.Error(
        message = CleartextPolicy.REFUSED,
        retryable = false,
        kind = ConnectionState.Error.Kind.UNENCRYPTED_REFUSED
    )

    @Test
    fun `a refused unencrypted connection is named as one, not as a version problem`() {
        show(unencryptedRefused)

        composeRule.onNodeWithText("Unencrypted connection refused").assertIsDisplayed()
        composeRule.onNodeWithText("Incompatible version").assertDoesNotExist()
    }

    @Test
    fun `a refused unencrypted connection offers Edit agent, not a Reconnect that would be refused again`() {
        var reconnects = 0
        var edits = 0
        show(unencryptedRefused, onReconnect = { reconnects++ }, onEditAgent = { edits++ })

        composeRule.onNodeWithText("Reconnect").assertDoesNotExist()
        composeRule.onNodeWithText("Edit agent").performClick()

        assertEquals(1, edits)
        assertEquals(0, reconnects)
    }

    @Test
    fun `the banner goes by the error's kind, not its wording`() {
        show(ConnectionState.Error(message = CleartextPolicy.REFUSED, retryable = false))

        composeRule.onNodeWithText("Unencrypted connection refused").assertDoesNotExist()
    }

    @Test
    fun `an ordinary error keeps the Connection failed banner`() {
        show(ConnectionState.Error(message = "Connection refused"))

        composeRule.onNodeWithText("Connection failed").assertIsDisplayed()
        composeRule.onNodeWithText("Incompatible version").assertDoesNotExist()
    }
}
