package ai.openonion.oochat.ui.chat

import ai.openonion.oochat.domain.model.AgentSkill
import ai.openonion.oochat.domain.model.AgentTool
import ai.openonion.oochat.domain.model.ApprovalMode
import ai.openonion.oochat.domain.model.SessionModes
import ai.openonion.oochat.domain.model.SessionUsageTotals
import ai.openonion.oochat.domain.model.WorkRooms
import ai.openonion.oochat.ui.adaptive.LocalChatBottomBar
import ai.openonion.oochat.ui.chat.components.ApprovalNotificationOffer
import ai.openonion.oochat.ui.chat.components.ChatComposerDraft
import ai.openonion.oochat.ui.chat.components.ChatTopBar
import ai.openonion.oochat.ui.chat.components.FailedMessagesBar
import ai.openonion.oochat.ui.chat.components.InputBar
import ai.openonion.oochat.ui.chat.components.LowBalanceBar
import ai.openonion.oochat.ui.chat.components.PlanPanel
import ai.openonion.oochat.ui.chat.components.VoiceInputState
import ai.openonion.oochat.ui.chat.components.composerEnabled
import ai.openonion.oochat.ui.chat.components.failedUserMessages
import ai.openonion.oochat.ui.common.appViewModel
import ai.openonion.oochat.ui.components.AppSnackbarHost
import ai.openonion.oochat.ui.components.ConnectionBanner
import ai.openonion.oochat.ui.controlcenter.controlCenterSupported
import ai.openonion.oochat.ui.home.AgentHome
import ai.openonion.oochat.ui.home.relativeAge
import ai.openonion.oochat.ui.navigation.DrawerAgentSection
import ai.openonion.oochat.ui.navigation.NavDrawer
import ai.openonion.oochat.ui.navigation.NavDrawerWidth
import ai.openonion.oochat.util.SoundEffectPlayer
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Compose-native chat screen used by both [Routes.CHAT] (auto-connect from
 * saved config) and [Routes.CHAT_WITH_AGENT] (tap an agent in
 * AgentListScreen) so the two entry points share the same look, layout, and
 * card types (OnboardGateCard, Thinking bubble, Approval card, etc.).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    connectToAgentId: String = "",
    onboardPending: Boolean = false,
    // Set when arriving via a different agent's session row in the drawer;
    // once this ChatViewModel connects, switch straight to this persisted
    // session instead of the default ensureActiveSession() pick.
    sessionIdToRestore: String? = null,
    // True only for an explicit "start a new conversation" action — the
    // drawer's "+" on a *different* agent. Everything else, cold start
    // included, resumes the previous conversation via ensureActiveSession.
    startNewSessionOnConnect: Boolean = false,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToLogs: () -> Unit = {},
    onNavigateToRem: () -> Unit = {},
    onNavigateToAgentList: () -> Unit = {},
    onNavigateToAgentEdit: (agentId: String) -> Unit = {},
    onNavigateToControlCenter: () -> Unit = {},
    onNavigateToWorkRoom: (invocationId: String) -> Unit = {},
    // Home is already part of this layout, beside the chat or as a tab, so the top bar's button to
    // open it would only open what is there.
    homeInLayout: Boolean = false,
    // Counts the times something outside this screen (the agent's Dashboard, a tab beside this
    // one) has asked for the drawer of conversations. Each new count opens it; 0 is never asked.
    openDrawerRequest: Int = 0,
    // Drawer row tapped for an agent other than the one this ChatViewModel
    // is connected to — the caller (NavigationGraph) re-navigates to that
    // agent's own chat/{address} destination, tearing down this ChatViewModel
    // and creating a fresh one the normal way (see NavigationGraph.kt).
    // startNew mirrors startNewSessionOnConnect above, threaded through
    // navigation for the fresh ChatViewModel on the other side.
    onNavigateToAgentChat: (agentAddress: String, sessionId: String?, startNew: Boolean) -> Unit = { _, _, _ -> },
    viewModel: ChatViewModel = appViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val hasLiveConnection by viewModel.hasLiveConnection.collectAsState()
    val isAgentWorking by viewModel.isAgentWorking.collectAsState()
    val voiceInput by viewModel.voiceInput.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val isOffline by viewModel.isOffline.collectAsState()
    val awaitingOnboardCode by viewModel.awaitingOnboardCode.collectAsState()
    val agentProfile by viewModel.agentProfile.collectAsState()
    val approvalMode by viewModel.approvalMode.collectAsState()
    val modePending by viewModel.modePending.collectAsState()
    val sessionModes by viewModel.sessionModes.collectAsState()
    val sessionActivity by viewModel.sessionActivity.collectAsState()
    val chatComposerDrafts by viewModel.chatComposerDrafts.collectAsState()
    val availableTools = agentProfile?.let { profile ->
        profile.toolDefinitions.ifEmpty { profile.tools.map { AgentTool(it) } }
    }.orEmpty()

    val dashboardHtml by viewModel.dashboardHtml.collectAsState()
    val controlCenterApp by viewModel.controlCenterApp.collectAsState()
    // Lazy: asking loads the WebView, which only an agent with both kinds of Home page needs.
    val webViewHostsApps = remember { lazy { controlCenterSupported() } }
    val agentHome = AgentHome(dashboardHtml != null, controlCenterApp != null) { webViewHostsApps.value }
    val plan by viewModel.plan.collectAsState()
    val activeSessionId by viewModel.activeSessionId.collectAsState()
    val itemTimestamps by viewModel.itemTimestamps.collectAsState()
    val hasOlderMessages by viewModel.hasOlderMessages.collectAsState()
    val targetAgentAddress by viewModel.targetAgentAddress.collectAsState()
    val drawerAgents by viewModel.drawerAgents.collectAsState()
    // W16AAPPLE-44: the offer to notify about approvals, and the OS dialog it
    // goes through where Android requires one. A refusal ends the offer too:
    // asking again would only reach a dialog Android has stopped showing.
    val approvalNotificationOffer by viewModel.approvalNotificationOffer.collectAsState()
    val notificationPermissionRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.acceptApprovalNotificationOffer() else viewModel.dismissApprovalNotificationOffer()
    }
    // Title, cleared-state, usage totals and the snackbar's error are derived
    // in ChatViewModel, not here: computing any of them in this body reads
    // uiState at function scope, which is what made a single tool_call frame
    // re-execute the whole screen. See their declarations for the detail.
    val conversationTitle by viewModel.conversationTitle.collectAsState()
    val wasCleared by viewModel.wasCleared.collectAsState()
    val sessionUsage by viewModel.sessionUsage.collectAsState()
    val error by viewModel.error.collectAsState()
    val execItems by viewModel.execItems.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()

    /** For the banner's Edit agent, which looks the agent up before navigating. */
    val bannerScope = rememberCoroutineScope()
    // ModalNavigationDrawer composes its drawerContent whether or not the
    // drawer is open, so the ~800-line panel — every agent's session rows (a
    // plain Column, not a LazyColumn, so all of them are realized), the theme
    // switcher, eight Icons.Filled.* vector builders — was composed, measured
    // and laid out as part of the first chat frame, before the user could
    // possibly have opened it. Latched, not toggled: closing it again should
    // not throw away work the user has already paid for.
    var drawerEverOpened by rememberSaveable { mutableStateOf(false) }
    // The menu button sets this itself so the content is in place for the very
    // first slide-in; this covers any other route to open (a drag, a restored
    // open state) without the body having to observe drawerState.
    LaunchedEffect(drawerState) {
        snapshotFlow { drawerState.isOpen || drawerState.isAnimationRunning }.first { it }
        drawerEverOpened = true
    }
    // When each chat was last used, for the drawer's rows. Read as the drawer opens rather than
    // observed: every message moves a chat's time, and a drawer that followed it would rebuild
    // through each turn, open or not (see ChatViewModel.drawerAgents). The list of chats is read
    // inside the flow, not as a key, so a new chat refreshes the times without this body running.
    var drawerSessionTimes by remember { mutableStateOf(emptyMap<String, String>()) }
    LaunchedEffect(drawerState) {
        snapshotFlow { (drawerState.targetValue == DrawerValue.Open) to drawerAgents }.collect { (opening, _) ->
            if (opening) {
                val now = System.currentTimeMillis()
                drawerSessionTimes = viewModel.sessionLastUsed().mapValues { (_, usedAt) -> relativeAge(now, usedAt) }
            }
        }
    }
    LaunchedEffect(openDrawerRequest) {
        if (openDrawerRequest > 0) {
            drawerEverOpened = true
            drawerState.open()
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val renderMarkdown by viewModel.renderMarkdown.collectAsState()
    val hapticFeedbackEnabled by viewModel.hapticFeedbackEnabled.collectAsState()
    val soundEffectsEnabled by viewModel.soundEffectsEnabled.collectAsState()
    val developerToolsEnabled by viewModel.developerToolsEnabled.collectAsState()
    val userTools = availableTools.filter { it.isFormAvailable }
    val toolsForPanel = if (developerToolsEnabled) availableTools else userTools
    val toolLabels = remember(availableTools) {
        availableTools.associate { tool -> tool.name to tool.toUserFacingName() }
    }
    val haptics = LocalHapticFeedback.current

    // The user's font-size preference is applied app-wide by ConnectOnionTheme
    // (LocalDensity override there) — this screen no longer scales its own.

    // Fires only for genuinely-new agent replies (see ChatViewModel.
    // agentReplyArrived's own doc) — never on session_sync replay of
    // already-persisted history.
    LaunchedEffect(viewModel) {
        viewModel.agentReplyArrived.collect {
            if (hapticFeedbackEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            if (soundEffectsEnabled) SoundEffectPlayer.playReceived()
        }
    }

    // Single decision point for which agent to connect to (route target, or
    // saved-config default). Reused by Reconnect so it replays the same
    // decision rather than falling back to the default agent when
    // connectToAgentId is set — see connectToAgentByConfig's doc for the
    // race this avoids.
    val connectToCurrentTarget: () -> Unit = {
        if (connectToAgentId.isNotBlank()) {
            viewModel.connectToAgent(connectToAgentId)
        } else {
            viewModel.connectToAgentByConfig()
        }
    }
    // Applied synchronously here (not reactively off isConnected) so it
    // fires exactly once and strictly before the Connected event it affects —
    // see markStartNewSessionOnConnect's doc.
    LaunchedEffect(connectToAgentId) {
        if (startNewSessionOnConnect) {
            viewModel.markStartNewSessionOnConnect()
        }
        connectToCurrentTarget()
    }

    // LoadingScreen already determined this agent needs onboarding — skip
    // re-announcing a generic "Connecting to agent…" banner while this
    // screen's own (necessary) fresh connect resolves the same outcome.
    LaunchedEffect(onboardPending) {
        if (onboardPending) {
            viewModel.markOnboardPending()
        }
    }

    // Keyed on isConnected (not sessionIdToRestore alone) so this only fires
    // once ensureActiveSession() — driven by the same Connected transition,
    // inside ChatViewModel's own collector — has already resolved its
    // default active session; calling switchToSession() after that instead
    // of racing it is what makes this land on the *intended* session.
    LaunchedEffect(sessionIdToRestore, isConnected) {
        if (sessionIdToRestore != null && isConnected) {
            viewModel.switchToSession(sessionIdToRestore)
        }
    }

    val accountRefreshLifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(accountRefreshLifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onReturnedToForeground()
            }
        }
        accountRefreshLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { accountRefreshLifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Monitor error state and show a snackbar when one occurs. Long/
    // multi-line server-reported errors (e.g. "Insufficient ConnectOnion
    // Credits") no longer reach uiState.error at all — ChatViewModel routes
    // those into the chat list itself (attached to the in-flight Thinking
    // bubble, or a standalone item) so they read as part of the
    // conversation instead of a transient toast. What's left here is
    // genuinely short, transient blips ("Connection lost", "N images
    // failed to attach"), which a Snackbar fits fine.
    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it.message)
            viewModel.clearError()
        }
    }

    // Approximates Figma's `backdrop-filter: blur(20px)` on the drawer panel:
    // blurring the content *behind* it reads identically to blurring the
    // panel itself (the drawer covers this content while open). Modifier.blur
    // uses RenderEffect, which is API 31+ only — a graceful no-op (plain
    // translucency, same as before) on older devices, not a crash.
    // Focus follows the keyboard down. The back key already hides the IME —
    // measured on the Huawei/Sogou pair, the input method consumes the key
    // before the app window sees it, so no BackHandler could run there — but
    // the field it was serving stayed focused, leaving a live caret and the
    // pill's focused ring under a keyboard that is gone. Keyed on the inset,
    // not on back, so a hide gesture or the IME's own dismiss key counts too.
    val imeVisible = WindowInsets.isImeVisible
    val focusManager = LocalFocusManager.current
    val composerFocus = remember { FocusRequester() }
    LaunchedEffect(imeVisible) { if (!imeVisible) focusManager.clearFocus() }

    val contentBlur by animateDpAsState(
        targetValue = if (drawerState.isOpen) 20.dp else 0.dp,
        animationSpec = tween(200),
        label = "drawerContentBlur"
    )

    // The drawer's own panel, for both kinds of drawer below.
    val drawerPanel: @Composable (onClose: () -> Unit) -> Unit = { onClose ->
        ChatNavDrawerContent(
            agentSections = drawerAgents,
            activeSessionId = activeSessionId,
            targetAgentAddress = targetAgentAddress,
            onCloseDrawer = onClose,
            onStartNewSession = viewModel::startNewSession,
            onSwitchToSession = viewModel::switchToSession,
            onDeleteSession = viewModel::deleteSession,
            onClearSession = viewModel::clearSession,
            onRenameSession = viewModel::renameSession,
            sessionTimes = drawerSessionTimes,
            onNavigateToAgentList = onNavigateToAgentList,
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToAgentChat = onNavigateToAgentChat
        )
    }
    // The Chat and Dashboard tabs, when this chat is one of the two (W16AAPPLE-114). They sit
    // inside the drawer's content so the drawer and its scrim cover them.
    val bottomBar = LocalChatBottomBar.current
    // Everything but the drawer: the top bar, the transcript, the composer and the tabs.
    val screen: @Composable (permanentDrawer: Boolean) -> Unit = { permanentDrawer ->
        Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                // The bar takes the navigation bar's inset when it is there.
                .then(if (bottomBar != null) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier)
                // Edge-to-edge: the background above still paints the full
                // window (including behind the transparent bars), but the
                // content is inset here. Bottom takes safeDrawing, whose ime
                // component is what replaces the window resize that
                // adjustResize no longer performs once the decor stops
                // fitting system windows — it shrinks the weighted
                // MessageList and lifts InputBar instead of pushing ChatTopBar
                // off-screen. Top is deliberately left alone: ChatTopBar's
                // TopAppBar applies its own status-bar inset, so its surface
                // keeps painting behind the bar.
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(contentBlur)
            ) {
            // W16AAPPLE-113: until the first message the composer is a card in
            // the middle of the screen; after it, the usual bar along the bottom.
            val emptyChat = uiState.chatItems.isEmpty()
            val composer: @Composable (centred: Boolean) -> Unit = { centred ->
                ChatInput(
                    awaitingOnboardCode = awaitingOnboardCode,
                    isConnected = isConnected,
                    hasAgent = targetAgentAddress != null,
                    approvalMode = approvalMode,
                    modePending = modePending,
                    sessionModes = sessionModes,
                    usage = sessionUsage,
                    skills = agentProfile?.skills.orEmpty(),
                    onCycleApprovalMode = viewModel::cycleApprovalMode,
                    onSelectApprovalMode = viewModel::setApprovalMode,
                    isAgentWorking = isAgentWorking,
                    draft = chatComposerDrafts[activeSessionId] ?: ChatComposerDraft(),
                    draftKey = activeSessionId,
                    onDraftChange = { viewModel.updateChatComposerDraft(activeSessionId, it) },
                    onSend = { text, images, files ->
                        if (hapticFeedbackEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (soundEffectsEnabled) SoundEffectPlayer.playSent()
                        viewModel.sendMessage(text, images, files)
                    },
                    voiceInput = voiceInput,
                    onStartVoiceRecording = viewModel::startVoiceRecording,
                    onCancelVoiceRecording = viewModel::cancelVoiceRecording,
                    onFinishVoiceInput = viewModel::finishVoiceInput,
                    onVoiceTranscriptConsumed = viewModel::consumeVoiceTranscript,
                    onQueryVoiceAmplitude = viewModel::currentVoiceAmplitude,
                    onStop = viewModel::interrupt,
                    centred = centred,
                    focusRequester = composerFocus,
                    // Dimmed only when it cannot be used. Offline it can, so it is
                    // drawn at full strength; the banner already says offline.
                    modifier = Modifier.graphicsLayer {
                        alpha = if (composerEnabled(targetAgentAddress != null, awaitingOnboardCode, isConnected)) 1f else 0.45f
                    }
                )
            }
            // The first send swaps one composer for the other. Focus goes with
            // it, so the keyboard the user was typing on stays up for the reply.
            LaunchedEffect(emptyChat) {
                if (!emptyChat && imeVisible) composerFocus.requestFocus()
            }
            ChatTopBar(
                title = conversationTitle,
                connectionState = connectionState,
                isConnected = isConnected,
                onClearChat = { viewModel.clearChat() },
                onNavigateToLogs = onNavigateToLogs,
                onMenuClick = if (permanentDrawer) {
                    null
                } else {
                    {
                        drawerEverOpened = true
                        drawerScope.launch { drawerState.open() }
                    }
                },
                awaitingOnboardCode = awaitingOnboardCode,
                modelName = agentProfile?.model,
                sessionActivity = sessionActivity,
                // Only a Control Center the device cannot host has a screen of its own; any other
                // Home is in the layout.
                hasDashboard = !homeInLayout && agentHome.opensControlCenter,
                onOpenDashboard = onNavigateToControlCenter,
                onOpenRem = onNavigateToRem,
                agentName = drawerAgents.firstOrNull { it.agentAddress == targetAgentAddress }?.name,
                // W16AAPPLE-114: New chat from the conversation itself, not only from the drawer.
                onNewChat = if (targetAgentAddress != null) viewModel::startNewSession else null,
                newChatEnabled = !emptyChat
            )

            ConnectionBanner(
                connectionState = connectionState,
                onReconnect = connectToCurrentTarget,
                onStop = { viewModel.disconnect() },
                // The agent's own Edit screen, or the list when it was only ever set up in Setup.
                onEditAgent = {
                    bannerScope.launch {
                        viewModel.targetSavedAgentId()?.let(onNavigateToAgentEdit) ?: onNavigateToAgentList()
                    }
                },
                awaitingOnboardCode = awaitingOnboardCode,
                isOffline = isOffline
            )

            PlanPanel(entries = plan)

            if (emptyChat) {
                EmptyChat(
                    agentName = agentProfile?.name,
                    keyboardOpen = imeVisible,
                    onStarter = { starter ->
                        val draft = chatComposerDrafts[activeSessionId] ?: ChatComposerDraft()
                        viewModel.updateChatComposerDraft(activeSessionId, draft.copy(text = starter.prefill))
                        composerFocus.requestFocus()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) { composer(true) }
            } else {
                // One card per Work Room: a resumed session is a new run in the same room (W16AAPPLE-38).
                val shownItems = remember(uiState.chatItems) { WorkRooms.oneCardPerRoom(uiState.chatItems) }
                MessageList(
                    items = shownItems,
                    timestamps = itemTimestamps,
                    wasCleared = wasCleared,
                    onRespond = viewModel::respondWithOption,
                    onApprove = viewModel::respondToApproval,
                    onOnboard = viewModel::respondToOnboard,
                    onPlanReviewResponse = viewModel::respondToPlanReview,
                    onUlwResponse = viewModel::respondToUlwTurnsReached,
                    onGateResolved = viewModel::markGateResolved,
                    onRetry = viewModel::retryFailedTurn,
                    onResendMessage = viewModel::resendMessage,
                    renderMarkdown = renderMarkdown,
                    hasLiveConnection = hasLiveConnection,
                    conversationKey = activeSessionId,
                    scrollPosition = viewModel.messageListScrollPosition(activeSessionId),
                    onScrollPositionChanged = { position ->
                        viewModel.updateMessageListScrollPosition(activeSessionId, position)
                    },
                    hasOlderMessages = hasOlderMessages,
                    onLoadOlderMessages = viewModel::loadOlderMessages,
                    onOpenWorkRoom = onNavigateToWorkRoom,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }

            ExecResultList(
                items = execItems,
                onDismiss = viewModel::dismissExec,
                toolLabels = toolLabels
            )

            if (toolsForPanel.isNotEmpty()) {
                ExecToolPanel(
                    tools = toolsForPanel,
                    onExecute = viewModel::executeTool,
                    allowRawJson = developerToolsEnabled
                )
            }

            // Beside the composer for the same reason as LowBalanceBar below: a
            // failed bubble far up the transcript is one the user has to go and
            // find. The bubble keeps its own retry.
            val failedMessages = remember(uiState.chatItems) { uiState.chatItems.failedUserMessages() }
            FailedMessagesBar(
                failed = failedMessages,
                onRetry = viewModel::resendMessage
            )

            // With the composer, not in the transcript: the transcript scrolls
            // away and this is the one notice that has to still be there when
            // the reader looks back down to type.
            LowBalanceBar(
                balanceUsd = agentProfile?.balanceUsd,
                agentAddress = targetAgentAddress
            )

            if (approvalNotificationOffer) {
                ApprovalNotificationOffer(
                    agentName = agentProfile?.name,
                    onTurnOn = {
                        if (viewModel.needsNotificationPermission) {
                            notificationPermissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.acceptApprovalNotificationOffer()
                        }
                    },
                    onNotNow = viewModel::dismissApprovalNotificationOffer
                )
            }

            if (!emptyChat) composer(false)
            }
            AppSnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
        if (bottomBar != null) Box(modifier = Modifier.blur(contentBlur)) { bottomBar() }
        }
    }

    // On a window where the chat itself is at least [PermanentDrawerMinWidth] wide (a tablet in
    // landscape with no Home beside it), the drawer stays open beside the chat as M3's standard
    // drawer for that size does, instead of sliding over it (W16AAPPLE-127). A chat in a half-width
    // pane next to Home keeps the modal drawer, so the drawer never takes room the chat needs.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (maxWidth >= PermanentDrawerMinWidth) {
            PermanentNavigationDrawer(drawerContent = { drawerPanel {} }) { screen(true) }
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    if (drawerEverOpened) {
                        drawerPanel { drawerScope.launch { drawerState.close() } }
                    } else {
                        // Same footprint, none of the content: the drawer's open anchor
                        // is derived from the sheet's measured width, so an empty
                        // drawerContent would leave nothing to slide.
                        Spacer(Modifier.fillMaxHeight().width(NavDrawerWidth))
                    }
                }
            ) { screen(false) }
        }
    }
}

