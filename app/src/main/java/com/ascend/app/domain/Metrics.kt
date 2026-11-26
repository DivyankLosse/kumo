package com.ascend.app.domain

import com.ascend.app.data.local.FocusSessionEntity
import com.ascend.app.data.local.FoodEntryEntity
import com.ascend.app.data.local.PlannedTaskEntity
import com.ascend.app.data.local.SetEntryEntity

enum class RangeStatus { NOT_LOGGED, BELOW, IN_RANGE, ABOVE }

enum class Trend { UP, SAME, DOWN, NEW }

data class NutritionTotals(
    val calories: Int?,
    val proteinG: Double?,
    val carbsG: Double?,
    val fatG: Double?,
    val entries: Int,
)

object Metrics {

    fun duration(task: PlannedTaskEntity) = task.endMin - task.startMin

    fun completionPercent(tasks: List<PlannedTaskEntity>): Int {
        val counted = tasks.filterNot { it.skipped }
        if (counted.isEmpty()) return 0
        return (counted.count { it.completed } * 100) / counted.size
    }

    /** Completed career tasks (actual time when timed) plus focus sessions not tied to a task. */
    fun careerMinutes(tasks: List<PlannedTaskEntity>, sessions: List<FocusSessionEntity>): Int {
        val fromTasks = tasks.filter { it.category == Category.CAREER.name && it.completed }
            .sumOf { it.actualMinutes ?: duration(it) }
        val fromFocus = sessions.filter { it.category == Category.CAREER.name && it.taskId == null }
            .sumOf { it.actualSeconds / 60 }
        return fromTasks + fromFocus
    }

    fun plannedCareerMinutes(tasks: List<PlannedTaskEntity>): Int =
        tasks.filter { it.category == Category.CAREER.name && !it.skipped }.sumOf { duration(it) }

    /** Sums only the values the user actually entered; a macro stays null if no entry has it. */
    fun nutritionTotals(entries: List<FoodEntryEntity>): NutritionTotals {
        fun <T : Number> sumOrNull(values: List<T?>, add: (List<T>) -> T): T? =
            values.filterNotNull().takeIf { it.isNotEmpty() }?.let(add)
        return NutritionTotals(
            calories = sumOrNull(entries.map { it.calories }) { it.sum() },
            proteinG = sumOrNull(entries.map { it.proteinG }) { it.sum() },
            carbsG = sumOrNull(entries.map { it.carbsG }) { it.sum() },
            fatG = sumOrNull(entries.map { it.fatG }) { it.sum() },
            entries = entries.size,
        )
    }

    fun rangeStatus(value: Double?, min: Int, max: Int): RangeStatus = when {
        value == null -> RangeStatus.NOT_LOGGED
        value < min -> RangeStatus.BELOW
        value > max -> RangeStatus.ABOVE
        else -> RangeStatus.IN_RANGE
    }

    /** Bedtimes after midnight count as late (e.g. 00:30 -> 24:30). */
    fun sleepOnTarget(bedMin: Int, wakeMin: Int): Boolean {
        val bed = if (bedMin < 12 * 60) bedMin + 24 * 60 else bedMin
        return bed <= ScheduleRules.SLEEP_MIN && wakeMin <= ScheduleRules.WAKE_MIN + 15
    }

    fun sleepHours(bedMin: Int, wakeMin: Int): Double {
        val mins = if (wakeMin >= bedMin) wakeMin - bedMin else (24 * 60 - bedMin) + wakeMin
        return mins / 60.0
    }

    /** Epley estimated one-rep max. */
    fun e1rm(weightKg: Double, reps: Int): Double = if (reps <= 1) weightKg else weightKg * (1 + reps / 30.0)

    data class ExerciseRecord(val bestE1rm: Double, val heaviestKg: Double, val bestSessionVolume: Double)

    fun record(sets: List<SetEntryEntity>): ExerciseRecord? {
        if (sets.isEmpty()) return null
        return ExerciseRecord(
            bestE1rm = sets.maxOf { e1rm(it.weightKg, it.reps) },
            heaviestKg = sets.maxOf { it.weightKg },
            bestSessionVolume = sets.groupBy { it.sessionId }.values.maxOf { s -> s.sumOf { it.weightKg * it.reps } },
        )
    }

    /** Compares the best set of the latest session with the session before it. */
    fun overloadTrend(setsForExercise: List<SetEntryEntity>): Trend {
        val bySession = setsForExercise.groupBy { it.sessionId }
            .entries.sortedBy { e -> e.value.minOf { it.loggedAt } }
        if (bySession.size < 2) return Trend.NEW
        val last = bySession[bySession.size - 1].value.maxOf { e1rm(it.weightKg, it.reps) }
        val prev = bySession[bySession.size - 2].value.maxOf { e1rm(it.weightKg, it.reps) }
        return when {
            last > prev + 0.01 -> Trend.UP
            last < prev - 0.01 -> Trend.DOWN
            else -> Trend.SAME
        }
    }

    fun bmi(weightKg: Double, heightCm: Double): Double = weightKg / ((heightCm / 100) * (heightCm / 100))
}
