package com.amit.pageone.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amit.pageone.data.AppSettings
import com.amit.pageone.data.DailyCard
import com.amit.pageone.data.Inspiration
import com.amit.pageone.data.Still
import com.amit.pageone.ui.theme.ScriptFont

/** Lifted blacks + a little less saturation: the soft "matte" print look. */
private val MatteFilter: ColorFilter = run {
    val m = ColorMatrix().apply { setToSaturation(0.82f) }
    val lift = ColorMatrix(
        floatArrayOf(
            0.88f, 0f, 0f, 0f, 14f,
            0f, 0.88f, 0f, 0f, 14f,
            0f, 0f, 0.86f, 0f, 20f,
            0f, 0f, 0f, 1f, 0f,
        )
    )
    m.timesAssign(lift)
    ColorFilter.colorMatrix(m)
}

/**
 * The cinematic card at the top of the library: a different film still and/or
 * quote every day, framed in a projection ratio (2.39 : 1 by default).
 * Tap it to hide the words and just look at the frame.
 */
@Composable
fun DailyInspirationCard(settings: AppSettings, onHide: () -> Unit = {}, modifier: Modifier = Modifier) {
    // Keep showing the last mode while the card folds away.
    val mode = settings.dailyCard.takeIf { it != DailyCard.OFF } ?: DailyCard.BOTH
    val ctx = LocalContext.current
    val day = remember { Inspiration.today() }
    val general = remember(day, mode) { if (mode == DailyCard.QUOTE) Inspiration.lineFor(ctx, day) else Inspiration.quoteFor(day) }
    val wantsStill = mode == DailyCard.STILL || mode == DailyCard.BOTH
    val still by produceState<Still?>(null, day, wantsStill, settings.stillsSource, settings.stillsFolder) {
        value = if (wantsStill) Inspiration.stillFor(ctx, settings.stillsSource, settings.stillsFolder, day) else null
    }
    var clean by rememberSaveable { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    val showQuote = mode == DailyCard.QUOTE || mode == DailyCard.BOTH || (mode == DailyCard.STILL && still?.bitmap == null)
    // With a film's frame, show that film's own famous line (or tagline); otherwise a writing quote.
    val quote = still?.takeIf { it.line.isNotBlank() }?.let { com.amit.pageone.data.Quote(it.line, it.by) } ?: general

    Box(
        modifier
            .fillMaxWidth()
            // A frame keeps its projection ratio; quote-only is a slimmer strip.
            .then(if (wantsStill) Modifier.aspectRatio(settings.frameRatio.ratio) else Modifier.height(132.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF0E0D10))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { clean = !clean },
    ) {
        // ---- the frame (or a matte gradient until/unless there is one)
        Crossfade(targetState = still?.bitmap, animationSpec = tween(700), label = "still") { bmp ->
            if (bmp != null) {
                Image(
                    bmp.asImageBitmap(), contentDescription = still?.title,
                    contentScale = ContentScale.Crop, colorFilter = MatteFilter,
                    // A poster (portrait) is cropped to its upper-middle, where faces usually are.
                    alignment = if (still?.poster == true) androidx.compose.ui.BiasAlignment(0f, -0.35f) else Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(
                            listOf(Color(0xFF17141C), accent.copy(alpha = 0.55f).compositeOn(Color(0xFF231A22)), Color(0xFF0F0E12)),
                            start = Offset(0f, 0f), end = Offset(1400f, 700f),
                        )
                    )
                )
            }
        }
        // Matte wash: soft vignette + a dark floor for legible type.
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)), center = center, radius = size.maxDimension * 0.75f))
            drawRect(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.15f), 0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.78f)))
        }
        // Thin letterbox hairlines, like a projected frame.
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)).align(Alignment.TopCenter))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)).align(Alignment.BottomCenter))

        AnimatedVisibility(visible = !clean, enter = fadeIn(tween(250)), exit = fadeOut(tween(250)), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp)) {
                Row(Modifier.align(Alignment.TopStart), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(accent))
                    Spacer(Modifier.width(7.dp))
                    Text(
                        if (still?.bitmap != null && !showQuote) "TODAY'S FRAME" else if (still?.bitmap != null) "TODAY'S FRAME · LINE" else "TODAY'S LINE",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold),
                        color = Color.White.copy(alpha = 0.72f),
                    )
                }
                Text(
                    "Hide", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                    color = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(50)).clickable(onClick = onHide)
                        .background(Color.Black.copy(alpha = 0.25f)).padding(horizontal = 10.dp, vertical = 4.dp),
                )
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (showQuote) {
                        Text(
                            "“${quote.text}”",
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = ScriptFont, lineHeight = 18.sp),
                            color = Color.White.copy(alpha = 0.95f),
                            maxLines = if (still?.bitmap != null) 3 else 4, overflow = TextOverflow.Ellipsis,
                        )
                        if (quote.by.isNotBlank()) Text(
                            "— ${quote.by}",
                            style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic),
                            color = Color.White.copy(alpha = 0.6f),
                        )
                    }
                    val s = still
                    if (s != null && s.title.isNotBlank()) {
                        Text(
                            listOf(s.title, s.credit).filter { it.isNotBlank() }.joinToString("  ·  "),
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                            color = Color.White.copy(alpha = 0.55f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = if (showQuote) 4.dp else 0.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun Color.compositeOn(bg: Color): Color {
    val a = alpha
    return Color(red * a + bg.red * (1 - a), green * a + bg.green * (1 - a), blue * a + bg.blue * (1 - a), 1f)
}
