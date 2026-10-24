package com.amit.pageone.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Copyright
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.amit.pageone.core.Credit
import com.amit.pageone.core.TitlePage
import com.amit.pageone.ui.theme.ScriptFont
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Full-screen title page editor with a live preview of the printed page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TitlePageEditor(initial: TitlePage, fallbackTitle: String, onDismiss: () -> Unit, onSave: (TitlePage) -> Unit) {
    var tp by remember { mutableStateOf(initial.copy(title = initial.title.ifBlank { fallbackTitle })) }
    val cs = MaterialTheme.colorScheme

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(color = cs.background, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().imePadding()) {
                TopAppBar(
                    title = { Text("Title page") },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Close") } },
                    actions = {
                        Button(onClick = { onSave(tp); onDismiss() }, modifier = Modifier.padding(end = 8.dp)) { Text("Save") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background),
                )
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    TitlePreview(tp)

                    Card("Title") {
                        Input("Title", tp.title, caps = KeyboardCapitalization.Characters) { tp = tp.copy(title = it) }
                    }

                    Card("Writers", "Use “&” for a writing team, “and” for writers who worked separately — the WGA convention.") {
                        tp.credits.forEachIndexed { i, cr ->
                            Column(Modifier.fillMaxWidth()) {
                                Row(
                                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    TitlePage.ROLES.forEach { r ->
                                        Pill(r, r == cr.role) { tp = tp.copy(credits = tp.credits.toMutableList().also { it[i] = cr.copy(role = r) }) }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.weight(1f)) {
                                        Input("Names", cr.names, Icons.Outlined.Person, caps = KeyboardCapitalization.Words, placeholder = "Amit Sharma & Priya Rao") {
                                            tp = tp.copy(credits = tp.credits.toMutableList().also { l -> l[i] = cr.copy(names = it) })
                                        }
                                    }
                                    if (tp.credits.size > 1) IconButton(onClick = { tp = tp.copy(credits = tp.credits.filterIndexed { k, _ -> k != i }) }) {
                                        Icon(Icons.Outlined.RemoveCircleOutline, "Remove credit", tint = cs.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        TextButton(onClick = {
                            val next = if (tp.credits.any { it.role == "Story by" }) "Screenplay by" else "Story by"
                            tp = tp.copy(credits = tp.credits + Credit(next, ""))
                        }) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Add another credit") }
                    }

                    Card("Adaptation", trailing = {
                        Switch(checked = tp.isAdaptation, onCheckedChange = { tp = tp.copy(isAdaptation = it) })
                    }) {
                        if (!tp.isAdaptation) {
                            Text("Is this based on a book, play, article or true events?", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                        AnimatedVisibility(tp.isAdaptation) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    TitlePage.SOURCE_TYPES.forEach { t ->
                                        Pill(t.replaceFirstChar { it.uppercase() }, tp.sourceType == t) { tp = tp.copy(sourceType = t) }
                                    }
                                }
                                Input("Source title", tp.sourceTitle, Icons.Outlined.AutoStories, caps = KeyboardCapitalization.Words) { tp = tp.copy(sourceTitle = it) }
                                Input("Source author", tp.sourceAuthor, Icons.Outlined.Person, caps = KeyboardCapitalization.Words) { tp = tp.copy(sourceAuthor = it) }
                                if (tp.basedOnLine.isNotBlank()) Text(tp.basedOnLine, style = MaterialTheme.typography.bodySmall.copy(fontFamily = ScriptFont), color = cs.primary)
                            }
                        }
                    }

                    Card("Draft") {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("First Draft", "Second Draft", "Revised Draft", "Polish", "Shooting Script").forEach { d ->
                                Pill(d, tp.draft == d) { tp = tp.copy(draft = if (tp.draft == d) "" else d) }
                            }
                        }
                        Input("Draft label", tp.draft, placeholder = "e.g. Final Draft / Blue Revisions") { tp = tp.copy(draft = it) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { Input("Date", tp.draftDate) { tp = tp.copy(draftDate = it) } }
                            TextButton(onClick = { tp = tp.copy(draftDate = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date())) }) { Text("Today") }
                        }
                    }

                    Card("Contact", "Printed bottom-left. Every field is optional.") {
                        Input("Name", tp.contactName, Icons.Outlined.Badge, caps = KeyboardCapitalization.Words) { tp = tp.copy(contactName = it) }
                        Input("Email", tp.email, Icons.Outlined.AlternateEmail, type = KeyboardType.Email, caps = KeyboardCapitalization.None) { tp = tp.copy(email = it) }
                        Input("Phone", tp.phone, Icons.Outlined.Phone, type = KeyboardType.Phone, caps = KeyboardCapitalization.None) { tp = tp.copy(phone = it) }
                        Input("Address", tp.address, Icons.Outlined.Home, lines = 2) { tp = tp.copy(address = it) }
                        Input("Agent / manager", tp.representation, Icons.Outlined.WorkOutline, lines = 2, placeholder = "Name, agency, email") { tp = tp.copy(representation = it) }
                    }

                    Card("Rights", "Printed bottom-right.") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { Input("Copyright", tp.copyright, Icons.Outlined.Copyright) { tp = tp.copy(copyright = it) } }
                            TextButton(onClick = {
                                val year = Calendar.getInstance().get(Calendar.YEAR)
                                tp = tp.copy(copyright = "© $year ${tp.author.replace(" / ", ", ").ifBlank { "" }}".trim())
                            }) { Text("Fill") }
                        }
                        Input("Registration", tp.registration, placeholder = "e.g. WGA Reg. #1234567 / SWA Reg.") { tp = tp.copy(registration = it) }
                    }

                    Card("Notes") {
                        Input("Notes", tp.notes, lines = 2, placeholder = "e.g. Confidential — do not distribute") { tp = tp.copy(notes = it) }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

/** A small, faithful sketch of the printed title page. */
@Composable
private fun TitlePreview(tp: TitlePage) {
    val ink = Color(0xFF1A1A1A)
    val s = TextStyle(fontFamily = ScriptFont, fontSize = 8.sp, lineHeight = 10.sp, color = ink)
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
    Surface(
        shape = RoundedCornerShape(6.dp), color = Color(0xFFFFFEFB), shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth(0.62f).aspectRatio(8.5f / 11f),
    ) {
        Box(Modifier.fillMaxSize().padding(14.dp)) {
            Column(Modifier.align(Alignment.TopCenter).padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(tp.title.uppercase(), style = s.copy(fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline), textAlign = TextAlign.Center)
                Spacer(Modifier.height(14.dp))
                tp.credits.filter { it.names.isNotBlank() }.forEach {
                    Text(it.role, style = s); Spacer(Modifier.height(4.dp))
                    Text(it.names, style = s, textAlign = TextAlign.Center); Spacer(Modifier.height(8.dp))
                }
                if (tp.basedOnLine.isNotBlank()) Text(tp.basedOnLine, style = s, textAlign = TextAlign.Center)
            }
            Column(Modifier.align(Alignment.BottomStart)) { tp.contactLines.forEach { Text(it, style = s.copy(fontSize = 6.sp, lineHeight = 7.sp)) } }
            Column(Modifier.align(Alignment.BottomEnd), horizontalAlignment = Alignment.End) {
                listOf(tp.draft, tp.draftDate, tp.copyright, tp.registration).filter { it.isNotBlank() }
                    .forEach { Text(it, style = s.copy(fontSize = 6.sp, lineHeight = 7.sp)) }
            }
        }
    }
    }
}

@Composable
private fun Card(title: String, sub: String? = null, trailing: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(22.dp), color = cs.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                trailing?.invoke()
            }
            content()
        }
    }
}

@Composable
private fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) cs.primary else cs.surfaceContainerHighest)
            .border(1.dp, if (selected) cs.primary else cs.outlineVariant, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) { Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) cs.onPrimary else cs.onSurfaceVariant) }
}

@Composable
private fun Input(
    label: String,
    value: String,
    icon: ImageVector? = null,
    lines: Int = 1,
    type: KeyboardType = KeyboardType.Text,
    caps: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    placeholder: String? = null,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        leadingIcon = icon?.let { { Icon(it, null, Modifier.size(20.dp)) } },
        singleLine = lines == 1, minLines = lines,
        keyboardOptions = KeyboardOptions(capitalization = caps, keyboardType = type),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}
