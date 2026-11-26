package com.ascend.app.domain

import com.ascend.app.data.local.PlannedTaskEntity

enum class RecoveryOption { RECOVER, SHORTEN, DEFER, DROP }

/** A single-row edit. [updated] has the same id as [original], so applying it never duplicates a task. */
data class TaskChange(val original: PlannedTaskEntity, val updated: PlannedTaskEntity)

data class RecoveryProposal(val option: RecoveryOption, val change: TaskChange)

/** A multi-row proposal. [unplaced] lists tasks that found no slot and are left as they are. */
data class BatchProposal(val changes: List<TaskChange>, val unplaced: List<PlannedTaskEntity> = emptyList())

/** Pure recovery rules. Nothing here writes; callers apply the returned proposals. */
object Recovery {
    const val LOOKAHEAD_DAYS = 6
    const val SHORT_MAX_MIN = 30
    const val SHORT_MIN_MIN = 15
    const val SHORT_SUFFIX = " (short)"
    const val MVD_MAX_MIN = 30
    const val ESSENTIAL_PRIORITY = 2
    private val MVD_SHRINK_CATEGORIES = setOf(Category.HEALTH.name, Category.GROOMING.name)

    fun isEssential(task: PlannedTaskEntity) = task.priority >= ESSENTIAL_PRIORITY

    /** A planned, still-open block whose time has passed. Fixed blocks (college, commute) are never "missed". */
    fun isMissed(task: PlannedTaskEntity, todayEpoch: Long, nowMin: Int): Boolean {
        val actionable = Category.of(task.category).focusable || task.category == Category.FITNESS.name
        return actionable && !task.completed && !task.skipped &&
            (task.date < todayEpoch || (task.date == todayEpoch && task.endMin <= nowMin))
    }

    fun shortenedMinutes(originalMin: Int): Int =
        if (originalMin <= SHORT_MIN_MIN) originalMin else minOf(SHORT_MAX_MIN, originalMin / 2).coerceAtLeast(SHORT_MIN_MIN)

    /** [dayTasks] returns every task stored for an epoch day. Null when the option does not apply or nothing fits. */
    fun propose(
        option: RecoveryOption,
        task: PlannedTaskEntity,
        todayEpoch: Long,
        nowMin: Int,
        window: SleepWindow,
        dayTasks: (Long) -> List<PlannedTaskEntity>,
    ): RecoveryProposal? {
        if (OverloadAnalyzer.isFixed(task)) return null
        val placer = Placer(window, dayTasks, setOf(task.id))
        val duration = task.endMin - task.startMin
        val change = when (option) {
            RecoveryOption.RECOVER -> placer.place(task, duration, todayEpoch, 0..LOOKAHEAD_DAYS, nowMin)
            RecoveryOption.SHORTEN -> placer.place(
                task, shortenedMinutes(duration), todayEpoch, 0..0, nowMin,
                title = if (task.title.endsWith(SHORT_SUFFIX)) task.title else task.title + SHORT_SUFFIX,
            )
            RecoveryOption.DEFER -> placer.place(task, duration, todayEpoch, 1..1, nowMin)
            RecoveryOption.DROP -> TaskChange(task, task.copy(skipped = true))
        } ?: return null
        return RecoveryProposal(option, change)
    }

    /** "Recover the rest of today": higher priority and earlier blocks are placed first; no two share a slot. */
    fun proposeRecoverRest(
        missed: List<PlannedTaskEntity>,
        todayEpoch: Long,
        nowMin: Int,
        window: SleepWindow,
        dayTasks: (Long) -> List<PlannedTaskEntity>,
    ): BatchProposal {
        val movable = missed.filter { !OverloadAnalyzer.isFixed(it) && !it.completed && !it.skipped }
            .sortedWith(compareByDescending<PlannedTaskEntity> { it.priority }.thenBy { it.date }.thenBy { it.startMin })
        val placer = Placer(window, dayTasks, movable.map { it.id }.toSet())
        val changes = mutableListOf<TaskChange>()
        val unplaced = mutableListOf<PlannedTaskEntity>()
        for (task in movable) {
            val change = placer.place(task, task.endMin - task.startMin, todayEpoch, 0..LOOKAHEAD_DAYS, nowMin)
            if (change == null) {
                unplaced += task
            } else {
                placer.reserve(change.updated)
                changes += change
            }
        }
        return BatchProposal(changes, unplaced)
    }

    /**
     * Minimum viable day: fixed blocks, essentials, finished, skipped and in-progress blocks stay as they are.
     * Remaining top-priority and basic-care (health, grooming) blocks shrink to at most [MVD_MAX_MIN] minutes
     * from their own start; every other remaining flexible block is skipped. Shrinking never moves a block,
     * so the result cannot overlap or enter sleep.
     */
    fun minimumViableDay(tasks: List<PlannedTaskEntity>, nowMin: Int): BatchProposal {
        val changes = tasks.mapNotNull { t ->
            val untouched = OverloadAnalyzer.isFixed(t) || isEssential(t) || t.completed || t.skipped || t.startMin < nowMin
            if (untouched) return@mapNotNull null
            val keepShort = t.priority >= 1 || t.category in MVD_SHRINK_CATEGORIES
            val updated = when {
                !keepShort -> t.copy(skipped = true)
                t.endMin - t.startMin > MVD_MAX_MIN -> t.copy(endMin = t.startMin + MVD_MAX_MIN)
                else -> return@mapNotNull null
            }
            TaskChange(t, updated)
        }
        return BatchProposal(changes)
    }

    private class Placer(
        private val window: SleepWindow,
        private val dayTasks: (Long) -> List<PlannedTaskEntity>,
        private val excludeIds: Set<Long>,
    ) {
        private val busyByDay = HashMap<Long, MutableList<Pair<Int, Int>>>()

        private fun busy(epoch: Long) = busyByDay.getOrPut(epoch) {
            dayTasks(epoch).filter { !it.skipped && it.id !in excludeIds }.map { it.startMin to it.endMin }.toMutableList()
        }

        fun reserve(task: PlannedTaskEntity) {
            busy(task.date) += task.startMin to task.endMin
        }

        fun place(
            task: PlannedTaskEntity, durationMin: Int, todayEpoch: Long, offsets: IntRange, nowMin: Int, title: String = task.title,
        ): TaskChange? {
            for (offset in offsets) {
                val epoch = todayEpoch + offset
                val notBefore = if (offset == 0) nowMin else 0
                val slot = ScheduleRules.nextFreeSlot(busy(epoch), durationMin, notBefore, window) ?: continue
                return TaskChange(
                    task,
                    task.copy(
                        title = title, date = epoch, startMin = slot, endMin = slot + durationMin,
                        rescheduledFromDate = task.rescheduledFromDate ?: task.date, completed = false, completedAt = null,
                    ),
                )
            }
            return null
        }
    }
}
