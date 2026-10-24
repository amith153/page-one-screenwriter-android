package com.amit.pageone.ui.editor

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amit.pageone.collab.Change
import com.amit.pageone.collab.Peer
import com.amit.pageone.core.Diff
import com.amit.pageone.core.Element
import com.amit.pageone.core.ElementType
import com.amit.pageone.ui.theme.ScriptFont

private val Added = Color(0xFF2E9E5B)
private val Removed = Color(0xFFD9534F)

/** Before → after, word by word: removed words struck through in red, new words in green. */
fun diffText(c: Change): AnnotatedString = buildAnnotatedString {
    when (c.kind) {
        "add" -> { pushStyle(SpanStyle(color = Added, background = Added.copy(alpha = 0.12f))); append(c.after.ifEmpty { "(empty line)" }); pop() }
        "delete" -> { pushStyle(SpanStyle(color = Removed, textDecoration = TextDecoration.LineThrough)); append(c.before.ifEmpty { "(empty line)" }); pop() }
        "format" -> {
            pushStyle(SpanStyle(color = Removed, textDecoration = TextDecoration.LineThrough)); append(c.before); pop()
            append("  →  ")
            pushStyle(SpanStyle(color = Added, fontWeight = FontWeight.Bold)); append(c.after); pop()
        }
        else -> for (p in Diff.words(c.before, c.after)) {
            when (p.op) {
                Diff.Op.SAME -> append(p.text)
                Diff.Op.ADDED -> { pushStyle(SpanStyle(color = Added, background = Added.copy(alpha = 0.12f), fontWeight = FontWeight.SemiBold)); append(p.text); pop() }
                Diff.Op.REMOVED -> { pushStyle(SpanStyle(color = Removed, textDecoration = TextDecoration.LineThrough)); append(p.text); pop() }
            }
        }
    }
}

private fun verb(c: Change) = when (c.kind) {
    "add" -> "added a line"
    "delete" -> "deleted a line"
    "format" -> "changed the element type"
    else -> "edited"
}

/** Where in the script a change is, e.g. "Scene 4 · INT. KITCHEN - NIGHT". */
fun whereIs(elements: List<Element>, id: Long): String {
    val i = elements.indexOfFirst { it.id == id }
    if (i < 0) return "removed line"
    var scene = 0
    var heading = ""
    for (k in 0..i) if (elements[k].type == ElementType.SCENE_HEADING) { scene++; heading = elements[k].text }
    return if (scene == 0) "Opening" else "Scene $scene · ${heading.ifBlank { "untitled" }}"
}

/** Slim strip under the toolbar while a session is live: who's here and what's new. */
@Composable
fun LiveBanner(
    peers: List<Peer>,
    unseen: Int,
    following: String?,
    onFollow: (String?) -> Unit,
    onReview: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.surfaceContainerHigh) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF3FBF7F)))
            Spacer(Modifier.width(8.dp))
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                if (peers.isEmpty()) Text("Live · waiting for your co-writer", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                peers.forEach { p ->
                    val f = following == p.uid
                    Row(
                        Modifier.clip(RoundedCornerShape(50))
                            .background(if (f) Color(p.color).copy(alpha = 0.25f) else Color.Transparent)
                            .clickable { onFollow(if (f) null else p.uid) }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(p.name, p.color, 20)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (f) "Following ${p.name.substringBefore(' ')}" else p.name.substringBefore(' '),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                }
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = if (unseen > 0) cs.primary else cs.surfaceContainerHighest,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onReview),
            ) {
                Text(
                    if (unseen > 0) "$unseen new" else "History",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (unseen > 0) cs.onPrimary else cs.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                )
            }
        }
    }
}

/** Every recent change, newest first. Tap one to jump there and see before/after. */
@Composable
fun ChangesSheet(
    changes: List<Change>,
    elements: List<Element>,
    myUid: String?,
    isUnseen: (Change) -> Boolean,
    onOpen: (Change) -> Unit,
    onMarkAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val unseen = changes.count(isUnseen)
    AppSheet(onDismiss) {
        Column {
            Row(Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Changes", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        if (unseen > 0) "$unseen you haven't seen" else "You're all caught up",
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                    )
                }
                if (unseen > 0) TextButton(onClick = onMarkAll) { Icon(Icons.Outlined.DoneAll, null); Spacer(Modifier.width(6.dp)); Text("Mark all seen") }
            }
            if (changes.isEmpty()) {
                Text("No changes yet. Edits by anyone in this live script will appear here.", modifier = Modifier.padding(24.dp), color = cs.onSurfaceVariant)
            }
            LazyColumn(Modifier.heightIn(max = 520.dp), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                items(changes, key = { it.id }) { c ->
                    val mine = c.uid == myUid
                    val fresh = isUnseen(c)
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(if (fresh) Color(c.color).copy(alpha = 0.10f) else Color.Transparent)
                            .clickable { onOpen(c) }
                            .padding(12.dp),
                    ) {
                        Avatar(if (mine) "You" else c.name, c.color, 30)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${if (mine) "You" else c.name} ${verb(c)}",
                                    style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                if (fresh) Box(Modifier.size(8.dp).clip(CircleShape).background(cs.primary))
                            }
                            Text(
                                whereIs(elements, c.elementId) + " · " + DateUtils.getRelativeTimeSpanString(c.at),
                                style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(diffText(c), style = MaterialTheme.typography.bodySmall.copy(fontFamily = ScriptFont), maxLines = 4, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

/** Tapped a highlighted line: what changed, how it read before, and the option to undo it. */
@Composable
fun ChangeDialog(
    change: Change,
    elements: List<Element>,
    onRevert: (() -> Unit)?,
    onSeen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Avatar(change.name, change.color, 36) },
        title = { Text("${change.name} ${verb(change)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    whereIs(elements, change.elementId) + " · " + DateUtils.getRelativeTimeSpanString(change.at),
                    style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant,
                )
                Surface(shape = RoundedCornerShape(12.dp), color = cs.surfaceContainerHighest) {
                    Text(diffText(change), style = MaterialTheme.typography.bodyMedium.copy(fontFamily = ScriptFont), modifier = Modifier.padding(12.dp).fillMaxWidth())
                }
                if (change.kind == "edit" && change.before.isNotBlank()) {
                    Text("Before", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                    Text(change.before, style = MaterialTheme.typography.bodySmall.copy(fontFamily = ScriptFont), color = cs.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSeen(); onDismiss() }) { Icon(Icons.Outlined.Visibility, null); Spacer(Modifier.width(6.dp)); Text("Got it") } },
        dismissButton = {
            if (onRevert != null) TextButton(onClick = { onRevert(); onDismiss() }) {
                Icon(Icons.AutoMirrored.Outlined.Undo, null); Spacer(Modifier.width(6.dp)); Text("Put back")
            }
        },
    )
}
