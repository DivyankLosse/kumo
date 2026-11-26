package com.ascend.app

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.ascend.app.work.ReminderType
import com.ascend.app.work.ReminderWorker
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderWorkerTest {
    @get:Rule
    val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val app get() = ctx as AscendApp
    private val nm get() = ctx.getSystemService(NotificationManager::class.java)

    private suspend fun run(type: ReminderType): ListenableWorker.Result =
        TestListenableWorkerBuilder<ReminderWorker>(ctx).setInputData(workDataOf(ReminderWorker.KEY_TYPE to type.name)).build().doWork()

    @Test fun morningOverviewPostsNotificationWhenEnabled() = runTest {
        nm.cancelAll()
        app.container.settings.setRemindersEnabled(true)
        app.container.settings.setReminderEnabled(ReminderType.MORNING.name, true)
        app.container.settings.setQuietHours(0, 0)
        assertEquals(ListenableWorker.Result.success(), run(ReminderType.MORNING))
        val posted = nm.activeNotifications.firstOrNull { it.id == 1000 + ReminderType.MORNING.ordinal }
        assertNotNull("morning notification posted", posted)
        assertTrue(posted!!.notification.extras.getCharSequence("android.text").toString().contains("blocks planned today"))
    }

    @Test fun nothingPostedWhenRemindersOff() = runTest {
        nm.cancelAll()
        app.container.settings.setRemindersEnabled(false)
        run(ReminderType.MORNING)
        assertTrue(nm.activeNotifications.none { it.id == 1000 + ReminderType.MORNING.ordinal })
    }

    @Test fun perTypeToggleIsRespected() = runTest {
        nm.cancelAll()
        app.container.settings.setRemindersEnabled(true)
        app.container.settings.setReminderEnabled(ReminderType.GYM.name, false)
        run(ReminderType.GYM)
        assertTrue(nm.activeNotifications.none { it.id == 1000 + ReminderType.GYM.ordinal })
        app.container.settings.setReminderEnabled(ReminderType.GYM.name, true)
        app.container.settings.setRemindersEnabled(false)
    }
}
