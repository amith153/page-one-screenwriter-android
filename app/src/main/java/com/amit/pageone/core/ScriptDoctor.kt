package com.amit.pageone.core

/**
 * Script Doctor: a format and consistency check of the whole script, the way a script
 * coordinator would read it before it goes out. Pure Kotlin and deterministic — no network,
 * no AI — so the same script always gets the same report.
 *
 * Every issue points at an element (so the UI can jump to it) and may carry fixes. A fix is
 * a pure function from the element list to a new one; it finds its target by id, so it stays
 * correct even if the script changed after the check ran, and returns the list unchanged
 * (the same instance) when there is nothing left to fix.
 */

enum class Severity(val label: String) {
    PROBLEM("Problems"),
    CHECK("Consistency"),
    POLISH("Polish"),
}

enum class Rule(val title: String, val severity: Severity, val why: String, val batchSafe: Boolean = false) {
    CUE_WITHOUT_DIALOGUE(
        "Character with nothing to say", Severity.PROBLEM,
        "A character cue must be followed by dialogue or a parenthetical, or it prints as a stray name.",
    ),
    ORPHAN_DIALOGUE(
        "Dialogue without a speaker", Severity.PROBLEM,
        "Dialogue and parentheticals need a character cue above them.",
    ),
    PAREN_ENDS_SPEECH(
        "Parenthetical ends a speech", Severity.PROBLEM,
        "A parenthetical can't be the last thing in a speech. If it describes what someone does, make it action.",
    ),
    CHARACTER_VARIANT(
        "Character name spelt two ways", Severity.CHECK,
        "Readers, reports and table reads treat each spelling as a different person.",
    ),
    LOCATION_VARIANT(
        "Location spelt two ways", Severity.CHECK,
        "Scheduling and location reports treat each spelling as a different place.",
    ),
    HEADING_NO_PREFIX(
        "Scene heading without INT. / EXT.", Severity.CHECK,
        "Scene headings start with INT., EXT. or INT./EXT. so production knows where it's shot.",
    ),
    CUE_LOOKS_LIKE_ACTION(
        "Character cue that reads like action", Severity.CHECK,
        "This looks like a sentence, not a name — it may have been typed as the wrong element.",
    ),
    PAGE_BREAK_ORPHAN(
        "Awkward page break", Severity.CHECK,
        "A heading or character name stranded at the foot of a page, or a single widowed line at the top of the next, reads badly in print.",
    ),
    TRANSITION_NOT_INTO_SCENE(
        "Transition that doesn't lead anywhere", Severity.CHECK,
        "A “TO:” transition should be followed by a new scene heading.",
    ),
    TITLE_NO_AUTHOR(
        "No writer on the title page", Severity.CHECK,
        "The title page should say who wrote the script.",
    ),
    HEADING_NO_TIME(
        "Scene heading without a time of day", Severity.POLISH,
        "End scene headings with – DAY, – NIGHT, – CONTINUOUS… It drives the schedule and the day/night breakdown.",
    ),
    HEADING_PREFIX_FORMAT(
        "INT / EXT without a period", Severity.POLISH,
        "The standard form is “INT.” and “EXT.”.", batchSafe = true,
    ),
    EXTENSION_FORMAT(
        "Non-standard character extension", Severity.POLISH,
        "Use the standard forms: (V.O.), (O.S.), (O.C.), (CONT'D).", batchSafe = true,
    ),
    PAREN_UNWRAPPED(
        "Parenthetical without brackets", Severity.POLISH,
        "Parentheticals print inside ( ).", batchSafe = true,
    ),
    TRANSITION_PUNCTUATION(
        "Transition without punctuation", Severity.POLISH,
        "Transitions end with a colon (CUT TO:) — or a period for FADE OUT.", batchSafe = true,
    ),
    LONG_ACTION(
        "Dense action paragraph", Severity.POLISH,
        "Action longer than four lines is often skimmed. Shorter paragraphs read faster and feel like shots.",
    ),
    LONG_PARENTHETICAL(
        "Long parenthetical", Severity.POLISH,
        "Parentheticals work best as a word or two. Longer stage directions usually belong in action.",
    ),
    LONG_SPEECH(
        "Very long speech", Severity.POLISH,
        "Fine for a monologue — but worth a look: long speeches are where scripts slow down.",
    ),
    STRAY_SPACES(
        "Extra spaces", Severity.POLISH,
        "Double or trailing spaces can shift text in the PDF.", batchSafe = true,
    ),
    EMPTY_ELEMENT(
        "Empty line", Severity.POLISH,
        "Empty elements print as extra blank space.", batchSafe = true,
    ),
    NOTE_LEFT(
        "Note to self", Severity.POLISH,
        "Notes never print, but it's worth clearing them before you send a draft.",
    ),
    TITLE_NO_CONTACT(
        "No contact details on the title page", Severity.POLISH,
        "A spec script should say how to reach you (or your representation).",
    ),
}

