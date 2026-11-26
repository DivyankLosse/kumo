package com.ascend.app.domain

import com.ascend.app.data.coach.CoachConstraints
import com.ascend.app.data.coach.Fact
import com.ascend.app.data.local.ChecklistItemEntity
import com.ascend.app.data.local.ChecklistLogEntity
import com.ascend.app.data.local.FocusSessionEntity
import com.ascend.app.data.local.FoodEntryEntity
import com.ascend.app.data.local.PlannedTaskEntity
import com.ascend.app.data.local.ProfileEntity
import com.ascend.app.data.local.SleepLogEntity
import com.ascend.app.data.local.WorkoutSessionEntity
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields

object FactIds {
    const val CAREER_MIN = "f_career_min"
    const val CAREER_TARGET_MIN = "f_career_target_min"
    const val CAREER_PCT = "f_career_pct"
    const val BLOCKS_PLANNED = "f_blocks_planned"
    const val BLOCKS_DONE = "f_blocks_done"
    const val COMPLETION_PCT = "f_completion_pct"
    const val FOCUS_MIN_TOTAL = "f_focus_min_total"
    const val GYM_SESSIONS = "f_gym_sessions"
    const val PROTEIN_DAYS = "f_protein_days"
    const val PROTEIN_LOGGED_DAYS = "f_protein_logged_days"
    const val SLEEP_ON_TARGET = "f_sleep_on_target"
    const val SLEEP_LOGGED = "f_sleep_logged"
    const val HABIT_RATE_PCT = "f_habit_rate_pct"

    fun focusMinutes(category: Category) = "f_focus_min_${category.name.lowercase()}"
}

/**
 * Builds the only data the coach ever sees. Metrics come from [Metrics] so they match Analytics exactly.
 * Facts that cannot be computed (nothing logged, no profile) are omitted rather than reported as zero.
 */
object FactSheetBuilder {

    fun periodLabel(weekStart: LocalDate): String =
        "%d-W%02d".format(weekStart.get(IsoFields.WEEK_BASED_YEAR), weekStart.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))

    fun build(
        weekStart: LocalDate,
        today: LocalDate,
        tasks: List<PlannedTaskEntity>,
        focus: List<FocusSessionEntity>,
        workouts: List<WorkoutSessionEntity>,
        food: List<FoodEntryEntity>,
        sleep: List<SleepLogEntity>,
        habitItems: List<ChecklistItemEntity>,
        habitLogs: List<ChecklistLogEntity>,
        profile: ProfileEntity?,
    ): List<Fact> {
        val period = periodLabel(weekStart)
        val range = weekStart.toEpochDay()..weekStart.toEpochDay() + 6
        val weekTasks = tasks.filter { it.date in range }
        val weekFocus = focus.filter { Dates.epochDayOfMs(it.startedAt) in range }
        val facts = mutableListOf<Fact>()
        fun add(id: String, kind: String, value: Number, unit: String) {
            facts.add(Fact(id, kind, value.toDouble(), unit, period))
        }

        val careerMin = Metrics.careerMinutes(weekTasks, weekFocus)
        add(FactIds.CAREER_MIN, "career_minutes", careerMin, "min")
        if (profile != null) {
            val target = (profile.careerHoursDaily * 60 * 7).toInt()
            add(FactIds.CAREER_TARGET_MIN, "career_target_minutes", target, "min")
            if (target > 0) add(FactIds.CAREER_PCT, "career_target_percent", careerMin * 100 / target, "%")
        }

        val counted = weekTasks.filterNot { it.skipped }
        if (counted.isNotEmpty()) {
            add(FactIds.BLOCKS_PLANNED, "blocks_planned", counted.size, "blocks")
            add(FactIds.BLOCKS_DONE, "blocks_completed", counted.count { it.completed }, "blocks")
            add(FactIds.COMPLETION_PCT, "completion_percent", Metrics.completionPercent(weekTasks), "%")
        }

        if (weekFocus.isNotEmpty()) {
            add(FactIds.FOCUS_MIN_TOTAL, "focus_minutes", weekFocus.sumOf { it.actualSeconds } / 60, "min")
            weekFocus.groupBy { Category.of(it.category) }.toSortedMap().forEach { (category, sessions) ->
                add(FactIds.focusMinutes(category), "focus_minutes_${category.name.lowercase()}", sessions.sumOf { it.actualSeconds } / 60, "min")
            }
        }

        add(FactIds.GYM_SESSIONS, "gym_sessions_finished", workouts.count { it.date in range && it.finishedAt != null }, "sessions")

        if (profile != null) {
            val proteinByDay = food.filter { it.date in range }.groupBy { it.date }
                .mapValues { (_, entries) -> Metrics.nutritionTotals(entries).proteinG }
                .filterValues { it != null }
            if (proteinByDay.isNotEmpty()) {
                val inRange = proteinByDay.values.count { Metrics.rangeStatus(it, profile.proteinMinG, profile.proteinMaxG) == RangeStatus.IN_RANGE }
                add(FactIds.PROTEIN_DAYS, "protein_days_in_range", inRange, "days")
                add(FactIds.PROTEIN_LOGGED_DAYS, "protein_days_logged", proteinByDay.size, "days")
            }
        }

        val weekSleep = sleep.filter { it.date in range }
        if (weekSleep.isNotEmpty()) {
            add(FactIds.SLEEP_ON_TARGET, "sleep_nights_on_target", weekSleep.count { Metrics.sleepOnTarget(it.bedMin, it.wakeMin) }, "nights")
            add(FactIds.SLEEP_LOGGED, "sleep_nights_logged", weekSleep.size, "nights")
        }

        val elapsedDays = (ChronoUnit.DAYS.between(weekStart, today) + 1).coerceIn(0, 7).toInt()
        val activeIds = habitItems.filter { it.active }.map { it.id }.toSet()
        if (activeIds.isNotEmpty() && elapsedDays > 0) {
            val done = habitLogs.count { it.date in range && it.itemId in activeIds }
            add(FactIds.HABIT_RATE_PCT, "habit_completion_percent", (done * 100 / (activeIds.size * elapsedDays)).coerceAtMost(100), "%")
        }
        return facts
    }

    fun constraints(profile: ProfileEntity?): CoachConstraints {
        fun hhmm(min: Int) = "%02d:%02d".format(min / 60 % 24, min % 60)
        return CoachConstraints(
            sleepStart = hhmm(profile?.sleepStartMin ?: ScheduleRules.SLEEP_MIN),
            sleepEnd = hhmm(profile?.wakeMin ?: ScheduleRules.WAKE_MIN),
        )
    }
}
