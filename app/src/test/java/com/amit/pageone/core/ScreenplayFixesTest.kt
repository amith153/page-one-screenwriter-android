package com.amit.pageone.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenplayFixesTest {
    private var id = 1L
    private fun el(t: ElementType, x: String) = Element(id++, t, x)
    private val L = LayoutOptions().paper.linesPerPage

    private fun texts(p: Pagination, page: Int) = p.pages[page].lines.mapNotNull { it.segs.firstOrNull()?.text }

    /** Action filler: [n] one-line paragraphs (each separated by a blank line, so 2 lines each). */
    private fun filler(n: Int) = List(n) { el(ElementType.ACTION, "Filler line number $it.") }

    private val speech = "I have looked at the scans twice and then a third time and the result does not change " +
        "because the shadow is still there and it is larger than it was in the spring and I cannot tell you otherwise " +
        "no matter how much either of us would like me to"

    @Test fun longSpeechSplitsAtPageEndWithMoreAndContd() {
        // Fill most of page 1, then a doctor speech with no sentence end inside it.
        val els = filler(24) + listOf(el(ElementType.CHARACTER, "DOCTOR"), el(ElementType.DIALOGUE, speech))
        val p = Paginator(LayoutOptions()).paginate(els)
        assertEquals(2, p.pageCount)
        val first = texts(p, 0)
        assertEquals("(MORE)", first.last())
        assertEquals("DOCTOR (CONT'D)", texts(p, 1).first())
        assertTrue(p.pages.all { it.lines.size <= L })
    }

    @Test fun speechThatFitsStaysOnThePage() {
        val els = filler(20) + listOf(el(ElementType.CHARACTER, "DOCTOR"), el(ElementType.DIALOGUE, "Sit down."))
        val p = Paginator(LayoutOptions()).paginate(els)
        assertEquals(1, p.pageCount)
    }

    @Test fun lastSpeechIsFilledIntoPageBottom() {
        // Find a filler count where the speech lands near the bottom and check page 1 is used to its limit.
        for (n in 20..27) {
            val els = filler(n) + listOf(el(ElementType.CHARACTER, "DOCTOR"), el(ElementType.DIALOGUE, speech))
            val p = Paginator(LayoutOptions()).paginate(els)
            if (p.pageCount == 2) {
                val used = p.pages[0].lines.size
                // Either the cue + 2 lines can't fit (then it moves whole), or the page is filled to within a line or two.
                val roomLeft = L - used
                assertTrue("n=$n roomLeft=$roomLeft", roomLeft <= 1 || texts(p, 0).last() != "(MORE)")
            }
        }
    }

    @Test fun everyPageIsNumbered() {
        val els = filler(40)
        val p = Paginator(LayoutOptions()).paginate(els)
        assertTrue(p.pageCount >= 2)
        assertEquals("1", p.label(0))
        assertEquals("2", p.label(1))
    }

    @Test fun caseComesBackWhenALineLeavesCharacter() {
        val dialogue = Element(1, ElementType.DIALOGUE, "Let me have a look, and we will see.")
        val asCharacter = dialogue.retyped(ElementType.CHARACTER)
        assertEquals("LET ME HAVE A LOOK, AND WE WILL SEE.", asCharacter.text)
        val back = asCharacter.retyped(ElementType.DIALOGUE)
        assertEquals("Let me have a look, and we will see.", back.text)
        assertEquals("Let me have a look, and we will see.", asCharacter.plain)
    }
}
