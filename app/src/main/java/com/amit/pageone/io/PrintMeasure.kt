package com.amit.pageone.io

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import com.amit.pageone.core.TextWrap

/**
 * Measures printed text with the fonts it actually prints in, so the paginator wraps a Kannada,
 * Hindi or Tamil line at the same right margin as an English one, and gives scripts whose marks
 * stack above and below the letters a tall enough line. The desktop app's io/measure.ts does the
 * same with its fonts.
 *
 * Courier runs advance exactly one column a character (7.2 pt); Indic runs are drawn in the
 * phone's own font for that script at [INDIC_PT] (see [PdfExporter]) and measured the same way.
 */
object PrintMeasure : TextWrap.Measurer {
    /** Indic text prints a point smaller than Courier's 12, so it sits level with it. */
    const val INDIC_PT = 11f
    private const val COL = 7.2f

    /** Ink a 1/6" line holds without touching its neighbours, measured from the baseline (pt). */
    private const val ROOM_ABOVE = 10f
    private const val ROOM_BELOW = 3f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT; textSize = INDIC_PT }
    private val bounds = Rect()
    private val widths = HashMap<String, Float>()
    private val heights = HashMap<String, Float>()

    private fun <T> remember(m: HashMap<String, T>, k: String, v: T): T { if (m.size > 4000) m.clear(); m[k] = v; return v }

    @Synchronized override fun cells(s: String): Float {
        widths[s]?.let { return it }
        val runs = TextWrap.runs(s)
        if (runs.none { it.second }) return s.codePointCount(0, s.length).toFloat()
        var n = 0f
        for ((t, indic) in runs) n += if (indic) paint.measureText(t) / COL else t.codePointCount(0, t.length).toFloat()
        // A hair of slack, for the PDF drawing a run a touch wider than measured.
        return remember(widths, s, n * 1.01f)
    }

    @Synchronized override fun height(s: String): Float {
        heights[s]?.let { return it }
        val indic = TextWrap.runs(s).filter { it.second }.joinToString(" ") { it.first.trim() }
        if (indic.isBlank()) return 1f
        paint.getTextBounds(indic, 0, indic.length, bounds)
        val over = maxOf(0f, -bounds.top - ROOM_ABOVE, bounds.bottom - ROOM_BELOW)
        // The extra space sits evenly above and below the text, in half-line steps.
        return remember(heights, s, 1f + kotlin.math.ceil(2f * over / 6f) / 2f)
    }
}
