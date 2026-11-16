package ai.openonion.oochat.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The widest a settings list or a form gets. On a tablet a row stretched across the whole window
 * leaves a label and its switch a hand's width apart; past this the content stays in the middle,
 * as the chat, Rem and a Work Room already do.
 */
internal val FORM_MAX_WIDTH = 640.dp

/**
 * Caps the content at [max] wide and centres it in the width it is given. Put it after
 * `verticalScroll`, so the scroll area keeps the whole width (a swipe in the margin still scrolls)
 * and only the content is narrowed.
 */
internal fun Modifier.formWidth(max: Dp = FORM_MAX_WIDTH): Modifier = layout { measurable, constraints ->
    val width = minOf(constraints.maxWidth, max.roundToPx())
    val placeable = measurable.measure(
        constraints.copy(minWidth = minOf(constraints.minWidth, width), maxWidth = width)
    )
    layout(constraints.maxWidth, placeable.height) {
        placeable.placeRelative((constraints.maxWidth - placeable.width) / 2, 0)
    }
}
