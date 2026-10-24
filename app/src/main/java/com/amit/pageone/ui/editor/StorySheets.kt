package com.amit.pageone.ui.editor

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amit.pageone.core.Analysis
import com.amit.pageone.core.Beat
import com.amit.pageone.core.CharacterProfile
import com.amit.pageone.core.ElementType
import com.amit.pageone.core.Formatting
import com.amit.pageone.core.LocationProfile
import com.amit.pageone.core.SceneInfo
import com.amit.pageone.core.Script
import com.amit.pageone.core.Structure
import com.amit.pageone.core.Structures
import com.amit.pageone.data.AppGraph
import com.amit.pageone.ui.components.EmptyNote
import com.amit.pageone.ui.components.GlassCard
import com.amit.pageone.ui.components.Pill
import com.amit.pageone.ui.components.SectionHeader
import com.amit.pageone.ui.theme.LocalBrand
import com.amit.pageone.ui.theme.ScriptFont
import kotlinx.coroutines.launch

/** A thumbnail from the media folder (or nothing while it loads). */
@Composable
fun MediaImage(name: String, modifier: Modifier = Modifier, maxPx: Int = 600) {
    val bmp by produceState<Bitmap?>(null, name) { value = AppGraph.media.load(name, maxPx) }
    val b = bmp
    if (b != null) Image(b.asImageBitmap(), null, modifier, contentScale = ContentScale.Crop)
    else Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest))
}

