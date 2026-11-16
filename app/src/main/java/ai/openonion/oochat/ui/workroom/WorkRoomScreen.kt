package ai.openonion.oochat.ui.workroom

import ai.openonion.oochat.domain.model.ChatItem
import ai.openonion.oochat.domain.model.ProviderActivity
import ai.openonion.oochat.domain.model.ProviderActivityStatus
import ai.openonion.oochat.domain.model.ProviderInvocationStatus
import ai.openonion.oochat.domain.model.ProviderMessage
import ai.openonion.oochat.domain.model.WorkRooms
import ai.openonion.oochat.ui.chat.ChatViewModel
import ai.openonion.oochat.ui.chat.components.WorkRoomHandover
import ai.openonion.oochat.ui.chat.components.WorkRoomStatus
import ai.openonion.oochat.ui.components.BackTopAppBar
import ai.openonion.oochat.ui.theme.spacing
import ai.openonion.oochat.ui.theme.statusColors
import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Binds [WorkRoomScreen] to the chat it belongs to. [chatViewModelStoreOwner]
 * is the chat destination's back-stack entry, as for the Control Center, so
 * the room follows the same live list the chat is folding frames into.
 */
@Composable
fun WorkRoomScreenWrapper(
    invocationId: String,
    chatViewModelStoreOwner: ViewModelStoreOwner?,
    onNavigateBack: () -> Unit
) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel: ChatViewModel = viewModel(
        viewModelStoreOwner = chatViewModelStoreOwner
            ?: checkNotNull(LocalViewModelStoreOwner.current) { "No ViewModelStoreOwner" },
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
    )
    val state by viewModel.uiState.collectAsState()
    val drafts by viewModel.workRoomDrafts.collectAsState()
    val permissionControls by viewModel.workRoomPermissionControls.collectAsState()
    val stops by viewModel.workRoomStopStates.collectAsState()
    // A resumed session is a new run in the same room (W16AAPPLE-38): the newest run, and the ones it resumed.
    val runs = WorkRooms.runsInRoom(state.chatItems, invocationId)
    WorkRoomScreen(
        run = runs.lastOrNull(),
        earlierRuns = runs.dropLast(1),
        onNavigateBack = onNavigateBack,
        draft = viewModel.workRoomDraft(invocationId, drafts),
        onDraftChange = { viewModel.editWorkRoomDraft(invocationId, it) },
        onSend = { viewModel.sendWorkRoomInput(invocationId) },
        scrollPosition = viewModel.workRoomScrollPosition(invocationId),
        onScrollPositionChanged = { viewModel.updateWorkRoomScrollPosition(invocationId, it) },
        approvals = state.chatItems.workRoomApprovals(invocationId),
        onApprovalDecision = { itemId, approved, scope ->
            viewModel.markGateResolved(itemId)
            viewModel.respondToApproval(itemId, approved, scope, if (approved) null else WORK_ROOM_DENY_MODE)
        },
        permissionUi = viewModel.workRoomPermissionControl(invocationId, permissionControls),
        onChoosePermission = { viewModel.chooseWorkRoomPermission(invocationId, it) },
        onConfirmPermission = { viewModel.confirmWorkRoomPermission(invocationId) },
        onCancelPermission = { viewModel.cancelWorkRoomPermission(invocationId) },
        stopUi = viewModel.workRoomStop(invocationId, stops),
        onStop = { viewModel.requestWorkRoomStop(invocationId) },
        onConfirmStop = { viewModel.confirmWorkRoomStop(invocationId) },
        onCancelStop = { viewModel.cancelWorkRoomStop(invocationId) }
    )
}

/** The approvals asked by any run in [invocationId]'s room. */
internal fun List<ChatItem>.workRoomApprovals(invocationId: String): List<ChatItem.ApprovalNeeded> {
    val runs = WorkRooms.runsInRoom(this, invocationId).mapTo(mutableSetOf()) { it.id } + invocationId
    return filterIsInstance<ChatItem.ApprovalNeeded>().filter { it.workRoom?.invocationId in runs }
}

