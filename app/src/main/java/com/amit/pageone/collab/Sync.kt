package com.amit.pageone.collab

import android.content.Context
import com.amit.pageone.core.Locking
import com.amit.pageone.core.Paginator
import com.amit.pageone.data.Accent
import com.amit.pageone.data.AppGraph
import com.amit.pageone.data.ScriptMeta
import com.amit.pageone.data.ScriptStore
import com.amit.pageone.data.SprintRecord
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Wi-Fi sync between your devices: projects, daily words, streak, sprints, name, goal and accent follow
 * you between this phone and your computer (or another phone), directly over your own network. No server,
 * no account, no keys. The desktop app's `src/state/sync.ts` speaks the same protocol.
 *
 * Pairing: one device shows an 8-letter code (and its address), the other types both. They then share a
 * secret and talk whenever both apps are open on the same network (a phone's hotspot works too).
 *
 * Every 15 seconds, and a moment after you save, each device that knows a partner trades "manifests"
 * (which projects it has and when each changed, its words per day, sprints, goal) and then moves the
 * projects that differ, newest copy winning. A copy that would be overwritten while it has changes the
 * other side never saw is first kept under Versions as "Before Wi-Fi sync". Deleting a project leaves a
 * note that tells the other devices to delete theirs.
 */
class SyncSpace(private val context: Context) {

    enum class Phase { OFF, WAITING, WORKING, SYNCED, ERROR }
    data class Peer(val id: String, val name: String, val secret: String, val ip: String, val lastAt: Long)
    data class Pairing(val code: String, val addresses: List<String>, val until: Long)
    data class State(val peers: List<Peer>, val phase: Phase, val message: String = "", val lastAt: Long = 0L, val pairing: Pairing? = null)

    private class Entry(
        val id: String, val title: String, val author: String, val modifiedAt: Long, val createdAt: Long, val pages: Int,
        val scenes: Int, val words: Int, val coverColor: Int, val logline: String, val deleted: Boolean,
    )

    private val p = context.getSharedPreferences("pageone_sync", Context.MODE_PRIVATE)
    private val scope get() = AppGraph.appScope
    val device: String = p.getString("device", null) ?: UUID.randomUUID().toString().replace("-", "").take(12).also { p.edit().putString("device", it).apply() }

    private val tomb = ConcurrentHashMap<String, Long>().apply { readMap("tomb").forEach { (k, v) -> put(k, v.toLong()) } }
    private val syncedAt = ConcurrentHashMap<String, Long>().apply { readMap("syncedAt").forEach { (k, v) -> put(k, v.toLong()) } }
    /** Words per day on other devices: deviceId → day → words. */
    private val otherDays = ConcurrentHashMap<String, ConcurrentHashMap<String, Int>>()
    private var prefsAt = p.getLong("prefsAt", 0L)

    private val _state = MutableStateFlow(State(readPeers(), Phase.OFF))
    val state: StateFlow<State> = _state.asStateFlow()

    /** With a partner paired, sync is on. */
    val active: Boolean get() = _state.value.peers.isNotEmpty()

    private val applying = ConcurrentHashMap.newKeySet<String>()
    private val lock = Mutex()
    private var server: ServerSocket? = null
    private var pairingTries = 0
    private var soonJob: Job? = null
    private var nextScan = 0L
    @Volatile private var applyingPrefs = false

    private fun update(f: (State) -> State) { _state.value = f(_state.value) }
    private fun myName() = AppGraph.collab.name.value.ifBlank { android.os.Build.MODEL ?: "Phone" }

    // ------------------------------------------------------------------ storage

    private fun readMap(key: String): Map<String, Number> = runCatching {
        val o = JSONObject(p.getString(key, "{}")!!)
        o.keys().asSequence().associateWith { o.getLong(it) as Number }
    }.getOrDefault(emptyMap())

    private fun readPeers(): List<Peer> = runCatching {
        val a = JSONArray(p.getString("peers", "[]"))
        (0 until a.length()).map { i -> a.getJSONObject(i).let { Peer(it.getString("id"), it.optString("name"), it.getString("secret"), it.optString("ip"), it.optLong("lastAt")) } }
    }.getOrDefault(emptyList())

    private fun persist() {
        val e = p.edit()
        e.putString("peers", JSONArray().also { a -> _state.value.peers.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("secret", it.secret).put("ip", it.ip).put("lastAt", it.lastAt)) } }.toString())
        e.putString("tomb", JSONObject(tomb.mapKeys { it.key }.mapValues { it.value }).toString())
        e.putString("syncedAt", JSONObject(syncedAt.mapKeys { it.key }.mapValues { it.value }).toString())
        e.putLong("prefsAt", prefsAt)
        e.apply()
    }

    init {
        runCatching {
            val o = JSONObject(p.getString("otherDays", "{}")!!)
            o.keys().forEach { dev -> val per = o.getJSONObject(dev); val m = ConcurrentHashMap<String, Int>(); per.keys().forEach { m[it] = per.getInt(it) }; otherDays[dev] = m }
        }
    }

    private fun persistDays() {
        val o = JSONObject(); otherDays.forEach { (dev, m) -> o.put(dev, JSONObject(m as Map<*, *>)) }
        p.edit().putString("otherDays", o.toString()).apply()
    }

    /** Hook everything up; called once the app's stores exist. */
    fun init() {
        AppGraph.store.onSaved = { m -> if (m.id !in applying) soon(3000) }
        AppGraph.goals.onDay = { _, _ -> soon(5000) }
        AppGraph.sprints.onRecorded = { soon(1000) }
        AppGraph.settings.onPrefsChanged = { prefsChanged() }
        AppGraph.collab.onNameChanged = { prefsChanged() }
        recomputeOthers()
        scope.launch {
            while (true) {
                delay(15_000)
                if (active) soon(0)
            }
        }
        if (active) scope.launch { delay(1500); runCatching { ensureServing() }; soon(1500) }
    }

    // ------------------------------------------------------------------ pairing

    /** Show a code (and this phone's address) for another device to type. */
    suspend fun startPairing(): Pairing = withContext(Dispatchers.IO) {
        ensureServing()
        val addr = addresses()
        check(addr.isNotEmpty()) { "This phone isn't on a Wi-Fi network." }
        val chars = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
        val r = SecureRandom()
        val raw = (1..8).map { chars[r.nextInt(chars.length)] }.joinToString("")
        val pr = Pairing(raw.take(4) + "-" + raw.drop(4), addr, System.currentTimeMillis() + 10 * 60_000L)
        pairingTries = 0
        update { it.copy(pairing = pr) }
        pr
    }

    fun cancelPairing() = update { it.copy(pairing = null) }

    /** Pair with a device that is showing a code. */
    suspend fun pairWith(address: String, codeText: String) = withContext(Dispatchers.IO) {
        val code = codeText.uppercase().filter { it.isLetterOrDigit() }
        val ip = address.trim().removePrefix("http://").substringBefore("/")
        check(Regex("""\d{1,3}(\.\d{1,3}){3}(:\d+)?""").matches(ip)) { "Type the other device's address as numbers, like 192.168.1.5." }
        check(code.length == 8) { "The code has 8 letters and numbers, like K7QP-X2MA." }
        ensureServing()
        val r = try { request(ip, "POST", "/pair", "", JSONObject().put("code", code).put("id", device).put("name", myName()).toString(), 6000) }
        catch (e: Exception) { error("Couldn't reach that address. Both devices must be on the same Wi-Fi, with Page One open on the other one showing its code.") }
        val o = runCatching { JSONObject(r.second) }.getOrNull()
        check(r.first == 200 && o?.has("secret") == true) { o?.optString("error")?.takeIf { it.isNotBlank() } ?: "That code was not accepted. Check it, or make a new one on the other device." }
        addPeer(Peer(o!!.getString("id"), o.optString("name", "Computer"), o.getString("secret"), ip.substringBefore(":"), System.currentTimeMillis()))
        soon(500)
    }

    fun unpair(id: String) {
        update { s -> s.copy(peers = s.peers.filter { it.id != id }, phase = if (s.peers.any { it.id != id }) Phase.WAITING else Phase.OFF, message = "") }
        persist()
    }

    fun syncNow() = soon(0)

    private fun addPeer(peer: Peer) {
        update { s -> s.copy(peers = s.peers.filter { it.id != peer.id } + peer, phase = Phase.WAITING, message = "", pairing = null) }
        persist()
    }

    // ------------------------------------------------------------------ network

    private fun addresses(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.asSequence() }
            .filterIsInstance<Inet4Address>()
            .filter { it.isSiteLocalAddress }
            .map { it.hostAddress!! }.toList()
    }.getOrDefault(emptyList())

    private fun ensureServing() {
        if (server?.isClosed == false) return
        val ss = ServerSocket()
        ss.reuseAddress = true
        ss.bind(InetSocketAddress(PORT))
        server = ss
        scope.launch {
            while (!ss.isClosed) {
                val s = try { ss.accept() } catch (e: Exception) { break }
                scope.launch { runCatching { handle(s) }; runCatching { s.close() } }
            }
        }
    }

    private class Req(val method: String, val path: String, val auth: String, val body: String, val remote: String)

    private fun readReq(s: Socket): Req? {
        s.soTimeout = 20_000
        val inp: InputStream = s.getInputStream()
        val buf = ByteArrayOutputStream()
        var headEnd = -1
        val tmp = ByteArray(8192)
        while (headEnd < 0) {
            val n = inp.read(tmp); if (n < 0) return null
            buf.write(tmp, 0, n)
            val b = buf.toByteArray()
            headEnd = indexOf(b, "\r\n\r\n".toByteArray())
            if (b.size > 64 * 1024 && headEnd < 0) return null
        }
        val all = buf.toByteArray()
        val head = String(all, 0, headEnd)
        val lines = head.split("\r\n")
        val first = lines[0].split(" ")
        val headers = lines.drop(1).mapNotNull { l -> l.indexOf(':').takeIf { it > 0 }?.let { l.substring(0, it).trim().lowercase() to l.substring(it + 1).trim() } }.toMap()
        val len = headers["content-length"]?.toIntOrNull() ?: 0
        if (len > 64 * 1024 * 1024) return null
        val body = ByteArrayOutputStream()
        body.write(all, headEnd + 4, all.size - headEnd - 4)
        while (body.size() < len) { val n = inp.read(tmp); if (n < 0) break; body.write(tmp, 0, n) }
        val bytes = body.toByteArray().let { if (it.size > len) it.copyOf(len) else it }
        return Req(first.getOrElse(0) { "" }, first.getOrElse(1) { "/" }, headers["authorization"] ?: "", String(bytes, Charsets.UTF_8), s.inetAddress.hostAddress ?: "")
    }

    private fun indexOf(h: ByteArray, n: ByteArray): Int {
        outer@ for (i in 0..h.size - n.size) { for (j in n.indices) if (h[i + j] != n[j]) continue@outer; return i }
        return -1
    }

    private fun reply(s: Socket, status: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val reason = when (status) { 200 -> "OK"; 400 -> "Bad Request"; 401 -> "Unauthorized"; 404 -> "Not Found"; else -> "Error" }
        val head = "HTTP/1.1 $status $reason\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        s.getOutputStream().apply { write(head.toByteArray()); write(bytes); flush() }
    }

    /** One request to another device: (status, body). */
    private fun request(address: String, method: String, path: String, auth: String, body: String, timeoutMs: Int): Pair<Int, String> {
        val host = address.substringBefore(":")
        val port = address.substringAfter(":", PORT.toString()).toInt()
        Socket().use { s ->
            s.connect(InetSocketAddress(host, port), timeoutMs)
            s.soTimeout = 60_000
            val bytes = body.toByteArray(Charsets.UTF_8)
            val head = StringBuilder("$method $path HTTP/1.1\r\nHost: $address\r\nConnection: close\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: ${bytes.size}\r\n")
            if (auth.isNotEmpty()) head.append("Authorization: $auth\r\n")
            head.append("\r\n")
            s.getOutputStream().apply { write(head.toString().toByteArray()); write(bytes); flush() }
            val raw = s.getInputStream().readBytes()
            val end = indexOf(raw, "\r\n\r\n".toByteArray())
            check(end >= 0) { "bad response" }
            val status = String(raw, 0, end).split(" ").getOrNull(1)?.toIntOrNull() ?: error("bad status")
            return status to String(raw, end + 4, raw.size - end - 4, Charsets.UTF_8)
        }
    }

    // ------------------------------------------------------------------ serving requests

    private suspend fun handle(s: Socket) {
        val r = readReq(s) ?: return reply(s, 400, """{"error":"bad request"}""")
        val path = r.path.substringBefore("?")
        if (r.method == "GET" && path == "/hello") return reply(s, 200, JSONObject().put("app", "pageone").put("id", device).put("name", myName()).toString())

        if (r.method == "POST" && path == "/pair") {
            val pr = _state.value.pairing
            val b = JSONObject(r.body.ifBlank { "{}" })
            if (pr == null || System.currentTimeMillis() > pr.until) return reply(s, 400, """{"error":"No code is being shown on that device (or it ran out). Make a new one."}""")
            if (++pairingTries > 5) { cancelPairing(); return reply(s, 400, """{"error":"Too many wrong tries. Make a new code."}""") }
            val theirs = b.optString("code").uppercase().filter { it.isLetterOrDigit() }
            if (theirs != pr.code.replace("-", "") || b.optString("id").isBlank()) return reply(s, 400, """{"error":"That code is not right."}""")
            val secret = (1..24).map { "%02x".format(SecureRandom().nextInt(256)) }.joinToString("")
            addPeer(Peer(b.getString("id"), b.optString("name", "Computer"), secret, r.remote, System.currentTimeMillis()))
            soon(1500)
            return reply(s, 200, JSONObject().put("id", device).put("name", myName()).put("secret", secret).toString())
        }

        val peer = _state.value.peers.firstOrNull { r.auth == "Bearer " + it.secret } ?: return reply(s, 401, """{"error":"not paired"}""")
        touch(peer, r.remote)

        if (r.method == "POST" && path == "/exchange") {
            val mine = manifest()
            applyManifest(JSONObject(r.body), peer)
            return reply(s, 200, mine.toString())
        }
        val m = Regex("""^/script/([\w-]+)$""").find(path)
        if (m != null && r.method == "GET") {
            val sc = AppGraph.store.load(m.groupValues[1]) ?: return reply(s, 404, """{"error":"no such script"}""")
            return reply(s, 200, ScriptStore.encode(sc).toString())
        }
        if (m != null && r.method == "PUT") return reply(s, 200, takeScript(m.groupValues[1], r.body).toString())
        reply(s, 404, """{"error":"unknown"}""")
    }

    private fun touch(peer: Peer, ip: String) {
        update { s -> s.copy(peers = s.peers.map { if (it.id == peer.id) it.copy(ip = ip, lastAt = System.currentTimeMillis()) else it }) }
    }

    // ------------------------------------------------------------------ manifests

    private suspend fun manifest(): JSONObject {
        AppGraph.store.refresh()
        val lib = JSONArray()
        val live = HashSet<String>()
        for (m in AppGraph.store.library.value) {
            live += m.id
            lib.put(JSONObject().put("id", m.id).put("title", m.title).put("author", m.author).put("modifiedAt", m.modifiedAt).put("createdAt", m.createdAt)
                .put("pages", m.pages).put("scenes", m.scenes).put("words", m.words).put("coverColor", m.coverColor).put("logline", m.logline).put("deleted", false))
        }
        for ((id, at) in tomb) if (id !in live) lib.put(JSONObject().put("id", id).put("title", "").put("author", "").put("modifiedAt", at).put("createdAt", 0)
            .put("pages", 0).put("scenes", 0).put("words", 0).put("coverColor", 0).put("logline", "").put("deleted", true))
        val days = JSONObject()
        otherDays.forEach { (dev, m) -> days.put(dev, JSONObject(m as Map<*, *>)) }
        days.put(device, JSONObject(AppGraph.goals.ownDays() as Map<*, *>))
        val sprints = JSONArray()
        AppGraph.sprints.history().forEach { sprints.put(JSONObject().put("at", it.at).put("minutes", it.minutes).put("words", it.words).put("goal", it.goal).put("script", "")) }
        val s = AppGraph.settings.state.value
        val prefs = JSONObject().put("name", AppGraph.collab.name.value).put("goal", s.dailyGoal).put("accent", s.accent.label.lowercase()).put("at", prefsAt)
        return JSONObject().put("id", device).put("name", myName()).put("lib", lib).put("days", days).put("sprints", sprints).put("prefs", prefs)
    }

    private fun recomputeOthers() {
        val others = HashMap<String, Int>()
        otherDays.values.forEach { per -> per.forEach { (day, n) -> others[day] = (others[day] ?: 0) + n } }
        AppGraph.goals.setOthers(others)
    }

    /** Take in a partner's words per day, sprints, name/goal/accent and deletions. */
    private suspend fun applyManifest(m: JSONObject, peer: Peer) {
        val days = m.optJSONObject("days")
        if (days != null) {
            for (dev in days.keys()) {
                if (dev == device) continue
                val per = days.getJSONObject(dev)
                val mine = otherDays.getOrPut(dev) { ConcurrentHashMap() }
                for (day in per.keys()) { val n = per.optInt(day); if (n > (mine[day] ?: 0)) mine[day] = n }
            }
            persistDays(); recomputeOthers()
        }
        m.optJSONArray("sprints")?.let { a ->
            AppGraph.sprints.merge((0 until a.length()).map { a.getJSONObject(it).let { o -> SprintRecord(o.optLong("at"), o.optInt("minutes"), o.optInt("goal"), o.optInt("words")) } })
        }
        m.optJSONObject("prefs")?.let { pf ->
            val at = pf.optLong("at")
            if (at > prefsAt) {
                prefsAt = at
                applyingPrefs = true
                try {
                    val name = pf.optString("name"); val goal = pf.optInt("goal"); val accent = pf.optString("accent")
                    if (name.isNotBlank() && name != AppGraph.collab.name.value) AppGraph.collab.setName(name)
                    AppGraph.settings.update { st ->
                        var n = st
                        if (goal > 0) n = n.copy(dailyGoal = goal)
                        Accent.entries.firstOrNull { it.label.equals(accent, true) }?.let { n = n.copy(accent = it) }
                        n
                    }
                } finally { applyingPrefs = false }
            }
        }
        val openId = AppGraph.openScriptId
        val lib = m.optJSONArray("lib") ?: JSONArray()
        for (i in 0 until lib.length()) {
            val e = lib.getJSONObject(i)
            val id = e.getString("id"); val at = e.optLong("modifiedAt")
            if (!e.optBoolean("deleted")) { if ((tomb[id] ?: Long.MIN_VALUE) != Long.MIN_VALUE && at > tomb[id]!!) tomb.remove(id); continue }
            if ((tomb[id] ?: 0L) < at) tomb[id] = at
            val local = AppGraph.store.library.value.firstOrNull { it.id == id }
            if (local != null && id != openId && at >= local.modifiedAt) {
                applying += id
                try { AppGraph.store.delete(id) } finally { applying -= id }
            }
        }
        persist()
    }

    // ------------------------------------------------------------------ moving projects

    /** Receive a project from a partner. Answers whether it was taken. */
    private suspend fun takeScript(id: String, body: String): JSONObject {
        val incoming = ScriptStore.decode(JSONObject(body))
        if (incoming.id != id) return JSONObject().put("taken", false).put("why", "mismatch")
        if (AppGraph.openScriptId == id) return JSONObject().put("taken", false).put("why", "open here")
        if ((tomb[id] ?: 0L) >= incoming.modifiedAt) return JSONObject().put("taken", false).put("why", "deleted here")
        val local = AppGraph.store.load(id)
        if (local != null && local.modifiedAt > incoming.modifiedAt + 1500) return JSONObject().put("taken", false).put("why", "newer here")
        applying += id
        try {
            val layout = AppGraph.settings.state.value.layout
            fun pagesOf(sc: com.amit.pageone.core.Script) = runCatching { Paginator(layout).paginate(sc.elements, Locking.forcedBreaks(sc.elements, sc.extras.lock)).pageCount }.getOrDefault(1)
            if (local != null && local.modifiedAt > (syncedAt[id] ?: 0L) + 1500) {
                runCatching { AppGraph.store.saveVersion(local, "Before Wi-Fi sync", pagesOf(local), AppGraph.settings.state.value.versionsToKeep) }
            }
            AppGraph.store.save(incoming, pagesOf(incoming))
            syncedAt[id] = incoming.modifiedAt; persist()
        } finally { applying -= id }
        return JSONObject().put("taken", true)
    }

    // ------------------------------------------------------------------ the sync loop

    private fun soon(ms: Long) {
        if (!active) return
        soonJob?.cancel()
        soonJob = scope.launch { delay(ms); runAll() }
    }

    private suspend fun runAll() {
        if (!lock.tryLock()) return
        try {
            withContext(Dispatchers.IO) {
                runCatching { ensureServing() }
                update { it.copy(phase = Phase.WORKING, message = "") }
                var reached = 0
                for (peer in _state.value.peers) if (runCatching { syncWith(peer) }.getOrDefault(false)) reached++
                persist()
                if (reached > 0) update { it.copy(phase = Phase.SYNCED, message = "", lastAt = System.currentTimeMillis()) }
                else update { it.copy(phase = Phase.WAITING, message = "Looking for your other device. Open Page One on it, on the same Wi-Fi.") }
            }
        } catch (e: Exception) {
            update { it.copy(phase = Phase.ERROR, message = e.message ?: "Something went wrong") }
        } finally { lock.unlock() }
    }

    private suspend fun syncWith(peer: Peer): Boolean {
        val auth = "Bearer " + peer.secret
        var ip = peer.ip
        suspend fun reach(): Pair<Int, String>? = runCatching { request(ip, "POST", "/exchange", auth, manifest().toString(), 8000) }.getOrNull()
        var r = if (ip.isNotBlank()) reach() else null
        if (r == null && System.currentTimeMillis() > nextScan) {
            nextScan = System.currentTimeMillis() + 60_000
            val mine = addresses().firstOrNull()
            val found = mine?.let { findDevice(it, peer.id) }
            if (found != null) { ip = found; update { s -> s.copy(peers = s.peers.map { if (it.id == peer.id) it.copy(ip = found) else it }) }; r = reach() }
        }
        if (r == null || r.first != 200) return false
        touch(peer, ip)
        val theirs = JSONObject(r.second)
        applyManifest(theirs, peer)

        val openId = AppGraph.openScriptId
        AppGraph.store.refresh()
        val local = AppGraph.store.library.value.associateBy { it.id }
        val remote = HashMap<String, Entry>()
        val lib = theirs.optJSONArray("lib") ?: JSONArray()
        for (i in 0 until lib.length()) {
            val e = lib.getJSONObject(i)
            remote[e.getString("id")] = Entry(e.getString("id"), e.optString("title"), e.optString("author"), e.optLong("modifiedAt"), e.optLong("createdAt"), e.optInt("pages"), e.optInt("scenes"), e.optInt("words"), e.optInt("coverColor"), e.optString("logline"), e.optBoolean("deleted"))
        }
        for (e in remote.values) {
            if (e.deleted) continue
            val l = local[e.id]
            runCatching {
                if (l == null) { if ((tomb[e.id] ?: 0L) < e.modifiedAt) pull(ip, auth, e.id) }
                else if (e.modifiedAt > l.modifiedAt + 1500) { if (e.id != openId) pull(ip, auth, e.id) }
                else if (l.modifiedAt > e.modifiedAt + 1500) push(ip, auth, e.id)
            }
        }
        for (l in local.values) if (!remote.containsKey(l.id)) runCatching { push(ip, auth, l.id) }
        return true
    }

    private suspend fun pull(ip: String, auth: String, id: String) {
        val r = request(ip, "GET", "/script/$id", auth, "", 8000)
        if (r.first == 200) takeScript(id, r.second)
    }

    private suspend fun push(ip: String, auth: String, id: String) {
        val s = AppGraph.store.load(id) ?: return
        val r = request(ip, "PUT", "/script/$id", auth, ScriptStore.encode(s).toString(), 8000)
        if (r.first == 200) { syncedAt[id] = s.modifiedAt; persist() }
    }

    private suspend fun findDevice(own: String, id: String): String? {
        val parts = own.split(".")
        if (parts.size != 4) return null
        val prefix = parts.take(3).joinToString(".")
        val hosts = (1..254).map { "$prefix.$it" }.filter { it != own }
        for (chunk in hosts.chunked(64)) {
            val hits = coroutineScope {
                chunk.map { h -> async(Dispatchers.IO) {
                    runCatching { val r = request(h, "GET", "/hello", "", "", 500); if (r.first == 200 && JSONObject(r.second).optString("id") == id) h else null }.getOrNull()
                } }.map { it.await() }
            }
            hits.firstOrNull { it != null }?.let { return it }
        }
        return null
    }

    // ------------------------------------------------------------------ hooks

    private fun prefsChanged() {
        if (!active || applyingPrefs) return
        prefsAt = System.currentTimeMillis(); persist()
        soon(2000)
    }

    /** The writer deleted a project from the library: tell the other devices. (Nothing else ever does.) */
    fun userDeleted(id: String) {
        if (!active) return
        tomb[id] = System.currentTimeMillis(); persist()
        soon(1000)
    }

    /** Run a pass now (the editor closed, the app came back): catches up on what was skipped while a script was open. */
    fun kick() { if (active) soon(500) }

    companion object { const val PORT = 47831 }
}
