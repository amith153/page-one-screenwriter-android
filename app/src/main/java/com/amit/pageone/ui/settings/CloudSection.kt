package com.amit.pageone.ui.settings

import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amit.pageone.data.AppGraph
import com.amit.pageone.data.DriveFile
import kotlinx.coroutines.launch

/** Settings → Cloud & backup: Google Drive account + copies kept in other drives. */
@Composable
fun CloudSection() {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val drive = AppGraph.drive
    val account by drive.account.collectAsState()
    val auto by drive.autoBackup.collectAsState()
    val withPdf by drive.withPdf.collectAsState()
    val last by drive.lastBackup.collectAsState()
    val status by drive.status.collectAsState()
    val links by AppGraph.links.links.collectAsState()
    val linkStatus by AppGraph.links.status.collectAsState()
    val library by AppGraph.store.library.collectAsState()
    var busy by remember { mutableStateOf<String?>(null) }
    var restoreList by remember { mutableStateOf<List<DriveFile>?>(null) }

    fun toast(m: String) = Toast.makeText(ctx, m, Toast.LENGTH_LONG).show()

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        scope.launch {
            runCatching {
                val r = drive.resultFromIntent(ctx, res.data)
                val t = r.accessToken ?: error("Google didn't grant access")
                drive.finishConnect(t)
            }.onSuccess { toast("Google Drive connected") }.onFailure { toast(it.message ?: "Couldn't connect") }
            busy = null
        }
    }

    fun connect() {
        busy = "Connecting…"
        scope.launch {
            runCatching {
                val r = drive.beginConnect(ctx)
                val pi = r.pendingIntent
                if (r.hasResolution() && pi != null) {
                    consent.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                    return@launch
                }
                drive.finishConnect(r.accessToken ?: error("Google didn't grant access"))
            }.onSuccess { toast("Google Drive connected") }.onFailure { toast(it.message ?: "Couldn't connect") }
            busy = null
        }
    }

    SectionCard("Cloud & backup") {
        // ---------------- Google Drive
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (account != null) Icons.Outlined.CloudDone else Icons.Outlined.Cloud, null, tint = cs.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Google Drive", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        account?.let { "${it.name.ifBlank { "Connected" }} · ${it.email}" }
                            ?: "Optional. Backs up your scripts, and hosts note pictures, audio and video so they open from exported PDFs",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (account == null) {
                Button(onClick = ::connect, enabled = busy == null) { Text(busy ?: "Connect Google account") }
                Text(
                    "Not needed to save a PDF to Drive — “Save to…” on the export sheet does that. Each person connects their own account; Page One can only see the files it creates.",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { drive.setAutoBackup(!auto) }) {
                    Column(Modifier.weight(1f)) {
                        Text("Automatic backup", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (last > 0) "Last backup ${DateUtils.getRelativeTimeSpanString(last)}" else "Uploads ~20 s after you stop writing",
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                        )
                    }
                    Switch(auto, { drive.setAutoBackup(it) })
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { drive.setWithPdf(!withPdf) }) {
                    Column(Modifier.weight(1f)) {
                        Text("Also keep a PDF", style = MaterialTheme.typography.bodyMedium)
                        Text("Handy for sharing a read-only copy from Drive", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    Switch(withPdf, { drive.setWithPdf(it) })
                }
                status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = busy == null, onClick = {
                        busy = "Backing up…"
                        scope.launch {
                            runCatching { drive.backupAll() }.onSuccess { toast("Backed up $it scripts") }.onFailure { toast(it.message ?: "Backup failed") }
                            busy = null
                        }
                    }) { Icon(Icons.Outlined.CloudUpload, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Back up now") }
                    OutlinedButton(enabled = busy == null, onClick = {
                        busy = "Loading…"
                        scope.launch {
                            runCatching { drive.list() }.onSuccess { restoreList = it }.onFailure { toast(it.message ?: "Couldn't list backups") }
                            busy = null
                        }
                    }) { Icon(Icons.Outlined.Restore, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Restore") }
                }
                busy?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
                TextButton(onClick = { drive.disconnect(); toast("Disconnected from Google Drive") }) { Text("Disconnect", color = cs.error) }
            }
        }

        // ---------------- Other drives
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Folder, null, tint = cs.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Dropbox, OneDrive & other drives", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "In any script: ⋮ → Export & share → “Keep a synced copy”. Pick a folder in your drive app; Page One updates that file every time you save, through the account already signed in on your phone.",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                    )
                }
            }
            linkStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.error) }
            links.forEach { l ->
                val title = library.firstOrNull { it.id == l.scriptId }?.title ?: "Script"
                Surface(shape = RoundedCornerShape(14.dp), color = cs.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                            Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${l.format.label} · ${l.label}", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                        }
                        IconButton(onClick = { AppGraph.links.remove(l) }) { Icon(Icons.Outlined.Delete, "Stop syncing") }
                    }
                }
            }
        }
    }

    restoreList?.let { files ->
        AlertDialog(
            onDismissRequest = { restoreList = null },
            title = { Text("Restore from Google Drive") },
            text = {
                if (files.isEmpty()) Text("No backups in your Drive's “Page One” folder yet.")
                else LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(files, key = { it.id }) { f ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                restoreList = null
                                scope.launch {
                                    runCatching { drive.restore(f) }
                                        .onSuccess { r -> toast(if (r.isNew) "Restored “${f.name.removeSuffix(".pageone")}”" else "Merged Drive copy into your script") }
                                        .onFailure { toast(it.message ?: "Restore failed") }
                                    AppGraph.store.refresh()
                                }
                            }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(f.name.removeSuffix(".pageone"), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(f.modified.take(16).replace('T', ' '), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                            }
                            Icon(Icons.Outlined.Restore, null)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { restoreList = null }) { Text("Close") } },
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.padding(top = 20.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = cs.primary, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
        Surface(shape = RoundedCornerShape(22.dp), color = cs.surfaceContainer) {
            Column(Modifier.padding(vertical = 6.dp)) { content() }
        }
    }
}
