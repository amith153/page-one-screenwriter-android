package com.amit.pageone.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.clickable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amit.pageone.core.Align
import com.amit.pageone.core.Element
import com.amit.pageone.core.ElementType
import com.amit.pageone.core.LayoutOptions
import com.amit.pageone.core.RevisionColor
import com.amit.pageone.core.SCENE_COLORS
import com.amit.pageone.core.Specs
import com.amit.pageone.ui.theme.EditorPalette
import com.amit.pageone.ui.theme.ScriptFont
import com.amit.pageone.data.AppGraph
import com.amit.pageone.data.Misspelling
import com.amit.pageone.data.SpellChecker
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay

/** Geometry of the on-screen page, derived from the available width. */
@Stable
data class PageMetrics(
    val leftGutter: Dp,
    val rightGutter: Dp,
    val col: Dp,              // one character column
    val fontSize: TextUnit,
    val lineHeight: TextUnit,
    val lineDp: Dp,           // a blank line, for "space before"
)

/** Callbacks from a row to the editor (kept in one stable object). */
@Stable
class RowActions(
    val onText: (id: Long, text: String, cursor: Int) -> Unit,
    val onEnter: (id: Long, before: String, after: String) -> Unit,
    val onPaste: (id: Long, full: String) -> Unit,
    val onBackspaceAtStart: (id: Long) -> Boolean,
    val onCycle: (id: Long) -> Unit,
    val onFocused: (id: Long, cursor: Int) -> Unit,
    val onCursor: (cursor: Int) -> Unit,
    val onConsumeFocus: (FocusReq) -> Unit,
    val onUndo: () -> Unit,
    val onRedo: () -> Unit,
    /** Hardware-keyboard shortcut; return true if handled. */
    val onShortcut: (id: Long, s: Shortcut) -> Boolean = { _, _ -> false },
    /** Selection changed inside an element (for anchoring notes). */
    val onSelect: (id: Long, start: Int, end: Int) -> Unit = { _, _, _ -> },
    /** The note marker in the right margin was tapped. */
    val onCommentsTap: (id: Long) -> Unit = {},
)

/** Keyboard shortcuts for writers on Bluetooth keyboards, tablets and Chromebooks. */
sealed class Shortcut(val keys: String, val what: String) {
    class SetType(val type: ElementType, keys: String) : Shortcut(keys, type.label)
    data object CycleBack : Shortcut("Shift+Tab", "Previous element type")
    data object Find : Shortcut("Ctrl+F", "Find & replace")
    data object Dual : Shortcut("Ctrl+D", "Dual dialogue")
    data object Comment : Shortcut("Ctrl+M", "Pin a note to the selection")
    data object Note : Shortcut("Ctrl+Shift+N", "Note to self")
    data object NewScene : Shortcut("Ctrl+Enter", "New scene after this one")
    data object PrevScene : Shortcut("Alt+↑", "Previous scene")
    data object NextScene : Shortcut("Alt+↓", "Next scene")
    data object Doctor : Shortcut("Ctrl+K", "Script Doctor")
    data object SaveVersion : Shortcut("Ctrl+S", "Save a version")
    data object Help : Shortcut("Ctrl+/", "Keyboard shortcuts")
    data object Focus : Shortcut("Ctrl+Shift+F", "Focus mode")
    data object SidePane : Shortcut("Ctrl+B", "Navigator / side panel")
    data object Cards : Shortcut("Ctrl+Shift+C", "Index cards")
    data object Export : Shortcut("Ctrl+E", "Export & share")
    data object PageView : Shortcut("Ctrl+Shift+P", "Page view")

