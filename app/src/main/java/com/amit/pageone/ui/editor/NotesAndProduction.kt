package com.amit.pageone.ui.editor

import android.app.DatePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.AssistChip
import com.amit.pageone.core.Attachment
import com.amit.pageone.core.AttachmentKind
import com.amit.pageone.ui.components.AudioPlayerRow
import com.amit.pageone.ui.components.ImageViewer
import com.amit.pageone.ui.components.VoiceRecorder
import com.amit.pageone.ui.components.VideoTile
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Link
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.PersonPin
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Theaters
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amit.pageone.core.Analysis
import com.amit.pageone.core.Comment
import com.amit.pageone.core.Deadline
import com.amit.pageone.core.Pagination
import com.amit.pageone.core.Planner
import com.amit.pageone.core.Production
import com.amit.pageone.core.Script
import com.amit.pageone.core.rangeIn
import com.amit.pageone.data.AppGraph
import com.amit.pageone.data.AppSettings
import com.amit.pageone.io.Exports
import com.amit.pageone.io.PdfExporter
import com.amit.pageone.io.ReportPdf
import com.amit.pageone.ui.components.EmptyNote
import com.amit.pageone.ui.components.GlassCard
import com.amit.pageone.ui.components.HeroNumber
import com.amit.pageone.ui.components.IconTile
import com.amit.pageone.ui.components.Meter
import com.amit.pageone.ui.components.Pill
import com.amit.pageone.ui.components.SectionHeader
import com.amit.pageone.ui.theme.LocalBrand
import com.amit.pageone.ui.theme.ScriptFont
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.time.LocalDate

// ====================================================================== anchored notes

@Composable
fun AddNoteDialog(quote: String, onDismiss: () -> Unit, onAdd: (String, List<Attachment>) -> Unit) {
    var text by remember { mutableStateOf("") }
    var attachments by remember { mutableStateOf(listOf<Attachment>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pin a note") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                    Text("“${quote.take(160)}”", style = MaterialTheme.typography.bodySmall.copy(fontFamily = ScriptFont), modifier = Modifier.padding(10.dp), maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
                OutlinedTextField(text, { text = it }, placeholder = { Text("What should change here — or how should it look and sound?") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                AttachmentEditor(attachments, onChange = { attachments = it })
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(text, attachments) }, enabled = text.isNotBlank() || attachments.isNotEmpty()) { Text("Pin note") } },
        dismissButton = { TextButton(onClick = { attachments.filter { it.name == "Voice memo" }.forEach { AppGraph.media.deleteOwn(it.file) }; onDismiss() }) { Text("Cancel") } },
    )
}

/**
 * Add a reference picture, music / audio, a video, or record a voice memo. Picked files stay where
 * they are in the writer's own storage (Page One keeps permission to read them); recordings are
 * saved to the phone's Recordings › Page One folder.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AttachmentEditor(attachments: List<Attachment>, onChange: (List<Attachment>) -> Unit) {
    var recording by remember { mutableStateOf(false) }
    var addingLink by remember { mutableStateOf(false) }
    fun picked(kind: AttachmentKind, fallback: String) = { uri: android.net.Uri? ->
        if (uri != null) {
            val ref = AppGraph.media.keep(uri)
            val name = AppGraph.media.displayName(ref).ifBlank { fallback }
            val dur = if (kind == AttachmentKind.IMAGE) 0L else AppGraph.media.durationOf(ref)
            onChange(attachments + Attachment(ref, kind, name, dur))
        }
        Unit
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), picked(AttachmentKind.IMAGE, "Picture"))
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), picked(AttachmentKind.AUDIO, "Audio"))
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), picked(AttachmentKind.VIDEO, "Video"))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AssistChip(onClick = { pickImage.launch(arrayOf("image/*")) }, label = { Text("Picture") }, leadingIcon = { Icon(Icons.Outlined.Image, null, Modifier.size(18.dp)) })
            AssistChip(onClick = { pickAudio.launch(arrayOf("audio/*")) }, label = { Text("Music / audio") }, leadingIcon = { Icon(Icons.Outlined.MusicNote, null, Modifier.size(18.dp)) })
            AssistChip(onClick = { pickVideo.launch(arrayOf("video/*")) }, label = { Text("Video") }, leadingIcon = { Icon(Icons.Outlined.Movie, null, Modifier.size(18.dp)) })
            AssistChip(onClick = { recording = !recording }, label = { Text(if (recording) "Cancel memo" else "Voice memo") }, leadingIcon = { Icon(Icons.Outlined.Mic, null, Modifier.size(18.dp)) })
            AssistChip(onClick = { addingLink = true }, label = { Text("Link") }, leadingIcon = { Icon(Icons.Outlined.Link, null, Modifier.size(18.dp)) })
        }
        if (addingLink) LinkDialog(onDismiss = { addingLink = false }, onAdd = { url, title -> addingLink = false; onChange(attachments + Attachment(url, AttachmentKind.LINK, title)) })
        if (recording) VoiceRecorder(onRecorded = { f, d -> recording = false; onChange(attachments + Attachment(f, AttachmentKind.AUDIO, "Voice memo", d)) }, autoStart = true)
        AttachmentList(attachments, onRemove = { a -> if (a.name == "Voice memo") AppGraph.media.deleteOwn(a.file); onChange(attachments - a) })
    }
}
/** Add a web link (a reference film on YouTube, a Pinterest board, a song…). */
@Composable
private fun LinkDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var url by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    val clean = url.trim().let { if (it.isEmpty() || it.startsWith("http://") || it.startsWith("https://")) it else "https://$it" }
    val valid = android.util.Patterns.WEB_URL.matcher(clean).matches()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a link") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(url, { url = it }, label = { Text("Link") }, placeholder = { Text("youtube.com/watch?v=…") }, singleLine = true, isError = url.isNotBlank() && !valid,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(title, { title = it }, label = { Text("Title (optional)") }, placeholder = { Text("The lighting I want") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(clean, title.trim()) }, enabled = valid) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Shows attachments: picture thumbnails (tap for full screen) and audio players. */
