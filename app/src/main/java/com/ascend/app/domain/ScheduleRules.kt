package com.ascend.app.domain

/** Pure scheduling rules. Times are minutes after midnight. */
object ScheduleRules {
    const val WAKE_MIN = 6 * 60 + 30
    const val SLEEP_MIN = 22 * 60 + 30
    const val SLOT_STEP = 15

    /** A task is allowed only fully inside the awake window [06:30, 22:30). */
    fun isWithinAwakeWindow(startMin: Int, endMin: Int): Boolean =
        startMin >= WAKE_MIN && endMin <= SLEEP_MIN && endMin > startMin

    fun overlaps(aStart: Int, aEnd: Int, bStart: Int, bEnd: Int): Boolean = aStart < bEnd && bStart < aEnd

    /**
     * Earliest start (rounded up to [SLOT_STEP]) at or after [notBefore] where a block of
     * [durationMin] fits between [busy] blocks without entering the sleep window. Null if the day is full.
     */
    fun nextFreeSlot(busy: List<Pair<Int, Int>>, durationMin: Int, notBefore: Int): Int? {
        if (durationMin <= 0 || durationMin > SLEEP_MIN - WAKE_MIN) return null
        var candidate = roundUp(maxOf(notBefore, WAKE_MIN))
        val sorted = busy.sortedBy { it.first }
        while (candidate + durationMin <= SLEEP_MIN) {
            val clash = sorted.firstOrNull { overlaps(candidate, candidate + durationMin, it.first, it.second) }
                ?: return candidate
            candidate = roundUp(clash.second)
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
