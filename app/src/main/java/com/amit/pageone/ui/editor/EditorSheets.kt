package com.amit.pageone.ui.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amit.pageone.core.Analysis
import com.amit.pageone.core.RevisionColor
import com.amit.pageone.core.Script
import com.amit.pageone.core.TitlePage
import com.amit.pageone.data.AppGraph
import com.amit.pageone.data.AppSettings
import com.amit.pageone.data.VersionInfo
import com.amit.pageone.io.ExportFormat
import com.amit.pageone.io.Exports
import com.amit.pageone.ui.theme.ScriptFont
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Box(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) { content() }
    }
}

@Composable
fun SheetTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ------------------------------------------------------------------ export

@Composable
fun ExportSheet(
    script: Script, settings: AppSettings, pages: Int, onDismiss: () -> Unit, onMessage: (String) -> Unit,
    /** Script Doctor problems + consistency notes, shown as a nudge before sending. */
    doctorCount: Int = 0, onDoctor: (() -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var format by remember { mutableStateOf(ExportFormat.PDF) }
    var busy by remember { mutableStateOf(false) }
    val openNotes = script.extras.comments.count { !it.resolved }
    var withNotes by remember(openNotes > 0) { mutableStateOf(openNotes > 0) }
    var progress by remember { mutableStateOf<String?>(null) }

    fun pdfOptions(f: ExportFormat): com.amit.pageone.io.PdfExporter.Options {
        if (f != ExportFormat.PDF || !withNotes) return com.amit.pageone.io.PdfExporter.Options()
        progress = "Building PDF…"
        return com.amit.pageone.io.PdfExporter.Options(notes = true)
    }

    fun saveLauncherResult(f: ExportFormat) = { uri: android.net.Uri? ->
        if (uri != null) scope.launch {
            busy = true
            runCatching { Exports.saveTo(ctx, uri, script, settings, f, pdfOptions(f)) }
                .onSuccess { onMessage("Saved ${Exports.fileName(script, f)}") }
                .onFailure { onMessage("Export failed: ${it.message}") }
            busy = false; progress = null
            onDismiss()
        }
        Unit
    }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf"), saveLauncherResult(ExportFormat.PDF))
    val fdxLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream"), saveLauncherResult(ExportFormat.FDX))
    val ftnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain"), saveLauncherResult(ExportFormat.FOUNTAIN))

    // "Keep a synced copy": pick a place once (Drive, Dropbox, OneDrive…), updated on every save.
    val links by AppGraph.links.links.collectAsState()
    val myLinks = links.filter { it.scriptId == script.id }
    fun linkResult(f: ExportFormat) = { uri: android.net.Uri? ->
        if (uri != null) scope.launch {
            runCatching { AppGraph.links.add(script, uri, f) }
                .onSuccess { onMessage("${f.label} copy will stay up to date") }
                .onFailure { onMessage("Couldn't link: ${it.message}") }
        }
        Unit
    }
    val pdfLink = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf"), linkResult(ExportFormat.PDF))
    val fdxLink = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream"), linkResult(ExportFormat.FDX))
    val ftnLink = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain"), linkResult(ExportFormat.FOUNTAIN))

    AppSheet(onDismiss) {
        Column {
            SheetTitle("Export", "$pages pages · ${settings.paper.label}")
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormatOption(Icons.Outlined.PictureAsPdf, "PDF", "Print-ready, industry format", format == ExportFormat.PDF) { format = ExportFormat.PDF }
                FormatOption(Icons.Outlined.Description, "Final Draft (.fdx)", "Open in Final Draft, WriterDuet, Fade In", format == ExportFormat.FDX) { format = ExportFormat.FDX }
                FormatOption(Icons.Outlined.Code, "Fountain", "Plain-text screenplay markup", format == ExportFormat.FOUNTAIN) { format = ExportFormat.FOUNTAIN }
            }
            if (format == ExportFormat.PDF) {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 10.dp)) {
                    ToggleRow("Include title page", settings.pdfTitlePage) { v -> AppGraph.settings.update { it.copy(pdfTitlePage = v) } }
                    ToggleRow("Revision asterisks", settings.pdfRevisionMarks) { v -> AppGraph.settings.update { it.copy(pdfRevisionMarks = v) } }
                    ToggleRow(
                        "Include notes & references", withNotes,
                        if (openNotes == 0) "No open notes in this script"
                        else "$openNotes note${if (openNotes == 1) "" else "s"} colour-marked in the text, with a dot in the margin",
                    ) { v -> withNotes = v && openNotes > 0 }
                    Text(
                        "Scene numbers: ${settings.pdfSceneNumbers.label} · change more in Settings",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (doctorCount > 0 && onDoctor != null) {
                val cs = MaterialTheme.colorScheme
                Surface(
                    shape = RoundedCornerShape(14.dp), color = cs.tertiaryContainer,
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onDoctor),
                ) {
                    Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.HealthAndSafety, null, tint = cs.onTertiaryContainer, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Script Doctor has ${doctorCount} note${if (doctorCount == 1) "" else "s"} to check before you send this.",
                            style = MaterialTheme.typography.bodySmall, color = cs.onTertiaryContainer, modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onDoctor) { Text("Review") }
                    }
                }
            }
            if (busy) Column(Modifier.padding(horizontal = 24.dp)) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                progress?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)) }
            }
            Row(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        val name = Exports.fileName(script, format)
                        when (format) {
                            ExportFormat.PDF -> pdfLauncher.launch(name)
                            ExportFormat.FDX -> fdxLauncher.launch(name)
                            ExportFormat.FOUNTAIN -> ftnLauncher.launch(name)
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Icon(Icons.Outlined.SaveAlt, null); Spacer(Modifier.width(8.dp)); Text("Save to…") }
                Button(
                    onClick = {
                        scope.launch {
                            busy = true
                            runCatching { Exports.share(ctx, script, settings, format, pdfOptions(format)) }.onFailure { onMessage("Share failed: ${it.message}") }
                            busy = false; progress = null
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Icon(Icons.Outlined.Share, null); Spacer(Modifier.width(8.dp)); Text("Share") }
            }
            HorizontalDivider(Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Keep a synced copy", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Choose a folder in Google Drive, Dropbox, OneDrive or any drive app — this ${format.label} is rewritten there every time you save.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                myLinks.forEach { l ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CloudSync, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("${l.format.label} in ${l.label}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { AppGraph.links.remove(l) }) { Text("Stop") }
                    }
                }
                OutlinedButton(onClick = {
                    val name = Exports.fileName(script, format)
                    when (format) {
                        ExportFormat.PDF -> pdfLink.launch(name)
                        ExportFormat.FDX -> fdxLink.launch(name)
                        ExportFormat.FOUNTAIN -> ftnLink.launch(name)
                    }
                }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.CloudSync, null); Spacer(Modifier.width(8.dp)); Text("Keep a synced ${format.label} copy…") }
            }
        }
    }
}

