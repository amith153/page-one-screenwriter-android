package com.amit.pageone.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.widget.RemoteViews
import com.amit.pageone.CaptureActivity
import com.amit.pageone.MainActivity
import com.amit.pageone.R
import com.amit.pageone.data.AppGraph
import com.amit.pageone.data.Inspiration
import com.amit.pageone.data.Notifier

/** Home-screen widgets: the idea inbox (type / speak an idea) and today's writing goal. */
object Widgets {
    fun refresh(c: Context) {
        runCatching {
            val m = AppWidgetManager.getInstance(c)
            m.getAppWidgetIds(ComponentName(c, InboxWidget::class.java)).let { ids -> if (ids.isNotEmpty()) InboxWidget.render(c, m, ids) }
            m.getAppWidgetIds(ComponentName(c, GoalWidget::class.java)).let { ids -> if (ids.isNotEmpty()) GoalWidget.render(c, m, ids) }
        }
    }

    internal fun activity(c: Context, req: Int, i: Intent) = PendingIntent.getActivity(c, req, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    internal fun openApp(c: Context, req: Int, open: String? = null) = activity(
        c, req, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP).apply { if (open != null) putExtra(Notifier.EXTRA_OPEN, open) },
    )
}

class InboxWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        AppGraph.init(context)
        render(context, manager, ids)
    }

    companion object {
        fun render(c: Context, m: AppWidgetManager, ids: IntArray) {
            AppGraph.ideas.reload()
            val waiting = AppGraph.ideas.ideas.value.filter { it.usedIn.isEmpty() }
            val v = RemoteViews(c.packageName, R.layout.widget_inbox)
            v.setTextViewText(R.id.count, if (waiting.isEmpty()) "empty" else "${waiting.size} waiting")
            v.setTextViewText(R.id.last, waiting.firstOrNull()?.text ?: "Caught a line in the shower? Overheard dialogue? Tap Type or Record.")
            v.setOnClickPendingIntent(R.id.type, Widgets.activity(c, 301, Intent(c, CaptureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)))
            v.setOnClickPendingIntent(R.id.speak, Widgets.activity(c, 302, Intent(c, CaptureActivity::class.java).putExtra(CaptureActivity.EXTRA_VOICE, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)))
            v.setOnClickPendingIntent(R.id.header, Widgets.openApp(c, 303, "inbox"))
            v.setOnClickPendingIntent(R.id.last, Widgets.openApp(c, 304, "inbox"))
            m.updateAppWidget(ids, v)
        }
    }
}

class GoalWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        AppGraph.init(context)
        render(context, manager, ids)
    }

    companion object {
        fun render(c: Context, m: AppWidgetManager, ids: IntArray) {
            val today = AppGraph.goals.todayFresh()
            val goal = AppGraph.settings.state.value.dailyGoal
            val streak = AppGraph.goals.streak()
            val q = Inspiration.quoteFor()
            val v = RemoteViews(c.packageName, R.layout.widget_goal)
            v.setImageViewBitmap(R.id.ring, ring(c, if (goal > 0) today / goal.toFloat() else if (today > 0) 1f else 0f))
            v.setTextViewText(R.id.words, if (today >= 1000) "%.1fk".format(today / 1000f) else "$today")
            v.setTextViewText(R.id.title, if (goal > 0) "TODAY · ${(100 * today / goal).coerceAtMost(999)}% OF $goal" else "WORDS TODAY")
            v.setTextViewText(R.id.streak, if (streak > 0) "✦ $streak-day streak" else if (today > 0) "Streak starts today" else "Fade in…")
            v.setTextViewText(R.id.quote, "“${q.text}” — ${q.by}")
            v.setOnClickPendingIntent(R.id.root, Widgets.openApp(c, 305))
            m.updateAppWidget(ids, v)
        }

        private fun ring(c: Context, progress: Float): Bitmap {
            val px = (76 * c.resources.displayMetrics.density).toInt().coerceAtLeast(64)
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val cv = Canvas(bmp)
            val w = px * 0.11f
            val r = RectF(w / 2, w / 2, px - w / 2, px - w / 2)
            val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = w; color = 0xFF2A2F3B.toInt() }
            cv.drawArc(r, 0f, 360f, false, track)
            val p = progress.coerceIn(0f, 1f)
            if (p > 0f) {
                val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE; strokeWidth = w; strokeCap = Paint.Cap.ROUND
                    shader = SweepGradient(px / 2f, px / 2f, intArrayOf(0xFFF2CF8A.toInt(), 0xFFE7B35A.toInt(), 0xFFC98A2E.toInt(), 0xFFF2CF8A.toInt()), null)
                }
                cv.save(); cv.rotate(-90f, px / 2f, px / 2f)
                cv.drawArc(r, 0f, 360f * p, false, arc)
                cv.restore()
            }
            return bmp
        }
    }
}
