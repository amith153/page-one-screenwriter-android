package com.amit.pageone.data

import android.content.Context
import com.amit.pageone.core.Element
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

/**
 * Collaboration preferences: your display name and author id (stamped on every line you
 * change), and the last version exchanged with co-writers ("base") that file merges are
 * computed against.
 */
class CollabStore(private val context: Context) {
    private val p = context.getSharedPreferences("pageone_collab", Context.MODE_PRIVATE)
    private val basesDir = File(context.filesDir, "bases").apply { mkdirs() }

    private val _name = MutableStateFlow(p.getString("name", "") ?: "")
    val name: StateFlow<String> = _name.asStateFlow()

    /** Sync listens here: your name changed. */
    var onNameChanged: (() -> Unit)? = null

    fun setName(n: String) { val changed = n != _name.value; p.edit().putString("name", n).apply(); _name.value = n; if (changed) onNameChanged?.invoke() }

    fun displayName(): String = _name.value.ifBlank { AppGraph.settings.state.value.defaultAuthor.ifBlank { "Writer" } }

    /** A random id for this install, stamped on lines you write; never a phone number or an email. */
    val authorId: String by lazy {
        p.getString("authorId", null) ?: java.util.UUID.randomUUID().toString().also { p.edit().putString("authorId", it).apply() }
    }

    /** [e] as written by you, now. */
    fun stamp(e: Element, now: Long): Element = e.copy(by = authorId, byName = displayName(), at = now)

    /** A stable colour per person, picked from their id. */
    fun colorFor(id: String): Long = PEER_COLORS[(id.hashCode() and 0x7fffffff) % PEER_COLORS.size]

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
