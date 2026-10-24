package com.amit.pageone.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** A timed writing sprint: "25 minutes, 500 words". */
data class Sprint(val startAt: Long, val minutes: Int, val goalWords: Int, val words: Int = 0, val scriptTitle: String = "") {
    val endAt: Long get() = startAt + minutes * 60_000L
    fun remainingMs(now: Long = System.currentTimeMillis()) = (endAt - now).coerceAtLeast(0)
    val progress: Float get() = if (goalWords <= 0) 0f else words / goalWords.toFloat()
}

data class SprintRecord(val at: Long, val minutes: Int, val goal: Int, val words: Int)

/** Runs one sprint at a time; counts words from [GoalTracker], and posts a notification when time is up. */
class SprintManager(private val context: Context, goals: GoalTracker) {
    private val p = context.getSharedPreferences("pageone_sprints", Context.MODE_PRIVATE)
    private val _current = MutableStateFlow(readCurrent())
    val current: StateFlow<Sprint?> = _current.asStateFlow()
    private val _finished = MutableStateFlow<Sprint?>(null)
    /** The sprint that just ended, for the summary dialog. */
    val finished: StateFlow<Sprint?> = _finished.asStateFlow()

    init {
        goals.listeners += { delta -> _current.value?.let { s -> if (System.currentTimeMillis() < s.endAt) update(s.copy(words = s.words + delta)) } }
    }

    fun start(minutes: Int, goalWords: Int, title: String = "") {
        val s = Sprint(System.currentTimeMillis(), minutes, goalWords, 0, title)
        update(s)
        Notifier.scheduleSprintEnd(context, s.endAt)
    }

    /** Called by the UI timer and the alarm: wrap up if time is up. */
    fun tick() {
        val s = _current.value ?: return
        if (System.currentTimeMillis() >= s.endAt) finish()
    }

    fun finish(): Sprint? {
        val s = _current.value ?: return null
        update(null)
        Notifier.cancelSprintEnd(context)
        record(SprintRecord(s.startAt, ((System.currentTimeMillis().coerceAtMost(s.endAt) - s.startAt) / 60_000L).toInt().coerceAtLeast(1), s.goalWords, s.words))
        _finished.value = s
        return s
    }

    fun cancel() { update(null); Notifier.cancelSprintEnd(context) }
    fun consumeFinished() { _finished.value = null }

    /** Sync listens here: a sprint finished on this phone. */
    var onRecorded: ((SprintRecord) -> Unit)? = null

    /** Sprints finished on your other devices (from sync). */
    fun merge(list: List<SprintRecord>) {
        val have = history().map { it.at }.toSet()
        val add = list.filter { it.at !in have }
        if (add.isEmpty()) return
        write((history() + add).sortedByDescending { it.at }.take(60))
    }

    fun history(): List<SprintRecord> = runCatching {
        val a = JSONArray(p.getString("history", "[]"))
        (0 until a.length()).map { i -> a.getJSONObject(i).let { SprintRecord(it.optLong("at"), it.optInt("m"), it.optInt("g"), it.optInt("w")) } }
    }.getOrDefault(emptyList())

    private fun record(r: SprintRecord) {
        write((listOf(r) + history()).take(60))
        onRecorded?.invoke(r)
    }

    private fun write(list: List<SprintRecord>) {
        val a = JSONArray(); list.forEach { a.put(JSONObject().put("at", it.at).put("m", it.minutes).put("g", it.goal).put("w", it.words)) }
        p.edit().putString("history", a.toString()).apply()
    }

    private fun update(s: Sprint?) {
        _current.value = s
        if (s == null) p.edit().remove("current").apply()
        else p.edit().putString("current", JSONObject().put("s", s.startAt).put("m", s.minutes).put("g", s.goalWords).put("w", s.words).put("t", s.scriptTitle).toString()).apply()
    }

    private fun readCurrent(): Sprint? = runCatching {
        val o = JSONObject(p.getString("current", null) ?: return null)
        Sprint(o.getLong("s"), o.getInt("m"), o.optInt("g"), o.optInt("w"), o.optString("t"))
    }.getOrNull()
}
