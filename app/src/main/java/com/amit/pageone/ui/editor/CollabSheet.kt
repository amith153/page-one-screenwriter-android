package com.amit.pageone.ui.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallMerge
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amit.pageone.collab.LiveStatus
import com.amit.pageone.collab.Peer
import com.amit.pageone.core.Script
import com.amit.pageone.data.AppGraph
import com.amit.pageone.data.CollabStore
import com.amit.pageone.io.Exports
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Live co-editing + file-based sharing, in one place. */
@Composable
fun CollabSheet(
    script: Script,
    vm: EditorViewModel,
    status: LiveStatus?,
    peers: List<Peer>,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
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
            SheetTitle("Collaborate", "Write together — live, or by passing drafts back and forth.")

            // ---------------- live
            Section("Write together, live", Icons.Outlined.Wifi) {
                if (status == null) {
                    Text(
                        "Your co-writer opens this script on their phone and you both type at the same time. They just enter a short code — no accounts, no setup. Every change shows who made it and how the line read before.",
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                    )
                    Button(onClick = { vm.goLive() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Wifi, null); Spacer(Modifier.width(8.dp)); Text("Start live session")
                    }
                } else {
                    LiveStatusRow(status)
                    // Every invite gets its own random code (valid for 7 days); a new one is made each time you share.
                    var code by remember { mutableStateOf<String?>(null) }
                    var minting by remember { mutableStateOf(false) }
                    suspend fun fresh(): String? {
                        minting = true
                        val c = vm.joinCode()
                        minting = false
                        if (c != null) code = c
                        return c
                    }
                    LaunchedEffect(status) { if (status is LiveStatus.Live && code == null) fresh() }
                    Surface(shape = RoundedCornerShape(18.dp), color = cs.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Join code", style = MaterialTheme.typography.labelLarge, color = cs.onPrimaryContainer)
                            Text(
                                code?.takeIf { !minting }?.let { com.amit.pageone.collab.Hub.pretty(it) } ?: "· · · ·  · · · ·",
                                style = MaterialTheme.typography.displaySmall.copy(fontFamily = com.amit.pageone.ui.theme.ScriptFont, fontWeight = FontWeight.Bold, letterSpacing = 2.sp),
                                color = cs.onPrimaryContainer,
                            )
                            Text("A new code every time you share · works for 7 days", style = MaterialTheme.typography.bodySmall, color = cs.onPrimaryContainer)
                        }
                    }
                    val ready = status is LiveStatus.Live && !minting
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(enabled = ready, onClick = {
                            scope.launch {
                                val c = fresh() ?: return@launch
                                val pc = com.amit.pageone.collab.Hub.pretty(c)
                                val text = "Let's write “${script.title}” together in Page One.\n\nJoin code: $pc\n\nOpen Page One → tap Join (the person-plus icon) → type the code.\nOr tap: pageone://join/$c"
                                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text)
                                }, "Invite a co-writer").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        }, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.Share, null); Spacer(Modifier.width(8.dp)); Text("Send invite") }
                        OutlinedButton(enabled = ready, onClick = {
                            scope.launch {
                                val c = fresh() ?: return@launch
                                copy(ctx, com.amit.pageone.collab.Hub.pretty(c)); onMessage("New join code copied")
                            }
                        }, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.ContentCopy, null); Spacer(Modifier.width(8.dp)); Text("Copy code") }
                    }
                    if (peers.isEmpty()) {
                        Text("No one else is here right now.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    } else {
                        peers.forEach { p ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                                Avatar(p.name, p.color, 28)
                                Spacer(Modifier.width(10.dp))
                                val where = p.elementId?.let { id -> script.elements.indexOfFirst { it.id == id } }?.takeIf { it >= 0 }
                                Text(p.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                if (where != null) Text(describe(script, where), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                            }
                        }
                    }
                    TextButton(onClick = { vm.stopLive() }) {
                        Icon(Icons.Outlined.CloudOff, null); Spacer(Modifier.width(8.dp)); Text("Stop live session on this phone")
                    }
                }
            }

            // ---------------- files
            Section("Pass drafts back and forth", Icons.AutoMirrored.Outlined.CallMerge) {
                Text(
                    "No account needed. Send your draft as a .pageone file over WhatsApp, email or Drive. When your co-writer sends theirs back, open it — Page One merges their changes into yours line by line. If you both changed the same line, yours stays and theirs is added as a note.",
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
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun describe(s: Script, index: Int): String {
    var scene = 0
    for (k in 0..index.coerceAtMost(s.elements.size - 1)) if (s.elements[k].type == com.amit.pageone.core.ElementType.SCENE_HEADING) scene++
    return if (scene > 0) "in scene $scene" else "near the top"
}

private fun copy(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Page One invite", text))
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

@Composable
fun LiveStatusRow(status: LiveStatus) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (status) {
            LiveStatus.Connecting -> { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)); Text("Connecting…") }
            LiveStatus.Live -> { Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF3FBF7F))); Spacer(Modifier.width(10.dp)); Text("Live — changes sync instantly", fontWeight = FontWeight.SemiBold) }
            is LiveStatus.Error -> { Icon(Icons.Outlined.ErrorOutline, null, tint = cs.error); Spacer(Modifier.width(10.dp)); Text(status.message, color = cs.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

/** Round initial badge for a co-writer. */
@Composable
fun Avatar(name: String, color: Long, sizeDp: Int, ring: Color? = null) {
    Box(
        Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(Color(color))
            .then(if (ring != null) Modifier.border(2.dp, ring, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().split(Regex("\\s+")).take(2).joinToString("") { it.take(1) }.uppercase().ifEmpty { "?" },
            color = Color(0xFF111111), fontSize = (sizeDp * 0.4f).sp, fontWeight = FontWeight.Bold,
        )
    }
}

/** Overlapping avatars for the top bar. */
@Composable
fun AvatarStack(peers: List<Peer>, ring: Color) {
    Box(Modifier.width((20 + 14 * (peers.take(3).size - 1).coerceAtLeast(0)).dp)) {
        peers.take(3).forEachIndexed { i, p ->
            Box(Modifier.offset(x = (14 * i).dp)) { Avatar(p.name, p.color, 22, ring) }
        }
    }
}
