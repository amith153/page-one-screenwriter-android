package com.amit.pageone.io

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import java.text.DateFormat
import java.util.Date

/** Clean, printable table reports (scene / character / location / breakdown) as PDF. */
object ReportPdf {
    data class Column(val title: String, val weight: Float)

    fun write(out: OutputStream, title: String, subtitle: String, columns: List<Column>, rows: List<List<String>>, landscape: Boolean = true) {
        val w = if (landscape) 792 else 612
        val h = if (landscape) 612 else 792
        val margin = 40f
        val doc = PdfDocument()
        val head = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); textSize = 16f; color = Color.BLACK }
        val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.SANS_SERIF; textSize = 9f; color = Color.rgb(100, 100, 100) }
        val th = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); textSize = 8.5f; color = Color.WHITE }
        val td = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.SANS_SERIF; textSize = 8.5f; color = Color.rgb(25, 25, 25) }
        val band = Paint().apply { color = Color.rgb(28, 31, 41) }
        val zebra = Paint().apply { color = Color.rgb(246, 243, 236) }
        val gold = Paint().apply { color = Color.rgb(231, 179, 90) }
        val total = columns.sumOf { it.weight.toDouble() }.toFloat()
        val widths = columns.map { (w - 2 * margin) * it.weight / total }
        val lineH = 11f

        fun wrap(text: String, width: Float): List<String> {
            val out = ArrayList<String>()
            for (para in text.split('\n')) {
                var line = ""
                for (word in para.split(' ')) {
                    val cand = if (line.isEmpty()) word else "$line $word"
                    if (td.measureText(cand) <= width - 6) line = cand else { if (line.isNotEmpty()) out += line; line = word }
                }
                out += line
            }
            return out.take(6)
        }

        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = 0f
        fun header() {
            val c = page!!.canvas
            c.drawRect(0f, 0f, w.toFloat(), 4f, gold)
            c.drawText(title, margin, margin + 6, head)
            c.drawText(subtitle + "  ·  " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date()) + "  ·  page $pageNo", margin, margin + 20, sub)
            y = margin + 34
            c.drawRect(margin, y, w - margin, y + 16, band)
            var x = margin
            columns.forEachIndexed { i, col -> c.drawText(col.title.uppercase(), x + 4, y + 11, th); x += widths[i] }
            y += 18
        }
        fun newPage() {
            page?.let { doc.finishPage(it) }
            page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, ++pageNo).create())
            header()
        }
        newPage()
        rows.forEachIndexed { r, row ->
            val cells = row.mapIndexed { i, v -> wrap(v, widths.getOrElse(i) { 60f }) }
            val rh = (cells.maxOfOrNull { it.size } ?: 1) * lineH + 6
            if (y + rh > h - margin) newPage()
            val c = page!!.canvas
            if (r % 2 == 1) c.drawRect(margin, y, w - margin, y + rh, zebra)
            var x = margin
            cells.forEachIndexed { i, lines ->
                lines.forEachIndexed { k, ln -> c.drawText(ln, x + 4, y + 10 + k * lineH, td) }
                x += widths.getOrElse(i) { 60f }
            }
            y += rh
        }
        page?.let { doc.finishPage(it) }
        doc.writeTo(out)
        doc.close()
    }
}
