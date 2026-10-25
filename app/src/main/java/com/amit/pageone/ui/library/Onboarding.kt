package com.amit.pageone.ui.library

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.amit.pageone.data.AppGraph
import com.amit.pageone.data.Notifier
import com.amit.pageone.ui.components.GlassCard
import com.amit.pageone.ui.components.studioGlow
import com.amit.pageone.ui.theme.LocalBrand
import com.amit.pageone.ui.theme.ScriptFont
import kotlinx.coroutines.launch

private data class Slide(val icon: ImageVector, val kicker: String, val title: String, val body: String)

private val slides = listOf(
    Slide(Icons.Outlined.EditNote, "WRITE", "Industry format,\nwithout thinking about it", "Scene headings, cues and dialogue snap into place as you type. Script Doctor catches what slips through."),
    Slide(Icons.Outlined.AccountTree, "SHAPE", "See the whole story", "Index cards, beat templates with page targets, storylines, an X-ray of your pacing, a bible for every character and place."),
    Slide(Icons.Outlined.Lightbulb, "CAPTURE", "Never lose an idea", "Save a line, a scene or a voice memo from any app or your home screen — then drop it into a script when you're ready."),
    Slide(Icons.Outlined.WorkOutline, "SHIP", "Ready for production", "Deadlines that keep you honest, locked pages and A-scenes, reports, sides, notes, and drafts you can pass to a co-writer."),
)

/** First run: four quick slides, then set a goal and a reminder. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Onboarding(onDone: (openSample: Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val brand = LocalBrand.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState { slides.size + 1 }
    var goal by remember { mutableStateOf(AppGraph.settings.state.value.dailyGoal.takeIf { it > 0 } ?: 500) }
    var remind by remember { mutableStateOf(true) }
    var hour by remember { mutableStateOf(19) }
    var minute by remember { mutableStateOf(0) }
    var picking by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { Notifier.scheduleAll(ctx) }

    fun finish(sample: Boolean) {
        AppGraph.settings.update { it.copy(onboarded = true, dailyGoal = goal, dailyReminder = remind, reminderHour = hour, reminderMinute = minute) }
        if (remind && Build.VERSION.SDK_INT >= 33 && !Notifier.canPost(ctx)) ask.launch(Manifest.permission.POST_NOTIFICATIONS)
        Notifier.scheduleAll(ctx)
        onDone(sample)
    }

    Column(Modifier.fillMaxSize().studioGlow(brand, cs.background).windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
            if (pager.currentPage < slides.size) TextButton(onClick = { scope.launch { pager.animateScrollToPage(slides.size) } }) { Text("Skip") }
            else Spacer(Modifier.height(48.dp))
        }
        HorizontalPager(pager, Modifier.weight(1f)) { i ->
            Box(Modifier.fillMaxSize().padding(horizontal = 28.dp), contentAlignment = Alignment.Center) {
                if (i < slides.size) {
                    val s = slides[i]
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 460.dp)) {
                        Box(Modifier.size(120.dp).clip(CircleShape).background(Brush.linearGradient(listOf(brand.accent.copy(alpha = 0.35f), brand.accent2.copy(alpha = 0.18f)))), contentAlignment = Alignment.Center) {
                            Icon(s.icon, null, tint = brand.accent, modifier = Modifier.size(56.dp))
                        }
                        Spacer(Modifier.height(32.dp))
                        Text(s.kicker, style = MaterialTheme.typography.labelLarge, color = brand.accent)
                        Spacer(Modifier.height(8.dp))
                        Text(s.title, style = MaterialTheme.typography.headlineMedium, color = cs.onBackground, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(14.dp))
                        Text(s.body, style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 460.dp)) {
                        Text("FADE IN:", style = MaterialTheme.typography.headlineMedium.copy(fontFamily = ScriptFont, brush = brand.hero))
                        Spacer(Modifier.height(10.dp))
                        Text("Make writing a habit", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(18.dp))
                        GlassCard(Modifier.fillMaxWidth()) {
                            Text("Daily goal", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(8.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(250 to "250 words", 500 to "500", 1000 to "1,000", 1800 to "10 pages", 0 to "No goal").forEach { (v, l) -> FilterChip(goal == v, { goal = v }, { Text(l) }) }
                            }
                            Spacer(Modifier.height(14.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.NotificationsActive, null, tint = brand.accent)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Remind me to write", style = MaterialTheme.typography.titleSmall)
                                    Text("Plus deadline countdowns and streak saves", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                                }
                                Switch(remind, { remind = it })
                            }
                            if (remind) {
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(onClick = { picking = true }) { Text("At ${com.amit.pageone.ui.components.timeLabel(hour, minute)} — change") }
                                if (picking) com.amit.pageone.ui.components.ReminderTimeDialog(hour, minute, { picking = false }) { h, m -> hour = h; minute = m; picking = false }
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        Button(onClick = { finish(true) }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Explore the sample script") }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { finish(false) }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Start with a blank page") }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 20.dp, top = 8.dp), horizontalArrangement = Arrangement.Center) {
            repeat(slides.size + 1) { i ->
                val w by animateDpAsState(if (pager.currentPage == i) 22.dp else 8.dp, label = "dot")
                Box(Modifier.padding(horizontal = 3.dp).height(8.dp).width(w).clip(RoundedCornerShape(4.dp)).background(if (pager.currentPage == i) brand.accent else cs.outline))
            }
        }
    }
}
