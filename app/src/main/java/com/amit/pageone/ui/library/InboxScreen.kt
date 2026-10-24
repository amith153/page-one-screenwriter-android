package com.amit.pageone.ui.library

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amit.pageone.core.Element
import com.amit.pageone.core.ElementType
import com.amit.pageone.data.AppGraph
import com.amit.pageone.data.Idea
import com.amit.pageone.data.IdeaKind
import com.amit.pageone.ui.components.EmptyNote
import com.amit.pageone.ui.components.GlassCard
import com.amit.pageone.ui.components.studioGlow
import com.amit.pageone.ui.editor.IdeaRow
import com.amit.pageone.ui.theme.LocalBrand
import com.amit.pageone.widget.Widgets
import kotlinx.coroutines.launch

/** Everything captured from the share sheet, the widget, your voice or typed here — waiting to be used. */
@Composable
fun InboxScreen(onBack: () -> Unit, onOpenScript: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val brand = LocalBrand.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val ideas by AppGraph.ideas.ideas.collectAsState()
    val library by AppGraph.store.library.collectAsState()
    LaunchedEffect(Unit) { AppGraph.ideas.reload(); AppGraph.store.refresh() }
    var text by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(0) } // 0 waiting, 1 used, 2 all
    var kind by remember { mutableStateOf<IdeaKind?>(null) }
    var sending by remember { mutableStateOf<Idea?>(null) }

    var recording by remember { mutableStateOf(false) }
    fun listen() { recording = !recording }

    val shown = ideas.filter {
        (filter == 2 || (filter == 0) == it.usedIn.isEmpty()) && (kind == null || it.kind == kind)
    }

    Column(Modifier.fillMaxSize().studioGlow(brand, cs.background).windowInsetsPadding(WindowInsets.statusBars)) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Column(Modifier.weight(1f)) {
                Text("Idea inbox", style = MaterialTheme.typography.headlineSmall)
                Text("${ideas.count { it.usedIn.isEmpty() }} waiting · share text to “Save idea” from any app", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }
        GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), corner = 22.dp, padding = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(text, { text = it }, placeholder = { Text("A line, a scene, a character…") }, maxLines = 4, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f))
                Spacer(Modifier.width(6.dp))
                FilledTonalIconButton(onClick = ::listen) { Icon(Icons.Outlined.Mic, "Record a voice idea") }
            }
            if (recording) {
                Spacer(Modifier.height(10.dp))
                com.amit.pageone.ui.components.VoiceRecorder(onRecorded = { f, d ->
                    AppGraph.ideas.add(text, source = "voice", audio = f, durationMs = d); text = ""; recording = false; Widgets.refresh(ctx)
                }, autoStart = true)
            }
            if (text.isNotBlank() && !recording) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { AppGraph.ideas.add(text); text = ""; Widgets.refresh(ctx) }) { Text("Save idea") }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Waiting", "Used", "All").forEachIndexed { i, l -> FilterChip(filter == i, { filter = i }, { Text(l) }) }
            Spacer(Modifier.width(6.dp))
            IdeaKind.entries.forEach { k -> FilterChip(kind == k, { kind = if (kind == k) null else k }, { Text(k.label) }) }
        }
        if (shown.isEmpty()) EmptyNote(Icons.Outlined.Inbox, if (filter == 0) "Inbox zero" else "Nothing here", "Add the Idea inbox widget to your home screen to catch ideas in one tap.")
        LazyColumn(Modifier.weight(1f).imePadding(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
            items(shown, key = { it.id }) { idea ->
                IdeaRow(idea, null, onInsert = { i, _ -> sending = i }, onDelete = { AppGraph.ideas.delete(idea.id); Widgets.refresh(ctx) })
            }
            item { Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) }
        }
    }

    sending?.let { idea ->
        var asType by remember { mutableStateOf(if (idea.kind == IdeaKind.SCENE) ElementType.SCENE_HEADING else if (idea.kind == IdeaKind.LINE) ElementType.DIALOGUE else ElementType.NOTE) }
        AlertDialog(
            onDismissRequest = { sending = null },
            title = { Text("Use in which script?") },
            text = {
                Column {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(ElementType.SCENE_HEADING to "New card", ElementType.ACTION to "Action", ElementType.DIALOGUE to "Dialogue", ElementType.NOTE to "Note").forEach { (t, l) ->
                            FilterChip(asType == t, { asType = t }, { Text(l) })
                        }
                    }
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(library, key = { it.id }) { m ->
                            Text(m.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    sending = null
                                    scope.launch {
                                        val s = AppGraph.store.load(m.id) ?: return@launch
                                        var next = s.nextId + 1
                                        val voice = idea.audio.isNotEmpty()
                                        val label = idea.text.ifBlank { if (voice) "Voice idea" else "" }
                                        val type = if (voice && asType != ElementType.SCENE_HEADING) ElementType.NOTE else asType
                                        val add = if (type == ElementType.SCENE_HEADING) listOf(Element(next++, ElementType.SCENE_HEADING, "", synopsis = label), Element(next++, ElementType.ACTION, ""))
                                        else listOf(Element(next++, type, if (type.allCaps) label.uppercase() else label))
                                        var extras = s.extras
                                        if (voice) {
                                            val me = AppGraph.collab.displayName()
                                            val target = add.last()
                                            extras = extras.copy(comments = extras.comments + com.amit.pageone.core.Comment(
                                                java.util.UUID.randomUUID().toString(), target.id, 0, target.text.length, target.text.ifEmpty { label }, me,
                                                AppGraph.collab.colorFor(me + s.id), "Voice idea from the inbox", System.currentTimeMillis(),
                                                attachments = listOf(com.amit.pageone.core.Attachment(idea.audio, com.amit.pageone.core.AttachmentKind.AUDIO, "Voice idea", idea.durationMs)),
                                            ))
                                        }
                                        AppGraph.store.save(s.copy(elements = s.elements + add, extras = extras, modifiedAt = System.currentTimeMillis()), m.pages)
                                        AppGraph.ideas.markUsed(idea.id, m.title)
                                        Widgets.refresh(ctx)
                                        onOpenScript(m.id)
                                    }
                                }.padding(vertical = 12.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { sending = null }) { Text("Cancel") } },
        )
    }
}

val InboxScrim = Color(0x66000000)
