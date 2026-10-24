package com.amit.pageone

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amit.pageone.data.AppGraph
import com.amit.pageone.data.IdeaKind
import com.amit.pageone.data.IdeaStore
import com.amit.pageone.ui.components.AudioPlayerRow
import com.amit.pageone.ui.components.GlassCard
import com.amit.pageone.ui.components.IconTile
import com.amit.pageone.ui.components.VoiceRecorder
import com.amit.pageone.ui.components.formatDuration
import com.amit.pageone.ui.theme.PageOneTheme
import com.amit.pageone.widget.Widgets

/**
 * Quick capture: "Save idea" in the share sheet, and the idea-inbox widget's Type / Speak
 * buttons. A floating card over whatever you were doing. Spoken ideas are kept as audio —
 * exactly as you said them — with an optional typed title.
 */
class CaptureActivity : ComponentActivity() {
    companion object { const val EXTRA_VOICE = "voice" }

    @OptIn(ExperimentalLayoutApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppGraph.init(this)
        val shared = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty().trim()
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty().trim()
        val initial = listOf(subject, shared).filter { it.isNotEmpty() }.distinct().joinToString("\n")
        val startVoice = intent.getBooleanExtra(EXTRA_VOICE, false)
        val source = when {
            intent.action == Intent.ACTION_SEND -> "share"
            startVoice -> "voice"
            else -> "widget"
        }

        setContent {
            val settings by AppGraph.settings.state.collectAsStateWithLifecycle()
            PageOneTheme(settings) {
                var text by remember { mutableStateOf(initial) }
                var kind by remember { mutableStateOf(IdeaStore.guessKind(initial)) }
                var recording by remember { mutableStateOf(startVoice) }
                var audio by remember { mutableStateOf("") }
                var duration by remember { mutableLongStateOf(0L) }
                val focus = remember { FocusRequester() }
                fun save() {
                    val idea = AppGraph.ideas.add(text, kind, source, audio, duration)
                    if (idea != null) {
                        Widgets.refresh(this)
                        Toast.makeText(this, "Saved to your idea inbox", Toast.LENGTH_SHORT).show()
                    }
                    finish()
                }
                fun cancel() { if (audio.isNotEmpty()) AppGraph.media.file(audio).delete(); finish() }
                LaunchedEffect(Unit) { if (!startVoice) runCatching { focus.requestFocus() } }

                Box(
                    Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) { cancel() },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    GlassCard(
                        Modifier.fillMaxWidth().padding(12.dp).navigationBarsPadding().imePadding()
                            .clickable(remember { MutableInteractionSource() }, null) {},
                        corner = 28.dp, wash = true,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconTile(Icons.Outlined.Inbox)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Save to idea inbox", style = MaterialTheme.typography.titleMedium)
                                Text("Use it in any script later", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (!recording && audio.isEmpty()) FilledTonalIconButton(onClick = { recording = true }) { Icon(Icons.Outlined.Mic, "Record a voice idea") }
                        }
                        Spacer(Modifier.height(12.dp))
                        if (recording && audio.isEmpty()) {
                            VoiceRecorder(onRecorded = { f, d -> audio = f; duration = d; recording = false; kind = IdeaKind.VOICE }, autoStart = true)
                            Spacer(Modifier.height(10.dp))
                        }
                        if (audio.isNotEmpty()) {
                            AudioPlayerRow(audio, "Voice idea · ${formatDuration(duration)}", duration, Modifier.fillMaxWidth())
                            Spacer(Modifier.height(10.dp))
                        }
                        OutlinedTextField(
                            value = text, onValueChange = { text = it },
                            placeholder = { Text(if (audio.isNotEmpty() || recording) "Title (optional)" else "A line of dialogue, a scene, a character…") },
                            minLines = if (audio.isNotEmpty() || recording) 1 else 3, maxLines = 8, shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                        if (audio.isEmpty() && !recording) {
                            Spacer(Modifier.height(8.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                IdeaKind.entries.filter { it != IdeaKind.VOICE }.forEach { k -> FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.label) }) }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = ::cancel) { Text("Cancel") }
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = ::save, enabled = text.isNotBlank() || audio.isNotEmpty()) { Text("Save idea") }
                        }
                    }
                }
            }
        }
    }
}
