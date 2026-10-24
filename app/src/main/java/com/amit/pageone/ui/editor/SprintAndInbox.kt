package com.amit.pageone.ui.editor

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amit.pageone.core.ElementType
import com.amit.pageone.data.AppGraph
import com.amit.pageone.data.Idea
import com.amit.pageone.data.IdeaKind
import com.amit.pageone.ui.components.EmptyNote
import com.amit.pageone.ui.components.GlassCard
import com.amit.pageone.ui.components.HeroNumber
import com.amit.pageone.ui.components.IconTile
import com.amit.pageone.ui.components.Meter
import com.amit.pageone.ui.components.Pill
import com.amit.pageone.ui.components.ProgressRing
import com.amit.pageone.ui.components.SectionHeader
import com.amit.pageone.ui.theme.LocalBrand
import kotlinx.coroutines.delay

// ====================================================================== sprints

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SprintSheet(scriptTitle: String, onDismiss: () -> Unit, onStart: (Int, Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var minutes by remember { mutableStateOf(25) }
    var words by remember { mutableStateOf(500) }
    val history = remember { AppGraph.sprints.history() }
    AppSheet(onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Outlined.Timer)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Writing sprint", style = MaterialTheme.typography.headlineSmall)
                    Text("Phone down, words up. A bell rings when time's up.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(18.dp))
            SectionHeader("Time")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(10, 15, 25, 45, 60, 90).forEach { m -> FilterChip(minutes == m, { minutes = m }, { Text("$m min") }) } }
            Spacer(Modifier.height(10.dp))
            SectionHeader("Goal")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "Just write", 180 to "1 page", 360 to "2 pages", 500 to "500 words", 900 to "5 pages", 1000 to "1,000 words").forEach { (w, l) -> FilterChip(words == w, { words = w }, { Text(l) }) }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = { onStart(minutes, words) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Outlined.Bolt, null); Spacer(Modifier.width(8.dp)); Text("Start $minutes-minute sprint")
            }
            if (history.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                SectionHeader("Recent sprints")
                val best = history.maxOf { it.words }
                Text("${history.size} sprints · best ${best} words · average ${history.sumOf { it.words } / history.size} words", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                history.take(5).forEach { r ->
                    Row(Modifier.padding(vertical = 4.dp)) {
                        Text(android.text.format.DateUtils.getRelativeTimeSpanString(r.at).toString(), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Text("${r.words} words · ${r.minutes} min" + if (r.goal > 0 && r.words >= r.goal) "  ✓" else "", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** Countdown strip while a sprint is running. */
@Composable
fun SprintBanner(modifier: Modifier = Modifier) {
    val sprint by AppGraph.sprints.current.collectAsState()
    val s = sprint
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(s?.startAt) {
        while (s != null) { now = System.currentTimeMillis(); AppGraph.sprints.tick(); delay(1000) }
    }
    AnimatedVisibility(s != null, modifier) {
        if (s == null) return@AnimatedVisibility
        val cs = MaterialTheme.colorScheme
        val left = s.remainingMs(now)
        val frac = 1f - left / (s.minutes * 60_000f)
        Surface(color = cs.surfaceContainerHigh) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Timer, null, tint = LocalBrand.current.accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("%d:%02d".format(left / 60_000, (left / 1000) % 60), style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace))
                    Spacer(Modifier.width(10.dp))
                    Text(if (s.goalWords > 0) "${s.words} / ${s.goalWords} words" else "${s.words} words", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
                    TextButton(onClick = { AppGraph.sprints.finish() }) { Text("Finish") }
                    IconButton(onClick = { AppGraph.sprints.cancel() }, modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.Close, "Cancel sprint", modifier = Modifier.size(18.dp)) }
                }
                Meter(if (s.goalWords > 0) s.progress else frac, height = 4.dp)
            }
        }
    }
}

/** Shown when a sprint ends. */
@Composable
fun SprintSummary() {
    val done by AppGraph.sprints.finished.collectAsState()
    val s = done ?: return
    val hit = s.goalWords > 0 && s.words >= s.goalWords
    AlertDialog(
        onDismissRequest = { AppGraph.sprints.consumeFinished() },
        icon = { Icon(if (hit) Icons.Outlined.EmojiEvents else Icons.Outlined.Timer, null, tint = LocalBrand.current.accent) },
        title = { Text(if (hit) "Goal smashed" else "Sprint complete") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                ProgressRing(if (s.goalWords > 0) s.progress else 1f, size = 120.dp, stroke = 10.dp) { HeroNumber("${s.words}") }
                Spacer(Modifier.height(8.dp))
                Text("words in ${s.minutes} minutes" + if (s.goalWords > 0) " · goal ${s.goalWords}" else "", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("≈ %.1f pages".format(s.words / 180f), style = MaterialTheme.typography.labelLarge)
            }
        },
        confirmButton = { TextButton(onClick = { AppGraph.sprints.consumeFinished() }) { Text("Nice") } },
    )
}

// ====================================================================== idea inbox (in the editor)

@Composable
fun InboxSheet(scriptTitle: String, onDismiss: () -> Unit, onInsert: (Idea, ElementType) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ideas by AppGraph.ideas.ideas.collectAsState()
    LaunchedEffect(Unit) { AppGraph.ideas.reload() }
    var adding by remember { mutableStateOf("") }
    val waiting = ideas.filter { it.usedIn.isEmpty() }
    AppSheet(onDismiss) {
        Column {
            SheetTitle("Idea inbox", "${waiting.size} waiting · tap an idea to drop it into the script at your cursor")
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(adding, { adding = it }, placeholder = { Text("Jot a new idea") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f))
                TextButton(onClick = { AppGraph.ideas.add(adding); adding = "" }, enabled = adding.isNotBlank()) { Text("Add") }
            }
            if (waiting.isEmpty()) EmptyNote(Icons.Outlined.Inbox, "Inbox zero", "Share text to “Save idea” from any app, or add the Idea inbox widget to your home screen.")
            LazyColumn(Modifier.heightIn(max = 560.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                items(waiting, key = { it.id }) { idea -> IdeaRow(idea, scriptTitle, onInsert) }
            }
        }
    }
}

@Composable
fun IdeaRow(idea: Idea, scriptTitle: String?, onInsert: ((Idea, ElementType) -> Unit)?, onDelete: (() -> Unit)? = null) {
    val cs = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    Box {
        Surface(shape = RoundedCornerShape(18.dp), color = cs.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(18.dp)).clickable { menu = true }) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill(idea.kind.label, color = when (idea.kind) { IdeaKind.LINE -> cs.primary; IdeaKind.SCENE -> cs.tertiary; IdeaKind.CHARACTER -> LocalBrand.current.accent2; else -> cs.onSurfaceVariant })
                    Spacer(Modifier.width(8.dp))
                    Text(android.text.format.DateUtils.getRelativeTimeSpanString(idea.createdAt).toString() + " · " + when (idea.source) { "share" -> "shared"; "voice" -> "recorded"; "widget" -> "widget"; else -> "typed" },
                        style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
                    if (idea.usedIn.isNotEmpty()) Text("used in ${idea.usedIn}", style = MaterialTheme.typography.labelSmall, color = LocalBrand.current.success, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(6.dp))
                if (idea.text.isNotBlank()) Text(idea.text, style = MaterialTheme.typography.bodyMedium, maxLines = 6, overflow = TextOverflow.Ellipsis)
                if (idea.audio.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    com.amit.pageone.ui.components.AudioPlayerRow(idea.audio, if (idea.text.isBlank()) "Voice idea" else "Recording", idea.durationMs, Modifier.fillMaxWidth())
                }
            }
        }
        DropdownMenu(menu, { menu = false }) {
            if (onInsert != null) {
                DropdownMenuItem(text = { Text("Insert as action") }, onClick = { menu = false; onInsert(idea, ElementType.ACTION) })
                DropdownMenuItem(text = { Text("Insert as dialogue") }, onClick = { menu = false; onInsert(idea, ElementType.DIALOGUE) })
                DropdownMenuItem(text = { Text("New scene card") }, onClick = { menu = false; onInsert(idea, ElementType.SCENE_HEADING) })
                DropdownMenuItem(text = { Text("Insert as note to self") }, onClick = { menu = false; onInsert(idea, ElementType.NOTE) })
            }
            DropdownMenuItem(text = { Text(if (idea.usedIn.isEmpty()) "Mark as used" else "Back to waiting") }, onClick = {
                menu = false; AppGraph.ideas.update(idea.copy(usedIn = if (idea.usedIn.isEmpty()) (scriptTitle ?: "a script") else ""))
            })
            DropdownMenuItem(text = { Text("Delete", color = cs.error) }, onClick = { menu = false; onDelete?.invoke() ?: AppGraph.ideas.delete(idea.id) })
        }
    }
}

// ====================================================================== shortcuts

@Composable
fun ShortcutsSheet(onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    AppSheet(onDismiss) {
        Column {
            Row(Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Outlined.Keyboard)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Keyboard shortcuts", style = MaterialTheme.typography.headlineSmall)
                    Text("For Bluetooth keyboards, tablets and Chromebooks", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
            LazyColumn(Modifier.heightIn(max = 560.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 4.dp)) {
                item { ShortcutRow("Tab", "Next element type") }
                item { ShortcutRow("Enter", "New element (smart: Character → Dialogue…)") }
                items(Shortcut.all) { s -> ShortcutRow(s.keys, s.what) }
                item { ShortcutRow("Ctrl+Z / Ctrl+Y", "Undo / redo") }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ShortcutRow(keys: String, what: String) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(what, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(cs.surfaceContainerHighest).padding(horizontal = 10.dp, vertical = 4.dp)) {
            Text(keys, style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace))
        }
    }
}