// ====================================================================== bible

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BibleSheet(
    script: Script,
    sceneLabel: (Long) -> String,
    onDismiss: () -> Unit,
    onSaveCharacter: (CharacterProfile) -> Unit,
    onSaveLocation: (LocationProfile) -> Unit,
    onJump: (Long) -> Unit,
    initialCharacter: String? = null,
) {
    val cs = MaterialTheme.colorScheme
    var tab by remember { mutableStateOf(0) }
    var editingChar by remember { mutableStateOf(initialCharacter) }
    var editingLoc by remember { mutableStateOf<String?>(null) }
    var isolating by remember { mutableStateOf<String?>(null) }
    val stats = remember(script.elements) { Analysis.stats(script.elements).characters.associateBy { it.name } }
    val names = remember(script.elements, script.extras.characters) {
        (Analysis.characterNames(script.elements) + script.extras.characters.map { it.name }).distinct()
    }
    val scenes = remember(script.elements) { Analysis.scenes(script.elements) }
    val locations = remember(script.elements, script.extras.locations) {
        (Analysis.locations(script.elements) + script.extras.locations.map { it.name }).distinct()
    }

    AppSheet(onDismiss) {
        Column(Modifier.heightIn(min = 420.dp)) {
            when {
                isolating != null -> IsolateLines(script, isolating!!, sceneLabel, onBack = { isolating = null }, onJump = onJump)
                editingChar != null -> CharacterEditor(
                    script.extras.character(editingChar!!) ?: CharacterProfile(editingChar!!),
                    stats[editingChar!!]?.let { "${it.cues} speeches · ${it.words} words · ${it.scenes} scenes" } ?: "Not in the script yet",
                    onBack = { editingChar = null }, onSave = onSaveCharacter, onIsolate = { isolating = it },
                )
                editingLoc != null -> LocationEditor(
                    script.extras.location(editingLoc!!) ?: LocationProfile(editingLoc!!),
                    scenes.filter { Analysis.location(it.heading) == editingLoc },
                    sceneLabel, onBack = { editingLoc = null }, onSave = onSaveLocation, onJump = onJump,
                )
                else -> {
                    SheetTitle("Story bible", "Who's in your world, and where it happens")
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        SegmentedButton(tab == 0, { tab = 0 }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Characters · ${names.size}") }
                        SegmentedButton(tab == 1, { tab = 1 }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Locations · ${locations.size}") }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (tab == 0) {
                        if (names.isEmpty()) EmptyNote(Icons.Outlined.AutoStories, "No characters yet", "Characters appear here as soon as they speak in your script.")
                        LazyColumn(Modifier.heightIn(max = 560.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                            items(names, key = { it }) { n ->
                                val p = script.extras.character(n)
                                val st = stats[n]
                                Surface(shape = RoundedCornerShape(18.dp), color = cs.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(18.dp)).clickable { editingChar = n }) {
                                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        if (p?.photo?.isNotBlank() == true) MediaImage(p.photo, Modifier.size(48.dp).clip(CircleShape), 200)
                                        else Avatar(n, 48)
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(n, style = MaterialTheme.typography.titleSmall.copy(fontFamily = ScriptFont, fontWeight = FontWeight.Bold))
                                            val sub = listOf(p?.role.orEmpty(), p?.want?.takeIf { it.isNotBlank() }?.let { "wants $it" }.orEmpty()).filter { it.isNotBlank() }.joinToString(" · ")
                                            Text(sub.ifBlank { "Tap to build their profile" }, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        if (st != null) Column(horizontalAlignment = Alignment.End) {
                                            Text("${st.cues}", style = MaterialTheme.typography.titleMedium)
                                            Text("speeches", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        if (locations.isEmpty()) EmptyNote(Icons.Outlined.Place, "No locations yet", "Every place in your scene headings shows up here.")
                        LazyColumn(Modifier.heightIn(max = 560.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                            items(locations, key = { it }) { l ->
                                val p = script.extras.location(l)
                                val count = scenes.count { Analysis.location(it.heading) == l }
                                Surface(shape = RoundedCornerShape(18.dp), color = cs.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(18.dp)).clickable { editingLoc = l }) {
                                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        if (p?.photo?.isNotBlank() == true) MediaImage(p.photo, Modifier.size(width = 64.dp, height = 44.dp).clip(RoundedCornerShape(10.dp)), 300)
                                        else Box(Modifier.size(width = 64.dp, height = 44.dp).clip(RoundedCornerShape(10.dp)).background(cs.surfaceContainerHighest), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Place, null, tint = cs.onSurfaceVariant) }
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(l, style = MaterialTheme.typography.titleSmall.copy(fontFamily = ScriptFont, fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(p?.description?.ifBlank { null } ?: "$count scene${if (count == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        Pill("$count")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackHeader(title: String, sub: String?, onBack: () -> Unit) {
    Row(Modifier.padding(horizontal = 8.dp).padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        Column {
            Text(title, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = ScriptFont, fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PhotoPicker(photo: String, round: Boolean, onPicked: (String) -> Unit, onClear: () -> Unit) {
    val scope = rememberCoroutineScope()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { AppGraph.media.import(uri)?.let(onPicked) }
    }
    val shape = if (round) CircleShape else RoundedCornerShape(16.dp)
    val mod = if (round) Modifier.size(88.dp) else Modifier.fillMaxWidth().height(150.dp)
    Box(mod.clip(shape).clickable { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, contentAlignment = Alignment.Center) {
        if (photo.isNotBlank()) {
            MediaImage(photo, Modifier.fillMaxSize())
            if (!round) IconButton(onClick = onClear, modifier = Modifier.align(Alignment.TopEnd)) { Icon(Icons.Outlined.Delete, "Remove photo", tint = Color.White) }
        } else {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.AddAPhoto, "Add photo", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CharacterEditor(p0: CharacterProfile, stats: String, onBack: () -> Unit, onSave: (CharacterProfile) -> Unit, onIsolate: (String) -> Unit) {
    var p by remember(p0.name) { mutableStateOf(p0) }
    fun save() { if (p != p0) onSave(p) }
    BackHeader(p.name, stats) { save(); onBack() }
    Column(Modifier.heightIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PhotoPicker(p.photo, round = true, onPicked = { p = p.copy(photo = it); onSave(p) }, onClear = { p = p.copy(photo = "") })
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(p.role, { p = p.copy(role = it) }, label = { Text("Role") }, placeholder = { Text("Protagonist, mentor…") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(p.age, { p = p.copy(age = it) }, label = { Text("Age") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        }
        Spacer(Modifier.height(10.dp))
        Field("Want — the goal they chase", p.want) { p = p.copy(want = it) }
        Field("Need — what they must learn", p.need) { p = p.copy(need = it) }
        Field("Arc — who they are at the end", p.arc) { p = p.copy(arc = it) }
        Field("Voice — how they talk", p.voice) { p = p.copy(voice = it) }
        Field("Backstory", p.bio, lines = 4) { p = p.copy(bio = it) }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { save(); onIsolate(p.name) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.FormatQuote, null); Spacer(Modifier.width(8.dp)); Text("Read only ${p.name}'s lines")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { save(); onBack() }, modifier = Modifier.align(Alignment.End)) { Text("Done") }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Field(label: String, value: String, lines: Int = 2, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text(label) }, minLines = lines, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
}

@Composable
private fun LocationEditor(p0: LocationProfile, scenes: List<SceneInfo>, sceneLabel: (Long) -> String, onBack: () -> Unit, onSave: (LocationProfile) -> Unit, onJump: (Long) -> Unit) {
    var p by remember(p0.name) { mutableStateOf(p0) }
    fun save() { if (p != p0) onSave(p) }
    BackHeader(p.name, "${scenes.size} scene${if (scenes.size == 1) "" else "s"}") { save(); onBack() }
    Column(Modifier.heightIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        PhotoPicker(p.photo, round = false, onPicked = { p = p.copy(photo = it); onSave(p) }, onClear = { p = p.copy(photo = ""); onSave(p) })
        Spacer(Modifier.height(10.dp))
        Field("What it looks like, feels like, sounds like", p.description, 3) { p = p.copy(description = it) }
        Field("Notes (scouting, permits, period details…)", p.notes, 3) { p = p.copy(notes = it) }
        Spacer(Modifier.height(10.dp))
        SectionHeader("Scenes here")
        scenes.forEach { sc ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { save(); onJump(sc.elementId) }.padding(vertical = 8.dp, horizontal = 4.dp)) {
                Text(sceneLabel(sc.elementId), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(40.dp))
                Text(sc.heading, style = MaterialTheme.typography.bodySmall.copy(fontFamily = ScriptFont), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        TextButton(onClick = { save(); onBack() }, modifier = Modifier.align(Alignment.End)) { Text("Done") }
        Spacer(Modifier.height(12.dp))
    }
}

/** Every line one character speaks, in order, with the scene it's in — for checking their voice. */
@Composable
private fun IsolateLines(script: Script, name: String, sceneLabel: (Long) -> String, onBack: () -> Unit, onJump: (Long) -> Unit) {
    val cs = MaterialTheme.colorScheme
    data class L(val sceneId: Long?, val heading: String, val id: Long, val text: String, val paren: String)
    val lines = remember(script.elements, name) {
        val out = ArrayList<L>()
        var head: Pair<Long, String>? = null
        val els = script.elements
        var i = 0
        while (i < els.size) {
            val e = els[i]
            if (e.type == ElementType.SCENE_HEADING) head = e.id to e.text
            if (e.type == ElementType.CHARACTER && Formatting.characterName(e.text) == name) {
                var j = i + 1; var paren = ""
                while (j < els.size && els[j].type.isDialogueBlockPart && els[j].type != ElementType.CHARACTER) {
                    val d = els[j]
                    if (d.type == ElementType.PARENTHETICAL) paren = d.text
                    else { out += L(head?.first, head?.second.orEmpty(), d.id, d.text, paren); paren = "" }
                    j++
                }
                i = j; continue
            }
            i++
        }
        out
    }
    BackHeader("$name's voice", "${lines.size} lines, start to finish", onBack)
    LazyColumn(Modifier.heightIn(max = 600.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)) {
        var last: Long? = null
        lines.forEach { l ->
            if (l.sceneId != last) {
                last = l.sceneId
                item(key = "h${l.id}") {
                    Text("${l.sceneId?.let(sceneLabel) ?: ""}  ${l.heading}".trim(), style = MaterialTheme.typography.labelMedium.copy(fontFamily = ScriptFont), color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            item(key = l.id) {
                Surface(shape = RoundedCornerShape(14.dp), color = cs.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(14.dp)).clickable { onJump(l.id) }) {
                    Column(Modifier.padding(12.dp)) {
                        if (l.paren.isNotBlank()) Text(l.paren, style = MaterialTheme.typography.bodySmall.copy(fontFamily = ScriptFont), color = cs.onSurfaceVariant)
                        Text(l.text, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = ScriptFont))
                    }
                }
            }
        }
    }
}

// ====================================================================== structure

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StructureSheet(
    script: Script,
    scenes: List<SceneInfo>,
    totalPages: Int,
    pageOfScene: (Long) -> Int?,
    sceneLabel: (Long) -> String,
    onDismiss: () -> Unit,
    onSet: (Structure?) -> Unit,
    onJump: (Long) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val brand = LocalBrand.current
    val st = script.extras.structure
    var picking by remember { mutableStateOf(st == null) }
    var assigning by remember { mutableStateOf<Beat?>(null) }
    var helping by remember { mutableStateOf(false) }

    AppSheet(onDismiss) {
        Column(Modifier.heightIn(min = 420.dp)) {
            if (picking || st == null) {
                Row(Modifier.padding(end = 16.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.weight(1f)) { SheetTitle("Story structure", "Pick a shape for your story. Your index cards slot into its beats.") }
                    TextButton(onClick = { helping = true }) { Text("More") }
                }
                LazyColumn(Modifier.heightIn(max = 600.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)) {
                    items(Structures.templates, key = { it.id }) { t ->
                        GlassCard(Modifier.fillMaxWidth().padding(vertical = 5.dp), corner = 20.dp, onClick = {
                            var n = 0
                            val fresh = Structures.create(t) { "${t.id}-${n++}-${System.nanoTime() % 100000}" }
                            // Keep scene assignments for beats with the same name.
                            val keep = st?.beats?.associate { it.name to it.sceneIds }.orEmpty()
                            onSet(fresh.copy(beats = fresh.beats.map { b -> b.copy(sceneIds = keep[b.name].orEmpty()) }))
                            picking = false
                        }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(t.name, style = MaterialTheme.typography.titleMedium)
                                    Text(t.blurb, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                                }
                                Pill("${t.beats.size} beats")
                            }
                            Spacer(Modifier.height(10.dp))
                            BeatMap(t.beats.map { it.third }, emptyList(), t.acts)
                        }
                    }
                    if (st != null) item { TextButton(onClick = { picking = false }) { Text("Cancel") } }
                }
            } else {
                val checks = remember(st, totalPages, scenes) { Structures.check(st, totalPages) { pageOfScene(it) } }
                val tmpl = Structures.template(st.template)
                Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tmpl?.name ?: "Structure", style = MaterialTheme.typography.headlineSmall)
                        val placed = checks.count { it.actualPage != null }
                        Text("$placed of ${checks.size} beats placed · ${checks.count { it.onTrack }} on track", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    }
                    TextButton(onClick = { helping = true }) { Text("More") }
                    TextButton(onClick = { picking = true }) { Text("Change") }
                }
                Spacer(Modifier.height(8.dp))
                Column(Modifier.padding(horizontal = 24.dp)) {
                    BeatMap(checks.map { it.targetPage / maxOf(totalPages, st.targetPages).toFloat() }, checks.map { c -> c.actualPage?.let { it / maxOf(totalPages, st.targetPages).toFloat() } }, tmpl?.acts ?: emptyList())
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Target length", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        listOf(12, 30, 60, 90, 110, 120).forEach { n ->
                            FilterChip(st.targetPages == n, { onSet(st.copy(targetPages = n)) }, { Text("$n") }, modifier = Modifier.padding(end = 4.dp))
                        }
                    }
                }
                LazyColumn(Modifier.heightIn(max = 520.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                    var lastAct = 0
                    checks.forEach { c ->
                        if (c.beat.act != lastAct) {
                            lastAct = c.beat.act
                            item(key = "act${c.beat.act}") { SectionHeader("Act ${c.beat.act}", Modifier.padding(top = 10.dp, start = 8.dp)) }
                        }
                        item(key = c.beat.id) {
                            val color = when { c.actualPage == null -> cs.onSurfaceVariant; c.onTrack -> brand.success; else -> brand.warning }
                            Surface(shape = RoundedCornerShape(18.dp), color = cs.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(18.dp)).clickable { assigning = c.beat }) {
                                Column(Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(if (c.onTrack) Icons.Outlined.CheckCircle else if (c.actualPage == null) Icons.Outlined.Schedule else Icons.Outlined.WarningAmber, null, tint = color, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(c.beat.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                        Text("p.${c.targetPage}", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                                    }
                                    Text(c.beat.description, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                                    Text(c.verdict, style = MaterialTheme.typography.labelMedium, color = color, modifier = Modifier.padding(top = 6.dp))
                                    if (c.beat.sceneIds.isNotEmpty()) FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        c.beat.sceneIds.forEach { id ->
                                            val sc = scenes.firstOrNull { it.elementId == id }
                                            Pill("${sceneLabel(id)} ${sc?.heading?.let { Analysis.location(it) }.orEmpty()}".trim().take(28), onClick = { onJump(id) })
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item { TextButton(onClick = { onSet(null); picking = true }) { Text("Remove structure", color = cs.error) } }
                }
            }
        }
    }

    if (helping) StructureHelp(onDismiss = { helping = false })

    assigning?.let { beat ->
        var chosen by remember(beat.id) { mutableStateOf(beat.sceneIds.toSet()) }
        AlertDialog(
            onDismissRequest = { assigning = null },
            title = { Text(beat.name) },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(scenes, key = { it.elementId }) { sc ->
                        Row(Modifier.fillMaxWidth().clickable { chosen = if (sc.elementId in chosen) chosen - sc.elementId else chosen + sc.elementId }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(sc.elementId in chosen, { chosen = if (it) chosen + sc.elementId else chosen - sc.elementId })
                            Text(sceneLabel(sc.elementId), style = MaterialTheme.typography.labelLarge, color = cs.primary, modifier = Modifier.width(36.dp))
                            Column {
                                Text(sc.heading.ifBlank { "UNTITLED SCENE" }, style = MaterialTheme.typography.bodySmall.copy(fontFamily = ScriptFont), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("p.${pageOfScene(sc.elementId) ?: "-"}", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val s = script.extras.structure ?: return@TextButton
                    onSet(s.copy(beats = s.beats.map { if (it.id == beat.id) it.copy(sceneIds = scenes.map { sc -> sc.elementId }.filter { id -> id in chosen }) else it }))
                    assigning = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { assigning = null }) { Text("Cancel") } },
        )
    }
}

/** "More": how to use story structure and beats, in plain words. */
@Composable
private fun StructureHelp(onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    @Composable fun step(n: Int, title: String, body: String) {
        Row(Modifier.padding(vertical = 6.dp)) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(cs.primary.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Text("$n", style = MaterialTheme.typography.labelMedium, color = cs.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(body, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How story structure works") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("A structure is a map of the big turning points every story passes through — the moment things change, the midpoint, the lowest point, the ending. Page One shows you where each one should fall and where yours actually does.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                step(1, "Pick a shape", "Short film for 5–15 pages, Save the Cat or Three-act for features, the TV templates for episodes. You can change it any time — scenes you've placed stay on beats with the same name.")
                step(2, "Set the length", "Choose the target length (12, 30, 90, 110 pages…). Each beat then shows the page it should land on, like “Catalyst · p.12”.")
                step(3, "Place your scenes", "Tap a beat and tick the scene (or scenes) where that moment happens. You can also do it from an index card: open the card › Story › Beat.")
                step(4, "Read the check", "Green: the beat lands close to its target page. Amber: it comes early or late — e.g. “Catalyst should land by p.12 — yours is on p.19”, a sign the opening may be slow. Grey: not placed yet.")
                step(5, "Use the strip", "The bar at the top runs from page 1 to the end. Thin marks are where beats should fall, dots are where yours are, and the gap between them is how far off you are.")
                Spacer(Modifier.height(8.dp))
                Text("Use it to outline before you write (place index cards on beats), or after a draft to spot pacing problems. It's a guide, not a rule — plenty of great films break it on purpose.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } },
    )
}

/** A strip from page 1 to the end: act breaks, where beats should land (ticks) and where yours are (dots). */
@Composable
fun BeatMap(targets: List<Float>, actuals: List<Float?>, acts: List<Float>) {
    val cs = MaterialTheme.colorScheme
    val brand = LocalBrand.current
    Canvas(Modifier.fillMaxWidth().height(34.dp)) {
        val h = size.height
        val y = h * 0.55f
        drawRoundRect(cs.surfaceContainerHighest, Offset(0f, y - 4.dp.toPx()), Size(size.width, 8.dp.toPx()), CornerRadius(4.dp.toPx()))
        acts.forEach { a -> drawRect(cs.outline, Offset(size.width * a - 1, 0f), Size(2.dp.toPx(), h)) }
        targets.forEach { t -> drawRect(cs.onSurfaceVariant.copy(alpha = 0.6f), Offset(size.width * t.coerceIn(0f, 1f) - 1, y - 9.dp.toPx()), Size(2.dp.toPx(), 18.dp.toPx())) }
        actuals.forEachIndexed { i, a ->
            if (a == null) return@forEachIndexed
            val t = targets.getOrNull(i) ?: return@forEachIndexed
            val c = if (kotlin.math.abs(a - t) < 0.05f) brand.success else brand.warning
            drawLine(c.copy(alpha = 0.5f), Offset(size.width * t, y), Offset(size.width * a.coerceIn(0f, 1f), y), 2.dp.toPx())
            drawCircle(c, 5.dp.toPx(), Offset(size.width * a.coerceIn(0f, 1f), y))
        }
    }
}

val StoryBorder = Modifier.border(0.dp, Color.Transparent)
