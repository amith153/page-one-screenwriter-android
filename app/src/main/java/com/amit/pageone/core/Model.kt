package com.amit.pageone.core

/**
 * Core screenplay data model. Pure Kotlin — no Android dependencies, so it can be
 * unit-tested on the JVM.
 */

enum class ElementType(val label: String, val shortLabel: String, val allCaps: Boolean, val printed: Boolean = true) {
    SCENE_HEADING("Scene Heading", "Scene", true),
    ACTION("Action", "Action", false),
    CHARACTER("Character", "Character", true),
    PARENTHETICAL("Parenthetical", "Paren", false),
    DIALOGUE("Dialogue", "Dialogue", false),
    TRANSITION("Transition", "Trans", true),
    SHOT("Shot", "Shot", true),
    LYRICS("Lyrics", "Lyrics", false),
    CENTERED("Centered", "Center", false),
    NOTE("Note", "Note", false, printed = false);

    val isDialogueBlockPart: Boolean get() = this == CHARACTER || this == PARENTHETICAL || this == DIALOGUE || this == LYRICS

    companion object {
        fun fromName(name: String?): ElementType = entries.firstOrNull { it.name == name } ?: ACTION
    }
}

/** Industry-standard revision colours, in order. Level 0 means "original / unrevised". */
enum class RevisionColor(val label: String, val argb: Long) {
    WHITE("White", 0xFFFFFFFF),
    BLUE("Blue", 0xFF6EA8FE),
    PINK("Pink", 0xFFF48FB1),
    YELLOW("Yellow", 0xFFFFE066),
    GREEN("Green", 0xFF81C784),
    GOLDENROD("Goldenrod", 0xFFE0B040),
    BUFF("Buff", 0xFFE8D3A9),
    SALMON("Salmon", 0xFFFF9E80),
    CHERRY("Cherry", 0xFFE57373);

    companion object {
        fun level(i: Int): RevisionColor = entries[i.coerceIn(0, entries.size - 1)]
    }
}

/** Colours a writer can tag a scene / index card with. 0 = none. */
val SCENE_COLORS: List<Long> = listOf(
    0x00000000, 0xFFE57373, 0xFFFFB74D, 0xFFFFE066, 0xFF81C784,
    0xFF4DD0E1, 0xFF6EA8FE, 0xFFB39DDB, 0xFFF48FB1, 0xFFA1887F,
)

/** A line taken from someone else's draft: if their file carries no stamp, credit it to them by name. */
fun Element.credited(name: String, now: Long = System.currentTimeMillis()): Element =
    if (by != null) this else copy(by = "guest:" + name.trim().lowercase(), byName = name, at = if (at != 0L) at else now)

data class Element(
    val id: Long,
    val type: ElementType,
    val text: String,
    /** On a CHARACTER element: this dialogue block is the right half of a dual-dialogue pair. */
    val dual: Boolean = false,
    /** Revision level (index into [RevisionColor]); 0 = unrevised. */
    val revision: Int = 0,
    /** Scene headings only: index into [SCENE_COLORS]. */
    val color: Int = 0,
    /** Scene headings only: one-line synopsis shown on index cards / navigator. */
    val synopsis: String = "",
    /**
     * The writer's own casing, kept while the text is shown in capitals (Character, Scene Heading…).
     * Lets a line go back to Dialogue/Action — by Undo, Backspace, Tab or Enter — as it was typed.
     */
    val orig: String? = null,
    /** Who last changed this line: an install's random author id, the name it wrote under, and when. */
    val by: String? = null,
    val byName: String? = null,
    val at: Long = 0L,
) {
    /** The text as the writer typed it (before any all-caps conversion). */
    val plain: String get() = if (orig != null && orig.uppercase() == text) orig else text

    /** This element as [type]: capitals for all-caps types (remembering the casing), the original casing otherwise. */
    fun retyped(type: ElementType): Element {
        val base = plain
        return if (type.allCaps) {
            val up = base.uppercase()
            copy(type = type, text = up, orig = base.takeIf { it != up })
        } else copy(type = type, text = base, orig = null)
    }
}

/** One writing credit on the title page, e.g. "Screenplay by" / "Amit & Priya". */
data class Credit(val role: String = "Written by", val names: String = "")

