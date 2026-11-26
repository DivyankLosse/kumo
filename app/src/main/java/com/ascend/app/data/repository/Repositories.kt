package com.ascend.app.data.repository

import androidx.room.withTransaction
import com.ascend.app.data.local.*
import com.ascend.app.domain.Dates
import com.ascend.app.domain.BatchProposal
import com.ascend.app.domain.Fur
import com.ascend.app.domain.Recovery
import com.ascend.app.domain.RecoveryOption
import com.ascend.app.domain.RecoveryProposal
import com.ascend.app.domain.SleepWindow
import com.ascend.app.domain.TaskChange
import com.ascend.app.domain.Rewards
import com.ascend.app.domain.ScheduleRules
import com.ascend.app.domain.SeedData
import java.time.LocalDate
import kotlin.random.Random

/** Thin repositories: CRUD comes from the DAO by delegation, domain rules live here. */

class ProfileRepository(private val dao: ProfileDao) : ProfileDao by dao

sealed interface TaskResult {
    data class Ok(val id: Long) : TaskResult
    data object InsideSleepWindow : TaskResult
    data object InvalidTime : TaskResult
    data object NoFreeSlot : TaskResult
}

class PlannerRepository(
    private val db: AscendDatabase,
    private val dao: PlannerDao,
    private val profileDao: ProfileDao,
    private val onChanged: suspend () -> Unit,
) : PlannerDao by dao {

    /** Copies the weekly recurring template into concrete tasks the first time a day is viewed. */
    suspend fun ensureDay(date: LocalDate) {
        val epoch = date.toEpochDay()
        if (dao.isMaterialized(epoch)) return
        // Before first-run seeding finishes there are no templates; don't lock the day in empty.
        if (dao.templateCount() == 0) return
        val tasks = dao.templatesFor(date.dayOfWeek.value).map {
            PlannedTaskEntity(
                date = epoch, title = it.title, category = it.category, startMin = it.startMin, endMin = it.endMin,
                templateId = it.id, reminder = it.reminder,
                priority = if (it.category == "CAREER") 1 else 0,
            )
        }
        dao.materialize(epoch, tasks)
    }

    suspend fun ensureRange(from: LocalDate, days: Int) = repeat(days) { ensureDay(from.plusDays(it.toLong())) }

    suspend fun sleepWindow(): SleepWindow =
        profileDao.get()?.let { SleepWindow(it.sleepStartMin, it.wakeMin) } ?: SleepWindow.DEFAULT

    suspend fun save(task: PlannedTaskEntity): TaskResult {
        if (task.endMin <= task.startMin) return TaskResult.InvalidTime
        if (!ScheduleRules.isWithinAwakeWindow(task.startMin, task.endMin, sleepWindow())) return TaskResult.InsideSleepWindow
        val id = dao.upsertTask(task)
        onChanged()
        return TaskResult.Ok(if (task.id == 0L) id else task.id)
    }

    /** Saves a recurring weekly block; it applies to days not yet opened. */
    suspend fun saveTemplate(template: TaskTemplateEntity): TaskResult {
        if (!ScheduleRules.isWithinAwakeWindow(template.startMin, template.endMin, sleepWindow())) return TaskResult.InsideSleepWindow
        return TaskResult.Ok(dao.upsertTemplate(template))
    }

    suspend fun toggle(task: PlannedTaskEntity, now: Long = System.currentTimeMillis()) {
        val done = !task.completed
        dao.setCompleted(task.id, done, if (done) now else null)
        onChanged()
    }

    suspend fun remove(task: PlannedTaskEntity) {
        dao.deleteTask(task)
        onChanged()
    }

    /**
     * Moves a missed task to the next free slot today (after now), else the earliest slot on
     * following days, never into the protected sleep window.
     */
    suspend fun reschedule(task: PlannedTaskEntity, today: LocalDate = Dates.today(), nowMin: Int = Dates.nowMinutes()): TaskResult {
        val proposal = proposeRecovery(RecoveryOption.RECOVER, task, today, nowMin) ?: return TaskResult.NoFreeSlot
        applyChanges(listOf(proposal.change))
        return TaskResult.Ok(task.id)
    }

    /** Reads what the domain needs to propose a recovery; writes nothing apart from lazily materializing days. */
    private suspend fun daysFrom(today: LocalDate): (Long) -> List<PlannedTaskEntity> {
        (0..Recovery.LOOKAHEAD_DAYS).forEach { ensureDay(today.plusDays(it.toLong())) }
        val byDay = (0..Recovery.LOOKAHEAD_DAYS).associate {
            val epoch = today.toEpochDay() + it
            epoch to dao.day(epoch)
        }
        return { epoch -> byDay[epoch].orEmpty() }
    }

    suspend fun proposeRecovery(
        option: RecoveryOption, task: PlannedTaskEntity, today: LocalDate = Dates.today(), nowMin: Int = Dates.nowMinutes(),
    ): RecoveryProposal? =
        Recovery.propose(option, task, today.toEpochDay(), nowMin, sleepWindow(), daysFrom(today))

    suspend fun proposeRecoverRest(
        missed: List<PlannedTaskEntity>, today: LocalDate = Dates.today(), nowMin: Int = Dates.nowMinutes(),
    ): BatchProposal = Recovery.proposeRecoverRest(missed, today.toEpochDay(), nowMin, sleepWindow(), daysFrom(today))

    suspend fun proposeMinimumViableDay(date: LocalDate, nowMin: Int): BatchProposal {
        ensureDay(date)
        return Recovery.minimumViableDay(dao.day(date.toEpochDay()), nowMin)
    }

    /** Writes every change in one transaction. Each update targets its own row id, so nothing is duplicated. */
    suspend fun applyChanges(changes: List<TaskChange>) {
        if (changes.isEmpty()) return
        db.withTransaction { changes.forEach { dao.upsertTask(it.updated) } }
        onChanged()
    }

    /** Restores the pre-change copies. */
    suspend fun undoChanges(changes: List<TaskChange>) {
        if (changes.isEmpty()) return
        db.withTransaction { changes.forEach { dao.upsertTask(it.original) } }
        onChanged()
    }

    suspend fun recordActual(taskId: Long, minutes: Int) {
        val task = dao.task(taskId) ?: return
        dao.upsertTask(task.copy(actualMinutes = (task.actualMinutes ?: 0) + minutes))
    }
}

