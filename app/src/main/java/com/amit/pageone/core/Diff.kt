package com.amit.pageone.core

/** Word-level diff (LCS) for showing what a co-writer changed in a line. */
object Diff {
    enum class Op { SAME, ADDED, REMOVED }
    data class Piece(val op: Op, val text: String)

    private val tokenRe = Regex("\\s+|[^\\s]+")

    fun words(before: String, after: String): List<Piece> {
        val a = tokenRe.findAll(before).map { it.value }.toList()
        val b = tokenRe.findAll(after).map { it.value }.toList()
        // Keep it cheap on very long elements.
        if (a.size * b.size > 400_000) return listOf(Piece(Op.REMOVED, before), Piece(Op.ADDED, after))
        val n = a.size; val m = b.size
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) for (j in m - 1 downTo 0)
            dp[i][j] = if (a[i] == b[j]) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
        val out = ArrayList<Piece>()
        fun add(op: Op, t: String) {
            if (out.isNotEmpty() && out.last().op == op) out[out.size - 1] = Piece(op, out.last().text + t) else out.add(Piece(op, t))
        }
        var i = 0; var j = 0
        while (i < n && j < m) {
            when {
                a[i] == b[j] -> { add(Op.SAME, a[i]); i++; j++ }
                dp[i + 1][j] >= dp[i][j + 1] -> { add(Op.REMOVED, a[i]); i++ }
                else -> { add(Op.ADDED, b[j]); j++ }
            }
        }
        while (i < n) { add(Op.REMOVED, a[i]); i++ }
        while (j < m) { add(Op.ADDED, b[j]); j++ }
        return out
    }
}
