package com.amit.pageone.data

import android.content.Context
import android.content.SharedPreferences
import com.amit.pageone.core.LayoutOptions
import com.amit.pageone.core.PaperSize
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark"), BLACK("Pure black") }

/**
 * Accents: quiet, considered shades (think hotel lobby, not toy box). Names of the enum entries
 * are stored in settings, so they stay the same even where the colour has been refined.
 */
enum class Accent(val label: String, val argb: Long) {
    MARQUEE("Champagne", 0xFFC8A96A),
    PEWTER("Pewter", 0xFF8E949C),
    TAUPE("Taupe", 0xFFA08F7D),
    EMBER("Terracotta", 0xFFB86A4B),
    ROSE("Blush", 0xFFC79A98),
    VELVET("Bordeaux", 0xFF8E2F45),
    VIOLET("Mauve", 0xFF94809E),
    MIDNIGHT("Slate", 0xFF6E7F99),
    OCEAN("Navy", 0xFF33507A),
    AURORA("Sage", 0xFF8DA38A),
    FOREST("Emerald", 0xFF2F6F5B),
    GOLD("Bronze", 0xFF9C7A4D),
}

enum class PaperLook(val label: String) { WHITE("White paper"), MATCH("Match theme"), SEPIA("Warm sepia") }

enum class SceneNumbers(val label: String) { NONE("Off"), LEFT("Left"), RIGHT("Right"), BOTH("Both sides") }

/** What the dashboard's daily card shows. */
enum class DailyCard(val label: String) { BOTH("Still + quote"), STILL("Film still"), QUOTE("Quote"), OFF("Off") }

/** Where the daily film still comes from. */
enum class StillsSource(val label: String) { CINEMA("All cinema"), FOLDER("My stills folder") }

/** Shape of the daily card — the classic projection ratios. */
enum class FrameRatio(val label: String, val ratio: Float) { SCOPE("2.39 : 1", 2.39f), FLAT("1.85 : 1", 1.85f), WIDE("16 : 9", 16f / 9f) }

data class AppSettings(
    // Appearance
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val accent: Accent = Accent.MARQUEE,
    val paperLook: PaperLook = PaperLook.MATCH,
    // Editor
    val textScale: Float = 1.15f,
    val showPageBreaks: Boolean = true,
    val editorSceneNumbers: Boolean = true,
    val showContd: Boolean = true,
    val showRevisionMarks: Boolean = true,
    val smartTypeDetection: Boolean = true,
    val autoParentheses: Boolean = true,
    val characterAutocomplete: Boolean = true,
    val sceneAutocomplete: Boolean = true,
    val typewriterScrolling: Boolean = false,
    val focusDim: Boolean = true,
    val keepScreenOn: Boolean = true,
    val haptics: Boolean = true,
    val spellCheck: Boolean = true,
    // Dashboard
    val dailyCard: DailyCard = DailyCard.BOTH,
    val stillsSource: StillsSource = StillsSource.CINEMA,
    val stillsFolder: String = "",
    val frameRatio: FrameRatio = FrameRatio.SCOPE,
    // Page & PDF
    val paper: PaperSize = PaperSize.LETTER,
    val sceneSpacing: Int = 1,
    val boldSceneHeadings: Boolean = true,
    val underlineSceneHeadings: Boolean = false,
    val moreContd: Boolean = true,
    val pdfSceneNumbers: SceneNumbers = SceneNumbers.BOTH,
    val pdfTitlePage: Boolean = true,
    val pdfRevisionMarks: Boolean = true,
    val pdfColoredPages: Boolean = false,
    val pdfWatermark: String = "",
    // Writing
    val dailyGoal: Int = 0,
    val autoVersionMinutes: Int = 15,
    val versionsToKeep: Int = 30,
    val defaultAuthor: String = "",
    // Notifications
    /** A coloured bar beside each line, by who last changed it. */
    val showAuthors: Boolean = false,
    val dailyReminder: Boolean = false,
    val reminderHour: Int = 19,
    val reminderMinute: Int = 0,
    val deadlineReminders: Boolean = true,
    val streakAlerts: Boolean = true,
    val inboxNudges: Boolean = true,
    val sprintAlerts: Boolean = true,
    val goalCelebrations: Boolean = true,
    // Table read
    val ttsRate: Float = 1.0f,
    val readParentheticals: Boolean = false,
    val readHeadings: Boolean = true,
    val readTransitions: Boolean = false,
    /** Natural cloud voices (Google Cloud Text-to-Speech) using the writer's own API key. */
    val studioVoices: Boolean = false,
    val cloudTtsKey: String = "",
    /** Accent for English lines: en-IN, en-US or en-GB. */
    val englishAccent: String = "en-IN",
    // First run
    val onboarded: Boolean = false,
) {
    val layout: LayoutOptions get() = LayoutOptions(paper, sceneSpacing, showContd, moreContd)
}

