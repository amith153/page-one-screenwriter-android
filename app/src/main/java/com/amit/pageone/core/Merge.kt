package com.amit.pageone.core

/**
 * Three-way merge of two versions of a script that share element ids, against the
 * last version both sides had in common (the "base"). Works element by element:
 * edits on different elements combine cleanly; an element edited differently on both
 * sides keeps your version and gets a NOTE right after it with your co-writer's text.
 */
object Merge {

    data class Result(
        val elements: List<Element>,
        val added: Int,
        val changed: Int,
        val deleted: Int,
        val conflicts: Int,
    ) {
        val total: Int get() = added + changed + deleted + conflicts
    }

    private fun same(a: Element, b: Element) =
        a.type == b.type && a.text == b.text && a.dual == b.dual && a.color == b.color && a.synopsis == b.synopsis

    fun threeWay(base: List<Element>?, local: List<Element>, remote: List<Element>, ids: IdGen, theirName: String = "Co-writer"): Result {
        val B = base?.associateBy { it.id } ?: emptyMap()
        val L = local.associateBy { it.id }
        val R = remote.associateBy { it.id }
        var added = 0; var changed = 0; var deleted = 0; var conflicts = 0

        // Decide the content of each surviving element.
        val keep = LinkedHashMap<Long, Element>()
        val conflictNotes = HashMap<Long, Element>()
        for (l in local) {
            val r = R[l.id]
            val b = B[l.id]
            if (r != null) {
                when {
                    same(l, r) -> keep[l.id] = l
                    b != null && same(l, b) -> { keep[l.id] = r.copy(revision = maxOf(l.revision, r.revision)); changed++ }
                    b != null && same(r, b) -> keep[l.id] = l
                    else -> {
                        keep[l.id] = l
                        conflictNotes[l.id] = Element(ids.next(), ElementType.NOTE, "$theirName's version: ${r.text}")
                        conflicts++
                    }
                }
            } else {
                // Missing remotely: deleted there, or new here.
                if (b != null && same(l, b)) deleted++ else keep[l.id] = l
            }
        }
        val remoteOnly = remote.filter { it.id !in L && (B[it.id] == null || !same(it, B.getValue(it.id))) }
        remoteOnly.forEach { keep[it.id] = it; added++ }

        // Order: if we didn't reorder anything since the base but they did, follow theirs.
        val common = local.map { it.id }.filter { it in R }
        val localOrderUnchanged = base != null && common == base.map { it.id }.filter { it in L && it in R }
        val remoteReordered = common != remote.map { it.id }.filter { it in L }
        val primary = if (localOrderUnchanged && remoteReordered) remote else local
        val secondary = if (primary === remote) local else remote

        val out = ArrayList<Element>(keep.size + conflictNotes.size)
        val placed = HashSet<Long>()
        for (e in primary) {
            val k = keep[e.id] ?: continue
            out.add(k); placed.add(k.id)
            conflictNotes[k.id]?.let { out.add(it) }
        }
        // Insert the rest after their nearest predecessor in the other ordering.
        var anchor: Long? = null
        for (e in secondary) {
            val k = keep[e.id]
            if (k == null) continue
            if (k.id in placed) { anchor = k.id; continue }
            var at = if (anchor == null) 0 else out.indexOfFirst { it.id == anchor } + 1
            // keep any conflict note glued to its element
            while (at < out.size && out[at].type == ElementType.NOTE && conflictNotes.values.any { it.id == out[at].id }) at++
            out.add(at, k); placed.add(k.id); anchor = k.id
            conflictNotes[k.id]?.let { out.add(at + 1, it) }
        }
        return Result(out, added, changed, deleted, conflicts)
    }
}
