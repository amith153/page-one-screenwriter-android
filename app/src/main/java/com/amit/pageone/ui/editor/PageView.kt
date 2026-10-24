package com.amit.pageone.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.amit.pageone.core.tops
import com.amit.pageone.core.ElementType
import com.amit.pageone.core.Pagination
import com.amit.pageone.core.RevisionColor
import com.amit.pageone.core.Script
import com.amit.pageone.data.AppSettings
import com.amit.pageone.data.SceneNumbers
import com.amit.pageone.ui.theme.ScriptFont
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/**
 * The script exactly as it prints — real pages, (MORE) and (CONT'D) where speeches break,
 * locked A-page numbers — with a page-turn as you swipe. Tap a line to edit it.
 */
@Composable
fun PageView(
    script: Script,
    pagination: Pagination,
    settings: AppSettings,
    startPage: Int,
    sceneLabels: Map<Long, String>,
    onEdit: (elementIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pages = pagination.pages
    val pager = rememberPagerState(initialPage = startPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))) { pages.size }
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    Column(modifier.fillMaxSize().background(Color(0xFF2B2E36))) {
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = 1) { pi ->
            val offset = (pager.currentPage - pi) + pager.currentPageOffsetFraction
            Box(
                Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 16.dp)
                    .graphicsLayer {
                        // Page turn: the outgoing sheet hinges on its spine and swings away.
                        val o = offset.coerceIn(-1f, 1f)
                        cameraDistance = 14f * density
                        transformOrigin = TransformOrigin(if (o > 0) 0f else 1f, 0.5f)
                        rotationY = o * -75f
                        alpha = 1f - (o.absoluteValue * 0.35f)
                        val s = 1f - o.absoluteValue * 0.06f
                        scaleX = s; scaleY = s
                    },
                contentAlignment = Alignment.Center,
            ) {
                Sheet(script, pagination, pi, settings, sceneLabels, onEdit)
            }
        }
        Row(Modifier.fillMaxWidth().background(cs.surfaceContainer).padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Page ${pagination.label(pager.currentPage)} of ${pagination.label(pages.size - 1)}", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.padding(horizontal = 8.dp))
            var drag by remember { mutableFloatStateOf(-1f) }
            if (pages.size > 1) Slider(
                value = if (drag >= 0) drag else pager.currentPage.toFloat(),
                onValueChange = { drag = it },
                onValueChangeFinished = { val p = drag.toInt(); drag = -1f; scope.launch { pager.scrollToPage(p) } },
                valueRange = 0f..(pages.size - 1).coerceAtLeast(1).toFloat(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Sheet(script: Script, p: Pagination, pi: Int, settings: AppSettings, labels: Map<Long, String>, onEdit: (Int) -> Unit) {
    val paper = settings.paper
    val page = p.pages[pi]
    val density = LocalDensity.current
    val pageRev = page.lines.maxOfOrNull { l -> l.segs.maxOfOrNull { it.revision } ?: 0 } ?: 0
    val tint = if (pageRev > 0 && settings.pdfColoredPages) Color(RevisionColor.level(pageRev).argb).copy(alpha = 0.18f) else Color.Transparent
    BoxWithConstraints(
        Modifier.widthIn(max = 720.dp).aspectRatio(paper.widthPt / paper.heightPt.toFloat())
            .shadow(14.dp, RoundedCornerShape(3.dp)).background(Color.White).background(tint),
    ) {
        val pt = maxWidth / paper.widthPt.toFloat() // one PostScript point, in dp
        val base = TextStyle(fontFamily = ScriptFont, fontSize = with(density) { (pt * 12f).toSp() }, color = Color(0xFF151515), lineHeight = with(density) { (pt * 12f).toSp() })
        val faint = base.copy(color = Color(0xFF8A8680))
        Text("${p.label(pi)}.", style = base, modifier = Modifier.offset(x = pt * (108f + 60 * 7.2f) - pt * 7.2f * (p.label(pi).length + 1), y = pt * 36f))
        if (pageRev > 0 && settings.pdfRevisionMarks) Text("${RevisionColor.level(pageRev).label} Rev.", style = faint, modifier = Modifier.offset(x = pt * 108f, y = pt * 36f))
        val tops = page.tops()
        page.lines.forEachIndexed { li, line ->
            val y = pt * (72f + tops[li] * 12f)
            if (line.segs.isEmpty()) return@forEachIndexed
            val el = line.segs.firstOrNull { it.element >= 0 }?.element
            Box(Modifier.offset(y = y).fillMaxWidth().height(pt * 12f).then(if (el != null) Modifier.clickable { onEdit(el) } else Modifier)) {
                line.segs.forEach { s ->
                    val heading = s.type == ElementType.SCENE_HEADING
                    val st = if (heading && settings.boldSceneHeadings) base.copy(fontWeight = FontWeight.Bold, textDecoration = if (settings.underlineSceneHeadings) TextDecoration.Underline else null) else base
                    if (com.amit.pageone.core.TextWrap.tall(s.text)) {
                        val indicStyle = SpanStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Default, fontSize = with(density) { (pt * com.amit.pageone.io.PrintMeasure.INDIC_PT).toSp() })
                        val runs: List<Pair<String, Boolean>> = com.amit.pageone.core.TextWrap.runs(s.text)
                        val text = androidx.compose.ui.text.buildAnnotatedString {
                            for ((t, ind) in runs) if (ind) withStyle(indicStyle) { append(t) } else append(t)
                        }
                        Text(text, style = st, softWrap = false, maxLines = 1, modifier = Modifier.offset(x = pt * (108f + s.col * 7.2f)))
                    } else Text(s.text, style = st, softWrap = false, maxLines = 1, modifier = Modifier.offset(x = pt * (108f + s.col * 7.2f)))
                    if (heading && s.element >= 0 && settings.pdfSceneNumbers != SceneNumbers.NONE) {
                        val first = page.lines.indexOfFirst { l -> l.segs.any { it.element == s.element } } == li
                        val label = script.elements.getOrNull(s.element)?.id?.let { labels[it] }
                        if (first && label != null) {
                            if (settings.pdfSceneNumbers == SceneNumbers.LEFT || settings.pdfSceneNumbers == SceneNumbers.BOTH)
                                Text(label, style = st, modifier = Modifier.offset(x = pt * (108f - 7.2f * (label.length + 3))))
                            if (settings.pdfSceneNumbers == SceneNumbers.RIGHT || settings.pdfSceneNumbers == SceneNumbers.BOTH)
                                Text(label, style = st, modifier = Modifier.offset(x = pt * (108f + 61.5f * 7.2f)))
                        }
                    }
                }
                val rev = line.segs.maxOf { it.revision }
                if (rev > 0 && settings.pdfRevisionMarks) Text("*", style = base.copy(color = Color(RevisionColor.level(rev).argb), fontWeight = FontWeight.Bold), modifier = Modifier.offset(x = pt * (108f + 66 * 7.2f)))
            }
        }
    }
}

@Composable
fun rememberStartPage(pagination: Pagination?, elementIndex: Int): Int = remember(pagination, elementIndex) {
    pagination?.elementPage?.getOrNull(elementIndex) ?: 0
}
