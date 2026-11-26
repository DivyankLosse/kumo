package com.ascend.app.domain

import com.ascend.app.data.local.PlannedTaskEntity

data class OverloadConfig(
    val bookedPctThreshold: Double = 0.85,
    val minBufferMin: Int = 30,
    val maxFocusRunMin: Int = 180,
    /** Focusable blocks separated by at most this many minutes count as one continuous run. */
    val focusRunGapMin: Int = 5,
)

data class OverloadWarning(val kind: Kind, val value: Int, val threshold: Int) {
    enum class Kind { HIGHLY_BOOKED, LOW_BUFFER, LONG_FOCUS_RUN }
}

data class OverloadReport(
    val awakeMin: Int,
    val fixedMin: Int,
    val discretionaryMin: Int,
    val flexibleMin: Int,
    val bookedPct: Double,
    val bufferMin: Int,
    val longestFocusRunMin: Int,
    val warnings: List<OverloadWarning>,
) {
    /** Calm, practical one-liners. The booked message already states the slack, so it replaces the buffer one. */
    fun messages(): List<String> {
        val booked = warnings.any { it.kind == OverloadWarning.Kind.HIGHLY_BOOKED }
        val slack = bufferMin.coerceAtLeast(0)
        return warnings.mapNotNull { w ->
            when (w.kind) {
                OverloadWarning.Kind.HIGHLY_BOOKED -> "Today is ${w.value}% booked: $slack min of slack"
                OverloadWarning.Kind.LOW_BUFFER -> if (booked) null else "Only $slack min of slack today"
                OverloadWarning.Kind.LONG_FOCUS_RUN -> "${formatDuration(w.value)} of focus blocks back to back. A short break would help"
            }
        }
    }

    private fun formatDuration(min: Int) = if (min % 60 == 0) "${min / 60}h" else "${min / 60}h ${min % 60}m"
}

data class CareerFeasibility(val plannedMin: Int, val targetMin: Int) {
    val shortfallMin: Int get() = (targetMin - plannedMin).coerceAtLeast(0)
    val feasible: Boolean get() = plannedMin >= targetMin
}

/** Pure, deterministic load analysis of one day's plan. */
object OverloadAnalyzer {
    val FIXED_CATEGORIES = setOf(Category.COLLEGE.name, Category.COMMUTE.name)

    fun isFixed(task: PlannedTaskEntity) = task.category in FIXED_CATEGORIES

    /**
     * Skipped tasks never count. Completed tasks DO count toward [OverloadReport.bookedPct] because they
     * already used capacity, but are ignored for the focus run, which describes what is still ahead.
     */
    fun analyze(
        tasks: List<PlannedTaskEntity>,
        sleepWindow: SleepWindow = SleepWindow.DEFAULT,
        config: OverloadConfig = OverloadConfig(),
    ): OverloadReport {
        val active = tasks.filter { !it.skipped }
        val awake = sleepWindow.awakeMinutes
        val fixed = active.filter(::isFixed).sumOf { it.endMin - it.startMin }
        val flexible = active.filterNot(::isFixed).sumOf { it.endMin - it.startMin }
        val discretionary = (awake - fixed).coerceAtLeast(0)
        val bookedPct = flexible.toDouble() / discretionary.coerceAtLeast(1)
        val buffer = discretionary - flexible
        val run = longestFocusRun(active.filter { !it.completed }, config.focusRunGapMin)

        val warnings = buildList {
            if (flexible > 0 && bookedPct >= config.bookedPctThreshold) {
                add(OverloadWarning(OverloadWarning.Kind.HIGHLY_BOOKED, Math.round(bookedPct * 100).toInt(), (config.bookedPctThreshold * 100).toInt()))
            }
            if (flexible > 0 && buffer < config.minBufferMin) {
                add(OverloadWarning(OverloadWarning.Kind.LOW_BUFFER, buffer, config.minBufferMin))
            }
            if (run > config.maxFocusRunMin) add(OverloadWarning(OverloadWarning.Kind.LONG_FOCUS_RUN, run, config.maxFocusRunMin))
        }
        return OverloadReport(awake, fixed, discretionary, flexible, bookedPct, buffer, run, warnings)
    }

    private fun longestFocusRun(tasks: List<PlannedTaskEntity>, gap: Int): Int {
        var best = 0
        var runStart = 0
        var runEnd = 0
        var inRun = false
        for (b in tasks.filter { Category.of(it.category).focusable }.sortedBy { it.startMin }) {
            if (inRun && b.startMin - runEnd <= gap) {
                runEnd = maxOf(runEnd, b.endMin)
            } else {
                runStart = b.startMin
                runEnd = b.endMin
                inRun = true
            }
            best = maxOf(best, runEnd - runStart)
        }
        return best
    }

    /** Planned (non-skipped, completed included) career minutes across the week versus the daily target. */
    fun weeklyCareerFeasibility(weekTasks: List<PlannedTaskEntity>, targetMinutesPerDay: Int, days: Int = 7): CareerFeasibility =
        CareerFeasibility(
            plannedMin = weekTasks.filter { it.category == Category.CAREER.name && !it.skipped }.sumOf { it.endMin - it.startMin },
            targetMin = targetMinutesPerDay * days,
        )
}
