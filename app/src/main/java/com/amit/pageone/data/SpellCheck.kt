package com.amit.pageone.data

import android.content.Context
import android.os.Build
import android.view.textservice.SentenceSuggestionsInfo
import android.view.textservice.SpellCheckerSession
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import android.view.textservice.TextServicesManager
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream
import kotlin.coroutines.resume

/** A misspelt word inside one element's text. */
data class Misspelling(val start: Int, val end: Int, val word: String)

/**
 * Spell check that gives the same answer every time.
 *
 * The phone's own spell checker (Gboard, Samsung…) is inconsistent when asked about
 * single words — it flags some typos and silently passes others. So Page One decides
 * with its own built-in English dictionary (~160,000 words from film & TV dialogue —
 * slang like "gonna", "y'all" included — plus British spellings), and only asks
 * the phone's checker as a second opinion for words the dictionary doesn't know
 * (names, places, words you've taught your keyboard).
 */
class SpellChecker(private val context: Context) {
    private val p = context.getSharedPreferences("pageone_dictionary", Context.MODE_PRIVATE)
    private val _personal = MutableStateFlow(p.getStringSet("words", emptySet())!!.toSet())
    /** Words you added ("Add to dictionary"), lower-case. */
    val personal: StateFlow<Set<String>> = _personal.asStateFlow()

    private val _ignored = MutableStateFlow<Set<String>>(emptySet())
    /** Words ignored for this run of the app only. */
    val ignored: StateFlow<Set<String>> = _ignored.asStateFlow()

    // ------------------------------------------------------------------ built-in dictionary

    @Volatile private var dict: HashMap<String, Int>? = null
    private val dictLock = Any()

    /** word → frequency score (higher = more common). Loaded once, off the main thread. */
    private fun dictionary(): HashMap<String, Int> {
        dict?.let { return it }
        synchronized(dictLock) {
            dict?.let { return it }
            val m = HashMap<String, Int>(240_000)
            runCatching {
                context.assets.open("spell/en_words.dat").use { raw ->
                    GZIPInputStream(raw).bufferedReader().forEachLine { line ->
                        val tab = line.indexOf('\t')
                        if (tab > 0) m[line.substring(0, tab)] = line.substring(tab + 1).toIntOrNull() ?: 1
                    }
                }
            }
            dict = m
            return m
        }
    }

    /** Load the dictionary in the background so the first check is instant. */
    suspend fun warmUp() = withContext(Dispatchers.IO) { dictionary(); Unit }

    private fun norm(w: String) = w.lowercase().replace('’', '\'')

    private fun inDictionary(word: String): Boolean {
        val d = dictionary()
        val l = norm(word)
        if (l in d) return true
        if (l.endsWith("'s") && l.dropLast(2) in d) return true // possessives: "Maya's"
        if (l.endsWith("s'") && l.dropLast(1) in d) return true // "the dogs'"
        if (l.endsWith("'ll") || l.endsWith("'ve") || l.endsWith("'re") || l.endsWith("'d")) {
            if (l.substringBefore('\'') in d) return true
        }
        return false
    }

    // ------------------------------------------------------------------ phone checker (second opinion)

    private val systemKnows = ConcurrentHashMap<String, Boolean>()
    private val systemSuggestions = ConcurrentHashMap<String, List<String>>()
    private var session: SpellCheckerSession? = null
    private var pending: CancellableContinuation<Array<SentenceSuggestionsInfo>?>? = null
    private val mutex = Mutex()

    private val listener = object : SpellCheckerSession.SpellCheckerSessionListener {
        override fun onGetSuggestions(results: Array<SuggestionsInfo>?) {}
        override fun onGetSentenceSuggestions(results: Array<SentenceSuggestionsInfo>?) {
            val c = pending ?: return
            pending = null
            if (c.isActive) c.resume(results)
        }
    }

    private fun tsm(): TextServicesManager? = context.getSystemService(Context.TEXT_SERVICES_MANAGER_SERVICE) as? TextServicesManager

    /** Whether the phone's own checker is on (Page One's dictionary works either way). */
    fun isAvailable(): Boolean {
        val t = tsm() ?: return false
        if (Build.VERSION.SDK_INT >= 31 && !t.isSpellCheckerEnabled) return false
        return true
    }

    private fun session(): SpellCheckerSession? {
        session?.let { return it }
        val s = runCatching { tsm()?.newSpellCheckerSession(null, null, listener, true) }.getOrNull()
        session = s
        return s
    }

