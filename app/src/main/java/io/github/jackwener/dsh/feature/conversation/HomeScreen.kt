package io.github.jackwener.dsh.feature.conversation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import io.github.jackwener.dsh.core.designsystem.LocalReducedMotion
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jackwener.dsh.R
import io.github.jackwener.dsh.core.data.Settings
import io.github.jackwener.dsh.core.designsystem.Dsh
import io.github.jackwener.dsh.core.designsystem.DshType
import io.github.jackwener.dsh.core.designsystem.IconBtn
import io.github.jackwener.dsh.core.designsystem.Segmented
import io.github.jackwener.dsh.core.designsystem.Tag
import io.github.jackwener.dsh.core.designsystem.Tone
import io.github.jackwener.dsh.core.session.Conversation
import io.github.jackwener.dsh.core.session.SessionLive
import io.github.jackwener.dsh.feature.trajectory.TrajectoryView
import kotlinx.coroutines.launch

/**
 * The single home screen: the conversation, with the dsh sidebar as a modal drawer and a
 * 对话 / 轨迹 toggle for the same session.
 */
@Composable
fun HomeScreen(vm: HomeViewModel, onSettings: (String?) -> Unit, onAutomation: () -> Unit) {
    val c = Dsh.colors
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val status by vm.rowStatus.collectAsStateWithLifecycle()
    val currentId by vm.currentId.collectAsStateWithLifecycle()
    // Transient layers close before Back leaves home (BackHandlerAllowlistTest): the open drawer.
    BackHandler(drawer.isOpen) { scope.launch { drawer.close() } }
    // Back in front with a conversation open (e.g. from the launcher after a background run): it is seen now.
    LifecycleResumeEffect(currentId) { vm.markSeen(); onPauseOrDispose {} }
    // Leaving the app: the floating ball / quick panel continues this conversation (#83); a new empty one is nothing to continue.
    val context = androidx.compose.ui.platform.LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        io.github.jackwener.dsh.core.session.PanelSession.leftApp(context, vm.id.takeIf { !vm.conversation.value.isEmpty })
    }
    ModalNavigationDrawer(
        drawerState = drawer,
        scrimColor = c.scrim,
        drawerContent = {
            ModalDrawerSheet(Modifier.width(300.dp), drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
                drawerContainerColor = c.sidebar, drawerTonalElevation = 0.dp, windowInsets = androidx.compose.foundation.layout.WindowInsets(0)) {
                SessionDrawer(
                    sessions, status, currentId,
                    onNew = { vm.newSession(null); scope.launch { drawer.close() } },
                    onOpen = { vm.open(it); scope.launch { drawer.close() } },
                    onAutomation = { scope.launch { drawer.close() }; onAutomation() },
                    onSettings = { section -> scope.launch { drawer.close() }; onSettings(section) },
                    onClose = { scope.launch { drawer.close() } },
                    onRename = vm::rename, onPin = vm::pin, onMute = vm::mute, onArchive = vm::archive, onDelete = vm::delete,
                    workspace = {
                        val ws by vm.workspace.collectAsStateWithLifecycle()
                        val hosts by vm.hosts.collectAsStateWithLifecycle()
                        val states by vm.remoteStates.collectAsStateWithLifecycle()
                        WorkspaceSwitcher(ws, hosts, states, onSelect = { vm.setWorkspace(it) },
                            onManage = { scope.launch { drawer.close() }; onSettings("Remote") })
                    },
                )
            }
        },
    ) {
        ConversationPane(vm, onMenu = { scope.launch { drawer.open() } }, onSettings = onSettings, onAutomation = onAutomation)
    }
}