@Composable
private fun FormatOption(icon: ImageVector, title: String, sub: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) cs.primaryContainer else cs.surfaceContainerHigh,
        border = if (selected) BorderStroke(1.5.dp, cs.primary) else null,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (selected) cs.primary else cs.onSurfaceVariant)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            if (selected) Icon(Icons.Filled.Check, null, tint = cs.primary)
        }
    }
}

@Composable
fun ToggleRow(label: String, value: Boolean, sub: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = value, onCheckedChange = onChange)
    }
}

// ------------------------------------------------------------------ revisions

@Composable
fun RevisionsSheet(script: Script, elementPage: IntArray?, coloredPages: Boolean, onDismiss: () -> Unit, onSet: (Boolean, Int) -> Unit, onClear: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val marked = remember(script.elements) { script.elements.count { it.revision > 0 } }
    // Each revision set: how many lines carry it and which pages it touches.
    val sets = remember(script.elements, elementPage, script.revisionDates) {
        val byLevel = sortedMapOf<Int, MutableSet<Int>>()
        val counts = HashMap<Int, Int>()
        script.elements.forEachIndexed { i, e ->
            if (e.revision > 0) {
                counts[e.revision] = (counts[e.revision] ?: 0) + 1
                byLevel.getOrPut(e.revision) { sortedSetOf() }.add((elementPage?.getOrNull(i) ?: 0) + 1)
            }
        }
        (byLevel.keys + script.revisionDates.keys).toSortedSet().map { lv -> Triple(lv, counts[lv] ?: 0, byLevel[lv].orEmpty().toList()) }
    }
    AppSheet(onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SheetTitle("Revisions", "Track changes the way productions do — with coloured pages and asterisks.")
            Column(Modifier.padding(horizontal = 24.dp)) {
                ToggleRow("Revision mode", script.revisionMode, "Mark every edit with the current colour") { onSet(it, script.revisionLevel) }
                Spacer(Modifier.height(8.dp))
                Text("Current revision", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                RevisionColor.entries.drop(1).chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                        row.forEach { rc ->
                            val sel = rc.ordinal == script.revisionLevel
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(rc.argb).copy(alpha = if (sel) 0.95f else 0.35f),
                                border = if (sel) BorderStroke(2.dp, cs.onSurface) else null,
                                modifier = Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp)).clickable { onSet(script.revisionMode, rc.ordinal) },
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(rc.label, style = MaterialTheme.typography.labelMedium, color = Color.Black, maxLines = 1)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (sets.isNotEmpty()) {
                    Text("Revision sets", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    sets.forEach { (lv, n, pages) ->
                        val rc = RevisionColor.level(lv)
                        val date = script.revisionDates[lv]?.let { d ->
                            runCatching {
                                java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault())
                                    .format(java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(d)!!)
                            }.getOrNull()
                        } ?: "not dated"
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)).background(Color(rc.argb)))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("${rc.label} · $date", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    if (n == 0) "No lines yet"
                                    else "$n line${if (n == 1) "" else "s"} · page${if (pages.size == 1) "" else "s"} " + pages.joinToString(", ").let { if (it.length > 60) it.take(57) + "…" else it },
                                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                ToggleRow("Coloured pages in PDF", coloredPages, "Tint each revised page in its revision colour, with a coloured margin edge and asterisks") { v ->
                    com.amit.pageone.data.AppGraph.settings.update { it.copy(pdfColoredPages = v) }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "$marked elements carry revision marks. Each set is dated when you issue it; every revised page's header shows its colour and date (e.g. “Blue Rev. 18/10/26”), and revised lines get an asterisk in the right margin.",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onClear, enabled = marked > 0, modifier = Modifier.fillMaxWidth()) { Text("Clear all revision marks") }
            }
        }
    }
}

