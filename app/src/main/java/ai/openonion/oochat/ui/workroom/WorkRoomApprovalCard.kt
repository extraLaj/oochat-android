package ai.openonion.oochat.ui.workroom

import ai.openonion.oochat.domain.model.ChatItem
import ai.openonion.oochat.domain.model.WorkRoomApproval
import ai.openonion.oochat.ui.components.AppCard
import ai.openonion.oochat.ui.theme.ButtonToken
import ai.openonion.oochat.ui.theme.spacing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

internal const val WORK_ROOM_DENY_MODE = "reject_soft"

/** [onDecide]'s scope must be "once" or "session": the Host reads any other value as a denial. */
@Composable
internal fun WorkRoomApprovalCard(
    item: ChatItem.ApprovalNeeded,
    approval: WorkRoomApproval,
    onDecide: (approved: Boolean, scope: String) -> Unit
) {
    val presentation = approval.presentation
    // The same card as the Work Room's above it (W16AAPPLE-125). Only its border says it is waiting
    // on you, so a request in a long chat still stands out from an answered one.
    AppCard(modifier = Modifier.fillMaxWidth(), highlighted = item.decision == null) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.md2),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)
        ) {
            Text(
                text = WorkRoomText.providerName(approval.provider),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = WorkRoomText.approvalTitle(approval),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            presentation?.let {
                Text(it.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(it.scope, style = MaterialTheme.typography.labelMedium)
                if (it.files.isNotEmpty()) {
                    Text(
                        text = it.files.joinToString(", "),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val decision = item.decision
            if (decision != null) {
                Text(
                    text = WorkRoomText.approvalOutcome(decision),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (decision.refused) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
                return@Column
            }

            // No "Waiting for your decision" line here: the run's status already says it.
            WorkRoomText.approvalDenyOnly(approval)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (approval.canAllowOnce) {
                Button(
                    onClick = { onDecide(true, "once") },
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().heightIn(min = ButtonToken.Compact)
                ) { Text("Allow once") }
            }
            if (approval.canAllowSession) {
                TextButton(
                    onClick = { onDecide(true, "session") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = ButtonToken.Compact)
                ) { Text("Allow for this session") }
            }
            OutlinedButton(
                onClick = { onDecide(false, "once") },
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().heightIn(min = ButtonToken.Compact)
            ) { Text("Deny") }
        }
    }
}