    companion object {
        val typeKeys = listOf(
            Key.One to ElementType.SCENE_HEADING, Key.Two to ElementType.ACTION, Key.Three to ElementType.CHARACTER,
            Key.Four to ElementType.PARENTHETICAL, Key.Five to ElementType.DIALOGUE, Key.Six to ElementType.TRANSITION,
            Key.Seven to ElementType.SHOT, Key.Eight to ElementType.LYRICS, Key.Nine to ElementType.CENTERED, Key.Zero to ElementType.NOTE,
        )
        val all: List<Shortcut> get() = typeKeys.mapIndexed { i, (_, t) -> SetType(t, "Ctrl+${(i + 1) % 10}") } +
            listOf(CycleBack, NewScene, PrevScene, NextScene, Find, Dual, Comment, Note, Doctor, SaveVersion, Focus, SidePane, Cards, PageView, Export, Help)

        fun from(ev: androidx.compose.ui.input.key.KeyEvent): Shortcut? {
            val ctrl = ev.isCtrlPressed || ev.isMetaPressed
            val shift = ev.isShiftPressed
            val alt = ev.isAltPressed
            if (ctrl && !alt) typeKeys.firstOrNull { it.first == ev.key }?.let { return SetType(it.second, "") }
            return when {
                !ctrl && !alt && shift && ev.key == Key.Tab -> CycleBack
                alt && ev.key == Key.DirectionUp -> PrevScene
                alt && ev.key == Key.DirectionDown -> NextScene
                !ctrl -> null
                ev.key == Key.Enter -> NewScene
                shift && ev.key == Key.N -> Note
                shift && ev.key == Key.F -> Focus
                shift && ev.key == Key.C -> Cards
                shift && ev.key == Key.P -> PageView
                ev.key == Key.F || ev.key == Key.H -> Find
                ev.key == Key.D -> Dual
                ev.key == Key.M -> Comment
                ev.key == Key.K -> Doctor
                ev.key == Key.S -> SaveVersion
                ev.key == Key.Slash -> Help
                ev.key == Key.B -> SidePane
                ev.key == Key.E -> Export
                else -> null
            }
        }
    }
}

private fun placeholderFor(t: ElementType) = when (t) {
    ElementType.SCENE_HEADING -> "INT. LOCATION - DAY"
    ElementType.ACTION -> "Action…"
    ElementType.CHARACTER -> "CHARACTER"
    ElementType.PARENTHETICAL -> "(beat)"
    ElementType.DIALOGUE -> "Dialogue…"
    ElementType.TRANSITION -> "CUT TO:"
    ElementType.SHOT -> "ANGLE ON"
    ElementType.LYRICS -> "Lyrics…"
    ElementType.CENTERED -> "Centered text"
    ElementType.NOTE -> "Note to self (not printed)"
}

/** Appends a faint " (CONT'D)" after a character cue without it being part of the text. */
private class SuffixTransformation(private val suffix: String, private val color: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val out = buildAnnotatedString {
            append(text)
            pushStyle(SpanStyle(color = color))
            append(suffix)
            pop()
        }
        val len = text.length
        return TransformedText(out, object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = offset
            override fun transformedToOriginal(offset: Int) = offset.coerceAtMost(len)
        })
    }
}

