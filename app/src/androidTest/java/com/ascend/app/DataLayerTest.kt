package com.ascend.app

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ascend.app.data.backup.BackupBundle
import com.ascend.app.data.backup.restore
import com.ascend.app.data.backup.snapshot
import com.ascend.app.data.local.*
import com.ascend.app.data.repository.*
import com.ascend.app.domain.Dates
import com.ascend.app.domain.ScheduleRules
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AscendDatabase::class.java)

    @Test
    fun migrate1To2KeepsTasksAndAddsPriority() {
        helper.createDatabase(dbName, 1).apply {
            execSQL("INSERT INTO planned_tasks (id, date, title, category, startMin, endMin, templateId, completed, completedAt, actualMinutes, reminder, notes, rescheduledFromDate, skipped) VALUES (1, 100, 'Learn', 'CAREER', 915, 1035, NULL, 0, NULL, NULL, 0, '', NULL, 0)")
            execSQL("INSERT INTO planned_tasks (id, date, title, category, startMin, endMin, templateId, completed, completedAt, actualMinutes, reminder, notes, rescheduledFromDate, skipped) VALUES (2, 100, 'Lunch', 'HEALTH', 870, 915, NULL, 1, 5, NULL, 0, '', NULL, 0)")
            close()
        }
        val db = helper.runMigrationsAndValidate(dbName, 2, true, MIGRATION_1_2)
        db.query("SELECT id, title, priority, completed FROM planned_tasks ORDER BY id").use { c ->
            assertTrue(c.moveToNext()); assertEquals("Learn", c.getString(1)); assertEquals(1, c.getInt(2))
            assertTrue(c.moveToNext()); assertEquals("Lunch", c.getString(1)); assertEquals(0, c.getInt(2)); assertEquals(1, c.getInt(3))
        }
    }

    @Test
    fun migratedDatabaseOpensWithRoom() {
        helper.createDatabase(dbName, 1).close()
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.databaseBuilder(ctx, AscendDatabase::class.java, dbName).addMigrations(*Migrations.ALL).build()
        runTest { assertNull(db.profileDao().get()) }
        db.close()
    }
}

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private lateinit var db: AscendDatabase
    private lateinit var planner: PlannerRepository
    private lateinit var focus: FocusRepository
    private val monday = LocalDate.of(2027, 3, 1)
    private val sunday = LocalDate.of(2027, 3, 7)

    @Before fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AscendDatabase::class.java).build()
        Seeder(db).seedIfNeeded()
        planner = PlannerRepository(db, db.plannerDao(), db.profileDao()) {}
        focus = FocusRepository(db, db.focusDao(), planner)
    }

    @After fun tearDown() = db.close()

    @Test fun seedIsIdempotentAndProfilePrepopulated() = runTest {
        Seeder(db).seedIfNeeded()
        val p = db.profileDao().get()!!
        assertEquals(173.0, p.heightCm, 0.0)
        assertEquals("Applied AI/ML Engineer", p.careerGoal)
        assertEquals(SeedData_templateCount(), db.plannerDao().templateCount())
        assertEquals(4, db.careerDao().observePhases().first().size)
    }

    @Test fun ensureDayMaterializesOnceFromTemplate() = runTest {
        planner.ensureDay(sunday)
        planner.ensureDay(sunday)
        val tasks = planner.day(sunday.toEpochDay())
        assertEquals(240, tasks.filter { it.category == "CAREER" }.sumOf { it.endMin - it.startMin })
        assertEquals(tasks.size, tasks.map { it.title + it.startMin }.toSet().size)
    }

    @Test fun crudRejectsSleepWindow() = runTest {
        val bad = PlannedTaskEntity(date = monday.toEpochDay(), title = "Late", category = "CAREER", startMin = 1340, endMin = 1400)
        assertEquals(TaskResult.InsideSleepWindow, planner.save(bad))
        val early = bad.copy(startMin = 300, endMin = 330)
        assertEquals(TaskResult.InsideSleepWindow, planner.save(early))
        val ok = planner.save(bad.copy(startMin = 1335, endMin = 1350)) as TaskResult.Ok
        val saved = planner.task(ok.id)!!
        planner.save(saved.copy(title = "Edited"))
        assertEquals("Edited", planner.task(ok.id)!!.title)
        planner.toggle(planner.task(ok.id)!!)
        assertTrue(planner.task(ok.id)!!.completed)
        planner.remove(planner.task(ok.id)!!)
        assertNull(planner.task(ok.id))
    }

    @Test fun rescheduleMovesMissedTaskToFreeSlotBeforeSleep() = runTest {
        planner.ensureRange(monday, 7)
        // The seeded weekdays are booked 06:30–22:30, so a missed 30-min block at 21:00 Monday
        // must skip to the first 30-min gap of the week: Saturday 13:30 (after lunch, before projects).
        val missed = planner.day(monday.toEpochDay()).first { it.title == "Breakfast & grooming" }
        assertTrue(planner.reschedule(missed, monday, 21 * 60) is TaskResult.Ok)
        val moved = planner.task(missed.id)!!
        assertEquals(monday.plusDays(5).toEpochDay(), moved.date)
        assertEquals(13 * 60 + 30, moved.startMin)
        assertTrue(ScheduleRules.isWithinAwakeWindow(moved.startMin, moved.endMin))
        assertEquals(monday.toEpochDay(), moved.rescheduledFromDate)
        val others = planner.day(moved.date).filter { it.id != moved.id }
        assertTrue(others.none { ScheduleRules.overlaps(it.startMin, it.endMin, moved.startMin, moved.endMin) })
    }

    @Test fun rescheduleReportsNoSlotInsteadOfOverbooking() = runTest {
        planner.ensureRange(monday, 7)
        val career = planner.day(monday.toEpochDay()).first { it.title == "Career learning" } // 2 h
        assertEquals(TaskResult.NoFreeSlot, planner.reschedule(career, monday, 21 * 60))
        assertEquals(monday.toEpochDay(), planner.task(career.id)!!.date)
    }

    @Test fun rescheduleUsesSameDayGapWhenAvailable() = runTest {
        planner.ensureDay(monday)
        planner.day(monday.toEpochDay()).filter { it.startMin >= 20 * 60 }.forEach { planner.remove(it) }
        val missed = planner.day(monday.toEpochDay()).first { it.title == "Academics" }
        assertTrue(planner.reschedule(missed, monday, 20 * 60 + 5) is TaskResult.Ok)
        val moved = planner.task(missed.id)!!
        assertEquals(monday.toEpochDay(), moved.date)
        assertEquals(20 * 60 + 15, moved.startMin)
    }

    @Test fun focusSessionAwardsPointsPlantsAndRecordsActual() = runTest {
        planner.ensureDay(monday)
        val task = planner.day(monday.toEpochDay()).first { it.category == "CAREER" }
        val outcome = focus.finishSession("CAREER", task.id, 45, 45 * 60, completed = true, rewardsEnabled = true, random = Random(1))
        assertEquals(55, outcome.points)
        assertNotNull(outcome.plantKind)
        assertEquals(55, db.focusDao().balance())
        assertEquals(45, planner.task(task.id)!!.actualMinutes)
        assertEquals(1, db.focusDao().observePark().first().size)

        val off = focus.finishSession("CAREER", null, 25, 25 * 60, completed = true, rewardsEnabled = false)
        assertEquals(0, off.points)
        assertEquals(55, db.focusDao().balance())
    }

    @Test fun shopNeverOverdraws() = runTest {
        assertFalse(focus.buy("cape"))
        db.focusDao().insertLedger(PointsLedgerEntity(delta = 130, reason = "test", at = 0))
        assertTrue(focus.buy("beanie_ink"))
        assertEquals(10, db.focusDao().balance())
        val owned = db.focusDao().observeGear().first()
        focus.setEquipped("beanie_ink", true, owned)
        assertTrue(db.focusDao().observeGear().first().first { it.itemId == "beanie_ink" }.equipped)
    }

    private fun SeedData_templateCount() = com.ascend.app.domain.SeedData.templates().size
}

