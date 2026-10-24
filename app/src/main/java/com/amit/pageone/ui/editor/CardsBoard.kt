package com.amit.pageone.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.amit.pageone.core.SCENE_COLORS
import com.amit.pageone.core.SceneInfo
import com.amit.pageone.ui.theme.ScriptFont

/**
 * Beat board: every scene as an index card. Long-press and drag to reorder —
 * the whole scene (heading and everything under it) moves in the script.
 */
@Composable
fun CardsBoard(
    scenes: List<SceneInfo>,
    pageOf: (SceneInfo) -> Int,
    onReorder: (List<Long>) -> Unit,
    onEdit: (SceneInfo) -> Unit,
    modifier: Modifier = Modifier,
    extras: com.amit.pageone.core.ScriptExtras = com.amit.pageone.core.ScriptExtras(),
    labelOf: (SceneInfo) -> String = { "${it.number}" },
) {
    val cs = MaterialTheme.colorScheme
    var thread by remember { mutableStateOf<String?>(null) }
    val visible = remember(scenes, thread, extras.scenes) { if (thread == null) scenes else scenes.filter { thread in extras.scene(it.elementId).threads } }
    var order by remember { mutableStateOf(visible) }
    var dragId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(visible) { if (dragId == null) order = visible }
    val grid = rememberLazyGridState()

    if (scenes.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Text("No scenes yet", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Add a card for each beat of your story, then drag them into order.",
                    style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                )
            }
        }
        return
    }

    Column(modifier.fillMaxSize()) {
    if (extras.threads.isNotEmpty()) {
        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        ) {
            item { androidx.compose.material3.FilterChip(thread == null, { thread = null }, { Text("All storylines") }) }
            items(extras.threads.size) { i ->
                val t = extras.threads[i]
                androidx.compose.material3.FilterChip(thread == t.id, { thread = if (thread == t.id) null else t.id }, { Text(t.name) },
                    leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(Color(SCENE_COLORS[t.color.coerceIn(1, SCENE_COLORS.size - 1)]))) })
            }
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(158.dp),
        state = grid,
        contentPadding = PaddingValues(14.dp, 14.dp, 14.dp, 96.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(thread) {
                // Reordering a filtered subset would scramble the rest, so only allow it on the full board.
                if (thread != null) return@pointerInput
                detectDragGesturesAfterLongPress(
                    onDragStart = { pos ->
                        val hit = grid.layoutInfo.visibleItemsInfo.firstOrNull { item ->
                            pos.x >= item.offset.x && pos.x <= item.offset.x + item.size.width &&
                                pos.y >= item.offset.y && pos.y <= item.offset.y + item.size.height
                        }
                        dragId = hit?.key as? Long
                        dragOffset = Offset.Zero
                    },
                    onDrag = { change, amount ->
                        val id = dragId ?: return@detectDragGesturesAfterLongPress
                        change.consume()
                        dragOffset += amount
                        val items = grid.layoutInfo.visibleItemsInfo
                        val cur = items.firstOrNull { it.key == id } ?: return@detectDragGesturesAfterLongPress
                        val cx = cur.offset.x + cur.size.width / 2f + dragOffset.x
                        val cy = cur.offset.y + cur.size.height / 2f + dragOffset.y
                        val target = items.firstOrNull {
                            it.key != id && cx >= it.offset.x && cx <= it.offset.x + it.size.width &&
                                cy >= it.offset.y && cy <= it.offset.y + it.size.height
                        }
                        if (target != null) {
                            val from = order.indexOfFirst { it.elementId == id }
                            val to = order.indexOfFirst { it.elementId == target.key }
                            if (from >= 0 && to >= 0) {
                                order = order.toMutableList().apply { add(to, removeAt(from)) }
                                dragOffset += Offset(
                                    (cur.offset.x - target.offset.x).toFloat(),
                                    (cur.offset.y - target.offset.y).toFloat(),
                                )
                            }
                        }
                    },
                    onDragEnd = {
                        if (dragId != null) onReorder(order.map { it.elementId })
                        dragId = null; dragOffset = Offset.Zero
                    },
                    onDragCancel = { dragId = null; dragOffset = Offset.Zero; order = visible },
                )
            },
    ) {
        items(order, key = { it.elementId }) { sc ->
            val dragging = sc.elementId == dragId
            val meta = extras.scene(sc.elementId)
            IndexCard(
                scene = sc,
                number = if (dragId != null) "${order.indexOf(sc) + 1}" else labelOf(sc),
                page = pageOf(sc),
                dragging = dragging,
                threadColors = meta.threads.mapNotNull { id -> extras.threads.firstOrNull { it.id == id } }.map { Color(SCENE_COLORS[it.color.coerceIn(1, SCENE_COLORS.size - 1)]) },
                mood = meta.moods.firstOrNull(),
                onClick = { onEdit(sc) },
                modifier = if (dragging) Modifier
                    .zIndex(1f)
                    .graphicsLayer {
                        translationX = dragOffset.x; translationY = dragOffset.y
                        scaleX = 1.04f; scaleY = 1.04f; rotationZ = -1.5f
                    } else Modifier,
            )
        }
    }
    }
}

