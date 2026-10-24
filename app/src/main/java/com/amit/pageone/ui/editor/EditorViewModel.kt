package com.amit.pageone.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amit.pageone.core.Analysis
import com.amit.pageone.core.Element
import com.amit.pageone.core.ElementType
import com.amit.pageone.core.Formatting
import com.amit.pageone.core.Fountain
import com.amit.pageone.core.IdGen
import com.amit.pageone.core.LayoutOptions
import com.amit.pageone.core.Pagination
import com.amit.pageone.core.Paginator
import com.amit.pageone.core.SceneInfo
import com.amit.pageone.core.Script
import com.amit.pageone.core.TitlePage
import com.amit.pageone.core.Words
import com.amit.pageone.data.AppGraph
import com.amit.pageone.data.VersionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.amit.pageone.collab.LiveSession
import com.amit.pageone.collab.LiveStatus
import com.amit.pageone.collab.Peer
import com.amit.pageone.core.Merge
import com.amit.pageone.data.Invite

/** A request for a particular row to take focus with a given selection. */
/** [focus] false = only scroll the line into view (Find), so typing stays where it is. */
data class FocusReq(val id: Long, val start: Int, val end: Int = start, val seq: Long, val focus: Boolean = true)

/** Things computed from the whole script off the main thread after edits settle. */
data class Derived(
    val pagination: Pagination,
    val scenes: List<SceneInfo>,
    val contd: Map<Long, Boolean>,
    val characters: List<String>,
    val locations: List<String>,
    val words: Int,
    /** Printed scene numbers — "12", or "12A" for scenes added after a production lock. */
    val sceneNumberById: Map<Long, String>,
)

/** A text selection inside one element (for anchoring notes). */
data class Selection(val elementId: Long, val start: Int, val end: Int) {
    val collapsed: Boolean get() = start == end
}

data class Suggestion(val label: String, val newText: String)

data class FindState(
    val open: Boolean = false,
    val query: String = "",
    val replace: String = "",
    val matchCase: Boolean = false,
    val matches: List<Pair<Long, Int>> = emptyList(),
    val current: Int = -1,
)

enum class SaveState { SAVED, PENDING, SAVING }

class EditorViewModel(private val scriptId: String) : ViewModel() {

    private val store = AppGraph.store
    private val settings get() = AppGraph.settings.state.value

    private val _script = MutableStateFlow<Script?>(null)
    val script: StateFlow<Script?> = _script.asStateFlow()

    private val _derived = MutableStateFlow<Derived?>(null)
    val derived: StateFlow<Derived?> = _derived.asStateFlow()

    private val _focusReq = MutableStateFlow<FocusReq?>(null)
    val focusReq: StateFlow<FocusReq?> = _focusReq.asStateFlow()

    /** Element that currently has keyboard focus (reported by the UI). */
    private val _focused = MutableStateFlow<Long?>(null)
    val focused: StateFlow<Long?> = _focused.asStateFlow()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val _saveState = MutableStateFlow(SaveState.SAVED)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    private val _sessionWords = MutableStateFlow(0)
    val sessionWords: StateFlow<Int> = _sessionWords.asStateFlow()

    private val _find = MutableStateFlow(FindState())
    val find: StateFlow<FindState> = _find.asStateFlow()

    private val _versions = MutableStateFlow<List<VersionInfo>>(emptyList())
    val versions: StateFlow<List<VersionInfo>> = _versions.asStateFlow()

    private var ids = IdGen(1)
    private var seq = 0L

    private data class Snap(val elements: List<Element>, val focus: Long?, val cursor: Int)
    private val undoStack = ArrayDeque<Snap>()
    private val redoStack = ArrayDeque<Snap>()
    private var lastTypingId: Long? = null
    private var lastTypingAt = 0L
    private var lastCursor = 0

    private var deriveJob: Job? = null
    private var saveJob: Job? = null
    private var dirty = false
    private var lastVersionAt = System.currentTimeMillis()
    private var layoutKey: LayoutOptions? = null

    private val stopHook: () -> Unit = { flush() }

    // ------------------------------------------------------------------ collaboration state
    private var live: LiveSession? = null
    private var pushJob: Job? = null
    private val _liveStatus = MutableStateFlow<LiveStatus?>(null)
    val liveStatus: StateFlow<LiveStatus?> = _liveStatus.asStateFlow()
    private val _peers = MutableStateFlow<List<Peer>>(emptyList())
    val peers: StateFlow<List<Peer>> = _peers.asStateFlow()
    private val _changes = MutableStateFlow<List<com.amit.pageone.collab.Change>>(emptyList())
    val changes: StateFlow<List<com.amit.pageone.collab.Change>> = _changes.asStateFlow()
    data class Seen(val all: Long, val elements: Map<Long, Long>)
    private val _seen = MutableStateFlow(Seen(AppGraph.collab.seenAt(scriptId), AppGraph.collab.seenElements(scriptId)))
    val seen: StateFlow<Seen> = _seen.asStateFlow()
    val myUid: String? get() = live?.uid
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun consumeMessage() { _message.value = null }

    init {
        AppGraph.stopHooks.add(stopHook)
        AppGraph.openScriptId = scriptId
        viewModelScope.launch {
            val s = store.load(scriptId)?.let { it.copy(extras = it.extras.prune(it.elements)) }
            if (s != null) {
                ids = IdGen(s.nextId)
                _script.value = s
                rederive(immediate = true)
                // Open with the cursor in the last element
                s.elements.lastOrNull()?.let { requestFocus(it.id, it.text.length) }
                if (AppGraph.collab.isLive(scriptId)) startLive()
            }
        }
        // Re-paginate when layout-affecting settings change.
        viewModelScope.launch {
            AppGraph.settings.state.collect {
                if (layoutKey != null && layoutKey != it.layout) rederive(immediate = true)
            }
        }
    }

