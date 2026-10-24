package com.amit.pageone.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Script Doctor issues the writer chose to ignore, per script, on this phone. Keys are [com.amit.pageone.core.Issue.key]. */
class DoctorIgnores(context: Context) {
    private val p = context.getSharedPreferences("pageone_doctor", Context.MODE_PRIVATE)
    private val _changed = MutableStateFlow(0)
    /** Ticks whenever any script's ignore list changes. */
    val changed: StateFlow<Int> = _changed.asStateFlow()

    fun ignored(scriptId: String): Set<String> = p.getStringSet("ignored_$scriptId", emptySet())!!.toSet()

    fun ignore(scriptId: String, key: String) {
        p.edit().putStringSet("ignored_$scriptId", ignored(scriptId) + key).apply()
        _changed.value++
    }

    fun reset(scriptId: String) {
        p.edit().remove("ignored_$scriptId").apply()
        _changed.value++
    }
}