/** A fix. [apply] gets the current elements and a source of fresh element ids. */
class Fix(val label: String, val apply: (List<Element>, () -> Long) -> List<Element>)

data class Issue(
    val rule: Rule,
    /** The element to jump to; null for title-page issues. */
    val elementId: Long?,
    val message: String,
    /** What the issue is about, for a stable ignore key (a name, a location…). */
    val subject: String = "",
    val fixes: List<Fix> = emptyList(),
) {
    val key: String get() = "${rule.name}:${elementId ?: "title"}:$subject"
}

object ScriptDoctor {

    const val ACTION_MAX_LINES = 4
    const val PAREN_MAX_CHARS = 50
    const val SPEECH_MAX_LINES = 18

    private val printedDialogue = setOf(ElementType.DIALOGUE, ElementType.PARENTHETICAL, ElementType.LYRICS)

    /** Headings that are legitimately not INT./EXT. */
    private val specialHeadings = listOf(
        "MONTAGE", "FLASHBACK", "SERIES OF SHOTS", "INTERCUT", "DREAM", "BACK TO", "LATER", "END OF",
        "TITLE", "SUPER", "BLACK", "OPENING", "CREDITS", "PRELAP", "INSERT", "ANGLE", "FLASH", "OMITTED",
    )
    private val prefixNoPeriod = Regex("^(INT|EXT)(?=\\s)", RegexOption.IGNORE_CASE)
    private val timeSeparator = Regex("\\s[-–—]\\s*\\S")
    private val extension = Regex("\\(([^)]*)\\)")
    private val extensionForms = listOf(
        Regex("^V\\.?\\s?O\\.?$", RegexOption.IGNORE_CASE) to "V.O.",
        Regex("^O\\.?\\s?S\\.?$", RegexOption.IGNORE_CASE) to "O.S.",
        Regex("^O\\.?\\s?C\\.?$", RegexOption.IGNORE_CASE) to "O.C.",
        Regex("^CONT['’]?\\s?D\\.?$|^CONTD\\.?$|^CONT\\.?$", RegexOption.IGNORE_CASE) to "CONT'D",
    )
    private val sentenceEnd = Regex("(?<=[.!?…][\"”’)]?)\\s+")

    fun check(script: Script, pagination: Pagination? = null): List<Issue> {
        val els = script.elements
        val out = ArrayList<Issue>()
        if (pagination != null) pageBreaks(els, pagination, out)
        val lastIndex = els.lastIndex

        // ---- per element
        for (i in els.indices) {
            val e = els[i]
            val text = e.text
            val blank = text.isBlank() || (e.type == ElementType.PARENTHETICAL && text.trim() == "()")

            if (blank) {
                // The last element is where the writer is typing; a lone blank action in a
                // scene is the placeholder an index card creates.
                if (i != lastIndex && !isPlaceholder(els, i) && e.type != ElementType.NOTE) {
                    out += Issue(Rule.EMPTY_ELEMENT, e.id, "Empty ${e.type.label.lowercase()}", fixes = listOf(Fix("Remove") { l, _ -> edit(l, e.id) { null } }))
                }
                continue
            }

            if (e.type != ElementType.NOTE && hasStraySpaces(text)) {
                out += Issue(Rule.STRAY_SPACES, e.id, "“${preview(text)}”", fixes = listOf(Fix("Tidy spaces") { l, _ -> edit(l, e.id) { it.copy(text = tidySpaces(it.text)) } }))
            }

            when (e.type) {
                ElementType.SCENE_HEADING -> checkHeading(e, out)
                ElementType.CHARACTER -> checkCue(els, i, out)
                ElementType.DIALOGUE, ElementType.LYRICS, ElementType.PARENTHETICAL -> {
                    val prev = els.getOrNull(i - 1)?.type
                    if (prev == null || !prev.isDialogueBlockPart) {
                        out += Issue(Rule.ORPHAN_DIALOGUE, e.id, "“${preview(text)}”", fixes = listOf(Fix("Make it action") { l, _ -> edit(l, e.id) { toAction(it) } }))
                    }
                    if (e.type == ElementType.PARENTHETICAL) checkParenthetical(els, i, out)
                    if (e.type == ElementType.DIALOGUE) {
                        val lines = TextWrap.wrap(text, 35).size
                        if (lines > SPEECH_MAX_LINES) out += Issue(Rule.LONG_SPEECH, e.id, "$lines lines of dialogue")
                    }
                }
                ElementType.ACTION -> {
                    val lines = TextWrap.wrap(text, Specs.FULL_WIDTH).size
                    if (lines > ACTION_MAX_LINES) {
                        val parts = splitAction(text)
                        val fixes = if (parts.size > 1) listOf(Fix("Split into ${parts.size} paragraphs") { l, next -> splitElement(l, e.id, next) }) else emptyList()
                        out += Issue(Rule.LONG_ACTION, e.id, "$lines lines — “${preview(text)}”", fixes = fixes)
                    }
                }
                ElementType.TRANSITION -> checkTransition(els, i, out)
                ElementType.NOTE -> out += Issue(Rule.NOTE_LEFT, e.id, "“${preview(text)}”", fixes = listOf(Fix("Remove note") { l, _ -> edit(l, e.id) { null } }))
                else -> {}
            }
        }

        // ---- whole-script consistency
        nameVariants(els, out)
        locationVariants(els, out)

        // ---- title page
        val tp = script.titlePage
        if (tp.author.isBlank()) out += Issue(Rule.TITLE_NO_AUTHOR, null, "Add your name in the title page")
        if (tp.contactLines.isEmpty()) out += Issue(Rule.TITLE_NO_CONTACT, null, "Add an email, phone or agent")

        return out.sortedWith(compareBy({ it.rule.severity.ordinal }, { it.rule.ordinal }))
    }

