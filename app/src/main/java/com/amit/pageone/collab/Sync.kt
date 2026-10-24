package com.amit.pageone.collab

import android.content.Context
import com.amit.pageone.core.Comment
import com.amit.pageone.core.Element
import com.amit.pageone.core.ElementType
import com.amit.pageone.core.Ordering
import com.amit.pageone.core.Script
import com.amit.pageone.core.ScriptExtras
import com.amit.pageone.core.TitlePage
import com.amit.pageone.data.Accent
import com.amit.pageone.data.AppGraph
import com.amit.pageone.data.ExtrasCodec
import com.amit.pageone.data.ScriptMeta
import com.amit.pageone.data.ScriptStore
import com.amit.pageone.data.SprintRecord
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Phone and computer, one writer: projects, daily words, streak, sprints, name and goal follow you
 * between devices. The desktop app's `collab/sync.ts` and `state/sync.ts` are the same thing.
 *
 * Both devices join one private "sync space" on the live-sync server, found with an 8-letter pairing
 * code (the same invite mechanism as join codes), and keep these in it:
 *
 *   scripts/sync-{space}                      pf: your name, daily goal and accent (json)
 *   scripts/sync-{space}/lib/{scriptId}       one line per project: title, pages, words, when it last changed
 *   scripts/sync-{space}/days/{yyyy-MM-dd}    words written that day, per device: d.{deviceId}
 *   scripts/sync-{space}/sprints/{startAt}    finished writing sprints
 *
 * The projects' own lines live where live co-writing keeps them (scripts/{scriptId}/elements…), so an
 * open project is live between your devices like a shared one (see CollabStore.isLive), and the
 * library entry says which projects exist and which copy is newer. Nothing here needs a Firestore
 * rule beyond the existing `scripts` ones.
 */
class SyncSpace(private val context: Context) {

    enum class Phase { OFF, CONNECTING, SYNCED, WORKING, ERROR }
    data class State(val space: String?, val phase: Phase, val message: String = "", val lastAt: Long = 0L)

    private data class Entry(
        val id: String, val title: String, val author: String, val modifiedAt: Long, val createdAt: Long, val pages: Int,
        val scenes: Int, val words: Int, val coverColor: Int, val logline: String, val deleted: Boolean,
    )

    private class Remote(val elements: List<Element>, val title: String, val tp: TitlePage?, val extras: ScriptExtras?, val comments: List<Comment>)

    private val p = context.getSharedPreferences("pageone_sync", Context.MODE_PRIVATE)
    private val scope get() = AppGraph.appScope
    val device: String = p.getString("device", null) ?: UUID.randomUUID().toString().take(8).also { p.edit().putString("device", it).apply() }

    private val _state = MutableStateFlow(State(p.getString("space", null), if (p.getString("space", null) != null) Phase.CONNECTING else Phase.OFF))
    val state: StateFlow<State> = _state.asStateFlow()

    /** With sync on, every project is live between your own devices. */
    val active: Boolean get() = _state.value.space != null

    private val regs = ArrayList<ListenerRegistration>()
    @Volatile private var remote: Map<String, Entry> = emptyMap()
    private val lock = Mutex()
    private val applying = ConcurrentHashMap.newKeySet<String>()
    private val timers = HashMap<String, Job>()
    private var prefsJob: Job? = null
    private var prefsSeen = false

    private val cfg get() = AppGraph.collab.server.value
    private val db get() = Hub.firestore(context, cfg)
    private fun spaceRef(space: String) = db.collection("scripts").document(space)

    private fun set(f: (State) -> State) { _state.value = f(_state.value) }
    private fun fail(e: Throwable) = set { it.copy(phase = Phase.ERROR, message = e.message ?: "Couldn't reach the sync server") }

    /** Hook everything up; called once the app's stores exist. */
    fun init() {
        AppGraph.store.onSaved = { m -> saved(m) }
        AppGraph.goals.onDay = { day, words -> dayChanged(day, words) }
        AppGraph.sprints.onRecorded = { r -> scope.launch { runCatching { putSprint(r) } } }
        AppGraph.settings.onPrefsChanged = { prefsChanged() }
        AppGraph.collab.onNameChanged = { prefsChanged() }
        if (active) scope.launch { delay(1200); start() }
    }

    // ------------------------------------------------------------------ pairing

