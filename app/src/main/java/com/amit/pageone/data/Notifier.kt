package com.amit.pageone.data

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.amit.pageone.MainActivity
import com.amit.pageone.R
import com.amit.pageone.core.Planner
import java.util.Calendar

/**
 * Every notification Page One sends:
 *  - Daily writing reminder (your time) — skipped once today's goal is met
 *  - Deadline reminders — every day while a "finish by" date is set: pages/day needed, ahead/behind,
 *    with louder nudges at 2 weeks, 1 week, 3 days, tomorrow, due today and overdue
 *  - Streak at risk — 8:30 pm if you have a streak going and haven't written today
 *  - Goal reached — a small celebration when you pass the daily goal
 *  - Idea inbox — Sunday morning, if captured ideas are still waiting
 *  - Sprint over — when a timed sprint ends, even with the app closed
 *
 * Alarms are inexact (battery-friendly) except the sprint bell. They re-arm themselves after
 * firing and after a reboot.
 */
object Notifier {
    const val CH_REMINDERS = "reminders"
    const val CH_DEADLINES = "deadlines"
    const val CH_STREAK = "streak"
    const val CH_SPRINT = "sprint"
    const val CH_IDEAS = "ideas"

    const val ACTION_DAILY = "com.amit.pageone.DAILY"
    const val ACTION_STREAK = "com.amit.pageone.STREAK"
    const val ACTION_INBOX = "com.amit.pageone.INBOX"
    const val ACTION_SPRINT = "com.amit.pageone.SPRINT"

    const val EXTRA_OPEN_SCRIPT = "open_script"
    const val EXTRA_OPEN = "open"

    private const val ID_DAILY = 101
    private const val ID_STREAK = 102
    private const val ID_INBOX = 103
    private const val ID_SPRINT = 104
    private const val ID_GOAL = 105
    private const val ID_DEADLINE_BASE = 2000

