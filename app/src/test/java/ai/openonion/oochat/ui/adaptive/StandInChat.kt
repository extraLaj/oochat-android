package ai.openonion.oochat.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A stand-in chat with the tabs along its foot. The real chat screen draws the bar that
 * [ChatWithHome] hands it under its own message box (see [LocalChatBottomBar]), so a stand-in
 * that left it out would have no tabs while the chat is the one showing.
 */
@Composable
internal fun WithChatTabs(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier) {
        Box(Modifier.weight(1f)) { content() }
        LocalChatBottomBar.current?.invoke()
    }
}
