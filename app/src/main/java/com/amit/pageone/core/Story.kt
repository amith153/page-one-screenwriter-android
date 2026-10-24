package com.amit.pageone.core

/**
 * Everything about a script that isn't the screenplay text itself: the character & world
 * bible, story structure, plot threads, per-scene breakdown and mood images, anchored
 * notes, production lock and the writer's deadline. Pure Kotlin, stored alongside the
 * elements in the script file and (except local images) shared in live sessions.
 */

data class CharacterProfile(
    /** Upper-case cue name, e.g. "MAYA". */
    val name: String,
    val role: String = "",
    val age: String = "",
    val want: String = "",
    val need: String = "",
    val arc: String = "",
    val voice: String = "",
    val bio: String = "",
    /** File name in the media folder (this phone only). */
    val photo: String = "",
    /** Table read: TTS voice name ("" = automatic), pitch and speed multipliers. */
    val ttsVoice: String = "",
    val pitch: Float = 1f,
    val rate: Float = 1f,
    /** Table read with studio (cloud) voices: the voice name, "" = automatic. */
    val cloudVoice: String = "",
)

data class LocationProfile(
    /** As it appears in scene headings, e.g. "MAYA'S APARTMENT". */
    val name: String,
    val description: String = "",
    val notes: String = "",
    val photo: String = "",
)

/** An A/B/C storyline. [color] indexes [SCENE_COLORS]. */
data class PlotThread(val id: String, val name: String, val color: Int)

/** One beat of a structure template. [target] is where it should land, as a fraction of the script (0..1). */
data class Beat(
    val id: String,
    val name: String,
    val description: String,
    val target: Float,
    val act: Int,
    /** Scene-heading ids assigned to this beat. */
    val sceneIds: List<Long> = emptyList(),
)

data class Structure(val template: String, val beats: List<Beat>, val targetPages: Int = 110)

/** Script-breakdown categories, as on a production breakdown sheet. */
enum class BreakdownCategory(val label: String) {
    CAST("Cast"), EXTRAS("Extras"), PROPS("Props"), WARDROBE("Wardrobe"), MAKEUP("Hair & make-up"),
    VEHICLES("Vehicles"), SFX("Special effects"), SOUND("Sound & music"), SET("Set dressing"), ANIMALS("Animals"),
}

data class SceneMeta(
    /** [PlotThread.id]s this scene belongs to. */
    val threads: List<String> = emptyList(),
    val breakdown: Map<BreakdownCategory, List<String>> = emptyMap(),
    /** Mood-board image file names (media folder, this phone only). */
    val moods: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = threads.isEmpty() && breakdown.values.all { it.isEmpty() } && moods.isEmpty()
}

data class MoodImage(val file: String, val caption: String = "")

data class Reply(val author: String, val color: Long, val text: String, val at: Long)

enum class AttachmentKind { IMAGE, AUDIO, VIDEO, LINK }

/** A picture, music, video or voice memo (a file on the writer's phone), or a web link ([file] holds the URL). */
data class Attachment(val file: String, val kind: AttachmentKind, val name: String = "", val durationMs: Long = 0)

/** A note pinned to a range of text, with a thread of replies. */
data class Comment(
    val id: String,
    val elementId: Long,
    val start: Int,
    val end: Int,
    /** The text the note was pinned to — used to re-find the range after edits. */
    val quote: String,
    val author: String,
    val color: Long,
    val text: String,
    val at: Long,
    val resolved: Boolean = false,
    val replies: List<Reply> = emptyList(),
    /** Reference pictures, music and voice memos: how you want it to look or sound. */
    val attachments: List<Attachment> = emptyList(),
)

/** Production lock: scene numbers and page breaks frozen as they were when locked. */
data class LockState(
    val locked: Boolean = false,
    val lockedAt: Long = 0,
    /** Scene-heading id → its locked number ("12", "12A"). */
    val sceneNumbers: Map<Long, String> = emptyMap(),
    /** Element ids that began pages 2, 3, 4… when the script was locked. */
    val pageStarts: List<Long> = emptyList(),
)

/** "Finish by" plan for this script. Dates are yyyy-MM-dd. */
data class Deadline(
    val date: String,
    val targetPages: Int,
    val startDate: String,
    val startPages: Int,
    val label: String = "First draft",
)

data class ScriptExtras(
    val characters: List<CharacterProfile> = emptyList(),
    val locations: List<LocationProfile> = emptyList(),
    val threads: List<PlotThread> = emptyList(),
    val structure: Structure? = null,
    val scenes: Map<Long, SceneMeta> = emptyMap(),
    val board: List<MoodImage> = emptyList(),
    val comments: List<Comment> = emptyList(),
    val lock: LockState = LockState(),
    val deadline: Deadline? = null,
) {
    fun character(name: String): CharacterProfile? = characters.firstOrNull { it.name == name }
    fun location(name: String): LocationProfile? = locations.firstOrNull { it.name == name }
    fun scene(id: Long): SceneMeta = scenes[id] ?: SceneMeta()

    fun withCharacter(p: CharacterProfile) = copy(characters = characters.filter { it.name != p.name } + p)
    fun withLocation(p: LocationProfile) = copy(locations = locations.filter { it.name != p.name } + p)
    fun withScene(id: Long, m: SceneMeta) = copy(scenes = if (m.isEmpty) scenes - id else scenes + (id to m))

    /** Drop per-scene data for scenes that no longer exist. */
    fun prune(elements: List<Element>): ScriptExtras {
        val heads = elements.asSequence().filter { it.type == ElementType.SCENE_HEADING }.map { it.id }.toHashSet()
        val sc = scenes.filterKeys { it in heads }
        val st = structure?.let { s -> s.copy(beats = s.beats.map { b -> b.copy(sceneIds = b.sceneIds.filter { it in heads }) }) }
        return if (sc.size == scenes.size && st == structure) this else copy(scenes = sc, structure = st)
    }
}

/** Find a comment's current range in [text]: its stored range if the quote is still there, else the quote's new position. */
fun Comment.rangeIn(text: String): IntRange? {
    if (quote.isEmpty()) return null
    if (start >= 0 && end <= text.length && start < end && text.regionMatches(start, quote, 0, quote.length)) return start until end
    val i = text.indexOf(quote)
    if (i >= 0) return i until i + quote.length
    return null
}
