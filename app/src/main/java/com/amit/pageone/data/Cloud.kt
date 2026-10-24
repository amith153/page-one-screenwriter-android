package com.amit.pageone.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.amit.pageone.core.Script
import com.amit.pageone.io.ExportFormat
import com.amit.pageone.io.Exports
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class DriveAccount(val email: String, val name: String)
data class DriveFile(val id: String, val name: String, val modified: String, val scriptId: String?)

class DriveException(message: String, val httpCode: Int? = null) : Exception(message)

/**
 * Google Drive, connected per person with their own Google account.
 *
 * Uses the narrow `drive.file` permission: Page One can only see files it created itself
 * (a "Page One" folder in the user's Drive), never the rest of their Drive.
 */
class GoogleDrive(private val app: Context) {
    private val p = app.getSharedPreferences("pageone_drive", Context.MODE_PRIVATE)

    private val _account = MutableStateFlow(readAccount())
    val account: StateFlow<DriveAccount?> = _account.asStateFlow()
    private val _autoBackup = MutableStateFlow(p.getBoolean("auto", true))
    val autoBackup: StateFlow<Boolean> = _autoBackup.asStateFlow()
    private val _withPdf = MutableStateFlow(p.getBoolean("pdf", false))
    val withPdf: StateFlow<Boolean> = _withPdf.asStateFlow()
    private val _lastBackup = MutableStateFlow(p.getLong("last", 0L))
    val lastBackup: StateFlow<Long> = _lastBackup.asStateFlow()
    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    private val pending = HashMap<String, Job>()

    private fun readAccount(): DriveAccount? {
        val e = p.getString("email", null) ?: return null
        return DriveAccount(e, p.getString("name", "") ?: "")
    }

    fun setAutoBackup(on: Boolean) { p.edit().putBoolean("auto", on).apply(); _autoBackup.value = on }
    fun setWithPdf(on: Boolean) { p.edit().putBoolean("pdf", on).apply(); _withPdf.value = on }

    // ------------------------------------------------------------------ sign-in

    private fun request(): AuthorizationRequest =
        AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()

    /** Step 1 of connecting. If the result has a resolution, launch it, then call [finishConnect]. */
    suspend fun beginConnect(context: Context): AuthorizationResult =
        try { Identity.getAuthorizationClient(context).authorize(request()).await() }
        catch (e: Exception) { throw DriveException(explain(e)) }