    override fun onCleared() {
        AppGraph.stopHooks.remove(stopHook)
        if (AppGraph.openScriptId == scriptId) { AppGraph.openScriptId = null; AppGraph.sync.kick() }
        live?.stop(); live = null
        flush()
        super.onCleared()
    }

    // ------------------------------------------------------------------ helpers

    private val elements: List<Element> get() = _script.value?.elements ?: emptyList()
    private fun indexOf(id: Long) = elements.indexOfFirst { it.id == id }
    fun elementById(id: Long?): Element? = id?.let { i -> elements.firstOrNull { it.id == i } }

    fun requestFocus(id: Long, start: Int, end: Int = start, focus: Boolean = true) {
        _focusReq.value = FocusReq(id, start, end, ++seq, focus)
    }

    fun consumeFocus(req: FocusReq) {
        if (_focusReq.value?.seq == req.seq) _focusReq.value = null
    }

    fun onFocused(id: Long, cursor: Int) { _focused.value = id; lastCursor = cursor; live?.setCursor(id) }
    fun onCursor(cursor: Int) { lastCursor = cursor }
    fun clearFocus() { _focused.value = null }

    private enum class Kind { TYPING, STRUCTURE }

    /**
     * The single entry point for every edit. Handles undo history, revision marks,
     * word-goal tracking, re-pagination and autosave.
     */
    private fun mutate(kind: Kind, typingId: Long? = null, f: (List<Element>) -> List<Element>) {
        val s = _script.value ?: return
        val old = s.elements
        var new = f(old)
        if (new === old) return
        if (new.isEmpty()) new = listOf(Element(ids.next(), ElementType.SCENE_HEADING, ""))

        val now = System.currentTimeMillis()
        val coalesce = kind == Kind.TYPING && typingId == lastTypingId && now - lastTypingAt < 1500
        if (!coalesce) {
            undoStack.addLast(Snap(old, _focused.value, lastCursor))
            if (undoStack.size > 300) undoStack.removeFirst()
            redoStack.clear()
        }
        if (kind == Kind.TYPING) { lastTypingId = typingId; lastTypingAt = now } else lastTypingId = null

        // Revision marks + word tracking for changed elements.
        val oldById = HashMap<Long, Element>(old.size * 2)
        old.forEach { oldById[it.id] = it }
        var delta = 0
        val marked = if (s.revisionMode) ArrayList<Element>(new.size) else null
        for (e in new) {
            val o = oldById[e.id]
            val changed = o == null || o.text != e.text || o.type != e.type || o.dual != e.dual
            if (changed && (o == null || o.text != e.text)) delta += Words.count(e.text) - (o?.let { Words.count(it.text) } ?: 0)
            marked?.add(if (changed && e.revision != s.revisionLevel) e.copy(revision = s.revisionLevel) else e)
        }
        if (delta > 0) { _sessionWords.value += delta; AppGraph.goals.add(delta) }

        _script.value = s.copy(elements = marked ?: new, modifiedAt = now)
        _canUndo.value = undoStack.isNotEmpty(); _canRedo.value = redoStack.isNotEmpty()
        afterChange()
    }

    private fun afterChange() {
        dirty = true
        _saveState.value = SaveState.PENDING
        rederive()
        saveJob?.cancel()
        saveJob = viewModelScope.launch { delay(1200); save() }
        if (_find.value.open && _find.value.query.isNotEmpty()) refreshMatches(keepIndex = true)
        schedulePush()
    }

    // ------------------------------------------------------------------ live collaboration

    private fun schedulePush() {
        val l = live ?: return
        pushJob?.cancel()
        pushJob = viewModelScope.launch {
            delay(350)
            val s = _script.value ?: return@launch
            l.push(s.elements, s.title, s.titlePage)
            l.pushExtras(sharedExtras(s), s.extras.comments)
        }
    }

    private fun sharedExtras(s: Script) = com.amit.pageone.data.ExtrasCodec.encode(s.extras, includeComments = false, includeLocalMedia = false).toString()

    private fun startLive() {
        val cfg = AppGraph.collab.server.value
        if (!cfg.isValid || live != null) {
            if (!cfg.isValid) _liveStatus.value = LiveStatus.Error("Live co-writing isn't available in this copy of Page One.")
            return
        }
        val c = AppGraph.collab
        val session = LiveSession(
            context = AppGraph.context,
            config = cfg,
            scriptId = scriptId,
            myName = c.displayName(),
            myColor = c.colorFor(c.displayName() + scriptId),
            scope = viewModelScope,
            hasLocalPending = { pushJob?.isActive == true },
            onRemoteElements = ::applyRemoteElements,
            onRemoteMeta = ::applyRemoteMeta,
            onRemoteExtras = ::applyRemoteExtras,
            onRemoteComments = ::applyRemoteComments,
        )
        live = session
        viewModelScope.launch { session.status.collect { _liveStatus.value = it } }
        viewModelScope.launch { session.peers.collect { _peers.value = it } }
        viewModelScope.launch { session.changes.collect { _changes.value = it } }
        session.start { remoteHad ->
            if (!remoteHad) {
                val s = _script.value ?: return@start
                session.push(s.elements, s.title, s.titlePage, log = false) // first upload isn't "a change"
                session.pushExtras(sharedExtras(s), s.extras.comments)
            }
        }
    }

    /** Is this change something a co-writer did that you haven't looked at yet? */
    fun isUnseen(c: com.amit.pageone.collab.Change, s: Seen = _seen.value): Boolean {
        val me = live?.uid ?: return false
        return c.uid != me && c.at > s.all && c.at > (s.elements[c.elementId] ?: 0L)
    }

