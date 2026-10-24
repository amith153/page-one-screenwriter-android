package com.amit.pageone.core

/**
 * Behavioural rules of the editor: what Enter / Tab / Backspace do, and
 * "smart" detection of element types from what's typed.
 */
object Formatting {

    /** Element created when Enter is pressed at the end of an element of [type]. */
    fun nextOnEnter(type: ElementType): ElementType = when (type) {
        ElementType.SCENE_HEADING -> ElementType.ACTION
        ElementType.ACTION -> ElementType.ACTION
        ElementType.CHARACTER -> ElementType.DIALOGUE
        ElementType.PARENTHETICAL -> ElementType.DIALOGUE
        ElementType.DIALOGUE -> ElementType.CHARACTER
        ElementType.LYRICS -> ElementType.CHARACTER
        ElementType.TRANSITION -> ElementType.SCENE_HEADING
        ElementType.SHOT -> ElementType.ACTION
        ElementType.CENTERED -> ElementType.ACTION
        ElementType.NOTE -> ElementType.ACTION
    }

    /** Pressing Enter on an *empty* element changes its type instead of adding a row. */
    fun onEnterWhenEmpty(type: ElementType): ElementType? = when (type) {
        ElementType.CHARACTER -> ElementType.ACTION
        ElementType.DIALOGUE -> ElementType.ACTION
        ElementType.PARENTHETICAL -> ElementType.DIALOGUE
        ElementType.ACTION -> ElementType.CHARACTER
        ElementType.SCENE_HEADING -> ElementType.ACTION
        ElementType.TRANSITION -> ElementType.SCENE_HEADING
        else -> null
    }

    /** Tab cycles through the most useful types for the current one. */
    fun nextOnTab(type: ElementType): ElementType = when (type) {
        ElementType.ACTION -> ElementType.CHARACTER
        ElementType.CHARACTER -> ElementType.TRANSITION
        ElementType.TRANSITION -> ElementType.SCENE_HEADING
        ElementType.SCENE_HEADING -> ElementType.ACTION
        ElementType.DIALOGUE -> ElementType.PARENTHETICAL
        ElementType.PARENTHETICAL -> ElementType.DIALOGUE
        ElementType.SHOT -> ElementType.ACTION
        ElementType.LYRICS -> ElementType.DIALOGUE
        ElementType.CENTERED -> ElementType.ACTION
        ElementType.NOTE -> ElementType.ACTION
    }

    /** Backspace on an empty element demotes it before deleting it (Final Draft–style). */
    fun demote(type: ElementType): ElementType? = when (type) {
        ElementType.DIALOGUE -> ElementType.CHARACTER
        ElementType.PARENTHETICAL -> ElementType.DIALOGUE
        ElementType.LYRICS -> ElementType.DIALOGUE
        ElementType.CHARACTER -> ElementType.ACTION
        ElementType.TRANSITION, ElementType.SHOT, ElementType.CENTERED, ElementType.NOTE -> ElementType.ACTION
        ElementType.SCENE_HEADING, ElementType.ACTION -> null
    }

    private val sceneStart = Regex("^(INT|EXT|EST|INT\\./EXT|INT/EXT|I/E)[ .]", RegexOption.IGNORE_CASE)

    fun looksLikeSceneHeading(text: String): Boolean = sceneStart.containsMatchIn(text.trimStart())

    fun looksLikeTransition(text: String): Boolean {
        val t = text.trim()
        return t.isNotEmpty() && t == t.uppercase() && (t.endsWith("TO:") || t == "FADE OUT." || t == "FADE TO BLACK." || t == "FADE IN:")
    }

    /**
     * While typing in an Action line, detect when the writer has obviously started
     * a scene heading ("int. " / "ext. ") and return the type to switch to.
     */
    fun detectWhileTyping(type: ElementType, text: String): ElementType? {
        if (type == ElementType.ACTION && looksLikeSceneHeading(text) && text.length <= 12) return ElementType.SCENE_HEADING
        return null
    }

    /** On Enter, an all-caps action line that ends in "TO:" is really a transition. */
    fun detectOnEnter(type: ElementType, text: String): ElementType? {
        if (type == ElementType.ACTION && looksLikeTransition(text)) return ElementType.TRANSITION
        return null
    }

    /** Wrap parenthetical text in parentheses if the writer didn't. */
    fun normalizeParenthetical(text: String): String {
        val t = text.trim()
        if (t.isEmpty()) return text
        var r = t
        if (!r.startsWith("(")) r = "($r"
        if (!r.endsWith(")")) r = "$r)"
        return r
    }

    /** Character cue minus extensions: "JOHN (V.O.)" → "JOHN". */
    fun characterName(cue: String): String =
        cue.replace(Regex("\\(.*?\\)"), "").replace("^", "").trim().uppercase()

    val timesOfDay = listOf("DAY", "NIGHT", "CONTINUOUS", "LATER", "MOMENTS LATER", "MORNING", "EVENING", "DAWN", "DUSK", "SAME TIME")
    val scenePrefixes = listOf("INT. ", "EXT. ", "INT./EXT. ", "EST. ")
    val transitions = listOf("CUT TO:", "DISSOLVE TO:", "SMASH CUT TO:", "MATCH CUT TO:", "JUMP CUT TO:", "FADE OUT.", "FADE TO BLACK.", "FADE IN:")
    val extensions = listOf("(V.O.)", "(O.S.)", "(O.C.)", "(CONT'D)")
    val parentheticals = listOf("(beat)", "(pause)", "(quietly)", "(sotto)", "(re: ", "(into phone)", "(continuing)")
}
