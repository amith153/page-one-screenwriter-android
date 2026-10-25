package com.amit.pageone.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.amit.pageone.data.AppGraph
import com.amit.pageone.data.AppSettings
import com.amit.pageone.io.Exports
import com.amit.pageone.ui.editor.EditorScreen
import com.amit.pageone.ui.library.LibraryScreen
import com.amit.pageone.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

/** Very small router: "library", "editor/<id>", "settings". */
@Composable
fun AppRoot(
    settings: AppSettings,
    pendingImport: Uri?,
    onImportConsumed: () -> Unit,
    keepScreenOn: (Boolean) -> Unit,
    /** From a notification or widget: "inbox" or "script:<id>". */
    pendingOpen: String? = null,
    onOpenConsumed: () -> Unit = {},
) {
    var stack by rememberSaveable { mutableStateOf(listOf("library")) }
    val scope0 = rememberCoroutineScope()

    if (!settings.onboarded) {
        com.amit.pageone.ui.library.Onboarding { sample ->
            if (sample) scope0.launch { val id = com.amit.pageone.data.SampleScript.create(); stack = listOf("library", "editor/$id") }
        }
        return
    }
    val route = stack.last()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    // Each open editor gets its own ViewModelStore, cleared when you leave it,
    // so closed scripts don't linger in memory and re-opening always reloads.
    val stores = remember { HashMap<String, ViewModelStore>() }

    fun push(r: String) { stack = stack + r }
    fun pop() {
        if (stack.size <= 1) return
        val popped = stack.last()
        stack = stack.dropLast(1)
        if (popped !in stack) stores.remove(popped)?.clear()
    }

    BackHandler(enabled = stack.size > 1) { pop() }

    LaunchedEffect(pendingImport) {
        val uri = pendingImport ?: return@LaunchedEffect
        onImportConsumed()
        scope.launch {
            runCatching {
                // Close open editors first so their final saves land before a merge reads the file.
                stores.values.forEach { it.clear() }; stores.clear()
                stack = listOf("library")
                kotlinx.coroutines.delay(400)
                val out = Exports.importAny(ctx, uri)
                out.message?.let { android.widget.Toast.makeText(ctx, it, android.widget.Toast.LENGTH_LONG).show() }
                stack = listOf("library", "editor/${out.scriptId}")
            }.onFailure { android.widget.Toast.makeText(ctx, "Couldn't open file: ${it.message}", android.widget.Toast.LENGTH_LONG).show() }
        }
    }

    LaunchedEffect(pendingOpen) {
        val p = pendingOpen ?: return@LaunchedEffect
        onOpenConsumed()
        when {
            p == "inbox" -> stack = listOf("library", "inbox")
            p.startsWith("script:") -> { stores.values.forEach { it.clear() }; stores.clear(); stack = listOf("library", "editor/" + p.removePrefix("script:")) }
        }
    }

    LaunchedEffect(route, settings.keepScreenOn) {
        keepScreenOn(route.startsWith("editor/") && settings.keepScreenOn)
    }

    AnimatedContent(
        targetState = route,
        transitionSpec = {
            (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.98f)) togetherWith fadeOut(tween(150))
        },
        label = "route",
    ) { r ->
        when {
            r == "library" -> LibraryScreen(
                settings = settings,
                onOpen = { id -> push("editor/$id") },
                onSettings = { push("settings") },
                onInbox = { push("inbox") },
            )
            r.startsWith("editor/") -> {
                val vmStore = stores.getOrPut(r) { ViewModelStore() }
                val owner = remember(vmStore) { object : ViewModelStoreOwner { override val viewModelStore: ViewModelStore = vmStore } }
                CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                    EditorScreen(
                        scriptId = r.removePrefix("editor/"),
                        settings = settings,
                        onBack = { pop() },
                        onSettings = { push("settings") },
                    )
                }
            }
            r == "settings" -> SettingsScreen(settings = settings, onBack = { pop() })
            r == "inbox" -> com.amit.pageone.ui.library.InboxScreen(onBack = { pop() }, onOpenScript = { id -> stack = listOf("library", "editor/$id") })
        }
    }
}