    fun markAllSeen() {
        val t = System.currentTimeMillis()
        AppGraph.collab.setSeenAt(scriptId, t); AppGraph.collab.setSeenElements(scriptId, emptyMap())
        _seen.value = Seen(t, emptyMap())
    }

    fun markSeen(c: com.amit.pageone.collab.Change) {
        val m = _seen.value.elements + (c.elementId to maxOf(c.at, _seen.value.elements[c.elementId] ?: 0L))
        AppGraph.collab.setSeenElements(scriptId, m)
        _seen.value = _seen.value.copy(elements = m)
    }

    /** Put an element back the way it read before a co-writer's change. */
    fun revert(c: com.amit.pageone.collab.Change): Boolean {
        val i = indexOf(c.elementId)
        if (i < 0 || c.kind != "edit") return false
        val e = elements[i]
        mutate(Kind.STRUCTURE) { list -> list.toMutableList().also { it[i] = e.copy(text = c.before) } }
        markSeen(c)
        return true
    }

    /** Short join code for this shared script (created on first use). */
    suspend fun joinCode(): String? {
        val s = _script.value ?: return null
        val cfg = AppGraph.collab.server.value
        return runCatching { com.amit.pageone.collab.Hub.codeFor(AppGraph.context, cfg, scriptId, s.title, AppGraph.collab.displayName()) }
            .onFailure { _message.value = "Couldn't create a join code: ${it.message}" }
            .getOrNull()
    }

    /** Turn on live co-editing for this script (uploads it if it isn't shared yet). */
    fun goLive() {
        AppGraph.collab.setLive(scriptId, true)
        startLive()
    }

    fun stopLive() {
        live?.stop(); live = null
        AppGraph.collab.setLive(scriptId, false)
        _liveStatus.value = null; _peers.value = emptyList()
    }


    private fun applyRemoteElements(list: List<Element>) {
        val s = _script.value ?: return
        if (list == s.elements) return
        ids.ensureAbove(0)
        _script.value = s.copy(elements = list, modifiedAt = System.currentTimeMillis())
        redoStack.clear(); _canRedo.value = false
        lastTypingId = null
        dirty = true
        _saveState.value = SaveState.PENDING
        rederive()
        saveJob?.cancel()
        saveJob = viewModelScope.launch { delay(1200); save() }
    }

    private fun applyRemoteMeta(title: String, tp: TitlePage) {
        val s = _script.value ?: return
        if (s.titlePage == tp && s.title == title) return
        _script.value = s.copy(title = title.ifBlank { s.title }, titlePage = tp)
        dirty = true
        saveJob?.cancel()
        saveJob = viewModelScope.launch { delay(1200); save() }
    }

    private fun applyRemoteExtras(json: String) {
        val s = _script.value ?: return
        val remote = runCatching { com.amit.pageone.data.ExtrasCodec.decode(org.json.JSONObject(json)) }.getOrNull() ?: return
        val merged = com.amit.pageone.data.ExtrasCodec.mergeRemote(s.extras, remote)
        if (merged == s.extras) return
        val relock = merged.lock != s.extras.lock
        _script.value = s.copy(extras = merged)
        dirty = true
        if (relock) rederive(immediate = true)
        saveJob?.cancel()
        saveJob = viewModelScope.launch { delay(1200); save() }
    }

    private fun applyRemoteComments(list: List<com.amit.pageone.core.Comment>) {
        val s = _script.value ?: return
        if (list == s.extras.comments) return
        _script.value = s.copy(extras = s.extras.copy(comments = list))
        dirty = true
        saveJob?.cancel()
        saveJob = viewModelScope.launch { delay(1200); save() }
    }

    // ------------------------------------------------------------------ story data (bible, beats, threads, notes, lock)

    private val _selection = MutableStateFlow<Selection?>(null)
    /** The current text selection in the focused element, if any. */
    val selection: StateFlow<Selection?> = _selection.asStateFlow()
    fun onSelection(id: Long, start: Int, end: Int) { _selection.value = Selection(id, minOf(start, end), maxOf(start, end)) }

    /** Change story data (not undoable; it's edited in forms, not typed). */
    fun updateExtras(f: (com.amit.pageone.core.ScriptExtras) -> com.amit.pageone.core.ScriptExtras) {
        val s = _script.value ?: return
        val n = f(s.extras)
        if (n == s.extras) return
        val relock = n.lock != s.extras.lock
        _script.value = s.copy(extras = n, modifiedAt = System.currentTimeMillis())
        afterChange()
        if (relock) rederive(immediate = true)
    }

    fun addComment(sel: Selection, text: String, attachments: List<com.amit.pageone.core.Attachment> = emptyList()) {
        val e = elementById(sel.elementId) ?: return
        val c = AppGraph.collab
        val start = sel.start.coerceIn(0, e.text.length)
        var end = sel.end.coerceIn(start, e.text.length)
        if (end == start) end = e.text.length // nothing selected: pin to the whole line
        val quote = e.text.substring(start, end).ifEmpty { e.text }
        val me = c.displayName()
        val comment = com.amit.pageone.core.Comment(
            java.util.UUID.randomUUID().toString(), e.id, start, end, quote, me, c.colorFor(me + scriptId), text.trim(), System.currentTimeMillis(),
            attachments = attachments,
        )
        updateExtras { it.copy(comments = it.comments + comment) }
    }

