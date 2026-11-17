package ai.openonion.oochat.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Lists drawn edge to edge (W16AAPPLE-127). A Scaffold's content padding includes the navigation
 * bar, and padding a list by all of it stopped the list at the bar: its last row sat cut off above
 * a blank strip, with nothing ever drawn behind the bar. Instead the list takes the top and sides
 * as padding, so it runs to the bottom of the window behind the bar, and the bottom as content
 * padding, so the last row can still scroll clear of it.
 */

/** This padding without its bottom: for the list itself, so it reaches the bottom of the window. */
@Composable
internal fun PaddingValues.exceptBottom(): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(direction),
        top = calculateTopPadding(),
        end = calculateEndPadding(direction),
        bottom = 0.dp
    )
}

/** Just this padding's bottom, for a scrolling column that pads its own content. */
internal val PaddingValues.bottom: Dp get() = calculateBottomPadding()
