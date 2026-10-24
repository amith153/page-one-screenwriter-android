package com.amit.pageone

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.compose.runtime.LaunchedEffect
import com.amit.pageone.ui.theme.isDark
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.animation.doOnEnd
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amit.pageone.data.AppGraph
import com.amit.pageone.ui.AppRoot
import com.amit.pageone.ui.theme.PageOneTheme

class MainActivity : ComponentActivity() {

    /** A file (fountain / fdx) handed to us by another app, waiting to be imported. */
    var pendingImport by mutableStateOf<Uri?>(null)

    /** A live-collaboration invite (code or link) waiting to be joined. */
    var pendingJoin by mutableStateOf<String?>(null)

    /** A notification or widget asked to open the inbox ("inbox") or a script ("script:<id>"). */
    var pendingOpen by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        CrashReport.pending(this)?.let { report ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) setTheme(R.style.Theme_PageOne)
            super.onCreate(savedInstanceState)
            showCrash(report)
            return
        }
        AppGraph.init(this)
        // Android 8–11 show the launch theme as a classic splash; switch to the real theme now.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) setTheme(R.style.Theme_PageOne)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        holdSplash()
        handleIntent(intent)
        com.amit.pageone.data.Notifier.scheduleAll(this)

        setContent {
            val settings by AppGraph.settings.state.collectAsStateWithLifecycle()
            val dark = isDark(settings)
            LaunchedEffect(dark) {
                val t = android.graphics.Color.TRANSPARENT
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t),
                )
            }
            PageOneTheme(settings) {
                AppRoot(
                    settings = settings,
                    pendingImport = pendingImport,
                    onImportConsumed = { pendingImport = null },
                    pendingJoin = pendingJoin,
                    onJoinConsumed = { pendingJoin = null },
                    pendingOpen = pendingOpen,
                    onOpenConsumed = { pendingOpen = null },
                    keepScreenOn = { on ->
                        if (on) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    },
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        runCatching { AppGraph.onAppStop() }
    }

    /** Last launch crashed: show what happened, offer to share it, then try again. Plain views only. */
    private fun showCrash(report: String) {
        val d = resources.displayMetrics.density
        val pad = (20 * d).toInt()
        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(pad, pad * 3, pad, pad * 2)
            setBackgroundColor(0xFF15171C.toInt())
        }
        root.addView(android.widget.TextView(this).apply {
            text = "Page One stopped last time"
            textSize = 22f; setTextColor(0xFFFFFFFF.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        root.addView(android.widget.TextView(this).apply {
            text = "Your scripts are safe. Tap Share report and send it to Amit so it can be fixed, then tap Open Page One."
            textSize = 15f; setTextColor(0xFFC8CCD4.toInt()); setPadding(0, pad / 2, 0, pad / 2)
        })
        val scroll = android.widget.ScrollView(this)
        scroll.addView(android.widget.TextView(this).apply {
            text = report; textSize = 11f; setTextColor(0xFFE8A598.toInt())
            typeface = android.graphics.Typeface.MONOSPACE; setTextIsSelectable(true)
        })
        root.addView(scroll, android.widget.LinearLayout.LayoutParams(-1, 0, 1f))
        val row = android.widget.LinearLayout(this).apply { setPadding(0, pad / 2, 0, 0) }
        row.addView(android.widget.Button(this).apply {
            text = "Share report"
            setOnClickListener {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, "Page One crash report").putExtra(Intent.EXTRA_TEXT, report)
                startActivity(Intent.createChooser(send, "Share crash report"))
            }
        }, android.widget.LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(android.widget.Button(this).apply {
            text = "Open Page One"
            setOnClickListener { CrashReport.clear(this@MainActivity); recreate() }
        }, android.widget.LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(row)
        setContentView(root)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        intent.getStringExtra(com.amit.pageone.data.Notifier.EXTRA_OPEN_SCRIPT)?.let { pendingOpen = "script:$it"; return }
        intent.getStringExtra(com.amit.pageone.data.Notifier.EXTRA_OPEN)?.let { pendingOpen = it; return }
        val data = intent.data
        if (intent.action == Intent.ACTION_VIEW && data?.scheme == "pageone") { pendingJoin = data.toString(); return }
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
        if (intent.action == Intent.ACTION_SEND && sharedText != null && sharedText.contains("P1-")) { pendingJoin = sharedText; return }
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
            else -> null
        }
        if (uri != null) pendingImport = uri
    }

    /** Keep the system splash (brand mark) up briefly, then fade it out with a little lift. */
    private fun holdSplash() {
        val start = SystemClock.uptimeMillis()
        val content: View = findViewById(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                return if (SystemClock.uptimeMillis() - start > 700) {
                    content.viewTreeObserver.removeOnPreDrawListener(this); true
                } else false
            }
        })
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        splashScreen.setOnExitAnimationListener { view ->
            val icon = view.iconView
            val set = AnimatorSet()
            val fade = ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0f)
            if (icon != null) {
                set.playTogether(
                    fade,
                    ObjectAnimator.ofFloat(icon, View.TRANSLATION_Y, 0f, -60f),
                    ObjectAnimator.ofFloat(icon, View.SCALE_X, 1f, 1.15f),
                    ObjectAnimator.ofFloat(icon, View.SCALE_Y, 1f, 1.15f),
                )
            } else set.play(fade)
            set.duration = 380
            set.doOnEnd { view.remove() }
            set.start()
        }
    }
}
