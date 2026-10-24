package com.amit.pageone.io

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.amit.pageone.core.Fdx
import com.amit.pageone.core.Fountain
import com.amit.pageone.core.IdGen
import com.amit.pageone.core.Script
import com.amit.pageone.data.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream

enum class ExportFormat(val label: String, val ext: String, val mime: String) {
    PDF("PDF", "pdf", "application/pdf"),
    FDX("Final Draft (.fdx)", "fdx", "application/octet-stream"),
    FOUNTAIN("Fountain", "fountain", "text/plain"),
}

object Exports {

    fun fileName(script: Script, f: ExportFormat): String {
        val base = script.title.ifBlank { "Untitled" }.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifEmpty { "Script" }
        return "$base.${f.ext}"
    }

    fun write(context: Context, script: Script, settings: AppSettings, f: ExportFormat, out: OutputStream, pdf: PdfExporter.Options = PdfExporter.Options()) {
        when (f) {
            ExportFormat.PDF -> PdfExporter.export(context, script, settings, out, pdf)
            ExportFormat.FDX -> out.write(Fdx.write(script).toByteArray())
            ExportFormat.FOUNTAIN -> out.write(Fountain.write(script).toByteArray())
        }
        out.flush()
    }

    /** Save to a user-picked location (Storage Access Framework uri). */
    suspend fun saveTo(context: Context, uri: Uri, script: Script, settings: AppSettings, f: ExportFormat, pdf: PdfExporter.Options = PdfExporter.Options()) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { write(context, script, settings, f, it, pdf) }
        Unit
    }

    /** Write to cache and open the Android share sheet. */
    suspend fun share(context: Context, script: Script, settings: AppSettings, f: ExportFormat, pdf: PdfExporter.Options = PdfExporter.Options()) {
        val file = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, fileName(script, f))
            file.outputStream().use { write(context, script, settings, f, it, pdf) }
            file
        }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = f.mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, script.title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share ${f.label}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Write any file to the share cache and open the share sheet (reports, sides, CSVs). */
    suspend fun shareFile(context: Context, name: String, mime: String, subject: String, write: (OutputStream) -> Unit) {
        val file = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val safe = name.replace(Regex("[^A-Za-z0-9 ._-]"), "").trim().ifEmpty { "export" }
            File(dir, safe).also { f -> f.outputStream().use { write(it); it.flush() } }
        }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share $subject").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun baseName(script: Script) = script.title.ifBlank { "Untitled" }.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifEmpty { "Script" }

    /** PDF options for copies written in the background (synced copies, Drive backup): open notes colour-marked. */
    fun annotatedOptions(script: Script): PdfExporter.Options =
        if (script.extras.comments.none { !it.resolved }) PdfExporter.Options() else PdfExporter.Options(notes = true)

    data class Imported(val title: String, val parsed: Fountain.Parsed, val pageOne: Pair<com.amit.pageone.core.Script, String>? = null)

    /** Result of opening any supported file: the script to open and a message to show. */
    data class Outcome(val scriptId: String, val message: String?)

    /**
     * Open a .fountain / .fdx (creates a new script) or a co-writer's .pageone file
     * (merged into your copy if you have it).
     */
    suspend fun importAny(context: Context, uri: Uri): Outcome {
        val imp = import(context, uri)
        val po = imp.pageOne
        if (po != null) {
            val r = com.amit.pageone.data.AppGraph.collab.receive(po.first, po.second)
            val m = r.merged
            val msg = when {
                r.isNew -> "Added “${po.first.title}” from ${po.second}"
                m == null || m.total == 0 -> "Already up to date with ${po.second}"
                else -> "Merged from ${po.second}: ${m.changed} changed, ${m.added} added, ${m.deleted} removed" +
                    if (m.conflicts > 0) " · ${m.conflicts} conflicts marked with notes" else ""
            }
            return Outcome(r.scriptId, msg)
        }
        val s = com.amit.pageone.data.AppGraph.store.create(imp.title, imp.parsed.elements.ifEmpty { null }, imp.parsed.titlePage.copy(title = imp.title))
        return Outcome(s.id, null)
    }

    /** Share a .pageone copy for a co-writer to merge. */
    suspend fun sharePageOne(context: Context, script: Script, bytes: ByteArray) {
        val file = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val base = script.title.ifBlank { "Untitled" }.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifEmpty { "Script" }
            File(dir, "$base.pageone").also { it.writeBytes(bytes) }
        }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Here's my latest draft of “${script.title}”. Open it with Page One to merge it into yours.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Send to co-writer").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Import .fountain / .txt / .fdx from a content uri. */
    suspend fun import(context: Context, uri: Uri): Imported = withContext(Dispatchers.IO) {
        val name = displayName(context, uri) ?: "Imported"
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
        val head = String(bytes, 0, minOf(bytes.size, 400))
        if (name.endsWith(".pageone", true) || head.trimStart().startsWith("{")) {
            com.amit.pageone.data.CollabStore.parsePageOne(bytes)?.let { po ->
                return@withContext Imported(po.first.title, Fountain.Parsed(po.first.titlePage, po.first.elements), po)
            }
        }
        val isFdx = name.endsWith(".fdx", true) || head.contains("<FinalDraft")
        val parsed = if (isFdx) Fdx.parse(bytes, IdGen(1)) else Fountain.parse(String(bytes), IdGen(1))
        val title = parsed.titlePage.title.ifBlank { name.substringBeforeLast('.') }
        Imported(title, parsed)
    }

    private fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()
}
