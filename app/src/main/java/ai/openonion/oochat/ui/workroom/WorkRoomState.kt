package ai.openonion.oochat.ui.workroom

/**
 * The portion of a Work Room's UI state that must survive leaving and
 * reopening its navigation destination.
 *
 * The run itself belongs to [ai.openonion.oochat.ui.chat.ChatViewModel], and
 * the composer draft already does too. The list viewport is different: a
 * [androidx.compose.foundation.lazy.LazyListState] belongs to the composable
 * instance, but the Work Room destination is popped when the user goes back
 * to chat. Keep only the serialisable coordinates here, not the Compose
 * object, so the state can outlive that destination safely.
 */
data class WorkRoomScrollPosition(
    val firstVisibleItemKey: String? = null,
    val firstVisibleItemIndex: Int = 0,
    val firstVisibleItemScrollOffset: Int = 0
) {
    /** Coordinates accepted by LazyListState; malformed/restored values cannot be negative. */
    fun normalised(): WorkRoomScrollPosition = copy(
        firstVisibleItemIndex = firstVisibleItemIndex.coerceAtLeast(0),
        firstVisibleItemScrollOffset = firstVisibleItemScrollOffset.coerceAtLeast(0)
    )
}