@Composable
fun ElementRow(
    element: Element,
    metrics: PageMetrics,
    layout: LayoutOptions,
    palette: EditorPalette,
    accent: Color,
    sceneNumber: String?,
    showSceneNumber: Boolean,
    contd: Boolean,
    inDual: Boolean,
    dim: Boolean,
    showRevision: Boolean,
    isFirstOnPage: Boolean,
    boldHeadings: Boolean,
    focusReq: FocusReq?,
    actions: RowActions,
    authorColor: Color? = null,
    spellCheck: Boolean = false,
    spellSkip: Set<String> = emptySet(),
    /** Dual dialogue: false = left speaker, true = right speaker, null = normal row. */
    dualSide: Boolean? = null,
    /** Open anchored notes on this element: highlighted range + author colour. */
    noteRanges: List<Pair<IntRange, Color>> = emptyList(),
    /** Find matches on this element; [findCurrent] is the one Find is on. */
    findRanges: List<IntRange> = emptyList(),
    findCurrent: IntRange? = null,
) {
    val type = element.type
    val spec = when {
        dualSide != null -> Specs.dual(type, dualSide)
        type == ElementType.NOTE -> Specs.of(ElementType.ACTION, layout)
        else -> Specs.of(type, layout)
    }
    var tfv by remember(element.id) { mutableStateOf(TextFieldValue(element.text, TextRange(element.text.length))) }
    // Keep local field in sync with external changes (undo, replace, merges…)
    val value = if (tfv.text == element.text) tfv else {
        val sel = TextRange(tfv.selection.start.coerceAtMost(element.text.length), tfv.selection.end.coerceAtMost(element.text.length))
        TextFieldValue(element.text, sel).also { tfv = it }
    }
    val fr = remember { FocusRequester() }
    var hasFocus by remember { mutableStateOf(false) }

    // ---- spell check: ask the phone's checker (debounced), draw squiggles ourselves
    val checkable = spellCheck && type in SpellChecker.CHECKED
    val personal by AppGraph.spell.personal.collectAsState()
    val ignored by AppGraph.spell.ignored.collectAsState()
    var misses by remember(element.id) { mutableStateOf(emptyList<Misspelling>()) }
    var justTyped by remember(element.id) { mutableStateOf(false) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    LaunchedEffect(element.text, checkable, personal, ignored, spellSkip) {
        if (!checkable || element.text.isBlank()) { misses = emptyList(); return@LaunchedEffect }
        delay(350)
        misses = runCatching { AppGraph.spell.check(element.text, spellSkip) }.getOrDefault(emptyList())
    }
    val sel = value.selection
    val liveMisses = misses.filter { m ->
        m.end <= value.text.length && value.text.regionMatches(m.start, m.word, 0, m.word.length) &&
            // don't nag about the word you're still typing
            !(hasFocus && justTyped && sel.collapsed && sel.start == m.end)
    }
    val activeMiss = if (hasFocus && sel.collapsed) liveMisses.firstOrNull { sel.start in it.start..it.end } else null

    LaunchedEffect(focusReq) {
        val r = focusReq ?: return@LaunchedEffect
        if (r.id != element.id) return@LaunchedEffect
        if (!r.focus) { actions.onConsumeFocus(r); return@LaunchedEffect }
        val len = element.text.length
        tfv = TextFieldValue(element.text, TextRange(r.start.coerceIn(0, len), r.end.coerceIn(0, len)))
        runCatching { fr.requestFocus() }
        actions.onConsumeFocus(r)
    }

    val spaceBefore = if (isFirstOnPage) 0 else spec.spaceBefore
    val bold = type == ElementType.SCENE_HEADING && boldHeadings
    val style = TextStyle(
        fontFamily = ScriptFont,
        fontSize = metrics.fontSize,
        lineHeight = metrics.lineHeight,
        color = if (type == ElementType.NOTE) palette.noteInk else palette.ink,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (type == ElementType.NOTE) FontStyle.Italic else FontStyle.Normal,
        textAlign = when (spec.align) {
            Align.RIGHT -> TextAlign.End
            Align.CENTER -> TextAlign.Center
            Align.LEFT -> TextAlign.Start
        },
    )
    val visual = if (contd && type == ElementType.CHARACTER) SuffixTransformation(" (CONT'D)", palette.faint) else VisualTransformation.None

    Row(
        Modifier
            .fillMaxWidth()
            .background(if (dualSide != null) Color.Transparent else palette.paper)
            .alpha(if (dim) 0.28f else 1f)
            .padding(top = metrics.lineDp * spaceBefore),
    ) {
        // ---- left gutter: scene number, dual marker, scene colour ----
        Box(
            Modifier.width(metrics.leftGutter),
            contentAlignment = Alignment.TopEnd,
        ) {
            if (type == ElementType.SCENE_HEADING) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                    if (element.color != 0) {
                        Box(
                            Modifier.padding(end = 3.dp).width(3.dp).height(metrics.lineDp * 0.9f)
                                .background(Color(SCENE_COLORS[element.color.coerceIn(0, SCENE_COLORS.size - 1)]), RoundedCornerShape(2.dp))
                        )
                    }
                    if (showSceneNumber && sceneNumber != null) {
                        Text(
                            sceneNumber, style = style.copy(color = palette.gutter, fontWeight = FontWeight.Normal, textAlign = TextAlign.End),
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    }
                }
            }
            if (authorColor != null) {
                Box(
                    Modifier.align(Alignment.TopStart).padding(start = 4.dp).width(4.dp).height(metrics.lineDp)
                        .background(authorColor, RoundedCornerShape(2.dp))
                )
            }
            if (inDual) {
                Box(
                    Modifier.padding(end = 6.dp).width(2.dp).height(metrics.lineDp)
                        .background(accent.copy(alpha = 0.7f), RoundedCornerShape(1.dp))
                )
            }
        }

        // ---- the text itself ----
        Box(Modifier.weight(1f)) {
            val fieldMod = Modifier
                .padding(start = metrics.col * spec.indent)
                .width(metrics.col * spec.width)
                .then(
                    if (type == ElementType.NOTE) Modifier.background(palette.note, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
                    else Modifier
                )
                .then(
                    if (noteRanges.isEmpty() && findRanges.isEmpty()) Modifier else Modifier.drawBehind {
                        val l = layout ?: return@drawBehind
                        val n = l.layoutInput.text.length
                        noteRanges.forEach { (r, c) ->
                            val s = r.first.coerceIn(0, n); val e = (r.last + 1).coerceIn(s, n)
                            if (e > s) drawPath(l.getPathForRange(s, e), c.copy(alpha = 0.28f))
                        }
                        findRanges.forEach { r ->
                            val s = r.first.coerceIn(0, n); val e = (r.last + 1).coerceIn(s, n)
                            if (e > s) drawPath(l.getPathForRange(s, e), FindHit.copy(alpha = if (r == findCurrent) 0.75f else 0.3f))
                        }
                    }
                )
                .focusRequester(fr)
                .onFocusChanged {
                    hasFocus = it.isFocused
                    if (it.isFocused) actions.onFocused(element.id, tfv.selection.start)
                }
                .then(
                    if (liveMisses.isEmpty()) Modifier else Modifier.drawWithContent {
                        drawContent()
                        val l = layout ?: return@drawWithContent
                        liveMisses.forEach { m -> drawSquiggle(l, m.start, m.end, SquiggleRed) }
                    }
                )
                .onPreviewKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val sc = Shortcut.from(ev)
                    when {
                        sc != null && actions.onShortcut(element.id, sc) -> true
                        ev.key == Key.Backspace && value.selection.collapsed && value.selection.start == 0 ->
                            actions.onBackspaceAtStart(element.id)
                        ev.key == Key.Tab -> { actions.onCycle(element.id); true }
                        ev.isCtrlPressed && ev.key == Key.Z && ev.isShiftPressed -> { actions.onRedo(); true }
                        ev.isCtrlPressed && ev.key == Key.Z -> { actions.onUndo(); true }
                        ev.isCtrlPressed && ev.key == Key.Y -> { actions.onRedo(); true }
                        else -> false
                    }
                }

            BasicTextField(
                value = value,
                onValueChange = { nv ->
                    val oldNl = value.text.count { it == '\n' }
                    val newNl = nv.text.count { it == '\n' }
                    if (newNl > oldNl) {
                        // Accept the raw value first so the field's IME buffer is reset
                        // when the ViewModel's (newline-free) text comes back.
                        tfv = nv
                        val pos = nv.selection.start
                        val before = nv.text.substring(0, pos)
                        if (newNl == oldNl + 1 && before.endsWith("\n")) {
                            actions.onEnter(element.id, before.dropLast(1), nv.text.substring(pos))
                        } else {
                            actions.onPaste(element.id, nv.text)
                        }
                        return@BasicTextField
                    }
                    justTyped = nv.text != value.text
                    val text = if (type.allCaps) nv.text.uppercase() else nv.text
                    val fixed = if (text.length == nv.text.length) nv.copy(text = text) else TextFieldValue(text, TextRange(text.length))
                    tfv = fixed
                    if (fixed.text != element.text) actions.onText(element.id, fixed.text, fixed.selection.start)
                    else actions.onCursor(fixed.selection.start)
                    actions.onSelect(element.id, fixed.selection.start, fixed.selection.end)
                },
                modifier = fieldMod,
                textStyle = style,
                cursorBrush = SolidColor(accent),
                visualTransformation = visual,
                onTextLayout = { layout = it },
                keyboardOptions = KeyboardOptions(
                    capitalization = if (type.allCaps) KeyboardCapitalization.Characters else KeyboardCapitalization.Sentences,
                ),
                decorationBox = { inner ->
                    Box {
                        if (value.text.isEmpty() && hasFocus) {
                            Text(placeholderFor(type), style = style.copy(color = palette.faint.copy(alpha = 0.7f)))
                        }
                        inner()
                    }
                },
            )

            val miss = activeMiss
            val l = layout
            if (miss != null && l != null && miss.end <= l.layoutInput.text.length) {
                val density = LocalDensity.current
                val line = l.getLineForOffset(miss.start)
                val x = with(density) { (metrics.col * spec.indent).roundToPx() } + l.getHorizontalPosition(miss.start, true).toInt()
                val y = l.getLineBottom(line).toInt() + with(density) { 6.dp.roundToPx() }
                SpellPopup(
                    word = miss.word,
                    offset = IntOffset(x, y),
                    onPick = { rep ->
                        val r = SpellChecker.matchCase(miss.word, rep)
                        val nt = value.text.replaceRange(miss.start, miss.end, r)
                        val cur = miss.start + r.length
                        tfv = TextFieldValue(nt, TextRange(cur))
                        justTyped = false
                        actions.onText(element.id, nt, cur)
                    },
                    onAdd = { AppGraph.spell.addToDictionary(miss.word) },
                    onIgnore = { AppGraph.spell.ignore(miss.word) },
                )
            }
        }

        // ---- right gutter: revision mark, note marker ----
        Box(Modifier.width(metrics.rightGutter), contentAlignment = Alignment.TopCenter) {
            if (noteRanges.isNotEmpty()) {
                Box(
                    Modifier.padding(top = 2.dp).size(12.dp).clip(RoundedCornerShape(4.dp, 4.dp, 4.dp, 0.dp))
                        .background(noteRanges.first().second).clickable { actions.onCommentsTap(element.id) },
                )
            } else if (showRevision && element.revision > 0) {
                Text(
                    "*",
                    style = style.copy(
                        color = Color(RevisionColor.level(element.revision).argb),
                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, textDecoration = TextDecoration.None,
                    ),
                )
            }
        }
    }
}

