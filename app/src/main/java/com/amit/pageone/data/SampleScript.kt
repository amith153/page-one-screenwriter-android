package com.amit.pageone.data

import com.amit.pageone.core.CharacterProfile
import com.amit.pageone.core.Comment
import com.amit.pageone.core.Credit
import com.amit.pageone.core.ElementType
import com.amit.pageone.core.Fountain
import com.amit.pageone.core.IdGen
import com.amit.pageone.core.LocationProfile
import com.amit.pageone.core.PlotThread
import com.amit.pageone.core.SceneMeta
import com.amit.pageone.core.ScriptExtras
import com.amit.pageone.core.Structures
import com.amit.pageone.core.TitlePage

/** A short original screenplay that shows off what Page One can do — bible, beats, storylines and notes included. */
object SampleScript {

    private val TEXT = """
FADE IN:

EXT. PALACE CINEMA - NIGHT

Rain hammers a dead neon marquee. Half the letters are gone: P A  CE.

A single window glows above the entrance.

INT. PROJECTION BOOTH - CONTINUOUS

Cramped. Film cans stacked to the ceiling. A 35mm projector hums like an old cat.

ELI (70s), cardigan, reading glasses on a cord, threads film through the gate with hands that have done this ten thousand times.

ELI
(to the projector)
Easy, old girl. One more night.

He flicks a switch. Light pours through the porthole.

INT. AUDITORIUM - CONTINUOUS

Four hundred red seats. Every one of them empty.

The beam lands on the screen. A black-and-white couple kiss in a train station.

A side door BANGS open. NADIA (17), soaked hoodie, earbuds, a phone held up like a torch.

NADIA
Hello? The sign said open.

ELI (O.S.)
The sign says "P A CE." It's been lying for years.

INT. PROJECTION BOOTH - MOMENTS LATER

Nadia squeezes in. She stares at the projector like it's an alien.

NADIA
Where's the screen thing? The laptop?

ELI
This is the screen thing.

NADIA
It's on fire.

ELI
It's a carbon arc. It's supposed to look like that.

He offers her a biscuit from a tin. She takes two.

NADIA
My mum says they're knocking this place down. Friday.

Eli keeps his eyes on the porthole.

ELI
Your mum's well informed.

INT. AUDITORIUM - LATER

Nadia sits in the front row, feet up. On screen, the train pulls away.

She takes out her phone. Films the screen. Stops. Puts the phone face down.

EXT. PALACE CINEMA - DAY

Bulldozers idle in the car park. A DEVELOPER (40s), hard hat over a good haircut, checks his watch.

DEVELOPER
He's got till five.

INT. PROJECTION BOOTH - DAY

Eli packs film cans into a shopping trolley. Nadia arrives with two coffees and a camera bag.

NADIA
I posted a clip. Of the kiss. People want to come.

ELI
How many people?

NADIA
(beat)
Eleven thousand.

Eli puts down the can.

ELI
The roof leaks on seats nine to forty.

NADIA
Then we'll need buckets.

INT. AUDITORIUM - NIGHT

Every seat is full. People stand in the aisles. Buckets everywhere, catching rain.

Up in the booth, Eli's hands are shaking. Nadia steadies the reel.

NADIA
Easy, old girl.

He laughs. Flicks the switch.

The beam cuts through the dark. Four hundred faces turn up to the light.

FADE OUT.
""".trimIndent()

    suspend fun create(): String {
        val parsed = Fountain.parse(TEXT, IdGen(1))
        val els = parsed.elements
        val heads = els.filter { it.type == ElementType.SCENE_HEADING }
        fun sid(i: Int) = heads.getOrNull(i)?.id

        val tmpl = Structures.template("save_the_cat")!!
        var n = 0
        val st = Structures.create(tmpl) { "sample-${n++}" }
        val place = mapOf("Opening Image" to 0, "Set-Up" to 1, "Catalyst" to 3, "Debate" to 4, "Break into Two" to 6, "Midpoint" to 7, "Finale" to 8, "Final Image" to 8)
        val structure = st.copy(targetPages = 30, beats = st.beats.map { b -> place[b.name]?.let { i -> sid(i)?.let { b.copy(sceneIds = listOf(it)) } } ?: b })

        val a = PlotThread("a", "Saving the Palace", 1)
        val b = PlotThread("b", "Eli & Nadia", 6)
        val scenes = HashMap<Long, SceneMeta>()
        heads.forEachIndexed { i, h -> scenes[h.id] = SceneMeta(threads = if (i in listOf(3, 4, 7)) listOf("b") else if (i == 0 || i == 5) listOf("a") else listOf("a", "b")) }

        val firstLine = els.firstOrNull { it.type == ElementType.DIALOGUE }
        val comments = listOfNotNull(firstLine?.let {
            Comment("sample-note", it.id, 0, it.text.length, it.text, "Page One", 0xFFE7B35A, "Tap the note marker in the margin to see notes like this one — select any words and tap the note button to pin your own.", System.currentTimeMillis())
        })

        val extras = ScriptExtras(
            characters = listOf(
                CharacterProfile("ELI", role = "Protagonist", age = "70s", want = "One more night in the booth", need = "To let someone else carry it on", arc = "From last projectionist to first teacher", voice = "Dry, patient, talks to machines more than people"),
                CharacterProfile("NADIA", role = "Deuteragonist", age = "17", want = "Somewhere dry to kill time", need = "Something real that isn't on a screen in her hand", arc = "Filming life → living it", voice = "Quick, flat, funny"),
                CharacterProfile("DEVELOPER", role = "Antagonist", age = "40s", want = "The site cleared by Friday"),
            ),
            locations = listOf(
                LocationProfile("PALACE CINEMA", "1930s picture palace, dead neon, art-deco tiles cracked by weather"),
                LocationProfile("PROJECTION BOOTH", "Hot, cramped, film cans to the ceiling, a porthole of light"),
            ),
            threads = listOf(a, b),
            structure = structure,
            scenes = scenes,
            comments = comments,
        )
        val tp = TitlePage(title = "The Last Projectionist", credits = listOf(Credit("Written by", "Page One")), draft = "Sample script", notes = "Delete me whenever you like.")
        val s = AppGraph.store.create("The Last Projectionist", els, tp)
        AppGraph.store.save(s.copy(extras = extras), 4)
        return s.id
    }
}