@Composable
fun AttachmentList(attachments: List<Attachment>, onRemove: ((Attachment) -> Unit)? = null) {
    val images = attachments.filter { it.kind == AttachmentKind.IMAGE }
    var viewing by remember { mutableStateOf<Int?>(null) }
    viewing?.let { i -> ImageViewer(images.map { it.file }, i) { viewing = null } }
    if (images.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        images.forEachIndexed { i, a ->
            Box {
                MediaImage(a.file, Modifier.size(width = 132.dp, height = 84.dp).clip(RoundedCornerShape(12.dp)).clickable { viewing = i }, 400)
                if (onRemove != null) IconButton(onClick = { onRemove(a) }, modifier = Modifier.align(Alignment.TopEnd).size(30.dp)) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(Color(0x99000000)), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Close, "Remove", tint = Color.White, modifier = Modifier.size(14.dp)) }
                }
            }
        }
    }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    attachments.filter { it.kind == AttachmentKind.LINK }.forEach { a ->
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable { runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(a.file))) } }
                .padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Link, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(a.name.ifBlank { android.net.Uri.parse(a.file).host ?: a.file }, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(a.file, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (onRemove != null) IconButton(onClick = { onRemove(a) }) { Icon(Icons.Outlined.Close, "Remove") } else Spacer(Modifier.width(12.dp))
        }
    }
    attachments.filter { it.kind == AttachmentKind.VIDEO }.forEach { a ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            VideoTile(a.file, a.name.ifBlank { "Video" }, Modifier.weight(1f).height(150.dp))
            if (onRemove != null) IconButton(onClick = { onRemove(a) }) { Icon(Icons.Outlined.Close, "Remove") }
        }
    }
    attachments.filter { it.kind == AttachmentKind.AUDIO }.forEach { a ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            AudioPlayerRow(a.file, a.name.ifBlank { "Audio" }, a.durationMs, Modifier.weight(1f))
            if (onRemove != null) IconButton(onClick = { onRemove(a) }) { Icon(Icons.Outlined.Close, "Remove") }
        }
    }
}

