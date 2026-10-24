package com.amit.pageone.core

/**
 * Fractional ordering keys for live sync. Each element carries a Double "position";
 * the script is the elements sorted by it. When the local order changes we keep as
 * many existing keys as possible (longest increasing subsequence) and only assign new
 * keys to the elements that actually moved or were inserted — so a typical edit
 * rewrites one or two documents, not the whole script.
 */
object Ordering {
    const val GAP = 1024.0

    fun assign(ids: List<Long>, existing: Map<Long, Double>): Map<Long, Double> {
        val n = ids.size
        val vals = DoubleArray(n) { existing[ids[it]] ?: Double.NaN }
        // LIS over the indices that have a key
        val idx = (0 until n).filter { !vals[it].isNaN() }
        val tails = ArrayList<Int>()          // indices into idx
        val prev = IntArray(idx.size) { -1 }
        for ((k, i) in idx.withIndex()) {
            var lo = 0; var hi = tails.size
            while (lo < hi) { val m = (lo + hi) / 2; if (vals[idx[tails[m]]] < vals[i]) lo = m + 1 else hi = m }
            if (lo > 0) prev[k] = tails[lo - 1]
            if (lo == tails.size) tails.add(k) else tails[lo] = k
        }
        val keep = BooleanArray(n)
        var t = if (tails.isEmpty()) -1 else tails.last()
        while (t >= 0) { keep[idx[t]] = true; t = prev[t] }

        val out = HashMap<Long, Double>(n * 2)
        var i = 0
        var lastKept = Double.NaN
        while (i < n) {
            if (keep[i]) { out[ids[i]] = vals[i]; lastKept = vals[i]; i++; continue }
            // run of elements needing new keys: [i, j)
            var j = i
            while (j < n && !keep[j]) j++
            val lo = if (lastKept.isNaN()) (if (j < n) vals[j] - GAP * (j - i + 1) else 0.0) else lastKept
            val hi = if (j < n) vals[j] else lo + GAP * (j - i + 1)
            val step = (hi - lo) / (j - i + 1)
            for (k in i until j) out[ids[k]] = lo + step * (k - i + 1)
            i = j
        }
        // If keys got too dense, renumber everything.
        val sorted = ids.map { out.getValue(it) }
        val tooDense = sorted.zipWithNext().any { (a, b) -> b - a < 1e-6 }
        if (tooDense) ids.forEachIndexed { k, id -> out[id] = (k + 1) * GAP }
        return out
    }
}
