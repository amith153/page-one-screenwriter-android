package com.amit.pageone.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amit.pageone.core.SCENE_COLORS
import com.amit.pageone.core.SceneInfo
import com.amit.pageone.ui.theme.ScriptFont

/** Side panel listing every scene, with page numbers and quick search. */
@Composable
fun NavigatorDrawer(
    scenes: List<SceneInfo>,
    pageOf: (SceneInfo) -> Int,
    currentSceneId: Long?,
    totalPages: Int,
    onPick: (SceneInfo) -> Unit,
    labelOf: (SceneInfo) -> String = { "${it.number}" },
) {
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.widthIn(max = 340.dp).fillMaxHeight(),
    ) {
        NavigatorContent(scenes, pageOf, currentSceneId, totalPages, onPick, labelOf)
    }
}

/** The navigator's list — in the drawer on phones, as a permanent side pane on tablets. */
@Composable
fun NavigatorContent(
    scenes: List<SceneInfo>,
    pageOf: (SceneInfo) -> Int,
    currentSceneId: Long?,
    totalPages: Int,
    onPick: (SceneInfo) -> Unit,
    labelOf: (SceneInfo) -> String = { "${it.number}" },
) {
    val cs = MaterialTheme.colorScheme
    var q by remember { mutableStateOf("") }
    val filtered = remember(scenes, q) {
        if (q.isBlank()) scenes else scenes.filter {
            it.heading.contains(q, true) || it.synopsis.contains(q, true) || it.preview.contains(q, true)
        }
    }
    Column {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text("Navigator", style = MaterialTheme.typography.headlineSmall)
            Text(
                "${scenes.size} scenes · $totalPages pages",
                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = q, onValueChange = { q = it },
                placeholder = { Text("Search scenes") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = cs.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        LazyColumn(contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 24.dp)) {
            items(filtered, key = { it.elementId }) { sc ->
                val selected = sc.elementId == currentSceneId
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) cs.primaryContainer else Color.Transparent)
                        .clickable { onPick(sc) }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        Modifier.width(34.dp).padding(top = 2.dp),
                        contentAlignment = Alignment.TopStart,
                    ) {
                        Text(
                            labelOf(sc),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant,
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (sc.color != 0) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(Color(SCENE_COLORS[sc.color.coerceIn(0, SCENE_COLORS.size - 1)])))
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                sc.heading.ifBlank { "UNTITLED SCENE" },
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = ScriptFont, fontWeight = FontWeight.Bold),
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        val sub = sc.synopsis.ifBlank { sc.preview }
                        if (sub.isNotBlank()) {
                            Text(
                                sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Text(
                        "p.${pageOf(sc)}", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, top = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun ColorDot(index: Int, size: Int = 10) {
    if (index == 0) return
    Box(Modifier.size(size.dp).clip(CircleShape).background(Color(SCENE_COLORS[index.coerceIn(0, SCENE_COLORS.size - 1)])))
}

val SceneRowSpacing = Arrangement.spacedBy(2.dp)
