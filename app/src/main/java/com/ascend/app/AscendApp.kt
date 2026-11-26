package com.ascend.app

import android.app.Application
import app.rive.runtime.kotlin.core.Rive
import com.ascend.app.di.AppContainer
import com.ascend.app.domain.Dates
import com.ascend.app.work.Notifier
import com.ascend.app.work.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AscendApp : Application() {
    companion object {
        @Volatile var riveReady = false
            private set

        /** UI tests turn Rive off: its per-frame redraw keeps Compose from ever reporting idle. */
        @Volatile var riveEnabled = true
    }

    lateinit var container: AppContainer
        private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Loads Rive's native renderer once; Kumo falls back to the Canvas bear if this fails.
        riveReady = runCatching { Rive.init(this) }.isSuccess
        Notifier.createChannels(this)
        appScope.launch {
            container.awaitReady()
            container.planner.ensureRange(Dates.weekStart(), 14)
            if (container.settings.current().remindersEnabled) ReminderScheduler.scheduleAll(this@AscendApp)
        }
    }
}