class FocusRepository(
    private val db: AscendDatabase,
    private val dao: FocusDao,
    private val planner: PlannerRepository,
) : FocusDao by dao {

    data class SessionOutcome(val sessionId: Long, val points: Int, val plantKind: String?)

    suspend fun finishSession(
        category: String,
        taskId: Long?,
        plannedMinutes: Int,
        actualSeconds: Int,
        completed: Boolean,
        rewardsEnabled: Boolean,
        now: Long = System.currentTimeMillis(),
        random: Random = Random.Default,
    ): SessionOutcome = db.withTransaction {
        val points = if (rewardsEnabled) Rewards.pointsFor(actualSeconds, plannedMinutes, completed) else 0
        val id = dao.insertSession(
            FocusSessionEntity(
                category = category, taskId = taskId, plannedMinutes = plannedMinutes,
                actualSeconds = actualSeconds, startedAt = now - actualSeconds * 1000L,
                completed = completed, pointsEarned = points,
            ),
        )
        if (points > 0) dao.insertLedger(PointsLedgerEntity(delta = points, reason = "Focus · $category", at = now))
        if (taskId != null && actualSeconds >= 60) planner.recordActual(taskId, actualSeconds / 60)
        val plant = if (completed) Rewards.plantKinds[random.nextInt(Rewards.plantKinds.size)] else null
        if (plant != null) {
            dao.insertPlant(ParkPlantEntity(sessionId = id, kind = plant, x = random.nextFloat(), y = random.nextFloat(), plantedAt = now))
        }
        SessionOutcome(id, points, plant)
    }

    /** Returns false when the balance is too low; core features never depend on this. */
    suspend fun buy(itemId: String, now: Long = System.currentTimeMillis()): Boolean = db.withTransaction {
        val item = Rewards.item(itemId) ?: return@withTransaction false
        if (dao.balance() < item.cost) return@withTransaction false
        if (item.cost > 0) dao.insertLedger(PointsLedgerEntity(delta = -item.cost, reason = "Shop · ${item.name}", at = now))
        dao.upsertGear(OwnedGearEntity(itemId, equipped = false, acquiredAt = now))
        true
    }

    suspend fun setEquipped(itemId: String, equip: Boolean, owned: List<OwnedGearEntity>) = db.withTransaction {
        val item = Rewards.item(itemId) ?: return@withTransaction
        if (equip) {
            val sameSlot = owned.filter { Rewards.item(it.itemId)?.slot == item.slot }.map { it.itemId }
            dao.unequip(sameSlot)
        }
        val current = owned.firstOrNull { it.itemId == itemId } ?: return@withTransaction
        dao.upsertGear(current.copy(equipped = equip))
    }

    suspend fun chooseCompanion(name: String, fur: Fur) = dao.upsertCompanion(CompanionEntity(name = name.ifBlank { "Kumo" }, fur = fur.name))
}

