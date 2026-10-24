package com.amit.pageone.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.amit.pageone.data.AppGraph
import com.amit.pageone.ui.theme.LocalBrand
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun formatDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/** Plays one audio file from the media folder: play / pause, progress, length. */
@Composable
fun AudioPlayerRow(file: String, title: String, durationMs: Long, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var pos by remember { mutableLongStateOf(0L) }
    val length = remember(file, durationMs) { if (durationMs > 0) durationMs else AppGraph.media.durationOf(file) }
    DisposableEffect(file) { onDispose { player?.release(); player = null } }
    LaunchedEffect(playing) { while (playing) { pos = player?.currentPosition?.toLong() ?: 0L; delay(200) } }
    fun toggle() {
        val p = player
        if (p != null && playing) { p.pause(); playing = false; return }
        if (p != null) { p.start(); playing = true; return }
        if (!AppGraph.media.exists(file)) return
        player = MediaPlayer().apply {
            if (AppGraph.media.isUri(file)) setDataSource(ctx, android.net.Uri.parse(file)) else setDataSource(AppGraph.media.file(file).path)
            prepare()
            setOnCompletionListener { playing = false; pos = 0; it.seekTo(0) }
            start()
        }
        playing = true
    }
    Row(
        modifier.clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainerHighest).padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = ::toggle) { Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Pause" else "Play", tint = cs.primary) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(progress = { if (length > 0) pos / length.toFloat() else 0f }, modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape))
        }
        Spacer(Modifier.width(10.dp))
        Text(if (playing || pos > 0) formatDuration(pos) else formatDuration(length), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
    }
}

/**
 * Tap to record, tap to stop — saves an .m4a into the media folder and reports (file, length).
 * Asks for the microphone the first time.
 */
@Composable
fun VoiceRecorder(onRecorded: (file: String, durationMs: Long) -> Unit, modifier: Modifier = Modifier, autoStart: Boolean = false, title: String = "Page One voice memo") {
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val brand = LocalBrand.current
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var current by remember { mutableStateOf<Pair<String, java.io.File>?>(null) }
    var started by remember { mutableLongStateOf(0L) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var level by remember { mutableFloatStateOf(0f) }
    var error by remember { mutableStateOf<String?>(null) }

    fun start() {
        val (name, f) = AppGraph.media.newFile("m4a")
        val r = (if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else @Suppress("DEPRECATION") MediaRecorder())
        runCatching {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(96_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(f.path)
            r.prepare(); r.start()
        }.onFailure { error = "Couldn't start recording"; r.release(); return }
        recorder = r; current = name to f; started = System.currentTimeMillis(); elapsed = 0; error = null
    }
    fun stop() {
        val r = recorder ?: return
        val dur = System.currentTimeMillis() - started
        runCatching { r.stop() }
        r.release(); recorder = null
        // Saved into the writer's own storage (Recordings/Page One), not hidden inside the app.
        current?.let { (name, f) -> if (dur > 600 && f.length() > 0) scope.launch { onRecorded(AppGraph.media.publishRecording(name, title), dur) } else f.delete() }
        current = null
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) start() else error = "Microphone permission is needed to record" }
    fun toggle() {
        if (recorder != null) { stop(); return }
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) start()
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
    LaunchedEffect(Unit) { if (autoStart) toggle() }
    LaunchedEffect(recorder) {
        while (recorder != null) {
            elapsed = System.currentTimeMillis() - started
            level = ((recorder?.maxAmplitude ?: 0) / 12000f).coerceIn(0.05f, 1f)
            delay(80)
        }
    }
    DisposableEffect(Unit) { onDispose { recorder?.let { runCatching { it.stop() }; it.release() }; current?.second?.delete() } }

    val pulse by rememberInfiniteTransition(label = "rec").animateFloat(1f, 1.18f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse")
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        val recording = recorder != null
        Box(
            Modifier.size(56.dp).graphicsLayer { if (recording) { scaleX = pulse; scaleY = pulse } }
                .clip(CircleShape).background(if (recording) cs.error else brand.accent).clickable(onClick = ::toggle),
            contentAlignment = Alignment.Center,
        ) { Icon(if (recording) Icons.Filled.Stop else Icons.Filled.Mic, if (recording) "Stop recording" else "Record", tint = Color.White) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                when { error != null -> error!!; recording -> "Recording · ${formatDuration(elapsed)}"; else -> "Tap to record your idea" },
                style = MaterialTheme.typography.titleSmall, color = if (error != null) cs.error else cs.onSurface,
            )
            if (recording) {
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(cs.surfaceContainerHighest)) {
                    Box(Modifier.fillMaxWidth(level).height(6.dp).clip(CircleShape).background(cs.error))
                }
            } else Text("Saved to your phone's Recordings › Page One", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}

/** Full-screen image viewer: swipe between images, pinch or double-tap to zoom, tap ✕ to close. */
@Composable
fun ImageViewer(files: List<String>, start: Int, captions: List<String> = emptyList(), onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val pager = rememberPagerState(start.coerceIn(0, (files.size - 1).coerceAtLeast(0))) { files.size }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(pager, Modifier.fillMaxSize()) { i -> ZoomableImage(files[i]) }
            Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (files.size > 1) "${pager.currentPage + 1} / ${files.size}" else "", color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f).padding(start = 12.dp))
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Close", tint = Color.White) }
            }
            captions.getOrNull(pager.currentPage)?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp))
            }
        }
    }
}