/** All notes pinned to the script — open and resolved — with replies. */
@Composable
fun CommentsSheet(
    script: Script,
    onlyElement: Long?,
    onDismiss: () -> Unit,
    onJump: (Long) -> Unit,
    onReply: (String, String) -> Unit,
    onResolve: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onAttach: ((String, Attachment) -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    var showResolved by remember { mutableStateOf(false) }
    var filterElement by remember { mutableStateOf(onlyElement) }
    val byId = remember(script.elements) { script.elements.associateBy { it.id } }
    val all = script.extras.comments.sortedBy { c -> script.elements.indexOfFirst { it.id == c.elementId }.let { if (it < 0) Int.MAX_VALUE else it } }
    val shown = all.filter { (showResolved || !it.resolved) && (filterElement == null || it.elementId == filterElement) }
    AppSheet(onDismiss) {
        Column {
            SheetTitle("Notes", "${all.count { !it.resolved }} open · ${all.count { it.resolved }} resolved · select text and tap the note button to pin one")
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!showResolved, { showResolved = false }, { Text("Open") })
                FilterChip(showResolved, { showResolved = true }, { Text("All") })
                if (filterElement != null) FilterChip(true, { filterElement = null }, { Text("This line only ✕") })
            }
            if (shown.isEmpty()) EmptyNote(Icons.Outlined.CheckCircle, "No open notes", "Select any words in the script, then tap the note button in the toolbar.")
            LazyColumn(Modifier.heightIn(max = 600.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                items(shown, key = { it.id }) { c -> NoteCard(c, byId[c.elementId]?.text, onJump, onReply, onResolve, onDelete, onAttach) }
            }
        }
    }
}