@Composable
private fun ConversationPane(vm: HomeViewModel, onMenu: () -> Unit, onSettings: (String?) -> Unit, onAutomation: () -> Unit) {
    val c = Dsh.colors
    val live by vm.live.collectAsStateWithLifecycle()
    val conv by vm.conversation.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val view by vm.view.collectAsStateWithLifecycle()
    val prefill by vm.prefill.collectAsStateWithLifecycle()
    val lineage by vm.lineage.collectAsStateWithLifecycle()
    val composer = rememberComposerState(live.sessionId)
    // 编辑 / 重试 / 从这里分叉 (#2): each makes a new conversation (dsh's fork), shown right away.
    val actions = remember(vm, composer) {
        MessageActions(
            onEdit = { u -> u.messageId?.let { composer.edit(EditTarget(it, u.attachments), u.text) } },
            onRetry = { u -> u.messageId?.let { vm.resend(it, io.github.jackwener.dsh.core.session.ForkKind.Retry, u.text, emptyList(), u.attachments) } },
            onFork = { turns, t -> vm.fork(io.github.jackwener.dsh.core.session.Forks.nextMessageId(turns, t)) },
        )
    }
    LaunchedEffect(prefill, live.sessionId) {
        if (live.sessionId == vm.id) prefill?.let { composer.text = it; vm.prefill.value = null }
    }
    val empty = conv.isEmpty && !live.running
    // …and 轨迹, a view of the same session (not a page): Back returns to 对话.
    BackHandler(view == SessionView.Trajectory) { vm.view.value = SessionView.Chat }
    // While the run waits on the user (approval / question / permission / takeover card) nothing is
    // happening, so stop the "working" animations: the UI settles to idle, which also lets
    // accessibility services and UI automation read the card's buttons.
    val waitingOnUser = live.approval != null || live.question != null || live.permission != null || live.takeover != null
    CompositionLocalProvider(LocalReducedMotion provides (LocalReducedMotion.current || waitingOnUser)) {
    Column(Modifier.fillMaxSize().background(c.bgLayer1).statusBarsPadding()) {
        // Miuix SmallTopAppBar (the column already carries the status-bar inset, so the bar
        // adds no window padding of its own; its surface matches the pane background).
        top.yukonga.miuix.kmp.basic.SmallTopAppBar(
            title = if (empty) "" else live.title.ifBlank { conv.turns.firstOrNull()?.user?.text?.lineSequence()?.firstOrNull() ?: stringResource(R.string.new_session) },
            color = c.bgLayer1,
            defaultWindowInsetsPadding = false,
            navigationIcon = {
                IconBtn(R.drawable.dsw_panel_left_outline, onMenu, size = 40.dp, iconSize = 18.dp, tint = c.labelPrimary, contentDescription = stringResource(R.string.open_sidebar))
            },
            actions = {
                // Remote mode (#51): which computer this conversation runs on, and its link.
                val remote by vm.remote.collectAsStateWithLifecycle()
                remote?.let { RemoteBadge(it, onDisconnect = { vm.disconnectRemote() }, onHostSettings = { onSettings("Remote") }) }
                // 对话 / 轨迹: one icon toggle instead of a segmented control, so the title keeps its room at 360 dp.
                if (!empty) {
                    val traj = view == SessionView.Trajectory
                    IconBtn(if (traj) R.drawable.dsw_flat_list_outline else R.drawable.dsw_inspect_outline,
                        { vm.view.value = if (traj) SessionView.Chat else SessionView.Trajectory }, size = 40.dp, iconSize = 18.dp,
                        tint = if (traj) c.labelPrimary else c.labelSecondary,
                        contentDescription = stringResource(if (traj) R.string.view_chat else R.string.view_trajectory))
                }
                // Nothing to leave on the empty home, so no 新会话 there.
                if (!empty) IconBtn(R.drawable.dsw_new_chat_outline, { vm.newSession(null) }, size = 40.dp, iconSize = 18.dp, tint = c.labelPrimary, contentDescription = stringResource(R.string.new_session))
            },
        )
        val agentDisplay by vm.agentDisplay.collectAsStateWithLifecycle()
        agentDisplay?.takeIf { live.running }?.let { AgentPreview(vm, it) }
        // Each conversation's list position and tail-following outlive 轨迹 and switching away (#76).
        val streams = rememberSaveableStateHolder()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AnimatedContent(Triple(view, empty, live.sessionId), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "pane") { (v, isEmpty, sid) ->
                when {
                    v == SessionView.Trajectory -> TrajectoryView(vm)
                    isEmpty -> EmptyHero(vm, composer, settings, live, conv, onAutomation, onSettings)
                    // One list state per session, so positions never leak across sessions.
                    else -> streams.SaveableStateProvider("chat/$sid") {
                        ChatStream(conv, live, settings.workDetails, rememberLazyListState(), onInspect = vm::inspect, onRetry = vm::retry, onContinue = { vm.continueTask() }, onOpenSettings = { onSettings(it) }, modifier = Modifier.fillMaxSize(),
                            // A computer's stock dsh has no fork (android/session/fork, #51): no 编辑 / 重试 / 分叉 there.
                            actions = actions.takeIf { live.hostId == null }, lineage = lineage, onOpenSession = vm::open)
                    }
                }
            }
        }
        if (!empty && view == SessionView.Chat) BottomDock(vm, composer, settings, live, conv, onAutomation, onSettings)
        else if (view == SessionView.Trajectory) {
            // Trajectory has no composer: keep the run stoppable from here.
            if (live.running) Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(8.dp), contentAlignment = Alignment.Center) {
                io.github.jackwener.dsh.core.designsystem.DButton(stringResource(R.string.stop_generating), { vm.stop() },
                    variant = io.github.jackwener.dsh.core.designsystem.BtnVariant.Outline, icon = R.drawable.dsw_stop_fill)
            } else Spacer(Modifier.navigationBarsPadding())
        }
    }
    }
}

