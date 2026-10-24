package com.amit.pageone.core

/** Script X-ray data, production reports, sides, and the production lock (A-pages / A-scenes / OMITTED). */
object Production {

    // ------------------------------------------------------------------ scene geometry

    data class SceneX(
        val index: Int,           // 0-based scene order
        val headingId: Long,
        val label: String,        // scene number as printed ("12", "12A")
        val heading: String,
        val intExt: String,       // "INT", "EXT", "INT/EXT", ""
        val location: String,
        val time: String,
        val lines: Int,
        val eighths: Int,
        val page: Int,
        val speaking: List<String>,
        val mentioned: List<String>,
        val synopsis: String,
        val words: Int,
    )

    fun intExt(heading: String): String {
        val u = heading.trim().uppercase()
        return when {
            u.startsWith("INT./EXT") || u.startsWith("INT/EXT") || u.startsWith("I/E") || u.startsWith("EXT./INT") -> "INT/EXT"
            u.startsWith("INT") -> "INT"
            u.startsWith("EXT") -> "EXT"
            else -> ""
        }
    }

    fun timeOfDay(heading: String): String {
        val u = heading.trim().uppercase()
        val dash = Regex("\\s[-–—]\\s*").findAll(u).lastOrNull() ?: return ""
        return u.substring(dash.range.last + 1).trim()
    }

    /** Lines each scene occupies on the printed page (blank spacing lines count towards the scene they sit in). */
    fun sceneLines(elements: List<Element>, pagination: Pagination): IntArray {
        val sceneOf = IntArray(elements.size) { -1 }
        var s = -1
        for (i in elements.indices) { if (elements[i].type == ElementType.SCENE_HEADING) s++; sceneOf[i] = s }
        val n = s + 1
        val out = FloatArray(n.coerceAtLeast(0))
        var cur = -1
        for (p in pagination.pages) for (ln in p.lines) {
            val el = ln.segs.firstOrNull { it.element >= 0 }?.element
            if (el != null) cur = sceneOf[el]
            if (cur >= 0 && cur < n) out[cur] += ln.h
        }
        return IntArray(out.size) { Math.round(out[it]) }
    }

    /** Page length in eighths, the way production measures it (a scene is never less than 1/8). */
    fun eighths(lines: Int, linesPerPage: Int): Int = if (lines <= 0) 0 else maxOf(1, Math.round(lines * 8f / linesPerPage))

    fun formatEighths(e: Int): String {
        val whole = e / 8; val rest = e % 8
        return when {
            whole == 0 -> "$rest/8"
            rest == 0 -> "$whole"
            else -> "$whole $rest/8"
        }
    }

    fun xray(script: Script, pagination: Pagination, linesPerPage: Int): List<SceneX> {
        val els = script.elements
        val scenes = Analysis.scenes(els)
        val lines = sceneLines(els, pagination)
        val labels = Locking.sceneLabels(els, script.extras.lock)
        val allNames = Analysis.characterNames(els)
        return scenes.mapIndexed { k, sc ->
            val speaking = LinkedHashSet<String>()
            val action = StringBuilder()
            for (i in sc.elementIndex + 1 until sc.endIndex) {
                val e = els[i]
                if (e.type == ElementType.CHARACTER) Formatting.characterName(e.text).takeIf { it.isNotEmpty() }?.let(speaking::add)
                if (e.type == ElementType.ACTION) action.append(' ').append(e.text.uppercase())
            }
            val act = action.toString()
            val mentioned = allNames.filter { it !in speaking && Regex("\\b${Regex.escape(it)}\\b").containsMatchIn(act) }
            val ln = lines.getOrElse(k) { 0 }
            SceneX(
                k, sc.elementId, labels[sc.elementId] ?: "${sc.number}", sc.heading, intExt(sc.heading), Analysis.location(sc.heading),
                timeOfDay(sc.heading), ln, eighths(ln, linesPerPage), (pagination.elementPage.getOrNull(sc.elementIndex) ?: 0) + 1,
                speaking.toList(), mentioned, sc.synopsis, sc.words,
            )
        }
    }

