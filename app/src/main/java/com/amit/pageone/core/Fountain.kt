package com.amit.pageone.core

/** Fountain (https://fountain.io) import / export. */
object Fountain {

    data class Parsed(val titlePage: TitlePage, val elements: List<Element>)

    private val headingRe = Regex("^(INT|EXT|EST|INT\\./EXT|INT/EXT|I/E)[. ]", RegexOption.IGNORE_CASE)
    private val sceneNumRe = Regex("\\s*#[^#]+#\\s*$")
    private val titleKeyRe = Regex("^([A-Za-z][A-Za-z ]*):(.*)$")
    private val titleKeys = setOf("title", "credit", "author", "authors", "source", "draft date", "date", "contact", "notes", "copyright", "revision", "draft")

    fun parse(source: String, ids: IdGen): Parsed {
        var text = source.replace("\r\n", "\n").replace('\r', '\n').replace("\t", "    ")
        text = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL).replace(text, "")
        val lines = text.split('\n')

        // ---- Title page ----
        var i = 0
        while (i < lines.size && lines[i].isBlank()) i++
        var tp = TitlePage()
        // Only Fountain's own keys start a title page — otherwise an opening "FADE IN:" would be swallowed.
        val firstKey = lines.getOrNull(i)?.trim()?.let { titleKeyRe.matchEntire(it) }?.groupValues?.get(1)?.trim()?.lowercase()
        if (i < lines.size && firstKey in titleKeys && !headingRe.containsMatchIn(lines[i].trim())) {
            val map = LinkedHashMap<String, StringBuilder>()
            val order = ArrayList<Pair<String, StringBuilder>>()
            var key: String? = null
            while (i < lines.size && lines[i].isNotBlank()) {
                val raw = lines[i]
                val m = titleKeyRe.matchEntire(raw.trim())
                if (m != null && !raw.startsWith(" ") && !raw.startsWith("\t")) {
                    key = m.groupValues[1].trim().lowercase()
                    val sb = StringBuilder(m.groupValues[2].trim())
                    map[key] = sb
                    order.add(key to sb)
                } else if (key != null) {
                    val sb = map.getValue(key)
                    if (sb.isNotEmpty()) sb.append('\n')
                    sb.append(raw.trim())
                }
                i++
            }
            fun g(vararg k: String) = k.firstNotNullOfOrNull { map[it]?.toString() }?.let(::stripEmphasis) ?: ""
            // Credits can repeat (Credit/Author pairs) — pair them up in order.
            val credits = ArrayList<Credit>()
            var pendingRole: String? = null
            for ((k, v) in order) {
                when (k) {
                    "credit" -> pendingRole = stripEmphasis(v.toString())
                    "author", "authors" -> { credits.add(Credit(pendingRole?.ifBlank { null } ?: "Written by", stripEmphasis(v.toString()))); pendingRole = null }
                }
            }
            val base = TitlePage.simple(
                title = g("title").replace("\n", " "), credit = g("credit"), author = g("author", "authors"),
                source = g("source"), draftDate = g("draft date", "date"), contact = g("contact"),
                notes = g("notes"), copyright = g("copyright"),
            )
            tp = base.copy(credits = credits.ifEmpty { base.credits }, draft = g("revision", "draft"))
        }

        // ---- Body ----
        val out = ArrayList<Element>()
        var inDialogue = false
        var prevBlank = true

        fun add(type: ElementType, t: String, dual: Boolean = false) {
            out.add(Element(ids.next(), type, t, dual = dual))
        }