/**
 * A Claude Code or Codex run's Work Room (W16AAPPLE-33), O Chat's Work Room on
 * a phone: the task and where it stands, the provider's own permission mode when
 * the Host offers one (W16AAPPLE-43), the latest picture of its workspace, what
 * was said to and by the provider (W16AAPPLE-34), then each step it took, as the Host
 * describes it, and a field to steer it (W16AAPPLE-35), and a Stop for this run only,
 * behind a 15-second confirmation (W16AAPPLE-37).
 * [run] is null once the chat no longer holds it.
 */
@Composable
fun WorkRoomScreen(
    run: ChatItem.ProviderInvocation?,
    onNavigateBack: () -> Unit,
    /** The runs this one resumed, oldest first (W16AAPPLE-38); shown above it, each as it ended. */
    earlierRuns: List<ChatItem.ProviderInvocation> = emptyList(),
    draft: WorkRoomDraft = WorkRoomDraft(),
    onDraftChange: (String) -> Unit = {},
    onSend: () -> Unit = {},
    scrollPosition: WorkRoomScrollPosition = WorkRoomScrollPosition(),
    onScrollPositionChanged: (WorkRoomScrollPosition) -> Unit = {},
    approvals: List<ChatItem.ApprovalNeeded> = emptyList(),
    onApprovalDecision: (itemId: String, approved: Boolean, scope: String) -> Unit = { _, _, _ -> },
    permissionUi: WorkRoomPermissionUi = WorkRoomPermissionUi(),
    onChoosePermission: (optionId: String) -> Unit = {},
    onConfirmPermission: () -> Unit = {},
    onCancelPermission: () -> Unit = {},
    stopUi: WorkRoomStopUi = WorkRoomStopUi(),
    onStop: () -> Unit = {},
    onConfirmStop: () -> Unit = {},
    onCancelStop: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            BackTopAppBar(
                title = run?.let { "${WorkRoomText.providerName(it)} Work Room" } ?: "Work Room",
                onNavigateBack = onNavigateBack
            )
        },
        bottomBar = {
            if (run != null) {
                Column {
                    WorkRoomApprovalDock(approvals.filter { it.decision == null }, onApprovalDecision)
                    WorkRoomComposer(run, draft, onDraftChange, onSend)
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            if (run == null) {
                Text(
                    text = "This Work Room is no longer in the chat.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(MaterialTheme.spacing.lg)
                )
                return@Box
            }
            WorkRoomTranscript(
                run,
                earlierRuns,
                scrollPosition,
                onScrollPositionChanged,
                approvals,
                stop = { WorkRoomStopControl(run, stopUi, onStop, onConfirmStop, onCancelStop) },
                permission = {
                    run.providerPermission?.let { permission ->
                        WorkRoomPermissionControl(
                            run = run,
                            permission = permission,
                            ui = permissionUi,
                            onChoose = onChoosePermission,
                            onConfirm = onConfirmPermission,
                            onCancel = onCancelPermission
                        )
                    }
                }
            )
        }
    }
}