// ------------------------------------------------------------------ versions

@Composable
fun VersionsSheet(
    versions: List<VersionInfo>,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onRestore: (VersionInfo) -> Unit,
    onDelete: (VersionInfo) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var name by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf<VersionInfo?>(null) }
    AppSheet(onDismiss) {
        Column {
            SheetTitle("Versions", "Snapshots of your script. Auto-saved periodically, or save one now.")
            Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    placeholder = { Text("e.g. Draft 2 — before notes") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                )
                Spacer(Modifier.width(10.dp))
                FilledTonalButton(onClick = { onSave(name); name = "" }) { Text("Save") }
            }
            Spacer(Modifier.height(10.dp))
            if (versions.isEmpty()) {
                Text("No versions yet.", modifier = Modifier.padding(24.dp), color = cs.onSurfaceVariant)
            }
            LazyColumn(Modifier.height(360.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                items(versions, key = { it.time }) { v ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(v.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(v.time)) +
                                    if (v.pages > 0) " · ${v.pages} pages" else "",
                                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { confirm = v }) { Icon(Icons.Outlined.Restore, "Restore") }
                        IconButton(onClick = { onDelete(v) }) { Icon(Icons.Outlined.Delete, "Delete") }
                    }
                }
            }
        }
    }
    confirm?.let { v ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Restore this version?") },
            text = { Text("Your current script is saved as a version first, so nothing is lost.") },
            confirmButton = { TextButton(onClick = { onRestore(v); confirm = null; onDismiss() }) { Text("Restore") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

// ------------------------------------------------------------------ statistics

@Composable
fun StatsSheet(script: Script, pages: Int, sessionWords: Int, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val st = remember(script.elements) { Analysis.stats(script.elements) }
    val scenes = remember(script.elements) { Analysis.scenes(script.elements) }
    val today by AppGraph.goals.todayWords.collectAsState()
    AppSheet(onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Text("Statistics", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Pages", "$pages", "~$pages min", Modifier.weight(1f))
                StatTile("Scenes", "${st.scenes}", null, Modifier.weight(1f))
                StatTile("Words", "%,d".format(st.words), null, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Today", "%,d".format(today), "words", Modifier.weight(1f))
                StatTile("Session", "%,d".format(sessionWords), "words", Modifier.weight(1f))
                StatTile("Streak", "${AppGraph.goals.streak()}", "days", Modifier.weight(1f))
            }
            Spacer(Modifier.height(20.dp))
            Text("Writing days", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            val days = remember(today) { AppGraph.goals.history(26 * 7) }
            com.amit.pageone.ui.components.WritingHeatmap(days, AppGraph.settings.state.value.dailyGoal)
            script.extras.deadline?.let { d ->
                val plan = remember(d, pages) { com.amit.pageone.core.Planner.plan(d, pages) }
                Spacer(Modifier.height(14.dp))
                Text(d.label, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                com.amit.pageone.ui.components.Meter(pages / d.targetPages.toFloat().coerceAtLeast(1f))
                Text("$pages of ${d.targetPages} pages · ${plan.status}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }

            Spacer(Modifier.height(20.dp))
            Text("Balance", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Ratio("Dialogue", st.dialogueWords, "Action", st.actionWords, cs.primary, cs.tertiary)
            Spacer(Modifier.height(8.dp))
            Ratio("INT.", st.interior, "EXT.", st.exterior, cs.primary, cs.tertiary)
            Spacer(Modifier.height(8.dp))
            Ratio("Day", st.day, "Night", st.night, cs.primary, cs.tertiary)

            Spacer(Modifier.height(20.dp))
            Text("Characters", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            val maxW = (st.characters.maxOfOrNull { it.words } ?: 1).coerceAtLeast(1)
            st.characters.take(20).forEach { c ->
                Column(Modifier.padding(vertical = 5.dp)) {
                    Row {
                        Text(c.name, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = ScriptFont, fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                        Text("${c.cues} lines · ${c.words} words · ${c.scenes} sc", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(cs.surfaceContainerHighest)) {
                        Box(Modifier.fillMaxWidth(c.words / maxW.toFloat()).height(6.dp).clip(CircleShape).background(cs.primary))
                    }
                }
            }
            if (st.characters.isEmpty()) Text("No characters yet.", color = cs.onSurfaceVariant)

            Spacer(Modifier.height(20.dp))
            Text("Longest scenes", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            scenes.sortedByDescending { it.words }.take(5).forEach { s ->
                Row(Modifier.padding(vertical = 4.dp)) {
                    Text("${s.number}", style = MaterialTheme.typography.labelLarge, color = cs.primary, modifier = Modifier.width(32.dp))
                    Text(s.heading, style = MaterialTheme.typography.bodySmall.copy(fontFamily = ScriptFont), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("${s.words} w", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, sub: String?, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(16.dp), color = cs.surfaceContainerHigh, modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall)
            if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun Ratio(a: String, av: Int, b: String, bv: Int, ca: Color, cb: Color) {
    val total = (av + bv).coerceAtLeast(1)
    val fa = av / total.toFloat()
    Column {
        Row {
            Text("$a ${(fa * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text("$b ${100 - (fa * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)) {
            if (av > 0) Box(Modifier.weight(fa.coerceAtLeast(0.001f)).height(8.dp).background(ca))
            if (bv > 0) Box(Modifier.weight((1 - fa).coerceAtLeast(0.001f)).height(8.dp).background(cb))
            if (av == 0 && bv == 0) Box(Modifier.weight(1f).height(8.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest))
        }
    }
}


// ------------------------------------------------------------------ find bar

@Composable
fun FindBar(
    state: FindState,
    onQuery: (String) -> Unit,
    onMatchCase: (Boolean) -> Unit,
    onReplace: (String) -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onReplaceOne: () -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var showReplace by remember { mutableStateOf(false) }
    // Typing goes straight into the Find box, never into the script behind it.
    val findFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { findFocus.requestFocus() } }
    Surface(color = cs.surfaceContainer) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.query, onValueChange = onQuery, singleLine = true,
                    placeholder = { Text("Find in script") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).focusRequester(findFocus),
                    trailingIcon = {
                        Text(
                            if (state.matches.isEmpty()) (if (state.query.isEmpty()) "" else "0") else "${state.current + 1}/${state.matches.size}",
                            style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(end = 10.dp),
                        )
                    },
                )
                IconButton(onClick = onPrev) { Icon(Icons.Filled.KeyboardArrowUp, "Previous") }
                IconButton(onClick = onNext) { Icon(Icons.Filled.KeyboardArrowDown, "Next") }
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp)).clickable { onMatchCase(!state.matchCase) }
                        .border(1.dp, if (state.matchCase) cs.primary else cs.outlineVariant, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) { Text("Aa", color = if (state.matchCase) cs.primary else cs.onSurfaceVariant, style = MaterialTheme.typography.labelLarge) }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { showReplace = !showReplace }) { Text(if (showReplace) "Hide replace" else "Replace…") }
            }
            if (showReplace) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = state.replace, onValueChange = onReplace, singleLine = true,
                        placeholder = { Text("Replace with") }, shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onReplaceOne) { Text("Replace") }
                    TextButton(onClick = onReplaceAll) { Text("All") }
                }
            }
            HorizontalDivider(Modifier.padding(top = 4.dp), color = cs.outlineVariant)
        }
    }
}