/**
 * The chat's own width from which its drawer stays open beside it rather than sliding over it:
 * M3's Expanded width class (W16AAPPLE-127). The drawer takes [NavDrawerWidth] of it, leaving the
 * chat at least 540dp.
 */
internal val PermanentDrawerMinWidth = 840.dp

// ── Navigation drawer ───────────────────────────────────────────

// Internal for SessionDrawerComposeTest.
@Composable
internal fun ChatNavDrawerContent(
    agentSections: List<DrawerAgentSection>,
    activeSessionId: String?,
    targetAgentAddress: String?,
    onCloseDrawer: () -> Unit,
    onStartNewSession: () -> Unit,
    onSwitchToSession: (sessionId: String) -> Unit,
    onDeleteSession: (sessionId: String) -> Unit,
    onRenameSession: (sessionId: String, newTitle: String) -> Unit,
    onNavigateToAgentList: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAgentChat: (agentAddress: String, sessionId: String?, startNew: Boolean) -> Unit,
    onClearSession: (sessionId: String) -> Unit = {},
    sessionTimes: Map<String, String> = emptyMap()
) {
    // Passed straight through: ChatViewModel already emits the drawer's own
    // shape, and re-deriving it here would mint a fresh list on every
    // recomposition, which is exactly what stopped NavDrawer from skipping.
    NavDrawer(
        agentSections = agentSections,
        activeEntryId = activeSessionId,
        onNewChatForAgent = { agentAddress ->
            onCloseDrawer()
            if (agentAddress == targetAgentAddress) {
                onStartNewSession()
            } else {
                onNavigateToAgentChat(agentAddress, null, true)
            }
        },
        onSelectAgentSession = { agentAddress, sessionId ->
            onCloseDrawer()
            if (agentAddress == targetAgentAddress) {
                onSwitchToSession(sessionId)
            } else {
                onNavigateToAgentChat(agentAddress, sessionId, false)
            }
        },
        onDeleteSession = onDeleteSession,
        onClearSession = onClearSession,
        onRenameSession = onRenameSession,
        onOpenAgentList = {
            onCloseDrawer()
            onNavigateToAgentList()
        },
        onOpenSettings = {
            onCloseDrawer()
            onNavigateToSettings()
        },
        onClose = onCloseDrawer,
        connectedAgentAddress = targetAgentAddress,
        sessionTimes = sessionTimes
    )
}