    /** Apply the first fix of every batch-safe issue, in one go. */
    fun fixAll(elements: List<Element>, issues: List<Issue>, nextId: () -> Long): List<Element> {
        var list = elements
        for (iss in issues) if (iss.rule.batchSafe) iss.fixes.firstOrNull()?.let { list = it.apply(list, nextId) }
        return list
    }

    // ------------------------------------------------------------------ element checks

    private fun checkHeading(e: Element, out: MutableList<Issue>) {
        val t = e.text.trim().uppercase()
        if (!Formatting.looksLikeSceneHeading(t)) {
            if (specialHeadings.none { t.contains(it) }) {
                out += Issue(
                    Rule.HEADING_NO_PREFIX, e.id, t,
                    fixes = listOf(
                        Fix("Add INT.") { l, _ -> edit(l, e.id) { it.copy(text = "INT. " + it.text.trim()) } },
                        Fix("Add EXT.") { l, _ -> edit(l, e.id) { it.copy(text = "EXT. " + it.text.trim()) } },
                    ),
                )
            }
            return
        }
        if (prefixNoPeriod.containsMatchIn(t)) {
            out += Issue(Rule.HEADING_PREFIX_FORMAT, e.id, t, fixes = listOf(Fix("Add the period") { l, _ ->
                edit(l, e.id) { it.copy(text = prefixNoPeriod.replaceFirst(it.text.trim(), "$1.")) }
            }))
        }
        if (!timeSeparator.containsMatchIn(t) && Formatting.timesOfDay.none { t.endsWith(" $it") }) {
            fun add(tod: String) = Fix("Add – $tod") { l, _ -> edit(l, e.id) { it.copy(text = it.text.trimEnd() + " - $tod") } }
            out += Issue(Rule.HEADING_NO_TIME, e.id, t, fixes = listOf(add("DAY"), add("NIGHT"), add("CONTINUOUS")))
        }
    }

