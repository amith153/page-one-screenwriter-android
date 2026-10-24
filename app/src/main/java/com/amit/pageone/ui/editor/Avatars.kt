package com.amit.pageone.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TheaterComedy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Avatar colour for a character, stable across the app — muted tones that sit well on paper and ink. */
fun characterColor(name: String): Color {
    val palette = listOf(0xFFC9A96E, 0xFF7D8BA6, 0xFFC48B8F, 0xFF8FA58C, 0xFF9C8AA5, 0xFF6F9FA8, 0xFFC0704F, 0xFFA9814F, 0xFF9E3B4C, 0xFF4F6D96)
    return Color(palette[(name.hashCode() and 0x7FFFFFFF) % palette.size])
}

@Composable
fun Avatar(name: String?, size: Int = 34) {
    val c = if (name == null) MaterialTheme.colorScheme.onSurfaceVariant else characterColor(name)
    Box(Modifier.size(size.dp).clip(CircleShape).background(c.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
        if (name == null) Icon(Icons.Outlined.TheaterComedy, null, tint = c, modifier = Modifier.size((size * 0.55f).dp))
        else Text(name.take(1), style = MaterialTheme.typography.labelLarge, color = c)
    }
}
