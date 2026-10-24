package com.amit.pageone.data

import android.content.Context
import com.amit.pageone.core.IdGen
import com.amit.pageone.core.Merge
import com.amit.pageone.core.Script
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/** The three values from a Firebase project that live collaboration needs. */
data class ServerConfig(val projectId: String, val apiKey: String, val appId: String) {
    val isValid: Boolean get() = projectId.isNotBlank() && apiKey.isNotBlank() && appId.isNotBlank()
}

/**
 * A resolved join code. The server config is always this phone's own — it is never
 * sent inside an invite, so join codes and links carry no server details at all.
 */
data class Invite(val config: ServerConfig, val scriptId: String, val title: String, val from: String)

/**
 * Collaboration preferences: your display name, the live-sync server, which scripts
 * are live on this device, and the last version exchanged with co-writers ("base")
 * that file merges are computed against.
 */
class CollabStore(private val context: Context) {
    private val p = context.getSharedPreferences("pageone_collab", Context.MODE_PRIVATE)
    private val basesDir = File(context.filesDir, "bases").apply { mkdirs() }

    /** The server baked into the app — everyone who installs Page One shares it. */
    val builtInServer: ServerConfig by lazy {
        ServerConfig(
            context.getString(com.amit.pageone.R.string.collab_project_id),
            context.getString(com.amit.pageone.R.string.collab_api_key),
            context.getString(com.amit.pageone.R.string.collab_app_id),
        )
    }

    private val _server = MutableStateFlow(readServer())
    val server: StateFlow<ServerConfig> = _server.asStateFlow()

    private val _name = MutableStateFlow(p.getString("name", "") ?: "")
    val name: StateFlow<String> = _name.asStateFlow()

    private val _live = MutableStateFlow(p.getStringSet("live", emptySet())!!.toSet())
    val liveScripts: StateFlow<Set<String>> = _live.asStateFlow()


    /** A custom server set in Settings (advanced), else the built-in one. */
    private fun readServer(): ServerConfig {
        val custom = ServerConfig(p.getString("projectId", "") ?: "", p.getString("apiKey", "") ?: "", p.getString("appId", "") ?: "")
        return if (custom.isValid) custom else builtInServer
    }

    val usingBuiltIn: Boolean get() = _server.value == builtInServer

    fun resetServer() {
        p.edit().remove("projectId").remove("apiKey").remove("appId").apply()
        _server.value = readServer()
    }

    fun setServer(c: ServerConfig) {
        p.edit().putString("projectId", c.projectId.trim()).putString("apiKey", c.apiKey.trim()).putString("appId", c.appId.trim()).apply()
        _server.value = readServer()
    }

    /** Sync listens here: your name changed. */
    var onNameChanged: (() -> Unit)? = null

    fun setName(n: String) { val changed = n != _name.value; p.edit().putString("name", n).apply(); _name.value = n; if (changed) onNameChanged?.invoke() }

    fun displayName(): String = _name.value.ifBlank { AppGraph.settings.state.value.defaultAuthor.ifBlank { "Writer" } }

    fun setLive(scriptId: String, on: Boolean) {
        val s = _live.value.toMutableSet().apply { if (on) add(scriptId) else remove(scriptId) }
        p.edit().putStringSet("live", s).apply()
        _live.value = s
    }

    fun isLive(scriptId: String) = scriptId in _live.value

    // ---- what you've already looked at in a shared script's change history
    fun seenAt(scriptId: String): Long = p.getLong("seen_$scriptId", 0L)
    fun setSeenAt(scriptId: String, t: Long) { p.edit().putLong("seen_$scriptId", t).apply() }
    fun seenElements(scriptId: String): Map<Long, Long> = runCatching {
        val o = JSONObject(p.getString("seenEl_$scriptId", "{}")!!)
        o.keys().asSequence().associate { it.toLong() to o.getLong(it) }
    }.getOrDefault(emptyMap())
    fun setSeenElements(scriptId: String, m: Map<Long, Long>) {
        val o = JSONObject(); m.forEach { (k, v) -> o.put(k.toString(), v) }
        p.edit().putString("seenEl_$scriptId", o.toString()).apply()
    }

    /** A stable colour per person, picked from their id. */
    fun colorFor(id: String): Long = PEER_COLORS[(id.hashCode() and 0x7fffffff) % PEER_COLORS.size]

    /** Accept an invite: adopt the server settings, create a local shell, mark it live. */
    suspend fun join(inv: Invite): String {
        if (_server.value != inv.config) {
            if (inv.config == builtInServer) resetServer() else setServer(inv.config)
        }
        val store = AppGraph.store
        if (store.load(inv.scriptId) == null) {
            val shell = Script(
                id = inv.scriptId, title = inv.title.ifBlank { "Shared script" },
                elements = listOf(com.amit.pageone.core.Element(IdGen().next(), com.amit.pageone.core.ElementType.SCENE_HEADING, "")),
                titlePage = com.amit.pageone.core.TitlePage(title = inv.title),
            )
            store.save(shell, 1)
            setSeenAt(inv.scriptId, System.currentTimeMillis()) // earlier history isn't "new" to you
        }
        setLive(inv.scriptId, true)
        return inv.scriptId
    }

    // ------------------------------------------------------------------ file exchange

    suspend fun saveBase(s: Script) = withContext(Dispatchers.IO) {
        File(basesDir, "${s.id}.json").writeText(ScriptStore.encode(s).toString())
    }

    suspend fun loadBase(id: String): Script? = withContext(Dispatchers.IO) {
        val f = File(basesDir, "$id.json")
        if (!f.exists()) null else runCatching { ScriptStore.decode(JSONObject(f.readText())) }.getOrNull()
    }

    /** Bytes of a .pageone file for sending to a co-writer. Records it as the shared base. */
    suspend fun packForSending(s: Script): ByteArray {
        saveBase(s)
        val o = ScriptStore.encode(s).put("pageone", 1).put("sender", displayName()).put("sentAt", System.currentTimeMillis())
        return o.toString().toByteArray()
    }

    data class Received(val scriptId: String, val merged: Merge.Result?, val sender: String, val isNew: Boolean)

    /**
     * Take in a co-writer's .pageone file: a script we don't have becomes a new script
     * (same id, so later exchanges merge); one we do have is three-way merged.
     */
    suspend fun receive(remote: Script, sender: String): Received {
        val store = AppGraph.store
        val local = store.load(remote.id)
        if (local == null) {
            store.save(remote.copy(modifiedAt = System.currentTimeMillis()), 0)
            saveBase(remote)
            return Received(remote.id, null, sender, true)
        }
        val base = loadBase(remote.id)
        val r = Merge.threeWay(base?.elements, local.elements, remote.elements, IdGen(), sender.ifBlank { "Co-writer" })
        val tp = if (base != null && base.titlePage == local.titlePage) remote.titlePage else local.titlePage
        val merged = local.copy(elements = r.elements, titlePage = tp, title = tp.title.ifBlank { local.title }, modifiedAt = System.currentTimeMillis())
        store.save(merged, 0)
        saveBase(merged)
        return Received(remote.id, r, sender, false)
    }

    companion object {
        val PEER_COLORS = listOf(0xFF4D9DFF, 0xFF3FBF7F, 0xFFB388FF, 0xFFFF5C8A, 0xFFFFB74D, 0xFF26C6DA, 0xFFE0B040)

        fun parsePageOne(bytes: ByteArray): Pair<Script, String>? = runCatching {
            val o = JSONObject(String(bytes))
            if (!o.has("pageone")) return null
            ScriptStore.decode(o) to o.optString("sender", "Co-writer")
        }.getOrNull()
    }
}
