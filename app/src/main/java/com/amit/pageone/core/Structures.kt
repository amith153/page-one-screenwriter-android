package com.amit.pageone.core

/** Story-structure templates and the page-target check. */
object Structures {

    data class Template(val id: String, val name: String, val blurb: String, val pages: Int, val beats: List<Triple<String, String, Float>>, val acts: List<Float>)

    /** (beat, what happens, where it lands as a fraction of the script). Acts: fractions where acts 2, 3… begin. */
    val templates: List<Template> = listOf(
        Template(
            "save_the_cat", "Save the Cat!", "Blake Snyder's 15 beats — the modern studio standard.", 110,
            listOf(
                Triple("Opening Image", "A snapshot of the hero's world before the story.", 0.00f),
                Triple("Set-Up", "Hero, stakes and everything that needs fixing.", 0.01f),
                Triple("Theme Stated", "Someone hints at the lesson the hero must learn.", 0.045f),
                Triple("Catalyst", "The event that knocks the hero's world off course.", 0.11f),
                Triple("Debate", "Should I go? The hero hesitates.", 0.12f),
                Triple("Break into Two", "The hero chooses and enters the upside-down world.", 0.23f),
                Triple("B Story", "A new relationship that carries the theme.", 0.27f),
                Triple("Fun and Games", "The promise of the premise — the trailer moments.", 0.27f),
                Triple("Midpoint", "False victory or false defeat; stakes are raised.", 0.50f),
                Triple("Bad Guys Close In", "Doubt, jealousy and real enemies tighten the screws.", 0.50f),
                Triple("All Is Lost", "The opposite of the midpoint. A whiff of death.", 0.68f),
                Triple("Dark Night of the Soul", "The hero hits bottom and wallows.", 0.68f),
                Triple("Break into Three", "The solution, thanks to the B story.", 0.77f),
                Triple("Finale", "The hero proves they've changed and wins.", 0.77f),
                Triple("Final Image", "The opposite of the opening image.", 0.99f),
            ),
            listOf(0.23f, 0.77f),
        ),
        Template(
            "short_film", "Short film", "Seven beats for a 5–15 page short: one want, one turn, one image that stays.", 12,
            listOf(
                Triple("Opening image", "Drop us into the world and the mood in a single shot.", 0.0f),
                Triple("Want", "What does the character want, right now, today?", 0.08f),
                Triple("Disruption", "Something gets in the way — the story starts here.", 0.2f),
                Triple("Escalation", "They try; it gets harder, more personal.", 0.4f),
                Triple("Turn", "A reversal or reveal that changes what the story is about.", 0.65f),
                Triple("Climax", "The choice that shows who they really are.", 0.85f),
                Triple("Final image", "The button — an image that answers the opening one.", 0.97f),
            ),
            listOf(0.2f, 0.8f),
        ),
        Template(
            "three_act", "Three-act structure", "Set-up, confrontation, resolution — with the key turning points.", 110,
            listOf(
                Triple("Hook", "Grab the audience in the first pages.", 0.0f),
                Triple("Inciting incident", "The event that sets the story in motion.", 0.10f),
                Triple("First plot point", "The hero commits; Act Two begins.", 0.25f),
                Triple("Pinch point 1", "A reminder of the antagonist's power.", 0.375f),
                Triple("Midpoint", "A shift from reaction to action.", 0.50f),
                Triple("Pinch point 2", "The antagonist strikes again, harder.", 0.625f),
                Triple("Second plot point", "The final piece; Act Three begins.", 0.75f),
                Triple("Climax", "The final confrontation.", 0.88f),
                Triple("Resolution", "The new normal.", 0.97f),
            ),
            listOf(0.25f, 0.75f),
        ),
        Template(
            "heros_journey", "The Hero's Journey", "Vogler's twelve stages of the monomyth.", 110,
            listOf(
                Triple("Ordinary World", "The hero at home, incomplete.", 0.0f),
                Triple("Call to Adventure", "A problem or challenge appears.", 0.08f),
                Triple("Refusal of the Call", "Fear makes the hero hesitate.", 0.14f),
                Triple("Meeting the Mentor", "Guidance, a gift, confidence.", 0.19f),
                Triple("Crossing the Threshold", "Committing to the special world.", 0.25f),
                Triple("Tests, Allies, Enemies", "Learning the rules of the new world.", 0.32f),
                Triple("Approach to the Inmost Cave", "Preparing for the central ordeal.", 0.44f),
                Triple("The Ordeal", "Facing the greatest fear; death and rebirth.", 0.52f),
                Triple("Reward", "Seizing the sword.", 0.62f),
                Triple("The Road Back", "Consequences; the chase home.", 0.75f),
                Triple("Resurrection", "A final test where everything is at stake.", 0.86f),
                Triple("Return with the Elixir", "Home, transformed.", 0.96f),
            ),
            listOf(0.25f, 0.75f),
        ),
        Template(
            "story_circle", "Story Circle", "Dan Harmon's eight steps — great for TV and shorts.", 30,
            listOf(
                Triple("You", "A character in a zone of comfort.", 0.0f),
                Triple("Need", "But they want something.", 0.12f),
                Triple("Go", "They enter an unfamiliar situation.", 0.25f),
                Triple("Search", "Adapt to it.", 0.37f),
                Triple("Find", "Get what they wanted.", 0.50f),
                Triple("Take", "Pay a heavy price for it.", 0.62f),
                Triple("Return", "Then return to their familiar situation.", 0.75f),
                Triple("Change", "Having changed.", 0.90f),
            ),
            listOf(0.25f, 0.75f),
        ),
        Template(
            "tv_hour", "TV drama (one hour)", "Teaser, five acts and a tag, with act-out cliffhangers.", 60,
            listOf(
                Triple("Teaser", "Hook and the episode's question.", 0.0f),
                Triple("Act One out", "The problem becomes unavoidable.", 0.20f),
                Triple("Act Two out", "A complication raises the stakes.", 0.38f),
                Triple("Act Three out", "Midpoint reversal.", 0.56f),
                Triple("Act Four out", "Lowest point / biggest cliffhanger.", 0.75f),
                Triple("Act Five", "Climax and resolution.", 0.88f),
                Triple("Tag", "A button — or a hook for next week.", 0.98f),
            ),
            listOf(0.08f, 0.20f, 0.38f, 0.56f, 0.75f),
        ),
        Template(
            "tv_half", "TV comedy (half hour)", "Cold open, two acts and a tag.", 30,
            listOf(
                Triple("Cold open", "A standalone runner or the episode's setup.", 0.0f),
                Triple("Inciting incident", "The A-story problem lands.", 0.12f),
                Triple("Act One break", "The plan goes wrong.", 0.45f),
                Triple("Act Two crisis", "Everything collides.", 0.75f),
                Triple("Resolution", "Lessons (not) learned.", 0.90f),
                Triple("Tag", "One last laugh.", 0.97f),
            ),
            listOf(0.10f, 0.50f),
        ),
    )