@Composable
private fun NoteCard(c: Comment, elementText: String?, onJump: (Long) -> Unit, onReply: (String, String) -> Unit, onResolve: (String, Boolean) -> Unit, onDelete: (String) -> Unit, onAttach: ((String, Attachment) -> Unit)? = null) {
    val cs = MaterialTheme.colorScheme
    var reply by remember { mutableStateOf("") }
    val detached = elementText == null || c.rangeIn(elementText) == null
    Surface(shape = RoundedCornerShape(18.dp), color = cs.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(Color(c.color).copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                    Text(c.author.take(1).uppercase(), style = MaterialTheme.typography.labelMedium, color = Color(c.color))
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.author, style = MaterialTheme.typography.labelLarge)
                    Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(java.util.Date(c.at)), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }
                IconButton(onClick = { onResolve(c.id, !c.resolved) }) { Icon(if (c.resolved) Icons.Outlined.Undo else Icons.Outlined.CheckCircle, if (c.resolved) "Reopen" else "Resolve", tint = if (c.resolved) cs.onSurfaceVariant else LocalBrand.current.success) }
                IconButton(onClick = { onDelete(c.id) }) { Icon(Icons.Outlined.Delete, "Delete note", tint = cs.onSurfaceVariant) }
            }
            Box(Modifier.padding(vertical = 8.dp).clip(RoundedCornerShape(10.dp)).background(Color(c.color).copy(alpha = 0.10f)).clickable(enabled = elementText != null) { onJump(c.elementId) }.padding(10.dp)) {
                Text(
                    (if (detached) "Text changed since — was: " else "") + "“${c.quote.take(200)}”",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = ScriptFont, fontStyle = if (detached) FontStyle.Italic else FontStyle.Normal),
                    maxLines = 4, overflow = TextOverflow.Ellipsis,
                )
            }
            if (c.text.isNotBlank()) Text(c.text, style = MaterialTheme.typography.bodyMedium)
            if (c.attachments.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                val missing = c.attachments.filter { it.kind != AttachmentKind.LINK && !AppGraph.media.exists(it.file) }
                AttachmentList(c.attachments - missing.toSet())
                if (missing.isNotEmpty()) Text("${missing.size} attachment${if (missing.size == 1) "" else "s"} on ${c.author}'s phone", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
            if (!c.resolved && onAttach != null) {
                var adding by remember { mutableStateOf(false) }
                if (adding) AttachmentEditor(emptyList(), onChange = { list -> list.forEach { onAttach(c.id, it) }; adding = false })
                else TextButton(onClick = { adding = true }) { Icon(Icons.Outlined.AttachFile, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Attach picture, audio, video or link") }
            }
            c.replies.forEach { r ->
                Row(Modifier.padding(top = 8.dp, start = 12.dp)) {
                    Box(Modifier.padding(top = 4.dp).size(8.dp).clip(CircleShape).background(Color(r.color)))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(r.author, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                        Text(r.text, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (!c.resolved) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                OutlinedTextField(reply, { reply = it }, placeholder = { Text("Reply") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f))
                IconButton(onClick = { if (reply.isNotBlank()) { onReply(c.id, reply); reply = "" } }) { Icon(Icons.AutoMirrored.Outlined.Send, "Send reply") }
            }
        }
    }
}

// ====================================================================== production

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProductionSheet(
    script: Script,
    settings: AppSettings,
    pagination: Pagination?,
    onDismiss: () -> Unit,
    onLock: () -> Unit,
    onUnlock: () -> Unit,
    onDeadline: (Deadline?) -> Unit,
    onMessage: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val brand = LocalBrand.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    var confirmLock by remember { mutableStateOf(false) }
    var sidesFor by remember { mutableStateOf(setOf<String>()) }
    val pages = pagination?.pageCount ?: 1
    val xs = remember(script.elements, pagination, script.extras.lock) {
        pagination?.let { Production.xray(script, it, settings.paper.linesPerPage) } ?: emptyList()
    }

    fun share(name: String, mime: String, subject: String, w: (java.io.OutputStream) -> Unit) = scope.launch {
        runCatching { Exports.shareFile(ctx, name, mime, subject, w) }.onFailure { onMessage("Couldn't share: ${it.message}") }
    }

    AppSheet(onDismiss) {
        Column(Modifier.heightIn(min = 460.dp)) {
            SheetTitle("Production", "Deadline, production lock, reports and sides")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                listOf("Deadline", "Lock", "Reports", "Sides").forEachIndexed { i, t -> SegmentedButton(tab == i, { tab = i }, SegmentedButtonDefaults.itemShape(i, 4)) { Text(t, maxLines = 1) } }
            }
            Column(Modifier.heightIn(max = 620.dp).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp)) {
                when (tab) {
                    0 -> DeadlinePanel(script.extras.deadline, pages, onDeadline)
                    1 -> {
                        val lock = script.extras.lock
                        GlassCard(Modifier.fillMaxWidth(), wash = lock.locked) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconTile(if (lock.locked) Icons.Outlined.Lock else Icons.Outlined.LockOpen, if (lock.locked) brand.accent else cs.onSurfaceVariant)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(if (lock.locked) "Locked" else "Unlocked", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        if (lock.locked) "Since ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(java.util.Date(lock.lockedAt))} · ${lock.sceneNumbers.size} scenes · ${lock.pageStarts.size + 1} pages"
                                        else "Scenes and pages renumber as you write",
                                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Once a script goes to production, everyone works from the same numbers. Locking freezes scene numbers and page breaks: " +
                                    "new scenes become 12A, 12B; overflowing pages become 34A; deleted scenes stay as OMITTED. Pair it with revision colours.",
                                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            if (lock.locked) OutlinedButton(onClick = onUnlock, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.LockOpen, null); Spacer(Modifier.width(8.dp)); Text("Unlock") }
                            else Button(onClick = { confirmLock = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Lock, null); Spacer(Modifier.width(8.dp)); Text("Lock scenes & pages") }
                        }
                        if (lock.locked && pagination != null) {
                            val added = xs.count { it.label.any(Char::isLetter) }
                            val aPages = pagination.labels.count { l -> l.any(Char::isLetter) }
                            val omitted = script.elements.count { it.type == com.amit.pageone.core.ElementType.SCENE_HEADING && it.text.trim() == "OMITTED" }
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Pill("$added A-scenes"); Pill("$aPages A-pages", cs.tertiary); Pill("$omitted omitted", cs.onSurfaceVariant)
                            }
                        }
                    }
                    2 -> {
                        val name = Exports.baseName(script)
                        ReportRow(Icons.Outlined.Theaters, "Scene report", "Every scene: number, page, INT/EXT, location, time, length in eighths, cast, storylines") {
                            share("$name - Scenes.pdf", "application/pdf", "Scene report") { out ->
                                ReportPdf.write(out, "Scene report — ${script.title}", "${xs.size} scenes · $pages pages",
                                    listOf(ReportPdf.Column("Sc", 0.5f), ReportPdf.Column("Pg", 0.5f), ReportPdf.Column("I/E", 0.6f), ReportPdf.Column("Location", 2.2f), ReportPdf.Column("Time", 1f), ReportPdf.Column("Length", 0.7f), ReportPdf.Column("Cast", 2.6f), ReportPdf.Column("Synopsis", 3f)),
                                    xs.map { listOf(it.label, "${it.page}", it.intExt, it.location, it.time, Production.formatEighths(it.eighths), (it.speaking + it.mentioned).joinToString(", "), it.synopsis) })
                            }
                        }
                        ReportRow(Icons.Outlined.PersonPin, "Character report", "Speeches, words, scenes, pages and first/last appearance per character") {
                            val rows = Production.characters(script, xs)
                            share("$name - Characters.pdf", "application/pdf", "Character report") { out ->
                                ReportPdf.write(out, "Character report — ${script.title}", "${rows.size} characters",
                                    listOf(ReportPdf.Column("Character", 1.6f), ReportPdf.Column("Speeches", 0.8f), ReportPdf.Column("Words", 0.8f), ReportPdf.Column("Scenes", 0.7f), ReportPdf.Column("Pages", 0.7f), ReportPdf.Column("First", 0.6f), ReportPdf.Column("Last", 0.6f), ReportPdf.Column("Scene list", 4f)),
                                    rows.map { listOf(it.name, "${it.lines}", "${it.words}", "${it.scenes.size}", Production.formatEighths(it.eighths), "${it.first}", "${it.last}", it.scenes.joinToString(" ")) })
                            }
                        }
                        ReportRow(Icons.Outlined.Place, "Location report", "Each location's scenes, page count, INT/EXT and times of day") {
                            val rows = Production.locations(xs)
                            share("$name - Locations.pdf", "application/pdf", "Location report") { out ->
                                ReportPdf.write(out, "Location report — ${script.title}", "${rows.size} locations",
                                    listOf(ReportPdf.Column("Location", 2.4f), ReportPdf.Column("Scenes", 0.7f), ReportPdf.Column("Pages", 0.7f), ReportPdf.Column("I/E", 0.8f), ReportPdf.Column("Times", 1.6f), ReportPdf.Column("Scene list", 3.5f)),
                                    rows.map { listOf(it.name, "${it.scenes.size}", Production.formatEighths(it.eighths), it.intExt.joinToString("/"), it.times.joinToString(", "), it.scenes.joinToString(" ")) })
                            }
                        }
                        ReportRow(Icons.Outlined.TableChart, "Breakdown sheets", "Cast, extras, props, wardrobe, vehicles, effects… per scene (from each card's Breakdown tab)") {
                            share("$name - Breakdown.csv", "text/csv", "Breakdown") { it.write(Production.breakdownCsv(script, xs).toByteArray()) }
                        }
                        Spacer(Modifier.height(8.dp))
                        SectionHeader("Spreadsheets (CSV)")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { share("$name - Scenes.csv", "text/csv", "Scenes") { it.write(Production.scenesCsv(script, xs).toByteArray()) } }) { Text("Scenes") }
                            OutlinedButton(onClick = { share("$name - Characters.csv", "text/csv", "Characters") { it.write(Production.charactersCsv(Production.characters(script, xs)).toByteArray()) } }) { Text("Characters") }
                            OutlinedButton(onClick = { share("$name - Locations.csv", "text/csv", "Locations") { it.write(Production.locationsCsv(Production.locations(xs)).toByteArray()) } }) { Text("Locations") }
                        }
                    }
                    3 -> {
                        val names = remember(script.elements) { Analysis.characterNames(script.elements) }
                        Text("Sides are the pages an actor needs for an audition or a shoot day: only the scenes their character is in, with the script's scene numbers.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            names.forEach { n -> FilterChip(n in sidesFor, { sidesFor = if (n in sidesFor) sidesFor - n else sidesFor + n }, { Text(n) }, leadingIcon = { Avatar(n, 20) }) }
                        }
                        Spacer(Modifier.height(12.dp))
                        val preview = remember(sidesFor, script.elements) { if (sidesFor.isEmpty()) 0 else Analysis.scenes(Production.sides(script, sidesFor).elements).size }
                        Button(onClick = {
                            val sides = Production.sides(script, sidesFor)
                            val labels = com.amit.pageone.core.Locking.sceneLabels(script.elements, script.extras.lock)
                            val who = sidesFor.joinToString(" & ")
                            share("${Exports.baseName(script)} - Sides - $who.pdf", "application/pdf", "Sides for $who") { out ->
                                PdfExporter.export(ctx, sides, settings, out, PdfExporter.Options(header = "SIDES — $who", sceneLabels = labels, titlePage = false, ignoreLock = true))
                            }
                        }, enabled = sidesFor.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.Description, null); Spacer(Modifier.width(8.dp)); Text(if (sidesFor.isEmpty()) "Pick characters" else "Make sides · $preview scenes")
                        }
                    }
                }
            }
        }
    }
    if (confirmLock) AlertDialog(
        onDismissRequest = { confirmLock = false },
        title = { Text("Lock scenes and pages?") },
        text = { Text("Scene numbers and page breaks will stay exactly as they are now. You can keep writing — new material gets A-numbers. Unlock any time.") },
        confirmButton = { TextButton(onClick = { confirmLock = false; onLock() }) { Text("Lock") } },
        dismissButton = { TextButton(onClick = { confirmLock = false }) { Text("Cancel") } },
    )
}