// ── Input bar ───────────────────────────────────────────────────

@Composable
private fun ChatInput(
    isConnected: Boolean,
    awaitingOnboardCode: Boolean,
    hasAgent: Boolean,
    isAgentWorking: Boolean,
    approvalMode: ApprovalMode,
    modePending: Boolean,
    sessionModes: SessionModes,
    usage: SessionUsageTotals,
    skills: List<AgentSkill>,
    onCycleApprovalMode: () -> Unit,
    onSelectApprovalMode: (ApprovalMode, Int?) -> Unit,
    onSend: (String, List<String>, List<String>) -> Unit,
    draft: ChatComposerDraft,
    draftKey: String?,
    onDraftChange: (ChatComposerDraft) -> Unit,
    voiceInput: VoiceInputState,
    onStartVoiceRecording: () -> Unit,
    onCancelVoiceRecording: () -> Unit,
    onFinishVoiceInput: () -> Unit,
    onVoiceTranscriptConsumed: () -> Unit,
    onQueryVoiceAmplitude: () -> Float = { 0f },
    onStop: () -> Unit,
    centred: Boolean,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    // Matched to the transcript's own cap: a narrow column of messages above a
    // composer running the full width of a tablet reads as an oversight. The
    // Box keeps the full width so the bar's background still spans the screen.
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        InputBar(
            isConnected = isConnected,
            awaitingOnboardCode = awaitingOnboardCode,
            hasAgent = hasAgent,
            approvalMode = approvalMode,
            modePending = modePending,
            sessionModes = sessionModes,
            usage = usage,
            skills = skills,
            onCycleApprovalMode = onCycleApprovalMode,
            onSelectApprovalMode = onSelectApprovalMode,
            isAgentWorking = isAgentWorking,
            draft = draft,
            draftKey = draftKey,
            onDraftChange = onDraftChange,
            onSend = onSend,
            voiceInput = voiceInput,
            onStartVoiceRecording = onStartVoiceRecording,
            onCancelVoiceRecording = onCancelVoiceRecording,
            onFinishVoiceInput = onFinishVoiceInput,
            onVoiceTranscriptConsumed = onVoiceTranscriptConsumed,
            onQueryVoiceAmplitude = onQueryVoiceAmplitude,
            onStop = onStop,
            centred = centred,
            focusRequester = focusRequester,
            // Centred, EmptyChat already sets the card's width.
            modifier = if (centred) Modifier else Modifier.widthIn(max = ChatContentMaxWidth)
        )
    }
}
