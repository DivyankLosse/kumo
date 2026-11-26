package com.ascend.app.work

import com.ascend.app.data.local.PlannedTaskEntity

object QuickActionLogic {
    const val DEFAULT_QUIET_START = 22 * 60 + 30
    const val DEFAULT_QUIET_END = 6 * 60 + 30
    const val SNOOZE_MINUTES = 15L

    val DEFAULT_ENABLED_REMINDERS: Set<String> = setOf("MORNING", "NIGHT")

    /** Quiet window [start, end); when start > end it crosses midnight. Equal bounds mean no quiet hours. */
    fun isQuietTime(nowMin: Int, startMin: Int, endMin: Int): Boolean = when {
        startMin == endMin -> false
        startMin < endMin -> nowMin in startMin until endMin
        else -> nowMin >= startMin || nowMin < endMin
    }

    /** The earliest incomplete task that is still running or ahead. */
    fun nextBlock(tasks: List<PlannedTaskEntity>, nowMin: Int): PlannedTaskEntity? =
        tasks.filter { !it.completed && !it.skipped && it.endMin > nowMin }.minByOrNull { it.startMin }

    /** Done must never toggle a finished task back; a missing task is a no-op too. */
    fun shouldMarkDone(task: PlannedTaskEntity?): Boolean = task != null && !task.completed

    fun parseClock(text: String): Int? {
        val parts = text.trim().split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        return if (h in 0..23 && m in 0..59) h * 60 + m else null
    }

    fun formatClock(min: Int): String = "%02d:%02d".format(min / 60, min % 60)
}