/** SharedPreferences-backed settings exposed as a StateFlow. */
class SettingsStore(context: Context) {
    private val p: SharedPreferences = context.getSharedPreferences("pageone_settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    /** Sync listens here: the daily goal or accent changed. */
    var onPrefsChanged: (() -> Unit)? = null

    fun update(f: (AppSettings) -> AppSettings) {
        val old = _state.value
        val n = f(old)
        _state.value = n
        write(n)
        if (n.dailyGoal != old.dailyGoal || n.accent != old.accent) onPrefsChanged?.invoke()
    }

    private inline fun <reified E : Enum<E>> en(key: String, def: E): E =
        runCatching { enumValueOf<E>(p.getString(key, def.name)!!) }.getOrDefault(def)

    private fun read(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            theme = en("theme", d.theme),
            dynamicColor = p.getBoolean("dynamicColor", d.dynamicColor),
            accent = en("accent", d.accent),
            paperLook = en("paperLook", d.paperLook),
            textScale = p.getFloat("textScale", d.textScale),
            showPageBreaks = p.getBoolean("showPageBreaks", d.showPageBreaks),
            editorSceneNumbers = p.getBoolean("editorSceneNumbers", d.editorSceneNumbers),
            showContd = p.getBoolean("showContd", d.showContd),
            showRevisionMarks = p.getBoolean("showRevisionMarks", d.showRevisionMarks),
            smartTypeDetection = p.getBoolean("smartTypeDetection", d.smartTypeDetection),
            autoParentheses = p.getBoolean("autoParentheses", d.autoParentheses),
            characterAutocomplete = p.getBoolean("characterAutocomplete", d.characterAutocomplete),
            sceneAutocomplete = p.getBoolean("sceneAutocomplete", d.sceneAutocomplete),
            typewriterScrolling = p.getBoolean("typewriterScrolling", d.typewriterScrolling),
            focusDim = p.getBoolean("focusDim", d.focusDim),
            keepScreenOn = p.getBoolean("keepScreenOn", d.keepScreenOn),
            haptics = p.getBoolean("haptics", d.haptics),
            spellCheck = p.getBoolean("spellCheck", d.spellCheck),
            dailyCard = en("dailyCard", d.dailyCard),
            stillsSource = en("stillsSource", d.stillsSource),
            stillsFolder = p.getString("stillsFolder", d.stillsFolder) ?: "",
            frameRatio = en("frameRatio", d.frameRatio),
            paper = en("paper", d.paper),
            sceneSpacing = p.getInt("sceneSpacing", d.sceneSpacing),
            boldSceneHeadings = p.getBoolean("boldSceneHeadings", d.boldSceneHeadings),
            underlineSceneHeadings = p.getBoolean("underlineSceneHeadings", d.underlineSceneHeadings),
            moreContd = p.getBoolean("moreContd", d.moreContd),
            pdfSceneNumbers = en("pdfSceneNumbers", d.pdfSceneNumbers),
            pdfTitlePage = p.getBoolean("pdfTitlePage", d.pdfTitlePage),
            pdfRevisionMarks = p.getBoolean("pdfRevisionMarks", d.pdfRevisionMarks),
            pdfColoredPages = p.getBoolean("pdfColoredPages", d.pdfColoredPages),
            pdfWatermark = p.getString("pdfWatermark", d.pdfWatermark) ?: "",
            dailyGoal = p.getInt("dailyGoal", d.dailyGoal),
            autoVersionMinutes = p.getInt("autoVersionMinutes", d.autoVersionMinutes),
            versionsToKeep = p.getInt("versionsToKeep", d.versionsToKeep),
            defaultAuthor = p.getString("defaultAuthor", d.defaultAuthor) ?: "",
            showAuthors = p.getBoolean("showAuthors", d.showAuthors),
            dailyReminder = p.getBoolean("dailyReminder", d.dailyReminder),
            reminderHour = p.getInt("reminderHour", d.reminderHour),
            reminderMinute = p.getInt("reminderMinute", d.reminderMinute),
            deadlineReminders = p.getBoolean("deadlineReminders", d.deadlineReminders),
            streakAlerts = p.getBoolean("streakAlerts", d.streakAlerts),
            inboxNudges = p.getBoolean("inboxNudges", d.inboxNudges),
            sprintAlerts = p.getBoolean("sprintAlerts", d.sprintAlerts),
            goalCelebrations = p.getBoolean("goalCelebrations", d.goalCelebrations),
            ttsRate = p.getFloat("ttsRate", d.ttsRate),
            readParentheticals = p.getBoolean("readParentheticals", d.readParentheticals),
            readHeadings = p.getBoolean("readHeadings", d.readHeadings),
            readTransitions = p.getBoolean("readTransitions", d.readTransitions),
            studioVoices = p.getBoolean("studioVoices", d.studioVoices),
            cloudTtsKey = p.getString("cloudTtsKey", d.cloudTtsKey) ?: "",
            englishAccent = p.getString("englishAccent", d.englishAccent) ?: d.englishAccent,
            // Existing users (who already have scripts/settings) skip the first-run tour.
            onboarded = p.getBoolean("onboarded", p.contains("theme")),
        )
    }