        while (i < lines.size) {
            val raw = lines[i]
            val t = raw.trim()
            val next = lines.getOrNull(i + 1)?.trim() ?: ""
            val nextBlank = next.isEmpty()

            if (t.isEmpty()) {
                // "  " (two spaces) inside dialogue keeps the speech going
                if (inDialogue && raw.length >= 2) { i++; continue }
                inDialogue = false; prevBlank = true; i++; continue
            }

            if (inDialogue) {
                val last = out.last()
                if (t.startsWith("(") && t.endsWith(")")) add(ElementType.PARENTHETICAL, t)
                else if (last.type == ElementType.DIALOGUE) out[out.size - 1] = last.copy(text = last.text + "\n" + stripEmphasis(t))
                else add(ElementType.DIALOGUE, stripEmphasis(t))
                prevBlank = false; i++; continue
            }

            when {
                t.startsWith("[[") && t.endsWith("]]") -> add(ElementType.NOTE, t.removePrefix("[[").removeSuffix("]]").trim())
                t.startsWith("===") -> {}
                t.startsWith("#") -> {}
                t.startsWith("=") -> {
                    val idx = out.indexOfLast { it.type == ElementType.SCENE_HEADING }
                    if (idx >= 0) out[idx] = out[idx].copy(synopsis = (out[idx].synopsis + " " + t.removePrefix("=").trim()).trim())
                }
                t.startsWith(".") && !t.startsWith("..") -> add(ElementType.SCENE_HEADING, cleanHeading(t.substring(1)))
                prevBlank && headingRe.containsMatchIn(t) -> add(ElementType.SCENE_HEADING, cleanHeading(t))
                t.startsWith("!") -> appendAction(out, ids, stripEmphasis(t.substring(1)), prevBlank)
                t.startsWith("~") -> add(ElementType.LYRICS, t.substring(1).trim())
                t.startsWith(">") && t.endsWith("<") -> add(ElementType.CENTERED, stripEmphasis(t.removePrefix(">").removeSuffix("<").trim()))
                t.startsWith(">") -> add(ElementType.TRANSITION, t.substring(1).trim().uppercase())
                prevBlank && nextBlank && isUpper(t) && t.endsWith("TO:") -> add(ElementType.TRANSITION, t)
                t.startsWith("@") && !nextBlank -> {
                    val dual = t.endsWith("^")
                    add(ElementType.CHARACTER, t.substring(1).removeSuffix("^").trim().uppercase(), dual)
                    inDialogue = true
                }
                prevBlank && !nextBlank && isCharacterCue(t) -> {
                    val dual = t.endsWith("^")
                    add(ElementType.CHARACTER, t.removeSuffix("^").trim(), dual)
                    inDialogue = true
                }
                else -> appendAction(out, ids, stripEmphasis(raw.trimEnd()), prevBlank)
            }
            prevBlank = false
            i++
        }
        return Parsed(tp, out)
    }

    private fun appendAction(out: MutableList<Element>, ids: IdGen, t: String, prevBlank: Boolean) {
        val last = out.lastOrNull()
        if (!prevBlank && last != null && last.type == ElementType.ACTION) {
            out[out.size - 1] = last.copy(text = last.text + "\n" + t)
        } else out.add(Element(ids.next(), ElementType.ACTION, t.trimStart()))
    }

    private fun cleanHeading(s: String) = sceneNumRe.replace(s.trim(), "").uppercase()

    private fun isUpper(s: String) = s.any { it.isLetter() } && s == s.uppercase()

    private fun isCharacterCue(t: String): Boolean {
        val base = t.substringBefore("(").removeSuffix("^").trim()
        return base.isNotEmpty() && base.any { it.isLetter() } && base == base.uppercase() && !base.endsWith(":") && base.length <= 40
    }

    private fun stripEmphasis(s: String): String =
        s.replace(Regex("\\[\\[.*?]]"), "").replace(Regex("(?<!\\\\)\\*{1,3}"), "").replace("\\*", "*")

    // ------------------------------------------------------------------ writer

    fun write(script: Script): String {
        val sb = StringBuilder()
        val tp = script.titlePage
        fun kv(k: String, v: String) {
            if (v.isBlank()) return
            val ls = v.split('\n')
            if (ls.size == 1) sb.append(k).append(": ").append(v.trim()).append('\n')
            else { sb.append(k).append(":\n"); ls.forEach { sb.append("    ").append(it.trim()).append('\n') } }
        }
        kv("Title", tp.title.ifBlank { script.title })
        tp.credits.filter { it.names.isNotBlank() }.forEach { kv("Credit", it.role); kv("Author", it.names) }
        kv("Source", tp.basedOnLine)
        kv("Draft date", listOf(tp.draft, tp.draftDate).filter { it.isNotBlank() }.joinToString("\n"))
        kv("Contact", tp.contactLines.joinToString("\n"))
        kv("Copyright", listOf(tp.copyright, tp.registration).filter { it.isNotBlank() }.joinToString("\n"))
        kv("Notes", tp.notes)
        sb.append('\n')

        val els = script.elements
        var i = 0
        while (i < els.size) {
            val e = els[i]
            val t = e.text
            when (e.type) {
                ElementType.SCENE_HEADING -> {
                    sb.append('\n')
                    sb.append(if (headingRe.containsMatchIn(t)) t.uppercase() else ".$t").append("\n\n")
                    if (e.synopsis.isNotBlank()) sb.append("= ").append(e.synopsis).append("\n\n")
                }
                ElementType.ACTION, ElementType.SHOT -> {
                    val needsForce = headingRe.containsMatchIn(t) || (isUpper(t) && !t.contains('\n')) || t.startsWith("@") || t.startsWith(">")
                    sb.append(if (needsForce) "!$t" else t).append("\n\n")
                }
                ElementType.CHARACTER -> {
                    val cue = t.uppercase()
                    sb.append(if (isCharacterCue(cue)) cue else "@$cue")
                    if (e.dual) sb.append(" ^")
                    sb.append('\n')
                    var j = i + 1
                    while (j < els.size && els[j].type in setOf(ElementType.DIALOGUE, ElementType.PARENTHETICAL, ElementType.LYRICS, ElementType.NOTE)) {
                        val d = els[j]
                        when (d.type) {
                            ElementType.PARENTHETICAL -> sb.append(Formatting.normalizeParenthetical(d.text))
                            ElementType.LYRICS -> sb.append("~").append(d.text)
                            ElementType.NOTE -> sb.append("[[").append(d.text).append("]]")
                            else -> sb.append(d.text.ifEmpty { " " }.split('\n').joinToString("\n") { it.ifEmpty { "  " } })
                        }
                        sb.append('\n')
                        j++
                    }
                    sb.append('\n')
                    i = j
                    continue
                }
                ElementType.DIALOGUE -> sb.append(t).append("\n\n") // orphaned dialogue
                ElementType.PARENTHETICAL -> sb.append(t).append("\n\n")
                ElementType.TRANSITION -> sb.append(if (t.uppercase().endsWith("TO:")) t.uppercase() else "> ${t.uppercase()}").append("\n\n")
                ElementType.CENTERED -> sb.append("> ").append(t).append(" <\n\n")
                ElementType.LYRICS -> sb.append("~").append(t).append("\n\n")
                ElementType.NOTE -> sb.append("[[").append(t).append("]]\n\n")
            }
            i++
        }
        return sb.toString().replace(Regex("\n{3,}"), "\n\n").trimEnd() + "\n"
    }
}