/**
 * EmptyHero: coing wordmark + tagline centered in the space above, composer pinned to the
 * bottom — laid out exactly like [BottomDock] (same ime / navigation-bar padding, same
 * horizontal inset), so the composer never jumps when the first message turns the hero
 * into a chat. No suggestion chips.
 */
@Composable
private fun EmptyHero(vm: HomeViewModel, composer: ComposerState, settings: Settings, live: SessionLive, conv: Conversation, onAutomation: () -> Unit, onSettings: (String?) -> Unit) {
    val c = Dsh.colors
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.hero_title), Modifier.padding(horizontal = 24.dp), style = DshType.xl24.copy(fontSize = 34.sp, fontWeight = androidx.compose.ui.text.font.FontWeight(600)),
                color = c.labelPrimary, textAlign = TextAlign.Center)
            Text(stringResource(R.string.onb_tagline), Modifier.padding(horizontal = 48.dp).padding(top = 10.dp), style = DshType.xs13,
                color = c.labelTertiary, textAlign = TextAlign.Center)
            // Remote mode (#51): the new conversation runs on this computer.
            val remote by vm.remote.collectAsStateWithLifecycle()
            remote?.host?.let { h ->
                Text(stringResource(R.string.remote_hero, h.name), Modifier.padding(top = 8.dp), style = DshType.xs13, color = c.labelTertiary)
            }
        }
        Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(bottom = 4.dp)) {
            Box(Modifier.padding(horizontal = 8.dp)) {
                ComposerSlot(vm, composer, settings, live, conv, hero = true, onAutomation = onAutomation, onManageModes = { onSettings("Modes") })
            }
        }
    }
}

/** Bottom dock: [todo dock | queue dock] above the composer slot (approval ▸ permission ▸ question ▸ composer). */
@Composable
private fun BottomDock(vm: HomeViewModel, composer: ComposerState, settings: Settings, live: SessionLive, conv: Conversation, onAutomation: () -> Unit, onSettings: (String?) -> Unit) {
    Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (live.running) TodoDock(conv.todos, writtenThisTurn = conv.turns.lastOrNull()?.let { it.status == io.github.jackwener.dsh.core.session.TurnStatus.Running && it.todos != null } == true)
        QueueDock(live.queue, onEdit = { q -> vm.removeQueued(q.id); composer.text = q.text }, onDelete = { vm.removeQueued(it.id) }, onSendNow = { vm.sendQueuedNow(it.id) })
        Box(Modifier.padding(horizontal = 8.dp)) { ComposerSlot(vm, composer, settings, live, conv, hero = false, onAutomation = onAutomation, onManageModes = { onSettings("Modes") }) }
    }
}

