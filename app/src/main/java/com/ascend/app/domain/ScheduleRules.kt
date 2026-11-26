package com.ascend.app.domain

/**
 * Protected sleep from [sleepStartMin] to [wakeMin] (minutes after midnight). The usual case crosses midnight
 * (sleepStart > wake, e.g. 22:30 -> 06:30); a same-day window (e.g. 01:00 -> 09:00) is also supported.
 */
data class SleepWindow(val sleepStartMin: Int = ScheduleRules.SLEEP_MIN, val wakeMin: Int = ScheduleRules.WAKE_MIN) {
    private val valid = sleepStartMin in 0..MINUTES_PER_DAY && wakeMin in 0..MINUTES_PER_DAY && sleepStartMin != wakeMin

    /** Awake stretches within one calendar day, ascending. An invalid window falls back to the default. */
    val awakeIntervals: List<Pair<Int, Int>> = when {
        !valid -> listOf(ScheduleRules.WAKE_MIN to ScheduleRules.SLEEP_MIN)
        sleepStartMin > wakeMin -> listOf(wakeMin to sleepStartMin)
        else -> listOfNotNull((0 to sleepStartMin).takeIf { sleepStartMin > 0 }, (wakeMin to MINUTES_PER_DAY).takeIf { wakeMin < MINUTES_PER_DAY })
    }

    val awakeMinutes: Int get() = awakeIntervals.sumOf { it.second - it.first }

    companion object {
        const val MINUTES_PER_DAY = 24 * 60
        val DEFAULT = SleepWindow()
    }
}

/** Pure scheduling rules. Times are minutes after midnight. */
object ScheduleRules {
    const val WAKE_MIN = 6 * 60 + 30
    const val SLEEP_MIN = 22 * 60 + 30
    const val SLOT_STEP = 15

    /** A task is allowed only fully inside one awake stretch of the day (default [06:30, 22:30)). */
    fun isWithinAwakeWindow(startMin: Int, endMin: Int, window: SleepWindow = SleepWindow.DEFAULT): Boolean =
        endMin > startMin && window.awakeIntervals.any { startMin >= it.first && endMin <= it.second }

    fun overlaps(aStart: Int, aEnd: Int, bStart: Int, bEnd: Int): Boolean = aStart < bEnd && bStart < aEnd

    /**
     * Earliest start (rounded up to [SLOT_STEP]) at or after [notBefore] where a block of
     * [durationMin] fits between [busy] blocks without entering the sleep window. Null if the day is full.
     */
    fun nextFreeSlot(busy: List<Pair<Int, Int>>, durationMin: Int, notBefore: Int, window: SleepWindow = SleepWindow.DEFAULT): Int? {
        if (durationMin <= 0) return null
        val sorted = busy.sortedBy { it.first }
        for ((awakeStart, awakeEnd) in window.awakeIntervals) {
            var candidate = roundUp(maxOf(notBefore, awakeStart))
            while (candidate + durationMin <= awakeEnd) {
                val clash = sorted.firstOrNull { overlaps(candidate, candidate + durationMin, it.first, it.second) }
                    ?: return candidate
                candidate = roundUp(clash.second)
            }
        }
        return null
    }

    private fun roundUp(min: Int): Int = ((min + SLOT_STEP - 1) / SLOT_STEP) * SLOT_STEP

    fun format(min: Int): String = "%02d:%02d".format(min / 60, min % 60)

    fun parse(hhmm: String): Int? {
        val parts = hhmm.trim().split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }
}