    fun replyToComment(id: String, text: String) {
        val c = AppGraph.collab
        val me = c.displayName()
        val r = com.amit.pageone.core.Reply(me, c.colorFor(me + scriptId), text.trim(), System.currentTimeMillis())
        updateExtras { x -> x.copy(comments = x.comments.map { if (it.id == id) it.copy(replies = it.replies + r) else it }) }
    }

    fun attachToComment(id: String, a: com.amit.pageone.core.Attachment) =
        updateExtras { x -> x.copy(comments = x.comments.map { if (it.id == id) it.copy(attachments = it.attachments + a) else it }) }

    fun resolveComment(id: String, resolved: Boolean) =
        updateExtras { x -> x.copy(comments = x.comments.map { if (it.id == id) it.copy(resolved = resolved) else it }) }

    fun deleteComment(id: String) = updateExtras { x -> x.copy(comments = x.comments.filter { it.id != id }) }

    /** Freeze scene numbers and page breaks, as for a production draft. */
    fun lockScript() {
        val p = _derived.value?.pagination ?: Paginator(settings.layout).paginate(elements)
        val lock = com.amit.pageone.core.Locking.lock(elements, p)
        updateExtras { it.copy(lock = lock) }
        _message.value = "Locked: ${lock.sceneNumbers.size} scene numbers and ${p.pageCount} pages are frozen"
    }

    fun unlockScript() {
        updateExtras { it.copy(lock = com.amit.pageone.core.LockState()) }
        _message.value = "Unlocked — scenes and pages renumber as you write"
    }

    /** Insert an idea from the inbox after the focused element (or at the end). */
    fun insertIdea(text: String, type0: ElementType, audio: String = "", durationMs: Long = 0) {
        val at = _focused.value?.let { indexOf(it) }?.takeIf { it >= 0 } ?: (elements.size - 1)
        // A voice idea goes in as a note-to-self line with the recording pinned to it.
        val type = if (audio.isNotEmpty() && type0 != ElementType.SCENE_HEADING) ElementType.NOTE else type0
        val label = text.ifBlank { if (audio.isNotEmpty()) "Voice idea" else "" }
        val els = if (type == ElementType.SCENE_HEADING) {
            val h = Element(ids.next(), ElementType.SCENE_HEADING, "", synopsis = label)
            listOf(h, Element(ids.next(), ElementType.ACTION, ""))
        } else listOf(Element(ids.next(), type, if (type.allCaps) label.uppercase() else label))
        mutate(Kind.STRUCTURE) { list -> list.toMutableList().also { it.addAll((at + 1).coerceIn(0, it.size), els) } }
        if (audio.isNotEmpty()) {
            val target = els.first()
            val att = com.amit.pageone.core.Attachment(audio, com.amit.pageone.core.AttachmentKind.AUDIO, "Voice idea", durationMs)
            addComment(Selection(target.id, 0, (if (type == ElementType.SCENE_HEADING) 0 else target.text.length)), "Voice idea from the inbox", listOf(att))
        }
        requestFocus(els.first().id, els.first().text.length)
    }

    // ------------------------------------------------------------------ file exchange

    suspend fun packForSending(): ByteArray? {
        val s = _script.value ?: return null
        flush()
        return AppGraph.collab.packForSending(s)
    }

    /** Merge a co-writer's .pageone file into this script (undoable). */
    fun mergeFromFile(remote: com.amit.pageone.core.Script, sender: String) = viewModelScope.launch {
        val s = _script.value ?: return@launch
        if (remote.id != s.id) {
            _message.value = "That file is a different script (“${remote.title}”). Open it from the library instead."
            return@launch
        }
        val base = AppGraph.collab.loadBase(s.id)
        val r = Merge.threeWay(base?.elements, s.elements, remote.elements, ids, sender.ifBlank { "Co-writer" })
        mutate(Kind.STRUCTURE) { r.elements }
        if (base != null && base.titlePage == s.titlePage && remote.titlePage != s.titlePage) setTitlePage(remote.titlePage)
        _script.value?.let { AppGraph.collab.saveBase(it) }
        _message.value = if (r.total == 0) "Already up to date with $sender"
        else "Merged from $sender: ${r.changed} changed, ${r.added} added, ${r.deleted} removed" +
            if (r.conflicts > 0) " · ${r.conflicts} conflict${if (r.conflicts > 1) "s" else ""} marked with notes" else ""
    }

    private fun rederive(immediate: Boolean = false) {
        deriveJob?.cancel()
        deriveJob = viewModelScope.launch {
            if (!immediate) delay(220)
            val els = elements
            val layout = settings.layout
            layoutKey = layout
            val lock = _script.value?.extras?.lock ?: com.amit.pageone.core.LockState()
            val d = withContext(Dispatchers.Default) {
                val p = Paginator(layout).paginate(els, com.amit.pageone.core.Locking.forcedBreaks(els, lock))
                val contdArr = Analysis.contd(els)
                val contd = HashMap<Long, Boolean>()
                els.forEachIndexed { i, e -> if (contdArr[i]) contd[e.id] = true }
                val scenes = Analysis.scenes(els)
                Derived(
                    pagination = p,
                    scenes = scenes,
                    contd = contd,
                    characters = Analysis.characterNames(els),
                    locations = Analysis.locations(els),
                    words = els.sumOf { if (it.type.printed) Words.count(it.text) else 0 },
                    sceneNumberById = com.amit.pageone.core.Locking.sceneLabels(els, lock),
                )
            }
            _derived.value = d
        }
    }

