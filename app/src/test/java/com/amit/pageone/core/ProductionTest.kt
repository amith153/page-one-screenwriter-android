package com.amit.pageone.core

import com.amit.pageone.data.ExtrasCodec
import com.amit.pageone.data.ScriptStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProductionTest {

    private var nextId = 1L
    private fun el(type: ElementType, text: String) = Element(nextId++, type, text)

    /** A script of [n] scenes, each about [lines] action lines long. */
    private fun longScript(n: Int, lines: Int = 20): List<Element> {
        val out = ArrayList<Element>()
        val para = List(lines) { "Line of action number $it goes here." }.joinToString(" ")
        repeat(n) { k ->
            out += el(ElementType.SCENE_HEADING, "INT. ROOM $k - DAY")
            out += el(ElementType.ACTION, para)
            out += el(ElementType.CHARACTER, if (k % 2 == 0) "MAYA" else "SAM")
            out += el(ElementType.DIALOGUE, "Something to say in scene $k.")
        }
        return out
    }

    @Test fun lockKeepsSceneNumbersAndAddsANumbers() {
        val els = longScript(4)
        val p = Paginator(LayoutOptions()).paginate(els)
        val lock = Locking.lock(els, p)
        // Insert a new scene after scene 2 (index of scene 3's heading).
        val s3 = els.indexOfFirst { it.text == "INT. ROOM 2 - DAY" }
        val added = els.subList(0, s3) + el(ElementType.SCENE_HEADING, "EXT. NEW - NIGHT") + els.subList(s3, els.size)
        val labels = Locking.sceneLabels(added, lock)
        assertEquals(listOf("1", "2", "2A", "3", "4"), added.filter { it.type == ElementType.SCENE_HEADING }.map { labels[it.id] })
        // And another right after the A-scene.
        assertEquals("2B", Locking.next("2A"))
        assertEquals("12AA", Locking.next("12Z"))
    }

    @Test fun omittedScenesKeepTheirNumber() {
        val els = longScript(3)
        val p = Paginator(LayoutOptions()).paginate(els)
        val lock = Locking.lock(els, p)
        val second = els.filter { it.type == ElementType.SCENE_HEADING }[1]
        val omitted = Locking.omitScene(els, second.id)
        assertEquals("OMITTED", omitted.first { it.id == second.id }.text)
        assertEquals(els.size - 3, omitted.size)
        assertEquals("2", Locking.sceneLabels(omitted, lock)[second.id])
    }

    @Test fun lockedPagesGetAPages() {
        val els = longScript(6, lines = 30).toMutableList()
        val opts = LayoutOptions()
        val p = Paginator(opts).paginate(els)
        assertTrue(p.pageCount >= 3)
        val lock = Locking.lock(els, p)
        // Add a lot of text to the first scene: it should spill onto "1A", not renumber page 2.
        els[1] = els[1].copy(text = els[1].text + " " + els[1].text + " " + els[1].text)
        val p2 = Paginator(opts).paginate(els, Locking.forcedBreaks(els, lock))
        val labels = p2.labels
        assertEquals("1", labels[0])
        assertTrue(labels.contains("1A"))
        assertTrue(labels.contains("2"))
        assertEquals(p.pageCount, labels.count { it.all(Char::isDigit) })
    }

    @Test fun unlockedPagesAreJustNumbers() {
        val p = Paginator(LayoutOptions()).paginate(longScript(5, 30))
        assertEquals((1..p.pageCount).map { "$it" }, p.labels)
    }

    @Test fun eighths() {
        assertEquals("1/8", Production.formatEighths(1))
        assertEquals("1", Production.formatEighths(8))
        assertEquals("2 3/8", Production.formatEighths(19))
        assertEquals(8, Production.eighths(55, 55))
        assertEquals(1, Production.eighths(2, 55))
    }

    @Test fun xrayAndReports() {
        val els = longScript(3)
        val s = Script("x", "X", els)
        val p = Paginator(LayoutOptions()).paginate(els)
        val xs = Production.xray(s, p, PaperSize.LETTER.linesPerPage)
        assertEquals(3, xs.size)
        assertEquals(listOf("MAYA"), xs[0].speaking)
        assertEquals("INT", xs[0].intExt)
        assertEquals("DAY", xs[0].time)
        assertTrue(xs.all { it.eighths > 0 })
        val chars = Production.characters(s, xs)
        assertEquals(setOf("MAYA", "SAM"), chars.map { it.name }.toSet())
        assertTrue(Production.scenesCsv(s, xs).lines().size == 4)
        assertTrue(Production.charactersCsv(chars).startsWith("Character,"))
    }

    @Test fun sidesKeepOnlyTheActorsScenes() {
        val els = longScript(4)
        val sides = Production.sides(Script("x", "X", els), setOf("SAM"))
        val heads = sides.elements.filter { it.type == ElementType.SCENE_HEADING }.map { it.text }
        assertEquals(listOf("INT. ROOM 1 - DAY", "INT. ROOM 3 - DAY"), heads)
    }

    @Test fun structureCheck() {
        val t = Structures.template("save_the_cat")!!
        var n = 0
        val st = Structures.create(t) { "b${n++}" }
        assertEquals(15, st.beats.size)
        val catalyst = st.beats.first { it.name == "Catalyst" }.copy(sceneIds = listOf(42L))
        val s2 = st.copy(beats = st.beats.map { if (it.id == catalyst.id) catalyst else it })
        val res = Structures.check(s2, 110) { if (it == 42L) 19 else null }
        val c = res.first { it.beat.name == "Catalyst" }
        assertEquals(12, c.targetPage)
        assertEquals(19, c.actualPage)
        assertFalse(c.onTrack)
        assertTrue(c.verdict.contains("p.12") && c.verdict.contains("p.19"))
    }

    @Test fun deadlinePlan() {
        val d = Deadline("2026-12-01", 110, "2026-10-01", 0)
        val plan = Planner.plan(d, 40, LocalDate.parse("2026-11-01"))
        assertEquals(30L, plan.daysLeft)
        assertEquals(70, plan.pagesLeft)
        assertEquals(70 / 30f, plan.pagesPerDay, 0.01f)
        assertTrue(plan.behind > 10) // halfway through the time, well under halfway on pages
        assertTrue(plan.status.contains("behind"))
        assertTrue(Planner.plan(d, 110, LocalDate.parse("2026-11-01")).done)
        assertTrue(Planner.plan(d, 50, LocalDate.parse("2026-12-05")).overdue)
    }

    @Test fun indicLinesGetRoomForTheirMarks() {
        assertTrue(TextWrap.tall("ಅಷ್ಟೆಲ್ಲಾ ಮಾತಾಡ್ತಿರೋದು"))
        assertTrue(TextWrap.tall("तुम कहाँ हो"))
        assertFalse(TextWrap.tall("Plain English (V.O.)"))
        // No spacer lines between Kannada lines: each is one taller line instead, so marks never overlap.
        val long = "ಅಷ್ಟೆಲ್ಲಾ ಮಾತಾಡ್ತಿರೋದು ನಿಂಗೆ ಅಷ್ಟೆಲ್ಲಾ ಮಾತಾಡ್ತಿರೋದು ನಿಂಗೆ ಅಷ್ಟೆಲ್ಲಾ ಮಾತಾಡ್ತಿರೋದು ನಿಂಗೆ ಅಷ್ಟೆಲ್ಲಾ ಮಾತಾಡ್ತಿರೋದು ನಿಂಗೆ"
        val one = Paginator(LayoutOptions()).paginate(listOf(el(ElementType.CHARACTER, "MAN"), el(ElementType.DIALOGUE, "ಅಷ್ಟೆಲ್ಲಾ ಮಾತಾಡ್ತಿರೋದು")))
        assertEquals(2, one.pages[0].lines.size)
        val lines = Paginator(LayoutOptions()).paginate(listOf(el(ElementType.CHARACTER, "MAN"), el(ElementType.DIALOGUE, long))).pages[0].lines
        assertTrue(lines.size >= 3)
        assertFalse(lines.any { it.isBlank })
        assertTrue(lines.drop(1).all { it.h > 1f })
        assertEquals(1f, lines[0].h, 0f) // the English cue stays one line
    }

    @Test fun indicTextWrapsByPrintedWidth() {
        // Vowel signs and viramas take no column of their own, so a Kannada line holds more characters than English.
        assertEquals(5f, TextWrap.cells("Hello"), 0.001f)
        val word = "ಮಾತಾಡ್ತಿರೋದು"
        assertTrue(TextWrap.cells(word) < word.length * 0.75f)
        val lines = TextWrap.wrap(List(8) { word }.joinToString(" "), 35)
        assertTrue(lines.all { TextWrap.cells(it) <= 35f })
        assertTrue(lines.first().length > 35)
    }

    @Test fun mixedLinesSplitIntoRuns() {
        assertEquals(listOf("ನಿಂಗೆ " to true, "HURT" to false), TextWrap.runs("ನಿಂಗೆ HURT"))
        assertEquals(listOf("I'M " to false, "ಆಗಿದೆ" to true, "?" to false), TextWrap.runs("I'M ಆಗಿದೆ?"))
    }

    @Test fun commentRangeFollowsEdits() {
        val c = Comment("c", 1, 4, 9, "brown", "A", 0, "?", 0)
        assertEquals(4 until 9, c.rangeIn("The brown fox"))
        assertEquals(10 until 15, c.rangeIn("The quick brown fox"))
        assertEquals(null, c.rangeIn("The red fox"))
    }

    @Test fun extrasSurviveSaveAndLoad() {
        val x = ScriptExtras(
            characters = listOf(CharacterProfile("MAYA", role = "Lead", want = "Home", pitch = 1.2f)),
            locations = listOf(LocationProfile("KITCHEN", "Cramped")),
            threads = listOf(PlotThread("a", "A story", 3)),
            structure = Structure("save_the_cat", listOf(Beat("b1", "Catalyst", "x", 0.11f, 1, listOf(7L))), 100),
            scenes = mapOf(7L to SceneMeta(listOf("a"), mapOf(BreakdownCategory.PROPS to listOf("Knife")), listOf("m.jpg"))),
            board = listOf(MoodImage("b.jpg", "Dusk")),
            comments = listOf(Comment("c1", 7, 0, 3, "INT", "Amit", 0xFF00FF00, "Night instead?", 5, false, listOf(Reply("Priya", 1, "Agreed", 6)),
                listOf(Attachment("a.jpg", AttachmentKind.IMAGE, "Ref"), Attachment("b.m4a", AttachmentKind.AUDIO, "Memo", 4200)))),
            lock = LockState(true, 9, mapOf(7L to "1"), listOf(8L)),
            deadline = Deadline("2026-12-01", 110, "2026-10-01", 3),
        )
        val s = Script("id", "T", listOf(Element(7, ElementType.SCENE_HEADING, "INT. KITCHEN - DAY")), extras = x)
        val back = ScriptStore.decode(ScriptStore.encode(s))
        assertEquals(x, back.extras)
        // Sharing leaves phone-only media and comments out.
        val shared = ExtrasCodec.decode(ExtrasCodec.encode(x, includeComments = false, includeLocalMedia = false))
        assertTrue(shared.comments.isEmpty() && shared.board.isEmpty() && shared.scenes[7L]!!.moods.isEmpty())
        val merged = ExtrasCodec.mergeRemote(x, shared)
        assertEquals(x.board, merged.board)
        assertEquals(listOf("m.jpg"), merged.scenes[7L]!!.moods)
    }
}
