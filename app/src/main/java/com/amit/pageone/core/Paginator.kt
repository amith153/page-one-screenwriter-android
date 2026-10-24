package com.amit.pageone.core

/**
 * Industry-standard screenplay pagination.
 *
 * All horizontal positions are in "columns" (characters of 12pt Courier = 1/10 inch),
 * measured from the 1.5" left margin. A full-width line is 60 columns (6.0").
 * Vertically, a page holds a fixed number of 12pt lines (6 per inch).
 */

enum class PaperSize(val label: String, val widthPt: Int, val heightPt: Int) {
    LETTER("US Letter", 612, 792),
    A4("A4", 595, 842);

    /** Body lines per page with a 1" top margin and 0.75" bottom margin. */
    val linesPerPage: Int get() = (heightPt - 72 - 54) / 12
}

data class TypeSpec(val indent: Int, val width: Int, val spaceBefore: Int, val align: Align = Align.LEFT)

enum class Align { LEFT, RIGHT, CENTER }

data class LayoutOptions(
    val paper: PaperSize = PaperSize.LETTER,
    val sceneSpacing: Int = 1,        // blank lines before scene headings
    val autoContd: Boolean = true,
    val moreContd: Boolean = true,     // split dialogue across pages with (MORE)/(CONT'D)
)

object Specs {
    const val FULL_WIDTH = 60

    fun of(type: ElementType, opts: LayoutOptions): TypeSpec = when (type) {
        ElementType.SCENE_HEADING -> TypeSpec(0, 60, opts.sceneSpacing)
        ElementType.ACTION -> TypeSpec(0, 60, 1)
        ElementType.CHARACTER -> TypeSpec(22, 38, 1)
        ElementType.PARENTHETICAL -> TypeSpec(16, 25, 0)
        ElementType.DIALOGUE -> TypeSpec(10, 35, 0)
        ElementType.LYRICS -> TypeSpec(10, 35, 0)
        ElementType.TRANSITION -> TypeSpec(0, 60, 1, Align.RIGHT)
        ElementType.SHOT -> TypeSpec(0, 60, 1)
        ElementType.CENTERED -> TypeSpec(0, 60, 1, Align.CENTER)
        ElementType.NOTE -> TypeSpec(0, 60, 1)
    }

    /** Dual dialogue column specs. [right] = second speaker. */
    fun dual(type: ElementType, right: Boolean): TypeSpec {
        val base = if (right) 31 else 0
        return when (type) {
            ElementType.CHARACTER -> TypeSpec(base + 8, 20, 0)
            ElementType.PARENTHETICAL -> TypeSpec(base + 4, 22, 0)
            else -> TypeSpec(base, 28, 0)
        }
    }
}

data class Seg(val text: String, val col: Int, val type: ElementType, val element: Int, val revision: Int)
/** [h]: the line's height in printed lines (1 = 1/6"). Scripts that stack marks above and below the letters need more. */
data class PLine(val segs: List<Seg>, val h: Float = 1f) {
    val isBlank: Boolean get() = segs.isEmpty()
}

/** Printed lines a run of page lines takes. */
fun List<PLine>.height(): Float = sumOf { it.h.toDouble() }.toFloat()

/** Where each line of a page starts, in printed lines from the top margin; a tall line sits in the middle of its taller slot. */
fun PageLayout.tops(): FloatArray {
    val out = FloatArray(lines.size)
    var t = 0f
    lines.forEachIndexed { i, l -> out[i] = t + (l.h - 1f) / 2f; t += l.h }
    return out
}
data class PageLayout(val lines: List<PLine>)
data class Pagination(
    val pages: List<PageLayout>,
    val elementPage: IntArray,
    /** Locked scripts: per page, the locked page number it begins (0 = an A-page continuation). Empty when unlocked. */
    val lockNumbers: IntArray = IntArray(0),
) {
    val pageCount: Int get() = pages.size.coerceAtLeast(1)
    /** Printed page labels ("12", "12A", "4-5"). */
    val labels: List<String> by lazy { Locking.pageLabels(this) }
    fun label(page: Int): String = labels.getOrElse(page) { "${page + 1}" }
}

