package com.amit.pageone.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amit.pageone.core.Fix
import com.amit.pageone.core.Issue
import com.amit.pageone.core.Rule
import com.amit.pageone.core.Script
import com.amit.pageone.core.ScriptDoctor
import com.amit.pageone.core.Severity
import com.amit.pageone.data.AppGraph

/** Script Doctor issues for [script], minus the ones ignored on this phone. */
fun doctorIssues(script: Script, pagination: com.amit.pageone.core.Pagination? = null): List<Issue> {
    val ignored = AppGraph.doctor.ignored(script.id)
    return ScriptDoctor.check(script, pagination).filter { it.key !in ignored }
}

@Composable
fun severityColor(s: Severity): Color = when (s) {
    Severity.PROBLEM -> MaterialTheme.colorScheme.error
    Severity.CHECK -> MaterialTheme.colorScheme.tertiary
    Severity.POLISH -> MaterialTheme.colorScheme.secondary
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScriptDoctorSheet(
    script: Script,
    pagination: com.amit.pageone.core.Pagination?,
    canUndo: Boolean,
    onDismiss: () -> Unit,
    onJump: (Long) -> Unit,
    onTitlePage: () -> Unit,
    onFix: (Fix) -> Unit,
    onFixAll: (List<Issue>) -> Unit,
    onUndo: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val ignoreTick by AppGraph.doctor.changed.collectAsState()
    val elementPage = pagination?.elementPage
    val all = remember(script.elements, script.titlePage, pagination) { ScriptDoctor.check(script, pagination) }
    val ignored = remember(script.id, ignoreTick) { AppGraph.doctor.ignored(script.id) }
    val issues = remember(all, ignored) { all.filter { it.key !in ignored } }
    val ignoredCount = all.size - issues.size
    val bySeverity = remember(issues) { issues.groupingBy { it.rule.severity }.eachCount() }
    val batch = remember(issues) { issues.filter { it.rule.batchSafe && it.fixes.isNotEmpty() } }
    val indexById = remember(script.elements) { HashMap<Long, Int>().apply { script.elements.forEachIndexed { i, e -> put(e.id, i) } } }

    var filter by remember { mutableStateOf<Severity?>(null) }
    var expanded by remember { mutableStateOf(setOf<Rule>()) }
    var justFixed by remember { mutableStateOf<String?>(null) }

    fun pageOf(i: Issue): String? = i.elementId?.let { id -> indexById[id]?.let { idx -> elementPage?.getOrNull(idx)?.let { pagination?.label(it) } } }
    fun fix(f: Fix, what: String) { onFix(f); justFixed = what }

    val shown = if (filter == null) issues else issues.filter { it.rule.severity == filter }
    val groups = shown.groupBy { it.rule }

    AppSheet(onDismiss) {
        Column {
            // ---- header
            Row(Modifier.padding(horizontal = 24.dp).padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.HealthAndSafety, null, tint = cs.onPrimaryContainer)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Script Doctor", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        when {
                            issues.isEmpty() -> "Clean bill of health."
                            bySeverity[Severity.PROBLEM] != null -> "${plural(issues.size, "note")} — fix the problems before you send it"
                            else -> "${plural(issues.size, "note")} — nothing that breaks the format"
                        },
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                    )
                }
            }

            // ---- severity filters
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Severity.entries.forEach { s ->
                    val n = bySeverity[s] ?: 0
                    val sel = filter == s
                    val color = severityColor(s)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (sel) color.copy(alpha = 0.16f) else cs.surfaceContainerHigh,
                        border = if (sel) BorderStroke(1.5.dp, color) else null,
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable { filter = if (sel) null else s },
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(if (n > 0) color else cs.outlineVariant))
                                Spacer(Modifier.width(6.dp))
                                Text(s.label, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text("$n", style = MaterialTheme.typography.headlineSmall, color = if (n > 0) cs.onSurface else cs.onSurfaceVariant)
                        }
                    }
                }
            }

            // ---- one-tap tidy-up, and undo for the last fix
            if (batch.isNotEmpty()) {
                Column(Modifier.padding(horizontal = 16.dp).padding(top = 12.dp)) {
                    FilledTonalButton(onClick = { onFixAll(batch); justFixed = "Tidied ${plural(batch.size, "line")}" }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.AutoFixHigh, null); Spacer(Modifier.width(8.dp)); Text("Tidy up ${batch.size} automatically")
                    }
                    Text(
                        "Spacing, brackets, extensions, punctuation and empty lines. One undo puts it all back.",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            justFixed?.let { msg ->
                Row(Modifier.padding(horizontal = 24.dp).padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CheckCircle, null, tint = cs.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(msg, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    if (canUndo) TextButton(onClick = { onUndo(); justFixed = null }) {
                        Icon(Icons.AutoMirrored.Outlined.Undo, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Undo")
                    }
                }
            }

            // ---- issues, grouped by rule
            if (shown.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.CheckCircle, null, tint = cs.primary, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(if (filter == null) "Formatted like a pro. Ready to send." else "Nothing here.", style = MaterialTheme.typography.titleMedium)
                }
            } else {
                LazyColumn(Modifier.heightIn(max = 520.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                    groups.forEach { (rule, list) ->
                        item(key = "h-${rule.name}") {
                            Column(Modifier.padding(top = 10.dp, bottom = 6.dp, start = 8.dp, end = 8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(severityColor(rule.severity)))
                                    Spacer(Modifier.width(8.dp))
                                    Text(rule.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                    Text("${list.size}", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                                    if (rule.batchSafe && list.size > 1) TextButton(onClick = { onFixAll(list); justFixed = "Fixed ${plural(list.size, "line")}" }) { Text("Fix all") }
                                }
                                Text(rule.why, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                            }
                        }
                        val visible = if (rule in expanded || list.size <= 5) list else list.take(4)
                        items(visible, key = { it.key }) { iss ->
                            IssueCard(
                                issue = iss, page = pageOf(iss),
                                onOpen = { if (iss.elementId == null) onTitlePage() else onJump(iss.elementId) },
                                onFix = { f -> fix(f, "Done: ${f.label.replaceFirstChar { it.lowercase() }}") },
                                onIgnore = { AppGraph.doctor.ignore(script.id, iss.key) },
                            )
                        }
                        if (visible.size < list.size) item(key = "more-${rule.name}") {
                            TextButton(onClick = { expanded = expanded + rule }, modifier = Modifier.padding(start = 4.dp)) { Text("Show all ${list.size}") }
                        }
                    }
                }
            }

            if (ignoredCount > 0) {
                Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${plural(ignoredCount, "note")} ignored", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
                    TextButton(onClick = { AppGraph.doctor.reset(script.id) }) { Text("Show again") }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IssueCard(issue: Issue, page: String?, onOpen: () -> Unit, onFix: (Fix) -> Unit, onIgnore: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = cs.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onOpen),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(issue.message, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(
                    if (issue.elementId == null) "Title page" else page?.let { "p. $it" } ?: "",
                    style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 2.dp),
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                issue.fixes.forEach { f -> TextButton(onClick = { onFix(f) }) { Text(f.label) } }
                TextButton(onClick = onIgnore) { Text("Ignore", color = cs.onSurfaceVariant) }
            }
        }
    }
}

private fun plural(n: Int, w: String) = "$n $w${if (n == 1) "" else "s"}"
