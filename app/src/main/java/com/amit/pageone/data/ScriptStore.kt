package com.amit.pageone.data

import android.content.Context
import com.amit.pageone.core.Credit
import com.amit.pageone.core.Element
import com.amit.pageone.core.ElementType
import com.amit.pageone.core.Script
import com.amit.pageone.core.TitlePage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Lightweight summary used by the library screen. */
data class ScriptMeta(
    val id: String,
    val title: String,
    val author: String,
    val modifiedAt: Long,
    val createdAt: Long,
    val pages: Int,
    val scenes: Int,
    val words: Int,
    val coverColor: Int,
    val logline: String,
    val deadline: com.amit.pageone.core.Deadline? = null,
    val locked: Boolean = false,
)

data class VersionInfo(val file: File, val name: String, val time: Long, val pages: Int)

/**
 * Stores each script as one JSON file: files/scripts/<id>.json.
 * A small sidecar index keeps the library fast even with many long scripts.
 * Versions (snapshots) live in files/versions/<id>/<time>.json.
 */
class ScriptStore(context: Context) {
    private val dir = File(context.filesDir, "scripts").apply { mkdirs() }
    private val versionsRoot = File(context.filesDir, "versions").apply { mkdirs() }
    private val indexFile = File(dir, "_index.json")

    /** Sync listens here: a script was saved. */
    var onSaved: ((ScriptMeta) -> Unit)? = null

    private val _library = MutableStateFlow<List<ScriptMeta>>(emptyList())
    val library: StateFlow<List<ScriptMeta>> = _library.asStateFlow()