    private fun checkCue(els: List<Element>, i: Int, out: MutableList<Issue>) {
        val e = els[i]
        val next = els.getOrNull(i + 1)
        if (next == null || next.type !in printedDialogue) {
            if (next != null) {
                val fixes = buildList {
                    if (next.type == ElementType.ACTION && next.text.isNotBlank())
                        add(Fix("Make the next line dialogue") { l, _ -> edit(l, next.id) { it.copy(type = ElementType.DIALOGUE) } })
                    add(Fix("Make it action") { l, _ -> edit(l, e.id) { toAction(it) } })
                }
                out += Issue(Rule.CUE_WITHOUT_DIALOGUE, e.id, e.text.trim(), fixes = fixes)
            }
        }

        // Extensions: (VO) → (V.O.)
        val fixed = canonExtensions(e.text)
        if (fixed != e.text) {
            out += Issue(Rule.EXTENSION_FORMAT, e.id, "${e.text.trim()}  →  ${fixed.trim()}", fixes = listOf(Fix("Fix") { l, _ ->
                edit(l, e.id) { it.copy(text = canonExtensions(it.text)) }
            }))
        }

        // "HE WALKS TO THE DOOR." typed as a cue.
        val name = Formatting.characterName(e.text)
        val words = name.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val last = words.lastOrNull().orEmpty()
        val sentence = name.endsWith("!") || name.endsWith("?") || (name.endsWith(".") && last.length > 4)
        if (words.size > 5 || sentence) {
            out += Issue(Rule.CUE_LOOKS_LIKE_ACTION, e.id, e.text.trim(), fixes = listOf(Fix("Make it action") { l, _ -> edit(l, e.id) { toAction(it) } }))
        }
    }

    private fun checkParenthetical(els: List<Element>, i: Int, out: MutableList<Issue>) {
        val e = els[i]
        val t = e.text.trim()
        if (!t.startsWith("(") || !t.endsWith(")")) {
            out += Issue(Rule.PAREN_UNWRAPPED, e.id, t, fixes = listOf(Fix("Add ( )") { l, _ -> edit(l, e.id) { it.copy(text = Formatting.normalizeParenthetical(it.text)) } }))
        }
        val next = els.getOrNull(i + 1)?.type
        val prevOk = els.getOrNull(i - 1)?.type?.isDialogueBlockPart == true
        if (prevOk && (next == null || (next != ElementType.DIALOGUE && next != ElementType.LYRICS))) {
            out += Issue(Rule.PAREN_ENDS_SPEECH, e.id, t, fixes = listOf(Fix("Make it action") { l, _ -> edit(l, e.id) { toAction(it) } }))
        }
        if (t.removePrefix("(").removeSuffix(")").length > PAREN_MAX_CHARS) {
            out += Issue(Rule.LONG_PARENTHETICAL, e.id, "“${preview(t)}”")
        }
    }

    private fun checkTransition(els: List<Element>, i: Int, out: MutableList<Issue>) {
        val e = els[i]
        val t = e.text.trim().uppercase()
        if (!t.endsWith(":") && !t.endsWith(".")) {
            val add = if (t.endsWith("OUT") || t.endsWith("BLACK")) "." else ":"
            out += Issue(Rule.TRANSITION_PUNCTUATION, e.id, "$t  →  $t$add", fixes = listOf(Fix("Add “$add”") { l, _ ->
                edit(l, e.id) { el -> el.text.trimEnd().let { if (it.endsWith(":") || it.endsWith(".")) el else el.copy(text = it + add) } }
            }))
        }
        if (t.endsWith("TO:")) {
            val next = (i + 1 until els.size).map { els[it] }.firstOrNull { it.type != ElementType.NOTE && it.text.isNotBlank() }
            if (next != null && next.type != ElementType.SCENE_HEADING) out += Issue(Rule.TRANSITION_NOT_INTO_SCENE, e.id, t)
        }
    }

    // ------------------------------------------------------------------ page breaks

    private fun pageBreaks(els: List<Element>, p: Pagination, out: MutableList<Issue>) {
        for (pi in 0 until p.pages.size - 1) {
            val lines = p.pages[pi].lines.filter { !it.isBlank }
            val last = lines.lastOrNull()?.segs?.firstOrNull() ?: continue
            val label = p.label(pi)
            if (last.element >= 0 && (last.type == ElementType.SCENE_HEADING || last.type == ElementType.SHOT)) {
                els.getOrNull(last.element)?.let { out += Issue(Rule.PAGE_BREAK_ORPHAN, it.id, "Heading alone at the bottom of page $label", subject = "foot$label") }
            }
            if (last.element >= 0 && last.type == ElementType.CHARACTER) {
                els.getOrNull(last.element)?.let { out += Issue(Rule.PAGE_BREAK_ORPHAN, it.id, "${it.text.trim()} is cut off from their dialogue at the bottom of page $label", subject = "foot$label") }
            }
            // A single closing line of an action paragraph at the top of the next page.
            val next = p.pages[pi + 1].lines.filter { !it.isBlank }
            val first = next.firstOrNull()?.segs?.firstOrNull() ?: continue
            if (first.type == ElementType.ACTION && first.element >= 0) {
                val onNext = next.count { l -> l.segs.any { it.element == first.element } }
                val onThis = lines.count { l -> l.segs.any { it.element == first.element } }
                if (onThis > 0 && onNext == 1) els.getOrNull(first.element)?.let {
                    out += Issue(Rule.PAGE_BREAK_ORPHAN, it.id, "One widowed line of action at the top of page ${p.label(pi + 1)}", subject = "top${p.label(pi + 1)}")
                }
            }
        }
    }

