package com.ascend.app.data.coach

import com.ascend.app.data.repository.ChecklistRepository
import com.ascend.app.data.repository.FitnessRepository
import com.ascend.app.data.repository.FocusRepository
import com.ascend.app.data.repository.NutritionRepository
import com.ascend.app.data.repository.PlannerRepository
import com.ascend.app.data.repository.ProfileRepository
import com.ascend.app.domain.Dates
import com.ascend.app.domain.FactSheetBuilder
import com.ascend.app.domain.SeedData
import kotlinx.coroutines.flow.first
import java.time.LocalDate

data class CoachFacts(val facts: List<Fact>, val constraints: CoachConstraints)

class CoachFactsLoader(
    private val planner: PlannerRepository,
    private val focus: FocusRepository,
    private val fitness: FitnessRepository,
    private val nutrition: NutritionRepository,
    private val checklists: ChecklistRepository,
    private val profile: ProfileRepository,
) {
    suspend fun load(today: LocalDate = Dates.today()): CoachFacts {
        val weekStart = Dates.weekStart(today)
        val from = weekStart.toEpochDay()
        val to = from + 6
        val p = profile.get()
        val facts = FactSheetBuilder.build(
            weekStart = weekStart,
            today = today,
            tasks = planner.observeRange(from, to).first(),
            focus = focus.observeSessionsBetween(Dates.startOfDayMs(weekStart), Dates.endOfDayMs(weekStart.plusDays(6))).first(),
            workouts = fitness.observeSessions().first(),
            food = nutrition.observeFoodRange(from, to).first(),
            sleep = nutrition.observeSleep().first(),
            habitItems = checklists.observeItems(SeedData.HABITS).first(),
            habitLogs = checklists.observeLogsRange(from, to).first(),
            profile = p,
        )
        return CoachFacts(facts, FactSheetBuilder.constraints(p))
    }
}