/** Gap between two sheets of paper, labelled with the new page number. */
@Composable
fun PageBreak(page: String, palette: EditorPalette, metrics: PageMetrics) {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(metrics.lineDp * 2).background(palette.paper))
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.pageEdge))
        Box(Modifier.fillMaxWidth().height(14.dp).background(palette.desk))
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.pageEdge))
        Box(
            Modifier.fillMaxWidth().background(palette.paper).padding(end = metrics.rightGutter + 4.dp, top = 10.dp, bottom = metrics.lineDp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(
                "$page.",
                style = TextStyle(fontFamily = ScriptFont, fontSize = metrics.fontSize, color = palette.gutter),
            )
        }
    }
}

@Composable
fun GutterBar(color: Color, height: Dp) {
    Box(Modifier.width(3.dp).height(height).fillMaxHeight().background(color, RoundedCornerShape(2.dp)))
}

/** Find-in-script highlight. */
private val FindHit = Color(0xFFFFC94D)

val TinyLabel = TextStyle(fontSize = 9.sp, letterSpacing = 0.8.sp, fontWeight = FontWeight.SemiBold)

@Composable
fun MiniLabel(text: String, color: Color) {
    Text(text, style = MaterialTheme.typography.labelSmall.merge(TinyLabel), color = color)
}

