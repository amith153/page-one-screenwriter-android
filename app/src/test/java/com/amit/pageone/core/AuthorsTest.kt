package com.amit.pageone.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthorsTest {
    private fun el(id: Long, text: String, by: String? = null, name: String? = null) = Element(id, ElementType.ACTION, text, by = by, byName = name, at = if (by != null) 5L else 0L)

    @Test fun mergeCreditsTheirLinesByNameWhenTheirFileHasNoStamps() {
        val base = listOf(el(1, "One."), el(2, "Two."))
        val local = listOf(el(1, "One!", "me", "Amith"), el(2, "Two."))
        val remote = listOf(el(1, "One."), el(2, "Two, changed."), el(3, "Three."))
        val r = Merge.threeWay(base, local, remote, IdGen(100), "Sam")
        val byId = r.elements.associateBy { it.id }
        assertEquals("Amith", byId.getValue(1).byName)
        assertEquals("Sam", byId.getValue(2).byName)
        assertEquals("Sam", byId.getValue(3).byName)
        assertEquals("guest:sam", byId.getValue(3).by)
    }

    @Test fun anExistingStampIsKept() {
        val e = el(1, "Hi.", "x", "Kim").credited("Sam")
        assertEquals("Kim", e.byName)
        assertNull(el(1, "Hi.").by)
    }
}