    /** Asks the phone about words our dictionary doesn't know. Must run on the main thread. */
    private suspend fun askSystem(words: List<String>) = withContext(Dispatchers.Main) {
        mutex.withLock {
            val todo = words.filter { !systemKnows.containsKey(it) }
            if (todo.isEmpty()) return@withLock
            val s = session()
            if (s == null) { todo.forEach { systemKnows[it] = false }; return@withLock }
            val infos = todo.mapIndexed { i, w -> TextInfo(w, 0, w.length, 0, i) }.toTypedArray()
            val res = withTimeoutOrNull(2500) {
                suspendCancellableCoroutine<Array<SentenceSuggestionsInfo>?> { c ->
                    pending = c
                    c.invokeOnCancellation { if (pending === c) pending = null }
                    runCatching { s.getSentenceSuggestions(infos, 5) }.onFailure { pending = null; c.resume(null) }
                }
            }
            todo.forEachIndexed { i, w ->
                val r = res?.getOrNull(i)
                var known = false
                val sugg = ArrayList<String>()
                if (r != null) for (j in 0 until r.suggestionsCount) {
                    val si = r.getSuggestionsInfoAt(j) ?: continue
                    if (si.suggestionsAttributes and SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY != 0) known = true
                    for (k in 0 until si.suggestionsCount.coerceAtLeast(0)) si.getSuggestionAt(k)?.let { sugg += it }
                }
                if (res != null) systemKnows[w] = known // on timeout, ask again next time
                if (sugg.isNotEmpty()) systemSuggestions[w] = sugg
            }
        }
    }

    // ------------------------------------------------------------------ public API

    fun addToDictionary(word: String) {
        val n = _personal.value + norm(word)
        p.edit().putStringSet("words", n).apply()
        _personal.value = n
    }

    fun ignore(word: String) { _ignored.value = _ignored.value + norm(word) }

    fun clearPersonal() {
        p.edit().remove("words").apply()
        _personal.value = emptySet()
    }

    private fun skip(word: String, extra: Set<String>): Boolean {
        if (word.length < 2) return true
        if (word.any { it.isDigit() }) return true
        val l = norm(word)
        return l in _personal.value || l in _ignored.value || l in extra || l.substringBefore('\'') in extra
    }

    /**
     * Misspelt words in [text]. [skipWords] are lower-case words to treat as correct
     * (the script's character names, for instance).
     */
    suspend fun check(text: String, skipWords: Set<String> = emptySet()): List<Misspelling> {
        if (text.isBlank()) return emptyList()
        val words = WORD.findAll(text).map { m ->
            val w = m.value.trimEnd('\'', '’')
            Misspelling(m.range.first, m.range.first + w.length, w)
        }.filter { !skip(it.word, skipWords) }.toList()
        if (words.isEmpty()) return emptyList()
        val unknown = withContext(Dispatchers.Default) { words.filter { !inDictionary(it.word) } }
        if (unknown.isEmpty()) return emptyList()
        // The phone's checker is only trusted for Capitalised words (names, places, brands) —
        // keyboards "learn" whatever you type, so for ordinary words it would pass gibberish.
        val names = unknown.filter { isProperNounShape(it.word) }
        if (names.isNotEmpty()) askSystem(names.map { it.word }.distinct().take(60))
        return unknown.filter { !(isProperNounShape(it.word) && systemKnows[it.word] == true) }
    }

    /** "Mumbai" or "MUMBAI" — could be a name the phone knows. "mumbai" is just a word. */
    private fun isProperNounShape(w: String) = w.first().isUpperCase()

    /** Up to five corrections, most likely first. */
    suspend fun suggestionsFor(word: String): List<String> = withContext(Dispatchers.Default) {
        val d = dictionary()
        val l = norm(word)
        val own = edits1(l).filter { it in d }.distinct().sortedByDescending { d[it] }.take(5).ifEmpty {
            if (l.length > 12) emptyList()
            else edits1(l).flatMap { edits1(it) }.filter { it in d }.distinct().sortedByDescending { d[it] }.take(5)
        }
        val sys = systemSuggestions[word].orEmpty().map { norm(it) }
        (own.take(3) + sys + own.drop(3)).distinct().filter { it != l }.take(5)
    }

    private fun edits1(w: String): List<String> {
        val out = ArrayList<String>(w.length * 60)
        for (i in 0..w.length) {
            val a = w.substring(0, i); val b = w.substring(i)
            if (b.isNotEmpty()) out += a + b.substring(1)                                   // delete
            if (b.length > 1) out += a + b[1] + b[0] + b.substring(2)                       // transpose
            for (c in ALPHABET) {
                if (b.isNotEmpty()) out += a + c + b.substring(1)                           // replace
                out += a + c + b                                                            // insert
            }
        }
        return out
    }

    companion object {
        private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz'"
        private val WORD = Regex("[\\p{L}]+(?:['’][\\p{L}]+)*['’]?")

        /** Element types worth spell checking (not headings, cues, transitions or shots). */
        val CHECKED = setOf(
            com.amit.pageone.core.ElementType.SCENE_HEADING,
            com.amit.pageone.core.ElementType.ACTION,
            com.amit.pageone.core.ElementType.DIALOGUE,
            com.amit.pageone.core.ElementType.PARENTHETICAL,
            com.amit.pageone.core.ElementType.LYRICS,
            com.amit.pageone.core.ElementType.CENTERED,
            com.amit.pageone.core.ElementType.NOTE,
            com.amit.pageone.core.ElementType.TRANSITION,
            com.amit.pageone.core.ElementType.SHOT,
        )

        /** Match the case of the word being replaced: "teh" → "the", "Teh" → "The". */
        fun matchCase(original: String, replacement: String): String = when {
            original.length > 1 && original.all { !it.isLetter() || it.isUpperCase() } -> replacement.uppercase()
            original.firstOrNull()?.isUpperCase() == true -> replacement.replaceFirstChar { it.uppercase() }
            else -> replacement
        }
    }
}