    // ------------------------------------------------------------------ consistency

    private fun nameVariants(els: List<Element>, out: MutableList<Issue>) {
        val counts = LinkedHashMap<String, Int>()
        val firstId = HashMap<String, Long>()
        for (e in els) if (e.type == ElementType.CHARACTER) {
            val n = Formatting.characterName(e.text)
            if (n.isEmpty()) continue
            counts[n] = (counts[n] ?: 0) + 1
            firstId.getOrPut(n) { e.id }
        }
        variants(counts, minLength = 4).forEach { (rare, common) ->
            out += Issue(
                Rule.CHARACTER_VARIANT, firstId[rare], "“$rare” (${plural(counts[rare]!!, "cue")}) — did you mean “$common” (${plural(counts[common]!!, "cue")})?",
                subject = rare,
                fixes = listOf(Fix("Rename to $common") { l, _ -> renameCharacter(l, rare, common) }),
            )
        }
    }

    private fun locationVariants(els: List<Element>, out: MutableList<Issue>) {
        val counts = LinkedHashMap<String, Int>()
        val firstId = HashMap<String, Long>()
        for (e in els) if (e.type == ElementType.SCENE_HEADING) {
            val loc = Analysis.location(e.text)
            if (loc.isEmpty()) continue
            counts[loc] = (counts[loc] ?: 0) + 1
            firstId.getOrPut(loc) { e.id }
        }
        variants(counts, minLength = 7).forEach { (rare, common) ->
            out += Issue(
                Rule.LOCATION_VARIANT, firstId[rare], "“$rare” (${plural(counts[rare]!!, "scene")}) — same place as “$common” (${plural(counts[common]!!, "scene")})?",
                subject = rare,
                fixes = listOf(Fix("Rename to $common") { l, _ -> renameLocation(l, rare, common) }),
            )
        }
    }

    /**
     * Pairs (rare spelling → likely intended spelling). Two spellings are variants if they're
     * equal once punctuation and spaces are ignored (DR. SMITH / DR SMITH), or one letter
     * apart (MAYAH / MAYA) when one is clearly the usual spelling.
     */
    internal fun variants(counts: Map<String, Int>, minLength: Int): List<Pair<String, String>> {
        val names = counts.keys.toList()
        val key = names.associateWith { n -> n.filter { it.isLetterOrDigit() } }
        val out = ArrayList<Pair<String, String>>()
        val claimed = HashSet<String>()
        // Most used first (ties: the fuller spelling, "DR. SMITH" over "DR SMITH"), so each
        // rare spelling maps onto the most likely intended one.
        val byUse = names.sortedWith(compareByDescending<String> { counts[it] }.thenByDescending { it.length }.thenBy { it })
        for (ai in byUse.indices) {
            val a = byUse[ai]
            if (a in claimed) continue
            for (bi in ai + 1 until byUse.size) {
                val b = byUse[bi]
                if (b in claimed) continue
                val ca = counts[a]!!; val cb = counts[b]!!
                val ka = key[a]!!; val kb = key[b]!!
                val same = ka == kb && ka.isNotEmpty()
                val close = !same && ka.length >= minLength && kb.length >= minLength &&
                    ka.none { it.isDigit() } && kb.none { it.isDigit() } &&
                    cb <= 3 && ca >= cb * 3 && oneEditApart(ka, kb)
                if (same || close) { out += b to a; claimed += b }
            }
        }
        return out
    }

    internal fun oneEditApart(a: String, b: String): Boolean {
        if (a == b) return false
        if (kotlin.math.abs(a.length - b.length) > 1) return false
        if (a.length == b.length) {
            val diffs = a.indices.filter { a[it] != b[it] }
            if (diffs.size == 1) return true
            // A swap of two neighbouring letters (MYAA / MAYA) counts as one slip.
            return diffs.size == 2 && diffs[1] == diffs[0] + 1 && a[diffs[0]] == b[diffs[1]] && a[diffs[1]] == b[diffs[0]]
        }
        val (s, l) = if (a.length < b.length) a to b else b to a
        var i = 0
        while (i < s.length && s[i] == l[i]) i++
        return s.substring(i) == l.substring(i + 1)
    }