    private suspend fun save() {
        val s = _script.value ?: return
        if (!dirty) return
        _saveState.value = SaveState.SAVING
        dirty = false
        val pages = _derived.value?.pagination?.pageCount ?: 0
        store.save(s, pages)
        AppGraph.drive.onSaved(s)
        AppGraph.links.onSaved(s)
        val mins = settings.autoVersionMinutes
        if (mins > 0 && System.currentTimeMillis() - lastVersionAt > mins * 60_000L) {
            lastVersionAt = System.currentTimeMillis()
            store.saveVersion(s, "Auto-save", pages, settings.versionsToKeep)
        }
        _saveState.value = if (dirty) SaveState.PENDING else SaveState.SAVED
    }

    /** Save right now on an app-wide scope (survives the ViewModel being cleared). */
    fun flush() {
        val s = _script.value ?: return
        if (!dirty) return
        dirty = false
        val pages = _derived.value?.pagination?.pageCount ?: 0
        AppGraph.appScope.launch { store.save(s, pages) }
        AppGraph.drive.onSaved(s)
        AppGraph.links.onSaved(s)
        _saveState.value = SaveState.SAVED
    }

    // ------------------------------------------------------------------ typing

    fun updateText(id: Long, text: String, cursor: Int) {
        lastCursor = cursor
        val i = indexOf(id); if (i < 0) return
        val e = elements[i]
        if (e.text == text) return
        var type = e.type
        if (settings.smartTypeDetection) Formatting.detectWhileTyping(type, text)?.let { type = it }
        val newText = if (type.allCaps) text.uppercase() else text
        mutate(Kind.TYPING, id) { list -> list.toMutableList().also { it[i] = e.copy(text = newText, type = type, orig = null) } }
    }

    /** Enter pressed: [before] stays in the element, [after] moves to a new one. */
    fun enter(id: Long, before: String, after: String) {
        val i = indexOf(id); if (i < 0) return
        val e = elements[i]

        // Empty element: Enter changes its type instead of adding blank rows.
        if (before.isEmpty() && after.isEmpty()) {
            val t = Formatting.onEnterWhenEmpty(e.type)
            if (t != null) { setType(id, t); return }
        }
        // Cursor at start of a non-empty element: open an empty line above.
        if (before.isEmpty() && after.isNotEmpty()) {
            val n = Element(ids.next(), e.type, "")
            mutate(Kind.STRUCTURE) { list -> list.toMutableList().also { it.add(i, n) } }
            requestFocus(e.id, 0)
            return
        }

        var curType = e.type
        var curText = before.trimEnd()
        if (settings.smartTypeDetection) Formatting.detectOnEnter(curType, curText)?.let { curType = it; curText = curText.uppercase() }
        if (curType == ElementType.PARENTHETICAL && settings.autoParentheses) curText = Formatting.normalizeParenthetical(curText)
        if (curType == ElementType.PARENTHETICAL && curText == "()") curText = ""

        var nextType = Formatting.nextOnEnter(curType)
        var nextText = after.trimStart()
        val nextOrig = if (nextType.allCaps && nextText != nextText.uppercase()) nextText else null
        if (nextType.allCaps) nextText = nextText.uppercase()
        val focusAt: Int
        if (nextType == ElementType.PARENTHETICAL && nextText.isEmpty() && settings.autoParentheses) { nextText = "()"; focusAt = 1 } else focusAt = 0
        if (nextType == ElementType.SCENE_HEADING && curType == ElementType.TRANSITION) nextType = ElementType.SCENE_HEADING

        val n = Element(ids.next(), nextType, nextText, orig = nextOrig)
        mutate(Kind.STRUCTURE) { list ->
            list.toMutableList().also {
                it[i] = e.copy(text = curText, type = curType, orig = null)
                it.add(i + 1, n)
            }
        }
        requestFocus(n.id, focusAt)
    }

    /** Multi-line paste: first line stays, the rest is parsed as Fountain. */
    fun pasteLines(id: Long, fullText: String) {
        val i = indexOf(id); if (i < 0) return
        val e = elements[i]
        val lines = fullText.split('\n')
        val first = lines.first()
        val rest = lines.drop(1).joinToString("\n")
        val parsed = Fountain.parse("\n" + rest, ids).elements
        mutate(Kind.STRUCTURE) { list ->
            list.toMutableList().also {
                it[i] = e.copy(text = if (e.type.allCaps) first.uppercase() else first)
                it.addAll(i + 1, parsed)
            }
        }
        (parsed.lastOrNull() ?: e).let { requestFocus(it.id, it.text.length) }
    }

    /** Backspace with the cursor at position 0. Returns true if handled. */
    fun backspaceAtStart(id: Long): Boolean {
        val i = indexOf(id); if (i < 0) return false
        val e = elements[i]
        if (e.text.isEmpty() || (e.type == ElementType.PARENTHETICAL && e.text == "()")) {
            val demoted = Formatting.demote(e.type)
            if (demoted != null) {
                mutate(Kind.STRUCTURE) { list -> list.toMutableList().also { it[i] = e.copy(type = demoted, text = "", orig = null) } }
                requestFocus(id, 0)
                return true
            }
            if (i == 0) return true
            val prev = elements[i - 1]
            mutate(Kind.STRUCTURE) { list -> list.toMutableList().also { it.removeAt(i) } }
            requestFocus(prev.id, prev.text.length)
            return true
        }
        if (i == 0) return false
        // Merge into the previous element.
        val prev = elements[i - 1]
        val joinAt = prev.text.length
        val mergedPlain = prev.plain + e.plain
        val merged = if (prev.type.allCaps) mergedPlain.uppercase() else mergedPlain
        val mergedOrig = if (prev.type.allCaps && mergedPlain != merged) mergedPlain else null
        mutate(Kind.STRUCTURE) { list ->
            list.toMutableList().also { it[i - 1] = prev.copy(text = merged, orig = mergedOrig); it.removeAt(i) }
        }
        requestFocus(prev.id, joinAt)
        return true
    }

