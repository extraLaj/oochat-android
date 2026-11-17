package ai.openonion.oochat.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The design system's card: `surfaceContainer` with a 1dp `outlineVariant` border and 8dp corners
 * (docs/design-system.md). The chat's Work Room and approval cards both use it, so "the agent needs
 * you" reads as one thing (W16AAPPLE-125).
 *
 * [highlighted] is for a card that is waiting on the user: only its border turns to the primary
 * colour, so it stands out in a long transcript while the fill and corners stay the card's own.
 */
@Composable
internal fun AppCard(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, appCardBorderColor(highlighted, MaterialTheme.colorScheme)),
        content = content
    )
}

/** The border of an [AppCard]: the quiet outline, or the primary colour at 30% while it is [highlighted]. */
internal fun appCardBorderColor(highlighted: Boolean, colors: ColorScheme): Color =
    if (highlighted) colors.primary.copy(alpha = 0.30f) else colors.outlineVariant
