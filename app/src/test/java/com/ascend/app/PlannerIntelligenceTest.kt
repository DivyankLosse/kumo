package com.ascend.app

import com.ascend.app.data.local.PlannedTaskEntity
import com.ascend.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class PlannerIntelligenceTest {
    private val today = 20_000L
    private val night = SleepWindow(23 * 60 + 30, 7 * 60 + 30)
    private val earlyBird = SleepWindow(60, 9 * 60)

    private var nextId = 1L
    private fun task(
        start: Int, end: Int, category: Category = Category.CAREER, date: Long = today, priority: Int = 0,
        completed: Boolean = false, skipped: Boolean = false, title: String = "T$start",
    ) = PlannedTaskEntity(
        id = nextId++, date = date, title = title, category = category.name, startMin = start, endMin = end,
        priority = priority, completed = completed, skipped = skipped,
    )

    private fun seededDay(dow: Int): List<PlannedTaskEntity> = SeedData.templates().filter { it.dayOfWeek == dow }.map {
        task(it.startMin, it.endMin, Category.of(it.category), title = it.title)
    }

    private fun applied(tasks: List<PlannedTaskEntity>, changes: List<TaskChange>): List<PlannedTaskEntity> {
        val byId = changes.associate { it.updated.id to it.updated }
        return tasks.map { byId[it.id] ?: it }
    }

    private fun assertSane(all: List<PlannedTaskEntity>, window: SleepWindow, label: String) {
        all.filter { !it.skipped }.forEach {
            assertTrue("$label: ${it.title} ${it.startMin}-${it.endMin} outside awake window", ScheduleRules.isWithinAwakeWindow(it.startMin, it.endMin, window))
        }
        all.filter { !it.skipped }.groupBy { it.date }.forEach { (_, day) ->
            val sorted = day.sortedBy { it.startMin }
            sorted.zipWithNext().forEach { (a, b) -> assertTrue("$label: overlap ${a.title} / ${b.title}", a.endMin <= b.startMin) }
        }
    }

    // --- analyzer on seeded data ---

    @Test fun seededWeekdayAndSaturdayNumbers() {
        val monday = OverloadAnalyzer.analyze(seededDay(1))
        // Awake 960 min, college 360 + commute 60 are fixed, so 540 discretionary, all of it booked.
        assertEquals(960, monday.awakeMin)
        assertEquals(420, monday.fixedMin)
        assertEquals(540, monday.discretionaryMin)
        assertEquals(540, monday.flexibleMin)
        assertEquals(1.0, monday.bookedPct, 0.0001)
        assertEquals(0, monday.bufferMin)
        assertEquals(180, monday.longestFocusRunMin)
        assertEquals(listOf(OverloadWarning.Kind.HIGHLY_BOOKED, OverloadWarning.Kind.LOW_BUFFER), monday.warnings.map { it.kind })

        val saturday = OverloadAnalyzer.analyze(seededDay(6))
        assertEquals(960, saturday.discretionaryMin)
        assertEquals(735, saturday.flexibleMin)
        assertEquals(225, saturday.bufferMin)
        assertEquals(735 / 960.0, saturday.bookedPct, 0.0001)
        assertEquals(120, saturday.longestFocusRunMin)
        assertTrue(saturday.warnings.isEmpty())
    }

    // --- thresholds ---

    @Test fun bookedPctThreshold() {
        // discretionary 960; 816 min = exactly 85%
        assertTrue(OverloadAnalyzer.analyze(listOf(task(390, 390 + 816, Category.HEALTH))).warnings.any { it.kind == OverloadWarning.Kind.HIGHLY_BOOKED })
        assertTrue(OverloadAnalyzer.analyze(listOf(task(390, 390 + 815, Category.HEALTH))).warnings.none { it.kind == OverloadWarning.Kind.HIGHLY_BOOKED })
    }

    @Test fun bufferThreshold() {
        val lowBuffer = OverloadAnalyzer.analyze(listOf(task(390, 390 + 931, Category.HEALTH)))
        assertEquals(29, lowBuffer.bufferMin)
        assertTrue(lowBuffer.warnings.any { it.kind == OverloadWarning.Kind.LOW_BUFFER })
        val ok = OverloadAnalyzer.analyze(listOf(task(390, 390 + 930, Category.HEALTH)))
        assertEquals(30, ok.bufferMin)
        assertTrue(ok.warnings.none { it.kind == OverloadWarning.Kind.LOW_BUFFER })
    }

    @Test fun focusRunThresholdAndGapTolerance() {
        val exactly180 = listOf(task(480, 600), task(600, 660, Category.ACADEMICS))
        assertTrue(OverloadAnalyzer.analyze(exactly180).warnings.isEmpty())
        val chained = listOf(task(480, 600), task(605, 665, Category.ACADEMICS)) // 5 min gap still chains
        val report = OverloadAnalyzer.analyze(chained)
        assertEquals(185, report.longestFocusRunMin)
        assertTrue(report.warnings.any { it.kind == OverloadWarning.Kind.LONG_FOCUS_RUN })
        val broken = listOf(task(480, 600), task(606, 666, Category.ACADEMICS)) // 6 min gap breaks it
        assertEquals(120, OverloadAnalyzer.analyze(broken).longestFocusRunMin)
        val nonFocusBreak = listOf(task(480, 600), task(600, 615, Category.HEALTH), task(615, 735))
        assertEquals(120, OverloadAnalyzer.analyze(nonFocusBreak).longestFocusRunMin)
    }

    @Test fun customConfigAndSkippedAndCompleted() {
        val tasks = listOf(task(480, 600), task(600, 720, completed = true), task(720, 840, skipped = true))
        val report = OverloadAnalyzer.analyze(tasks)
        assertEquals(240, report.flexibleMin) // completed counts, skipped does not
        assertEquals(120, report.longestFocusRunMin) // completed is not remaining load
        val strict = OverloadAnalyzer.analyze(tasks, config = OverloadConfig(bookedPctThreshold = 0.2))
        assertTrue(strict.warnings.any { it.kind == OverloadWarning.Kind.HIGHLY_BOOKED })
    }

    @Test fun skippedFixedBlocksFreeUpTime() {
        val day = listOf(task(480, 840, Category.COLLEGE, skipped = true), task(900, 960))
        assertEquals(0, OverloadAnalyzer.analyze(day).fixedMin)
    }

    @Test fun messagesAreCalm() {
        val msgs = OverloadAnalyzer.analyze(listOf(task(390, 390 + 880, Category.HEALTH))).messages()
        assertEquals(listOf("Today is 92% booked: 80 min of slack"), msgs.take(1))
        assertTrue(OverloadAnalyzer.analyze(listOf(task(390, 400, Category.HEALTH))).messages().isEmpty())
    }

    @Test fun weeklyCareerFeasibility() {
        val week = (0..6).map { task(480, 480 + 180, date = today + it) } + task(900, 1000, date = today, skipped = true)
        val f = OverloadAnalyzer.weeklyCareerFeasibility(week, 240)
        assertEquals(1260, f.plannedMin)
        assertEquals(1680, f.targetMin)
        assertEquals(420, f.shortfallMin)
        assertFalse(f.feasible)
        assertTrue(OverloadAnalyzer.weeklyCareerFeasibility(week, 180).feasible)
    }

    // --- sleep windows ---

    @Test fun sleepWindowCrossingMidnight() {
        assertEquals(16 * 60, night.awakeMinutes)
        assertTrue(ScheduleRules.isWithinAwakeWindow(7 * 60 + 30, 8 * 60, night))
        assertTrue(ScheduleRules.isWithinAwakeWindow(23 * 60, 23 * 60 + 30, night))
        assertFalse(ScheduleRules.isWithinAwakeWindow(23 * 60, 23 * 60 + 45, night))
        assertFalse(ScheduleRules.isWithinAwakeWindow(7 * 60, 7 * 60 + 45, night))
        assertTrue("default window still allows 06:30", ScheduleRules.isWithinAwakeWindow(390, 420))
        assertEquals(7 * 60 + 30, ScheduleRules.nextFreeSlot(emptyList(), 30, 0, night))
        assertNull(ScheduleRules.nextFreeSlot(emptyList(), 60, 23 * 60, night))
    }

    @Test fun sameDaySleepWindowHasTwoAwakeStretches() {
        assertEquals(16 * 60, earlyBird.awakeMinutes)
        assertTrue(ScheduleRules.isWithinAwakeWindow(0, 60, earlyBird))
        assertFalse(ScheduleRules.isWithinAwakeWindow(30, 90, earlyBird))
        assertEquals(9 * 60, ScheduleRules.nextFreeSlot(listOf(0 to 60), 30, 60, earlyBird))
        val busyUntilEnd = listOf(540 to 1440)
        assertEquals(0, ScheduleRules.nextFreeSlot(busyUntilEnd, 30, 0, earlyBird))
    }

    @Test fun analyzerUsesProvidedWindow() {
        val report = OverloadAnalyzer.analyze(listOf(task(8 * 60, 9 * 60)), night)
        assertEquals(960, report.awakeMin)
        assertEquals(60, report.flexibleMin)
    }

    // --- recovery options ---

    private fun dayLoader(vararg days: List<PlannedTaskEntity>): (Long) -> List<PlannedTaskEntity> {
        val all = days.flatMap { it }
        return { epoch -> all.filter { it.date == epoch } }
    }

    @Test fun eachOptionAvoidsOverlapSleepAndFixedBlocks() {
        val college = task(8 * 60, 14 * 60, Category.COLLEGE, title = "College")
        val missed = task(9 * 60, 10 * 60, Category.CAREER, title = "Study")
        val todayTasks = listOf(college, missed, task(15 * 60, 17 * 60), task(17 * 60, 22 * 60 + 15, Category.PERSONAL))
        val tomorrow = listOf(task(7 * 60, 8 * 60, date = today + 1), task(8 * 60, 14 * 60, Category.COLLEGE, date = today + 1))
        val loader = dayLoader(todayTasks, tomorrow)
        val all = todayTasks + tomorrow
        for (option in RecoveryOption.entries) {
            val p = Recovery.propose(option, missed, today, 10 * 60, SleepWindow.DEFAULT, loader)
            assertNotNull("$option", p)
            assertEquals(missed.id, p!!.change.updated.id)
            assertSane(applied(all, listOf(p.change)), SleepWindow.DEFAULT, "$option")
            assertEquals(college, applied(all, listOf(p.change)).first { it.id == college.id })
        }
        val recover = Recovery.propose(RecoveryOption.RECOVER, missed, today, 10 * 60, SleepWindow.DEFAULT, loader)!!.change.updated
        assertEquals(14 * 60 to 15 * 60, recover.startMin to recover.endMin)
        val shorten = Recovery.propose(RecoveryOption.SHORTEN, missed, today, 10 * 60, SleepWindow.DEFAULT, loader)!!.change.updated
        assertEquals(30, shorten.endMin - shorten.startMin)
        assertEquals("Study (short)", shorten.title)
        val defer = Recovery.propose(RecoveryOption.DEFER, missed, today, 10 * 60, SleepWindow.DEFAULT, loader)!!.change.updated
        assertEquals(today + 1, defer.date)
        assertEquals(14 * 60, defer.startMin)
        val drop = Recovery.propose(RecoveryOption.DROP, missed, today, 10 * 60, SleepWindow.DEFAULT, loader)!!.change.updated
        assertTrue(drop.skipped)
        assertEquals(missed.copy(skipped = true), drop)
    }

    @Test fun shortenedLengthRule() {
        assertEquals(30, Recovery.shortenedMinutes(120))
        assertEquals(30, Recovery.shortenedMinutes(60))
        assertEquals(22, Recovery.shortenedMinutes(45))
        assertEquals(15, Recovery.shortenedMinutes(30))
        assertEquals(15, Recovery.shortenedMinutes(20))
        assertEquals(10, Recovery.shortenedMinutes(10))
    }

    @Test fun fixedTasksAreNeverMoved() {
        val loader = dayLoader(emptyList())
        for (cat in listOf(Category.COLLEGE, Category.COMMUTE)) {
            val fixed = task(480, 540, cat)
            RecoveryOption.entries.forEach { assertNull(Recovery.propose(it, fixed, today, 600, SleepWindow.DEFAULT, loader)) }
        }
        assertTrue(Recovery.proposeRecoverRest(listOf(task(480, 540, Category.COLLEGE)), today, 600, SleepWindow.DEFAULT, loader).changes.isEmpty())
    }

    @Test fun recoveryRespectsCustomSleepWindow() {
        val missed = task(9 * 60, 10 * 60)
        val blocked = listOf(task(7 * 60 + 30, 23 * 60, Category.PERSONAL))
        val p = Recovery.propose(RecoveryOption.RECOVER, missed, today, 12 * 60, night, dayLoader(blocked, listOf(task(8 * 60, 23 * 60 + 30, date = today + 1, category = Category.PERSONAL))))
        // today has only 23:00-23:30 left (30 min) and tomorrow is full, so a 60-min block lands later in the week
        assertNotNull(p)
        assertTrue(p!!.change.updated.date >= today + 2)
        assertTrue(ScheduleRules.isWithinAwakeWindow(p.change.updated.startMin, p.change.updated.endMin, night))
    }

    @Test fun recoverRestPlacesWithoutOverlap() {
        val a = task(7 * 60, 8 * 60, priority = 1, title = "A")
        val b = task(8 * 60, 9 * 60, title = "B")
        val c = task(9 * 60, 10 * 60, Category.FITNESS, title = "C")
        val busy = task(12 * 60, 13 * 60, Category.PERSONAL)
        val loader = dayLoader(listOf(a, b, c, busy))
        val batch = Recovery.proposeRecoverRest(listOf(c, b, a), today, 11 * 60, SleepWindow.DEFAULT, loader)
        assertEquals(3, batch.changes.size)
        assertEquals("A", batch.changes.first().updated.title) // priority first
        assertSane(applied(listOf(a, b, c, busy), batch.changes), SleepWindow.DEFAULT, "batch")
        assertEquals(3, batch.changes.map { it.updated.id }.toSet().size)
    }

    // --- minimum viable day ---

    @Test fun minimumViableDayKeepsEssentialsAndFixedBlocks() {
        val college = task(480, 840, Category.COLLEGE)
        val essential = task(900, 1020, priority = 2, title = "Essential")
        val top = task(1020, 1140, priority = 1, title = "Top")
        val meal = task(1140, 1200, Category.HEALTH, title = "Dinner")
        val social = task(1200, 1320, Category.SOCIAL, title = "Social")
        val done = task(840, 890, completed = true)
        val past = task(420, 470, Category.PERSONAL)
        val day = listOf(college, essential, top, meal, social, done, past)
        val batch = Recovery.minimumViableDay(day, 450)
        val after = applied(day, batch.changes)
        assertEquals(college, after.first { it.id == college.id })
        assertEquals(essential, after.first { it.id == essential.id })
        assertEquals(done, after.first { it.id == done.id })
        assertEquals(30, after.first { it.id == top.id }.let { it.endMin - it.startMin })
        assertEquals(30, after.first { it.id == meal.id }.let { it.endMin - it.startMin })
        assertTrue(after.first { it.id == social.id }.skipped)
        assertEquals(past, after.first { it.id == past.id }.copy(startMin = past.startMin)) // started before now: untouched
        assertSane(after, SleepWindow.DEFAULT, "mvd")
        assertTrue(batch.changes.none { OverloadAnalyzer.isFixed(it.original) || Recovery.isEssential(it.original) })
    }

    // --- property-style ---

    private fun randomDay(rng: Random, window: SleepWindow, date: Long): List<PlannedTaskEntity> {
        val tasks = mutableListOf<PlannedTaskEntity>()
        val (awakeStart, awakeEnd) = window.awakeIntervals.random(rng)
        var cursor = awakeStart + rng.nextInt(0, 60)
        while (cursor < awakeEnd - 15) {
            val len = rng.nextInt(1, 13) * 15
            val end = minOf(cursor + len, awakeEnd)
            if (end > cursor) {
                tasks += task(
                    cursor, end, Category.entries.random(rng), date = date, priority = rng.nextInt(0, 3),
                    completed = rng.nextInt(10) == 0, skipped = rng.nextInt(10) == 0,
                )
            }
            cursor = end + if (rng.nextBoolean()) 0 else rng.nextInt(1, 5) * 15
        }
        return tasks
    }

    @Test fun randomLayoutsNeverOverlapOrEnterSleepAfterRecovery() {
        val rng = Random(20270101)
        val windows = listOf(SleepWindow.DEFAULT, night, earlyBird, SleepWindow(22 * 60, 5 * 60))
        var proposals = 0
        repeat(300) { iteration ->
            val window = windows[iteration % windows.size]
            val todays = randomDay(rng, window, today)
            val tomorrows = randomDay(rng, window, today + 1)
            val rest = (2..6).flatMap { randomDay(rng, window, today + it.toLong()) }
            val all = todays + tomorrows + rest
            val loader = dayLoader(all)
            val nowMin = rng.nextInt(0, 1440)
            val label = "iteration $iteration window $window now $nowMin"
            assertSane(all, window, "$label baseline")

            val victim = todays.filter { !it.skipped }.randomOrNull(rng)
            if (victim != null) for (option in RecoveryOption.entries) {
                val p = Recovery.propose(option, victim, today, nowMin, window, loader)
                if (OverloadAnalyzer.isFixed(victim)) {
                    assertNull(p)
                } else if (p != null) {
                    proposals++
                    assertEquals(victim.id, p.change.updated.id)
                    val after = applied(all, listOf(p.change))
                    assertSane(after, window, "$label $option")
                    assertEquals(all.size, after.size)
                    all.filter { OverloadAnalyzer.isFixed(it) }.forEach { f -> assertEquals(f, after.first { it.id == f.id }) }
                }
            }

            val missed = todays.filter { Recovery.isMissed(it, today, nowMin) }
            val batch = Recovery.proposeRecoverRest(missed, today, nowMin, window, loader)
            assertSane(applied(all, batch.changes), window, "$label batch")
            assertEquals(batch.changes.size + batch.unplaced.size, missed.count { !OverloadAnalyzer.isFixed(it) })

            val mvd = Recovery.minimumViableDay(todays, nowMin)
            val afterMvd = applied(all, mvd.changes)
            assertSane(afterMvd, window, "$label mvd")
            todays.filter { OverloadAnalyzer.isFixed(it) || Recovery.isEssential(it) }.forEach { keep ->
                assertEquals(keep, afterMvd.first { it.id == keep.id })
            }
        }
        assertTrue("property loop exercised recovery", proposals > 300)
    }
}
