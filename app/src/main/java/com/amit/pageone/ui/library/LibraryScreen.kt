package com.amit.pageone.ui.library

import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.amit.pageone.core.Planner
import com.amit.pageone.core.SCENE_COLORS
import com.amit.pageone.data.AppGraph
import com.amit.pageone.data.AppSettings
import com.amit.pageone.data.ScriptMeta
import com.amit.pageone.io.Exports
import com.amit.pageone.ui.components.GlassCard
import com.amit.pageone.ui.components.HeroNumber
import com.amit.pageone.ui.components.Meter
import com.amit.pageone.ui.components.Pill
import com.amit.pageone.ui.components.ProgressRing
import com.amit.pageone.ui.components.QuickAction
import com.amit.pageone.ui.components.SectionHeader
import com.amit.pageone.ui.components.WritingHeatmap
import com.amit.pageone.ui.components.studioGlow
import com.amit.pageone.ui.editor.SprintBanner
import com.amit.pageone.ui.editor.SprintSheet
import com.amit.pageone.ui.editor.SprintSummary
import com.amit.pageone.ui.theme.LocalBrand
import com.amit.pageone.ui.theme.ScriptFont
import kotlinx.coroutines.launch
import java.util.Calendar

private enum class Sort(val label: String) { RECENT("Recent"), TITLE("Title"), LENGTH("Longest"), DEADLINE("Due soonest") }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(settings: AppSettings, onOpen: (String) -> Unit, onSettings: () -> Unit, onInbox: () -> Unit = {}) {
    val store = AppGraph.store
    val library by store.library.collectAsState()
    val today by AppGraph.goals.todayWords.collectAsState()
    val ideas by AppGraph.ideas.ideas.collectAsState()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val brand = LocalBrand.current
    val snackbar = remember { SnackbarHostState() }

    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf(Sort.RECENT) }
    var creating by remember { mutableStateOf(false) }
    var sprinting by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<ScriptMeta?>(null) }
    var deleteTarget by remember { mutableStateOf<ScriptMeta?>(null) }

    LaunchedEffect(Unit) { store.refresh(); AppGraph.ideas.reload() }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val out = Exports.importAny(ctx, uri)
                out.message?.let { android.widget.Toast.makeText(ctx, it, android.widget.Toast.LENGTH_LONG).show() }
                onOpen(out.scriptId)
            }.onFailure { snackbar.showSnackbar("Couldn't import: ${it.message}") }
        }
    }

    val shown = remember(library, query, sort) {
        library.filter { query.isBlank() || it.title.contains(query, true) || it.author.contains(query, true) || it.logline.contains(query, true) }
            .let { l ->
                when (sort) {
                    Sort.RECENT -> l.sortedByDescending { it.modifiedAt }
                    Sort.TITLE -> l.sortedBy { it.title.lowercase() }
                    Sort.LENGTH -> l.sortedByDescending { it.pages }
                    Sort.DEADLINE -> l.sortedWith(compareBy<ScriptMeta> { it.deadline?.date ?: "9999" }.thenByDescending { it.modifiedAt })
                }
            }
    }
    val withDeadlines = remember(library) { library.filter { it.deadline != null }.sortedBy { it.deadline!!.date } }
    val waiting = ideas.filter { it.usedIn.isEmpty() }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = cs.onBackground,
        modifier = Modifier.studioGlow(brand, cs.background),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("New script") },
                containerColor = cs.primary, contentColor = cs.onPrimary,
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
            )
        },
    ) { pad ->
        Box {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(165.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(pad).fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(top = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(greeting(), style = MaterialTheme.typography.labelLarge, color = cs.primary)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(androidx.compose.ui.res.painterResource(com.amit.pageone.R.drawable.ic_brand_mark), null, tint = cs.primary, modifier = Modifier.size(34.dp))
                                Spacer(Modifier.width(10.dp))
                                Text("Page One", style = MaterialTheme.typography.displaySmall, color = cs.onBackground)
                            }
                        }
                        IconButton(onClick = { searching = !searching; if (!searching) query = "" }) { Icon(if (searching) Icons.Filled.Close else Icons.Filled.Search, "Search") }
                        IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Settings") }
                    }
                    if (searching) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = query, onValueChange = { query = it }, singleLine = true,
                            placeholder = { Text("Search titles, authors, loglines") },
                            leadingIcon = { Icon(Icons.Filled.Search, null) }, shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = cs.outlineVariant), modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    // ---- quick actions
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        QuickAction(Icons.Outlined.Timer, "Sprint", Modifier.width(76.dp)) { sprinting = true }
                        QuickAction(Icons.Outlined.Inbox, "Ideas", Modifier.width(76.dp), badge = waiting.size, onClick = onInbox)
                        QuickAction(Icons.Outlined.FileOpen, "Import", Modifier.width(76.dp)) { importer.launch(arrayOf("*/*")) }
                    }
                    SprintBanner(Modifier.clip(RoundedCornerShape(16.dp)))
                    Spacer(Modifier.height(10.dp))
                    androidx.compose.animation.AnimatedVisibility(
                        visible = settings.dailyCard != com.amit.pageone.data.DailyCard.OFF,
                        enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut(),
                    ) {
                        Column {
                            DailyInspirationCard(settings, onHide = { AppGraph.settings.update { it.copy(dailyCard = com.amit.pageone.data.DailyCard.OFF) } })
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                    val week = remember(today) { AppGraph.goals.history(7) }
                    TodayCard(today, settings.dailyGoal, AppGraph.goals.streak(), week)

                    if (withDeadlines.isNotEmpty()) {
                        Spacer(Modifier.height(18.dp))
                        SectionHeader("Deadlines")
                        withDeadlines.forEach { m -> DeadlineRow(m) { onOpen(m.id) } }
                    }

                    Spacer(Modifier.height(18.dp))
                    SectionHeader("Your writing year")
                    GlassCard(Modifier.fillMaxWidth(), corner = 22.dp, padding = 16.dp) {
                        val days = remember(today) { AppGraph.goals.history(371) }
                        WritingHeatmap(days, settings.dailyGoal)
                    }

                    if (waiting.isNotEmpty()) {
                        Spacer(Modifier.height(18.dp))
                        SectionHeader("Idea inbox · ${waiting.size}", action = "Open", onAction = onInbox)
                        GlassCard(Modifier.fillMaxWidth(), corner = 22.dp, padding = 14.dp, onClick = onInbox) {
                            waiting.take(2).forEachIndexed { i, idea ->
                                if (i > 0) Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.Top) {
                                    Box(Modifier.padding(top = 6.dp).size(6.dp).clip(CircleShape).background(cs.tertiary))
                                    Spacer(Modifier.width(10.dp))
                                    Text(idea.text, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    SectionHeader(if (library.isEmpty()) "Your scripts" else "Your scripts · ${library.size}")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Sort.entries.forEach { s -> FilterChip(selected = sort == s, onClick = { sort = s }, label = { Text(s.label) }) }
                    }
                }
            }
            if (library.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(onCreate = { creating = true }, onSample = { scope.launch { onOpen(com.amit.pageone.data.SampleScript.create()) } })
                }
            }
            items(shown, key = { it.id }) { m ->
                var menu by remember { mutableStateOf(false) }
                Box {
                    ScriptCard(m, onClick = { onOpen(m.id) }, onLongClick = { menu = true })
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, null) }, onClick = { menu = false; renameTarget = m })
                        DropdownMenuItem(text = { Text("Duplicate") }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) }, onClick = {
                            menu = false; scope.launch { store.duplicate(m.id); snackbar.showSnackbar("Duplicated") }
                        })
                        DropdownMenuItem(text = { Text("Delete", color = cs.error) }, leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = cs.error) }, onClick = { menu = false; deleteTarget = m })
                    }
                }
            }
        }
        // Frosted strip behind the status bar so scrolled content never collides with the clock.
        Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(Brush.verticalGradient(listOf(cs.background, cs.background.copy(alpha = 0.85f)))))
        }
    }

    SprintSummary()
    if (sprinting) SprintSheet("", onDismiss = { sprinting = false }, onStart = { mi, w ->
        sprinting = false
        AppGraph.sprints.start(mi, w)
        com.amit.pageone.data.Notifier.scheduleAll(ctx)
        scope.launch { snackbar.showSnackbar("Sprint started — open a script and write") }
    })
    if (creating) {
        NewScriptDialog(
            defaultAuthor = settings.defaultAuthor,
            onDismiss = { creating = false },
            onCreate = { title, author ->
                creating = false
                scope.launch {
                    val s = store.create(title)
                    val withAuthor = s.copy(titlePage = s.titlePage.copy(credits = listOf(com.amit.pageone.core.Credit("Written by", author))))
                    store.save(withAuthor, 1)
                    if (author.isNotBlank() && settings.defaultAuthor.isBlank()) AppGraph.settings.update { it.copy(defaultAuthor = author) }
                    onOpen(s.id)
                }
            },
        )
    }
    renameTarget?.let { m ->
        var t by remember { mutableStateOf(m.title) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename") },
            text = { OutlinedTextField(t, { t = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(onClick = {
                    renameTarget = null
                    scope.launch {
                        store.load(m.id)?.let { s -> store.save(s.copy(title = t, titlePage = s.titlePage.copy(title = t), modifiedAt = System.currentTimeMillis()), m.pages) }
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } },
        )
    }
    deleteTarget?.let { m ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete “${m.title}”?") },
            text = { Text("This removes the script and all its versions from this device. Export it first if you want a copy.") },
            confirmButton = { TextButton(onClick = { deleteTarget = null; scope.launch { store.delete(m.id); AppGraph.sync.userDeleted(m.id) } }) { Text("Delete", color = cs.error) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
        )
    }
}

private fun greeting(): String {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        h < 5 -> "Writing late"
        h < 12 -> "Good morning"
        h < 17 -> "Good afternoon"
        else -> "Good evening"
    }
}

/** Today: a progress ring to the daily goal, the streak, and the last seven days. */
@Composable
private fun TodayCard(today: Int, goal: Int, streak: Int, week: List<Pair<String, Int>>) {
    val cs = MaterialTheme.colorScheme
    val brand = LocalBrand.current
    val days = remember(week) {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val out = java.text.SimpleDateFormat("EEEEE", java.util.Locale.getDefault())
        week.map { (k, v) -> (runCatching { out.format(fmt.parse(k)!!) }.getOrDefault("")) to v }
    }
    val weekTotal = week.sumOf { it.second } - (week.lastOrNull()?.second ?: 0) + today
    GlassCard(Modifier.fillMaxWidth(), wash = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(if (goal > 0) today / goal.toFloat() else if (today > 0) 1f else 0f, size = 92.dp, stroke = 9.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (today >= 10000) "%.1fk".format(today / 1000f) else "%,d".format(today), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                    Text(if (goal > 0) "of %,d".format(goal) else "words", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("TODAY", style = MaterialTheme.typography.labelMedium, color = brand.accent)
                Text(
                    when {
                        today == 0 -> "Fade in…"
                        goal > 0 && today >= goal -> "Goal reached"
                        goal > 0 -> "%,d to go".format(goal - today)
                        else -> "≈ %.1f pages".format(today / 180f)
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (streak > 0) {
                        com.amit.pageone.ui.components.GradientIcon(Icons.Outlined.LocalFireDepartment, null, Modifier.size(18.dp))
                        Text(" $streak-day streak", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.width(10.dp))
                    }
                    Text("%,d this week".format(weekTotal), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                WeekBars(days, today, goal, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun DeadlineRow(m: ScriptMeta, onOpen: () -> Unit) {
    val d = m.deadline ?: return
    val cs = MaterialTheme.colorScheme
    val brand = LocalBrand.current
    val plan = remember(d, m.pages) { Planner.plan(d, m.pages) }
    GlassCard(Modifier.fillMaxWidth().padding(vertical = 4.dp), corner = 20.dp, padding = 14.dp, onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(m.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${d.label} · ${m.pages} of ${d.targetPages} pages", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(if (plan.done) "✓" else if (plan.overdue) "late" else "${plan.daysLeft}", style = MaterialTheme.typography.titleLarge.copy(brush = brand.hero))
                Text(if (plan.done || plan.overdue) "" else "days left", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(8.dp))
        Meter(m.pages / d.targetPages.toFloat().coerceAtLeast(1f), height = 6.dp)
        Spacer(Modifier.height(6.dp))
        Text(plan.status, style = MaterialTheme.typography.labelMedium, color = if (plan.behind > 0.5f || plan.overdue) brand.warning else brand.success)
    }
}

/** Seven slim bars (oldest → today); today's bar is the accent colour, the goal is a faint line. */
@Composable
private fun WeekBars(days: List<Pair<String, Int>>, today: Int, goal: Int, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val brand = LocalBrand.current
    val values = days.mapIndexed { i, d -> if (i == days.lastIndex) today else d.second }
    val top = maxOf(values.maxOrNull() ?: 0, goal, 1)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(36.dp)) {
            if (goal > 0) {
                val y = 1f - goal / top.toFloat()
                Box(Modifier.fillMaxWidth().padding(top = (36 * y).dp).height(1.dp).background(cs.onSurfaceVariant.copy(alpha = 0.25f)))
            }
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                values.forEachIndexed { i, v ->
                    val h = if (v == 0) 3f else (5f + 31f * v / top)
                    Box(
                        Modifier.width(10.dp).height(h.dp).clip(RoundedCornerShape(4.dp))
                            .background(if (i == values.lastIndex) brand.hero else Brush.linearGradient(listOf(cs.onSurface.copy(alpha = if (v == 0) 0.12f else 0.3f), cs.onSurface.copy(alpha = if (v == 0) 0.12f else 0.3f))))
                    )
                }
            }
        }
        Spacer(Modifier.height(3.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            days.forEachIndexed { i, d ->
                Text(d.first, style = MaterialTheme.typography.labelSmall, color = if (i == days.lastIndex) cs.primary else cs.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.width(10.dp), textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScriptCard(m: ScriptMeta, onClick: () -> Unit, onLongClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val brand = LocalBrand.current
    val tint = if (m.coverColor in 1 until SCENE_COLORS.size) Color(SCENE_COLORS[m.coverColor]) else cs.primary
    val shape = RoundedCornerShape(22.dp)
    Surface(
        shape = shape, color = cs.surfaceContainer,
        modifier = Modifier.clip(shape).border(1.dp, brand.edge, shape).combinedClickable(onClick = onClick, onLongClick = onLongClick).heightIn(min = 214.dp),
    ) {
        Column {
            Box(
                Modifier.fillMaxWidth().height(122.dp)
                    .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.65f), tint.copy(alpha = 0.18f), Color.Transparent)))
                    .padding(14.dp),
            ) {
                Row(Modifier.align(Alignment.TopEnd).zIndex(2f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (m.locked) Badge("LOCKED", Icons.Outlined.Lock)
                }
                // The cover: a sheet of paper with the title in Courier, slightly lifted.
                Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFFBFAF7), shadowElevation = 6.dp, modifier = Modifier.fillMaxSize().padding(top = 6.dp, start = 4.dp, end = 4.dp)) {
                    Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(m.title.uppercase(), maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = ScriptFont, fontWeight = FontWeight.Bold), color = Color(0xFF1A1A1A))
                        if (m.author.isNotBlank()) Text("by ${m.author}", maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = ScriptFont), color = Color(0xFF6A6A6A))
                    }
                }
            }
            Column(Modifier.padding(14.dp)) {
                Text(m.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${m.pages.coerceAtLeast(1)} pp · ${m.scenes} scenes", style = MaterialTheme.typography.labelMedium, color = cs.primary)
                Spacer(Modifier.height(4.dp))
                if (m.logline.isNotBlank()) {
                    Text(m.logline, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                }
                m.deadline?.let { d ->
                    val plan = remember(d, m.pages) { Planner.plan(d, m.pages) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CalendarMonth, null, tint = if (plan.behind > 0.5f || plan.overdue) brand.warning else brand.success, modifier = Modifier.size(14.dp))
                        Text(if (plan.done) " done" else if (plan.overdue) " overdue" else " ${plan.daysLeft}d · %.1f pp/day".format(plan.pagesPerDay), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(2.dp))
                }
                Text(DateUtils.getRelativeTimeSpanString(m.modifiedAt).toString(), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
private fun Badge(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, dot: Color? = null) {
    Surface(shape = RoundedCornerShape(50), color = Color(0xCC111111)) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            if (dot != null) Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
            if (icon != null) Icon(icon, null, tint = Color.White, modifier = Modifier.size(11.dp))
            Spacer(Modifier.width(4.dp))
            Text(text, style = MaterialTheme.typography.labelSmall, color = Color.White)
        }
    }
}

@Composable
private fun EmptyState(onCreate: () -> Unit, onSample: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    GlassCard(Modifier.fillMaxWidth().padding(vertical = 8.dp), wash = true) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("FADE IN:", style = MaterialTheme.typography.headlineMedium.copy(fontFamily = ScriptFont, brush = LocalBrand.current.hero))
            Spacer(Modifier.height(10.dp))
            Text("Every film starts with page one.", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text("Start a new screenplay, open the sample to explore, or import a Fountain or Final Draft file.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.Button(onClick = onCreate) { Text("Start writing") }
                androidx.compose.material3.OutlinedButton(onClick = onSample) { Text("Open the sample") }
            }
        }
    }
}

@Composable
private fun NewScriptDialog(defaultAuthor: String, onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf(defaultAuthor) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New screenplay") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = author, onValueChange = { author = it }, label = { Text("Written by") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(title.trim().ifEmpty { "Untitled" }, author.trim()) }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
