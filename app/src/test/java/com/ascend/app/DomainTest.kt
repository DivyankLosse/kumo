package com.ascend.app

import com.ascend.app.data.backup.BackupBundle
import com.ascend.app.data.backup.Csv
import com.ascend.app.data.local.FoodEntryEntity
import com.ascend.app.data.local.PlannedTaskEntity
import com.ascend.app.data.local.SetEntryEntity
import com.ascend.app.domain.*
import com.ascend.app.work.ReminderScheduler
import com.ascend.app.work.ReminderType
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

class ScheduleRulesTest {
    @Test fun awakeWindowRejectsSleepHours() {
        assertTrue(ScheduleRules.isWithinAwakeWindow(390, 420))
        assertTrue(ScheduleRules.isWithinAwakeWindow(1335, 1350))
        assertFalse("ends after 22:30", ScheduleRules.isWithinAwakeWindow(1340, 1360))
        assertFalse("starts before 06:30", ScheduleRules.isWithinAwakeWindow(360, 400))
        assertFalse("zero length", ScheduleRules.isWithinAwakeWindow(600, 600))
    }

    @Test fun nextFreeSlotSkipsBusyBlocksAndRoundsUp() {
        val busy = listOf(600 to 660, 660 to 720)
        assertEquals(720, ScheduleRules.nextFreeSlot(busy, 30, 607))
        assertEquals(615, ScheduleRules.nextFreeSlot(emptyList(), 30, 607))
    }

    @Test fun nextFreeSlotNeverEntersSleepWindow() {
        assertNull(ScheduleRules.nextFreeSlot(emptyList(), 60, 1320))
        assertEquals(1290, ScheduleRules.nextFreeSlot(emptyList(), 60, 1290))
        assertEquals(ScheduleRules.WAKE_MIN, ScheduleRules.nextFreeSlot(emptyList(), 30, 0))
    }

    @Test fun parseAndFormat() {
        assertEquals(930, ScheduleRules.parse("15:30"))
        assertNull(ScheduleRules.parse("25:00"))
        assertNull(ScheduleRules.parse("abc"))
        assertEquals("06:05", ScheduleRules.format(365))
    }
}

class SeedDataTest {
    private val templates = SeedData.templates()
    private fun careerMinutes(dow: Int) = templates.filter { it.dayOfWeek == dow && it.category == "CAREER" }.sumOf { it.endMin - it.startMin }

    @Test fun sundayCareerIsExactlyFourHours() = assertEquals(240, careerMinutes(7))

    @Test fun everyDayHasFourCareerHours() = (1..7).forEach { assertEquals("day $it", 240, careerMinutes(it)) }

    @Test fun noTemplateTouchesSleepWindow() = templates.forEach {
        assertTrue("${it.title} on ${it.dayOfWeek}", ScheduleRules.isWithinAwakeWindow(it.startMin, it.endMin))
    }

    @Test fun noOverlapsWithinADay() = (1..7).forEach { d ->
        val day = templates.filter { it.dayOfWeek == d }.sortedBy { it.startMin }
        day.zipWithNext().forEach { (a, b) -> assertTrue("${a.title} overlaps ${b.title}", a.endMin <= b.startMin) }
    }

    @Test fun weekdayMatchesSpec() {
        val mon = templates.filter { it.dayOfWeek == 1 }.associateBy { it.title }
        assertEquals(480 to 840, mon.getValue("College").let { it.startMin to it.endMin })
        assertEquals(1050 to 1110, mon.getValue("Gym — Push").let { it.startMin to it.endMin })
        assertEquals(915 to 1035, mon.getValue("Career learning").let { it.startMin to it.endMin })
        assertEquals(1200 to 1320, mon.getValue("Career project development").let { it.startMin to it.endMin })
        assertEquals(1335 to 1350, mon.getValue("Night routine").let { it.startMin to it.endMin })
    }

    @Test fun splitAndProfileDefaults() {
        assertEquals(listOf("Push", "Pull", "Legs + Abs", "Recovery", "Upper", "Lower + Arms", "Rest"), (1..7).map { SeedData.split[it] })
        assertEquals(173.0, SeedData.profile.heightCm, 0.0)
        assertEquals(63.0, SeedData.profile.startWeightKg, 0.0)
        assertEquals(100, SeedData.profile.proteinMinG)
        assertEquals(130, SeedData.profile.proteinMaxG)
    }

    @Test fun roadmapHasFourPhasesEachWithMilestone() {
        assertEquals(4, SeedData.phases.size)
        val topics = SeedData.topics()
        (1..4).forEach { p -> assertTrue(topics.any { it.phaseId == p && it.isMilestone }) }
    }
}

class MetricsTest {
    private fun task(cat: String, s: Int, e: Int, done: Boolean, actual: Int? = null) =
        PlannedTaskEntity(date = 0, title = "t", category = cat, startMin = s, endMin = e, completed = done, actualMinutes = actual)

    @Test fun completionIgnoresSkipped() {
        val tasks = listOf(task("CAREER", 0, 60, true), task("HEALTH", 60, 90, false), task("X", 90, 100, false).copy(skipped = true))
        assertEquals(50, Metrics.completionPercent(tasks))
        assertEquals(0, Metrics.completionPercent(emptyList()))
    }

    @Test fun careerMinutesUsesActualWhenTimed() {
        val tasks = listOf(task("CAREER", 0, 120, true, actual = 95), task("CAREER", 200, 320, true), task("CAREER", 400, 460, false))
        assertEquals(95 + 120, Metrics.careerMinutes(tasks, emptyList()))
    }