@Composable
private fun IndexCard(
    scene: SceneInfo,
    number: String,
    page: Int,
    dragging: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    threadColors: List<Color> = emptyList(),
    mood: String? = null,
) {
    val cs = MaterialTheme.colorScheme
    val tag = if (scene.color != 0) Color(SCENE_COLORS[scene.color.coerceIn(0, SCENE_COLORS.size - 1)]) else cs.outlineVariant
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cs.surfaceContainerHigh,
        modifier = modifier
            .shadow(if (dragging) 16.dp else 0.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, if (dragging) cs.primary else cs.outlineVariant, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .heightIn(min = 170.dp),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(5.dp).background(tag))
            if (mood != null) MediaImage(mood, Modifier.fillMaxWidth().height(70.dp), 400)
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(number, style = MaterialTheme.typography.labelLarge, color = cs.primary)
                    threadColors.forEach { c -> Spacer(Modifier.size(4.dp)); Box(Modifier.size(8.dp).clip(CircleShape).background(c)) }
                    Spacer(Modifier.weight(1f))
                    Text("p.$page", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    scene.heading.ifBlank { "UNTITLED SCENE" },
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = ScriptFont, fontWeight = FontWeight.Bold),
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                val body = scene.synopsis.ifBlank { scene.preview }
                Text(
                    body.ifBlank { "Tap to add a synopsis" },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (body.isBlank()) cs.onSurfaceVariant.copy(alpha = 0.6f) else cs.onSurfaceVariant,
                    maxLines = 5, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Edit a scene's heading, synopsis and colour tag. */
@Composable
fun SceneEditDialog(
    scene: SceneInfo?,
    onDismiss: () -> Unit,
    onSave: (heading: String, synopsis: String, color: Int) -> Unit,
    onGoTo: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    var heading by remember(scene) { mutableStateOf(scene?.heading ?: "") }
    var synopsis by remember(scene) { mutableStateOf(scene?.synopsis ?: "") }
    var color by remember(scene) { mutableStateOf(scene?.color ?: 0) }
    val cs = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (scene == null) "New scene" else "Scene ${scene.number}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = heading, onValueChange = { heading = it.uppercase() },
                    label = { Text("Scene heading") }, placeholder = { Text("INT. LOCATION - DAY") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = ScriptFont),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = synopsis, onValueChange = { synopsis = it },
                    label = { Text("Synopsis / beat") }, minLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Colour", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                SCENE_COLORS.indices.chunked(5).forEach { rowIdx ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowIdx.forEach { i ->
                        val c = SCENE_COLORS[i]
                        Box(
                            Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(if (i == 0) cs.surfaceContainerHighest else Color(c))
                                .border(2.dp, if (i == color) cs.onSurface else Color.Transparent, CircleShape)
                                .clickable { color = i },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (i == color) Icon(Icons.Filled.Check, null, Modifier.size(14.dp), tint = if (i == 0) cs.onSurface else Color.Black)
                        }
                    }
                }
                }
                if (onGoTo != null || onDelete != null) {
                    Row {
                        if (onGoTo != null) TextButton(onClick = onGoTo) { Text("Open in script") }
                        Spacer(Modifier.weight(1f))
                        if (onDelete != null) TextButton(onClick = onDelete) {
                            Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp), tint = cs.error)
                            Spacer(Modifier.size(4.dp))
                            Text("Delete", color = cs.error)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(heading, synopsis, color) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
