package com.tablodecori.app.data

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
import androidx.core.content.ContextCompat
import com.tablodecori.app.MainActivity
import com.tablodecori.app.TablodecoriApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Persists the active cycle and lets an alarm finish it even while the planner is closed. */
object PomodoroTimer {
    private const val PREFS = "pomodoro_cycle_v1"
    private const val CHANNEL = "focus_cycle_v1"
    private const val NOTICE_ID = 54841

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun alarm(c: Context) = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private fun pending(c: Context) = PendingIntent.getBroadcast(c, 54841,
        Intent(c, PomodoroAlarmReceiver::class.java).setAction("com.tablodecori.app.FOCUS_PHASE_END"),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    @Synchronized fun state(c: Context): PomodoroCycle.State {
        val p = prefs(c)
        return PomodoroCycle.State(
            phase = p.getString("phase", PomodoroCycle.READY) ?: PomodoroCycle.READY,
            remainingMillis = p.getLong("remaining", PomodoroCycle.FOCUS_MILLIS),
            endsAtMillis = p.getLong("ends", 0L),
            completed = p.getInt("completed", 0),
            taskId = p.getString("task_id", "").orEmpty(),
            taskTitle = p.getString("task_title", "").orEmpty(),
            taskDayMillis = p.getLong("task_day", 0L)
        )
    }

    private fun save(c: Context, s: PomodoroCycle.State) {
        prefs(c).edit().putString("phase", s.phase).putLong("remaining", s.remainingMillis)
            .putLong("ends", s.endsAtMillis).putInt("completed", s.completed)
            .putString("task_id", s.taskId).putString("task_title", s.taskTitle)
            .putLong("task_day", s.taskDayMillis).commit()
    }

    private fun schedule(c: Context, s: PomodoroCycle.State) {
        val a = alarm(c)
        val pi = pending(c)
        a.cancel(pi)
        if (!s.running) return
        val trigger = s.endsAtMillis.coerceAtLeast(System.currentTimeMillis() + 1000L)
        if (Build.VERSION.SDK_INT >= 31 && a.canScheduleExactAlarms()) {
            try { a.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi); return }
            catch (_: SecurityException) { /* Inexact fallback keeps the timer usable. */ }
        } else if (Build.VERSION.SDK_INT in 23..30) {
            a.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            return
        }
        if (Build.VERSION.SDK_INT >= 23) a.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        else a.set(AlarmManager.RTC_WAKEUP, trigger, pi)
    }

    @Synchronized fun start(c: Context, taskId: String = "", taskTitle: String = "", dayMillis: Long = System.currentTimeMillis()): PomodoroCycle.State {
        val current = state(c)
        val next = PomodoroCycle.start(current, System.currentTimeMillis(), taskId, taskTitle, dayMillis)
        save(c, next); schedule(c, next)
        return next
    }

    @Synchronized fun pause(c: Context): PomodoroCycle.State {
        val next = PomodoroCycle.pause(state(c), System.currentTimeMillis())
        save(c, next); schedule(c, next)
        return next
    }

    @Synchronized fun skipBreak(c: Context): PomodoroCycle.State {
        val next = PomodoroCycle.skipBreak(state(c))
        save(c, next); schedule(c, next)
        return next
    }

    @Synchronized fun reset(c: Context): PomodoroCycle.State {
        val next = PomodoroCycle.reset()
        save(c, next); schedule(c, next)
        return next
    }

    private data class Transition(val before: PomodoroCycle.State, val result: PomodoroCycle.Result)

    @Synchronized private fun advance(c: Context, rearm: Boolean): Transition {
        val before = state(c)
        val result = PomodoroCycle.advance(before, System.currentTimeMillis())
        if (result.state != before) { save(c, result.state); schedule(c, result.state) }
        else if (rearm && before.running) schedule(c, before)
        return Transition(before, result)
    }

    /** Also repairs a missed alarm after reboot or when the app returns to foreground. */
    suspend fun refresh(c: Context, rearm: Boolean = false): PomodoroCycle.State {
        val transition = advance(c, rearm)
        if ("FOCUS_DONE" in transition.result.events && transition.before.taskId.isNotBlank()) {
            runCatching {
                (c.applicationContext as TablodecoriApp).repository.addPlannerFocus(
                    transition.before.taskId, transition.before.taskDayMillis, 25)
            }
        }
        if (transition.result.events.isNotEmpty()) notifyPhase(c, transition.result)
        return transition.result.state
    }

    private fun notifyPhase(c: Context, result: PomodoroCycle.Result) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "چرخهٔ تمرکز و استراحت", NotificationManager.IMPORTANCE_HIGH))
        val focusEnded = result.events.last() == "FOCUS_DONE"
        val title = if (focusEnded) "✅ ۲۵ دقیقه تمرکز تمام شد" else "☕ استراحت تمام شد"
        val body = if (focusEnded) {
            if (result.state.phase == PomodoroCycle.LONG_BREAK) "۱۵ دقیقه استراحت بلند؛ خسته نباشی!"
            else "حالا ۵ دقیقه استراحت کن."
        } else "برای شروع نوبت بعدی، پلنر را باز کن."
        val open = PendingIntent.getActivity(c, 54841, Intent(c, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        nm.notify(NOTICE_ID, NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title).setContentText(body).setContentIntent(open)
            .setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH).build())
    }
}

class PomodoroAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.tablodecori.app.FOCUS_PHASE_END") return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { PomodoroTimer.refresh(context) } catch (_: Throwable) {} finally { pending.finish() }
        }
    }
}
