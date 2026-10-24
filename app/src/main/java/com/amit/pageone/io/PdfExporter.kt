package com.amit.pageone.io

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.res.ResourcesCompat
import com.amit.pageone.R
import com.amit.pageone.core.ElementType
import com.amit.pageone.core.Paginator
import com.amit.pageone.core.RevisionColor
import com.amit.pageone.core.Script
import com.amit.pageone.core.rangeIn
import com.amit.pageone.core.tops
import com.amit.pageone.data.AppSettings
import com.amit.pageone.data.SceneNumbers
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Renders a script to an industry-format PDF (12pt Courier Prime, 6 lines/inch). */
object PdfExporter {

    private const val LEFT = 108f      // 1.5"
    private const val TOP = 72f        // 1.0"
    private const val LINE = 12f
    private const val CHAR = 7.2f      // 10 characters per inch

    /** Extra knobs for sides and other special prints. */
    data class Options(
        /** Printed top-left on every page, e.g. "SIDES — MAYA". */
        val header: String = "",
        /** Use these scene labels (by heading id) instead of the script's own. */
        val sceneLabels: Map<Long, String>? = null,
        val titlePage: Boolean? = null,
        /** Ignore the production lock's page breaks (sides are re-paginated). */
        val ignoreLock: Boolean = false,
        /** Annotated copy: colour-mark noted text, with a dot in the margin. */
        val notes: Boolean = false,
    )

    private sealed class Target { data class Page(val index: Int, val y: Float) : Target(); data class Url(val url: String) : Target() }
    private class Link(val page: Int, val x0: Float, val y0: Float, val x1: Float, val y1: Float, val target: Target)
    private class Mark(val line: Int, val x0: Float, val x1: Float, val color: Int, val first: Boolean)

