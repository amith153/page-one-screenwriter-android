package com.amit.pageone.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptDoctorTest {

    private var nextId = 1L
    private fun el(type: ElementType, text: String) = Element(nextId++, type, text)
    private fun h(t: String) = el(ElementType.SCENE_HEADING, t)
    private fun a(t: String) = el(ElementType.ACTION, t)
    private fun c(t: String) = el(ElementType.CHARACTER, t)
    private fun d(t: String) = el(ElementType.DIALOGUE, t)
    private fun p(t: String) = el(ElementType.PARENTHETICAL, t)
    private fun tr(t: String) = el(ElementType.TRANSITION, t)

    private val goodTitle = TitlePage(title = "Test", credits = listOf(Credit("Written by", "Amit")), email = "a@b.c")
    private fun script(vararg els: Element) = Script("s", "Test", els.toList(), goodTitle)
    private fun rules(s: Script) = ScriptDoctor.check(s).map { it.rule }
    private val ids = { nextId++ }

    @Test fun cleanScriptHasNoIssues() {
        val s = script(
            h("INT. KITCHEN - DAY"),
            a("Maya pours coffee."),
            c("MAYA"), p("(quietly)"), d("Morning."),
            tr("CUT TO:"),
            h("EXT. STREET - NIGHT"),
            a("Rain."),
        )
        assertEquals(emptyList<Rule>(), rules(s))
    }

    @Test fun cueWithoutDialogue() {
        val s = script(h("INT. ROOM - DAY"), c("MAYA"), a("She leaves."), a("End."))
        val issue = ScriptDoctor.check(s).single { it.rule == Rule.CUE_WITHOUT_DIALOGUE }
        assertEquals(listOf("Make the next line dialogue", "Make it action"), issue.fixes.map { it.label })
        val fixed = issue.fixes[0].apply(s.elements, ids)
        assertEquals(ElementType.DIALOGUE, fixed[2].type)
        assertFalse(Rule.CUE_WITHOUT_DIALOGUE in rules(s.copy(elements = fixed)))
    }

    @Test fun cueBeingTypedAtTheEndIsNotFlagged() {
        val s = script(h("INT. ROOM - DAY"), a("Quiet."), c("MAYA"))
        assertFalse(Rule.CUE_WITHOUT_DIALOGUE in rules(s))
    }

    @Test fun orphanDialogue() {
        val s = script(h("INT. ROOM - DAY"), a("Quiet."), d("Hello?"))
        assertTrue(Rule.ORPHAN_DIALOGUE in rules(s))
    }

    @Test fun parentheticalEndingASpeechBecomesAction() {
        val s = script(h("INT. ROOM - DAY"), c("MAYA"), d("Fine."), p("(she walks out)"), a("Silence."))
        val issue = ScriptDoctor.check(s).single { it.rule == Rule.PAREN_ENDS_SPEECH }
        val fixed = issue.fixes.single().apply(s.elements, ids)
        assertEquals(ElementType.ACTION, fixed[3].type)
        assertEquals("She walks out.", fixed[3].text)
    }

    @Test fun characterSpellingVariants() {
        val els = mutableListOf(h("INT. ROOM - DAY"))
        repeat(9) { els += c("MAYA"); els += d("Line $it.") }
        els += c("MAYAH (V.O.)"); els += d("Typo.")
        els += c("DR. SMITH"); els += d("Hi."); els += c("DR SMITH"); els += d("Hi.")
        val s = script(*els.toTypedArray())
        val issues = ScriptDoctor.check(s).filter { it.rule == Rule.CHARACTER_VARIANT }
        assertEquals(setOf("MAYAH", "DR SMITH"), issues.map { it.subject }.toSet())

        val maya = issues.single { it.subject == "MAYAH" }
        val fixed = maya.fixes.single().apply(s.elements, ids)
        assertTrue(fixed.any { it.text == "MAYA (V.O.)" })
        assertTrue(fixed.none { it.text.startsWith("MAYAH") })
    }

    @Test fun differentPeopleWithSimilarNamesAreNotMerged() {
        // Both used often: JOHN and JOAN are two people, not a typo.
        val els = mutableListOf(h("INT. ROOM - DAY"))
        repeat(5) { els += c("JOHN"); els += d("Hi."); els += c("JOAN"); els += d("Hi.") }
        assertFalse(Rule.CHARACTER_VARIANT in rules(script(*els.toTypedArray())))
        // Short names are never compared letter by letter.
        assertTrue(ScriptDoctor.variants(mapOf("TOM" to 20, "TIM" to 1), 4).isEmpty())
    }

    @Test fun locationVariants() {
        val s = script(
            h("INT. JOHN'S APARTMENT - DAY"), a("x."),
            h("INT. JOHN'S APARTMENT - NIGHT"), a("x."),
            h("INT. JOHNS APARTMENT - NIGHT"), a("x."),
        )
        val issue = ScriptDoctor.check(s).single { it.rule == Rule.LOCATION_VARIANT }
        val fixed = issue.fixes.single().apply(s.elements, ids)
        assertEquals("INT. JOHN'S APARTMENT - NIGHT", fixed[4].text)
    }

    @Test fun headingChecks() {
        val s = script(h("KITCHEN"), a("x."), h("INT HALL - DAY"), a("x."), h("EXT. GARDEN"), a("x."), h("MONTAGE"), a("x."))
        val r = rules(s)
        assertTrue(Rule.HEADING_NO_PREFIX in r)
        assertTrue(Rule.HEADING_PREFIX_FORMAT in r)
        assertTrue(Rule.HEADING_NO_TIME in r)
        assertEquals(1, r.count { it == Rule.HEADING_NO_PREFIX }) // MONTAGE is fine

        val noTime = ScriptDoctor.check(s).single { it.rule == Rule.HEADING_NO_TIME }
        assertEquals("EXT. GARDEN - NIGHT", noTime.fixes[1].apply(s.elements, ids)[4].text)
        val period = ScriptDoctor.check(s).single { it.rule == Rule.HEADING_PREFIX_FORMAT }
        assertEquals("INT. HALL - DAY", period.fixes.single().apply(s.elements, ids)[2].text)
    }

    @Test fun extensions() {
        assertEquals("MAYA (V.O.)", ScriptDoctor.canonExtensions("MAYA (VO)"))
        assertEquals("MAYA (O.S.)", ScriptDoctor.canonExtensions("MAYA (O.S)"))
        assertEquals("MAYA (CONT'D)", ScriptDoctor.canonExtensions("MAYA (CONTD)"))
        assertEquals("MAYA (CONT’D)", ScriptDoctor.canonExtensions("MAYA (CONT’D)"))
        assertEquals("MAYA (INTO PHONE)", ScriptDoctor.canonExtensions("MAYA (INTO PHONE)"))
    }

    @Test fun cueThatIsReallyAction() {
        val s = script(h("INT. ROOM - DAY"), c("SHE WALKS SLOWLY TO THE DOOR AND STOPS."), d("x"))
        assertTrue(Rule.CUE_LOOKS_LIKE_ACTION in rules(s))
        assertFalse(Rule.CUE_LOOKS_LIKE_ACTION in rules(script(h("INT. ROOM - DAY"), c("MRS. ROBINSON"), d("x"))))
    }

    @Test fun transitions() {
        val s = script(h("INT. ROOM - DAY"), a("x."), tr("CUT TO"), a("Still here."))
        val r = rules(s)
        assertTrue(Rule.TRANSITION_PUNCTUATION in r)
        val fixed = ScriptDoctor.fixAll(s.elements, ScriptDoctor.check(s), ids)
        assertEquals("CUT TO:", fixed[2].text)
        assertTrue(Rule.TRANSITION_NOT_INTO_SCENE in rules(s.copy(elements = fixed)))
        val fade = script(h("INT. ROOM - DAY"), a("x."), tr("FADE OUT"))
        assertEquals("FADE OUT.", ScriptDoctor.fixAll(fade.elements, ScriptDoctor.check(fade), ids).last().text)
    }

    @Test fun longActionSplitsAtSentences() {
        val sentence = "Maya crosses the room, past the broken lamp and the boxes nobody unpacked."
        val text = List(6) { sentence }.joinToString(" ")
        val s = script(h("INT. ROOM - DAY"), a(text), a("End."))
        val issue = ScriptDoctor.check(s).single { it.rule == Rule.LONG_ACTION }
        val fixed = issue.fixes.single().apply(s.elements, ids)
        assertTrue(fixed.size > s.elements.size)
        fixed.filter { it.type == ElementType.ACTION }.forEach { assertTrue(TextWrap.wrap(it.text, Specs.FULL_WIDTH).size <= ScriptDoctor.ACTION_MAX_LINES) }
        assertEquals(text, fixed.drop(1).dropLast(1).joinToString(" ") { it.text })
        assertEquals(fixed.size, fixed.map { it.id }.toSet().size)
    }

    @Test fun emptyElementsAndSpacesFixAll() {
        val s = script(h("INT. ROOM - DAY"), a("Two  spaces. "), a(""), c("MAYA"), p("beat"), d("Hi."), h("EXT. YARD - DAY"), a(""), h("EXT. ROAD - DAY"), a("End."))
        val issues = ScriptDoctor.check(s)
        assertEquals(1, issues.count { it.rule == Rule.EMPTY_ELEMENT }) // the card placeholder under EXT. YARD is kept
        val fixed = ScriptDoctor.fixAll(s.elements, issues, ids)
        assertEquals("Two spaces.", fixed[1].text)
        assertEquals("(beat)", fixed.single { it.type == ElementType.PARENTHETICAL }.text)
        assertEquals(s.elements.size - 1, fixed.size)
        assertTrue(ScriptDoctor.check(s.copy(elements = fixed)).none { it.rule.batchSafe })
    }

    @Test fun fixesAreNoOpsOnceApplied() {
        val s = script(h("INT. ROOM - DAY"), a("Two  spaces."), a("End."))
        val fix = ScriptDoctor.check(s).single { it.rule == Rule.STRAY_SPACES }.fixes.single()
        val once = fix.apply(s.elements, ids)
        assertSame(once, fix.apply(once, ids))
    }

    @Test fun titlePageChecks() {
        val r = rules(Script("s", "T", listOf(h("INT. ROOM - DAY"), a("x.")), TitlePage(title = "T")))
        assertTrue(Rule.TITLE_NO_AUTHOR in r)
        assertTrue(Rule.TITLE_NO_CONTACT in r)
    }

    @Test fun oneEditApart() {
        assertTrue(ScriptDoctor.oneEditApart("MAYA", "MAYAH"))
        assertTrue(ScriptDoctor.oneEditApart("MAYA", "MAIA"))
        assertTrue(ScriptDoctor.oneEditApart("MAYA", "MYAA"))
        assertFalse(ScriptDoctor.oneEditApart("MAYA", "MAYA"))
        assertFalse(ScriptDoctor.oneEditApart("MAYA", "MARIA"))
    }

    @Test fun issuesAreOrderedBySeverity() {
        val s = Script("s", "T", listOf(h("KITCHEN"), a("Two  spaces."), d("Orphan.")), TitlePage(title = "T"))
        val sev = ScriptDoctor.check(s).map { it.rule.severity.ordinal }
        assertEquals(sev.sorted(), sev)
    }
}
