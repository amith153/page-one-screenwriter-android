package com.amit.pageone.collab

import android.content.Context
import com.amit.pageone.core.Element
import com.amit.pageone.core.ElementType
import com.amit.pageone.core.Ordering
import com.amit.pageone.core.TitlePage
import com.amit.pageone.data.ScriptStore
import com.amit.pageone.data.ServerConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

/** One entry in the change history: who changed which element, and how it read before. */
data class Change(
    val id: String,
    val elementId: Long,
    val uid: String,
    val name: String,
    val color: Long,
    val kind: String,      // "edit" | "add" | "delete" | "format"
    val before: String,
    val after: String,
    val at: Long,
)

data class Peer(val uid: String, val name: String, val color: Long, val elementId: Long?, val lastSeen: Long)

sealed class LiveStatus {
    data object Connecting : LiveStatus()
    data object Live : LiveStatus()
    data class Error(val message: String) : LiveStatus()
}

/**
 * Real-time co-editing over Cloud Firestore.
 *
 * Layout:
 *   scripts/{scriptId}                      title, title page (json), members
 *   scripts/{scriptId}/elements/{elementId} one document per screenplay element, with a
 *                                           fractional order key "o"
 *   scripts/{scriptId}/presence/{uid}       who is online and which element they're on
 *
 * Edits are merged per element (last writer wins on the same element), so two people
 * writing different scenes — or different lines of the same scene — never clash.
 * Firestore's offline cache queues edits made without signal and syncs them later.
 */