private val SquiggleRed = Color(0xFFE5484D)

/** A wavy red underline under text[start, end), line by line. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSquiggle(l: TextLayoutResult, start: Int, end: Int, color: Color) {
    val n = l.layoutInput.text.length
    if (start >= end || end > n) return
    val amp = 1.4.dp.toPx()
    val wave = 3.2.dp.toPx()
    val first = l.getLineForOffset(start)
    val last = l.getLineForOffset(end - 1)
    for (line in first..last) {
        val s = if (line == first) start else l.getLineStart(line)
        val e = if (line == last) end else l.getLineEnd(line, visibleEnd = true)
        if (e <= s) continue
        val x0 = l.getHorizontalPosition(s, true)
        val x1 = if (line == last || e < n) l.getHorizontalPosition(e, true).let { if (it <= x0) l.getLineRight(line) else it } else l.getLineRight(line)
        val y = l.getLineBaseline(line) + 2.2.dp.toPx()
        val path = androidx.compose.ui.graphics.Path()
        var x = minOf(x0, x1)
        val xe = maxOf(x0, x1)
        path.moveTo(x, y)
        var up = true
        while (x < xe) {
            val nx = minOf(x + wave, xe)
            path.quadraticTo((x + nx) / 2f, if (up) y - amp else y + amp, nx, y)
            up = !up
            x = nx
        }
        drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2.dp.toPx()))
    }
}

/** Suggestions bubble under a misspelt word. Not focusable, so the keyboard stays up. */
@Composable
private fun SpellPopup(word: String, offset: IntOffset, onPick: (String) -> Unit, onAdd: () -> Unit, onIgnore: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val suggestions by androidx.compose.runtime.produceState(emptyList<String>(), word) { value = AppGraph.spell.suggestionsFor(word).take(4) }
    Popup(offset = offset, properties = PopupProperties(focusable = false, clippingEnabled = true)) {
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(14.dp), color = cs.surfaceContainerHigh, shadowElevation = 8.dp, tonalElevation = 2.dp,
        ) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (suggestions.isEmpty()) {
                    Text("No suggestions", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp))
                }
                suggestions.forEach { sg ->
                    Text(
                        SpellChecker.matchCase(word, sg),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = cs.primary,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { onPick(sg) }.padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
                Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height(22.dp).background(cs.outlineVariant))
                Text(
                    "Add", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onAdd).padding(horizontal = 10.dp, vertical = 8.dp),
                )
                Text(
                    "Ignore", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onIgnore).padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        }
    }
}
