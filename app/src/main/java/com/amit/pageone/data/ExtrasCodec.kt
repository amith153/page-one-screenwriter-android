package com.amit.pageone.data

import com.amit.pageone.core.Beat
import com.amit.pageone.core.BreakdownCategory
import com.amit.pageone.core.CharacterProfile
import com.amit.pageone.core.Comment
import com.amit.pageone.core.Deadline
import com.amit.pageone.core.LocationProfile
import com.amit.pageone.core.LockState
import com.amit.pageone.core.MoodImage
import com.amit.pageone.core.PlotThread
import com.amit.pageone.core.Reply
import com.amit.pageone.core.SceneMeta
import com.amit.pageone.core.ScriptExtras
import com.amit.pageone.core.Structure
import org.json.JSONArray
import org.json.JSONObject

/** JSON for [ScriptExtras]. Every field is optional on read, so older files and partial syncs load fine. */
object ExtrasCodec {

    private fun strings(a: JSONArray?): List<String> = if (a == null) emptyList() else (0 until a.length()).map { a.optString(it) }
    private fun arr(l: List<String>) = JSONArray().also { a -> l.forEach { a.put(it) } }
    private inline fun <T> objs(a: JSONArray?, f: (JSONObject) -> T?): List<T> =
        if (a == null) emptyList() else (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let(f) }

    fun encode(x: ScriptExtras, includeComments: Boolean = true, includeLocalMedia: Boolean = true): JSONObject {
        val o = JSONObject()
        if (x.characters.isNotEmpty()) o.put("chars", JSONArray().also { a ->
            x.characters.forEach { c ->
                a.put(JSONObject().put("n", c.name).put("role", c.role).put("age", c.age).put("want", c.want).put("need", c.need)
                    .put("arc", c.arc).put("voice", c.voice).put("bio", c.bio).put("photo", if (includeLocalMedia) c.photo else "")
                    .put("tts", c.ttsVoice).put("pitch", c.pitch.toDouble()).put("rate", c.rate.toDouble()).put("cloud", c.cloudVoice))
            }
        })
        if (x.locations.isNotEmpty()) o.put("locs", JSONArray().also { a ->
            x.locations.forEach { l -> a.put(JSONObject().put("n", l.name).put("d", l.description).put("notes", l.notes).put("photo", if (includeLocalMedia) l.photo else "")) }
        })
        if (x.threads.isNotEmpty()) o.put("threads", JSONArray().also { a -> x.threads.forEach { t -> a.put(JSONObject().put("id", t.id).put("n", t.name).put("c", t.color)) } })
        x.structure?.let { s ->
            o.put("structure", JSONObject().put("t", s.template).put("pages", s.targetPages).put("beats", JSONArray().also { a ->
                s.beats.forEach { b ->
                    a.put(JSONObject().put("id", b.id).put("n", b.name).put("d", b.description).put("f", b.target.toDouble()).put("act", b.act)
                        .put("s", JSONArray().also { sa -> b.sceneIds.forEach { sa.put(it) } }))
                }
            }))
        }
        if (x.scenes.isNotEmpty()) o.put("scenes", JSONObject().also { so ->
            x.scenes.forEach { (id, m) ->
                val mo = JSONObject()
                if (m.threads.isNotEmpty()) mo.put("th", arr(m.threads))
                if (m.breakdown.isNotEmpty()) mo.put("bd", JSONObject().also { b -> m.breakdown.forEach { (k, v) -> if (v.isNotEmpty()) b.put(k.name, arr(v)) } })
                if (includeLocalMedia && m.moods.isNotEmpty()) mo.put("mood", arr(m.moods))
                if (mo.length() > 0) so.put(id.toString(), mo)
            }
        })
        if (includeLocalMedia && x.board.isNotEmpty()) o.put("board", JSONArray().also { a -> x.board.forEach { m -> a.put(JSONObject().put("f", m.file).put("c", m.caption)) } })
        if (includeComments && x.comments.isNotEmpty()) o.put("comments", JSONArray().also { a -> x.comments.forEach { a.put(encodeComment(it)) } })
        if (x.lock.locked) o.put("lock", JSONObject().put("at", x.lock.lockedAt)
            .put("scenes", JSONObject().also { so -> x.lock.sceneNumbers.forEach { (k, v) -> so.put(k.toString(), v) } })
            .put("pages", JSONArray().also { a -> x.lock.pageStarts.forEach { a.put(it) } }))
        x.deadline?.let { d -> o.put("deadline", JSONObject().put("date", d.date).put("pages", d.targetPages).put("start", d.startDate).put("startPages", d.startPages).put("label", d.label)) }
        return o
    }

    fun encodeComment(c: Comment): JSONObject = JSONObject().put("id", c.id).put("e", c.elementId).put("s", c.start).put("x", c.end)
        .put("q", c.quote).put("a", c.author).put("c", c.color).put("t", c.text).put("at", c.at).put("r", c.resolved)
        .put("rp", JSONArray().also { a -> c.replies.forEach { r -> a.put(JSONObject().put("a", r.author).put("c", r.color).put("t", r.text).put("at", r.at)) } })
        .put("att", JSONArray().also { a -> c.attachments.forEach { m -> a.put(JSONObject().put("f", m.file).put("k", m.kind.name).put("n", m.name).put("d", m.durationMs)) } })

