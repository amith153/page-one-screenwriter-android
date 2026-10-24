package com.amit.pageone.ui.settings

// DEBUG BUILDS ONLY. This file is compiled into the builds you run from Android Studio,
// never into the release APK you share (src/release has an empty ServerPanel instead),
// so co-writers' copies contain no server settings, rules or keys screen at all.

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.amit.pageone.data.AppGraph

internal const val FIRESTORE_RULES = """rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // A script can be opened only by its unguessable id. Nobody can list scripts,
    // so there is no way to browse or discover other people's work.
    match /scripts/{scriptId} {
      allow get, create, update: if request.auth != null;
      allow list, delete: if false;
      match /{rest=**} {
        allow read, write: if request.auth != null;
      }
    }
    // Short join codes: looked up one at a time, written once, never listed or changed.
    match /invites/{code} {
      allow get: if request.auth != null;
      allow create: if request.auth != null && code.size() == 8
        && request.resource.data.keys().hasOnly(['s', 't', 'n', 'at']);
      allow list, update, delete: if false;
    }
  }
}"""

private tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Live-sync server. The built-in server's details are never shown on screen, and the
 * advanced (custom server) panel only opens after the phone's screen lock is confirmed.
 * While it is open, the screen is kept out of screenshots and the recent-apps preview.
 */
@Composable
internal fun ServerPanel() {
    val collab = AppGraph.collab
    val server by collab.server.collectAsState()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val builtIn = server == collab.builtInServer
    var unlocked by remember { mutableStateOf(false) }
    var projectId by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var appId by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme

    fun fill() {
        val c = collab.server.value
        val mine = c != collab.builtInServer
        projectId = if (mine) c.projectId else ""
        apiKey = if (mine) c.apiKey else ""
        appId = if (mine) c.appId else ""
        showKey = false
    }
    fun lock() { unlocked = false; projectId = ""; apiKey = ""; appId = ""; showKey = false }

    val confirm = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == android.app.Activity.RESULT_OK) { fill(); unlocked = true }
    }
    fun requestUnlock() {
        val km = ctx.getSystemService(android.app.KeyguardManager::class.java)
        if (km != null && km.isDeviceSecure) {
            @Suppress("DEPRECATION")
            val i = km.createConfirmDeviceCredentialIntent("Live sync server", "Confirm it's you to view or change the server")
            if (i != null) { confirm.launch(i); return }
        }
        android.widget.Toast.makeText(ctx, "Tip: set a screen lock on this phone so nobody else can open this", android.widget.Toast.LENGTH_LONG).show()
        fill(); unlocked = true
    }

    val activity = remember(ctx) { ctx.findActivity() }
    androidx.compose.runtime.DisposableEffect(unlocked) {
        val w = activity?.window
        if (unlocked) w?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (unlocked) w?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) }
    }

    run {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(androidx.compose.material.icons.Icons.Outlined.Lock, null, tint = cs.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Live sync server", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        when {
                            !server.isValid -> "Not set up"
                            builtIn -> "Built in · details hidden"
                            else -> "Your own server · details hidden"
                        },
                        style = MaterialTheme.typography.bodySmall, color = if (server.isValid) cs.primary else cs.onSurfaceVariant,
                    )
                }
                androidx.compose.material3.TextButton(onClick = { if (unlocked) lock() else requestUnlock() }) { Text(if (unlocked) "Lock" else "Advanced") }
            }
            if (unlocked) {
                Surface(shape = RoundedCornerShape(16.dp), color = cs.surfaceContainerHighest) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Only needed if you want your own private server instead of the built-in one.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        val steps = listOf(
                            "Go to console.firebase.google.com and create a project (free Spark plan; Analytics not needed).",
                            "Build → Firestore Database → Create database (production mode, a region near you, e.g. asia-south1).",
                            "In Firestore → Rules, paste the rules below and Publish.",
                            "Build → Authentication → Get started → Sign-in method → enable Anonymous.",
                            "Project settings (gear) → Your apps → add an Android app with package name com.amit.pageone.",
                            "Copy its Project ID, Web API key and App ID into the boxes below.",
                        )
                        steps.forEachIndexed { i, st ->
                            Row { Text("${i + 1}.", style = MaterialTheme.typography.labelLarge, color = cs.primary, modifier = Modifier.width(22.dp)); Text(st, style = MaterialTheme.typography.bodySmall) }
                        }
                        Surface(shape = RoundedCornerShape(10.dp), color = cs.surface) {
                            Text(FIRESTORE_RULES, style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace), modifier = Modifier.padding(10.dp))
                        }
                        androidx.compose.material3.TextButton(onClick = {
                            val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("Firestore rules", FIRESTORE_RULES))
                        }) { Text("Copy rules") }
                    }
                }
                val hiddenHint: @Composable () -> Unit = { Text(if (builtIn) "Hidden — using built-in server" else "") }
                OutlinedTextField(projectId, { projectId = it }, label = { Text("Project ID") }, placeholder = hiddenHint, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    apiKey, { apiKey = it }, label = { Text("Web API key") }, placeholder = hiddenHint, singleLine = true, shape = RoundedCornerShape(14.dp),
                    visualTransformation = if (showKey) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(if (showKey) androidx.compose.material.icons.Icons.Outlined.VisibilityOff else androidx.compose.material.icons.Icons.Outlined.Visibility, if (showKey) "Hide key" else "Show key")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(appId, { appId = it }, label = { Text("App ID") }, placeholder = hiddenHint, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                val candidate = com.amit.pageone.data.ServerConfig(projectId.trim(), apiKey.trim(), appId.trim())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.Button(
                        onClick = { collab.setServer(candidate); lock(); android.widget.Toast.makeText(ctx, "Server saved", android.widget.Toast.LENGTH_SHORT).show() },
                        enabled = candidate.isValid && candidate != server,
                    ) { Text("Save server") }
                    if (!builtIn) androidx.compose.material3.TextButton(onClick = { collab.resetServer(); lock() }) { Text("Use built-in server") }
                }
            }
        }
    }
}