object TextWrap {
    /**
     * Real measurements of printed text, installed by the app once it can measure with the fonts it
     * prints in (see io/PrintMeasure). Without one — in unit tests — the estimates below stand in.
     * The desktop app does the same (src/core/paginator.ts), so both break lines in the same places.
     */
    interface Measurer {
        /** Columns [s] fills when printed: Courier runs one a character, other scripts their drawn width. */
        fun cells(s: String): Float?
        /** Printed lines [s] needs so its marks don't reach into the lines above and below. */
        fun height(s: String): Float?
    }

    @Volatile var measurer: Measurer? = null

    /** Printed lines a line of text needs: 1 for Latin, more where marks stack above and below. */
    fun lineHeight(s: String): Float = measurer?.height(s) ?: if (tall(s)) 1.5f else 1f

    /** The longest start of a word that fits in [width] columns, never splitting a letter from its marks. */
    private fun fitPrefix(w: String, width: Int): String {
        val it = java.text.BreakIterator.getCharacterInstance().apply { setText(w) }
        var end = it.next().let { if (it == java.text.BreakIterator.DONE) w.length else it }
        while (true) {
            val next = it.next()
            if (next == java.text.BreakIterator.DONE || cells(w.substring(0, next)) > width + 0.001f) break
            end = next
        }
        return w.substring(0, end)
    }

    fun wrap(text: String, width: Int): List<String> {
        val out = ArrayList<String>()
        for (para in text.split('\n')) {
            if (para.isEmpty()) { out.add(""); continue }
            var line = StringBuilder()
            var used = 0f
            for (word in para.split(' ')) {
                var w = word
                // hard-break words longer than the line
                while (cells(w) > width + 0.001f) {
                    if (line.isNotEmpty()) { out.add(line.toString()); line = StringBuilder(); used = 0f }
                    val head = fitPrefix(w, width)
                    out.add(head); w = w.substring(head.length)
                }
                val ww = cells(w)
                if (line.isEmpty()) { line.append(w); used = ww }
                else if (used + 1 + ww <= width + 0.001f) { line.append(' ').append(w); used += 1 + ww }
                else { out.add(line.toString()); line = StringBuilder(w); used = ww }
            }
            out.add(line.toString())
        }
        return out
    }

    /** How many Courier columns [s] fills when printed. */
    fun cells(s: String): Float = measurer?.cells(s) ?: estimateCells(s)

    /**
     * The estimate, where nothing can measure: Latin text is one column per character. Indic text
     * is narrower than its character count: vowel signs and other marks sit on the letter (no
     * width), and a consonant after a virama mostly tucks under the one before it.
     */
    fun estimateCells(s: String): Float {
        var n = 0f
        var i = 0
        var afterVirama = false
        while (i < s.length) {
            val cp = s.codePointAt(i)
            i += Character.charCount(cp)
            if (!isIndic(cp)) { n += 1f; afterVirama = false; continue }
            when (Character.getType(cp).toByte()) {
                Character.NON_SPACING_MARK, Character.ENCLOSING_MARK, Character.FORMAT -> {}
                Character.COMBINING_SPACING_MARK -> n += 0.5f
                else -> n += if (afterVirama) 0.4f else 1f
            }
            afterVirama = isVirama(cp)
        }
        return n
    }

    private fun isIndic(cp: Int) = cp in 0x0900..0x0DFF || cp in 0x0E00..0x0EFF || cp in 0x0F00..0x109F || cp == 0x200C || cp == 0x200D

    /** The virama (halant) of each Indic script sits at offset 0x4D in its block. */
    private fun isVirama(cp: Int) = cp in 0x0900..0x0DFF && (cp and 0x7F) == 0x4D

