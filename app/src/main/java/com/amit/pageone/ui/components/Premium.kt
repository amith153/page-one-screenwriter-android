package com.amit.pageone.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.amit.pageone.ui.theme.LocalBrand

/** A soft pool of accent light in the top corner — the studio backdrop for top-level screens. */
fun Modifier.studioGlow(brand: com.amit.pageone.ui.theme.Brand, base: Color): Modifier = this
    .background(base)
    .drawBehind {
        drawRect(
            Brush.radialGradient(
                listOf(brand.glow, Color.Transparent),
                center = Offset(size.width * 0.15f, 0f), radius = size.width * 0.95f,
            )
        )
        drawRect(
            Brush.radialGradient(
                listOf(brand.accent2.copy(alpha = brand.glow.alpha * 0.55f), Color.Transparent),
                center = Offset(size.width * 1.05f, size.height * 0.10f), radius = size.width * 0.7f,
            )
        )
    }

/**
 * The premium card: layered surface, hairline gradient edge that catches the light,
 * optional accent wash. Every dashboard and sheet card uses this.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    corner: Dp = 24.dp,
    wash: Boolean = false,
    onClick: (() -> Unit)? = null,
    padding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val brand = LocalBrand.current
    val shape = RoundedCornerShape(corner)
    Column(
        modifier
            .clip(shape)
            .background(cs.surfaceContainer)
            .then(if (wash) Modifier.background(Brush.linearGradient(listOf(brand.accent.copy(alpha = 0.16f), Color.Transparent, brand.accent2.copy(alpha = 0.10f)))) else Modifier)
            .border(1.dp, brand.edge, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** Small caps-style section label with an optional trailing action. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    val cs = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f), letterSpacing = MaterialTheme.typography.labelMedium.letterSpacing * 1.6f)
        if (action != null && onAction != null) {
            Text(action, style = MaterialTheme.typography.labelLarge, color = cs.primary, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(horizontal = 8.dp, vertical = 4.dp))
        }
    }
}

/** A progress ring drawn with the brand gradient. [progress] 0..1 (values above 1 show a full ring). */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 76.dp,
    stroke: Dp = 8.dp,
    track: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val brand = LocalBrand.current
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(700), label = "ring")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = stroke.toPx()
            val inset = w / 2
            val arcSize = Size(this.size.width - w, this.size.height - w)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(w))
            if (p > 0f) drawArc(
                Brush.sweepGradient(listOf(brand.accent2, brand.accent, brand.accent2)),
                -90f, 360f * p, false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round),
            )
        }
        content()
    }
}

/** A rounded icon tile: tinted square with an icon, for quick actions and list rows. */
@Composable
fun IconTile(icon: ImageVector, tint: Color = MaterialTheme.colorScheme.primary, size: Dp = 40.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.52f)) }
}

/** One of the dashboard's quick actions: icon tile above a short label. */
@Composable
fun QuickAction(icon: ImageVector, label: String, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary, badge: Int = 0, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            IconTile(icon, tint, 48.dp)
            if (badge > 0) Box(
                Modifier.align(Alignment.TopEnd).padding(start = 30.dp).size(18.dp).clip(CircleShape).background(cs.error),
                contentAlignment = Alignment.Center,
            ) { Text(if (badge > 9) "9+" else "$badge", style = MaterialTheme.typography.labelSmall, color = Color.White) }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** An icon filled with the theme's accent gradient instead of a flat colour. */
@Composable
fun GradientIcon(icon: ImageVector, contentDescription: String?, modifier: Modifier = Modifier) {
    val brand = LocalBrand.current
    Icon(
        icon, contentDescription,
        modifier = modifier
            .graphicsLayer(alpha = 0.99f)
            .drawWithCache { onDrawWithContent { drawContent(); drawRect(Brush.linearGradient(listOf(brand.accent2, brand.accent)), blendMode = BlendMode.SrcAtop) } },
    )
}

/** A big number with a gradient fill — the hero figure on cards. */
@Composable
fun HeroNumber(text: String, modifier: Modifier = Modifier) {
    val brand = LocalBrand.current
    Text(
        text,
        style = MaterialTheme.typography.displaySmall.copy(brush = brand.hero),
        modifier = modifier,
    )
}

/** A pill: rounded label, optionally coloured. */
@Composable
fun Pill(text: String, color: Color = MaterialTheme.colorScheme.primary, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Box(
        modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.16f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) { Text(text, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1) }
}

/** A thin horizontal meter with the brand gradient. */
@Composable
fun Meter(progress: Float, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val cs = MaterialTheme.colorScheme
    val brand = LocalBrand.current
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(600), label = "meter")
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(cs.surfaceContainerHighest)) {
        if (p > 0f) Box(Modifier.fillMaxWidth(p).height(height).clip(CircleShape).background(brand.hero))
    }
}

/** Empty-state block used across the new screens. */
@Composable
fun EmptyNote(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        IconTile(icon, cs.primary, 56.dp)
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
fun HSpace(w: Dp) = Spacer(Modifier.width(w))

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))
