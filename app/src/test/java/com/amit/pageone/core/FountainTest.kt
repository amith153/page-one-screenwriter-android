package com.amit.pageone.core

import org.junit.Assert.assertEquals
import org.junit.Test

class FountainTest {

    @Test fun openingFadeInIsNotMistakenForATitlePage() {
        val parsed = Fountain.parse("FADE IN:\n\nEXT. ROAD - DAY\n\nDust.\n", IdGen())
        assertEquals(
            listOf(ElementType.ACTION to "FADE IN:", ElementType.SCENE_HEADING to "EXT. ROAD - DAY", ElementType.ACTION to "Dust."),
            parsed.elements.map { it.type to it.text },
        )
        assertEquals("", parsed.titlePage.title)
    }

    @Test fun realTitlePageStillParses() {
        val parsed = Fountain.parse("Title: Dust\nAuthor: Amit\n\nEXT. ROAD - DAY\n", IdGen())
        assertEquals("Dust", parsed.titlePage.title)
        assertEquals("Amit", parsed.titlePage.author)
        assertEquals(listOf("EXT. ROAD - DAY"), parsed.elements.map { it.text })
    }
}
