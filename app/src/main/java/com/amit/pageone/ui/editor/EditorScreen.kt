package com.amit.pageone.ui.editor

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.Close
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Stream
import androidx.compose.material.icons.outlined.TextDecrease
import androidx.compose.material.icons.outlined.TextIncrease
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amit.pageone.core.Analysis
import com.amit.pageone.core.Element
import com.amit.pageone.core.ElementType
import com.amit.pageone.core.Production
import com.amit.pageone.core.SceneInfo
import com.amit.pageone.core.Script
import com.amit.pageone.core.rangeIn
import com.amit.pageone.data.AppGraph
import com.amit.pageone.data.AppSettings
import com.amit.pageone.data.Notifier
import com.amit.pageone.ui.theme.EditorPalette
import com.amit.pageone.ui.theme.LocalBrand
import com.amit.pageone.ui.theme.LocalEditorPalette
import com.amit.pageone.ui.theme.ScriptFont
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class EditorMode { WRITE, CARDS, PAGES }
private enum class SheetKind { EXPORT, REVISIONS, VERSIONS, TITLE, STATS, COLLAB, CHANGES, DOCTOR, BIBLE, STRUCTURE, XRAY, MOOD, NOTES, PRODUCTION, SPRINT, INBOX, SHORTCUTS }
/** Tablets / unfolded foldables: what sits beside the script. */
private enum class SidePane { NONE, NAVIGATOR, CARDS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(scriptId: String, settings: AppSettings, onBack: () -> Unit, onSettings: () -> Unit) {
    val vm: EditorViewModel = viewModel(key = "editor-$scriptId") { EditorViewModel(scriptId) }
    // While this screen is up the script is open: Wi-Fi sync leaves it alone, and catches up when it closes.
    androidx.compose.runtime.DisposableEffect(scriptId) {
        AppGraph.openScriptId = scriptId
        onDispose { if (AppGraph.openScriptId == scriptId) { AppGraph.openScriptId = null; AppGraph.sync.kick() } }
    }
    val scriptState by vm.script.collectAsStateWithLifecycle()
    val derived by vm.derived.collectAsStateWithLifecycle()
    val focusReq by vm.focusReq.collectAsStateWithLifecycle()
    val focusedId by vm.focused.collectAsStateWithLifecycle()
    val canUndo by vm.canUndo.collectAsStateWithLifecycle()
    val canRedo by vm.canRedo.collectAsStateWithLifecycle()
    val saveState by vm.saveState.collectAsStateWithLifecycle()
    val sessionWords by vm.sessionWords.collectAsStateWithLifecycle()
    val find by vm.find.collectAsStateWithLifecycle()
    val versions by vm.versions.collectAsStateWithLifecycle()
    val liveStatus by vm.liveStatus.collectAsStateWithLifecycle()
    val peers by vm.peers.collectAsStateWithLifecycle()
    val vmMessage by vm.message.collectAsStateWithLifecycle()
    val changes by vm.changes.collectAsStateWithLifecycle()
    val seen by vm.seen.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val today by AppGraph.goals.todayWords.collectAsState()

