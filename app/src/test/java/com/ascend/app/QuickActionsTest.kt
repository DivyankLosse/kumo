package com.ascend.app

import com.ascend.app.data.local.PlannedTaskEntity
import com.ascend.app.data.prefs.Settings
import com.ascend.app.work.QuickActionLogic
import org.junit.Assert.*
import org.junit.Test

class QuickActionsTest {
    private fun task(id: Long, start: Int, end: Int, done: Boolean = false) =
        PlannedTaskEntity(id = id, date = 1, title = "t$id", category = "OTHER", startMin = start, endMin = end, completed = done)

    @Test fun quietHoursCrossingMidnight() {
        val s = QuickActionLogic.DEFAULT_QUIET_START
        val e = QuickActionLogic.DEFAULT_QUIET_END
        assertTrue(QuickActionLogic.isQuietTime(23 * 60, s, e))
        assertTrue(QuickActionLogic.isQuietTime(0, s, e))
        assertTrue(QuickActionLogic.isQuietTime(s, s, e))
        assertTrue(QuickActionLogic.isQuietTime(e - 1, s, e))
        assertFalse(QuickActionLogic.isQuietTime(e, s, e))
        assertFalse(QuickActionLogic.isQuietTime(12 * 60, s, e))
        assertFalse(QuickActionLogic.isQuietTime(s - 1, s, e))
    }

    @Test fun quietHoursSameDayAndEmpty() {
        assertTrue(QuickActionLogic.isQuietTime(13 * 60, 12 * 60, 14 * 60))
        assertFalse(QuickActionLogic.isQuietTime(14 * 60, 12 * 60, 14 * 60))
        assertFalse(QuickActionLogic.isQuietTime(5, 600, 600))
    }

    @Test fun defaultEnabledSet() {
        assertEquals(setOf("MORNING", "NIGHT"), Settings().enabledReminders)
        assertEquals(QuickActionLogic.DEFAULT_ENABLED_REMINDERS, Settings().enabledReminders)
        assertEquals(22 * 60 + 30, Settings().quietStartMin)
        assertEquals(6 * 60 + 30, Settings().quietEndMin)
    }

    @Test fun nextBlockPicksEarliestIncompleteNotYetEnded() {
        val tasks = listOf(task(1, 480, 540), task(2, 600, 660, done = true), task(3, 700, 760), task(4, 650, 690))
        assertEquals(4L, QuickActionLogic.nextBlock(tasks, 560)?.id)
        assertEquals(3L, QuickActionLogic.nextBlock(tasks, 690)?.id)
    }

    @Test fun nextBlockCanBeTheCurrentOne() {
        assertEquals(1L, QuickActionLogic.nextBlock(listOf(task(1, 480, 540), task(2, 560, 600)), 500)?.id)
    }

    @Test fun nextBlockNullWhenNothingLeft() {
        assertNull(QuickActionLogic.nextBlock(listOf(task(1, 480, 540), task(2, 560, 600, done = true)), 540))
        assertNull(QuickActionLogic.nextBlock(emptyList(), 0))
    }

    @Test fun doneIsIdempotent() {
        assertTrue(QuickActionLogic.shouldMarkDone(task(1, 0, 10)))
        assertFalse(QuickActionLogic.shouldMarkDone(task(1, 0, 10, done = true)))
        assertFalse(QuickActionLogic.shouldMarkDone(null))
    }

    @Test fun clockParsing() {
        assertEquals(22 * 60 + 30, QuickActionLogic.parseClock("22:30"))
        assertEquals(6 * 60, QuickActionLogic.parseClock(" 6:00 "))
        assertNull(QuickActionLogic.parseClock("24:00"))
        assertNull(QuickActionLogic.parseClock("12:60"))
        assertNull(QuickActionLogic.parseClock("abc"))
        assertEquals("06:30", QuickActionLogic.formatClock(390))
    }
}