    // ------------------------------------------------------------------ reports (CSV)

    private fun csv(rows: List<List<String>>): String = rows.joinToString("\r\n") { r ->
        r.joinToString(",") { v -> if (v.any { it == ',' || it == '"' || it == '\n' }) "\"" + v.replace("\"", "\"\"") + "\"" else v }
    }

    fun scenesCsv(script: Script, xs: List<SceneX>): String {
        val threads = script.extras.threads.associateBy { it.id }
        return csv(listOf(listOf("Scene", "Page", "INT/EXT", "Location", "Time", "Length", "Characters", "Threads", "Synopsis")) +
            xs.map { x ->
                listOf(
                    x.label, "${x.page}", x.intExt, x.location, x.time, formatEighths(x.eighths),
                    (x.speaking + x.mentioned).joinToString("; "),
                    script.extras.scene(x.headingId).threads.mapNotNull { threads[it]?.name }.joinToString("; "),
                    x.synopsis,
                )
            })
    }

    data class CharacterRow(val name: String, val scenes: List<String>, val lines: Int, val words: Int, val first: Int, val last: Int, val eighths: Int)

    fun characters(script: Script, xs: List<SceneX>): List<CharacterRow> {
        val stats = Analysis.stats(script.elements).characters.associateBy { it.name }
        val names = Analysis.characterNames(script.elements)
        return names.map { n ->
            val sc = xs.filter { n in it.speaking || n in it.mentioned }
            val st = stats[n]
            CharacterRow(n, sc.map { it.label }, st?.cues ?: 0, st?.words ?: 0, sc.minOfOrNull { it.page } ?: 0, sc.maxOfOrNull { it.page } ?: 0, sc.sumOf { it.eighths })
        }.sortedByDescending { it.lines }
    }

    fun charactersCsv(rows: List<CharacterRow>): String = csv(
        listOf(listOf("Character", "Speeches", "Words", "Scenes", "Pages", "First page", "Last page", "Scene list")) +
            rows.map { listOf(it.name, "${it.lines}", "${it.words}", "${it.scenes.size}", formatEighths(it.eighths), "${it.first}", "${it.last}", it.scenes.joinToString(" ")) }
    )

    data class LocationRow(val name: String, val scenes: List<String>, val eighths: Int, val intExt: Set<String>, val times: Set<String>)

    fun locations(xs: List<SceneX>): List<LocationRow> =
        xs.filter { it.location.isNotEmpty() }.groupBy { it.location }.map { (loc, l) ->
            LocationRow(loc, l.map { it.label }, l.sumOf { it.eighths }, l.map { it.intExt }.filter { it.isNotEmpty() }.toSet(), l.map { it.time }.filter { it.isNotEmpty() }.toSet())
        }.sortedByDescending { it.eighths }

    fun locationsCsv(rows: List<LocationRow>): String = csv(
        listOf(listOf("Location", "Scenes", "Pages", "INT/EXT", "Times", "Scene list")) +
            rows.map { listOf(it.name, "${it.scenes.size}", formatEighths(it.eighths), it.intExt.joinToString("/"), it.times.joinToString("; "), it.scenes.joinToString(" ")) }
    )

    fun breakdownCsv(script: Script, xs: List<SceneX>): String {
        val cats = BreakdownCategory.entries
        return csv(listOf(listOf("Scene", "Page", "Length", "Heading") + cats.map { it.label }) + xs.map { x ->
            val m = script.extras.scene(x.headingId)
            val cast = (m.breakdown[BreakdownCategory.CAST].orEmpty() + x.speaking).distinct()
            listOf(x.label, "${x.page}", formatEighths(x.eighths), x.heading) + cats.map { c ->
                (if (c == BreakdownCategory.CAST) cast else m.breakdown[c].orEmpty()).joinToString("; ")
            }
        })
    }

    // ------------------------------------------------------------------ sides