    // ------------------------------------------------------------------ fixes

    private inline fun edit(list: List<Element>, id: Long, f: (Element) -> Element?): List<Element> {
        val i = list.indexOfFirst { it.id == id }
        if (i < 0) return list
        val r = f(list[i])
        if (r == list[i]) return list
        return list.toMutableList().also { if (r == null) it.removeAt(i) else it[i] = r }
    }

    private fun toAction(e: Element): Element {
        var t = e.text.trim()
        if (e.type == ElementType.PARENTHETICAL) t = t.removePrefix("(").removeSuffix(")").trim()
        if (e.type == ElementType.PARENTHETICAL) t = t.replaceFirstChar { it.uppercaseChar() }.let { if (TextWrap.endsSentence(it)) it else "$it." }
        return e.copy(type = ElementType.ACTION, text = t, dual = false)
    }

    internal fun renameCharacter(list: List<Element>, from: String, to: String): List<Element> {
        var changed = false
        val out = list.map { e ->
            if (e.type == ElementType.CHARACTER && Formatting.characterName(e.text) == from) {
                changed = true
                val paren = e.text.indexOf('(')
                val suffix = if (paren >= 0) " " + e.text.substring(paren).trim() else ""
                e.copy(text = to + suffix)
            } else e
        }
        return if (changed) out else list
    }

    internal fun renameLocation(list: List<Element>, from: String, to: String): List<Element> {
        var changed = false
        val out = list.map { e ->
            if (e.type == ElementType.SCENE_HEADING && Analysis.location(e.text) == from) {
                val up = e.text.uppercase()
                val at = up.indexOf(from)
                if (at < 0) e else { changed = true; e.copy(text = e.text.substring(0, at) + to + e.text.substring(at + from.length)) }
            } else e
        }
        return if (changed) out else list
    }

    /** Break dense action into paragraphs of at most [ACTION_MAX_LINES] lines, at sentence ends. */
    internal fun splitAction(text: String): List<String> {
        val sentences = text.trim().split(sentenceEnd).filter { it.isNotBlank() }
        if (sentences.size < 2) return listOf(text)
        val parts = ArrayList<String>()
        var cur = ""
        for (s in sentences) {
            val joined = if (cur.isEmpty()) s else "$cur $s"
            if (cur.isNotEmpty() && TextWrap.wrap(joined, Specs.FULL_WIDTH).size > ACTION_MAX_LINES) { parts += cur; cur = s } else cur = joined
        }
        if (cur.isNotEmpty()) parts += cur
        return parts
    }

    private fun splitElement(list: List<Element>, id: Long, nextId: () -> Long): List<Element> {
        val i = list.indexOfFirst { it.id == id }
        if (i < 0) return list
        val e = list[i]
        val parts = splitAction(e.text)
        if (parts.size < 2) return list
        return list.toMutableList().also {
            it[i] = e.copy(text = parts.first())
            it.addAll(i + 1, parts.drop(1).map { p -> Element(nextId(), ElementType.ACTION, p, revision = e.revision) })
        }
    }

    // ------------------------------------------------------------------ helpers

    /** A lone blank action under a scene heading: the placeholder an index card creates. */
    private fun isPlaceholder(els: List<Element>, i: Int): Boolean {
        if (els[i].type != ElementType.ACTION) return false
        val prev = els.getOrNull(i - 1)?.type
        val next = els.getOrNull(i + 1)?.type
        return prev == ElementType.SCENE_HEADING && (next == null || next == ElementType.SCENE_HEADING)
    }

    /** "(VO)" → "(V.O.)", "(CONTD)" → "(CONT'D)". The curly CONT’D is left alone. */
    internal fun canonExtensions(cue: String): String = extension.replace(cue) { m ->
        val inner = m.groupValues[1].trim()
        if (inner.equals("CONT’D", ignoreCase = true)) return@replace m.value
        extensionForms.firstOrNull { it.first.matches(inner) }?.second?.let { "($it)" } ?: m.value
    }

    private fun hasStraySpaces(t: String) = t.lines().any { it != it.trim() || it.contains("  ") }

    internal fun tidySpaces(t: String) = t.lines().joinToString("\n") { it.trim().replace(Regex(" {2,}"), " ") }

    private fun preview(t: String): String {
        val s = t.trim().replace(Regex("\\s+"), " ")
        return if (s.length > 60) s.take(57).trimEnd() + "…" else s
    }

    private fun plural(n: Int, w: String) = "$n $w${if (n == 1) "" else "s"}"
}