@Composable
private fun ZoomableImage(file: String) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val state = rememberTransformableState { z, pan, _ ->
        scale = (scale * z).coerceIn(1f, 5f)
        offset = if (scale == 1f) androidx.compose.ui.geometry.Offset.Zero else offset + pan
    }
    Box(
        Modifier.fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { if (scale > 1f) { scale = 1f; offset = androidx.compose.ui.geometry.Offset.Zero } else scale = 2.5f }) }
            .transformable(state, canPan = { scale > 1f }),
        contentAlignment = Alignment.Center,
    ) {
        val bmp by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, file) { value = AppGraph.media.load(file, 2400) }
        bmp?.let {
            androidx.compose.foundation.Image(
                it.asImageBitmapSafe(), null,
                modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            )
        }
    }
}

private fun android.graphics.Bitmap.asImageBitmapSafe() = this.asImageBitmap()

/** A video reference: its first frame with a play badge; tap to watch full screen. */
@Composable
fun VideoTile(ref: String, title: String, modifier: Modifier = Modifier) {
    var playing by remember { mutableStateOf(false) }
    val frame by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, ref) { value = AppGraph.media.videoFrame(ref) }
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF15171C)).clickable { playing = true }, contentAlignment = Alignment.Center) {
        frame?.let { androidx.compose.foundation.Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
        Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0x99000000)), contentAlignment = Alignment.Center) { Icon(Icons.Filled.PlayArrow, "Play video", tint = Color.White) }
        Text(title, color = Color.White, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color(0x88000000)).padding(horizontal = 8.dp, vertical = 3.dp))
    }
    if (playing) VideoDialog(ref) { playing = false }
}

@Composable
fun VideoDialog(ref: String, onDismiss: () -> Unit) {
    var failed by remember { mutableStateOf(!AppGraph.media.exists(ref)) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            if (failed) Text("This video can't be played here. It may have been moved or deleted.", color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(32.dp))
            else androidx.compose.ui.viewinterop.AndroidView(
                factory = { c ->
                    android.widget.VideoView(c).apply {
                        val mc = android.widget.MediaController(c); mc.setAnchorView(this); setMediaController(mc)
                        setOnPreparedListener { start() }
                        setOnErrorListener { _, _, _ -> failed = true; true }
                        if (AppGraph.media.isUri(ref)) setVideoURI(android.net.Uri.parse(ref)) else setVideoPath(AppGraph.media.file(ref).path)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                onRelease = { it.stopPlayback() },
            )
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.statusBars).padding(8.dp)) { Icon(Icons.Filled.Close, "Close", tint = Color.White) }
        }
    }
}