    /** Turn the consent screen's result into an access token. */
    fun resultFromIntent(context: Context, data: Intent?): AuthorizationResult =
        try { Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data) }
        catch (e: Exception) { throw DriveException(explain(e)) }

    /** Step 2: look up who signed in and remember it. */
    suspend fun finishConnect(token: String) {
        val o = JSONObject(String(http("GET", "$API/about?fields=user(displayName,emailAddress)", token)))
        val u = o.getJSONObject("user")
        val acct = DriveAccount(u.optString("emailAddress"), u.optString("displayName"))
        p.edit().putString("email", acct.email).putString("name", acct.name).remove("folder").apply()
        _account.value = acct
        _status.value = null
    }

    fun disconnect() {
        val wasConnected = _account.value != null
        p.edit().clear().apply()
        _account.value = null
        _lastBackup.value = 0
        if (wasConnected) AppGraph.appScope.launch {
            runCatching {
                Identity.getAuthorizationClient(app)
                    .revokeAccess(RevokeAccessRequest.builder().build())
                    .await()
            }
        }
    }

    /** Ask Google Play services for a usable access token; never persist bearer tokens locally. */
    private suspend fun silentToken(): String? {
        val result = Identity.getAuthorizationClient(app).authorize(request()).await()
        return if (result.hasResolution()) null else result.accessToken?.takeIf(String::isNotBlank)
    }

    /** Evict a token rejected by Drive and ask Google Play services for a replacement. */
    private suspend fun refreshRejectedToken(rejected: String): String? {
        val client = Identity.getAuthorizationClient(app)
        client.clearToken(ClearTokenRequest.builder().setToken(rejected).build()).await()
        val result = client.authorize(request()).await()
        return if (result.hasResolution()) null else result.accessToken?.takeIf { it.isNotBlank() && it != rejected }
    }
    private suspend fun token(): String {
        val t = try { silentToken() } catch (e: Exception) { throw DriveException(explain(e)) }
        return t ?: throw DriveException("Google Drive needs you to sign in again (Settings → Cloud & backup).")
    }

    private fun explain(e: Exception): String {
        val code = (e as? ApiException)?.statusCode ?: (e.cause as? ApiException)?.statusCode
        val msg = e.message.orEmpty()
        return when {
            code == 10 || code == 8 || msg.contains("UNREGISTERED_ON_API_CONSOLE") || msg.contains("DEVELOPER_ERROR") ->
                "Google Drive isn't available in this version of Page One yet."
            code == 7 -> "No internet connection."
            code == 12501 || code == 16 -> "Sign-in was cancelled."
            else -> "Couldn't connect to Google Drive. Try again in a moment."
        }
    }

    // ------------------------------------------------------------------ REST

    private suspend fun http(method: String, url: String, token: String, body: ByteArray? = null, contentType: String? = null, override: String? = null): ByteArray {
        return try {
            httpOnce(method, url, token, body, contentType, override)
        } catch (e: DriveException) {
            if (e.httpCode != 401) throw e
            val refreshed = try { refreshRejectedToken(token) } catch (refreshError: Exception) {
                throw DriveException("Google Drive authorization expired. Reconnect your account in Settings → Cloud & backup.")
            } ?: throw DriveException("Google Drive authorization expired. Reconnect your account in Settings → Cloud & backup.")
            httpOnce(method, url, refreshed, body, contentType, override)
        }
    }

    private suspend fun httpOnce(method: String, url: String, token: String, body: ByteArray?, contentType: String?, override: String?): ByteArray =
        withContext(Dispatchers.IO) {
            val c = URL(url).openConnection() as HttpURLConnection
            try {
                c.requestMethod = method
                c.connectTimeout = 15000; c.readTimeout = 30000
                c.setRequestProperty("Authorization", "Bearer $token")
                if (override != null) c.setRequestProperty("X-HTTP-Method-Override", override)
                if (body != null) {
                    c.doOutput = true
                    c.setRequestProperty("Content-Type", contentType ?: "application/json; charset=UTF-8")
                    c.setFixedLengthStreamingMode(body.size)
                    c.outputStream.use { it.write(body) }
                }
                val code = c.responseCode
                val stream = if (code in 200..299) c.inputStream else c.errorStream
                val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
                if (code !in 200..299) {
                    val msg = runCatching { JSONObject(String(bytes)).getJSONObject("error").getString("message") }.getOrNull() ?: "HTTP $code"
                    throw DriveException(if (msg.contains("has not been used", true) || msg.contains("disabled", true))
                        "Google Drive isn't available in this version of Page One yet." else msg, code)
                        .also { if (code == 404) lastWas404 = true }
                }
                bytes
            } finally { c.disconnect() }
        }
    @Volatile private var lastWas404 = false

    private fun q(s: String) = URLEncoder.encode(s, "UTF-8")

    private suspend fun folderId(token: String): String {
        p.getString("folder", null)?.let { return it }
        val query = "name = 'Page One' and mimeType = '$FOLDER' and trashed = false"
        val found = JSONObject(String(http("GET", "$API/files?q=${q(query)}&fields=files(id)&spaces=drive", token))).getJSONArray("files")
        val id = if (found.length() > 0) found.getJSONObject(0).getString("id") else {
            val meta = JSONObject().put("name", "Page One").put("mimeType", FOLDER).toString().toByteArray()
            JSONObject(String(http("POST", "$API/files?fields=id", token, meta))).getString("id")
        }
        p.edit().putString("folder", id).apply()
        return id
    }

    /** [mime] is what Drive stores; [contentMime] is what [bytes] are, when Drive converts them (HTML → Google Doc). */
    private suspend fun upload(token: String, existingId: String?, name: String, mime: String, bytes: ByteArray, folder: String, props: Map<String, String>, contentMime: String = mime): String {
        val boundary = "pageone" + System.nanoTime()
        val meta = JSONObject().put("name", name)
        if (existingId == null || contentMime == mime) meta.put("mimeType", mime)
        if (props.isNotEmpty()) meta.put("appProperties", JSONObject(props))
        if (existingId == null) meta.put("parents", JSONArray().put(folder))
        val out = ByteArrayOutputStream()
        fun w(s: String) = out.write(s.toByteArray())
        w("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n")
        w("--$boundary\r\nContent-Type: $contentMime\r\n\r\n"); out.write(bytes); w("\r\n--$boundary--\r\n")
        val type = "multipart/related; boundary=$boundary"
        return if (existingId != null) {
            try {
                JSONObject(String(http("POST", "$UPLOAD/files/$existingId?uploadType=multipart&fields=id", token, out.toByteArray(), type, override = "PATCH"))).getString("id")
            } catch (e: DriveException) {
                if (!lastWas404) throw e
                lastWas404 = false
                upload(token, null, name, mime, bytes, folder, props, contentMime) // file was deleted from Drive — create again
            }
        } else JSONObject(String(http("POST", "$UPLOAD/files?uploadType=multipart&fields=id", token, out.toByteArray(), type))).getString("id")
    }

    private fun safeName(s: Script) = s.title.ifBlank { "Untitled" }.replace(Regex("[\\\\/:*?\"<>|]"), "").trim()

    /** Upload (or update) one script's backup. */
    suspend fun backup(script: Script) {
        val token = token()
        val folder = try { folderId(token) } catch (e: DriveException) { p.edit().remove("folder").apply(); folderId(token) }
        val bytes = ScriptStore.encode(script).put("pageone", 1).put("sender", AppGraph.collab.displayName()).toString().toByteArray()
        val key = "file_" + script.id
        val id = upload(token, p.getString(key, null), safeName(script) + ".pageone", "application/octet-stream", bytes, folder, mapOf("scriptId" to script.id))
        p.edit().putString(key, id).apply()
        if (_withPdf.value) {
            val pdf = ByteArrayOutputStream()
            val opts = runCatching { Exports.annotatedOptions(script) }.getOrDefault(com.amit.pageone.io.PdfExporter.Options())
            withContext(Dispatchers.Default) { Exports.write(app, script, AppGraph.settings.state.value, ExportFormat.PDF, pdf, opts) }
            val pk = "pdf_" + script.id
            val pid = upload(token, p.getString(pk, null), safeName(script) + ".pdf", "application/pdf", pdf.toByteArray(), folder, mapOf("scriptPdf" to script.id))
            p.edit().putString(pk, pid).apply()
        }
        val now = System.currentTimeMillis()
        p.edit().putLong("last", now).apply()
        _lastBackup.value = now
        _status.value = null
    }

    suspend fun backupAll(): Int {
        var n = 0
        for (m in AppGraph.store.library.value) {
            val s = AppGraph.store.load(m.id) ?: continue
            backup(s); n++
        }
        return n
    }

    /** Called after every local save; uploads ~20 s after you stop writing. */
    fun onSaved(script: Script) {
        if (_account.value == null || !_autoBackup.value) return
        pending.remove(script.id)?.cancel()
        pending[script.id] = AppGraph.appScope.launch {
            delay(20_000)
            runCatching { backup(script) }.onFailure { _status.value = it.message }
            pending.remove(script.id)
        }
    }

    // ------------------------------------------------------------------ note references (pictures, audio, video)

    private suspend fun referencesFolder(token: String): String {
        p.getString("refs_folder", null)?.let { return it }
        val parent = folderId(token)
        val query = "name = 'References' and mimeType = '$FOLDER' and '$parent' in parents and trashed = false"
        val found = JSONObject(String(http("GET", "$API/files?q=${q(query)}&fields=files(id)&spaces=drive", token))).getJSONArray("files")
        val id = if (found.length() > 0) found.getJSONObject(0).getString("id") else {
            val meta = JSONObject().put("name", "References").put("mimeType", FOLDER).put("parents", JSONArray().put(parent)).toString().toByteArray()
            JSONObject(String(http("POST", "$API/files?fields=id", token, meta))).getString("id")
        }
        p.edit().putString("refs_folder", id).apply()
        return id
    }

    private suspend fun childFolder(token: String, name: String, parent: String): String {
        val meta = JSONObject().put("name", name).put("mimeType", FOLDER).put("parents", JSONArray().put(parent)).toString().toByteArray()
        return JSONObject(String(http("POST", "$API/files?fields=id", token, meta))).getString("id")
    }

    /** Anyone with the link can view, so the PDF's links open for whoever you send it to. */
    private suspend fun shareWithLink(token: String, id: String) {
        http("POST", "$API/files/$id/permissions", token, JSONObject().put("type", "anyone").put("role", "reader").toString().toByteArray())
    }

    /** Still there and not in the bin? */
    private suspend fun alive(token: String, id: String): Boolean = try {
        !JSONObject(String(http("GET", "$API/files/$id?fields=id,trashed", token))).optBoolean("trashed")
    } catch (e: DriveException) { if (e.httpCode == 404) false else throw e }

    /** Drive › Page One › References › <script title>. */
    private suspend fun scriptReferencesFolder(token: String, script: Script): String {
        val key = "sref_" + script.id
        p.getString(key, null)?.let { if (alive(token, it)) return it }
        val refs = p.getString("refs_folder", null)?.takeIf { alive(token, it) }
            ?: run { p.edit().remove("refs_folder").remove("folder").apply(); referencesFolder(token) }
        val id = childFolder(token, safeName(script), refs)
        p.edit().putString(key, id).apply()
        return id
    }

    /**
     * Everything pinned to one note, in its own Drive folder that anyone with the link can open:
     * Drive › Page One › References › <script> › “<noted words>”. Pictures, audio and video are
     * uploaded into it (once each), and a "Note" doc holds the note, its replies and its web links.
     * Returns the link the PDF puts on the noted words: the folder, or just the doc for a note with no files.
     *
     * Not called yet: links on noted words in the PDF are switched off for now (19 Oct 2026), to come back later.
     */
    suspend fun shareNote(script: Script, c: com.amit.pageone.core.Comment): String = withContext(Dispatchers.IO) {
        val token = token()
        val media = AppGraph.media
        val files = c.attachments.filter { it.kind != com.amit.pageone.core.AttachmentKind.LINK && media.exists(it.file) }
        val webLinks = c.attachments.filter { it.kind == com.amit.pageone.core.AttachmentKind.LINK }
        val title = "“" + c.quote.replace(Regex("\\s+"), " ").trim().take(48).let { if (c.quote.length > 48) "$it…" else it } + "” — " + c.author
        // The note's folder (recreated if it was deleted from Drive).
        val fk = "nref_" + c.id
        var folder = p.getString(fk, null)?.takeIf { alive(token, it) }
        if (folder == null) {
            p.edit().remove(fk).remove("nrefname_" + c.id).remove("nfiles_" + c.id).remove("ndoc_" + c.id).remove("ndochash_" + c.id).apply()
            folder = childFolder(token, title, scriptReferencesFolder(token, script))
            shareWithLink(token, folder)
            p.edit().putString(fk, folder).putString("nrefname_" + c.id, title).apply()
        } else if (p.getString("nrefname_" + c.id, null) != title) {
            http("POST", "$API/files/$folder?fields=id", token, JSONObject().put("name", title).toString().toByteArray(), override = "PATCH")
            p.edit().putString("nrefname_" + c.id, title).apply()
        }
        // Pictures, audio, video: upload what's new, bin what was taken off the note.
        val known = runCatching { JSONObject(p.getString("nfiles_" + c.id, "{}")!!) }.getOrDefault(JSONObject())
        val now = JSONObject()
        for (a in files) {
            val id = known.optString(a.file).takeIf { it.isNotEmpty() && alive(token, it) }
                ?: uploadReference(a.file, a.name.ifBlank { a.kind.name.lowercase() }, folder).first.also { shareWithLink(token, it) }
            now.put(a.file, id)
        }
        known.keys().forEach { ref ->
            if (!now.has(ref)) runCatching {
                http("POST", "$API/files/${known.getString(ref)}?fields=id", token, JSONObject().put("trashed", true).toString().toByteArray(), override = "PATCH")
            }
        }
        p.edit().putString("nfiles_" + c.id, now.toString()).apply()
        // The note itself, its replies and its web links, as a Google Doc.
        val html = noteHtml(script, c, webLinks, files.map { it.name.ifBlank { it.kind.name.lowercase() } })
        val hash = html.hashCode().toString()
        val dk = "ndoc_" + c.id
        var doc = p.getString(dk, null)
        if (doc == null || p.getString("ndochash_" + c.id, null) != hash || !alive(token, doc)) {
            val existing = doc?.takeIf { alive(token, it) }
            doc = try {
                upload(token, existing, "Note from ${c.author}", DOC, html.toByteArray(), folder, emptyMap(), "text/html")
            } catch (e: DriveException) {
                if (existing == null) throw e
                // Couldn't rewrite it in place: make a fresh doc and bin the old one.
                upload(token, null, "Note from ${c.author}", DOC, html.toByteArray(), folder, emptyMap(), "text/html").also {
                    runCatching { http("POST", "$API/files/$existing?fields=id", token, JSONObject().put("trashed", true).toString().toByteArray(), override = "PATCH") }
                }
            }
            if (doc != existing) shareWithLink(token, doc)
            p.edit().putString(dk, doc).putString("ndochash_" + c.id, hash).apply()
        }
        if (files.isEmpty()) "https://docs.google.com/document/d/$doc/edit?usp=sharing"
        else "https://drive.google.com/drive/folders/$folder?usp=sharing"
    }

    private fun noteHtml(script: Script, c: com.amit.pageone.core.Comment, links: List<com.amit.pageone.core.Attachment>, files: List<String>): String {
        fun esc(s: String) = android.text.TextUtils.htmlEncode(s).replace("\n", "<br>")
        val date = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(c.at))
        val sb = StringBuilder("<html><body style=\"font-family:Arial\">")
        sb.append("<p style=\"color:#777\">${esc(script.title.ifBlank { "Untitled" })} · note from ${esc(c.author)} · $date</p>")
        sb.append("<blockquote style=\"font-family:'Courier New';border-left:4px solid #ccc;padding-left:12px\">${esc(c.quote)}</blockquote>")
        if (c.text.isNotBlank()) sb.append("<p style=\"font-size:13pt\">${esc(c.text)}</p>")
        if (c.replies.isNotEmpty()) {
            sb.append("<h3>Replies</h3>")
            c.replies.forEach { r -> sb.append("<p><b>${esc(r.author)}:</b> ${esc(r.text)}</p>") }
        }
        if (links.isNotEmpty()) {
            sb.append("<h3>Links</h3><ul>")
            links.forEach { l -> sb.append("<li><a href=\"${esc(l.file)}\">${esc(l.name.ifBlank { l.file })}</a></li>") }
            sb.append("</ul>")
        }
        if (files.isNotEmpty()) {
            sb.append("<h3>In this folder</h3><ul>")
            files.forEach { sb.append("<li>${esc(it)}</li>") }
            sb.append("</ul>")
        }
        return sb.append("</body></html>").toString()
    }

    /** Stream one picture / audio / video file into [folder]; returns (file id, view link). */
    private suspend fun uploadReference(ref: String, name: String, folder: String): Pair<String, String> = withContext(Dispatchers.IO) {
        var token = token()
        val media = AppGraph.media
        val mime = media.mimeOf(ref)
        val size = runCatching { if (media.isUri(ref)) app.contentResolver.openFileDescriptor(Uri.parse(ref), "r")?.use { it.statSize } ?: -1L else media.file(ref).length() }.getOrDefault(-1L)
        // Resumable upload: start a session, then stream the file (videos can be large).
        val meta = JSONObject().put("name", name).put("mimeType", mime).put("parents", JSONArray().put(folder)).toString().toByteArray()
        var session: String? = null
        for (attempt in 0..1) {
            val start = URL("$UPLOAD/files?uploadType=resumable&fields=id,webViewLink").openConnection() as HttpURLConnection
            try {
                start.requestMethod = "POST"; start.doOutput = true
                start.setRequestProperty("Authorization", "Bearer $token")
                start.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                start.setRequestProperty("X-Upload-Content-Type", mime)
                if (size > 0) start.setRequestProperty("X-Upload-Content-Length", size.toString())
                start.outputStream.use { it.write(meta) }
                val code = start.responseCode
                if (code !in 200..299) {
                    if (code == 401 && attempt == 0) {
                        token = refreshRejectedToken(token)
                            ?: throw DriveException("Google Drive authorization expired. Reconnect your account in Settings → Cloud & backup.")
                        continue
                    }
                    val stream = start.errorStream
                    val message = stream?.bufferedReader()?.use { it.readText() }?.let {
                        runCatching { JSONObject(it).getJSONObject("error").getString("message") }.getOrNull()
                    } ?: "HTTP $code"
                    throw DriveException("Couldn't start uploading $name: $message", code)
                }
                session = start.getHeaderField("Location")
                    ?: throw DriveException("Drive didn't accept the upload")
                break
            } finally { start.disconnect() }
        }
        val uploadSession = session ?: throw DriveException("Drive didn't accept the upload")
        val put = URL(uploadSession).openConnection() as HttpURLConnection
        val res = try {
            put.requestMethod = "PUT"; put.doOutput = true
            put.setRequestProperty("Content-Type", mime)
            if (size > 0) put.setFixedLengthStreamingMode(size) else put.setChunkedStreamingMode(256 * 1024)
            put.readTimeout = 120_000
            (media.open(ref) ?: throw DriveException("$name isn't on this phone any more")).use { input -> put.outputStream.use { input.copyTo(it, 256 * 1024) } }
            if (put.responseCode !in 200..299) throw DriveException("Uploading $name failed (HTTP ${put.responseCode})")
            JSONObject(put.inputStream.bufferedReader().readText())
        } finally { put.disconnect() }
        val id = res.getString("id")
        id to res.optString("webViewLink").ifBlank { "https://drive.google.com/file/d/$id/view" }
    }

    suspend fun list(): List<DriveFile> {
        val token = token()
        val folder = folderId(token)
        val query = "'$folder' in parents and trashed = false and name contains '.pageone'"
        val arr = JSONObject(String(http("GET", "$API/files?q=${q(query)}&fields=files(id,name,modifiedTime,appProperties)&orderBy=modifiedTime desc&pageSize=200", token))).getJSONArray("files")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            DriveFile(o.getString("id"), o.optString("name"), o.optString("modifiedTime"), o.optJSONObject("appProperties")?.optString("scriptId"))
        }
    }

    /** Download a backup and bring it in (merged with your copy if you already have the script). */
    suspend fun restore(f: DriveFile): CollabStore.Received {
        val bytes = http("GET", "$API/files/${f.id}?alt=media", token())
        val po = CollabStore.parsePageOne(bytes) ?: throw DriveException("That file isn't a Page One backup")
        return AppGraph.collab.receive(po.first, "Google Drive")
    }

    companion object {
        const val SCOPE = "https://www.googleapis.com/auth/drive.file"
        private const val API = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        private const val FOLDER = "application/vnd.google-apps.folder"
        private const val DOC = "application/vnd.google-apps.document"
    }
}