/** First non-null wins: permission card, approval card, question card, composer. */
@Composable
fun ComposerSlot(vm: ConversationController, composer: ComposerState, settings: Settings, live: SessionLive, conv: Conversation, hero: Boolean,
                 onAutomation: (() -> Unit)? = null, showChips: Boolean = true, canSendEmpty: Boolean = false,
                 prepare: (String) -> String = { it }, onManageModes: (() -> Unit)? = null,
                 /** Bumped by a voice summon (assistant gesture, ball long-press): start listening. */
                 listenRequest: Int = 0) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val modelChangePending by vm.modelChangePending.collectAsStateWithLifecycle()
    val model by vm.model.collectAsStateWithLifecycle()
    val remote by vm.remote.collectAsStateWithLifecycle()
    // 工作目录 (#76): a folder of the agent's workspace, picked in the system folder picker (the DSH root).
    val workingDirectory by vm.workingDirectory.collectAsStateWithLifecycle()
    val home = remember { io.github.jackwener.dsh.core.platform.Workspace.home(context) }
    val pickFolder = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val dir = io.github.jackwener.dsh.core.platform.Workspace.fileOfUri(context, uri)?.takeIf { it.isDirectory }
        if (dir == null) android.widget.Toast.makeText(context, R.string.workdir_outside, android.widget.Toast.LENGTH_LONG).show()
        else vm.setWorkingDirectory(dir.path) { ok -> if (!ok) android.widget.Toast.makeText(context, R.string.workdir_failed, android.widget.Toast.LENGTH_LONG).show() }
    }
    val send = {
        val text = prepare(composer.outgoingText(context))
        val images = composer.images.map { it.mime to it.base64 }
        val editing = composer.editing
        composer.clear()
        // 编辑 (#2): the edited message goes to a new version of the conversation.
        if (editing != null) vm.resend(editing.messageId, io.github.jackwener.dsh.core.session.ForkKind.Edit, text, images, editing.attachments)
        else vm.send(text, images, now = settings.busySend == io.github.jackwener.dsh.core.data.BusySend.Steer)
    }
    val voice = io.github.jackwener.dsh.feature.voice.rememberVoiceInput(composer) {
        // Attachments still loading: leave the spoken text in the draft for a manual send.
        if (composer.pending == 0 && !composer.isEmpty) send()
    }
    val cardShown = live.takeover != null || live.permission != null || live.approval != null || live.question != null
    // A summon can land just before the panel switches to its recent session (a new composer, so a new
    // VoiceInput): a request taken in the last moment is replayed on the new one.
    var handled by remember { mutableIntStateOf(0) }
    var handledAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(listenRequest, voice) {
        val now = android.os.SystemClock.uptimeMillis()
        val replay = listenRequest == handled && listenRequest > 0 && now - handledAt < 1500
        if ((listenRequest > handled || replay) && !cardShown) { if (!replay) handledAt = now; handled = listenRequest; voice.start() }
    }
    // Queued messages the run never sent (stopped / failed) come back into the composer.
    LaunchedEffect(live.sessionId, live.restoredDraft) {
        val draft = live.restoredDraft ?: return@LaunchedEffect
        composer.text = listOf(draft, composer.text).filter { it.isNotBlank() }.joinToString("\n\n")
        vm.consumeRestoredDraft()
    }
    when {
        live.takeover != null -> TakeoverCard(live.takeover) { vm.answerTakeover(live.takeover.requestKey, it) }
        live.permission != null -> PermissionCard(live.permission) { vm.answerPermission(live.permission.requestKey, it) }
        live.approval != null -> ApprovalCard(live.approval) { vm.approve(live.approval, it) }
        live.question != null -> QuestionCard(live.question, onSubmit = { vm.answer(live.question, it) }, onSkip = { vm.skipQuestion(live.question) })
        else -> Composer(
            composer, live.running, live.preset, vm::setPreset, model.choice, settings.providers, model.effort, vm::setModel, vm::setEffort, conv.contextTokens,
            onSend = { voice.cancel(); send() },
            onStop = { vm.stop() },
            reasoning = model.reasoning(settings.reasoningCatalog), reasoningChoice = model.reasoningChoice, onReasoning = vm::setReasoning,
            // #53: this conversation's own model is gone; the next message runs on the default.
            modelNotice = live.model?.takeIf { model.fellBack }?.let { stringResource(R.string.session_model_gone, modelLabel(it.model), modelLabel(model.choice.model)) },
            hero = hero,
            actions = ComposerActions(
                onNewSession = if (conv.isEmpty) null else ({ vm.newSession() }),
                onTrajectory = if (conv.isEmpty) null else ({ vm.view.value = SessionView.Trajectory }),
                onAutomation = onAutomation,
                // The folder picker browses this phone; a remote conversation starts in its host's directory (#51).
                onWorkingDirectory = if (remote != null) null else ({
                    val ws = io.github.jackwener.dsh.core.platform.Workspace
                    val start = ws.docIdOf(home, java.io.File(workingDirectory ?: home.path))?.let { android.provider.DocumentsContract.buildDocumentUri(ws.authority(context), it) }
                    runCatching { pickFolder.launch(start ?: ws.rootDocumentUri(context)) }
                        .onFailure { android.widget.Toast.makeText(context, R.string.workspace_no_files_app, android.widget.Toast.LENGTH_LONG).show() }
                }),
                workingDirectory = workingDirectory?.takeIf { remote == null }?.let { io.github.jackwener.dsh.core.platform.Workspace.displayPath(home.path, it) },
            ),
            showChips = showChips,
            canSendEmpty = canSendEmpty,
            modelChangePending = modelChangePending,
            // A computer's dsh (#51) has no session modes (plugin-mode is the phone engine's).
            mode = if (remote == null) settings.mode(live.modeId) else null,
            modes = settings.allModes,
            modeLocked = !live.modeChangeable,
            onMode = vm::setMode,
            onManageModes = onManageModes,
            voice = voice,
            remoteLabel = remote?.label,
        )
    }
}