class CareerRepository(dao: CareerDao) : CareerDao by dao
class NotesRepository(dao: NotesDao) : NotesDao by dao
class FitnessRepository(private val dao: FitnessDao) : FitnessDao by dao {
    suspend fun createRoutine(name: String, exerciseIds: List<Long>): Long {
        val id = dao.insertRoutine(RoutineEntity(name = name, dayOfWeek = null, custom = true))
        dao.insertRoutineExercises(exerciseIds.mapIndexed { i, e -> RoutineExerciseEntity(id, e, i, 3, "8-12") })
        return id
    }
}
class NutritionRepository(dao: NutritionDao) : NutritionDao by dao
class AcademicsRepository(dao: AcademicsDao) : AcademicsDao by dao
class CommunicationRepository(dao: CommunicationDao) : CommunicationDao by dao
class ChecklistRepository(dao: ChecklistDao) : ChecklistDao by dao
class LifestyleRepository(dao: LifestyleDao) : LifestyleDao by dao

/** First-run seeding. Idempotent: each block checks whether its table is already populated. */
class Seeder(private val db: AscendDatabase) {
    suspend fun seedIfNeeded() = db.withTransaction {
        if (db.profileDao().get() == null) db.profileDao().upsert(SeedData.profile)
        val planner = db.plannerDao()
        if (planner.templateCount() == 0) planner.insertTemplates(SeedData.templates())
        val career = db.careerDao()
        if (career.phaseCount() == 0) {
            career.insertPhases(SeedData.phases)
            career.insertTopics(SeedData.topics())
        }
        val fitness = db.fitnessDao()
        if (fitness.exerciseCount() == 0) {
            val ids = fitness.insertExercises(SeedData.exercises.map { (n, g, e) -> ExerciseEntity(name = n, muscleGroup = g, equipment = e) })
            val byName = SeedData.exercises.map { it.first }.zip(ids).toMap()
            SeedData.routines.forEach { (dow, items) ->
                val rid = fitness.insertRoutine(RoutineEntity(name = SeedData.split.getValue(dow), dayOfWeek = dow))
                fitness.insertRoutineExercises(items.mapIndexed { i, (n, sets, reps) ->
                    RoutineExerciseEntity(rid, byName.getValue(n), i, sets, reps)
                })
            }
            fitness.upsertWeight(BodyWeightEntity(Dates.todayEpoch(), SeedData.profile.startWeightKg))
        }
        if (db.checklistDao().itemCount() == 0) db.checklistDao().insertItems(SeedData.checklists())
        val focus = db.focusDao()
        if (focus.balance() == 0 && db.backupDao().allOwnedGear().isEmpty()) {
            focus.upsertCompanion(CompanionEntity(name = "Kumo", fur = Fur.HONEY.name))
            focus.upsertGear(OwnedGearEntity("scarf_sky", equipped = true, acquiredAt = System.currentTimeMillis()))
        }
    }
}