/** Displays the room transcript and reports its scroll position. */
@Composable
private fun WorkRoomTranscript(
    run: ChatItem.ProviderInvocation,
    earlierRuns: List<ChatItem.ProviderInvocation>,
    scrollPosition: WorkRoomScrollPosition,
    onScrollPositionChanged: (WorkRoomScrollPosition) -> Unit,
    approvals: List<ChatItem.ApprovalNeeded>,
    stop: @Composable () -> Unit = {},
    permission: @Composable () -> Unit = {}
) {
    // Only the answered ones are here, as a record; one still waiting is in the dock above the field.
    val resolvedApprovals = approvals.filter { it.decision != null }
    // Only a picture of the run as it stands, as the Host's replay keeps: an older one would look current.
    val currentArtifact = run.artifact?.takeIf { it.stateRevision == run.stateRevision }
    val rows = transcriptRows(run, earlierRuns, resolvedApprovals)
    val itemKeys = buildList {
        add("header")
        if (!run.status.isTerminal) add("stop")
        if (run.providerPermission != null) add("permission")
        currentArtifact?.let { add("artifact") }
        rows.forEach { add(it.key) }
    }
    val itemCount = itemKeys.size
    val keyIndex = scrollPosition.firstVisibleItemKey?.let(itemKeys::indexOf)?.takeIf { it >= 0 }
    val restoredIndex = (keyIndex ?: scrollPosition.firstVisibleItemIndex)
        .coerceIn(0, (itemCount - 1).coerceAtLeast(0))
    val restoredOffset = if (keyIndex != null || restoredIndex == scrollPosition.firstVisibleItemIndex) {
        scrollPosition.firstVisibleItemScrollOffset
    } else {
        0
    }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = restoredIndex,
        initialFirstVisibleItemScrollOffset = restoredOffset
    )

    LaunchedEffect(listState) {
        snapshotFlow {
            // The key of the item the index and offset describe, not just the first one
            // laid out: one peeking into the top padding would anchor the room an item off.
            val firstVisible = listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.index == listState.firstVisibleItemIndex }
            WorkRoomScrollPosition(
                firstVisibleItemKey = firstVisible?.key as? String,
                firstVisibleItemIndex = listState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset
            )
        }
            .distinctUntilChanged()
            .collect(onScrollPositionChanged)
    }

    val providerName = WorkRoomText.providerName(run)
    LazyColumn(
        state = listState,
        // Capped for reading on a tablet, as Rem's pages are.
        modifier = Modifier
            .widthIn(max = WORK_ROOM_COLUMN_MAX_WIDTH)
            .fillMaxWidth()
            .testTag(WORK_ROOM_TRANSCRIPT_TAG),
        contentPadding = PaddingValues(MaterialTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)
    ) {
        item(key = "header") { RunHeader(run) }
        // Stop for this run only (W16AAPPLE-37), while there is something to stop.
        if (!run.status.isTerminal) item(key = "stop") { stop() }
        if (run.providerPermission != null) item(key = "permission") { permission() }
        currentArtifact?.let { artifact -> item(key = "artifact") { LatestView(artifact) } }
        items(rows, key = { it.key }) { row ->
            when (row) {
                is TranscriptRow.Heading -> Text(
                    text = row.text,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.semantics { heading() }
                )
                is TranscriptRow.Message -> ConversationMessage(row.message, providerName)
                is TranscriptRow.StepRow -> Step(row.step)
                is TranscriptRow.Resolved -> WorkRoomApprovalCard(row.approval, row.approval.workRoom ?: return@items) { _, _ -> }
                is TranscriptRow.Outcome -> Outcome(row.run)
                is TranscriptRow.Resumed -> Resumed(providerName)
            }
        }
    }
}

/** One item of a room's transcript below its header, keyed so a room keeps its place. */
private sealed interface TranscriptRow {
    val key: String

    data class Heading(override val key: String, val text: String) : TranscriptRow
    data class Message(override val key: String, val message: ProviderMessage) : TranscriptRow
    data class StepRow(override val key: String, val step: ProviderActivity) : TranscriptRow
    data class Resolved(override val key: String, val approval: ChatItem.ApprovalNeeded) : TranscriptRow
    data class Outcome(override val key: String, val run: ChatItem.ProviderInvocation) : TranscriptRow
    data class Resumed(override val key: String) : TranscriptRow
}

/**
 * What was said and done, run by run: each run the room resumed (W16AAPPLE-38), as it
 * ended, then where the session was resumed, then the newest run. A room of one run
 * reads as it always has. A message the Host echoes into the resumed run is shown once.
 */