    private fun write(s: AppSettings) {
        p.edit()
            .putString("theme", s.theme.name)
            .putBoolean("dynamicColor", s.dynamicColor)
            .putString("accent", s.accent.name)
            .putString("paperLook", s.paperLook.name)
            .putFloat("textScale", s.textScale)
            .putBoolean("showPageBreaks", s.showPageBreaks)
            .putBoolean("editorSceneNumbers", s.editorSceneNumbers)
            .putBoolean("showContd", s.showContd)
            .putBoolean("showRevisionMarks", s.showRevisionMarks)
            .putBoolean("smartTypeDetection", s.smartTypeDetection)
            .putBoolean("autoParentheses", s.autoParentheses)
            .putBoolean("characterAutocomplete", s.characterAutocomplete)
            .putBoolean("sceneAutocomplete", s.sceneAutocomplete)
            .putBoolean("typewriterScrolling", s.typewriterScrolling)
            .putBoolean("focusDim", s.focusDim)
            .putBoolean("keepScreenOn", s.keepScreenOn)
            .putBoolean("haptics", s.haptics)
            .putBoolean("spellCheck", s.spellCheck)
            .putString("dailyCard", s.dailyCard.name)
            .putString("stillsSource", s.stillsSource.name)
            .putString("stillsFolder", s.stillsFolder)
            .putString("frameRatio", s.frameRatio.name)
            .putString("paper", s.paper.name)
            .putInt("sceneSpacing", s.sceneSpacing)
            .putBoolean("boldSceneHeadings", s.boldSceneHeadings)
            .putBoolean("underlineSceneHeadings", s.underlineSceneHeadings)
            .putBoolean("moreContd", s.moreContd)
            .putString("pdfSceneNumbers", s.pdfSceneNumbers.name)
            .putBoolean("pdfTitlePage", s.pdfTitlePage)
            .putBoolean("pdfRevisionMarks", s.pdfRevisionMarks)
            .putBoolean("pdfColoredPages", s.pdfColoredPages)
            .putString("pdfWatermark", s.pdfWatermark)
            .putInt("dailyGoal", s.dailyGoal)
            .putInt("autoVersionMinutes", s.autoVersionMinutes)
            .putInt("versionsToKeep", s.versionsToKeep)
            .putString("defaultAuthor", s.defaultAuthor)
            .putBoolean("showAuthors", s.showAuthors)
            .putBoolean("dailyReminder", s.dailyReminder)
            .putInt("reminderHour", s.reminderHour)
            .putInt("reminderMinute", s.reminderMinute)
            .putBoolean("deadlineReminders", s.deadlineReminders)
            .putBoolean("streakAlerts", s.streakAlerts)
            .putBoolean("inboxNudges", s.inboxNudges)
            .putBoolean("sprintAlerts", s.sprintAlerts)
            .putBoolean("goalCelebrations", s.goalCelebrations)
            .putFloat("ttsRate", s.ttsRate)
            .putBoolean("readParentheticals", s.readParentheticals)
            .putBoolean("readHeadings", s.readHeadings)
            .putBoolean("readTransitions", s.readTransitions)
            .putBoolean("studioVoices", s.studioVoices)
            .putString("cloudTtsKey", s.cloudTtsKey)
            .putString("englishAccent", s.englishAccent)
            .putBoolean("onboarded", s.onboarded)
            .apply()
    }
}