    fun export(context: Context, script: Script, settings: AppSettings, out: OutputStream, options: Options = Options()) {
        val paper = settings.paper
        val lock = if (options.ignoreLock) com.amit.pageone.core.LockState() else script.extras.lock
        val pagination = Paginator(settings.layout).paginate(script.elements, com.amit.pageone.core.Locking.forcedBreaks(script.elements, lock))
        val labels = options.sceneLabels ?: com.amit.pageone.core.Locking.sceneLabels(script.elements, script.extras.lock)
        val regular = ResourcesCompat.getFont(context, R.font.courier_prime) ?: Typeface.MONOSPACE
        val bold = ResourcesCompat.getFont(context, R.font.courier_prime_bold) ?: Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = regular; textSize = 12f; color = Color.BLACK }
        val boldPaint = Paint(paint).apply { typeface = bold }
        val markPaint = Paint(paint)
        val indicPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT; textSize = PrintMeasure.INDIC_PT; color = Color.BLACK }

        // scene numbers by element index
        val sceneNo = HashMap<Int, String>()
        script.elements.forEachIndexed { i, e -> if (e.type == ElementType.SCENE_HEADING) labels[e.id]?.let { sceneNo[i] = it } }

        // ---- annotated copy: where each note's words sit on the printed page
        val notes = if (options.notes) script.extras.comments.filter { !it.resolved }
            .sortedWith(compareBy({ c -> script.elements.indexOfFirst { it.id == c.elementId }.let { if (it < 0) Int.MAX_VALUE else it } }, { it.start })) else emptyList()
        val marksByPage = HashMap<Int, MutableList<Mark>>()
        if (notes.isNotEmpty()) {
            val wanted = notes.mapNotNull { c -> script.elements.indexOfFirst { it.id == c.elementId }.takeIf { it >= 0 } }.toSet()
            val segsOf = HashMap<Int, MutableList<Triple<Int, Int, com.amit.pageone.core.Seg>>>()
            pagination.pages.forEachIndexed { pi, pl -> pl.lines.forEachIndexed { li, ln -> ln.segs.forEach { s -> if (s.element in wanted) segsOf.getOrPut(s.element) { ArrayList() } += Triple(pi, li, s) } } }
            notes.forEach { c ->
                val ei = script.elements.indexOfFirst { it.id == c.elementId }.takeIf { it >= 0 } ?: return@forEach
                val text = script.elements[ei].text
                val r = c.rangeIn(text) ?: return@forEach
                var cursor = 0; var first = true
                for ((pi, li, s) in segsOf[ei].orEmpty()) {
                    if (s.text.isEmpty()) continue
                    val pos = text.indexOf(s.text, cursor).takeIf { it >= 0 } ?: continue
                    cursor = pos + s.text.length
                    val a = maxOf(r.first, pos); val b = minOf(r.last + 1, pos + s.text.length)
                    if (a >= b) continue
                    marksByPage.getOrPut(pi) { ArrayList() } += Mark(li, LEFT + (s.col + cellsOf(s.text, a - pos)) * CHAR, LEFT + (s.col + cellsOf(s.text, b - pos)) * CHAR, c.color.toInt(), first)
                    first = false
                }
            }
        }
        val links = ArrayList<Link>()
        val hiPaint = Paint().apply { style = Paint.Style.FILL }
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val doc = PdfDocument()
        var pageNo = 0
        val rightEdge = LEFT + 60 * CHAR // 7.5"

        fun watermark(c: android.graphics.Canvas) {
            if (settings.pdfWatermark.isBlank()) return
            val wp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = bold; textSize = 64f; color = Color.argb(28, 0, 0, 0); textAlign = Paint.Align.CENTER
            }
            c.save()
            c.rotate(-45f, paper.widthPt / 2f, paper.heightPt / 2f)
            c.drawText(settings.pdfWatermark, paper.widthPt / 2f, paper.heightPt / 2f, wp)
            c.restore()
        }

        // ---- title page ----
        if (options.titlePage ?: settings.pdfTitlePage) {
            val tp = script.titlePage
            val page = doc.startPage(PdfDocument.PageInfo.Builder(paper.widthPt, paper.heightPt, ++pageNo).create())
            val c = page.canvas
            val cx = paper.widthPt / 2f
            fun center(text: String, y: Float, p: Paint) { c.drawText(text, cx - p.measureText(text) / 2f, y, p) }
            fun wrapCenter(text: String, y0: Float): Float {
                var y = y0
                for (ln in com.amit.pageone.core.TextWrap.wrap(text, 52)) { center(ln, y, paint); y += LINE }
                return y
            }
            var y = paper.heightPt * 0.34f
            val title = tp.title.ifBlank { script.title }.uppercase()
            for (ln in com.amit.pageone.core.TextWrap.wrap(title, 40)) {
                center(ln, y, boldPaint)
                val tw = boldPaint.measureText(ln)
                c.drawLine(cx - tw / 2, y + 2, cx + tw / 2, y + 2, boldPaint)
                y += LINE
            }
            y += LINE * 3
            for (cr in tp.credits.filter { it.names.isNotBlank() }) {
                center(cr.role, y, paint); y += LINE * 2
                for (ln in cr.names.split('\n')) { center(ln.trim(), y, paint); y += LINE }
                y += LINE * 2
            }
            if (tp.basedOnLine.isNotBlank()) y = wrapCenter(tp.basedOnLine, y)

            // bottom-left: contact block
            val contact = tp.contactLines
            var by = paper.heightPt - 72f - (contact.size - 1) * LINE
            for (ln in contact) { if (ln.isNotEmpty()) c.drawText(ln, LEFT, by, paint); by += LINE }
            // bottom-right: draft, date, copyright, registration
            val right = listOf(tp.draft, tp.draftDate, tp.copyright, tp.registration).filter { it.isNotBlank() }
            var ry = paper.heightPt - 72f - (right.size - 1) * LINE
            for (ln in right) { c.drawText(ln, rightEdge - paint.measureText(ln), ry, paint); ry += LINE }
            if (tp.notes.isNotBlank()) {
                val nl = tp.notes.lines()
                var ny = paper.heightPt - 72f - (maxOf(contact.size, right.size) + 2 + nl.size - 1) * LINE
                for (ln in nl) { c.drawText(ln, LEFT, ny, paint); ny += LINE }
            }
            watermark(c)
            doc.finishPage(page)
        }

        // ---- script pages ----
        val hasMarks = script.elements.any { it.revision > 0 }
        val iso = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val shortDate = SimpleDateFormat("dd/MM/yy", Locale.getDefault())
        fun revDate(level: Int): String = runCatching { shortDate.format(iso.parse(script.revisionDates[level]!!)!!) }
            .getOrDefault(shortDate.format(Date()))
        val tint = Paint().apply { style = Paint.Style.FILL }

        pagination.pages.forEachIndexed { pi, pl ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(paper.widthPt, paper.heightPt, ++pageNo).create())
            val c = page.canvas
            // The page's revision = the newest set any line on it belongs to (like coloured paper in a production draft).
            val pageRev = pl.lines.maxOfOrNull { l -> l.segs.maxOfOrNull { it.revision } ?: 0 } ?: 0
            if (pageRev > 0 && settings.pdfColoredPages && hasMarks) {
                val rc = RevisionColor.level(pageRev).argb.toInt()
                tint.color = (rc and 0x00FFFFFF) or (0x2E shl 24)
                c.drawRect(0f, 0f, paper.widthPt.toFloat(), paper.heightPt.toFloat(), tint)
                tint.color = rc
                c.drawRect(0f, 0f, 10f, paper.heightPt.toFloat(), tint) // coloured margin edge
            }
            // header
            run {
                val num = "${pagination.label(pi)}."
                c.drawText(num, rightEdge - paint.measureText(num), 36f + 9f, paint)
            }
            val headLeft = listOfNotNull(
                options.header.takeIf { it.isNotBlank() },
                if (settings.pdfRevisionMarks && pageRev > 0) "${RevisionColor.level(pageRev).label} Rev. ${revDate(pageRev)}" else null,
            ).joinToString("   ")
            if (headLeft.isNotEmpty()) c.drawText(headLeft, LEFT, 36f + 9f, paint)
            val docIndex = pageNo - 1
            val tops = pl.tops()
            marksByPage[pi]?.forEach { m ->
                val top = TOP + tops[m.line] * LINE
                hiPaint.color = (m.color and 0x00FFFFFF) or (0x55 shl 24)
                c.drawRect(m.x0, top + 0.5f, m.x1, top + LINE + 1.5f, hiPaint)
                if (m.first) {
                    // A dot in the margin, so a note on a short word is still easy to spot.
                    dotPaint.color = darker(m.color)
                    c.drawCircle(rightEdge + 4 * CHAR, top + 6.5f, 2.6f, dotPaint)
                }
            }
            pl.lines.forEachIndexed { li, line ->
                val y = TOP + tops[li] * LINE + 9.5f
                var revised = 0
                for (s in line.segs) {
                    val isHeading = s.type == ElementType.SCENE_HEADING
                    val p = if (isHeading && settings.boldSceneHeadings) boldPaint else paint
                    val x = LEFT + s.col * CHAR
                    if (com.amit.pageone.core.TextWrap.tall(s.text)) {
                        // Kannada, Hindi…: Indic words in the phone's font for that script, English in Courier, on one
                        // baseline. The paginator wrapped the line by these same widths (PrintMeasure) and gave it a
                        // taller slot where the marks need room; the shrink below is only a last-resort guard.
                        val maxW = com.amit.pageone.core.Specs.of(s.type, settings.layout).width * CHAR
                        val runs = com.amit.pageone.core.TextWrap.runs(s.text)
                        val latinSize = 12f; val indicSize = PrintMeasure.INDIC_PT
                        fun widthAt(k: Float): Float { var w = 0f; for ((t, ind) in runs) { val q = if (ind) indicPaint else p; q.textSize = (if (ind) indicSize else latinSize) * k; w += q.measureText(t) }; return w }
                        val k = widthAt(1f).let { w -> if (w > maxW) (maxW / w).coerceAtLeast(0.6f) else 1f }
                        val base = y
                        var cx = x
                        indicPaint.isFakeBoldText = p === boldPaint
                        for ((t, ind) in runs) {
                            val q = if (ind) indicPaint else p
                            q.textSize = (if (ind) indicSize else latinSize) * k
                            c.drawText(t, cx, base, q); cx += q.measureText(t)
                        }
                        indicPaint.textSize = indicSize; p.textSize = latinSize
                    } else c.drawText(s.text, x, y, p)
                    if (isHeading && settings.underlineSceneHeadings) {
                        c.drawLine(x, y + 2, x + p.measureText(s.text), y + 2, p)
                    }
                    if (s.revision > revised) revised = s.revision
                    if (isHeading && s.element >= 0) {
                        val no = sceneNo[s.element]
                        val firstLine = pl.lines.indexOfFirst { l -> l.segs.any { it.element == s.element } } == li
                        if (no != null && firstLine) {
                            val label = no
                            if (settings.pdfSceneNumbers == SceneNumbers.LEFT || settings.pdfSceneNumbers == SceneNumbers.BOTH)
                                c.drawText(label, LEFT - 8 * CHAR + (4 - label.length).coerceAtLeast(0) * CHAR, y, p)
                            if (settings.pdfSceneNumbers == SceneNumbers.RIGHT || settings.pdfSceneNumbers == SceneNumbers.BOTH)
                                c.drawText(label, rightEdge + 1.5f * CHAR, y, p)
                        }
                    }
                }
                if (revised > 0 && settings.pdfRevisionMarks) {
                    // Coloured asterisk on screen-PDFs; still reads as a normal * when printed in black.
                    markPaint.color = if (settings.pdfColoredPages) darker(RevisionColor.level(revised).argb.toInt()) else android.graphics.Color.BLACK
                    c.drawText("*", rightEdge + 6 * CHAR, y, markPaint)
                }
            }
            watermark(c)
            doc.finishPage(page)
        }
        if (links.isEmpty()) { doc.writeTo(out); doc.close(); return }
        // Android's PDF writer can't make links, so add them with PdfBox in a second pass.
        val raw = java.io.ByteArrayOutputStream().also { doc.writeTo(it) }.toByteArray()
        doc.close()
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context.applicationContext)
        com.tom_roush.pdfbox.pdmodel.PDDocument.load(raw).use { pd ->
            val h = paper.heightPt.toFloat()
            for (l in links) {
                val target = l.target
                val pg = pd.getPage(l.page)
                val a = com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink()
                a.rectangle = com.tom_roush.pdfbox.pdmodel.common.PDRectangle(l.x0, h - l.y1, l.x1 - l.x0, l.y1 - l.y0)
                a.borderStyle = com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary().apply { width = 0f }
                when (target) {
                    is Target.Url -> a.action = com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionURI().apply { uri = target.url }
                    is Target.Page -> a.destination = com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageXYZDestination().apply {
                        page = pd.getPage(target.index.coerceIn(0, pd.numberOfPages - 1)); top = (h - target.y).toInt(); left = 0
                    }
                }
                pg.annotations.add(a)
            }
            pd.save(out)
        }
    }
}

/** Printed columns taken by the first [n] characters of [s] (Indic letters are narrower than their character count). */
private fun cellsOf(s: String, n: Int): Float = com.amit.pageone.core.TextWrap.cells(s.substring(0, n.coerceIn(0, s.length)))

private fun darker(c: Int): Int {
    val r = (android.graphics.Color.red(c) * 0.6f).toInt()
    val g = (android.graphics.Color.green(c) * 0.6f).toInt()
    val b = (android.graphics.Color.blue(c) * 0.6f).toInt()
    return android.graphics.Color.rgb(r, g, b)
}
