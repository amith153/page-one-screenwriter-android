package com.amit.pageone.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/**
 * Pictures, audio and video used by mood boards, the bible, notes and voice ideas.
 *
 * A reference is either:
 *  - a "content://" URI to a file in the writer's own storage (gallery, Music, Recordings, Downloads…),
 *    kept readable with a persistent permission — nothing is duplicated; or
 *  - a plain file name in the app's media folder (mood-board copies, downscaled to 1600 px).
 */
class MediaFiles(private val context: Context) {
    private val dir = File(context.filesDir, "media").apply { mkdirs() }
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun isUri(ref: String) = ref.startsWith("content://") || ref.startsWith("file://")
    fun file(name: String) = File(dir, name)

    /** Does the referenced file still exist / can we still read it? */
    fun exists(ref: String): Boolean = if (isUri(ref)) runCatching { context.contentResolver.openFileDescriptor(Uri.parse(ref), "r")?.use { true } ?: false }.getOrDefault(false) else file(ref).exists()

    fun open(ref: String): InputStream? = runCatching { if (isUri(ref)) context.contentResolver.openInputStream(Uri.parse(ref)) else file(ref).inputStream() }.getOrNull()

    fun mimeOf(ref: String): String = if (isUri(ref)) context.contentResolver.getType(Uri.parse(ref)) ?: "application/octet-stream"
    else when (ref.substringAfterLast('.').lowercase()) { "jpg", "jpeg" -> "image/jpeg"; "png" -> "image/png"; "m4a", "mp4a" -> "audio/mp4"; "mp3" -> "audio/mpeg"; "mp4" -> "video/mp4"; else -> "application/octet-stream" }

    /** Keep read access to a file the writer picked from their own storage, across restarts. */
    fun keep(uri: Uri): String {
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        return uri.toString()
    }

    fun displayName(ref: String): String = if (isUri(ref)) runCatching {
        context.contentResolver.query(Uri.parse(ref), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()?.substringBeforeLast('.') ?: "File" else ref

    /** Copy a picked image in (mood boards / photos). Returns the stored file name, or null if it couldn't be read. */
    suspend fun import(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1600) sample *= 2
            val bmp = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching null
            save(bmp)
        }.getOrNull()
    }

    /** A new empty file in the app's media folder (recordings start here). */
    fun newFile(ext: String): Pair<String, File> {
        val name = java.util.UUID.randomUUID().toString() + "." + ext
        return name to File(dir, name)
    }

    /**
     * Move a finished recording into the writer's own storage (Recordings/Page One), so it's theirs —
     * visible in their Files app, backed up with their phone. Returns the new reference.
     * (Android 8–9 keep it inside the app.)
     */
    suspend fun publishRecording(name: String, title: String): String = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < 29) return@withContext name
        val src = file(name)
        runCatching {
            val folder = if (Build.VERSION.SDK_INT >= 31) Environment.DIRECTORY_RECORDINGS else Environment.DIRECTORY_MUSIC
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$title ${java.text.SimpleDateFormat("yyyy-MM-dd HH.mm.ss", java.util.Locale.US).format(java.util.Date())}.m4a")
                put(MediaStore.MediaColumns.MIME_TYPE, "audio/mp4")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/Page One")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return@runCatching name
            context.contentResolver.openOutputStream(uri)?.use { out -> src.inputStream().use { it.copyTo(out) } }
            context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            src.delete()
            uri.toString()
        }.getOrDefault(name)
    }

    /** Remove something Page One created (a recording). Never touches files the writer picked. */
    fun deleteOwn(ref: String) {
        if (isUri(ref)) runCatching { if (ref.contains("/audio/media/")) context.contentResolver.delete(Uri.parse(ref), null, null) } else file(ref).delete()
    }

    /** Length of an audio or video file in ms (0 if unknown). */
    fun durationOf(ref: String): Long = runCatching {
        val r = MediaMetadataRetriever()
        try {
            if (isUri(ref)) r.setDataSource(context, Uri.parse(ref)) else r.setDataSource(file(ref).path)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
        } finally { r.release() }
    }.getOrDefault(0L)

    /** A frame from a video, for its thumbnail. */
    suspend fun videoFrame(ref: String): Bitmap? = withContext(Dispatchers.IO) {
        cache.get("v:$ref")?.let { return@withContext it }
        runCatching {
            val r = MediaMetadataRetriever()
            try {
                if (isUri(ref)) r.setDataSource(context, Uri.parse(ref)) else r.setDataSource(file(ref).path)
                r.getFrameAtTime(1_000_000)
            } finally { r.release() }
        }.getOrNull()?.also { cache.put("v:$ref", it) }
    }

    suspend fun save(bmp: Bitmap): String = withContext(Dispatchers.IO) {
        val name = java.util.UUID.randomUUID().toString() + ".jpg"
        val scaled = if (maxOf(bmp.width, bmp.height) > 1600) {
            val f = 1600f / maxOf(bmp.width, bmp.height)
            Bitmap.createScaledBitmap(bmp, (bmp.width * f).toInt(), (bmp.height * f).toInt(), true)
        } else bmp
        File(dir, name).outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        name
    }

    /** Load (and cache) an image, decoded small enough for a [maxPx] wide thumbnail. */
    suspend fun load(ref: String, maxPx: Int = 800): Bitmap? = withContext(Dispatchers.IO) {
        if (ref.isBlank()) return@withContext null
        val key = "$ref@$maxPx"
        cache.get(key)?.let { return@withContext it }
        runCatching {
            // A bounds-only decode always returns null, so check the size it reports, not the result.
            val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            (open(ref) ?: return@runCatching null).use { BitmapFactory.decodeStream(it, null, b) }
            if (b.outWidth <= 0) return@runCatching null
            var s = 1
            while (b.outWidth / (s * 2) >= maxPx) s *= 2
            open(ref)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) }
        }.getOrNull()?.also { cache.put(key, it) }
    }
}