    /** A code to type on the other device. Makes the sync space the first time. */
    suspend fun link(): String {
        val c = cfg
        check(c.isValid) { "Sync needs the live-sync server, which this copy of Page One was built without." }
        Hub.uid(context, c)
        if (_state.value.space == null) {
            val sp = newSpaceId()
            spaceRef(sp).set(mapOf("sync" to true, "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge()).await()
            p.edit().putString("space", sp).apply()
            set { it.copy(space = sp, phase = Phase.CONNECTING, message = "") }
            start()
        }
        return Hub.pretty(Hub.codeFor(context, c, _state.value.space!!, "Page One sync", AppGraph.collab.displayName()))
    }

    /** Join the space a pairing code points at. */
    suspend fun adopt(space: String) {
        val c = cfg
        check(c.isValid) { "Sync needs the live-sync server, which this copy of Page One was built without." }
        Hub.uid(context, c)
        val exists = runCatching { spaceRef(space).get().await().exists() }.getOrDefault(true)
        check(exists) { "That sync code doesn't point anywhere any more. Make a new one on your other device." }
        if (_state.value.space != null && _state.value.space != space) stop()
        p.edit().putString("space", space).apply()
        set { it.copy(space = space, phase = Phase.CONNECTING, message = "") }
        start()
    }

    fun unlink() {
        stop()
        p.edit().remove("space").apply()
        AppGraph.goals.setOthers(emptyMap())
        set { State(null, Phase.OFF) }
    }

    /** Run a pass now (the editor closed, the app came back): catches up on what was skipped while a script was open. */
    fun kick() { if (active && remote.isNotEmpty()) scope.launch { reconcileSafely() } }

    fun syncNow() { if (active) { remote = emptyMap(); stop(); start() } }

    private fun stop() {
        regs.forEach { it.remove() }; regs.clear()
        prefsSeen = false
        prefsJob?.cancel()
    }

    private fun newSpaceId(): String {
        val r = SecureRandom()
        return "sync-" + (1..12).joinToString("") { "%02x".format(r.nextInt(256)) }
    }

    // ------------------------------------------------------------------ listening

    private fun start() {
        stop()
        val sp = _state.value.space ?: return
        if (!cfg.isValid) return set { it.copy(phase = Phase.ERROR, message = "The live-sync server is not set up in this build.") }
        set { it.copy(phase = Phase.CONNECTING, message = "") }
        scope.launch {
            runCatching { Hub.uid(context, cfg) }.onFailure { return@launch fail(it) }
            val ref = spaceRef(sp)

            regs += ref.collection("lib").addSnapshotListener { snap, err ->
                if (err != null) return@addSnapshotListener fail(err)
                if (snap == null) return@addSnapshotListener
                remote = snap.documents.map { readEntry(it) }.associateBy { it.id }
                scope.launch { reconcileSafely() }
            }

            regs += ref.collection("days").addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                val others = HashMap<String, Int>()
                val seen = HashMap<String, Int>()
                for (d in snap.documents) {
                    @Suppress("UNCHECKED_CAST") val per = d.get("d") as? Map<String, Any?> ?: continue
                    var sum = 0
                    for ((dev, w) in per) { val n = (w as? Number)?.toInt() ?: 0; if (dev == device) seen[d.id] = n else sum += n }
                    if (sum > 0) others[d.id] = sum
                }
                AppGraph.goals.setOthers(others)
                // What this phone wrote that the server doesn't have yet (older days, or a first sync).
                scope.launch {
                    for ((day, w) in AppGraph.goals.ownDays()) if (w > 0 && seen[day] != w) runCatching { putDay(day, w) }
                }
            }

            regs += ref.collection("sprints").addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                val list = snap.documents.map { d ->
                    SprintRecord(d.getLong("at") ?: d.id.toLongOrNull() ?: 0L, (d.getLong("m") ?: 0L).toInt(), (d.getLong("g") ?: 0L).toInt(), (d.getLong("w") ?: 0L).toInt())
                }
                AppGraph.sprints.merge(list)
                val have = list.map { it.at }.toSet()
                scope.launch { for (r in AppGraph.sprints.history()) if (r.at !in have) runCatching { putSprint(r) } }
            }

            regs += ref.addSnapshotListener { snap, _ ->
                val j = snap?.getString("pf") ?: return@addSnapshotListener
                prefsSeen = true
                val o = runCatching { JSONObject(j) }.getOrNull() ?: return@addSnapshotListener
                val at = o.optLong("at")
                if (at <= p.getLong("prefsAt", 0L)) return@addSnapshotListener
                p.edit().putLong("prefsAt", at).apply()
                applyingPrefs = true
                try {
                    val name = o.optString("name"); val goal = o.optInt("goal"); val accent = o.optString("accent")
                    if (name.isNotBlank() && name != AppGraph.collab.name.value) AppGraph.collab.setName(name)
                    AppGraph.settings.update { s ->
                        var n = s
                        if (goal > 0) n = n.copy(dailyGoal = goal)
                        Accent.entries.firstOrNull { it.label.equals(accent, true) }?.let { n = n.copy(accent = it) }
                        n
                    }
                } finally { applyingPrefs = false }
            }
            // Nothing on the server yet (the first device to sync): it gets this phone's name, goal and accent.
            prefsJob = scope.launch { delay(4000); if (!prefsSeen) pushPrefs() }
        }
    }