    /** The library as last indexed, read straight from disk (for background reminders). */
    fun indexSnapshot(): List<ScriptMeta> = readIndex().values.toList()

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val index = readIndex()
        val files = dir.listFiles { f -> f.name.endsWith(".json") && f.name != "_index.json" }.orEmpty()
        val metas = files.mapNotNull { f ->
            val id = f.nameWithoutExtension
            index[id]?.takeIf { it.modifiedAt >= f.lastModified() - 2000 } ?: runCatching {
                metaOf(decode(JSONObject(f.readText())), pages = 0)
            }.getOrNull()
        }
        writeIndex(metas)
        _library.value = metas.sortedByDescending { it.modifiedAt }
    }

    suspend fun load(id: String): Script? = withContext(Dispatchers.IO) {
        val f = File(dir, "$id.json")
        if (!f.exists()) null else runCatching { decode(JSONObject(f.readText())) }.getOrNull()
    }

    /** Atomic save: write to temp, then rename, so a crash can never corrupt a script. */
    suspend fun save(script: Script, pages: Int) = withContext(Dispatchers.IO) {
        val tmp = File(dir, "${script.id}.json.tmp")
        tmp.writeText(encode(script).toString())
        val target = File(dir, "${script.id}.json")
        if (!tmp.renameTo(target)) { target.delete(); tmp.renameTo(target) }
        val meta = metaOf(script, pages)
        val list = _library.value.filter { it.id != script.id } + meta
        _library.value = list.sortedByDescending { it.modifiedAt }
        writeIndex(list)
        onSaved?.invoke(meta)
    }

    suspend fun create(title: String, elements: List<Element>? = null, titlePage: TitlePage? = null): Script {
        val id = UUID.randomUUID().toString()
        val els = elements ?: listOf(
            Element(1, ElementType.ACTION, "FADE IN:"),
            Element(2, ElementType.SCENE_HEADING, ""),
        )
        val s = Script(
            id = id, title = title, elements = els,
            titlePage = titlePage ?: TitlePage(title = title),
            coverColor = (1..9).random(),
        )
        save(s, pages = 1)
        return s
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        File(dir, "$id.json").delete()
        File(versionsRoot, id).deleteRecursively()
        val list = _library.value.filter { it.id != id }
        _library.value = list
        writeIndex(list)
    }

    suspend fun duplicate(id: String): Script? {
        val s = load(id) ?: return null
        val copy = s.copy(
            id = UUID.randomUUID().toString(),
            title = s.title + " (copy)",
            titlePage = s.titlePage.copy(title = s.titlePage.title + " (copy)"),
            createdAt = System.currentTimeMillis(), modifiedAt = System.currentTimeMillis(),
        )
        save(copy, _library.value.firstOrNull { it.id == id }?.pages ?: 0)
        return copy
    }

    // ---------------------------------------------------------------- versions

    suspend fun saveVersion(script: Script, name: String, pages: Int, keep: Int) = withContext(Dispatchers.IO) {
        val vdir = File(versionsRoot, script.id).apply { mkdirs() }
        val obj = encode(script).put("versionName", name).put("versionPages", pages)
        File(vdir, "${System.currentTimeMillis()}.json").writeText(obj.toString())
        val all = vdir.listFiles().orEmpty().sortedByDescending { it.name }
        all.drop(keep.coerceAtLeast(1)).forEach { it.delete() }
    }

    suspend fun versions(id: String): List<VersionInfo> = withContext(Dispatchers.IO) {
        File(versionsRoot, id).listFiles().orEmpty().sortedByDescending { it.name }.mapNotNull { f ->
            runCatching {
                val o = JSONObject(f.readText())
                VersionInfo(f, o.optString("versionName", "Snapshot"), f.nameWithoutExtension.toLong(), o.optInt("versionPages", 0))
            }.getOrNull()
        }
    }

    suspend fun loadVersion(v: VersionInfo): Script? = withContext(Dispatchers.IO) {
        runCatching { decode(JSONObject(v.file.readText())) }.getOrNull()
    }

    suspend fun deleteVersion(v: VersionInfo) = withContext(Dispatchers.IO) { v.file.delete(); Unit }

    // ---------------------------------------------------------------- json

    private fun metaOf(s: Script, pages: Int): ScriptMeta {
        var words = 0; var scenes = 0
        for (e in s.elements) {
            if (e.type.printed) words += com.amit.pageone.core.Words.count(e.text)
            if (e.type == ElementType.SCENE_HEADING) scenes++
        }
        val logline = s.elements.firstOrNull { it.type == ElementType.SCENE_HEADING && it.synopsis.isNotBlank() }?.synopsis
            ?: s.elements.firstOrNull { it.type == ElementType.ACTION && it.text.isNotBlank() && it.text != "FADE IN:" }?.text ?: ""
        return ScriptMeta(s.id, s.title, s.titlePage.author, s.modifiedAt, s.createdAt, pages, scenes, words, s.coverColor, logline.take(140), s.extras.deadline, s.extras.lock.locked)
    }

    private fun readIndex(): Map<String, ScriptMeta> = runCatching {
        val arr = JSONArray(indexFile.readText())
        (0 until arr.length()).associate { i ->
            val o = arr.getJSONObject(i)
            val m = ScriptMeta(
                o.getString("id"), o.optString("title"), o.optString("author"), o.optLong("modifiedAt"), o.optLong("createdAt"),
                o.optInt("pages"), o.optInt("scenes"), o.optInt("words"), o.optInt("coverColor"), o.optString("logline"),
                o.optJSONObject("deadline")?.let { d -> com.amit.pageone.core.Deadline(d.optString("date"), d.optInt("pages"), d.optString("start"), d.optInt("startPages"), d.optString("label", "First draft")) },
                o.optBoolean("locked"),
            )
            m.id to m
        }
    }.getOrDefault(emptyMap())

    private fun writeIndex(list: List<ScriptMeta>) {
        val arr = JSONArray()
        list.forEach { m ->
            val o = JSONObject().put("id", m.id).put("title", m.title).put("author", m.author).put("modifiedAt", m.modifiedAt)
                .put("createdAt", m.createdAt).put("pages", m.pages).put("scenes", m.scenes).put("words", m.words)
                .put("coverColor", m.coverColor).put("logline", m.logline).put("locked", m.locked)
            m.deadline?.let { d -> o.put("deadline", JSONObject().put("date", d.date).put("pages", d.targetPages).put("start", d.startDate).put("startPages", d.startPages).put("label", d.label)) }
            arr.put(o)
        }
        runCatching { indexFile.writeText(arr.toString()) }
    }

    companion object {
        fun encodeTitlePage(tp: TitlePage): JSONObject {
            val cr = JSONArray()
            tp.credits.forEach { cr.put(JSONObject().put("role", it.role).put("names", it.names)) }
            return JSONObject().put("v", 2).put("title", tp.title).put("credits", cr)
                .put("isAdaptation", tp.isAdaptation).put("sourceType", tp.sourceType)
                .put("sourceTitle", tp.sourceTitle).put("sourceAuthor", tp.sourceAuthor)
                .put("draft", tp.draft).put("draftDate", tp.draftDate)
                .put("contactName", tp.contactName).put("email", tp.email).put("phone", tp.phone)
                .put("address", tp.address).put("representation", tp.representation)
                .put("copyright", tp.copyright).put("registration", tp.registration).put("notes", tp.notes)
        }

        fun decodeTitlePage(t: JSONObject, fallbackTitle: String): TitlePage {
            if (t.optInt("v", 1) < 2) {
                // Files from before multi-author title pages
                return TitlePage.simple(
                    title = t.optString("title", fallbackTitle), credit = t.optString("credit", "Written by"),
                    author = t.optString("author"), source = t.optString("source"), draftDate = t.optString("draftDate"),
                    contact = t.optString("contact"), notes = t.optString("notes"),
                )
            }
            val arr = t.optJSONArray("credits") ?: JSONArray()
            val credits = (0 until arr.length()).map { i ->
                val c = arr.getJSONObject(i); Credit(c.optString("role", "Written by"), c.optString("names"))
            }
            return TitlePage(
                title = t.optString("title", fallbackTitle),
                credits = credits.ifEmpty { listOf(Credit()) },
                isAdaptation = t.optBoolean("isAdaptation"), sourceType = t.optString("sourceType", "novel"),
                sourceTitle = t.optString("sourceTitle"), sourceAuthor = t.optString("sourceAuthor"),
                draft = t.optString("draft"), draftDate = t.optString("draftDate"),
                contactName = t.optString("contactName"), email = t.optString("email"), phone = t.optString("phone"),
                address = t.optString("address"), representation = t.optString("representation"),
                copyright = t.optString("copyright"), registration = t.optString("registration"), notes = t.optString("notes"),
            )
        }

        fun encode(s: Script): JSONObject {
            val els = JSONArray()
            s.elements.forEach { e ->
                val o = JSONObject().put("i", e.id).put("t", e.type.name).put("x", e.text)
                if (e.dual) o.put("d", true)
                if (e.revision != 0) o.put("r", e.revision)
                if (e.color != 0) o.put("c", e.color)
                if (e.synopsis.isNotEmpty()) o.put("s", e.synopsis)
                if (e.orig != null) o.put("o", e.orig)
                els.put(o)
            }
            val tp = s.titlePage
            return JSONObject()
                .put("format", 2)
                .put("id", s.id).put("title", s.title)
                .put("createdAt", s.createdAt).put("modifiedAt", s.modifiedAt)
                .put("revisionMode", s.revisionMode).put("revisionLevel", s.revisionLevel)
                .put("coverColor", s.coverColor).put("wordGoal", s.wordGoal)
                .put("revDates", JSONObject().also { o -> s.revisionDates.forEach { (k, v) -> o.put(k.toString(), v) } })
                .put("titlePage", encodeTitlePage(tp))
                .put("extras", ExtrasCodec.encode(s.extras))
                .put("elements", els)
        }

        fun decode(o: JSONObject): Script {
            val arr = o.getJSONArray("elements")
            val els = ArrayList<Element>(arr.length())
            for (i in 0 until arr.length()) {
                val e = arr.getJSONObject(i)
                els.add(
                    Element(
                        id = e.optLong("i", i + 1L),
                        type = ElementType.fromName(e.optString("t")),
                        text = e.optString("x"),
                        dual = e.optBoolean("d", false),
                        revision = e.optInt("r", 0),
                        color = e.optInt("c", 0),
                        synopsis = e.optString("s", ""),
                        orig = if (e.has("o")) e.optString("o") else null,
                    )
                )
            }
            val title = o.optString("title", "Untitled")
            return Script(
                id = o.getString("id"),
                title = title,
                elements = els.ifEmpty { listOf(Element(1, ElementType.SCENE_HEADING, "")) },
                titlePage = decodeTitlePage(o.optJSONObject("titlePage") ?: JSONObject(), title),
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                modifiedAt = o.optLong("modifiedAt", System.currentTimeMillis()),
                revisionMode = o.optBoolean("revisionMode", false),
                revisionLevel = o.optInt("revisionLevel", 1),
                coverColor = o.optInt("coverColor", 0),
                wordGoal = o.optInt("wordGoal", 0),
                revisionDates = o.optJSONObject("revDates")?.let { r ->
                    r.keys().asSequence().mapNotNull { k -> k.toIntOrNull()?.let { it to r.optString(k) } }.toMap()
                } ?: emptyMap(),
                extras = ExtrasCodec.decode(o.optJSONObject("extras")),
            )
        }
    }
}