    @Test fun nutritionTotalsNeverInventValues() {
        val e = listOf(
            FoodEntryEntity(date = 0, meal = "Lunch", name = "Dal", calories = null, proteinG = 18.0, carbsG = null, fatG = null, loggedAt = 0),
            FoodEntryEntity(date = 0, meal = "Lunch", name = "Rice", calories = null, proteinG = null, carbsG = null, fatG = null, loggedAt = 0),
        )
        val t = Metrics.nutritionTotals(e)
        assertNull(t.calories); assertNull(t.carbsG); assertNull(t.fatG)
        assertEquals(18.0, t.proteinG!!, 0.0)
        assertNull(Metrics.nutritionTotals(emptyList()).proteinG)
    }

    @Test fun proteinRangeStatus() {
        assertEquals(RangeStatus.NOT_LOGGED, Metrics.rangeStatus(null, 100, 130))
        assertEquals(RangeStatus.BELOW, Metrics.rangeStatus(99.0, 100, 130))
        assertEquals(RangeStatus.IN_RANGE, Metrics.rangeStatus(130.0, 100, 130))
        assertEquals(RangeStatus.ABOVE, Metrics.rangeStatus(131.0, 100, 130))
    }

    @Test fun sleepTarget() {
        assertTrue(Metrics.sleepOnTarget(1350, 390))
        assertFalse("after midnight is late", Metrics.sleepOnTarget(30, 390))
        assertEquals(8.0, Metrics.sleepHours(1350, 390), 0.001)
    }

    @Test fun overloadTrendAndRecords() {
        fun set(session: Long, reps: Int, kg: Double, at: Long) = SetEntryEntity(sessionId = session, exerciseId = 1, setIndex = 1, reps = reps, weightKg = kg, loggedAt = at)
        assertEquals(Trend.NEW, Metrics.overloadTrend(listOf(set(1, 8, 50.0, 1))))
        assertEquals(Trend.UP, Metrics.overloadTrend(listOf(set(1, 8, 50.0, 1), set(2, 8, 52.5, 2))))
        assertEquals(Trend.DOWN, Metrics.overloadTrend(listOf(set(1, 8, 50.0, 1), set(2, 6, 50.0, 2))))
        val rec = Metrics.record(listOf(set(1, 5, 60.0, 1), set(1, 10, 50.0, 2)))!!
        assertEquals(60.0, rec.heaviestKg, 0.0)
        assertEquals(70.0, rec.bestE1rm, 0.01) // 60 kg × (1 + 5/30) beats 50 kg × (1 + 10/30) = 66.7
    }
}

class RewardsTest {
    @Test fun pointsPerMinutePlusBonus() {
        assertEquals(45 + 10, Rewards.pointsFor(45 * 60, 45, true))
        assertEquals(20, Rewards.pointsFor(20 * 60 + 59, 45, false))
        assertEquals(5, Rewards.pointsFor(5 * 60, 5, true))
        assertEquals(0, Rewards.pointsFor(-10, 25, false))
    }

    @Test fun catalogIdsUniqueAndStarterIsFree() {
        assertEquals(Rewards.catalog.size, Rewards.catalog.map { it.id }.toSet().size)
        assertEquals(0, Rewards.item("scarf_sky")!!.cost)
    }
}

class DatesTest {
    @Test fun daysRemaining() {
        assertEquals(37L to false, Dates.daysRemainingIn2027(LocalDate.of(2026, 11, 25)))
        assertEquals(365L to true, Dates.daysRemainingIn2027(LocalDate.of(2027, 1, 1)))
        assertEquals(1L to true, Dates.daysRemainingIn2027(LocalDate.of(2027, 12, 31)))
    }
}

class ReminderSchedulerTest {
    @Test fun dailyReminderRollsToTomorrowOncePassed() {
        val now = LocalDateTime.of(2027, 3, 1, 7, 0)
        assertEquals(Duration.ofHours(23).plusMinutes(35), ReminderScheduler.initialDelay(ReminderType.MORNING, now))
        assertEquals(Duration.ofHours(8).plusMinutes(10), ReminderScheduler.initialDelay(ReminderType.CAREER, now))
    }

    @Test fun weeklyReviewTargetsSunday() {
        val mon = LocalDateTime.of(2027, 3, 1, 12, 0) // Monday
        val d = ReminderScheduler.initialDelay(ReminderType.WEEKLY_REVIEW, mon)
        assertEquals(java.time.DayOfWeek.SUNDAY, mon.plus(d).dayOfWeek)
    }
}

class ExportFormatTest {
    @Test fun csvEscaping() {
        assertEquals("plain", Csv.escape("plain"))
        assertEquals("\"a,b\"", Csv.escape("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", Csv.escape("say \"hi\""))
        assertEquals("", Csv.escape(null))
        assertEquals("h1,h2\n1,\"x,y\"\n", Csv.table(listOf("h1", "h2"), listOf(listOf(1, "x,y"))))
    }

    @Test fun backupJsonRoundTrip() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val bundle = BackupBundle(
            schemaVersion = 2, exportedAt = 123,
            profile = listOf(SeedData.profile),
            taskTemplates = SeedData.templates(),
            plannedTasks = listOf(PlannedTaskEntity(id = 5, date = 10, title = "Ship", category = "CAREER", startMin = 900, endMin = 960, priority = 1)),
        )
        val text = json.encodeToString(BackupBundle.serializer(), bundle)
        val back = json.decodeFromString(BackupBundle.serializer(), text)
        assertEquals(bundle, back)
        assertEquals("kumo-backup", back.format)
    }
}