    /**
     * Scripts whose letters stack marks above and below the line (Kannada, Hindi, Tamil, Telugu,
     * Malayalam, Bengali, Thai…). They need a taller line than Courier's 12 pt to print cleanly.
     */
    fun tall(s: String): Boolean {
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            if (cp in 0x0900..0x0DFF || cp in 0x0E00..0x0EFF || cp in 0x0F00..0x109F) return true
            i += Character.charCount(cp)
        }
        return false
    }

    /** Splits a line into runs: (text, isIndic). Spaces stay with the run before them. */
    fun runs(s: String): List<Pair<String, Boolean>> {
        val out = ArrayList<Pair<String, Boolean>>()
        var cur = StringBuilder(); var curIndic = false
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val n = Character.charCount(cp)
            val indic = cp in 0x0900..0x0DFF || cp in 0x0E00..0x0EFF || cp in 0x0F00..0x109F || (cp == 0x200C || cp == 0x200D)
            val joins = cp == ' '.code || indic == curIndic
            if (cur.isNotEmpty() && !joins) { out += cur.toString() to curIndic; cur = StringBuilder() }
            if (cur.isEmpty()) curIndic = indic
            cur.append(s, i, i + n)
            i += n
        }
        if (cur.isNotEmpty()) out += cur.toString() to curIndic
        return out
    }

    fun endsSentence(s: String): Boolean {
        val t = s.trimEnd().trimEnd('"', '”', '\'', '’', ')')
        return t.endsWith(".") || t.endsWith("?") || t.endsWith("!") || t.endsWith("--") || t.endsWith("…")
    }
}

class Paginator(private val opts: LayoutOptions) {

    private val L = opts.paper.linesPerPage.toFloat()

    /** How many of [lines] fit, from the top, in [room] printed lines. */
    private fun fitting(lines: List<PLine>, room: Float): Int {
        var used = 0f; var k = 0
        while (k < lines.size && used + lines[k].h <= room + 1e-4f) used += lines[k++].h
        return k
    }

    private fun withHeight(ln: PLine): PLine {
        val h = maxOf(1f, ln.segs.maxOfOrNull { TextWrap.lineHeight(it.text) } ?: 1f)
        return if (h > 1f) ln.copy(h = h) else ln
    }

    /** A unit that is placed on the page as a whole or split by rules. */
    private class Block(
        val kind: Kind,
        val lines: List<PLine>,
        val spaceBefore: Int,
        val firstElement: Int,
        val elements: IntRange,
        /** For dialogue: index (into lines) where character cue lines end. */
        val cueLines: Int = 0,
        /** For dialogue: per line, can we split *after* this line? */
        val splitAfter: BooleanArray = BooleanArray(lines.size),
        /** For dialogue: per line, does a sentence end here (the preferred place to break a speech)? */
        val sentenceAfter: BooleanArray = BooleanArray(lines.size),
        val cueText: String = "",
        val revision: Int = 0,
    )

    private enum class Kind { HEADING, ACTION, DIALOGUE, DUAL, OTHER }

    /** [forced]: element index → locked page number that must begin at that element (see [Locking]). */
    fun paginate(elements: List<Element>, forced: Map<Int, Int> = emptyMap()): Pagination {
        val contd = if (opts.autoContd) Analysis.contd(elements) else BooleanArray(elements.size)
        val blocks = buildBlocks(elements, contd)
        val pages = ArrayList<PageLayout>()
        val elementPage = IntArray(elements.size) { -1 }
        var cur = ArrayList<PLine>()
        val lockNos = ArrayList<Int>()
        var curLock = if (forced.isEmpty()) 0 else 1

        var used = 0f
        fun flush() { pages.add(PageLayout(cur)); lockNos.add(curLock); cur = ArrayList(); used = 0f; curLock = 0 }
        fun remaining() = L - used
        fun push(ln: PLine) { cur.add(ln); used += ln.h }
        fun place(lines: List<PLine>) {
            for (ln in lines) {
                for (s in ln.segs) if (s.element >= 0 && elementPage[s.element] < 0) elementPage[s.element] = pages.size
                push(ln)
            }
        }
        fun placeSpace(n: Int) { if (cur.isNotEmpty()) repeat(n) { if (used + 1f <= L) push(PLine(emptyList())) } }

        var bi = 0
        while (bi < blocks.size) {
            val b = blocks[bi]
            // Locked script: this block began a page when it was locked, so it still does.
            forced[b.firstElement]?.let { n ->
                if (cur.isNotEmpty()) flush()
                curLock = n
            }
            val sb = if (cur.isEmpty()) 0 else b.spaceBefore
            val need = sb + b.lines.height()

            // Keep headings with at least two lines of what follows.
            var keepExtra = 0f
            if (b.kind == Kind.HEADING && bi + 1 < blocks.size) {
                val nx = blocks[bi + 1]
                keepExtra = nx.spaceBefore + nx.lines.take(2).height()
            }

            if (need + keepExtra <= remaining()) {
                placeSpace(sb); place(b.lines); bi++; continue
            }

            // Doesn't fit: try to split.
            val room = remaining() - sb
            val split = when (b.kind) {
                Kind.ACTION -> actionSplit(b, room)
                Kind.DIALOGUE -> if (opts.moreContd) dialogueSplit(b, room - 1) else -1
                else -> -1
            }
            if (split > 0) {
                placeSpace(sb)
                place(b.lines.subList(0, split))
                if (b.kind == Kind.DIALOGUE) {
                    push(PLine(listOf(Seg("(MORE)", 22, ElementType.CHARACTER, -1, 0))))
                }
                flush()
                if (b.kind == Kind.DIALOGUE) {
                    val cue = b.cueText.replace(Regex("\\s*\\(CONT['’]D\\)", RegexOption.IGNORE_CASE), "") + " (CONT'D)"
                    push(withHeight(PLine(listOf(Seg(cue, 22, ElementType.CHARACTER, -1, b.revision)))))
                }
                place(b.lines.subList(split, b.lines.size))
                bi++
                continue
            }

            if (cur.isEmpty()) {
                // Bigger than a whole page: force-split at the page boundary.
                val fit = maxOf(1, fitting(b.lines, L))
                place(b.lines.subList(0, fit))
                if (fit < b.lines.size) {
                    flush()
                    place(b.lines.subList(fit, b.lines.size))
                }
                bi++
                continue
            }
            flush()
        }
        if (cur.isNotEmpty() || pages.isEmpty()) flush()
        // Elements that produced no lines (notes, empties) inherit the previous page.
        var last = 0
        for (i in elementPage.indices) { if (elementPage[i] < 0) elementPage[i] = last else last = elementPage[i] }
        return Pagination(pages, elementPage, if (forced.isEmpty()) IntArray(0) else lockNos.toIntArray())
    }