    /**
     * Sides for an actor: only the scenes where any of [names] speaks, is mentioned or is
     * cast in the breakdown. Heading ids are kept so locked scene numbers still apply.
     */
    fun sides(script: Script, names: Set<String>): Script {
        val els = script.elements
        val scenes = Analysis.scenes(els)
        val keep = ArrayList<Element>()
        for (sc in scenes) {
            val slice = els.subList(sc.elementIndex, sc.endIndex)
            val speaks = slice.any { it.type == ElementType.CHARACTER && Formatting.characterName(it.text) in names }
            val mentioned = slice.any { e -> e.type == ElementType.ACTION && names.any { n -> Regex("\\b${Regex.escape(n)}\\b").containsMatchIn(e.text.uppercase()) } }
            val cast = script.extras.scene(sc.elementId).breakdown[BreakdownCategory.CAST].orEmpty().any { it.uppercase() in names }
            if (speaks || mentioned || cast) keep.addAll(slice.filter { it.type != ElementType.NOTE })
        }
        return script.copy(elements = keep.ifEmpty { listOf(Element(1, ElementType.ACTION, "No scenes for ${names.joinToString(", ")}.")) })
    }
}

/** Production lock: freeze scene numbers and page breaks; new material gets A-numbers. */
object Locking {

    fun lock(elements: List<Element>, pagination: Pagination): LockState {
        val numbers = LinkedHashMap<Long, String>()
        var n = 0
        for (e in elements) if (e.type == ElementType.SCENE_HEADING) numbers[e.id] = "${++n}"
        val starts = pagination.pages.drop(1).mapNotNull { p ->
            p.lines.firstNotNullOfOrNull { ln -> ln.segs.firstOrNull { it.element >= 0 }?.element }?.let { elements.getOrNull(it)?.id }
        }
        return LockState(true, System.currentTimeMillis(), numbers, starts)
    }

    /** Scene labels: plain numbers when unlocked; locked numbers plus A/B/C for scenes added since. */
    fun sceneLabels(elements: List<Element>, lock: LockState): Map<Long, String> {
        val out = LinkedHashMap<Long, String>()
        if (!lock.locked) {
            var n = 0
            for (e in elements) if (e.type == ElementType.SCENE_HEADING) out[e.id] = "${++n}"
            return out
        }
        val used = HashSet(lock.sceneNumbers.values)
        var last: String? = null
        val heads = elements.filter { it.type == ElementType.SCENE_HEADING }
        val firstLocked = heads.firstNotNullOfOrNull { lock.sceneNumbers[it.id] } ?: "1"
        var prefix = 0
        for (h in heads) {
            val locked = lock.sceneNumbers[h.id]
            if (locked != null) { out[h.id] = locked; last = locked; continue }
            var label: String
            if (last == null) {
                // Before the first locked scene: A1, B1…
                do { label = letters(prefix++) + firstLocked } while (label in used)
            } else {
                var cand = next(last)
                while (cand in used) cand = next(cand)
                label = cand
            }
            used += label; out[h.id] = label; last = label
        }
        return out
    }

    /** "12" → "12A", "12A" → "12B", "12Z" → "12AA". */
    internal fun next(label: String): String {
        val digits = label.takeWhile { it.isDigit() }
        val tail = label.drop(digits.length)
        if (tail.isEmpty() || tail.any { !it.isLetter() }) return label + "A"
        return digits + increment(tail)
    }

    private fun increment(s: String): String {
        val c = s.toCharArray()
        var i = c.size - 1
        while (i >= 0) {
            if (c[i] < 'Z') { c[i] = c[i] + 1; return String(c) }
            c[i] = 'A'; i--
        }
        return "A" + String(c)
    }

    private fun letters(i: Int): String = if (i < 26) "${'A' + i}" else letters(i / 26 - 1) + ('A' + i % 26)

    /** Element index → locked page number, for the paginator to start those pages on. */
    fun forcedBreaks(elements: List<Element>, lock: LockState): Map<Int, Int> {
        if (!lock.locked) return emptyMap()
        val idx = HashMap<Long, Int>(elements.size * 2)
        elements.forEachIndexed { i, e -> idx[e.id] = i }
        val out = HashMap<Int, Int>()
        lock.pageStarts.forEachIndexed { k, id -> idx[id]?.let { out[it] = k + 2 } }
        return out
    }