class LiveSession(
    private val context: Context,
    private val config: ServerConfig,
    private val scriptId: String,
    private val myName: String,
    private val myColor: Long,
    private val scope: CoroutineScope,
    private val hasLocalPending: () -> Boolean,
    private val onRemoteElements: (List<Element>) -> Unit,
    private val onRemoteMeta: (title: String, tp: TitlePage) -> Unit,
    /** Shared story data (bible, structure, threads, breakdown, lock, deadline) as JSON. */
    private val onRemoteExtras: (String) -> Unit = {},
    /** Anchored notes — one document each, so simultaneous comments never overwrite each other. */
    private val onRemoteComments: (List<com.amit.pageone.core.Comment>) -> Unit = {},
) {
    private val _status = MutableStateFlow<LiveStatus>(LiveStatus.Connecting)
    val status: StateFlow<LiveStatus> = _status.asStateFlow()
    private val _peers = MutableStateFlow<List<Peer>>(emptyList())
    val peers: StateFlow<List<Peer>> = _peers.asStateFlow()
    private val _changes = MutableStateFlow<List<Change>>(emptyList())
    val changes: StateFlow<List<Change>> = _changes.asStateFlow()

    /** Consecutive edits to one element within a couple of minutes become one history entry. */
    private data class Burst(val docId: String, val before: String, val kind: String, var lastAt: Long)
    private val bursts = HashMap<Long, Burst>()

    private var db: FirebaseFirestore? = null
    var uid: String? = null
        private set
    private val regs = ArrayList<ListenerRegistration>()
    private var heartbeat: Job? = null
    private var stopped = false

    // What the shared copy holds, as far as we know (last snapshot + our own writes).
    private val known = HashMap<Long, Element>()
    private val knownOrder = HashMap<Long, Double>()
    private var knownMeta: String? = null
    private var knownExtras: String? = null
    private val knownComments = HashMap<String, String>() // id → json
    private var ready = false
    private var cursor: Long? = null
    private var lastPresenceAt = 0L

    private fun scriptRef() = db!!.collection("scripts").document(scriptId)

    /** Connect. [onReady] gets whether the shared script already had content. */
    fun start(onReady: (remoteHadContent: Boolean) -> Unit) {
        try {
            val app = Hub.app(context, config)
            val auth = FirebaseAuth.getInstance(app)
            val firestore = FirebaseFirestore.getInstance(app)
            db = firestore
            val existing = auth.currentUser
            if (existing != null) {
                uid = existing.uid
                attach(onReady)
            } else {
                auth.signInAnonymously()
                    .addOnSuccessListener { res ->
                        uid = res.user?.uid
                        attach(onReady)
                    }
                    .addOnFailureListener { e -> fail("Couldn't start live co-writing. Check your connection and try again.") }
            }
        } catch (e: Exception) {
            fail("Couldn't connect: ${e.message}")
        }
    }

    private fun fail(msg: String) { _status.value = LiveStatus.Error(msg) }

    private fun attach(onReady: (Boolean) -> Unit) {
        if (stopped) return
        val me = uid ?: return fail("No user id")
        val ref = scriptRef()
        ref.set(mapOf("members" to mapOf(me to myName), "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
            .addOnFailureListener { e -> fail(permissionHint(e)) }

        // Wait (briefly) for the server's copy before deciding who is the source of truth.
        val readyTimer = scope.launch {
            delay(8000)
            if (!ready) {
                ready = true
                onReady(known.isNotEmpty())
            }
        }

        regs += ref.collection("elements").addSnapshotListener(MetadataChanges.INCLUDE) { snap, err ->
            if (err != null) { fail(permissionHint(err)); return@addSnapshotListener }
            if (snap == null) return@addSnapshotListener
            known.clear(); knownOrder.clear()
            for (d in snap.documents) {
                val e = parseElement(d) ?: continue
                known[e.id] = e
                knownOrder[e.id] = d.getDouble("o") ?: 0.0
            }
            if (!ready) {
                if (snap.metadata.isFromCache && known.isEmpty()) return@addSnapshotListener
                ready = true
                readyTimer.cancel()
                _status.value = LiveStatus.Live
                onReady(known.isNotEmpty())
            } else if (!snap.metadata.isFromCache) {
                _status.value = LiveStatus.Live
            }
            if (known.isNotEmpty() && !hasLocalPending()) onRemoteElements(currentRemote())
        }

        regs += ref.addSnapshotListener { snap, err ->
            if (err != null || snap == null || !snap.exists()) return@addSnapshotListener
            snap.getString("ex")?.let { ex -> if (ex != knownExtras) { knownExtras = ex; onRemoteExtras(ex) } }
            val tpJson = snap.getString("tp") ?: return@addSnapshotListener
            val title = snap.getString("title") ?: ""
            val key = "$title\u0000$tpJson"
            if (key == knownMeta) return@addSnapshotListener
            knownMeta = key
            runCatching { ScriptStore.decodeTitlePage(JSONObject(tpJson), title) }.getOrNull()?.let { onRemoteMeta(title, it) }
        }

        regs += ref.collection("changes")
            .orderBy("at", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(250)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                _changes.value = snap.documents.mapNotNull { d ->
                    Change(
                        id = d.id,
                        elementId = d.getLong("e") ?: return@mapNotNull null,
                        uid = d.getString("u") ?: "",
                        name = d.getString("n") ?: "Co-writer",
                        color = d.getLong("c") ?: 0xFF4D9DFF,
                        kind = d.getString("k") ?: "edit",
                        before = d.getString("b") ?: "",
                        after = d.getString("a") ?: "",
                        at = d.getLong("at") ?: 0L,
                    )
                }
            }

        regs += ref.collection("comments").addSnapshotListener { snap, err ->
            if (err != null || snap == null) return@addSnapshotListener
            knownComments.clear()
            val list = snap.documents.mapNotNull { d ->
                val json = d.getString("j") ?: return@mapNotNull null
                knownComments[d.id] = json
                runCatching { com.amit.pageone.data.ExtrasCodec.decodeComment(JSONObject(json)) }.getOrNull()
            }
            onRemoteComments(list.sortedBy { it.at })
        }

        regs += ref.collection("presence").addSnapshotListener { snap, err ->
            if (err != null || snap == null) return@addSnapshotListener
            val now = System.currentTimeMillis()
            _peers.value = snap.documents.mapNotNull { d ->
                if (d.id == me) return@mapNotNull null
                val at = d.getLong("at") ?: 0L
                if (now - at > 90_000) return@mapNotNull null
                Peer(d.id, d.getString("n") ?: "Co-writer", d.getLong("c") ?: 0xFF4D9DFF, d.getLong("e"), at)
            }
        }

        heartbeat = scope.launch {
            while (isActive) { writePresence(force = true); delay(25_000) }
        }
    }

    private fun permissionHint(e: Exception): String {
        val m = e.message ?: "Unknown error"
        return if (m.contains("PERMISSION_DENIED", true)) "Live co-writing isn't available in this version of Page One yet." else m
    }

    private fun parseElement(d: DocumentSnapshot): Element? {
        val id = d.id.toLongOrNull() ?: return null
        return Element(
            id = id,
            type = ElementType.fromName(d.getString("t")),
            text = d.getString("x") ?: "",
            dual = d.getBoolean("d") ?: false,
            revision = (d.getLong("r") ?: 0L).toInt(),
            color = (d.getLong("c") ?: 0L).toInt(),
            synopsis = d.getString("s") ?: "",
        )
    }

    private fun currentRemote(): List<Element> =
        known.values.sortedWith(compareBy<Element> { knownOrder[it.id] ?: 0.0 }.thenBy { it.id })

    /** Write story data and notes that differ from the shared copy. */
    fun pushExtras(extrasJson: String, comments: List<com.amit.pageone.core.Comment>) {
        val firestore = db ?: return
        if (uid == null || stopped) return
        val ref = scriptRef()
        val batch = firestore.batch()
        var any = false
        if (extrasJson != knownExtras) {
            knownExtras = extrasJson
            batch.set(ref, mapOf("ex" to extrasJson), SetOptions.merge()); any = true
        }
        val ids = HashSet<String>()
        for (c in comments) {
            ids += c.id
            val json = com.amit.pageone.data.ExtrasCodec.encodeComment(c).toString()
            if (knownComments[c.id] == json) continue
            knownComments[c.id] = json
            batch.set(ref.collection("comments").document(c.id), mapOf("j" to json, "at" to c.at)); any = true
        }
        for (gone in knownComments.keys.filter { it !in ids }) {
            knownComments.remove(gone)
            batch.delete(ref.collection("comments").document(gone)); any = true
        }
        if (any) batch.commit().addOnFailureListener { e -> fail(permissionHint(e)) }
    }

    /** Write whatever differs between [elements] and the shared copy. */
    fun push(elements: List<Element>, title: String, tp: TitlePage, log: Boolean = true) {
        val firestore = db ?: return
        val me = uid ?: return
        if (stopped) return
        val ref = scriptRef()
        val orders = Ordering.assign(elements.map { it.id }, knownOrder)
        val ops = ArrayList<(com.google.firebase.firestore.WriteBatch) -> Unit>()
        for (e in elements) {
            val o = orders.getValue(e.id)
            if (known[e.id] == e && knownOrder[e.id] == o) continue
            val data = hashMapOf<String, Any>(
                "t" to e.type.name, "x" to e.text, "d" to e.dual, "r" to e.revision.toLong(),
                "c" to e.color.toLong(), "s" to e.synopsis, "o" to o,
                "by" to me, "at" to FieldValue.serverTimestamp(),
            )
            val doc = ref.collection("elements").document(e.id.toString())
            ops += { b -> b.set(doc, data) }
            val prev = known[e.id]
            if (log) logChange(ref, ops, e.id, prev, e)
            known[e.id] = e; knownOrder[e.id] = o
        }
        val ids = elements.mapTo(HashSet()) { it.id }
        for (gone in known.keys.filter { it !in ids }) {
            val doc = ref.collection("elements").document(gone.toString())
            ops += { b -> b.delete(doc) }
            if (log) logChange(ref, ops, gone, known[gone], null)
            known.remove(gone); knownOrder.remove(gone)
        }
        val tpJson = ScriptStore.encodeTitlePage(tp).toString()
        val metaKey = "$title\u0000$tpJson"
        if (metaKey != knownMeta) {
            knownMeta = metaKey
            ops += { b -> b.set(ref, mapOf("title" to title, "tp" to tpJson, "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge()) }
        }
        ops.chunked(450).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { it(batch) }
            batch.commit().addOnFailureListener { e -> fail(permissionHint(e)) }
        }
    }

    /** Record (or extend) a history entry for one element. */
    private fun logChange(
        ref: com.google.firebase.firestore.DocumentReference,
        ops: MutableList<(com.google.firebase.firestore.WriteBatch) -> Unit>,
        id: Long, prev: Element?, now: Element?,
    ) {
        val me = uid ?: return
        val kind = when {
            prev == null && now != null -> "add"
            now == null -> "delete"
            prev!!.text != now.text -> "edit"
            prev.type != now.type -> "format"
            else -> return // colour / revision / order only — not worth a history entry
        }
        val t = System.currentTimeMillis()
        val burst = bursts[id]
        val continuing = burst != null && t - burst.lastAt < 120_000 && kind != "delete"
        val docId: String
        val before: String
        val effectiveKind: String
        if (continuing) {
            burst!!.lastAt = t
            docId = burst.docId; before = burst.before; effectiveKind = burst.kind
        } else {
            docId = java.util.UUID.randomUUID().toString()
            before = when (kind) { "format" -> prev!!.type.label; "add" -> ""; else -> prev?.text ?: "" }
            effectiveKind = kind
            bursts[id] = Burst(docId, before, kind, t)
        }
        val after = when {
            now == null -> ""
            effectiveKind == "format" -> now.type.label
            else -> now.text
        }
        val data = hashMapOf<String, Any>(
            "e" to id, "u" to me, "n" to myName, "c" to myColor, "k" to effectiveKind,
            "b" to before, "a" to after, "at" to t,
        )
        val doc = ref.collection("changes").document(docId)
        ops += { b -> b.set(doc, data) }
    }

    /** Tell co-writers where you are. Throttled. */
    fun setCursor(elementId: Long?) {
        cursor = elementId
        writePresence(force = false)
    }

    private fun writePresence(force: Boolean) {
        val me = uid ?: return
        if (db == null || stopped) return
        val now = System.currentTimeMillis()
        if (!force && now - lastPresenceAt < 1500) return
        lastPresenceAt = now
        val data = hashMapOf<String, Any>("n" to myName, "c" to myColor, "at" to now)
        cursor?.let { data["e"] = it }
        scriptRef().collection("presence").document(me).set(data)
    }

    fun stop() {
        if (stopped) return
        stopped = true
        heartbeat?.cancel()
        regs.forEach { it.remove() }
        regs.clear()
        val me = uid
        if (me != null && db != null) runCatching { scriptRef().collection("presence").document(me).delete() }
    }
}