    private fun actionSplit(b: Block, room: Float): Int {
        if (b.lines.size < 4) return -1
        val hi = minOf(fitting(b.lines, room), b.lines.size - 2)
        if (hi < 2) return -1
        // Fill the page: break as low as allowed, but prefer the end of a sentence if one is within two lines.
        for (k in hi downTo maxOf(2, hi - 2)) {
            val seg = b.lines[k - 1].segs.firstOrNull()
            if (seg != null && TextWrap.endsSentence(seg.text)) return k
        }
        return hi
    }

    private fun dialogueSplit(b: Block, room: Float): Int {
        // Industry rules: the cue stays with at least 2 lines of speech on this page, and at least
        // 2 lines carry over. Never break right after a parenthetical. Fill the page as far as the
        // rules allow, but prefer the end of a sentence if one is within two lines of the lowest break.
        val hi = minOf(fitting(b.lines, room), b.lines.size - 2)
        val lo = b.cueLines + 2
        var best = -1
        for (k in hi downTo lo) if (b.splitAfter[k - 1]) { best = k; break }
        if (best < 0) return -1
        for (k in best downTo maxOf(lo, best - 2)) if (b.splitAfter[k - 1] && b.sentenceAfter[k - 1]) return k
        return best
    }

    private fun linesFor(text: String, spec: TypeSpec, type: ElementType, index: Int, revision: Int): List<PLine> {
        val raw = if (text.isEmpty()) listOf("") else TextWrap.wrap(text, spec.width)
        val out = ArrayList<PLine>()
        for (s in raw) {
            val w = TextWrap.cells(s).let { kotlin.math.ceil(it - 0.001f).toInt() }
            val col = when (spec.align) {
                Align.LEFT -> spec.indent
                Align.RIGHT -> (spec.indent + spec.width - w).coerceAtLeast(0)
                Align.CENTER -> spec.indent + ((spec.width - w) / 2).coerceAtLeast(0)
            }
            // Kannada, Hindi…: a taller line where the marks need it, so lines never overlap.
            out += withHeight(PLine(listOf(Seg(s, col, type, index, revision))))
        }
        return out
    }