/** Tracks words written per day for the daily goal. */
class GoalTracker(context: Context) {
    private val p = context.getSharedPreferences("pageone_goals", Context.MODE_PRIVATE)
    private fun today(): String = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
    /** Words your other devices (the desktop app) wrote, per day, from sync. Counted with your own. */
    private val others = HashMap<String, Int>().apply {
        p.all.forEach { (k, v) -> if (k.startsWith("o_") && v is Int) put(k.removePrefix("o_"), v) }
    }
    private fun total(day: String) = p.getInt(day, 0) + (others[day] ?: 0)
    private val _today = MutableStateFlow(total(today()))
    val todayWords: StateFlow<Int> = _today.asStateFlow()

    /** Sync listens here: the words this phone wrote on a day changed. */
    var onDay: ((String, Int) -> Unit)? = null

    private val dayKey = Regex("""\d{4}-\d{2}-\d{2}""")
    /** What this phone wrote itself, per day (for sync). */
    fun ownDays(): Map<String, Int> = p.all.filter { (k, v) -> dayKey.matches(k) && v is Int }.mapValues { it.value as Int }

    fun setOthers(m: Map<String, Int>) {
        val e = p.edit()
        p.all.keys.filter { it.startsWith("o_") }.forEach { e.remove(it) }
        m.forEach { (k, v) -> e.putInt("o_$k", v) }
        e.apply()
        others.clear(); others.putAll(m)
        _today.value = total(today())
    }

    /** Told about every word written (sprints count from this). */
    val listeners = java.util.concurrent.CopyOnWriteArrayList<(Int) -> Unit>()

    fun add(delta: Int) {
        if (delta <= 0) return
        val k = today()
        val v = p.getInt(k, 0) + delta
        p.edit().putInt(k, v).apply()
        _today.value = total(k)
        listeners.forEach { runCatching { it(delta) } }
        onDay?.invoke(k, v)
    }

    /** Words written today, read fresh (for background reminders). */
    fun todayFresh(): Int = total(today())

    /** Words per day for the last [days] days, oldest first. */
    fun history(days: Int): List<Pair<String, Int>> {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val cal = java.util.Calendar.getInstance()
        val out = ArrayList<Pair<String, Int>>()
        for (i in 0 until days) {
            val k = fmt.format(cal.time)
            out.add(k to total(k))
            cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        }
        return out.reversed()
    }

    fun streak(): Int {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val cal = java.util.Calendar.getInstance()
        var n = 0
        if (total(fmt.format(cal.time)) == 0) cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        while (total(fmt.format(cal.time)) > 0) { n++; cal.add(java.util.Calendar.DAY_OF_YEAR, -1) }
        return n
    }
}

/** Simple service locator. */

object AppGraph {
    lateinit var store: ScriptStore
    lateinit var settings: SettingsStore
    lateinit var goals: GoalTracker
    lateinit var collab: CollabStore
    lateinit var drive: GoogleDrive
    lateinit var links: LinkedCopies
    lateinit var spell: SpellChecker
    lateinit var doctor: DoctorIgnores
    lateinit var ideas: IdeaStore
    lateinit var sprints: SprintManager
    lateinit var media: MediaFiles
    lateinit var context: Context
    lateinit var sync: com.amit.pageone.collab.SyncSpace
    /** The script open in the editor right now, if any (sync leaves it alone). */
    @Volatile var openScriptId: String? = null
    private var ready = false

    /** Outlives screens/ViewModels so final saves are never cancelled. */
    val appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    /** Called when the app goes to the background — editors flush unsaved work. */
    val stopHooks = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()
    fun onAppStop() {
        stopHooks.forEach { it() }
        runCatching { com.amit.pageone.widget.Widgets.refresh(context) }
    }

    @Synchronized fun init(context: Context) {
        if (ready) return
        val app = context.applicationContext
        store = ScriptStore(app)
        settings = SettingsStore(app)
        goals = GoalTracker(app)
        collab = CollabStore(app)
        drive = GoogleDrive(app)
        links = LinkedCopies(app)
        spell = SpellChecker(app)
        doctor = DoctorIgnores(app)
        ideas = IdeaStore(app)
        sprints = SprintManager(app, goals)
        media = MediaFiles(app)
        sync = com.amit.pageone.collab.SyncSpace(app)
        Notifier.createChannels(app)
        appScope.launch { spell.warmUp() }
        this.context = app
        sync.init()
        ready = true
    }
}