    private fun readEntry(d: DocumentSnapshot) = Entry(
        d.id, d.getString("t") ?: "", d.getString("a") ?: "", d.getLong("m") ?: 0L, d.getLong("cr") ?: 0L,
        (d.getLong("p") ?: 0L).toInt(), (d.getLong("sc") ?: 0L).toInt(), (d.getLong("w") ?: 0L).toInt(), (d.getLong("c") ?: 0L).toInt(),
        d.getString("l") ?: "", d.getBoolean("d") ?: false,
    )

    // ------------------------------------------------------------------ writing the small things

    @Volatile private var applyingPrefs = false

    private fun prefsChanged() {
        if (!active || applyingPrefs) return
        prefsJob?.cancel()
        prefsJob = scope.launch { delay(1200); pushPrefs() }
    }

    private fun pushPrefs() {
        val sp = _state.value.space ?: return
        val s = AppGraph.settings.state.value
        val at = System.currentTimeMillis()
        p.edit().putLong("prefsAt", at).apply()
        val o = JSONObject().put("name", AppGraph.collab.displayName().takeIf { AppGraph.collab.name.value.isNotBlank() || it != "Writer" } ?: "")
            .put("goal", s.dailyGoal).put("accent", s.accent.label.lowercase()).put("at", at)
        spaceRef(sp).set(mapOf("pf" to o.toString()), SetOptions.merge())
    }

    private val dayJobs = HashMap<String, Job>()
    private fun dayChanged(day: String, words: Int) {
        if (!active) return
        dayJobs.remove(day)?.cancel()
        dayJobs[day] = scope.launch { delay(2500); runCatching { putDay(day, words) } }
    }

    private suspend fun putDay(day: String, words: Int) {
        val sp = _state.value.space ?: return
        spaceRef(sp).collection("days").document(day).set(mapOf("d" to mapOf(device to words)), SetOptions.merge()).await()
    }

    private suspend fun putSprint(r: SprintRecord) {
        val sp = _state.value.space ?: return
        spaceRef(sp).collection("sprints").document(r.at.toString()).set(mapOf("at" to r.at, "m" to r.minutes, "w" to r.words, "g" to r.goal, "s" to "")).await()
    }

    private suspend fun putEntry(m: ScriptMeta) {
        val sp = _state.value.space ?: return
        spaceRef(sp).collection("lib").document(m.id).set(mapOf(
            "t" to m.title, "a" to m.author, "m" to m.modifiedAt, "cr" to m.createdAt, "p" to m.pages, "sc" to m.scenes,
            "w" to m.words, "c" to m.coverColor, "l" to m.logline, "d" to false,
        )).await()
    }

    // ------------------------------------------------------------------ hooks from the library

    private fun saved(m: ScriptMeta) {
        if (!active || m.id in applying) return
        val r = remote[m.id]
        if (r != null && !r.deleted && r.modifiedAt == m.modifiedAt) return
        timers.remove(m.id)?.cancel()
        timers[m.id] = scope.launch { delay(1500); timers.remove(m.id); runCatching { putEntry(m) } }
    }

    /** The writer deleted a project from the library: tell the other device. (Nothing else ever does.) */
    fun userDeleted(id: String) {
        val sp = _state.value.space ?: return
        if (id in applying) return
        timers.remove(id)?.cancel()
        if (remote.containsKey(id)) scope.launch {
            runCatching {
                spaceRef(sp).collection("lib").document(id).set(mapOf(
                    "t" to "", "a" to "", "m" to System.currentTimeMillis(), "cr" to 0L, "p" to 0, "sc" to 0, "w" to 0, "c" to 0, "l" to "", "d" to true,
                )).await()
            }
        }
    }

