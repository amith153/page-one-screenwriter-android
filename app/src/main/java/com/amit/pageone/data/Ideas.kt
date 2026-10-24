package com.amit.pageone.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class IdeaKind(val label: String) { LINE("Dialogue"), SCENE("Scene"), CHARACTER("Character"), IMAGE("Image"), THOUGHT("Thought"), VOICE("Voice memo") }

data class Idea(
    val id: String,
    val text: String,
    val kind: IdeaKind = IdeaKind.THOUGHT,
    val createdAt: Long = System.currentTimeMillis(),
    /** "share", "voice", "widget", "app". */
    val source: String = "app",
    /** Set once it has been used in a script. */
    val usedIn: String = "",
    val pinned: Boolean = false,
    /** Voice ideas: the recording (media folder) and its length. */
    val audio: String = "",
    val durationMs: Long = 0,
)

/** The idea inbox: lines, scenes and thoughts captured from anywhere, waiting to be used. One small JSON file. */
class IdeaStore(context: Context) {
    private val file = File(context.filesDir, "ideas.json")
    private val _ideas = MutableStateFlow(read())
    val ideas: StateFlow<List<Idea>> = _ideas.asStateFlow()

    /** Ideas not yet used in a script. */
    val waiting: Int get() = _ideas.value.count { it.usedIn.isEmpty() }

    fun add(text: String, kind: IdeaKind = guessKind(text), source: String = "app", audio: String = "", durationMs: Long = 0): Idea? {
        val t = text.trim()
        if (t.isEmpty() && audio.isEmpty()) return null
        val idea = Idea(java.util.UUID.randomUUID().toString(), t, if (audio.isNotEmpty()) IdeaKind.VOICE else kind, source = source, audio = audio, durationMs = durationMs)
        write(listOf(idea) + _ideas.value)
        return idea
    }

    fun update(idea: Idea) = write(_ideas.value.map { if (it.id == idea.id) idea else it })
    fun delete(id: String) = write(_ideas.value.filter { it.id != id })
    fun markUsed(id: String, scriptTitle: String) = write(_ideas.value.map { if (it.id == id) it.copy(usedIn = scriptTitle) else it })

    /** Re-read the file (another process — the share sheet or widget — may have written it). */
    fun reload() { _ideas.value = read() }

    @Synchronized private fun write(list: List<Idea>) {
        _ideas.value = list
        val a = JSONArray()
        list.forEach { i ->
            a.put(JSONObject().put("id", i.id).put("t", i.text).put("k", i.kind.name).put("at", i.createdAt)
                .put("src", i.source).put("used", i.usedIn).put("pin", i.pinned).put("au", i.audio).put("dur", i.durationMs))
        }
        val tmp = File(file.parentFile, "ideas.json.tmp")
        tmp.writeText(a.toString())
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }

    private fun read(): List<Idea> = runCatching {
        val a = JSONArray(file.readText())
        (0 until a.length()).map { k ->
            val o = a.getJSONObject(k)
            Idea(
                o.getString("id"), o.optString("t"), runCatching { IdeaKind.valueOf(o.optString("k")) }.getOrDefault(IdeaKind.THOUGHT),
                o.optLong("at"), o.optString("src", "app"), o.optString("used"), o.optBoolean("pin"), o.optString("au"), o.optLong("dur"),
            )
        }
    }.getOrDefault(emptyList())

    companion object {
        /** A quoted line reads as dialogue, "INT./EXT." as a scene. */
        fun guessKind(t: String): IdeaKind {
            val s = t.trim()
            return when {
                Regex("^(INT|EXT|INT\\./EXT|I/E)[ .]", RegexOption.IGNORE_CASE).containsMatchIn(s) -> IdeaKind.SCENE
                s.startsWith("\"") || s.startsWith("“") -> IdeaKind.LINE
                else -> IdeaKind.THOUGHT
            }
        }
    }
}