/**
 * "Other drives": Dropbox, OneDrive, Box, Nextcloud, Samsung/Mi Cloud… — anything that
 * appears in Android's file picker. You pick a place once; Page One keeps that file
 * up to date every time you save, using the drive app already signed in on your phone.
 */
class LinkedCopies(private val app: Context) {
    data class Link(val scriptId: String, val uri: String, val format: ExportFormat, val label: String)

    private val p = app.getSharedPreferences("pageone_links", Context.MODE_PRIVATE)
    private val _links = MutableStateFlow(read())
    val links: StateFlow<List<Link>> = _links.asStateFlow()
    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()
    private val pending = HashMap<String, Job>()

    private fun read(): List<Link> = runCatching {
        val a = JSONArray(p.getString("links", "[]"))
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Link(o.getString("s"), o.getString("u"), ExportFormat.valueOf(o.getString("f")), o.optString("l"))
        }
    }.getOrDefault(emptyList())

    private fun write(l: List<Link>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("s", it.scriptId).put("u", it.uri).put("f", it.format.name).put("l", it.label)) }
        p.edit().putString("links", a.toString()).apply()
        _links.value = l
    }

    fun forScript(id: String) = _links.value.filter { it.scriptId == id }

    suspend fun add(script: Script, uri: Uri, format: ExportFormat) {
        runCatching {
            app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        val label = providerLabel(uri)
        write(_links.value.filterNot { it.uri == uri.toString() } + Link(script.id, uri.toString(), format, label))
        writeNow(script)
    }

    fun remove(link: Link) {
        runCatching { app.contentResolver.releasePersistableUriPermission(Uri.parse(link.uri), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        write(_links.value.filterNot { it.uri == link.uri })
    }

    private fun providerLabel(uri: Uri): String {
        val a = uri.authority ?: return "Cloud"
        return when {
            a.contains("google.android.apps.docs") -> "Google Drive"
            a.contains("dropbox") -> "Dropbox"
            a.contains("skydrive") || a.contains("onedrive") -> "OneDrive"
            a.contains("box") -> "Box"
            a.contains("nextcloud") -> "Nextcloud"
            a.contains("owncloud") -> "ownCloud"
            a.contains("externalstorage") -> "Phone storage"
            a.contains("downloads") -> "Downloads"
            else -> a.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }

    suspend fun writeNow(script: Script) = withContext(Dispatchers.IO) {
        for (l in forScript(script.id)) {
            runCatching {
                val opts = if (l.format == ExportFormat.PDF) runCatching { Exports.annotatedOptions(script) }.getOrDefault(com.amit.pageone.io.PdfExporter.Options()) else com.amit.pageone.io.PdfExporter.Options()
                app.contentResolver.openOutputStream(Uri.parse(l.uri), "wt")!!.use {
                    Exports.write(app, script, AppGraph.settings.state.value, l.format, it, opts)
                }
            }.onFailure { _status.value = "Couldn't update ${l.label} copy: ${it.message}" }
        }
    }

    fun onSaved(script: Script) {
        if (forScript(script.id).isEmpty()) return
        pending.remove(script.id)?.cancel()
        pending[script.id] = AppGraph.appScope.launch { delay(10_000); writeNow(script); pending.remove(script.id) }
    }
}