    /** Printed page labels: 1, 2, 2A, 2B, 3, 4-5… */
    fun pageLabels(p: Pagination): List<String> {
        val n = p.pages.size.coerceAtLeast(1)
        val ln = p.lockNumbers
        if (ln.isEmpty()) return (1..n).map { "$it" }
        val out = ArrayList<String>(n)
        var base = 1; var letter = -1
        for (i in 0 until n) {
            val forced = ln.getOrElse(i) { 0 }
            if (i == 0 || forced > 0) { base = if (i == 0 && forced == 0) 1 else forced; letter = -1; out += "$base" }
            else { letter++; out += "$base${letters(letter)}" }
        }
        // A locked page whose content vanished is absorbed by the page before it: "4-5".
        for (i in 0 until n) {
            val nextForced = (i + 1 until n).firstOrNull { ln.getOrElse(it) { 0 } > 0 }?.let { ln[it] }
            val isSegmentEnd = i + 1 >= n || ln.getOrElse(i + 1) { 0 } > 0
            val mine = out[i].takeWhile { it.isDigit() }.toIntOrNull() ?: continue
            if (isSegmentEnd && nextForced != null && nextForced > mine + 1 && out[i].all { it.isDigit() }) out[i] = "$mine-${nextForced - 1}"
        }
        return out
    }

    /** Omit a scene in a locked script: keep its number, replace it with OMITTED. */
    fun omitScene(elements: List<Element>, headingId: Long): List<Element> {
        val start = elements.indexOfFirst { it.id == headingId }
        if (start < 0) return elements
        var end = start + 1
        while (end < elements.size && elements[end].type != ElementType.SCENE_HEADING) end++
        val head = elements[start].copy(text = "OMITTED", synopsis = "", color = 0)
        return elements.subList(0, start) + head + elements.subList(end, elements.size)
    }
}

/** Deadline maths: how many pages a day to finish on time, and how far ahead or behind you are. */
object Planner {
    data class Plan(
        val daysLeft: Long,
        val pagesLeft: Int,
        val pagesPerDay: Float,
        val expectedNow: Float,
        /** Positive = pages behind the straight-line plan; negative = ahead. */
        val behind: Float,
        val status: String,
        val overdue: Boolean,
        val done: Boolean,
    )

    fun plan(d: Deadline, currentPages: Int, today: java.time.LocalDate = java.time.LocalDate.now()): Plan {
        val due = runCatching { java.time.LocalDate.parse(d.date) }.getOrDefault(today)
        val start = runCatching { java.time.LocalDate.parse(d.startDate) }.getOrDefault(today)
        val daysLeft = java.time.temporal.ChronoUnit.DAYS.between(today, due)
        val total = java.time.temporal.ChronoUnit.DAYS.between(start, due).coerceAtLeast(1)
        val elapsed = java.time.temporal.ChronoUnit.DAYS.between(start, today).coerceIn(0, total)
        val pagesLeft = (d.targetPages - currentPages).coerceAtLeast(0)
        val expected = d.startPages + (d.targetPages - d.startPages) * (elapsed.toFloat() / total)
        val behind = expected - currentPages
        val perDay = if (daysLeft > 0) pagesLeft / daysLeft.toFloat() else pagesLeft.toFloat()
        val done = pagesLeft == 0
        val overdue = !done && daysLeft < 0
        val status = when {
            done -> "Target reached — ${d.label.lowercase()} is the length you planned"
            overdue -> "${-daysLeft} day${if (daysLeft == -1L) "" else "s"} past the deadline · $pagesLeft pages to go"
            daysLeft == 0L -> "Due today · $pagesLeft page${if (pagesLeft == 1) "" else "s"} to go"
            behind > 0.5f -> "%.1f pages/day to finish · %.1f pages behind plan".format(perDay, behind)
            behind < -0.5f -> "%.1f pages/day to finish · %.1f pages ahead".format(perDay, -behind)
            else -> "%.1f pages/day to finish · right on plan".format(perDay)
        }
        return Plan(daysLeft, pagesLeft, perDay, expected, behind, status, overdue, done)
    }
}
