package com.amit.pageone.ui.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amit.pageone.core.BreakdownCategory
import com.amit.pageone.core.MoodImage
import com.amit.pageone.core.PlotThread
import com.amit.pageone.core.Production
import com.amit.pageone.core.SCENE_COLORS
import com.amit.pageone.core.SceneInfo
import com.amit.pageone.core.SceneMeta
import com.amit.pageone.core.Script
import com.amit.pageone.core.ScriptExtras
import com.amit.pageone.data.AppGraph
import com.amit.pageone.ui.components.EmptyNote
import com.amit.pageone.ui.components.GlassCard
import com.amit.pageone.ui.components.Pill
import com.amit.pageone.ui.components.SectionHeader
import com.amit.pageone.ui.theme.LocalBrand
import com.amit.pageone.ui.theme.ScriptFont
import kotlinx.coroutines.launch

private enum class StripMode(val label: String) { INTEXT("INT / EXT"), DAYNIGHT("Day / Night"), THREAD("Storyline"), COLOR("Card colour") }

// ====================================================================== X-ray

@Composable
fun XRaySheet(
    script: Script,
    xs: List<Production.SceneX>,
    onDismiss: () -> Unit,
    onJump: (Long) -> Unit,
    onExtras: ((ScriptExtras) -> ScriptExtras) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val brand = LocalBrand.current
    var mode by remember { mutableStateOf(if (script.extras.threads.isNotEmpty()) StripMode.THREAD else StripMode.INTEXT) }
    val threads = script.extras.threads
    val total = xs.sumOf { it.eighths }.coerceAtLeast(1)

    fun colorFor(x: Production.SceneX): Color = when (mode) {
        StripMode.INTEXT -> when (x.intExt) { "INT" -> brand.accent; "EXT" -> cs.tertiary; "INT/EXT" -> brand.accent2; else -> cs.outline }
        StripMode.DAYNIGHT -> when {
            x.time.contains("NIGHT") || x.time.contains("EVENING") || x.time.contains("DUSK") -> Color(0xFF5C6BC0)
            x.time.contains("DAY") || x.time.contains("MORNING") || x.time.contains("DAWN") || x.time.contains("AFTERNOON") -> Color(0xFFFFCA6B)
            else -> cs.outline
        }
        StripMode.THREAD -> script.extras.scene(x.headingId).threads.firstOrNull()?.let { id -> threads.firstOrNull { it.id == id } }
            ?.let { Color(SCENE_COLORS[it.color.coerceIn(1, SCENE_COLORS.size - 1)]) } ?: cs.outlineVariant
        StripMode.COLOR -> script.elements.firstOrNull { it.id == x.headingId }?.color?.takeIf { it != 0 }?.let { Color(SCENE_COLORS[it.coerceIn(0, SCENE_COLORS.size - 1)]) } ?: cs.outlineVariant
    }

    AppSheet(onDismiss) {
        Column(Modifier.heightIn(max = 720.dp).verticalScroll(rememberScrollState())) {
            SheetTitle("Script X-ray", "The shape of your story at a glance — tap any bar to jump there")
            if (xs.isEmpty()) { EmptyNote(Icons.Outlined.Movie, "No scenes yet", "Add scene headings and the X-ray fills in."); return@Column }
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                XStat("Scenes", "${xs.size}", Modifier.weight(1f))
                XStat("Avg scene", Production.formatEighths(total / xs.size.coerceAtLeast(1)), Modifier.weight(1f))
                XStat("Longest", xs.maxBy { it.eighths }.let { "${Production.formatEighths(it.eighths)} · ${it.label}" }, Modifier.weight(1.3f))
            }
            Spacer(Modifier.height(18.dp))
            Column(Modifier.padding(horizontal = 20.dp)) {
                SectionHeader("Pacing")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StripMode.entries.forEach { m -> FilterChip(mode == m, { mode = m }, { Text(m.label) }) }
                }
                Spacer(Modifier.height(10.dp))
                // Scene strip: width = length; tap to jump.
                Canvas(
                    Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(10.dp)).pointerInput(xs) {
                        detectTapGestures { p ->
                            var acc = 0f
                            for (x in xs) { acc += size.width * x.eighths / total.toFloat(); if (p.x <= acc) { onJump(x.headingId); break } }
                        }
                    }
                ) {
                    var x0 = 0f
                    xs.forEach { x ->
                        val w = size.width * x.eighths / total.toFloat()
                        drawRect(colorFor(x), Offset(x0, 0f), Size((w - 1.5f).coerceAtLeast(1f), size.height))
                        x0 += w
                    }
                }
                Spacer(Modifier.height(8.dp))
                // Rhythm: scene lengths in order, so runs of long scenes stand out.
                val maxE = xs.maxOf { it.eighths }.coerceAtLeast(1)
                Canvas(Modifier.fillMaxWidth().height(70.dp).pointerInput(xs) {
                    detectTapGestures { p -> xs.getOrNull((p.x / size.width * xs.size).toInt().coerceIn(0, xs.size - 1))?.let { onJump(it.headingId) } }
                }) {
                    val bw = size.width / xs.size
                    xs.forEachIndexed { i, x ->
                        val bh = size.height * x.eighths / maxE.toFloat()
                        drawRoundRect(colorFor(x), Offset(i * bw + bw * 0.15f, size.height - bh), Size((bw * 0.7f).coerceAtLeast(1f), bh), CornerRadius(2.dp.toPx()))
                    }
                }
                Legend(mode, threads)
            }

            // ---- character heatmap
            val top = remember(xs) {
                xs.flatMap { it.speaking }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(12).map { it.key }
            }
            if (top.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Column(Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader("Who's in which scene")
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val labelW = 92.dp
                        val cell = ((maxWidth - labelW) / xs.size).coerceIn(5.dp, 22.dp)
                        Row {
                            Column(Modifier.width(labelW)) {
                                top.forEach { n -> Text(n, style = MaterialTheme.typography.labelSmall.copy(fontFamily = ScriptFont), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.height(18.dp).padding(end = 6.dp)) }
                            }
                            Row(Modifier.horizontalScroll(rememberScrollState())) {
                                xs.forEach { x ->
                                    Column(Modifier.width(cell).clickable { onJump(x.headingId) }) {
                                        top.forEach { n ->
                                            val c = characterColor(n)
                                            val v = when (n) { in x.speaking -> 0.9f; in x.mentioned -> 0.35f; else -> 0f }
                                            Box(Modifier.height(18.dp).fillMaxWidth().padding(0.5.dp).clip(RoundedCornerShape(2.dp)).background(if (v > 0) c.copy(alpha = v) else cs.surfaceContainerHighest))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Text("Solid: speaks · faint: mentioned in action", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                }
            }

            // ---- plot threads
            Spacer(Modifier.height(18.dp))
            Column(Modifier.padding(horizontal = 20.dp)) {
                SectionHeader("Storylines")
                ThreadEditor(threads, counts = threads.associate { t -> t.id to script.extras.scenes.values.count { t.id in it.threads } }, onChange = { list -> onExtras { it.copy(threads = list) } })
                Text("Tag scenes with a storyline from each index card.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
            }
        }
    }
}

@Composable
private fun XStat(label: String, value: String, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(16.dp), color = cs.surfaceContainerHigh, modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(mode: StripMode, threads: List<PlotThread>) {
    val cs = MaterialTheme.colorScheme
    val brand = LocalBrand.current
    val items: List<Pair<String, Color>> = when (mode) {
        StripMode.INTEXT -> listOf("INT" to brand.accent, "EXT" to cs.tertiary, "INT/EXT" to brand.accent2)
        StripMode.DAYNIGHT -> listOf("Day" to Color(0xFFFFCA6B), "Night" to Color(0xFF5C6BC0), "Other" to cs.outline)
        StripMode.THREAD -> threads.map { it.name to Color(SCENE_COLORS[it.color.coerceIn(1, SCENE_COLORS.size - 1)]) } + ("Untagged" to cs.outlineVariant)
        StripMode.COLOR -> emptyList()
    }
    FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEach { (n, c) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(c)); Spacer(Modifier.width(4.dp))
                Text(n, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
        }
    }
}

/** Add, rename, recolour and remove A/B/C storylines. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThreadEditor(threads: List<PlotThread>, counts: Map<String, Int>, onChange: (List<PlotThread>) -> Unit) {
    var adding by remember { mutableStateOf("") }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        threads.forEach { t ->
            var menu by remember { mutableStateOf(false) }
            Box {
                InputChip(
                    selected = true, onClick = { menu = true },
                    label = { Text("${t.name} · ${counts[t.id] ?: 0}") },
                    leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(Color(SCENE_COLORS[t.color.coerceIn(1, SCENE_COLORS.size - 1)]))) },
                )
                DropdownMenu(menu, { menu = false }) {
                    Row(Modifier.padding(horizontal = 12.dp)) {
                        (1 until SCENE_COLORS.size).forEach { i ->
                            Box(Modifier.padding(2.dp).size(22.dp).clip(CircleShape).background(Color(SCENE_COLORS[i])).clickable { onChange(threads.map { if (it.id == t.id) it.copy(color = i) else it }); menu = false })
                        }
                    }
                    DropdownMenuItem(text = { Text("Remove storyline") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { onChange(threads.filter { it.id != t.id }); menu = false })
                }
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
        OutlinedTextField(
            adding, { adding = it }, singleLine = true, placeholder = { Text(if (threads.isEmpty()) "A story — e.g. “The heist”" else "Add a storyline") },
            shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f),
        )
        IconButton(onClick = {
            val n = adding.trim(); if (n.isEmpty()) return@IconButton
            val used = threads.map { it.color }.toSet()
            val color = (1 until SCENE_COLORS.size).firstOrNull { it !in used } ?: 1
            onChange(threads + PlotThread(java.util.UUID.randomUUID().toString().take(8), n, color)); adding = ""
        }) { Icon(Icons.Filled.Add, "Add storyline") }
    }
}

// ====================================================================== scene card (full)

/** Everything about one scene: heading, synopsis and colour; storylines and beat; breakdown; mood images. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SceneSheet(
    scene: SceneInfo,
    label: String,
    script: Script,
    onDismiss: () -> Unit,
    onSaveCard: (heading: String, synopsis: String, color: Int) -> Unit,
    onExtras: ((ScriptExtras) -> ScriptExtras) -> Unit,
    onGoTo: () -> Unit,
    onDelete: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var tab by remember { mutableStateOf(0) }
    var heading by remember(scene.elementId) { mutableStateOf(scene.heading) }
    var synopsis by remember(scene.elementId) { mutableStateOf(scene.synopsis) }
    var color by remember(scene.elementId) { mutableStateOf(scene.color) }
    val meta = script.extras.scene(scene.elementId)
    fun saveCard() { if (heading != scene.heading || synopsis != scene.synopsis || color != scene.color) onSaveCard(heading, synopsis, color) }
    fun setMeta(m: SceneMeta) = onExtras { it.withScene(scene.elementId, m) }

    AppSheet({ saveCard(); onDismiss() }) {
        Column(Modifier.heightIn(min = 460.dp)) {
            Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Scene $label", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { saveCard(); onGoTo() }) { Text("Open in script") }
            }
            PrimaryScrollableTabRow(tab, edgePadding = 16.dp, containerColor = androidx.compose.ui.graphics.Color.Transparent) {
                listOf("Card", "Story", "Breakdown", "Mood").forEachIndexed { i, t -> Tab(tab == i, { saveCard(); tab = i }, text = { Text(t) }) }
            }
            Column(Modifier.heightIn(max = 600.dp).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp)) {
                when (tab) {
                    0 -> {
                        OutlinedTextField(
                            heading, { heading = it.uppercase() }, label = { Text("Scene heading") }, singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = ScriptFont), modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(synopsis, { synopsis = it }, label = { Text("Synopsis / beat") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(12.dp))
                        SectionHeader("Card colour")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SCENE_COLORS.indices.forEach { i ->
                                Box(
                                    Modifier.size(30.dp).clip(CircleShape).background(if (i == 0) cs.surfaceContainerHighest else Color(SCENE_COLORS[i]))
                                        .border(2.dp, if (i == color) cs.onSurface else Color.Transparent, CircleShape).clickable { color = i },
                                    contentAlignment = Alignment.Center,
                                ) { if (i == color) Icon(Icons.Filled.Check, null, Modifier.size(16.dp), tint = if (i == 0) cs.onSurface else Color.Black) }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        TextButton(onClick = onDelete) {
                            Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp), tint = cs.error); Spacer(Modifier.width(6.dp))
                            Text(if (script.extras.lock.locked) "Omit scene (keeps its number)" else "Delete scene", color = cs.error)
                        }
                    }
                    1 -> {
                        SectionHeader("Storylines")
                        if (script.extras.threads.isEmpty()) Text("No storylines yet — add A, B and C stories here or in Script X-ray.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            script.extras.threads.forEach { t ->
                                val on = t.id in meta.threads
                                FilterChip(on, { setMeta(meta.copy(threads = if (on) meta.threads - t.id else meta.threads + t.id)) }, { Text(t.name) },
                                    leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(Color(SCENE_COLORS[t.color.coerceIn(1, SCENE_COLORS.size - 1)]))) })
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        ThreadEditor(script.extras.threads, emptyMap(), onChange = { list -> onExtras { it.copy(threads = list) } })
                        Spacer(Modifier.height(16.dp))
                        SectionHeader("Beat")
                        val st = script.extras.structure
                        if (st == null) Text("Choose a story structure (menu → Structure) to place this scene on a beat.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        else FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            st.beats.forEach { b ->
                                val on = scene.elementId in b.sceneIds
                                FilterChip(on, {
                                    onExtras { x -> x.copy(structure = x.structure?.let { s -> s.copy(beats = s.beats.map { bb -> if (bb.id == b.id) bb.copy(sceneIds = if (on) bb.sceneIds - scene.elementId else bb.sceneIds + scene.elementId) else bb }) }) }
                                }, { Text(b.name) })
                            }
                        }
                    }
                    2 -> {
                        Text("Tag what this scene needs. Cast fills in from who speaks; the breakdown report gathers it all.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        BreakdownCategory.entries.forEach { cat ->
                            BreakdownRow(cat, meta.breakdown[cat].orEmpty()) { list -> setMeta(meta.copy(breakdown = (meta.breakdown + (cat to list)).filterValues { it.isNotEmpty() })) }
                        }
                    }
                    3 -> MoodGrid(meta.moods.map { MoodImage(it) }, onAdd = { setMeta(meta.copy(moods = meta.moods + it)) }, onRemove = { f -> setMeta(meta.copy(moods = meta.moods - f)) })
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BreakdownRow(cat: BreakdownCategory, items: List<String>, onChange: (List<String>) -> Unit) {
    var text by remember { mutableStateOf("") }
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(cat.label, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items.forEach { v -> InputChip(true, { onChange(items - v) }, { Text(v) }, trailingIcon = { Icon(Icons.Outlined.Close, "Remove", Modifier.size(14.dp)) }) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(text, { text = it }, singleLine = true, placeholder = { Text("Add ${cat.label.lowercase()}") }, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).heightIn(min = 48.dp))
            IconButton(onClick = { val v = text.trim(); if (v.isNotEmpty() && v !in items) onChange(items + v); text = "" }) { Icon(Icons.Filled.Add, "Add") }
        }
    }
}

/** Grid of mood images with add-from-gallery and add-today's-still. */
@Composable
fun MoodGrid(images: List<MoodImage>, onAdd: (String) -> Unit, onRemove: (String) -> Unit, onCaption: ((String, String) -> Unit)? = null) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(12)) { uris ->
        scope.launch { uris.forEach { u -> AppGraph.media.import(u)?.let(onAdd) } }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
            Icon(Icons.Outlined.AddPhotoAlternate, null); Spacer(Modifier.width(6.dp)); Text("Add images")
        }
        OutlinedButton(onClick = {
            scope.launch {
                val s = AppGraph.settings.state.value
                val still = com.amit.pageone.data.Inspiration.stillFor(ctx, s.stillsSource, s.stillsFolder)
                val b = still?.bitmap
                if (b != null) onAdd(AppGraph.media.save(b)) else android.widget.Toast.makeText(ctx, "No still today — try again online", android.widget.Toast.LENGTH_SHORT).show()
            }
        }) { Icon(Icons.Outlined.Movie, null); Spacer(Modifier.width(6.dp)); Text("Today's still") }
    }
    Spacer(Modifier.height(10.dp))
    var viewing by remember { mutableStateOf<Int?>(null) }
    viewing?.let { i -> com.amit.pageone.ui.components.ImageViewer(images.map { it.file }, i, images.map { it.caption }) { viewing = null } }
    if (images.isEmpty()) EmptyNote(Icons.Outlined.PhotoLibrary, "No images yet", "Frames, locations, faces, colours — whatever this feels like.")
    else {
        val rows = images.chunked(2)
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                row.forEach { m ->
                    Box(Modifier.weight(1f).aspectRatio(16f / 10f).clip(RoundedCornerShape(14.dp)).clickable { viewing = images.indexOf(m) }) {
                        MediaImage(m.file, Modifier.fillMaxSize())
                        IconButton(onClick = { onRemove(m.file) }, modifier = Modifier.align(Alignment.TopEnd).size(36.dp)) {
                            Box(Modifier.size(26.dp).clip(CircleShape).background(Color(0x99000000)), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Close, "Remove", tint = Color.White, modifier = Modifier.size(16.dp)) }
                        }
                        if (m.caption.isNotBlank()) Text(m.caption, color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.align(Alignment.BottomStart).background(Color(0x88000000)).padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ====================================================================== mood board

/** The whole film's look: a script-wide board plus every scene's images. */
@Composable
fun MoodBoardSheet(script: Script, sceneLabel: (Long) -> String, onDismiss: () -> Unit, onExtras: ((ScriptExtras) -> ScriptExtras) -> Unit, onOpenScene: (Long) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val sceneImages = remember(script.extras.scenes) { script.extras.scenes.filterValues { it.moods.isNotEmpty() } }
    val headings = remember(script.elements) { script.elements.filter { it.type == com.amit.pageone.core.ElementType.SCENE_HEADING }.associate { it.id to it.text } }
    AppSheet(onDismiss) {
        Column(Modifier.heightIn(max = 760.dp).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text("Mood board", style = MaterialTheme.typography.headlineSmall)
            Text("The look and feel of the film. Images stay on this phone.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            MoodGrid(
                script.extras.board,
                onAdd = { f -> onExtras { it.copy(board = it.board + MoodImage(f)) } },
                onRemove = { f -> onExtras { it.copy(board = it.board.filter { m -> m.file != f }) } },
            )
            if (sceneImages.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                SectionHeader("By scene")
                sceneImages.forEach { (id, m) ->
                    GlassCard(Modifier.fillMaxWidth().padding(vertical = 5.dp), corner = 18.dp, padding = 12.dp, onClick = { onOpenScene(id) }) {
                        Text("${sceneLabel(id)}  ${headings[id].orEmpty()}", style = MaterialTheme.typography.labelLarge.copy(fontFamily = ScriptFont, fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(8.dp))
                        var viewing by remember { mutableStateOf<Int?>(null) }
                        viewing?.let { i -> com.amit.pageone.ui.components.ImageViewer(m.moods, i) { viewing = null } }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            m.moods.forEachIndexed { i, f -> MediaImage(f, Modifier.size(width = 120.dp, height = 75.dp).clip(RoundedCornerShape(10.dp)).clickable { viewing = i }, 360) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