@Composable
private fun ReportRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String, onShare: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(18.dp), color = cs.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onShare)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            Text("Share", style = MaterialTheme.typography.labelLarge, color = cs.primary)
        }
    }
}

/** Set a finish-by date and length; see pages/day needed and whether you're ahead or behind. */
@Composable
fun DeadlinePanel(d: Deadline?, pages: Int, onSet: (Deadline?) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val brand = LocalBrand.current
    val ctx = LocalContext.current
    var label by remember(d) { mutableStateOf(d?.label ?: "First draft") }
    var target by remember(d) { mutableStateOf(d?.targetPages ?: 110) }
    fun pickDate(then: (String) -> Unit) {
        val start = d?.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now().plusWeeks(6)
        DatePickerDialog(ctx, { _, y, m, day -> then(LocalDate.of(y, m + 1, day).toString()) }, start.year, start.monthValue - 1, start.dayOfMonth).apply {
            datePicker.minDate = System.currentTimeMillis()
        }.show()
    }
    if (d == null) {
        GlassCard(Modifier.fillMaxWidth(), wash = true) {
            IconTile(Icons.Outlined.CalendarMonth)
            Spacer(Modifier.height(10.dp))
            Text("Give this script a deadline", style = MaterialTheme.typography.titleMedium)
            Text("Pick a finish date and a length. Page One works out how many pages a day you need, tells you if you're falling behind, and reminds you every day.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(12, 30, 60, 90, 110, 120).forEach { n -> FilterChip(target == n, { target = n }, { Text("$n pp") }) } }
            Spacer(Modifier.height(10.dp))
            Button(onClick = { pickDate { date -> onSet(Deadline(date, target, LocalDate.now().toString(), pages, label)) } }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.CalendarMonth, null); Spacer(Modifier.width(8.dp)); Text("Choose finish date")
            }
        }
        return
    }
    val plan = remember(d, pages) { Planner.plan(d, pages) }
    GlassCard(Modifier.fillMaxWidth(), wash = true) {
        Text(d.label.uppercase(), style = MaterialTheme.typography.labelMedium, color = brand.accent)
        Row(verticalAlignment = Alignment.Bottom) {
            HeroNumber(if (plan.done) "Done" else if (plan.overdue) "${-plan.daysLeft}d late" else "${plan.daysLeft}")
            if (!plan.done && !plan.overdue) Text("  days to go", style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
        }
        Text("Due ${runCatching { DateFormat.getDateInstance(DateFormat.LONG).format(java.sql.Date.valueOf(d.date)) }.getOrDefault(d.date)}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Meter(pages / d.targetPages.toFloat().coerceAtLeast(1f), height = 10.dp)
        Spacer(Modifier.height(6.dp))
        Row {
            Text("$pages of ${d.targetPages} pages", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text("plan: %.0f by today".format(plan.expectedNow), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        Text(plan.status, style = MaterialTheme.typography.bodyMedium, color = when { plan.done -> brand.success; plan.behind > 0.5f || plan.overdue -> brand.warning; else -> brand.success })
    }
    Spacer(Modifier.height(14.dp))
    OutlinedTextField(label, { label = it }, label = { Text("What's due") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(12, 30, 60, 90, 110, 120).forEach { n -> FilterChip(target == n, { target = n; onSet(d.copy(targetPages = n, label = label)) }, { Text("$n") }) } }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { pickDate { date -> onSet(d.copy(date = date, label = label)) } }) { Icon(Icons.Outlined.CalendarMonth, null); Spacer(Modifier.width(6.dp)); Text("Change date") }
        if (label != d.label) Button(onClick = { onSet(d.copy(label = label)) }) { Text("Save") }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { onSet(null) }) { Text("Clear", color = cs.error) }
    }
    Spacer(Modifier.height(8.dp))
    Text("Daily deadline reminders are on in Settings → Notifications.", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
}