    val palette = LocalEditorPalette.current
    val cs = MaterialTheme.colorScheme
    val brand = LocalBrand.current
    val script = scriptState
    if (script == null) {
        Box(Modifier.fillMaxSize().background(palette.desk), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val ctx = LocalContext.current
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    var mode by rememberSaveable { mutableStateOf(EditorMode.WRITE) }
    var focusMode by rememberSaveable { mutableStateOf(false) }
    var textSizeBar by remember { mutableStateOf(false) }
    var sidePane by rememberSaveable { mutableStateOf(SidePane.NAVIGATOR) }
    var sheet by remember { mutableStateOf<SheetKind?>(null) }
    var sceneSheet by remember { mutableStateOf<SceneInfo?>(null) }
    var newScene by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var noteDraft by remember { mutableStateOf<Selection?>(null) }
    var notesFor by remember { mutableStateOf<Long?>(null) }
    var bibleFor by remember { mutableStateOf<String?>(null) }

    val elements = script.elements
    val indexById = remember(elements) { HashMap<Long, Int>(elements.size * 2).apply { elements.forEachIndexed { i, e -> put(e.id, i) } } }
    val dualIds = remember(elements) { dualBlockIds(elements) }
    val plan = remember(elements) { RowPlan.of(elements) }
    fun itemOf(idx: Int) = plan.rowOfElement(idx) + 1 // +1 for the title card
    val focusedEl = focusedId?.let { id -> indexById[id]?.let { elements[it] } }
    val pagination = derived?.pagination
    val totalPages = pagination?.pageCount ?: 1
    val firstVisible by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    val curIndex = focusedEl?.let { indexById[it.id] } ?: plan.firstElementOfRow(firstVisible - 1).coerceIn(0, (elements.size - 1).coerceAtLeast(0))
    val curPageIdx = pagination?.elementPage?.getOrNull(curIndex) ?: 0
    val curPage = pagination?.label(curPageIdx) ?: "${curPageIdx + 1}"
    val lastPage = pagination?.label(totalPages - 1) ?: "$totalPages"
    val curSceneIdx = Analysis.sceneIndexFor(elements, curIndex)
    val curSceneId = elements.getOrNull(curSceneIdx)?.id
    val scenes = derived?.scenes ?: emptyList()
    fun pageOfScene(sc: SceneInfo) = (pagination?.elementPage?.getOrNull(indexById[sc.elementId] ?: 0) ?: 0) + 1
    fun pageOfSceneId(id: Long): Int? = indexById[id]?.let { (pagination?.elementPage?.getOrNull(it) ?: 0) + 1 }
    fun sceneLabel(id: Long): String = derived?.sceneNumberById?.get(id) ?: scenes.firstOrNull { it.elementId == id }?.number?.toString() ?: "?"
    val characters = derived?.characters ?: emptyList()

    // Open anchored notes, by element: highlight range + author colour.
    val noteRanges = remember(script.extras.comments, elements) {
        val m = HashMap<Long, MutableList<Pair<IntRange, Color>>>()
        script.extras.comments.filter { !it.resolved }.forEach { c ->
            val e = indexById[c.elementId]?.let { elements[it] } ?: return@forEach
            c.rangeIn(e.text)?.let { r -> m.getOrPut(c.elementId) { ArrayList() } += r to Color(c.color) }
        }
        m
    }
    val openNotes = script.extras.comments.count { !it.resolved }

    fun hideKeyboard() { focusManager.clearFocus(); vm.clearFocus() }
    fun buzz() { if (settings.haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    fun message(m: String) { scope.launch { snackbar.showSnackbar(m) } }
    LaunchedEffect(vmMessage) { vmMessage?.let { vm.consumeMessage(); snackbar.showSnackbar(it) } }

    // Celebrate passing the daily goal while writing.
    var lastToday by remember { mutableIntStateOf(today) }
    LaunchedEffect(today) {
        val goal = settings.dailyGoal
        if (settings.goalCelebrations && goal > 0 && lastToday < goal && today >= goal) {
            buzz(); snackbar.showSnackbar("Daily goal reached — %,d words today".format(today))
        }
        lastToday = today
    }

    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { Notifier.scheduleAll(ctx) }
    fun ensureNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && !Notifier.canPost(ctx)) notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        Notifier.scheduleAll(ctx)
    }

    val peerColors = remember(peers) { peers.filter { it.elementId != null }.associate { it.elementId!! to Color(it.color) } }
    val unseenByElement = remember(changes, seen, liveStatus) {
        val m = LinkedHashMap<Long, com.amit.pageone.collab.Change>()
        for (c in changes) if (vm.isUnseen(c, seen) && c.kind != "delete" && c.elementId !in m) m[c.elementId] = c
        m
    }
    val unseenCount = remember(changes, seen, liveStatus) { changes.count { vm.isUnseen(it, seen) } }
    var openChange by remember { mutableStateOf<com.amit.pageone.collab.Change?>(null) }
    var following by remember { mutableStateOf<String?>(null) }

    fun startNote() {
        val fid = vm.focused.value ?: return message("Tap into a line first, then select the words to pin a note to")
        val e = vm.elementById(fid) ?: return
        val sel = vm.selection.value?.takeIf { it.elementId == fid && !it.collapsed } ?: Selection(fid, 0, e.text.length)
        noteDraft = sel
    }

    fun jumpScene(dir: Int) {
        val ss = vm.derived.value?.scenes ?: return
        val i = vm.focused.value?.let { indexById[it] } ?: curIndex
        val target = if (dir < 0) ss.lastOrNull { it.elementIndex < Analysis.sceneIndexFor(elements, i).coerceAtLeast(0) } else ss.firstOrNull { it.elementIndex > i }
        target?.let { vm.jumpTo(it.elementId) }
    }

    // Keyboard shortcuts (kept fresh with rememberUpdatedState so the row callbacks never go stale).
    val onShortcut by rememberUpdatedState { id: Long, s: Shortcut ->
        when (s) {
            is Shortcut.SetType -> vm.setType(id, s.type)
            Shortcut.CycleBack -> vm.cycleTypeBack(id)
            Shortcut.Find -> vm.openFind(true)
            Shortcut.Dual -> { if (!vm.toggleDual(id)) message("Dual dialogue needs two characters speaking back to back") }
            Shortcut.Comment -> startNote()
            Shortcut.Note -> vm.insertNote(id)
            Shortcut.NewScene -> vm.insertSceneAfter(id)
            Shortcut.PrevScene -> jumpScene(-1)
            Shortcut.NextScene -> jumpScene(1)
            Shortcut.Doctor -> { hideKeyboard(); sheet = SheetKind.DOCTOR }
            Shortcut.SaveVersion -> { vm.saveVersion("Saved with Ctrl+S"); message("Version saved") }
            Shortcut.Help -> { hideKeyboard(); sheet = SheetKind.SHORTCUTS }
            Shortcut.Focus -> focusMode = !focusMode
            Shortcut.SidePane -> sidePane = if (sidePane == SidePane.NAVIGATOR) SidePane.NONE else SidePane.NAVIGATOR
            Shortcut.Cards -> mode = if (mode == EditorMode.CARDS) EditorMode.WRITE else EditorMode.CARDS
            Shortcut.Export -> { hideKeyboard(); vm.flush(); sheet = SheetKind.EXPORT }
            Shortcut.PageView -> { hideKeyboard(); mode = EditorMode.PAGES }
        }
        true
    }

    BackHandler(enabled = drawer.isOpen || mode != EditorMode.WRITE || focusMode || find.open || textSizeBar) {
        when {
            drawer.isOpen -> scope.launch { drawer.close() }
            textSizeBar -> textSizeBar = false
            find.open -> vm.openFind(false)
            mode != EditorMode.WRITE -> mode = EditorMode.WRITE
            focusMode -> focusMode = false
        }
    }

    LaunchedEffect(focusReq?.seq) {
        val r = focusReq ?: return@LaunchedEffect
        val idx = indexById[r.id] ?: return@LaunchedEffect
        if (mode != EditorMode.WRITE) mode = EditorMode.WRITE
        val item = itemOf(idx)
        val visible = listState.layoutInfo.visibleItemsInfo.map { it.index }
        if (visible.isEmpty() || item < visible.first() || item > visible.last() - 1) listState.scrollToItem((item - 2).coerceAtLeast(0))
    }

    if (settings.typewriterScrolling) {
        LaunchedEffect(focusedId, focusedEl?.text?.length) {
            val idx = focusedId?.let { indexById[it] } ?: return@LaunchedEffect
            val h = listState.layoutInfo.viewportSize.height
            listState.animateScrollToItem(itemOf(idx), -(h / 3))
        }
    }

    LaunchedEffect(peers, following) {
        val f = following ?: return@LaunchedEffect
        val p = peers.firstOrNull { it.uid == f } ?: run { following = null; return@LaunchedEffect }
        val idx = p.elementId?.let { indexById[it] } ?: return@LaunchedEffect
        val visible = listState.layoutInfo.visibleItemsInfo.map { it.index }
        if (visible.isEmpty() || itemOf(idx) !in visible.drop(1).dropLast(1)) listState.animateScrollToItem((itemOf(idx) - 2).coerceAtLeast(0))
    }

    val rowActions = remember(vm) {
        RowActions(
            onText = vm::updateText,
            onEnter = vm::enter,
            onPaste = vm::pasteLines,
            onBackspaceAtStart = vm::backspaceAtStart,
            onCycle = vm::cycleType,
            onFocused = vm::onFocused,
            onCursor = vm::onCursor,
            onConsumeFocus = vm::consumeFocus,
            onUndo = vm::undo,
            onRedo = vm::redo,
            onShortcut = { id, s -> onShortcut(id, s) },
            onSelect = vm::onSelection,
            onCommentsTap = { id -> notesFor = id; sheet = SheetKind.NOTES },
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp

        fun openNavigator() { hideKeyboard(); if (wide) sidePane = if (sidePane == SidePane.NAVIGATOR) SidePane.NONE else SidePane.NAVIGATOR else scope.launch { drawer.open() } }
        fun toggleCards() {
            hideKeyboard()
            if (wide && mode != EditorMode.PAGES) sidePane = if (sidePane == SidePane.CARDS) SidePane.NONE else SidePane.CARDS
            else mode = if (mode == EditorMode.CARDS) EditorMode.WRITE else EditorMode.CARDS
        }

        ModalNavigationDrawer(
            drawerState = drawer,
            gesturesEnabled = drawer.isOpen,
            drawerContent = {
                NavigatorDrawer(
                    scenes = scenes, pageOf = ::pageOfScene, currentSceneId = curSceneId, totalPages = totalPages,
                    onPick = { sc ->
                        scope.launch { drawer.close() }
                        mode = EditorMode.WRITE
                        scope.launch { listState.scrollToItem((itemOf(indexById[sc.elementId] ?: 0) - 1).coerceAtLeast(0)) }
                    },
                    labelOf = { sceneLabel(it.elementId) },
                )
            },
        ) {
            Scaffold(
                containerColor = palette.desk,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = { SnackbarHost(snackbar) },
                topBar = {
                    when {
                        textSizeBar -> TextSizeBar(
                            scale = settings.textScale,
                            onChange = { d -> AppGraph.settings.update { it.copy(textScale = ((it.textScale + d) * 100).roundToInt().coerceIn(80, 180) / 100f) } },
                            onDone = { textSizeBar = false },
                        )
                        find.open -> Box(Modifier.statusBarsPadding()) {
                            FindBar(
                                state = find,
                                onQuery = { vm.setFindQuery(it) },
                                onMatchCase = { vm.setFindQuery(find.query, it) },
                                onReplace = vm::setReplace,
                                onNext = { vm.findNext(1) },
                                onPrev = { vm.findNext(-1) },
                                onReplaceOne = vm::replaceCurrent,
                                onReplaceAll = { val n = vm.replaceAll(); message("Replaced $n") },
                                onClose = { vm.openFind(false) },
                            )
                        }
                        focusMode -> Row(
                            Modifier.fillMaxWidth().background(palette.desk).statusBarsPadding().padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Page $curPage of $lastPage" + if (sessionWords > 0) "  ·  +$sessionWords words" else "",
                                style = MaterialTheme.typography.labelMedium, color = palette.faint, modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { focusMode = false }) { Icon(Icons.Filled.CloseFullscreen, "Exit focus mode", tint = palette.faint) }
                        }
                        else -> TopAppBar(
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.surface),
                            navigationIcon = { IconButton(onClick = { vm.flush(); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                            title = {
                                Column(Modifier.clickable { renaming = true }) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(script.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                        if (script.extras.lock.locked) Text("  LOCKED", style = MaterialTheme.typography.labelSmall, color = brand.accent)
                                    }
                                    val status = when (saveState) {
                                        SaveState.SAVED -> "Saved"
                                        SaveState.PENDING -> "Editing"
                                        SaveState.SAVING -> "Saving…"
                                    }
                                    val sub = when (mode) {
                                        EditorMode.CARDS -> "${scenes.size} scenes · $status"
                                        EditorMode.PAGES -> "Page view · $lastPage pages"
                                        EditorMode.WRITE -> "Page $curPage of $lastPage · $status" + if (sessionWords > 0) " · +$sessionWords" else ""
                                    }
                                    Text(sub, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, maxLines = 1)
                                }
                            },
                            actions = {
                                if (liveStatus != null) {
                                    Row(
                                        Modifier.clip(RoundedCornerShape(50)).clickable { hideKeyboard(); sheet = SheetKind.COLLAB }.padding(horizontal = 6.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        val dot = when (liveStatus) {
                                            is com.amit.pageone.collab.LiveStatus.Live -> Color(0xFF3FBF7F)
                                            is com.amit.pageone.collab.LiveStatus.Error -> cs.error
                                            else -> cs.onSurfaceVariant
                                        }
                                        Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(dot))
                                        if (peers.isNotEmpty()) { Spacer(Modifier.width(6.dp)); AvatarStack(peers, cs.surface) }
                                    }
                                }
                                IconButton(onClick = ::openNavigator) { Icon(Icons.AutoMirrored.Outlined.FormatListBulleted, "Navigator") }
                                val cardsOn = mode == EditorMode.CARDS || (wide && sidePane == SidePane.CARDS)
                                IconToggleButton(checked = cardsOn, onCheckedChange = { toggleCards() }) {
                                    Icon(Icons.Outlined.Dashboard, "Index cards", tint = if (cardsOn) cs.primary else cs.onSurface)
                                }
                                OverflowMenu(
                                    openNotes = openNotes,
                                    doctorCount = { doctorIssues(script, pagination).count { it.rule.severity != com.amit.pageone.core.Severity.POLISH } },
                                    inboxCount = { AppGraph.ideas.waiting },
                                    onAction = { a ->
                                        hideKeyboard()
                                        when (a) {
                                            MenuAction.FIND -> vm.openFind(true)
                                            MenuAction.TEXT_SIZE -> textSizeBar = true
                                            MenuAction.FOCUS -> { mode = EditorMode.WRITE; focusMode = true }
                                            MenuAction.PAGES -> mode = EditorMode.PAGES
                                            MenuAction.SPRINT -> sheet = SheetKind.SPRINT
                                            MenuAction.INBOX -> sheet = SheetKind.INBOX
                                            MenuAction.BIBLE -> { bibleFor = null; sheet = SheetKind.BIBLE }
                                            MenuAction.STRUCTURE -> sheet = SheetKind.STRUCTURE
                                            MenuAction.XRAY -> sheet = SheetKind.XRAY
                                            MenuAction.MOOD -> sheet = SheetKind.MOOD
                                            MenuAction.NOTES -> { notesFor = null; sheet = SheetKind.NOTES }
                                            MenuAction.DOCTOR -> sheet = SheetKind.DOCTOR
                                            MenuAction.PRODUCTION -> sheet = SheetKind.PRODUCTION
                                            MenuAction.EXPORT -> { vm.flush(); sheet = SheetKind.EXPORT }
                                            MenuAction.COLLAB -> sheet = SheetKind.COLLAB
                                            MenuAction.TITLE -> sheet = SheetKind.TITLE
                                            MenuAction.REVISIONS -> sheet = SheetKind.REVISIONS
                                            MenuAction.VERSIONS -> { vm.loadVersions(); sheet = SheetKind.VERSIONS }
                                            MenuAction.STATS -> sheet = SheetKind.STATS
                                            MenuAction.SHORTCUTS -> sheet = SheetKind.SHORTCUTS
                                            MenuAction.SETTINGS -> { vm.flush(); onSettings() }
                                        }
                                    },
                                )
                            },
                        )
                    }
                },
                floatingActionButton = {
                    if (mode == EditorMode.CARDS) {
                        ExtendedFloatingActionButton(onClick = { newScene = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("New card") })
                    }
                },
            ) { pad ->
                Column(Modifier.padding(pad).fillMaxSize()) {
                    SprintBanner()
                    if (liveStatus != null && !focusMode && mode == EditorMode.WRITE) {
                        LiveBanner(
                            peers = peers, unseen = unseenCount, following = following,
                            onFollow = { following = it },
                            onReview = { hideKeyboard(); sheet = SheetKind.CHANGES },
                        )
                    }
                    Row(Modifier.weight(1f)) {
                        // ---- tablet side pane
                        if (wide && !focusMode && mode == EditorMode.WRITE && sidePane != SidePane.NONE) {
                            Surface(color = cs.surfaceContainerLow, modifier = Modifier.width(if (sidePane == SidePane.CARDS) 380.dp else 320.dp).fillMaxHeight()) {
                                when (sidePane) {
                                    SidePane.NAVIGATOR -> NavigatorContent(
                                        scenes, ::pageOfScene, curSceneId, totalPages,
                                        onPick = { sc -> scope.launch { listState.scrollToItem((itemOf(indexById[sc.elementId] ?: 0) - 1).coerceAtLeast(0)) } },
                                        labelOf = { sceneLabel(it.elementId) },
                                    )
                                    SidePane.CARDS -> CardsBoard(
                                        scenes = scenes, pageOf = ::pageOfScene,
                                        onReorder = { vm.reorderScenes(it); buzz() },
                                        onEdit = { sceneSheet = it },
                                        extras = script.extras, labelOf = { sceneLabel(it.elementId) },
                                    )
                                    SidePane.NONE -> {}
                                }
                            }
                            VerticalDivider(color = cs.outlineVariant)
                        }
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            when (mode) {
                                EditorMode.WRITE -> ScriptPage(
                                    script = script, settings = settings, palette = palette, accent = cs.primary,
                                    listState = listState, derived = derived, dualIds = dualIds, plan = plan,
                                    focusedId = focusedId, focusReq = focusReq,
                                    dimOthers = focusMode && settings.focusDim,
                                    actions = rowActions,
                                    peerColors = peerColors,
                                    changeMarks = unseenByElement.mapValues { Color(it.value.color) },
                                    onChangeTap = { id -> openChange = unseenByElement[id] },
                                    onTitleClick = { sheet = SheetKind.TITLE },
                                    onAppend = { vm.appendNewElement() },
                                    noteRanges = noteRanges,
                                    find = find,
                                )
                                EditorMode.CARDS -> CardsBoard(
                                    scenes = scenes, pageOf = ::pageOfScene,
                                    onReorder = { vm.reorderScenes(it); buzz() },
                                    onEdit = { sceneSheet = it },
                                    extras = script.extras, labelOf = { sceneLabel(it.elementId) },
                                )
                                EditorMode.PAGES -> if (pagination != null) PageView(
                                    script, pagination, settings, startPage = curPageIdx,
                                    sceneLabels = derived?.sceneNumberById ?: emptyMap(),
                                    onEdit = { idx -> mode = EditorMode.WRITE; elements.getOrNull(idx)?.let { vm.jumpTo(it.id) } },
                                ) else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                            }
                        }
                    }
                    if (mode == EditorMode.WRITE && focusedEl != null) {
                        FormatBar(
                            current = focusedEl.type,
                            suggestions = remember(focusedEl, derived) { vm.suggestionsFor(focusedEl, derived) },
                            canUndo = canUndo,
                            canRedo = canRedo,
                            onType = { t -> buzz(); vm.setType(focusedEl.id, t) },
                            onSuggestion = { s -> buzz(); vm.applySuggestion(focusedEl.id, s.newText) },
                            onUndo = vm::undo,
                            onRedo = vm::redo,
                            onDual = { if (!vm.toggleDual(focusedEl.id)) message("Dual dialogue needs two characters speaking back to back") },
                            onNote = { vm.insertNote(focusedEl.id) },
                            onDelete = { vm.deleteElement(focusedEl.id) },
                            onHideKeyboard = ::hideKeyboard,
                            hasSelection = selection?.let { it.elementId == focusedEl.id && !it.collapsed } == true,
                            onComment = ::startNote,
                            onInbox = { hideKeyboard(); sheet = SheetKind.INBOX },
                            modifier = Modifier.windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
                        )
                    } else {
                        Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- dialogs & sheets
    if (renaming) {
        var t by remember { mutableStateOf(script.title) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Rename script") },
            text = { OutlinedTextField(value = t, onValueChange = { t = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { vm.rename(t.trim().ifEmpty { "Untitled" }); renaming = false }) { Text("Rename") } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
        )
    }
    sceneSheet?.let { sc0 ->
        val sc = scenes.firstOrNull { it.elementId == sc0.elementId } ?: sc0
        SceneSheet(
            scene = sc, label = sceneLabel(sc.elementId), script = script,
            onDismiss = { sceneSheet = null },
            onSaveCard = { h, syn, c -> vm.updateScene(sc.elementId, h, syn, c) },
            onExtras = vm::updateExtras,
            onGoTo = { sceneSheet = null; mode = EditorMode.WRITE; vm.jumpTo(sc.elementId) },
            onDelete = {
                sceneSheet = null
                vm.deleteScene(sc.elementId)
                if (!script.extras.lock.locked) scope.launch {
                    if (snackbar.showSnackbar("Scene deleted", actionLabel = "Undo") == SnackbarResult.ActionPerformed) vm.undo()
                }
            },
        )
    }
    if (newScene) {
        SceneEditDialog(
            scene = null,
            onDismiss = { newScene = false },
            onSave = { h, syn, c -> val id = vm.addScene(h, syn); if (c != 0) vm.updateScene(id, color = c); newScene = false },
            onGoTo = null, onDelete = null,
        )
    }
    noteDraft?.let { sel ->
        val e = vm.elementById(sel.elementId)
        val quote = e?.text?.let { t -> t.substring(sel.start.coerceIn(0, t.length), sel.end.coerceIn(sel.start.coerceIn(0, t.length), t.length)).ifEmpty { t } }.orEmpty()
        AddNoteDialog(quote, onDismiss = { noteDraft = null }, onAdd = { text, atts -> vm.addComment(sel, text, atts); noteDraft = null; message("Note pinned") })
    }
    openChange?.let { c ->
        ChangeDialog(
            change = c, elements = script.elements,
            onRevert = if (c.kind == "edit" && indexById.containsKey(c.elementId)) ({ vm.revert(c); message("Put back how it was") }) else null,
            onSeen = { vm.markSeen(c) },
            onDismiss = { openChange = null },
        )
    }
    SprintSummary()

    fun jump(id: Long) { sheet = null; mode = EditorMode.WRITE; vm.jumpTo(id) }
    when (sheet) {
        SheetKind.EXPORT -> {
            val toCheck = remember(script.elements, script.titlePage) { doctorIssues(script, pagination).count { it.rule.severity != com.amit.pageone.core.Severity.POLISH } }
            ExportSheet(script, settings, totalPages, onDismiss = { sheet = null }, onMessage = ::message, doctorCount = toCheck, onDoctor = { sheet = SheetKind.DOCTOR })
        }
        SheetKind.DOCTOR -> ScriptDoctorSheet(
            script = script, pagination = pagination, canUndo = canUndo,
            onDismiss = { sheet = null }, onJump = ::jump, onTitlePage = { sheet = SheetKind.TITLE },
            onFix = { vm.applyFix(it); buzz() }, onFixAll = { vm.applyBatchFixes(it); buzz() }, onUndo = vm::undo,
        )
        SheetKind.REVISIONS -> RevisionsSheet(script, pagination?.elementPage, settings.pdfColoredPages, onDismiss = { sheet = null }, onSet = vm::setRevision, onClear = vm::clearRevisionMarks)
        SheetKind.VERSIONS -> VersionsSheet(
            versions, onDismiss = { sheet = null },
            onSave = { vm.saveVersion(it); message("Version saved") },
            onRestore = { vm.restoreVersion(it); message("Version restored") },
            onDelete = { vm.deleteVersion(it) },
        )
        SheetKind.TITLE -> TitlePageEditor(script.titlePage, script.title, onDismiss = { sheet = null }, onSave = vm::setTitlePage)
        SheetKind.STATS -> StatsSheet(script, totalPages, sessionWords, onDismiss = { sheet = null })
        SheetKind.CHANGES -> ChangesSheet(
            changes = changes, elements = script.elements, myUid = vm.myUid,
            isUnseen = { vm.isUnseen(it, seen) },
            onOpen = { c ->
                sheet = null
                indexById[c.elementId]?.let { idx ->
                    scope.launch { listState.animateScrollToItem((itemOf(idx) - 2).coerceAtLeast(0)) }
                    openChange = c
                }
                if (c.uid == vm.myUid) openChange = null
            },
            onMarkAll = vm::markAllSeen,
            onDismiss = { sheet = null },
        )
        SheetKind.COLLAB -> CollabSheet(script, vm, liveStatus, peers, onDismiss = { sheet = null }, onOpenSettings = { vm.flush(); onSettings() }, onMessage = ::message)
        SheetKind.BIBLE -> BibleSheet(
            script, ::sceneLabel, onDismiss = { sheet = null },
            onSaveCharacter = { p -> vm.updateExtras { it.withCharacter(p) } },
            onSaveLocation = { p -> vm.updateExtras { it.withLocation(p) } },
            onJump = ::jump, initialCharacter = bibleFor,
        )
        SheetKind.STRUCTURE -> StructureSheet(
            script, scenes, totalPages, ::pageOfSceneId, ::sceneLabel,
            onDismiss = { sheet = null }, onSet = { st -> vm.updateExtras { it.copy(structure = st) } }, onJump = ::jump,
        )
        SheetKind.XRAY -> {
            val xs = remember(script.elements, pagination, script.extras.lock) { pagination?.let { Production.xray(script, it, settings.paper.linesPerPage) } ?: emptyList() }
            XRaySheet(script, xs, onDismiss = { sheet = null }, onJump = ::jump, onExtras = vm::updateExtras)
        }
        SheetKind.MOOD -> MoodBoardSheet(script, ::sceneLabel, onDismiss = { sheet = null }, onExtras = vm::updateExtras,
            onOpenScene = { id -> sheet = null; scenes.firstOrNull { it.elementId == id }?.let { sceneSheet = it } })
        SheetKind.NOTES -> CommentsSheet(
            script, notesFor, onDismiss = { sheet = null; notesFor = null }, onJump = ::jump,
            onReply = vm::replyToComment, onResolve = { id, r -> vm.resolveComment(id, r) }, onDelete = { vm.deleteComment(it) }, onAttach = { id, a -> vm.attachToComment(id, a) },
        )
        SheetKind.PRODUCTION -> ProductionSheet(
            script, settings, pagination, onDismiss = { sheet = null },
            onLock = vm::lockScript, onUnlock = vm::unlockScript,
            onDeadline = { d -> vm.updateExtras { it.copy(deadline = d) }; if (d != null) ensureNotifications() },
            onMessage = ::message,
        )
        SheetKind.SPRINT -> SprintSheet(script.title, onDismiss = { sheet = null }, onStart = { m, w ->
            sheet = null; AppGraph.sprints.start(m, w, script.title); ensureNotifications(); message("Sprint started — $m minutes on the clock")
        })
        SheetKind.INBOX -> InboxSheet(script.title, onDismiss = { sheet = null }, onInsert = { idea, type ->
            sheet = null; vm.insertIdea(idea.text, type, idea.audio, idea.durationMs); AppGraph.ideas.markUsed(idea.id, script.title); message(if (idea.audio.isNotEmpty()) "Voice idea pinned to the script as a note" else "Idea added to the script")
        })
        SheetKind.SHORTCUTS -> ShortcutsSheet(onDismiss = { sheet = null })
        null -> {}
    }
}

/** Ids of every element that's part of a dual-dialogue pair. */
private fun dualBlockIds(els: List<Element>): Set<Long> {
    val out = HashSet<Long>()
    fun blockFrom(start: Int): Int {
        var j = start + 1
        while (j < els.size && (els[j].type == ElementType.DIALOGUE || els[j].type == ElementType.PARENTHETICAL || els[j].type == ElementType.LYRICS)) j++
        return j
    }
    for (i in els.indices) {
        if (els[i].type == ElementType.CHARACTER && els[i].dual) {
            for (k in i until blockFrom(i)) out.add(els[k].id)
            var p = i - 1
            while (p >= 0 && els[p].type != ElementType.CHARACTER && els[p].type.isDialogueBlockPart) p--
            if (p >= 0 && els[p].type == ElementType.CHARACTER) for (k in p until i) out.add(els[k].id)
        }
    }
    return out
}

private enum class MenuAction { FIND, TEXT_SIZE, FOCUS, PAGES, SPRINT, INBOX, BIBLE, STRUCTURE, XRAY, MOOD, NOTES, DOCTOR, PRODUCTION, EXPORT, COLLAB, TITLE, REVISIONS, VERSIONS, STATS, SHORTCUTS, SETTINGS }

/** Text size, adjusted in place at the top of the script so the page stays in view. */
@Composable
private fun TextSizeBar(scale: Float, onChange: (Float) -> Unit, onDone: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.surfaceContainer) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Text size ${(scale * 100).roundToInt()}%", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { onChange(-0.05f) }, enabled = scale > 0.8f) { Icon(Icons.Outlined.TextDecrease, "Smaller") }
            IconButton(onClick = { onChange(0.05f) }, enabled = scale < 1.8f) { Icon(Icons.Outlined.TextIncrease, "Larger") }
            TextButton(onClick = onDone) { Text("Done") }
        }
    }
}

/** The ⋮ menu: a full-screen panel, grouped the way a writer thinks: write, story, check & ship, script. */
@Composable
private fun OverflowMenu(
    openNotes: Int,
    doctorCount: () -> Int,
    inboxCount: () -> Int,
    onAction: (MenuAction) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, "More") }
    if (!open) return
    val cs = MaterialTheme.colorScheme
    val doctorN = remember { doctorCount() }
    val inboxN = remember { inboxCount() }
    class Entry(val label: String, val icon: ImageVector, val action: MenuAction, val badge: Int = 0)
    val groups = listOf(
        "Write" to listOf(
            Entry("Find", Icons.Outlined.Search, MenuAction.FIND),
            Entry("Focus", Icons.Outlined.CenterFocusStrong, MenuAction.FOCUS),
            Entry("Page view", Icons.Outlined.MenuBook, MenuAction.PAGES),
            Entry("Sprint", Icons.Outlined.Timer, MenuAction.SPRINT),
            Entry("Ideas", Icons.Outlined.Inbox, MenuAction.INBOX, inboxN),
            Entry("Text size", Icons.Outlined.TextIncrease, MenuAction.TEXT_SIZE),
        ),
        "Story" to listOf(
            Entry("Bible", Icons.Outlined.AutoStories, MenuAction.BIBLE),
            Entry("Beats", Icons.Outlined.AccountTree, MenuAction.STRUCTURE),
            Entry("X-ray", Icons.Outlined.Stream, MenuAction.XRAY),
            Entry("Mood board", Icons.Outlined.Collections, MenuAction.MOOD),
            Entry("Notes", Icons.Outlined.ChatBubbleOutline, MenuAction.NOTES, openNotes),
        ),
        "Check & ship" to listOf(
            Entry("Doctor", Icons.Outlined.HealthAndSafety, MenuAction.DOCTOR, doctorN),
            Entry("Production", Icons.Outlined.WorkOutline, MenuAction.PRODUCTION),
            Entry("Export", Icons.Outlined.IosShare, MenuAction.EXPORT),
            Entry("Collaborate", Icons.Outlined.Groups, MenuAction.COLLAB),
        ),
        "Script" to listOf(
            Entry("Title page", Icons.AutoMirrored.Outlined.Article, MenuAction.TITLE),
            Entry("Revisions", Icons.Outlined.Palette, MenuAction.REVISIONS),
            Entry("Versions", Icons.Outlined.History, MenuAction.VERSIONS),
            Entry("Statistics", Icons.Outlined.BarChart, MenuAction.STATS),
            Entry("Shortcuts", Icons.Outlined.Keyboard, MenuAction.SHORTCUTS),
            Entry("Settings", Icons.Outlined.Settings, MenuAction.SETTINGS),
        ),
    )
    androidx.compose.ui.window.Dialog(
        onDismissRequest = { open = false },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = cs.surface) {
            Column(Modifier.fillMaxSize().statusBarsPadding().windowInsetsPadding(WindowInsets.navigationBars)) {
                Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Tools", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = { open = false }) { Icon(Icons.Outlined.Close, "Close") }
                }
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    groups.forEach { (title, entries) ->
                        Surface(shape = RoundedCornerShape(24.dp), color = cs.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(horizontal = 8.dp, vertical = 12.dp)) {
                                com.amit.pageone.ui.components.SectionHeader(title, Modifier.padding(horizontal = 12.dp))
                                entries.chunked(4).forEach { row ->
                                    Row(Modifier.fillMaxWidth()) {
                                        row.forEach { e ->
                                            com.amit.pageone.ui.components.QuickAction(e.icon, e.label, Modifier.weight(1f), badge = e.badge) { open = false; onAction(e.action) }
                                        }
                                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun ScriptPage(
    script: Script,
    settings: AppSettings,
    palette: EditorPalette,
    accent: Color,
    listState: LazyListState,
    derived: Derived?,
    dualIds: Set<Long>,
    plan: RowPlan,
    focusedId: Long?,
    focusReq: FocusReq?,
    dimOthers: Boolean,
    actions: RowActions,
    peerColors: Map<Long, Color>,
    changeMarks: Map<Long, Color>,
    onChangeTap: (Long) -> Unit,
    onTitleClick: () -> Unit,
    onAppend: () -> Unit,
    noteRanges: Map<Long, List<Pair<IntRange, Color>>> = emptyMap(),
    find: FindState? = null,
) {
    val density = LocalDensity.current
    val findLen = find?.query?.length ?: 0
    val findHits = remember(find?.matches, find?.open) {
        if (find == null || !find.open || findLen == 0) emptyMap()
        else find.matches.groupBy({ it.first }, { it.second until it.second + findLen })
    }
    val findCurrent = find?.matches?.getOrNull(find.current)
    BoxWithConstraints(Modifier.fillMaxSize().background(palette.desk)) {
        val paperWidth: Dp = min(maxWidth - 12.dp, 760.dp)
        val leftG = 30.dp
        val rightG = 16.dp
        val col = (paperWidth - leftG - rightG) / 60f
        val metrics = remember(paperWidth, settings.textScale, density) {
            val fontPx = with(density) { col.toPx() } / 0.6f * settings.textScale
            val fs = with(density) { fontPx.toSp() }
            val lh = with(density) { (fontPx * 1.2f).toSp() }
            PageMetrics(leftG, rightG, col, fs, lh, with(density) { (fontPx * 1.2f).toDp() })
        }
        val pages = derived?.pagination?.elementPage
        val layout = settings.layout
        val elements = script.elements
        val spellSkip = remember(derived?.characters) {
            derived?.characters.orEmpty().flatMap { it.lowercase().split(Regex("[^\\p{L}'’]+")) }.filter { it.isNotBlank() }.toSet()
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.width(paperWidth).fillMaxSize().align(Alignment.TopCenter),
            contentPadding = PaddingValues(top = 10.dp),
        ) {
            item(key = "title") { TitleCard(script, palette, metrics, onTitleClick) }
            itemsIndexed(plan.rows, key = { _, r -> elements[r.start].id }) { _, r ->
                val i = r.start
                val page = pages?.getOrNull(i) ?: 0
                val prevPage = if (i == 0) 0 else pages?.getOrNull(i - 1) ?: 0
                val newPage = settings.showPageBreaks && i > 0 && page > prevPage
                @Composable
                fun row(k: Int, side: Boolean?, first: Boolean) {
                    val e = elements[k]
                    ElementRow(
                        element = e,
                        metrics = metrics,
                        layout = layout,
                        palette = palette,
                        accent = accent,
                        sceneNumber = derived?.sceneNumberById?.get(e.id),
                        showSceneNumber = settings.editorSceneNumbers,
                        contd = settings.showContd && derived?.contd?.get(e.id) == true,
                        inDual = e.id in dualIds,
                        dim = dimOthers && focusedId != null && e.id != focusedId,
                        showRevision = settings.showRevisionMarks,
                        isFirstOnPage = first,
                        boldHeadings = settings.boldSceneHeadings,
                        focusReq = if (focusReq?.id == e.id) focusReq else null,
                        actions = actions,
                        peerColor = peerColors[e.id],
                        changeColor = changeMarks[e.id],
                        onChangeTap = if (changeMarks.containsKey(e.id)) ({ onChangeTap(e.id) }) else null,
                        spellCheck = settings.spellCheck,
                        spellSkip = spellSkip,
                        dualSide = side,
                        noteRanges = noteRanges[e.id].orEmpty(),
                        findRanges = findHits[e.id].orEmpty(),
                        findCurrent = findCurrent?.takeIf { it.first == e.id }?.let { it.second until it.second + findLen },
                    )
                }
                Column {
                    if (newPage) PageBreak(derived?.pagination?.label(page) ?: "${page + 1}", palette, metrics)
                    if (r.split < 0) {
                        row(i, null, i == 0 || newPage)
                    } else {
                        Box(Modifier.fillMaxWidth().background(palette.paper).padding(top = if (i == 0 || newPage) 0.dp else metrics.lineDp)) {
                            Column(Modifier.fillMaxWidth()) { for (k in r.start until r.split) row(k, false, true) }
                            Column(Modifier.fillMaxWidth()) { for (k in r.split until r.end) row(k, true, true) }
                        }
                    }
                }
            }
            item(key = "end") {
                Box(
                    Modifier.fillMaxWidth().height(280.dp).background(palette.paper)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onAppend() },
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Text("Tap to keep writing", style = MaterialTheme.typography.labelMedium, color = palette.faint.copy(alpha = 0.6f), modifier = Modifier.padding(top = 40.dp))
                }
            }
        }
    }
}

/** The title page, in miniature, at the top of the script. Tap to edit. */
@Composable
private fun TitleCard(script: Script, palette: EditorPalette, metrics: PageMetrics, onClick: () -> Unit) {
    val tp = script.titlePage
    val style = TextStyle(fontFamily = ScriptFont, fontSize = metrics.fontSize, lineHeight = metrics.lineHeight, color = palette.ink, textAlign = TextAlign.Center)
    Column {
        Column(
            Modifier.fillMaxWidth().background(palette.paper).clickable(onClick = onClick).padding(vertical = 44.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(tp.title.ifBlank { script.title }.uppercase(), style = style.copy(fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline))
            Spacer(Modifier.height(metrics.lineDp))
            val credits = tp.credits.filter { it.names.isNotBlank() }
            if (credits.isEmpty()) {
                Text("Written by", style = style)
                Text("Your name", style = style.copy(color = palette.faint))
            }
            credits.forEach { c ->
                Text(c.role, style = style)
                Text(c.names, style = style)
                Spacer(Modifier.height(metrics.lineDp * 0.6f))
            }
            if (tp.basedOnLine.isNotBlank()) Text(tp.basedOnLine, style = style.copy(color = palette.faint))
            val draft = listOf(tp.draft, tp.draftDate).filter { it.isNotBlank() }.joinToString(" · ")
            if (draft.isNotBlank()) {
                Spacer(Modifier.height(metrics.lineDp))
                Text(draft, style = style.copy(color = palette.faint))
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.pageEdge))
        Box(Modifier.fillMaxWidth().height(14.dp).background(palette.desk))
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.pageEdge))
        Box(Modifier.fillMaxWidth().height(metrics.lineDp * 2).background(palette.paper))
    }
}

/** One lazy-list row: a single element, or a dual-dialogue pair ([start, split) left, [split, end) right). */
data class PlanRow(val start: Int, val split: Int, val end: Int)

/** How elements map onto editor rows — dual-dialogue pairs share one row. */
class RowPlan(val rows: List<PlanRow>, private val rowOf: IntArray) {
    fun rowOfElement(i: Int): Int = if (i in rowOf.indices) rowOf[i] else (rows.size - 1).coerceAtLeast(0)
    fun firstElementOfRow(r: Int): Int = rows.getOrNull(r.coerceAtLeast(0))?.start ?: 0

    companion object {
        fun of(els: List<Element>): RowPlan {
            fun blockEnd(start: Int): Int {
                var j = start + 1
                while (j < els.size && (els[j].type == ElementType.DIALOGUE || els[j].type == ElementType.PARENTHETICAL || els[j].type == ElementType.LYRICS)) j++
                return j
            }
            val rows = ArrayList<PlanRow>(els.size)
            val rowOf = IntArray(els.size)
            var i = 0
            while (i < els.size) {
                val e = els[i]
                if (e.type == ElementType.CHARACTER && !e.dual) {
                    val e1 = blockEnd(i)
                    if (e1 < els.size && els[e1].type == ElementType.CHARACTER && els[e1].dual) {
                        val e2 = blockEnd(e1)
                        for (k in i until e2) rowOf[k] = rows.size
                        rows += PlanRow(i, e1, e2)
                        i = e2
                        continue
                    }
                }
                rowOf[i] = rows.size
                rows += PlanRow(i, -1, i + 1)
                i++
            }
            return RowPlan(rows, rowOf)
        }
    }
}
