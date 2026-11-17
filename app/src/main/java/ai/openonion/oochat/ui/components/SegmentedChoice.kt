package ai.openonion.oochat.ui.components

import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** One segment of a [SegmentedChoice]: the value it stands for, its label, and an optional icon. */
internal data class SegmentOption<T>(val value: T, val label: String, val icon: ImageVector? = null)

/**
 * A pick-one choice as Material's own segmented button row.
 *
 * Replaces the hand-drawn pill pairs that Relay/Direct and Appearance each had.
 * The row gives every segment the radio-button role and the selected state
 * itself, so the screen reader says "selected" without any semantics written
 * here. Material's own segment is 40dp tall, so each is held to a 48dp touch target here.
 */
@Composable
internal fun <T> SegmentedChoice(
    options: List<SegmentOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        options.forEachIndexed { index, option ->
            val isSelected = option.value == selected
            SegmentedButton(
                selected = isSelected,
                onClick = { onSelect(option.value) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                // Every segment fills the row, so one wrapped label makes them all taller.
                modifier = Modifier.fillMaxHeight().heightIn(min = 48.dp),
                // The selected segment keeps the app's green tint, as the pills it
                // replaces did, so the current choice is obvious at a glance.
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    activeBorderColor = MaterialTheme.colorScheme.primary
                ),
                icon = {
                    // Selected shows Material's check mark; an unselected segment
                    // shows its own icon, if it has one, so the choice stays readable.
                    val icon = option.icon
                    SegmentedButtonDefaults.Icon(
                        active = isSelected,
                        inactiveContent = icon?.let {
                            {
                                Icon(
                                    imageVector = it,
                                    contentDescription = null,
                                    modifier = Modifier.size(SegmentedButtonDefaults.IconSize)
                                )
                            }
                        }
                    )
                },
                // No line limit: at large font sizes a label wraps, as the old pills did, rather than losing words.
                label = { Text(text = option.label) }
            )
        }
    }
}