    fun createChannels(c: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(listOf(
            NotificationChannel(CH_REMINDERS, "Writing reminders", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Your daily nudge to write, and goal celebrations" },
            NotificationChannel(CH_DEADLINES, "Deadlines", NotificationManager.IMPORTANCE_HIGH).apply { description = "How you're tracking against a script's finish-by date" },
            NotificationChannel(CH_STREAK, "Streaks", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "When your writing streak is about to end" },
            NotificationChannel(CH_SPRINT, "Sprints", NotificationManager.IMPORTANCE_HIGH).apply { description = "When a timed writing sprint ends" },
            NotificationChannel(CH_IDEAS, "Idea inbox", NotificationManager.IMPORTANCE_LOW).apply { description = "Weekly reminder of ideas waiting to be used" },
        ))
    }

    fun canPost(c: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(c).areNotificationsEnabled()

    // ------------------------------------------------------------------ scheduling

    /** (Re)arm all repeating alarms from the current settings. Safe to call any time. */
    fun scheduleAll(c: Context) {
        val s = AppGraph.settings.state.value
        val needDaily = s.dailyReminder || s.deadlineReminders
        if (needDaily) schedule(c, ACTION_DAILY, nextAt(s.reminderHour, s.reminderMinute)) else cancel(c, ACTION_DAILY)
        if (s.streakAlerts) schedule(c, ACTION_STREAK, nextAt(20, 30)) else cancel(c, ACTION_STREAK)
        if (s.inboxNudges) schedule(c, ACTION_INBOX, nextWeekly(Calendar.SUNDAY, 11, 0)) else cancel(c, ACTION_INBOX)
    }

    private fun pi(c: Context, action: String): PendingIntent =
        PendingIntent.getBroadcast(c, action.hashCode(), Intent(c, NotifyReceiver::class.java).setAction(action), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun schedule(c: Context, action: String, at: Long) {
        val am = c.getSystemService(AlarmManager::class.java) ?: return
        runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(c, action)) }
    }

    private fun cancel(c: Context, action: String) { c.getSystemService(AlarmManager::class.java)?.cancel(pi(c, action)) }

    fun scheduleSprintEnd(c: Context, at: Long) {
        val am = c.getSystemService(AlarmManager::class.java) ?: return
        val p = pi(c, ACTION_SPRINT)
        runCatching {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
            else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
        }
    }

    fun cancelSprintEnd(c: Context) { cancel(c, ACTION_SPRINT) }

    private fun nextAt(hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        if (cal.timeInMillis <= System.currentTimeMillis() + 30_000) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    private fun nextWeekly(day: Int, hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        while (cal.get(Calendar.DAY_OF_WEEK) != day || cal.timeInMillis <= System.currentTimeMillis() + 30_000) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    // ------------------------------------------------------------------ posting

    private fun open(c: Context, req: Int, extras: Intent.() -> Unit = {}): PendingIntent =
        PendingIntent.getActivity(c, req, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP).apply(extras),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun post(c: Context, id: Int, channel: String, title: String, text: String, tap: PendingIntent, big: String? = null) {
        if (!canPost(c)) return
        val n = NotificationCompat.Builder(c, channel)
            .setSmallIcon(R.drawable.ic_logo_mono)
            .setColor(0xFFE7B35A.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(big ?: text))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(c).notify(id, n) }
    }

    fun daily(c: Context) {
        val s = AppGraph.settings.state.value
        if (s.dailyReminder) {
            val today = AppGraph.goals.todayFresh()
            val goal = s.dailyGoal
            if (goal <= 0 || today < goal) {
                val q = Inspiration.quoteFor()
                val title = if (today == 0) "Time to write" else "Keep going — ${goal - today} words to today's goal"
                val text = if (goal > 0 && today == 0) "Today's goal: $goal words. Even one scene counts." else "Open your script and write one page."
                post(c, ID_DAILY, CH_REMINDERS, title, text, open(c, ID_DAILY), "$text\n\n“${q.text}” — ${q.by}")
            }
        }
        if (s.deadlineReminders) deadlines(c)
    }

    /** One notification per script with an active deadline. */
    fun deadlines(c: Context) {
        val metas = AppGraph.store.indexSnapshot()
        metas.forEach { m ->
            val d = m.deadline ?: return@forEach
            val plan = Planner.plan(d, m.pages)
            if (plan.done) return@forEach
            val left = plan.daysLeft
            val title = when {
                plan.overdue -> "“${m.title}” is past its deadline"
                left == 0L -> "“${m.title}” is due today"
                left == 1L -> "“${m.title}” is due tomorrow"
                left == 3L || left == 7L || left == 14L -> "$left days left on “${m.title}”"
                plan.behind > 1f -> "Behind on “${m.title}”"
                else -> "${d.label}: ${m.title}"
            }
            val text = "${m.pages} of ${d.targetPages} pages · ${plan.status}"
            post(c, ID_DEADLINE_BASE + (m.id.hashCode() and 0xFFF), CH_DEADLINES, title, text, open(c, ID_DEADLINE_BASE + (m.id.hashCode() and 0xFFF)) { putExtra(EXTRA_OPEN_SCRIPT, m.id) })
        }
    }

    fun streak(c: Context) {
        val streak = AppGraph.goals.streak()
        if (streak > 0 && AppGraph.goals.todayFresh() == 0) {
            post(c, ID_STREAK, CH_STREAK, "Your $streak-day streak ends at midnight", "Write a few lines to keep it alive.", open(c, ID_STREAK))
        }
    }

    fun inbox(c: Context) {
        AppGraph.ideas.reload()
        val n = AppGraph.ideas.waiting
        if (n > 0) post(c, ID_INBOX, CH_IDEAS, "$n idea${if (n == 1) "" else "s"} waiting in your inbox", "Turn one into a scene this week.", open(c, ID_INBOX) { putExtra(EXTRA_OPEN, "inbox") })
    }

    fun sprintOver(c: Context, words: Int, goal: Int) {
        if (!AppGraph.settings.state.value.sprintAlerts) return
        val hit = goal > 0 && words >= goal
        post(c, ID_SPRINT, CH_SPRINT, if (hit) "Sprint goal smashed!" else "Sprint over", "You wrote $words word${if (words == 1) "" else "s"}" + if (goal > 0) " of $goal." else ".", open(c, ID_SPRINT))
    }

    fun goalReached(c: Context, goal: Int) {
        if (!AppGraph.settings.state.value.goalCelebrations) return
        post(c, ID_GOAL, CH_REMINDERS, "Daily goal reached", "$goal words today. That's a writer's day.", open(c, ID_GOAL))
    }
}

/** Fires the scheduled notifications and re-arms them. Also runs after a reboot or app update. */
class NotifyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AppGraph.init(context)
        when (intent.action) {
            Notifier.ACTION_DAILY -> Notifier.daily(context)
            Notifier.ACTION_STREAK -> Notifier.streak(context)
            Notifier.ACTION_INBOX -> Notifier.inbox(context)
            Notifier.ACTION_SPRINT -> {
                val s = AppGraph.sprints.current.value
                if (s != null) {
                    AppGraph.sprints.finish()
                    Notifier.sprintOver(context, s.words, s.goalWords)
                }
            }
        }
        if (intent.action != Notifier.ACTION_SPRINT) Notifier.scheduleAll(context)
    }
}
