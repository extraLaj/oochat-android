package ai.openonion.oochat.ui.chat

import ai.openonion.oochat.domain.model.ExecOutcome
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal const val MAX_EXEC_RESULT_CHARS = 4_000

/** Displays direct EXEC commands and their matching EXEC_RESULT outcomes. */
@Composable
internal fun ExecResultList(
    items: List<ExecUiItem>,
    onDismiss: (String) -> Unit,
    toolLabels: Map<String, String> = emptyMap(),
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(items, key = { it.execId }) { item ->
            ExecResultCard(
                item = item,
                toolLabel = toolLabels[item.tool] ?: item.tool.toReadableToolLabel(),
                onDismiss = { onDismiss(item.execId) }
            )
        }
    }
}

@Composable
private fun ExecResultCard(
    item: ExecUiItem,
    toolLabel: String,
    onDismiss: () -> Unit
) {
    var detailsExpanded by rememberSaveable(item.execId) { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = toolLabel,
                    style = MaterialTheme.typography.titleMedium
                )
                TextButton(onClick = onDismiss) {
                    Text("Dismiss")
                }
            }

            when (val outcome = item.outcome) {
                null -> Text("Running…")
                is ExecOutcome.Success -> {
                    Text("Completed")
                    ExecDetails(
                        value = outcome.result,
                        expanded = detailsExpanded,
                        onToggle = { detailsExpanded = !detailsExpanded }
                    )
                }
                is ExecOutcome.Error -> {
                    Text("Could not complete this action")
                    ExecDetails(
                        value = outcome.message,
                        expanded = detailsExpanded,
                        onToggle = { detailsExpanded = !detailsExpanded },
                        collapsedLabel = "View reason"
                    )
                }
                is ExecOutcome.NotSent -> {
                    Text("This action was not sent")
                    ExecDetails(
                        value = outcome.message,
                        expanded = detailsExpanded,
                        onToggle = { detailsExpanded = !detailsExpanded },
                        collapsedLabel = "View reason"
                    )
                }
                is ExecOutcome.Unknown -> {
                    Text("We could not confirm the result")
                    ExecDetails(
                        value = outcome.message,
                        expanded = detailsExpanded,
                        onToggle = { detailsExpanded = !detailsExpanded },
                        collapsedLabel = "View details"
                    )
                }
                is ExecOutcome.Refused -> {
                    Text("This action was refused")
                    ExecDetails(
                        value = outcome.message,
                        expanded = detailsExpanded,
                        onToggle = { detailsExpanded = !detailsExpanded },
                        collapsedLabel = "View reason"
                    )
                }
            }
        }
    }
}

@Composable
private fun ExecDetails(
    value: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    collapsedLabel: String = "View result"
) {
    if (value.isBlank()) return

    TextButton(onClick = onToggle) {
        Text(if (expanded) "Hide details" else collapsedLabel)
    }
    if (expanded) {
        Text(
            text = previewExecOutput(value),
            maxLines = 12,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun previewExecOutput(value: String): String =
    if (value.length <= MAX_EXEC_RESULT_CHARS) {
        value
    } else {
        value.take(MAX_EXEC_RESULT_CHARS) + "\n…output truncated"
    }