@RunWith(AndroidJUnit4::class)
class BackupAndPersistenceTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test fun backupRestoresIntoFreshDatabase() = runTest {
        val source = Room.inMemoryDatabaseBuilder(ctx, AscendDatabase::class.java).build()
        Seeder(source).seedIfNeeded()
        val planner = PlannerRepository(source, source.plannerDao(), source.profileDao()) {}
        planner.ensureDay(LocalDate.of(2027, 3, 1))
        source.nutritionDao().upsertFood(FoodEntryEntity(date = 1, meal = "Lunch", name = "Paneer", calories = null, proteinG = 20.0, carbsG = null, fatG = null, loggedAt = 1))
        val text = json.encodeToString(BackupBundle.serializer(), source.backupDao().snapshot(AscendDatabase.VERSION, 1))
        source.close()

        val target = Room.inMemoryDatabaseBuilder(ctx, AscendDatabase::class.java).build()
        target.backupDao().restore(json.decodeFromString(BackupBundle.serializer(), text))
        assertEquals(15, target.plannerDao().day(LocalDate.of(2027, 3, 1).toEpochDay()).size)
        val food = target.nutritionDao().observeFood(1).first().single()
        assertEquals(20.0, food.proteinG!!, 0.0)
        assertNull("blank stays blank after restore", food.calories)
        assertTrue(target.fitnessDao().observeRoutineExercises().first().isNotEmpty())
        target.close()
    }

    @Test fun dataSurvivesClosingAndReopeningTheDatabaseFile() = runTest {
        val name = "persist-test.db"
        ctx.deleteDatabase(name)
        var db = Room.databaseBuilder(ctx, AscendDatabase::class.java, name).build()
        db.nutritionDao().insertWater(WaterEntryEntity(date = Dates.todayEpoch(), ml = 500, loggedAt = 1))
        db.close()
        db = Room.databaseBuilder(ctx, AscendDatabase::class.java, name).addMigrations(*Migrations.ALL).build()
        assertEquals(500, db.nutritionDao().observeWater(Dates.todayEpoch()).first().sumOf { it.ml })
        db.close()
        ctx.deleteDatabase(name)
    }
}