    fun decodeComment(o: JSONObject): Comment? {
        val id = o.optString("id").ifEmpty { return null }
        return Comment(
            id, o.optLong("e"), o.optInt("s"), o.optInt("x"), o.optString("q"), o.optString("a"), o.optLong("c", 0xFF4D9DFF),
            o.optString("t"), o.optLong("at"), o.optBoolean("r"),
            objs(o.optJSONArray("rp")) { r -> Reply(r.optString("a"), r.optLong("c", 0xFF4D9DFF), r.optString("t"), r.optLong("at")) },
            objs(o.optJSONArray("att")) { m ->
                runCatching { com.amit.pageone.core.AttachmentKind.valueOf(m.optString("k")) }.getOrNull()?.let { k ->
                    com.amit.pageone.core.Attachment(m.optString("f"), k, m.optString("n"), m.optLong("d"))
                }
            },
        )
    }

    fun decode(o: JSONObject?): ScriptExtras {
        if (o == null) return ScriptExtras()
        return ScriptExtras(
            characters = objs(o.optJSONArray("chars")) { c ->
                CharacterProfile(
                    c.optString("n"), c.optString("role"), c.optString("age"), c.optString("want"), c.optString("need"), c.optString("arc"),
                    c.optString("voice"), c.optString("bio"), c.optString("photo"), c.optString("tts"),
                    c.optDouble("pitch", 1.0).toFloat(), c.optDouble("rate", 1.0).toFloat(), c.optString("cloud"),
                )
            },
            locations = objs(o.optJSONArray("locs")) { l -> LocationProfile(l.optString("n"), l.optString("d"), l.optString("notes"), l.optString("photo")) },
            threads = objs(o.optJSONArray("threads")) { t -> PlotThread(t.optString("id"), t.optString("n"), t.optInt("c")) },
            structure = o.optJSONObject("structure")?.let { s ->
                Structure(
                    s.optString("t"),
                    objs(s.optJSONArray("beats")) { b ->
                        val sa = b.optJSONArray("s")
                        Beat(b.optString("id"), b.optString("n"), b.optString("d"), b.optDouble("f", 0.0).toFloat(), b.optInt("act", 1),
                            if (sa == null) emptyList() else (0 until sa.length()).map { sa.optLong(it) })
                    },
                    s.optInt("pages", 110),
                )
            },
            scenes = o.optJSONObject("scenes")?.let { so ->
                so.keys().asSequence().mapNotNull { k ->
                    val id = k.toLongOrNull() ?: return@mapNotNull null
                    val m = so.optJSONObject(k) ?: return@mapNotNull null
                    val bd = m.optJSONObject("bd")?.let { b ->
                        b.keys().asSequence().mapNotNull { c -> runCatching { BreakdownCategory.valueOf(c) }.getOrNull()?.let { it to strings(b.optJSONArray(c)) } }.toMap()
                    } ?: emptyMap()
                    id to SceneMeta(strings(m.optJSONArray("th")), bd, strings(m.optJSONArray("mood")))
                }.toMap()
            } ?: emptyMap(),
            board = objs(o.optJSONArray("board")) { m -> MoodImage(m.optString("f"), m.optString("c")) },
            comments = objs(o.optJSONArray("comments")) { decodeComment(it) },
            lock = o.optJSONObject("lock")?.let { l ->
                val so = l.optJSONObject("scenes") ?: JSONObject()
                val pa = l.optJSONArray("pages")
                LockState(
                    true, l.optLong("at"),
                    so.keys().asSequence().mapNotNull { k -> k.toLongOrNull()?.let { it to so.optString(k) } }.toMap(),
                    if (pa == null) emptyList() else (0 until pa.length()).map { pa.optLong(it) },
                )
            } ?: LockState(),
            deadline = o.optJSONObject("deadline")?.let { d ->
                Deadline(d.optString("date"), d.optInt("pages", 110), d.optString("start"), d.optInt("startPages"), d.optString("label", "First draft"))
            },
        )
    }

    /**
     * Merge shared extras from a co-writer into ours: their story data wins, but our
     * phone-only images (photos, mood boards) and our comment list are kept.
     */
    fun mergeRemote(local: ScriptExtras, remote: ScriptExtras): ScriptExtras = remote.copy(
        characters = remote.characters.map { r -> r.copy(photo = local.character(r.name)?.photo ?: "") },
        locations = remote.locations.map { r -> r.copy(photo = local.location(r.name)?.photo ?: "") },
        scenes = (remote.scenes.keys + local.scenes.keys).associateWith { id ->
            (remote.scenes[id] ?: SceneMeta()).copy(moods = local.scenes[id]?.moods ?: emptyList())
        }.filterValues { !it.isEmpty },
        board = local.board,
        comments = local.comments,
    )
}
