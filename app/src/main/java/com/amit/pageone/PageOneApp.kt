package com.amit.pageone

import android.app.Application
import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Catches any crash, writes it to a file and lets Android close the app as usual.
 * The next launch shows the report (see [CrashReport]) instead of crashing in a loop,
 * so problems on a co-writer's phone can be shared without a USB cable.
 */
class PageOneApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Pages wrap and space Kannada, Hindi… lines by their printed size, as the desktop app does.
        com.amit.pageone.core.TextWrap.measurer = com.amit.pageone.io.PrintMeasure
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching { CrashReport.write(this, thread, e) }
            previous?.uncaughtException(thread, e)
        }
    }
}

object CrashReport {
    private fun file(c: Context) = File(c.filesDir, "last_crash.txt")

    fun write(c: Context, thread: Thread, e: Throwable) {
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        val v = runCatching { c.packageManager.getPackageInfo(c.packageName, 0).versionName }.getOrNull()
        val text = buildString {
            appendLine("Page One $v crash report")
            appendLine("Phone: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Thread: ${thread.name} · ${java.util.Date()}")
            appendLine()
            append(sw.toString())
        }
        file(c).writeText(text)
    }

    fun pending(c: Context): String? = file(c).takeIf { it.exists() }?.readText()

    fun clear(c: Context) { file(c).delete() }
}
