package com.ascend.app.work

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.ascend.app.AscendApp
import com.ascend.app.data.local.WaterEntryEntity
import com.ascend.app.domain.Dates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class QuickActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, 0)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val c = (app as AscendApp).container
                c.awaitReady()
                when (action) {
                    ACTION_DONE -> {
                        val task = c.planner.task(taskId)
                        if (QuickActionLogic.shouldMarkDone(task)) {
                            c.db.plannerDao().setCompleted(taskId, true, System.currentTimeMillis())
                        }
                    }
                    ACTION_SNOOZE -> snooze(app, taskId)
                    ACTION_WATER -> {
                        val ml = c.settings.current().glassMl
                        c.nutrition.insertWater(WaterEntryEntity(date = Dates.todayEpoch(), ml = ml, loggedAt = System.currentTimeMillis()))
                    }
                }
                if (notificationId >= 0) app.getSystemService(NotificationManager::class.java).cancel(notificationId)
                c.refreshWidget()
            } finally {
                pending.finish()
            }
        }
    }

    private fun snooze(context: Context, taskId: Long) {
        val req = OneTimeWorkRequestBuilder<TaskReminderWorker>()
            .setInitialDelay(QuickActionLogic.SNOOZE_MINUTES, TimeUnit.MINUTES)
            .setInputData(workDataOf("id" to taskId, TaskReminderWorker.KEY_SNOOZED to true))
            .addTag("reminders")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("task-$taskId", ExistingWorkPolicy.REPLACE, req)
    }

    companion object {
        const val ACTION_DONE = "com.ascend.app.action.TASK_DONE"
        const val ACTION_SNOOZE = "com.ascend.app.action.TASK_SNOOZE"
        const val ACTION_WATER = "com.ascend.app.action.WATER_ADD"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"

        fun pendingIntent(context: Context, action: String, notificationId: Int, taskId: Long = 0): PendingIntent {
            val intent = Intent(context, QuickActionReceiver::class.java)
                .setAction(action)
                .putExtra(EXTRA_TASK_ID, taskId)
                .putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            return PendingIntent.getBroadcast(
                context, action.hashCode() * 31 + notificationId, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }
}