    fun setType(id: Long, type: ElementType) {
        val i = indexOf(id); if (i < 0) return
        val e = elements[i]
        if (e.type == type) return
        val re = e.retyped(type)
        var text = re.text
        var cursor = text.length
        if (type == ElementType.PARENTHETICAL && settings.autoParentheses) {
            if (text.isEmpty()) { text = "()"; cursor = 1 } else { text = Formatting.normalizeParenthetical(text); cursor = text.length - 1 }
        } else if (e.type == ElementType.PARENTHETICAL) {
            text = text.removePrefix("(").removeSuffix(")"); cursor = text.length
        }
        mutate(Kind.STRUCTURE) { list -> list.toMutableList().also { it[i] = re.copy(text = text, dual = if (type == ElementType.CHARACTER) e.dual else false, orig = if (text == re.text) re.orig else null) } }
        requestFocus(id, cursor)
    }

    fun cycleType(id: Long) {
        val e = elementById(id) ?: return
        setType(id, Formatting.nextOnTab(e.type))
    }

    /** Shift+Tab: step back through the Tab cycle. */
    fun cycleTypeBack(id: Long) {
        val e = elementById(id) ?: return
        val prev = ElementType.entries.firstOrNull { it != e.type && Formatting.nextOnTab(it) == e.type } ?: ElementType.ACTION
        setType(id, prev)
    }

    /** Ctrl+Enter: start a new scene after the one containing [id]. */
    fun insertSceneAfter(id: Long) {
        val i = indexOf(id); if (i < 0) return
        var end = i + 1
        while (end < elements.size && elements[end].type != ElementType.SCENE_HEADING) end++
        val h = Element(ids.next(), ElementType.SCENE_HEADING, "")
        mutate(Kind.STRUCTURE) { list -> list.toMutableList().also { it.add(end.coerceAtMost(it.size), h) } }
        requestFocus(h.id, 0)
    }

    fun applySuggestion(id: Long, newText: String) {
        val i = indexOf(id); if (i < 0) return
        val e = elements[i]
        mutate(Kind.STRUCTURE) { list -> list.toMutableList().also { it[i] = e.copy(text = newText) } }
        val cursor = if (newText.endsWith("()")) newText.length - 1 else newText.length
        requestFocus(id, cursor)
    }

    /** Toggle dual dialogue on the dialogue block containing [id]. */
    fun toggleDual(id: Long): Boolean {
        val i = indexOf(id); if (i < 0) return false
        var c = i
        while (c >= 0 && elements[c].type != ElementType.CHARACTER) {
            if (!elements[c].type.isDialogueBlockPart) return false
            c--
        }
        if (c < 0) return false
        // Needs a dialogue block immediately before.
        var p = c - 1
        while (p >= 0 && (elements[p].type == ElementType.DIALOGUE || elements[p].type == ElementType.PARENTHETICAL || elements[p].type == ElementType.LYRICS)) p--
        val hasPartner = p >= 0 && elements[p].type == ElementType.CHARACTER
        val e = elements[c]
        if (!e.dual && !hasPartner) return false
        mutate(Kind.STRUCTURE) { list -> list.toMutableList().also { it[c] = e.copy(dual = !e.dual) } }
        requestFocus(id, elementById(id)?.text?.length ?: 0)
        return true
    }

    fun insertNote(afterId: Long) {
        val i = indexOf(afterId); if (i < 0) return
        val n = Element(ids.next(), ElementType.NOTE, "")
        mutate(Kind.STRUCTURE) { list -> list.toMutableList().also { it.add(i + 1, n) } }
        requestFocus(n.id, 0)
    }

    fun deleteElement(id: Long) {
        val i = indexOf(id); if (i < 0) return
        mutate(Kind.STRUCTURE) { list -> list.toMutableList().also { it.removeAt(i) } }
        elements.getOrNull((i - 1).coerceAtLeast(0))?.let { requestFocus(it.id, it.text.length) }
    }

    fun appendNewElement() {
        val last = elements.lastOrNull()
        if (last != null && last.text.isEmpty()) { requestFocus(last.id, 0); return }
        val t = last?.let { Formatting.nextOnEnter(it.type) } ?: ElementType.SCENE_HEADING
        val n = Element(ids.next(), t, "")
        mutate(Kind.STRUCTURE) { it + n }
        requestFocus(n.id, 0)
    }

    // ------------------------------------------------------------------ suggestions