/**
 * Everything that can appear on a title page. All fields except the title are optional;
 * empty ones are simply not printed.
 */
data class TitlePage(
    val title: String = "",
    val credits: List<Credit> = listOf(Credit()),
    // Adaptation
    val isAdaptation: Boolean = false,
    val sourceType: String = "novel",
    val sourceTitle: String = "",
    val sourceAuthor: String = "",
    // Draft
    val draft: String = "",
    val draftDate: String = "",
    // Contact
    val contactName: String = "",
    val email: String = "",
    val phone: String = "",
    val address: String = "",
    val representation: String = "",
    // Legal
    val copyright: String = "",
    val registration: String = "",
    val notes: String = "",
) {
    /** All writer names, for lists and search. */
    val author: String get() = credits.map { it.names.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(" / ")

    /** "Based on the novel “Dune” by Frank Herbert" — or empty. */
    val basedOnLine: String get() {
        if (!isAdaptation) return ""
        val t = sourceTitle.trim(); val a = sourceAuthor.trim()
        if (t.isEmpty() && a.isEmpty()) return ""
        val kind = sourceType.trim()
        val sb = StringBuilder("Based on")
        if (kind.isNotEmpty() && kind != "other") sb.append(if (kind.startsWith("true")) " " else " the ").append(kind)
        if (t.isNotEmpty()) sb.append(if (kind == "other") " $t" else " “$t”")
        if (a.isNotEmpty()) sb.append(" by ").append(a)
        return sb.toString()
    }

    /** Lines for the bottom-left contact block. */
    val contactLines: List<String> get() = buildList {
        if (contactName.isNotBlank()) add(contactName.trim())
        address.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { add(it) }
        if (phone.isNotBlank()) add(phone.trim())
        if (email.isNotBlank()) add(email.trim())
        if (representation.isNotBlank()) { add(""); representation.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { add(it) } }
    }

    companion object {
        val ROLES = listOf("Written by", "Screenplay by", "Story by", "Teleplay by", "Created by", "Adapted by")
        val SOURCE_TYPES = listOf("novel", "short story", "play", "article", "comic", "film", "book", "true events", "other")

        /** Build from the simple fields older files and Fountain/FDX use. */
        fun simple(title: String, credit: String, author: String, source: String = "", draftDate: String = "",
                   contact: String = "", notes: String = "", copyright: String = ""): TitlePage {
            val src = source.trim()
            return TitlePage(
                title = title,
                credits = listOf(Credit(credit.ifBlank { "Written by" }, author)),
                isAdaptation = src.isNotEmpty(),
                sourceType = "other",
                sourceTitle = src.removePrefix("Based on").trim(),
                draftDate = draftDate,
                address = contact,
                notes = notes,
                copyright = copyright,
            )
        }
    }
}

data class Script(
    val id: String,
    val title: String,
    val elements: List<Element>,
    val titlePage: TitlePage = TitlePage(title = title),
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    val revisionMode: Boolean = false,
    /** The revision level new edits are marked with when [revisionMode] is on. */
    val revisionLevel: Int = 1,
    val coverColor: Int = 0,
    val wordGoal: Int = 0,
    /** Revision sets: level → the date it was issued ("2026-10-18"). Blue = 1, Pink = 2, … */
    val revisionDates: Map<Int, String> = emptyMap(),
    /** Bible, structure, threads, breakdown, mood boards, notes, lock, deadline. */
    val extras: ScriptExtras = ScriptExtras(),
) {
    val nextId: Long get() = (elements.maxOfOrNull { it.id } ?: 0L) + 1L
}

/** Simple, process-wide id generator for new elements. Seeded from the loaded script. */
/**
 * Element ids. Random 52-bit values so that two co-writers creating elements at the
 * same time on different phones can never collide.
 */
class IdGen(@Suppress("UNUSED_PARAMETER") start: Long = 0) {
    private val rnd = java.security.SecureRandom()
    fun next(): Long = (rnd.nextLong() ushr 12).coerceAtLeast(1_000_000L)
    fun ensureAbove(@Suppress("UNUSED_PARAMETER") v: Long) {}
}

object Words {
    private val re = Regex("[\\p{L}\\p{N}'’]+")
    fun count(s: String): Int = re.findAll(s).count()
}