private fun transcriptRows(
    run: ChatItem.ProviderInvocation,
    earlierRuns: List<ChatItem.ProviderInvocation>,
    resolvedApprovals: List<ChatItem.ApprovalNeeded>
): List<TranscriptRow> = buildList {
    val shown = mutableSetOf<String>()
    earlierRuns.forEach { earlier ->
        val prefix = "${earlier.id}:"
        val messages = earlier.messages.filter { shown.add(it.id) }
        if (messages.isNotEmpty()) {
            add(TranscriptRow.Heading("conversation-heading:$prefix", "Conversation"))
            messages.forEach { add(TranscriptRow.Message("message:$prefix${it.id}", it)) }
        }
        if (earlier.activities.isNotEmpty()) {
            add(TranscriptRow.Heading("steps-heading:$prefix", "Steps"))
            earlier.activities.forEach { add(TranscriptRow.StepRow("step:$prefix${it.id}", it)) }
        }
        add(TranscriptRow.Outcome("outcome:$prefix", earlier))
    }
    if (earlierRuns.isNotEmpty()) add(TranscriptRow.Resumed("resumed:${run.id}"))
    val messages = run.messages.filter { shown.add(it.id) }
    if (messages.isNotEmpty()) {
        add(TranscriptRow.Heading("conversation-heading", "Conversation"))
        messages.forEach { add(TranscriptRow.Message("message:${it.id}", it)) }
    }
    add(TranscriptRow.Heading("steps-heading", if (run.activities.isEmpty()) "No steps yet" else "Steps"))
    run.activities.forEach { add(TranscriptRow.StepRow("step:${it.id}", it)) }
    resolvedApprovals.forEach { add(TranscriptRow.Resolved("approval:${it.id}", it)) }
    if (run.status.isTerminal) add(TranscriptRow.Outcome("outcome", run))
}

/** How [run] ended, under a rule. */
@Composable
private fun Outcome(run: ChatItem.ProviderInvocation) {
    val outcome = run.outcome() ?: return
    Column {
        HorizontalDivider()
        Text(
            text = outcome,
            style = MaterialTheme.typography.bodyMedium,
            color = if (run.status == ProviderInvocationStatus.FAILED) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.padding(top = MaterialTheme.spacing.sm)
        )
    }
}

/** Where a message resumed the session (W16AAPPLE-38): the provider carried on with what it already knew. */
@Composable
private fun Resumed(providerName: String) {
    Column(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxs)
    ) {
        Text(
            text = WorkRoomText.SESSION_RESUMED,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = WorkRoomText.sessionResumedDetail(providerName),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.primary)
    }
}

internal const val WORK_ROOM_TRANSCRIPT_TAG = "work-room-transcript"

@Composable
private fun RunHeader(run: ChatItem.ProviderInvocation) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
        Text(
            text = WorkRoomText.title(run),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() }
        )
        WorkRoomStatus(run.status)
        run.control?.let { WorkRoomHandover(it, showDetail = true) }
        if (!run.status.isTerminal) {
            // The Host's own line on a working run; the steps below say what it is doing.
            run.currentSummary?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        WorkRoomText.elapsed(run.elapsedMs)?.let {
            // Only a finished run's time is final; a working one's moves on with each frame.
            Text(
                text = if (run.status.isTerminal) "Took $it" else "Running for $it",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Step(step: ProviderActivity) {
    Row(verticalAlignment = Alignment.Top) {
        Box(modifier = Modifier.padding(top = MaterialTheme.spacing.xxs, end = MaterialTheme.spacing.sm)) {
            when (step.status) {
                ProviderActivityStatus.RUNNING -> CircularProgressIndicator(
                    modifier = Modifier
                        .size(16.dp)
                        .semantics { contentDescription = "Running" },
                    strokeWidth = 2.dp
                )
                ProviderActivityStatus.COMPLETED -> Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Done",
                    tint = MaterialTheme.statusColors.success,
                    modifier = Modifier.size(16.dp)
                )
                ProviderActivityStatus.FAILED -> Icon(
                    Icons.Default.Error,
                    contentDescription = "Failed",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxs)) {
            Text(step.title, style = MaterialTheme.typography.bodyMedium)
            step.summary.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (step.files.isNotEmpty()) {
                Text(
                    text = step.files.joinToString(", "),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** How the run ended, once it has; never the summary it had while working. */
private fun ChatItem.ProviderInvocation.outcome(): String? =
    if (status.isTerminal) WorkRoomText.summary(this) else null
