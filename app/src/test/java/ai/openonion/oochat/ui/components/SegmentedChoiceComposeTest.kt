package ai.openonion.oochat.ui.components

import ai.openonion.oochat.ui.theme.ConnectOnionTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.isNotSelected
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [SegmentedChoice] replaced the hand-drawn Relay/Direct and Appearance pills
 * (W16AAPPLE-123). What those guaranteed has to survive the swap: exactly one
 * selected option, announced as a radio button, a tap that changes the choice,
 * and a 48dp touch target.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SegmentedChoiceComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val options = listOf(
        SegmentOption("a", "First"),
        SegmentOption("b", "Second"),
        SegmentOption("c", "Third")
    )

    @Test
    fun `only the selected option is selected`() {
        composeRule.setContent {
            ConnectOnionTheme { SegmentedChoice(options = options, selected = "b", onSelect = {}) }
        }

        composeRule.onNodeWithText("Second").assert(isSelected())
        composeRule.onNodeWithText("First").assert(isNotSelected())
        composeRule.onNodeWithText("Third").assert(isNotSelected())
    }

    @Test
    fun `every option is announced as a radio button`() {
        composeRule.setContent {
            ConnectOnionTheme { SegmentedChoice(options = options, selected = "a", onSelect = {}) }
        }

        listOf("First", "Second", "Third").forEach { label ->
            val role = composeRule.onNodeWithText(label).fetchSemanticsNode()
                .config[SemanticsProperties.Role]
            assertEquals(label, Role.RadioButton, role)
        }
    }

    @Test
    fun `tapping an option selects it`() {
        composeRule.setContent {
            var chosen by remember { mutableStateOf("a") }
            ConnectOnionTheme { SegmentedChoice(options = options, selected = chosen, onSelect = { chosen = it }) }
        }

        composeRule.onNodeWithText("Third").performClick()

        composeRule.onNodeWithText("Third").assert(isSelected())
        composeRule.onNodeWithText("First").assert(isNotSelected())
    }

    @Test
    fun `every option meets the 48dp touch target`() {
        composeRule.setContent {
            ConnectOnionTheme { SegmentedChoice(options = options, selected = "a", onSelect = {}) }
        }

        listOf("First", "Second", "Third").forEach { label ->
            composeRule.onNodeWithText(label).assertHeightIsAtLeast(48.dp)
        }
    }

    @Test
    fun `a long label wraps at the largest font size instead of being cut`() {
        val long = listOf(
            SegmentOption("relay", "Relay (recommended)"),
            SegmentOption("direct", "Direct Connection")
        )
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                ConnectOnionTheme {
                    Box(Modifier.width(360.dp)) { SegmentedChoice(options = long, selected = "direct", onSelect = {}) }
                }
            }
        }

        long.forEach { option ->
            val results = mutableListOf<TextLayoutResult>()
            val node = composeRule.onNodeWithText(option.label).fetchSemanticsNode()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
            assertFalse(option.label, results.single().didOverflowHeight || results.single().hasVisualOverflow)
        }
    }

    @Test
    fun `when one label wraps every segment matches the tallest`() {
        val labels = listOf("Relay Server", "Direct Connection")
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                ConnectOnionTheme {
                    // Scrolling, as the agent form and Settings are, so the height on offer is unbounded.
                    Column(Modifier.width(360.dp).verticalScroll(rememberScrollState())) {
                        SegmentedChoice(
                            options = listOf(SegmentOption(false, labels[0]), SegmentOption(true, labels[1])),
                            selected = true,
                            onSelect = {}
                        )
                    }
                }
            }
        }

        val (relay, direct) = labels.map { composeRule.onNodeWithText(it).fetchSemanticsNode().boundsInRoot }
        assertEquals("top", relay.top, direct.top)
        assertEquals("bottom", relay.bottom, direct.bottom)
    }
}