    fun suggestionsFor(e: Element?, d: Derived?): List<Suggestion> {
        if (e == null) return emptyList()
        val st = settings
        val t = e.text
        val up = t.uppercase()
        return when (e.type) {
            ElementType.CHARACTER -> {
                if (!st.characterAutocomplete) return emptyList()
                val names = d?.characters.orEmpty()
                val base = Formatting.characterName(t)
                if (base.isNotEmpty() && names.contains(base) && !t.contains("(")) {
                    Formatting.extensions.map { Suggestion(it, "$base $it") }
                } else {
                    // Put the speaker before last first: in a two-hander it's who talks next.
                    val i = indexOf(e.id)
                    val recent = ArrayList<String>()
                    var k = i - 1
                    while (k >= 0 && recent.size < 3 && elements[k].type != ElementType.SCENE_HEADING) {
                        if (elements[k].type == ElementType.CHARACTER) {
                            val n = Formatting.characterName(elements[k].text)
                            if (n.isNotEmpty() && n !in recent) recent.add(n)
                        }
                        k--
                    }
                    val ordered = (recent.drop(1) + recent.take(1) + names).distinct()
                    ordered.filter { it.startsWith(up) && it != up }.take(8).map { Suggestion(it, it) }
                }
            }
            ElementType.SCENE_HEADING -> {
                if (!st.sceneAutocomplete) return emptyList()
                val prefix = Formatting.scenePrefixes.firstOrNull { up.startsWith(it) }
                when {
                    prefix == null -> Formatting.scenePrefixes.filter { it.startsWith(up) }.map { Suggestion(it.trim(), it) }
                    up.contains(" - ") -> {
                        val head = up.substringBeforeLast(" - ")
                        val tod = up.substringAfterLast(" - ")
                        Formatting.timesOfDay.filter { it.startsWith(tod) && it != tod }.map { Suggestion(it, "$head - $it") }
                    }
                    else -> {
                        val loc = up.removePrefix(prefix)
                        val locs = d?.locations.orEmpty().filter { it.startsWith(loc) && it != loc }.take(8)
                            .map { Suggestion(it, "$prefix$it - ") }
                        if (loc.isNotEmpty() && locs.isEmpty()) listOf(Suggestion("– DAY", "$up - DAY"), Suggestion("– NIGHT", "$up - NIGHT"))
                        else locs
                    }
                }
            }
            ElementType.TRANSITION -> Formatting.transitions.filter { it.startsWith(up) && it != up }.map { Suggestion(it, it) }
            ElementType.PARENTHETICAL -> {
                val inner = t.removePrefix("(").removeSuffix(")")
                if (inner.isEmpty()) Formatting.parentheticals.map { Suggestion(it, if (it.endsWith(")")) it else "$it)") }
                else emptyList()
            }
            else -> emptyList()
        }
    }

    // ------------------------------------------------------------------ undo

    fun undo() {
        val s = _script.value ?: return
        val snap = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(Snap(s.elements, _focused.value, lastCursor))
        lastTypingId = null
        _script.value = s.copy(elements = snap.elements, modifiedAt = System.currentTimeMillis())
        _canUndo.value = undoStack.isNotEmpty(); _canRedo.value = redoStack.isNotEmpty()
        afterChange()
        snap.focus?.let { f -> elementById(f)?.let { requestFocus(it.id, snap.cursor.coerceAtMost(it.text.length)) } }
    }

    fun redo() {
        val s = _script.value ?: return
        val snap = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(Snap(s.elements, _focused.value, lastCursor))
        lastTypingId = null
        _script.value = s.copy(elements = snap.elements, modifiedAt = System.currentTimeMillis())
        _canUndo.value = undoStack.isNotEmpty(); _canRedo.value = redoStack.isNotEmpty()
        afterChange()
        snap.focus?.let { f -> elementById(f)?.let { requestFocus(it.id, snap.cursor.coerceAtMost(it.text.length)) } }
    }

    // ------------------------------------------------------------------ scenes / cards

    fun updateScene(headingId: Long, heading: String? = null, synopsis: String? = null, color: Int? = null) {
        val i = indexOf(headingId); if (i < 0) return
        val e = elements[i]
        mutate(Kind.STRUCTURE) { list ->
            list.toMutableList().also {
                it[i] = e.copy(
                    text = heading?.uppercase() ?: e.text,
                    synopsis = synopsis ?: e.synopsis,
                    color = color ?: e.color,
                )
            }
        }
    }

    /** Reorder whole scenes. [order] is the list of scene-heading ids in their new order. */
    fun reorderScenes(order: List<Long>) {
        val els = elements
        val heads = els.indices.filter { els[it].type == ElementType.SCENE_HEADING }
        if (heads.isEmpty()) return
        val prelude = els.subList(0, heads.first())
        val blocks = HashMap<Long, List<Element>>()
        heads.forEachIndexed { n, start ->
            val end = if (n + 1 < heads.size) heads[n + 1] else els.size
            blocks[els[start].id] = els.subList(start, end)
        }
        val rebuilt = ArrayList<Element>(els.size)
        rebuilt.addAll(prelude)
        order.forEach { blocks.remove(it)?.let(rebuilt::addAll) }
        blocks.values.forEach(rebuilt::addAll) // anything not mentioned keeps its place at the end
        if (rebuilt.map { it.id } == els.map { it.id }) return
        mutate(Kind.STRUCTURE) { rebuilt }
    }

    fun addScene(heading: String = "", synopsis: String = ""): Long {
        val h = Element(ids.next(), ElementType.SCENE_HEADING, heading.uppercase(), synopsis = synopsis)
        val a = Element(ids.next(), ElementType.ACTION, "")
        mutate(Kind.STRUCTURE) { it + h + a }
        return h.id
    }

    fun deleteScene(headingId: Long) {
        // Locked scripts keep the number: the scene becomes "OMITTED".
        if (_script.value?.extras?.lock?.locked == true && headingId in (_script.value?.extras?.lock?.sceneNumbers ?: emptyMap())) {
            mutate(Kind.STRUCTURE) { com.amit.pageone.core.Locking.omitScene(it, headingId) }
            _message.value = "Scene marked OMITTED — its number stays locked"
            return
        }
        val sc = _derived.value?.scenes?.firstOrNull { it.elementId == headingId } ?: Analysis.scenes(elements).firstOrNull { it.elementId == headingId } ?: return
        val start = indexOf(headingId); if (start < 0) return
        val end = start + (sc.endIndex - sc.elementIndex)
        mutate(Kind.STRUCTURE) { list -> list.toMutableList().also { it.subList(start, end.coerceAtMost(it.size)).clear() } }
    }

    // ------------------------------------------------------------------ script doctor

    /** Apply one Script Doctor fix as a single undoable edit. */
    fun applyFix(fix: com.amit.pageone.core.Fix) {
        mutate(Kind.STRUCTURE) { fix.apply(it, ids::next) }
    }