    // ------------------------------------------------------------------ projects

    private suspend fun reconcileSafely() {
        lock.withLock {
            runCatching { reconcile() }.onFailure { fail(it) }
        }
    }

    /** Bring this phone and the shared library level, newest copy winning. */
    private suspend fun reconcile() {
        if (!active || !cfg.isValid) return
        val store = AppGraph.store
        store.refresh()
        val openId = AppGraph.openScriptId
        val local = store.library.value.associateBy { it.id }
        val todo = ArrayList<Triple<String, Entry?, ScriptMeta?>>() // kind, remote, local
        for (r in remote.values) {
            val l = local[r.id]
            if (r.deleted) {
                if (l != null && r.id != openId && r.modifiedAt >= l.modifiedAt) todo += Triple("gone", r, l)
                else if (l != null && l.modifiedAt > r.modifiedAt) todo += Triple("push", null, l)
                continue
            }
            if (l == null) todo += Triple("pull", r, null)
            else if (r.id == openId) { if (l.modifiedAt > r.modifiedAt + 1500) todo += Triple("entry", null, l) }
            else if (r.modifiedAt > l.modifiedAt + 1500) todo += Triple("pull", r, l)
            else if (l.modifiedAt > r.modifiedAt + 1500) todo += Triple("push", null, l)
        }
        for (l in local.values) if (!remote.containsKey(l.id)) todo += Triple("push", null, l)
        // Copies brought in by an earlier build were counted as one page; count them properly.
        for (l in local.values) if (l.pages <= 1 && l.scenes > 2 && l.id != openId && todo.none { it.third?.id == l.id || it.second?.id == l.id }) todo += Triple("pages", null, l)

        if (todo.isNotEmpty()) set { it.copy(phase = Phase.WORKING, message = "Syncing ${todo.size} project${if (todo.size == 1) "" else "s"}…") }
        for ((i, t) in todo.withIndex()) {
            if (todo.size > 1) set { it.copy(message = "Syncing project ${i + 1} of ${todo.size}…") }
            runCatching {
                when (t.first) {
                    "pull" -> pull(t.second!!)
                    "gone" -> { applying += t.third!!.id; try { store.delete(t.third!!.id) } finally { applying -= t.third!!.id } }
                    "push" -> pushWhole(t.third!!)
                    "pages" -> recount(t.third!!)
                    else -> putEntry(t.third!!)
                }
            }
        }
        set { it.copy(phase = Phase.SYNCED, message = "", lastAt = System.currentTimeMillis()) }
    }

    private suspend fun fetchScript(id: String): Remote? {
        Hub.uid(context, cfg)
        val ref = db.collection("scripts").document(id)
        val els = ref.collection("elements").get().await()
        val head = ref.get().await()
        val notes = ref.collection("comments").get().await()
        val elements = ArrayList<Element>(); val order = HashMap<Long, Double>()
        for (d in els.documents) {
            val eid = d.id.toLongOrNull() ?: continue
            elements += Element(
                eid, ElementType.fromName(d.getString("t")), d.getString("x") ?: "", d.getBoolean("d") ?: false,
                (d.getLong("r") ?: 0L).toInt(), (d.getLong("c") ?: 0L).toInt(), d.getString("s") ?: "",
            )
            order[eid] = d.getDouble("o") ?: 0.0
        }
        if (elements.isEmpty()) return null
        elements.sortWith(compareBy<Element> { order[it.id] ?: 0.0 }.thenBy { it.id })
        val title = head.getString("title") ?: ""
        val tp = head.getString("tp")?.let { j -> runCatching { ScriptStore.decodeTitlePage(JSONObject(j), title) }.getOrNull() }
        val extras = head.getString("ex")?.let { j -> runCatching { ExtrasCodec.decode(JSONObject(j)) }.getOrNull() }
        val comments = notes.documents.mapNotNull { d -> d.getString("j")?.let { j -> runCatching { ExtrasCodec.decodeComment(JSONObject(j)) }.getOrNull() } }.sortedBy { it.at }
        return Remote(elements, title, tp, extras, comments)
    }

