package com.ascend.app.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ascend.app.AscendApp
import com.ascend.app.MainActivity
import com.ascend.app.R
import com.ascend.app.domain.Dates
import com.ascend.app.domain.Metrics
import com.ascend.app.domain.ScheduleRules
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Reminder types. Times are targets: WorkManager may deliver late (Doze, battery saver), so the UI
 * says "around" and never promises exact timing.
 */
enum class ReminderType(val title: String, val hour: Int, val minute: Int, val repeatHours: Long, val weekday: DayOfWeek? = null) {
    MORNING("Morning overview", 6, 35, 24),
    CAREER("Career learning", 15, 10, 24),
    GYM("Gym", 17, 20, 24),
    SUNSCREEN("Sunscreen", 7, 20, 24),
    HYDRATION("Hydration", 9, 0, 3),
    POSTURE("Posture break", 10, 0, 2),
    NIGHT("Evening wind-down", 22, 10, 24),
    WEEKLY_REVIEW("Weekly review", 19, 55, 24 * 7, DayOfWeek.SUNDAY),
}

object Notifier {
    const val CHANNEL_ROUTINE = "routine"
    const val CHANNEL_TASKS = "tasks"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ROUTINE, "Daily routine", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_TASKS, "Task reminders", NotificationManager.IMPORTANCE_HIGH))
    }

    fun canPost(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun show(context: Context, id: Int, channel: String, title: String, text: String, actions: List<Pair<String, PendingIntent>> = emptyList()) {
        if (!canPost(context)) return
        val intent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFFFE08A.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(intent)
            .setAutoCancel(true)
        actions.forEach { (label, pi) -> builder.addAction(0, label, pi) }
        val n = builder.build()
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post; nothing to show.
        }
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val type = inputData.getString(KEY_TYPE)?.let { runCatching { ReminderType.valueOf(it) }.getOrNull() } ?: return Result.success()
        val app = applicationContext as AscendApp
        val settings = app.container.settings.current()
        if (!settings.remindersEnabled || type.name !in settings.enabledReminders) return Result.success()
        val now = LocalTime.now()
        val nowMin = now.hour * 60 + now.minute
        // Interval reminders stay quiet outside waking, non-college hours.
        if (type.repeatHours < 24 && (nowMin < 9 * 60 || nowMin > 21 * 60)) return Result.success()
        if (type.weekday != null && Dates.today().dayOfWeek != type.weekday) return Result.success()

        val c = app.container
        c.awaitReady()
        val today = Dates.today()
        c.planner.ensureDay(today)
        val tasks = c.planner.day(today.toEpochDay())
        val text = when (type) {
            ReminderType.MORNING -> {
                val next = tasks.firstOrNull { it.startMin >= nowMin && !it.completed }
                "${tasks.size} blocks planned today." + (next?.let { " Next: ${it.title} at ${ScheduleRules.format(it.startMin)}." } ?: "")
            }
            ReminderType.CAREER -> {
                val planned = Metrics.plannedCareerMinutes(tasks) / 60.0
                "Career block starting soon. Today's plan: %.1f h of career work.".format(planned)
            }
            ReminderType.GYM -> tasks.firstOrNull { it.category == "FITNESS" }?.let { "${it.title} at ${ScheduleRules.format(it.startMin)}." }
                ?: "No gym block today. Enjoy the rest."
            ReminderType.HYDRATION -> {
                val ml = c.nutrition.observeWater(today.toEpochDay()).first().sumOf { it.ml }
                "Water so far: ${ml} ml. Time for a glass."
            }
            ReminderType.SUNSCREEN -> "Sunscreen before you head out. Reapply around lunch if you're outdoors."
            ReminderType.POSTURE -> "Stand up, roll the shoulders, and do 10 chin tucks."
            ReminderType.NIGHT -> "Evening wind-down in 5 minutes. Screens down by 22:30."
            ReminderType.WEEKLY_REVIEW -> "Sunday review: look back at the week and set next week's deliverables."
        }
        val notificationId = 1000 + type.ordinal
        if (!QuickActionLogic.isQuietTime(nowMin, settings.quietStartMin, settings.quietEndMin)) {
            val actions = if (type == ReminderType.HYDRATION) {
                listOf("+${settings.glassMl} ml" to QuickActionReceiver.pendingIntent(applicationContext, QuickActionReceiver.ACTION_WATER, notificationId))
            } else emptyList()
            Notifier.show(applicationContext, notificationId, Notifier.CHANNEL_ROUTINE, type.title, text, actions)
        }
        if (type == ReminderType.MORNING) ReminderScheduler.scheduleTaskReminders(applicationContext, tasks.filter { it.reminder && !it.completed }.map { Triple(it.id, it.title, it.startMin) })
        return Result.success()
    }

    companion object { const val KEY_TYPE = "type" }
}

class TaskReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as AscendApp
        val settings = app.container.settings.current()
        if (!settings.remindersEnabled) return Result.success()
        if (QuickActionLogic.isQuietTime(Dates.nowMinutes(), settings.quietStartMin, settings.quietEndMin)) return Result.success()
        val id = inputData.getLong("id", 0)
        val task = app.container.planner.task(id) ?: return Result.success()
        if (task.completed || task.date != Dates.todayEpoch()) return Result.success()
        val notificationId = (2000 + id % 100000).toInt()
        val prefix = if (inputData.getBoolean(KEY_SNOOZED, false)) "Snoozed: starts" else "Starts"
        Notifier.show(applicationContext, notificationId, Notifier.CHANNEL_TASKS, task.title,
            "$prefix around ${ScheduleRules.format(task.startMin)}.",
            listOf(
                "Done" to QuickActionReceiver.pendingIntent(applicationContext, QuickActionReceiver.ACTION_DONE, notificationId, id),
                "Snooze 15 min" to QuickActionReceiver.pendingIntent(applicationContext, QuickActionReceiver.ACTION_SNOOZE, notificationId, id),
            ))
        return Result.success()
    }

    companion object { const val KEY_SNOOZED = "snoozed" }
}

object ReminderScheduler {
    private fun name(type: ReminderType) = "reminder-${type.name}"

    fun initialDelay(type: ReminderType, now: LocalDateTime = LocalDateTime.now()): Duration {
        var next = now.toLocalDate().atTime(type.hour, type.minute)
        if (type.weekday != null) {
            while (next.dayOfWeek != type.weekday || !next.isAfter(now)) next = next.plusDays(1)
        } else if (type.repeatHours < 24) {
            while (!next.isAfter(now)) next = next.plusHours(type.repeatHours)
        } else if (!next.isAfter(now)) {
            next = next.plusDays(1)
        }
        return Duration.between(now, next)
    }

    fun scheduleAll(context: Context) {
        val wm = WorkManager.getInstance(context)
        ReminderType.entries.forEach { type ->
            val req = PeriodicWorkRequestBuilder<ReminderWorker>(type.repeatHours, TimeUnit.HOURS)
                .setInitialDelay(initialDelay(type).toMinutes(), TimeUnit.MINUTES)
                .setInputData(workDataOf(ReminderWorker.KEY_TYPE to type.name))
                .addTag("reminders")
                .build()
            wm.enqueueUniquePeriodicWork(name(type), ExistingPeriodicWorkPolicy.UPDATE, req)
        }
    }

    fun cancelAll(context: Context) = WorkManager.getInstance(context).cancelAllWorkByTag("reminders")

    /** One-off nudges ~10 minutes before today's tasks that have reminders turned on. */
    fun scheduleTaskReminders(context: Context, tasks: List<Triple<Long, String, Int>>) {
        val wm = WorkManager.getInstance(context)
        val nowMin = Dates.nowMinutes()
        tasks.forEach { (id, _, start) ->
            val delay = start - 10 - nowMin
            if (delay <= 0) return@forEach
            val req = OneTimeWorkRequestBuilder<TaskReminderWorker>()
                .setInitialDelay(delay.toLong(), TimeUnit.MINUTES)
                .setInputData(workDataOf("id" to id))
                .addTag("reminders")
                .build()
            wm.enqueueUniqueWork("task-$id", ExistingWorkPolicy.REPLACE, req)
        }
    }
}