    /** Apply every batch-safe fix in [issues] as a single undoable edit. */
    fun applyBatchFixes(issues: List<com.amit.pageone.core.Issue>) {
        mutate(Kind.STRUCTURE) { com.amit.pageone.core.ScriptDoctor.fixAll(it, issues, ids::next) }
    }

    fun jumpTo(id: Long) {
        val e = elementById(id) ?: return
        requestFocus(e.id, e.text.length)
    }

    // ------------------------------------------------------------------ script-level

    fun rename(title: String) {
        val s = _script.value ?: return
        _script.value = s.copy(title = title, titlePage = s.titlePage.copy(title = title), modifiedAt = System.currentTimeMillis())
        afterChange()
    }

    fun setTitlePage(tp: TitlePage) {
        val s = _script.value ?: return
        _script.value = s.copy(titlePage = tp, title = tp.title.ifBlank { s.title }, modifiedAt = System.currentTimeMillis())
        afterChange()
    }

    fun setRevision(mode: Boolean, level: Int) {
        val s = _script.value ?: return
        val lv = level.coerceIn(1, 8)
        // Issuing a revision set stamps it with today's date (kept if it already has one).
        val dates = if (mode && lv !in s.revisionDates)
            s.revisionDates + (lv to java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()))
        else s.revisionDates
        _script.value = s.copy(revisionMode = mode, revisionLevel = lv, revisionDates = dates)
        afterChange()
    }

    fun clearRevisionMarks() {
        mutate(Kind.STRUCTURE) { list -> list.map { if (it.revision != 0) it.copy(revision = 0) else it } }
    }

    // ------------------------------------------------------------------ find & replace

    fun openFind(open: Boolean) {
        _find.value = if (open) _find.value.copy(open = true) else FindState()
    }

    fun setFindQuery(q: String, matchCase: Boolean = _find.value.matchCase) {
        _find.value = _find.value.copy(query = q, matchCase = matchCase)
        refreshMatches(keepIndex = false)
    }

    fun setReplace(r: String) { _find.value = _find.value.copy(replace = r) }

    private fun refreshMatches(keepIndex: Boolean) {
        val f = _find.value
        if (f.query.isEmpty()) { _find.value = f.copy(matches = emptyList(), current = -1); return }
        val out = ArrayList<Pair<Long, Int>>()
        for (e in elements) {
            var from = 0
            while (true) {
                val k = e.text.indexOf(f.query, from, ignoreCase = !f.matchCase)
                if (k < 0) break
                out.add(e.id to k); from = k + f.query.length.coerceAtLeast(1)
            }
        }
        val cur = if (keepIndex) f.current.coerceAtMost(out.size - 1) else if (out.isEmpty()) -1 else 0
        _find.value = f.copy(matches = out, current = cur)
        if (!keepIndex) selectCurrent()
    }

    fun findNext(dir: Int) {
        val f = _find.value
        if (f.matches.isEmpty()) return
        _find.value = f.copy(current = (f.current + dir + f.matches.size) % f.matches.size)
        selectCurrent()
    }

    private fun selectCurrent() {
        val f = _find.value
        val m = f.matches.getOrNull(f.current) ?: return
        // Scroll to the match and highlight it, but leave the keyboard in the Find box.
        requestFocus(m.first, m.second, m.second + f.query.length, focus = false)
    }

    fun replaceCurrent() {
        val f = _find.value
        val m = f.matches.getOrNull(f.current) ?: return
        val i = indexOf(m.first); if (i < 0) return
        val e = elements[i]
        if (m.second + f.query.length > e.text.length) return
        val t = e.text.substring(0, m.second) + f.replace + e.text.substring(m.second + f.query.length)
        mutate(Kind.STRUCTURE) { list -> list.toMutableList().also { it[i] = e.copy(text = if (e.type.allCaps) t.uppercase() else t) } }
        refreshMatches(keepIndex = true)
        selectCurrent()
    }

    fun replaceAll(): Int {
        val f = _find.value
        if (f.query.isEmpty()) return 0
        var count = 0
        val re = Regex(Regex.escape(f.query), if (f.matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE))
        mutate(Kind.STRUCTURE) { list ->
            list.map { e ->
                val n = re.findAll(e.text).count()
                if (n == 0) e else {
                    count += n
                    val t = re.replace(e.text, Regex.escapeReplacement(f.replace))
                    e.copy(text = if (e.type.allCaps) t.uppercase() else t)
                }
            }
        }
        refreshMatches(keepIndex = false)
        return count
    }

    // ------------------------------------------------------------------ versions

    fun loadVersions() = viewModelScope.launch { _versions.value = store.versions(scriptId) }

    fun saveVersion(name: String) = viewModelScope.launch {
        val s = _script.value ?: return@launch
        store.saveVersion(s, name.ifBlank { "Snapshot" }, _derived.value?.pagination?.pageCount ?: 0, settings.versionsToKeep)
        lastVersionAt = System.currentTimeMillis()
        _versions.value = store.versions(scriptId)
    }

    fun restoreVersion(v: VersionInfo) = viewModelScope.launch {
        val cur = _script.value ?: return@launch
        val old = store.loadVersion(v) ?: return@launch
        store.saveVersion(cur, "Before restore", _derived.value?.pagination?.pageCount ?: 0, settings.versionsToKeep)
        ids.ensureAbove(old.nextId)
        mutate(Kind.STRUCTURE) { old.elements }
        _script.value = _script.value?.copy(titlePage = old.titlePage)
        _versions.value = store.versions(scriptId)
    }

    fun deleteVersion(v: VersionInfo) = viewModelScope.launch {
        store.deleteVersion(v)
        _versions.value = store.versions(scriptId)
    }
}
