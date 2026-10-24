package com.amit.pageone.core

data class SceneInfo(
    val elementIndex: Int,
    val endIndex: Int,          // exclusive
    val number: Int,
    val heading: String,
    val color: Int,
    val synopsis: String,
    val elementId: Long,
    val words: Int,
    val preview: String,        // first action text, for cards
)

data class CharacterStat(val name: String, val cues: Int, val words: Int, val scenes: Int)

data class ScriptStats(
    val words: Int,
    val scenes: Int,
    val interior: Int,
    val exterior: Int,
    val day: Int,
    val night: Int,
    val characters: List<CharacterStat>,
    val dialogueWords: Int,
    val actionWords: Int,
)

object Analysis {

    fun scenes(elements: List<Element>): List<SceneInfo> {
        val heads = elements.indices.filter { elements[it].type == ElementType.SCENE_HEADING }
        return heads.mapIndexed { n, start ->
            val end = if (n + 1 < heads.size) heads[n + 1] else elements.size
            var words = 0
            var preview = ""
            for (i in start + 1 until end) {
                val e = elements[i]
                if (e.type.printed) words += Words.count(e.text)
                if (preview.isEmpty() && e.type == ElementType.ACTION && e.text.isNotBlank()) preview = e.text
            }
            val h = elements[start]
            SceneInfo(start, end, n + 1, h.text, h.color, h.synopsis, h.id, words, preview)
        }
    }

    /** Index of the scene heading at or before [index], or -1. */
    fun sceneIndexFor(elements: List<Element>, index: Int): Int {
        var i = index.coerceAtMost(elements.size - 1)
        while (i >= 0) {
            if (elements[i].type == ElementType.SCENE_HEADING) return i
            i--
        }
        return -1
    }

    /** For each element: should a (CONT'D) be shown automatically after this character cue? */
    fun contd(elements: List<Element>): BooleanArray {
        val out = BooleanArray(elements.size)
        var last: String? = null
        for (i in elements.indices) {
            val e = elements[i]
            when (e.type) {
                ElementType.SCENE_HEADING -> last = null
                ElementType.CHARACTER -> {
                    val name = Formatting.characterName(e.text)
                    val explicit = e.text.contains("CONT'D", ignoreCase = true) || e.text.contains("CONT’D", ignoreCase = true)
                    if (name.isNotEmpty() && name == last && !explicit && !e.dual) out[i] = true
                    if (name.isNotEmpty()) last = name
                }
                else -> {}
            }
        }
        return out
    }

    /** Distinct character names, most frequent first. */
    fun characterNames(elements: List<Element>): List<String> =
        elements.asSequence()
            .filter { it.type == ElementType.CHARACTER }
            .map { Formatting.characterName(it.text) }
            .filter { it.isNotEmpty() }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
            .map { it.key }

    /** Distinct locations used in scene headings ("INT. KITCHEN - DAY" → "KITCHEN"). */
    fun locations(elements: List<Element>): List<String> =
        elements.asSequence()
            .filter { it.type == ElementType.SCENE_HEADING }
            .map { location(it.text) }
            .filter { it.isNotEmpty() }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
            .map { it.key }

    fun location(heading: String): String {
        var h = heading.trim().uppercase()
        h = h.replace(Regex("^(INT\\./EXT\\.|INT/EXT\\.?|I/E\\.?|INT\\.?|EXT\\.?|EST\\.?)\\s*"), "")
        val dash = h.lastIndexOf(" - ")
        if (dash >= 0) h = h.substring(0, dash)
        return h.trim()
    }

    fun stats(elements: List<Element>): ScriptStats {
        var words = 0; var dWords = 0; var aWords = 0
        var interior = 0; var exterior = 0; var day = 0; var night = 0; var scenes = 0
        data class Acc(var cues: Int = 0, var words: Int = 0, val scenes: MutableSet<Int> = mutableSetOf())
        val chars = LinkedHashMap<String, Acc>()
        var currentChar: String? = null
        var sceneNo = 0
        for (e in elements) {
            if (!e.type.printed) continue
            val w = Words.count(e.text)
            words += w
            when (e.type) {
                ElementType.SCENE_HEADING -> {
                    scenes++; sceneNo++
                    val u = e.text.uppercase()
                    if (u.startsWith("INT") || u.startsWith("I/E")) interior++
                    if (u.startsWith("EXT") || u.contains("/EXT") || u.startsWith("I/E")) exterior++
                    if (u.contains("NIGHT") || u.contains("EVENING") || u.contains("DUSK")) night++
                    else if (u.contains("DAY") || u.contains("MORNING") || u.contains("DAWN") || u.contains("AFTERNOON")) day++
                    currentChar = null
                }
                ElementType.CHARACTER -> {
                    val n = Formatting.characterName(e.text)
                    currentChar = n.ifEmpty { null }
                    if (n.isNotEmpty()) {
                        val a = chars.getOrPut(n) { Acc() }
                        a.cues++; a.scenes.add(sceneNo)
                    }
                }
                ElementType.DIALOGUE, ElementType.LYRICS -> {
                    dWords += w
                    currentChar?.let { chars[it]?.words = (chars[it]?.words ?: 0) + w }
                }
                ElementType.ACTION -> { aWords += w; currentChar = null }
                else -> {}
            }
        }
        val cs = chars.map { (k, v) -> CharacterStat(k, v.cues, v.words, v.scenes.size) }.sortedByDescending { it.words }
        return ScriptStats(words, scenes, interior, exterior, day, night, cs, dWords, aWords)
    }
}