    fun template(id: String) = templates.firstOrNull { it.id == id }

    fun create(t: Template, ids: () -> String): Structure {
        val beats = t.beats.map { (name, desc, f) ->
            val act = 1 + t.acts.count { f >= it }
            Beat(ids(), name, desc, f, act)
        }
        return Structure(t.id, beats, t.pages)
    }

    data class BeatCheck(val beat: Beat, val targetPage: Int, val actualPage: Int?, val verdict: String, val onTrack: Boolean)

    /**
     * Where each beat lands vs. where the template says it should, scaled to the script's
     * actual length (or the target length while the draft is shorter than half of it).
     */
    fun check(structure: Structure, totalPages: Int, pageOfScene: (Long) -> Int?): List<BeatCheck> {
        val length = if (totalPages * 2 < structure.targetPages) structure.targetPages else totalPages
        return structure.beats.map { b ->
            val target = (b.target * length).toInt().coerceAtLeast(1)
            val actual = b.sceneIds.mapNotNull(pageOfScene).minOrNull()
            val tolerance = maxOf(3, (length * 0.05f).toInt())
            when {
                actual == null -> BeatCheck(b, target, null, "Not placed yet — aim for p.$target", false)
                actual > target + tolerance -> BeatCheck(b, target, actual, "${b.name} should land by p.$target — yours is on p.$actual", false)
                actual < target - tolerance -> BeatCheck(b, target, actual, "Early: p.$actual (target p.$target)", false)
                else -> BeatCheck(b, target, actual, "On track: p.$actual (target p.$target)", true)
            }
        }
    }
}