    private fun buildBlocks(elements: List<Element>, contd: BooleanArray): List<Block> {
        val out = ArrayList<Block>()
        var i = 0
        while (i < elements.size) {
            val e = elements[i]
            if (!e.type.printed) { i++; continue }
            if (e.type == ElementType.CHARACTER) {
                val end = dialogueEnd(elements, i)
                // Is the next block a dual-dialogue partner?
                if (end < elements.size && elements[end].type == ElementType.CHARACTER && elements[end].dual) {
                    val end2 = dialogueEnd(elements, end)
                    out.add(dualBlock(elements, i, end, end2, contd))
                    i = end2
                    continue
                }
                out.add(dialogueBlock(elements, i, end, contd))
                i = end
                continue
            }
            val spec = Specs.of(e.type, opts)
            val lines = linesFor(e.text, spec, e.type, i, e.revision)
            val kind = when (e.type) {
                ElementType.SCENE_HEADING, ElementType.SHOT -> Kind.HEADING
                ElementType.ACTION -> Kind.ACTION
                else -> Kind.OTHER
            }
            out.add(Block(kind, lines, spec.spaceBefore, i, i..i))
            i++
        }
        return out
    }

    private fun dialogueEnd(elements: List<Element>, start: Int): Int {
        var j = start + 1
        while (j < elements.size) {
            val t = elements[j].type
            if (t == ElementType.DIALOGUE || t == ElementType.PARENTHETICAL || t == ElementType.LYRICS) j++
            else if (t == ElementType.NOTE) j++
            else break
        }
        return j
    }

    private fun cueText(e: Element, contd: Boolean): String {
        val t = e.text.replace("^", "").trim()
        return if (contd) "$t (CONT'D)" else t
    }

    private fun dialogueBlock(elements: List<Element>, start: Int, end: Int, contd: BooleanArray): Block {
        val lines = ArrayList<PLine>()
        val splitAfter = ArrayList<Boolean>()
        val sentenceAfter = ArrayList<Boolean>()
        val cue = cueText(elements[start], contd[start])
        val cueLines = linesFor(cue, Specs.of(ElementType.CHARACTER, opts), ElementType.CHARACTER, start, elements[start].revision)
        lines.addAll(cueLines); repeat(cueLines.size) { splitAfter.add(false); sentenceAfter.add(false) }
        for (j in start + 1 until end) {
            val e = elements[j]
            if (!e.type.printed) continue
            val ls = linesFor(e.text, Specs.of(e.type, opts), e.type, j, e.revision)
            ls.forEachIndexed { k, ln ->
                lines.add(ln)
                val lastOfElement = k == ls.size - 1
                val sentence = ln.segs.firstOrNull()?.let { TextWrap.endsSentence(it.text) } == true
                val ok = when (e.type) {
                    // Any line of a speech is a legal break; the last line only if more follows (a parenthetical or speech).
                    ElementType.DIALOGUE, ElementType.LYRICS -> !lastOfElement || j + 1 < end
                    ElementType.PARENTHETICAL -> false // never leave a parenthetical at the bottom
                    else -> false
                }
                splitAfter.add(ok)
                sentenceAfter.add(sentence)
            }
        }
        val sp = Specs.of(ElementType.CHARACTER, opts).spaceBefore
        return Block(Kind.DIALOGUE, lines, sp, start, start until end, cueLines.size, splitAfter.toBooleanArray(), sentenceAfter.toBooleanArray(), cue, elements[start].revision)
    }

    private fun dualBlock(elements: List<Element>, s1: Int, e1: Int, e2: Int, contd: BooleanArray): Block {
        fun column(start: Int, end: Int, right: Boolean): List<Seg> {
            val segs = ArrayList<Seg>()
            for (j in start until end) {
                val e = elements[j]
                if (!e.type.printed) continue
                val spec = Specs.dual(e.type, right)
                val text = if (e.type == ElementType.CHARACTER) cueText(e, false) else e.text
                for (s in TextWrap.wrap(text.ifEmpty { " " }, spec.width)) segs.add(Seg(s, spec.indent, e.type, j, e.revision))
            }
            return segs
        }
        val left = column(s1, e1, false)
        val right = column(e1, e2, true)
        val n = maxOf(left.size, right.size)
        val lines = (0 until n).map { k -> withHeight(PLine(listOfNotNull(left.getOrNull(k), right.getOrNull(k)))) }
        return Block(Kind.DUAL, lines, 1, s1, s1 until e2)
    }
}
