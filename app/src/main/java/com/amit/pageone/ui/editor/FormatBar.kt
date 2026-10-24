package com.amit.pageone.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.VerticalSplit
import androidx.compose.material.icons.outlined.FormatAlignCenter
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amit.pageone.core.ElementType
import com.amit.pageone.ui.theme.ScriptFont

private val primaryTypes = listOf(
    ElementType.SCENE_HEADING, ElementType.ACTION, ElementType.CHARACTER,
    ElementType.PARENTHETICAL, ElementType.DIALOGUE, ElementType.TRANSITION, ElementType.SHOT,
)

/**
 * The bar that sits on top of the keyboard: autocomplete suggestions plus an
 * element-type picker, undo/redo and extra actions.
 */
@Composable
fun FormatBar(
    current: ElementType?,
    suggestions: List<Suggestion>,
    canUndo: Boolean,
    canRedo: Boolean,
    onType: (ElementType) -> Unit,
    onSuggestion: (Suggestion) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onDual: () -> Unit,
    onNote: () -> Unit,
    onDelete: () -> Unit,
    onHideKeyboard: () -> Unit,
    modifier: Modifier = Modifier,
    /** Words are selected: offer "pin a note" right on the bar. */
    hasSelection: Boolean = false,
    onComment: () -> Unit = {},
    onInbox: () -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.surfaceContainer, tonalElevation = 0.dp, modifier = modifier) {
        Column {
            HorizontalDivider(color = cs.outlineVariant)
            AnimatedVisibility(visible = suggestions.isNotEmpty(), enter = expandVertically(), exit = shrinkVertically()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    items(suggestions, key = { it.label + it.newText }) { s ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = cs.primaryContainer,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { onSuggestion(s) },
                        ) {
                            Text(
                                s.label,
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = ScriptFont, fontWeight = FontWeight.Bold),
                                color = cs.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            )
                        }
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().height(52.dp).padding(start = 6.dp),
            ) {
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.width(2.dp))
                    primaryTypes.forEach { t ->
                        TypePill(t.shortLabel, selected = t == current) { onType(t) }
                    }
                    Spacer(Modifier.width(4.dp))
                }
                Box(Modifier.width(1.dp).height(26.dp).background(cs.outlineVariant))
                AnimatedVisibility(hasSelection) {
                    IconButton(onClick = onComment) { Icon(Icons.Outlined.AddComment, "Pin a note to the selection", tint = cs.primary) }
                }
                IconButton(onClick = onUndo, enabled = canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, "Undo") }
                IconButton(onClick = onRedo, enabled = canRedo) { Icon(Icons.AutoMirrored.Filled.Redo, "Redo") }
                var menu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreHoriz, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Dual dialogue") },
                            leadingIcon = { Icon(Icons.Outlined.VerticalSplit, null) },
                            onClick = { menu = false; onDual() },
                        )
                        DropdownMenuItem(
                            text = { Text("Pin a note here") },
                            leadingIcon = { Icon(Icons.Outlined.AddComment, null) },
                            onClick = { menu = false; onComment() },
                        )
                        DropdownMenuItem(
                            text = { Text("Note to self (line)") },
                            leadingIcon = { Icon(Icons.Outlined.Lightbulb, null) },
                            onClick = { menu = false; onNote() },
                        )
                        DropdownMenuItem(
                            text = { Text("Insert from idea inbox") },
                            leadingIcon = { Icon(Icons.Outlined.Inbox, null) },
                            onClick = { menu = false; onInbox() },
                        )
                        DropdownMenuItem(
                            text = { Text("Lyrics") },
                            leadingIcon = { Icon(Icons.Outlined.MusicNote, null) },
                            onClick = { menu = false; onType(ElementType.LYRICS) },
                        )
                        DropdownMenuItem(
                            text = { Text("Centered") },
                            leadingIcon = { Icon(Icons.Outlined.FormatAlignCenter, null) },
                            onClick = { menu = false; onType(ElementType.CENTERED) },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Delete element") },
                            leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                            onClick = { menu = false; onDelete() },
                        )
                        DropdownMenuItem(
                            text = { Text("Hide keyboard") },
                            leadingIcon = { Icon(Icons.Filled.KeyboardHide, null) },
                            onClick = { menu = false; onHideKeyboard() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TypePill(label: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) cs.primary else cs.surfaceContainerHighest,
        contentColor = if (selected) cs.onPrimary else cs.onSurfaceVariant,
        border = if (selected) null else BorderStroke(1.dp, cs.outlineVariant),
        modifier = Modifier.height(34.dp).clip(RoundedCornerShape(50)).clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
