package com.amit.pageone.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.amit.pageone.ui.theme.LocalBrand

/**
 * A year-at-a-glance calendar of writing days (like a contributions graph): one square per day,
 * columns are weeks, brighter = more words. [days] is oldest → today.
 */
@Composable
fun WritingHeatmap(days: List<Pair<String, Int>>, goal: Int, modifier: Modifier = Modifier, rows: Int = 7) {
    val cs = MaterialTheme.colorScheme
    val brand = LocalBrand.current
    val max = maxOf(days.maxOfOrNull { it.second } ?: 0, goal, 1)
    val active = days.count { it.second > 0 }
    val total = days.sumOf { it.second }
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(((rows * 14) + 2).dp)) {
            // As many whole weeks as fit the width (squares stay square), today in the bottom-right corner.
            val cell = size.height / rows
            val cols = (size.width / cell).toInt().coerceAtLeast(1)
            val shown = days.takeLast(cols * rows)
            val gap = cell * 0.18f
            val offset = cols * rows - shown.size
            shown.forEachIndexed { i, (_, v) ->
                val k = i + offset
                val col = k / rows; val row = k % rows
                val x = size.width - (cols - col) * cell
                val c = when {
                    v <= 0 -> cs.surfaceContainerHighest
                    else -> brand.accent.copy(alpha = (0.25f + 0.75f * (v / max.toFloat())).coerceAtMost(1f))
                }
                drawRoundRect(c, Offset(x + gap / 2, row * cell + gap / 2), Size(cell - gap, cell - gap), CornerRadius(cell * 0.22f))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$active writing days · %,d words".format(total), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("less", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            listOf(0f, 0.3f, 0.6f, 1f).forEach { a ->
                Canvas(Modifier.padding(horizontal = 2.dp).size(10.dp)) { drawRoundRect(if (a == 0f) cs.surfaceContainerHighest else brand.accent.copy(alpha = 0.25f + 0.75f * a), cornerRadius = CornerRadius(3f)) }
            }
            Text("more", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            Spacer(Modifier.width(2.dp))
        }
    }
}