    /** Take the shared copy of a project this phone doesn't have, or has an older copy of. */
    private suspend fun pull(r: Entry) {
        val data = fetchScript(r.id) ?: return // the other device is still uploading it; the next change brings it
        applying += r.id
        try {
            val store = AppGraph.store
            val local = store.load(r.id)
            val title = data.title.ifBlank { r.title }
            val tp = data.tp ?: local?.titlePage ?: TitlePage(title = title)
            val s = if (local != null) {
                val ex = data.extras?.let { ExtrasCodec.mergeRemote(local.extras, it) } ?: local.extras
                local.copy(title = title, titlePage = tp, elements = data.elements, extras = ex.copy(comments = data.comments), coverColor = if (r.coverColor != 0) r.coverColor else local.coverColor, modifiedAt = r.modifiedAt)
            } else {
                Script(
                    id = r.id, title = title, elements = data.elements, titlePage = tp, createdAt = if (r.createdAt > 0) r.createdAt else System.currentTimeMillis(),
                    modifiedAt = r.modifiedAt, coverColor = r.coverColor, extras = (data.extras ?: ScriptExtras()).copy(comments = data.comments),
                )
            }
            val pages = runCatching {
                com.amit.pageone.core.Paginator(AppGraph.settings.state.value.layout).paginate(s.elements, com.amit.pageone.core.Locking.forcedBreaks(s.elements, s.extras.lock)).pageCount
            }.getOrDefault(r.pages)
            store.save(s, pages)
        } finally { applying -= r.id }
    }

    private suspend fun recount(m: ScriptMeta) {
        val s = AppGraph.store.load(m.id) ?: return
        val pages = com.amit.pageone.core.Paginator(AppGraph.settings.state.value.layout).paginate(s.elements, com.amit.pageone.core.Locking.forcedBreaks(s.elements, s.extras.lock)).pageCount
        if (pages == m.pages) return
        applying += m.id
        try { AppGraph.store.save(s, pages) } finally { applying -= m.id }
    }

    private suspend fun pushWhole(m: ScriptMeta) {
        val s = AppGraph.store.load(m.id) ?: return
        uploadScript(s)
        putEntry(m)
    }

    /** Write a whole script as the shared copy: its lines, title page, story data and notes. */
    private suspend fun uploadScript(s: Script) {
        val me = Hub.uid(context, cfg)
        val ref = db.collection("scripts").document(s.id)
        val els = ref.collection("elements").get().await()
        val notes = ref.collection("comments").get().await()
        val known = HashMap<Long, Double>()
        for (d in els.documents) d.id.toLongOrNull()?.let { known[it] = d.getDouble("o") ?: 0.0 }
        val orders = Ordering.assign(s.elements.map { it.id }, known)
        val ops = ArrayList<(com.google.firebase.firestore.WriteBatch) -> Unit>()
        for (e in s.elements) {
            val data = hashMapOf<String, Any>(
                "t" to e.type.name, "x" to e.text, "d" to e.dual, "r" to e.revision.toLong(), "c" to e.color.toLong(), "s" to e.synopsis,
                "o" to orders.getValue(e.id), "by" to me, "at" to FieldValue.serverTimestamp(),
            )
            val doc = ref.collection("elements").document(e.id.toString())
            ops += { b -> b.set(doc, data) }
        }
        val ids = s.elements.map { it.id.toString() }.toSet()
        for (d in els.documents) if (d.id !in ids) ops += { b -> b.delete(d.reference) }
        val meta = mapOf(
            "members" to mapOf(me to AppGraph.collab.displayName()), "title" to s.title,
            "tp" to ScriptStore.encodeTitlePage(s.titlePage).toString(),
            "ex" to ExtrasCodec.encode(s.extras, includeComments = false, includeLocalMedia = false).toString(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        ops += { b -> b.set(ref, meta, SetOptions.merge()) }
        val have = s.extras.comments.map { it.id }.toSet()
        for (c in s.extras.comments) {
            val doc = ref.collection("comments").document(c.id)
            val data = mapOf("j" to ExtrasCodec.encodeComment(c).toString(), "at" to c.at)
            ops += { b -> b.set(doc, data) }
        }
        for (d in notes.documents) if (d.id !in have) ops += { b -> b.delete(d.reference) }
        ops.chunked(450).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { it(batch) }
            batch.commit().await()
        }
    }
}
