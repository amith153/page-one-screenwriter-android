package com.amit.pageone.ui.settings

import kotlinx.coroutines.launch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.amit.pageone.core.PaperSize
import com.amit.pageone.data.Accent
import com.amit.pageone.data.AppGraph
import com.amit.pageone.data.AppSettings
import com.amit.pageone.data.PaperLook
import com.amit.pageone.data.SceneNumbers
import com.amit.pageone.data.ThemeMode
import com.amit.pageone.data.DailyCard
import com.amit.pageone.data.FrameRatio
import com.amit.pageone.data.StillsSource
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: AppSettings, onBack: () -> Unit) {
    val upd: ((AppSettings) -> AppSettings) -> Unit = { AppGraph.settings.update(it) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val s = settings
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        LazyColumn(
            // imePadding: the list shrinks above the keyboard, so the field you're typing in scrolls into view.
            Modifier.padding(pad).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        ) {
            item {
                Section("Appearance") {
                    Choice("Theme", ThemeMode.entries, s.theme, { it.label }) { v -> upd { it.copy(theme = v) } }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S)
                        Toggle("Material You colours", "Use your wallpaper colours instead of an accent", s.dynamicColor) { v -> upd { it.copy(dynamicColor = v) } }
                    if (!s.dynamicColor) AccentPicker(s.accent) { v -> upd { it.copy(accent = v) } }
                    Choice("Paper", PaperLook.entries, s.paperLook, { it.label }) { v -> upd { it.copy(paperLook = v) } }
                }
            }
            item { DashboardSection(s, upd) }
            item {
                Section("Editor") {
                    SliderRow("Text size", "${(s.textScale * 100).toInt()}% — 100% shows true page line lengths", s.textScale, 0.8f..1.8f) { v -> upd { it.copy(textScale = (v * 20).toInt() / 20f) } }
                    Toggle("Page breaks", "Show where each page ends as you write", s.showPageBreaks) { v -> upd { it.copy(showPageBreaks = v) } }
                    Toggle("Scene numbers", "In the left margin of the editor", s.editorSceneNumbers) { v -> upd { it.copy(editorSceneNumbers = v) } }
                    Toggle("Bold scene headings", null, s.boldSceneHeadings) { v -> upd { it.copy(boldSceneHeadings = v) } }
                    Toggle("Automatic (CONT'D)", "When a character speaks again after action", s.showContd) { v -> upd { it.copy(showContd = v) } }
                    Toggle("Show revision marks", "Coloured asterisks next to revised lines", s.showRevisionMarks) { v -> upd { it.copy(showRevisionMarks = v) } }
                    Toggle("Typewriter scrolling", "Keep the line you're writing in the upper third", s.typewriterScrolling) { v -> upd { it.copy(typewriterScrolling = v) } }
                    Toggle("Dim in focus mode", "Fade everything except the current element", s.focusDim) { v -> upd { it.copy(focusDim = v) } }
                    Toggle("Keep screen on", "While a script is open", s.keepScreenOn) { v -> upd { it.copy(keepScreenOn = v) } }
                    Toggle("Haptic feedback", null, s.haptics) { v -> upd { it.copy(haptics = v) } }
                    Toggle("Spell check", "Red underline under misspelt words — tap one for suggestions", s.spellCheck) { v -> upd { it.copy(spellCheck = v) } }
                    if (s.spellCheck) SpellingRow()
                }
            }
            item {
                Section("Smart typing") {
                    Toggle("Smart element detection", "\"int. \" becomes a scene heading, \"CUT TO:\" a transition", s.smartTypeDetection) { v -> upd { it.copy(smartTypeDetection = v) } }
                    Toggle("Auto parentheses", "Wrap parentheticals in ( ) for you", s.autoParentheses) { v -> upd { it.copy(autoParentheses = v) } }
                    Toggle("Character suggestions", "Names, extensions and who speaks next", s.characterAutocomplete) { v -> upd { it.copy(characterAutocomplete = v) } }
                    Toggle("Scene heading suggestions", "INT./EXT., locations and times of day", s.sceneAutocomplete) { v -> upd { it.copy(sceneAutocomplete = v) } }
                }
            }
            item {
                Section("Page & PDF") {
                    Choice("Paper size", PaperSize.entries, s.paper, { it.label }) { v -> upd { it.copy(paper = v) } }
                    Choice("Space before scene headings", listOf(1, 2), s.sceneSpacing, { if (it == 1) "1 line" else "2 lines" }) { v -> upd { it.copy(sceneSpacing = v) } }
                    Toggle("(MORE) / (CONT'D) at page breaks", "Split long speeches across pages", s.moreContd) { v -> upd { it.copy(moreContd = v) } }
                    Choice("PDF scene numbers", SceneNumbers.entries, s.pdfSceneNumbers, { it.label }) { v -> upd { it.copy(pdfSceneNumbers = v) } }
                    Toggle("Underline scene headings", "In the PDF", s.underlineSceneHeadings) { v -> upd { it.copy(underlineSceneHeadings = v) } }
                    Toggle("Title page", "Include it when exporting PDF", s.pdfTitlePage) { v -> upd { it.copy(pdfTitlePage = v) } }
                    Toggle("Revision asterisks", "Mark revised lines in the PDF margin", s.pdfRevisionMarks) { v -> upd { it.copy(pdfRevisionMarks = v) } }
                    TextSetting("Watermark", "e.g. CONFIDENTIAL — leave empty for none", s.pdfWatermark) { v -> upd { it.copy(pdfWatermark = v) } }
                }
            }
            item {
                Section("Writing") {
                    NumberSetting("Daily word goal", "0 turns the goal off", s.dailyGoal) { v -> upd { it.copy(dailyGoal = v) } }
                    TextSetting("Default author", "Pre-fills “Written by” on new scripts", s.defaultAuthor) { v -> upd { it.copy(defaultAuthor = v) } }
                    Choice("Auto-save a version every", listOf(0, 10, 15, 30, 60), s.autoVersionMinutes, { if (it == 0) "Off" else "$it min" }) { v -> upd { it.copy(autoVersionMinutes = v) } }
                    Choice("Versions to keep", listOf(10, 30, 60, 100), s.versionsToKeep, { "$it" }) { v -> upd { it.copy(versionsToKeep = v) } }
                }
            }
            item { NotificationsSection(s, upd) }
            item { CloudSection() }
            item { SyncSection() }
            item { CollaborationSection() }
            item {
                Column(Modifier.padding(top = 24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Page One 3.0", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Courier Prime · Fountain · Final Draft · PDF", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.padding(top = 20.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = cs.primary, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
        Surface(shape = RoundedCornerShape(22.dp), color = cs.surfaceContainer, modifier = Modifier.border(1.dp, com.amit.pageone.ui.theme.LocalBrand.current.edge, RoundedCornerShape(22.dp))) {
            Column(Modifier.padding(vertical = 6.dp)) { content() }
        }
    }
}

@Composable
private fun Toggle(title: String, sub: String?, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun <T> Choice(title: String, options: List<T>, value: T, label: (T) -> String, onChange: (T) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { o ->
                val sel = o == value
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (sel) cs.primary else cs.surfaceContainerHighest)
                        .clickable { onChange(o) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(label(o), style = MaterialTheme.typography.labelLarge, color = if (sel) cs.onPrimary else cs.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun AccentPicker(value: Accent, onChange: (Accent) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
        Text("Accent", style = MaterialTheme.typography.bodyLarge)
        Text(value.label, style = MaterialTheme.typography.bodySmall, color = cs.primary)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Accent.entries.forEach { a ->
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(a.argb))
                        .border(3.dp, if (a == value) cs.onSurface else Color.Transparent, CircleShape)
                        .clickable { onChange(a) },
                    contentAlignment = Alignment.Center,
                ) { if (a == value) Icon(Icons.Filled.Check, a.label, tint = Color.Black, modifier = Modifier.size(18.dp)) }
            }
        }
    }
}

@Composable
private fun SliderRow(title: String, sub: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun TextSetting(title: String, hint: String, value: String, onChange: (String) -> Unit) {
    var t by remember(value) { mutableStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = t, onValueChange = { t = it; onChange(it) }, singleLine = true,
            placeholder = { Text(hint) }, shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun NumberSetting(title: String, hint: String, value: Int, onChange: (Int) -> Unit) {
    var t by remember(value) { mutableStateOf(if (value == 0) "" else value.toString()) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(
            value = t,
            onValueChange = { v -> t = v.filter { it.isDigit() }.take(5); onChange(t.toIntOrNull() ?: 0) },
            singleLine = true, placeholder = { Text("Off") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.width(110.dp),
        )
    }
}

/** Every reminder Page One can send, each with its own switch. */
@Composable
private fun NotificationsSection(s: AppSettings, upd: ((AppSettings) -> AppSettings) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val cs = MaterialTheme.colorScheme
    var allowed by remember { mutableStateOf(com.amit.pageone.data.Notifier.canPost(ctx)) }
    val ask = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {
        allowed = com.amit.pageone.data.Notifier.canPost(ctx); com.amit.pageone.data.Notifier.scheduleAll(ctx)
    }
    fun set(f: (AppSettings) -> AppSettings) {
        upd(f)
        if (!allowed && android.os.Build.VERSION.SDK_INT >= 33) ask.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        com.amit.pageone.data.Notifier.scheduleAll(ctx)
    }
    Section("Notifications") {
        if (!allowed) Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Notifications are off for Page One", style = MaterialTheme.typography.bodyMedium, color = cs.error, modifier = Modifier.weight(1f))
            androidx.compose.material3.TextButton(onClick = {
                if (android.os.Build.VERSION.SDK_INT >= 33) ask.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                else ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName))
            }) { Text("Turn on") }
        }
        Toggle("Daily writing reminder", "A nudge at your writing time — skipped once you've hit today's goal", s.dailyReminder) { v -> set { it.copy(dailyReminder = v) } }
        if (s.dailyReminder) {
            var picking by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth().clickable { picking = true }.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Remind me at", style = MaterialTheme.typography.bodyLarge)
                    Text("Tap to choose the time", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                Text(com.amit.pageone.ui.components.timeLabel(s.reminderHour, s.reminderMinute), style = MaterialTheme.typography.titleMedium, color = cs.primary)
            }
            if (picking) com.amit.pageone.ui.components.ReminderTimeDialog(s.reminderHour, s.reminderMinute, { picking = false }) { h, m ->
                picking = false
                set { it.copy(reminderHour = h, reminderMinute = m) }
            }
        }
        Toggle("Deadline reminders", "Every day while a script has a finish date: pages per day, ahead or behind, and countdowns at 2 weeks, 1 week, 3 days and the day before", s.deadlineReminders) { v -> set { it.copy(deadlineReminders = v) } }
        Toggle("Streak at risk", "8:30 pm, if you haven't written yet and a streak is going", s.streakAlerts) { v -> set { it.copy(streakAlerts = v) } }
        Toggle("Sprint bell", "When a writing sprint ends, even with the app closed", s.sprintAlerts) { v -> set { it.copy(sprintAlerts = v) } }
        Toggle("Idea inbox", "Sunday morning, if ideas are waiting to be used", s.inboxNudges) { v -> set { it.copy(inboxNudges = v) } }
        Toggle("Goal celebrations", "A cheer when you pass the daily goal", s.goalCelebrations) { v -> set { it.copy(goalCelebrations = v) } }
    }
}

/** Sync: pair this phone with the Page One desktop app (or another phone) over Wi-Fi. */
@Composable
private fun SyncSection() {
    val sync = AppGraph.sync
    val st by sync.state.collectAsState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    var address by remember { mutableStateOf("") }
    var typed by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    fun run(f: suspend () -> Unit) { scope.launch { busy = true; err = ""; try { f() } catch (e: Exception) { err = e.message ?: "Something went wrong" } finally { busy = false } } }
    Section("Sync") {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
            Text("Wi-Fi sync", style = MaterialTheme.typography.bodyLarge)
            Text("Your projects, daily words, streak, sprints and goal, the same on every device. Over your own Wi-Fi: no account, no server.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            if (st.peers.isNotEmpty()) {
                val label = when (st.phase) {
                    com.amit.pageone.collab.SyncSpace.Phase.ERROR -> st.message.ifBlank { "Something went wrong" }
                    com.amit.pageone.collab.SyncSpace.Phase.WORKING -> "Syncing…"
                    com.amit.pageone.collab.SyncSpace.Phase.WAITING -> st.message.ifBlank { "Waiting for your other device…" }
                    else -> if (st.lastAt > 0) "Up to date · " + android.text.format.DateUtils.getRelativeTimeSpanString(st.lastAt, System.currentTimeMillis(), android.text.format.DateUtils.SECOND_IN_MILLIS) else "Up to date"
                }
                Text(label, style = MaterialTheme.typography.bodyMedium, color = if (st.phase == com.amit.pageone.collab.SyncSpace.Phase.ERROR) cs.error else cs.primary)
                st.peers.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        androidx.compose.material3.TextButton(onClick = { sync.unpair(p.id) }) { Text("Unpair") }
                    }
                }
                androidx.compose.material3.OutlinedButton(onClick = { sync.syncNow() }) { Text("Sync now") }
                Spacer(Modifier.height(10.dp))
            }
            androidx.compose.material3.Button(onClick = { run { sync.startPairing() } }, enabled = !busy) { Text(if (st.peers.isEmpty()) "Show a code for my computer" else "Add another device") }
            st.pairing?.let { pr ->
                Spacer(Modifier.height(8.dp))
                Text(pr.code, style = MaterialTheme.typography.headlineSmall, color = cs.primary)
                Text("Address: " + pr.addresses.joinToString(" or ") + ". Keep Page One open here until it connects.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = address, onValueChange = { address = it }, singleLine = true, placeholder = { Text("Address, like 192.168.1.5") }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(value = typed, onValueChange = { typed = it }, singleLine = true, placeholder = { Text("Code, like K7QP-X2MA") }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            androidx.compose.material3.OutlinedButton(enabled = !busy && address.isNotBlank() && typed.isNotBlank(), onClick = { run { sync.pairWith(address, typed); address = ""; typed = "" } }) { Text("Use a code from my computer") }
            Text("Both devices must be on the same Wi-Fi (a phone's hotspot works).", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            if (err.isNotEmpty()) { Spacer(Modifier.height(6.dp)); Text(err, style = MaterialTheme.typography.bodySmall, color = cs.error) }
        }
    }
}

/** Collaboration: your display name. (The server panel exists only in debug builds — see src/debug.) */
@Composable
private fun CollaborationSection() {
    val collab = AppGraph.collab
    val name by collab.name.collectAsState()
    Section("Collaboration") {
        TextSetting("Your name", "Shown to co-writers next to your cursor", name) { collab.setName(it) }
        ServerPanel()
    }
}

/** Personal dictionary: words you've told spell check are fine. */
@Composable
private fun SpellingRow() {
    val words by AppGraph.spell.personal.collectAsState()
    val available = remember { AppGraph.spell.isAvailable() }
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text("Personal dictionary", style = MaterialTheme.typography.bodyLarge)
            Text(
                if (!available) "Using Page One's built-in dictionary (your phone's own spell checker is off)"
                else if (words.isEmpty()) "Words you add show up here. Character names are always skipped."
                else words.sorted().take(8).joinToString(", ") + if (words.size > 8) " … (${words.size})" else "",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
            )
        }
        if (words.isNotEmpty()) androidx.compose.material3.TextButton(onClick = { AppGraph.spell.clearPersonal() }) { Text("Clear") }
    }
}

/** The film-still / quote card at the top of the library. */
@Composable
private fun DashboardSection(s: AppSettings, upd: ((AppSettings) -> AppSettings) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val cs = MaterialTheme.colorScheme
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            upd { it.copy(stillsFolder = uri.toString(), stillsSource = StillsSource.FOLDER) }
        }
    }
    Section("Dashboard") {
        Choice("Daily card", DailyCard.entries, s.dailyCard, { it.label }) { v -> upd { it.copy(dailyCard = v) } }
        if (s.dailyCard != DailyCard.OFF) {
            Choice("Frame", FrameRatio.entries, s.frameRatio, { it.label }) { v -> upd { it.copy(frameRatio = v) } }
        }
        if (s.dailyCard == DailyCard.STILL || s.dailyCard == DailyCard.BOTH) {
            Choice("Stills from", StillsSource.entries, s.stillsSource, { it.label }) { v ->
                if (v == StillsSource.FOLDER && s.stillsFolder.isBlank()) picker.launch(null) else upd { it.copy(stillsSource = v) }
            }
            if (s.stillsSource == StillsSource.FOLDER) {
                val folderName = remember(s.stillsFolder) {
                    runCatching {
                        android.provider.DocumentsContract.getTreeDocumentId(android.net.Uri.parse(s.stillsFolder)).substringAfter(':').ifBlank { "Phone storage" }
                    }.getOrDefault("")
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Stills folder", style = MaterialTheme.typography.bodyLarge)
                        Text(folderName.ifBlank { "Not chosen" }, style = MaterialTheme.typography.bodySmall, color = cs.primary)
                    }
                    androidx.compose.material3.TextButton(onClick = { picker.launch(null) }) { Text(if (folderName.isBlank()) "Choose…" else "Change") }
                }
            }
            Text(
                if (s.stillsSource == StillsSource.FOLDER)
                    "Save frames you love from ShotDeck, FILMGRAB or your own screenshots into this folder — a different one shows every day. Name a file like “Blade Runner 2049 (2017).jpg” and that becomes its caption."
                else
                    "A frame from a different film every day — any era, country, language or genre, from Sholay and Seven Samurai to Parasite and Dune — with one of its famous lines. Tap the card to hide the text.",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            )
        }
    }
}
