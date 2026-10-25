package com.amit.pageone.ui.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallMerge
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.amit.pageone.core.Script
import com.amit.pageone.data.AppGraph
import com.amit.pageone.data.CollabStore
import com.amit.pageone.io.Exports
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Write together by passing drafts back and forth, and see who wrote which line. */
@Composable
fun CollabSheet(
    script: Script,
    vm: EditorViewModel,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val settings by AppGraph.settings.state.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val bytes = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } }
            val po = bytes?.let { CollabStore.parsePageOne(it) }
            if (po == null) onMessage("That isn't a Page One (.pageone) file")
            else { vm.mergeFromFile(po.first, po.second); onDismiss() }
        }
    }

    AppSheet(onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SheetTitle("Collaborate", "Write together by passing drafts back and forth.")

            Section("Pass drafts back and forth", Icons.AutoMirrored.Outlined.CallMerge) {
                Text(
                    "No account needed. Send your draft as a .pageone file over WhatsApp, email or Drive. When your co-writer sends theirs back, open it — Page One merges their changes into yours line by line, and credits each one to its writer. If you both changed the same line, yours stays and theirs is added as a note.",
                    style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = {
                        scope.launch {
                            val bytes = vm.packForSending() ?: return@launch
                            runCatching { Exports.sharePageOne(ctx, script, bytes) }.onFailure { onMessage("Couldn't share: ${it.message}") }
                        }
                    }, modifier = Modifier.weight(1f)) { Icon(Icons.AutoMirrored.Outlined.Send, null); Spacer(Modifier.width(8.dp)); Text("Send draft") }
                    OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.AutoMirrored.Outlined.CallMerge, null); Spacer(Modifier.width(8.dp)); Text("Merge theirs")
                    }
                }
            }

            val authors = script.elements.filter { it.by != null }.groupBy { it.by!! }
                .map { (id, els) -> Triple(id, els.first().byName ?: "Writer", els.size) }.sortedByDescending { it.third }
            Section("Who wrote what", Icons.AutoMirrored.Outlined.CallMerge) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Show a bar beside each line", style = MaterialTheme.typography.bodyLarge)
                        Text("Coloured by the writer who last changed it. Your name is set in Settings.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    Switch(checked = settings.showAuthors, onCheckedChange = { v -> AppGraph.settings.update { it.copy(showAuthors = v) } })
                }
                if (authors.isEmpty()) Text("No names are on any lines yet — lines are stamped as they are written.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                authors.forEach { (id, name, n) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(Color(AppGraph.collab.colorFor(id))))
                        Spacer(Modifier.width(10.dp))
                        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text("$n line${if (n == 1) "" else "s"}", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Section(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(22.dp), color = cs.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = cs.primary)
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}
