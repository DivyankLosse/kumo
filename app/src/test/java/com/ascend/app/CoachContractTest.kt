package com.ascend.app

import com.ascend.app.data.coach.*
import com.ascend.app.data.local.*
import com.ascend.app.domain.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class CoachContractTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val weekStart = LocalDate.of(2027, 3, 8)
    private val day = weekStart.toEpochDay()
    private val profile = SeedData.profile

    private fun task(offset: Int, cat: Category, minutes: Int, done: Boolean, skipped: Boolean = false) = PlannedTaskEntity(
        date = day + offset, title = "t", category = cat.name, startMin = 600, endMin = 600 + minutes,
        completed = done, actualMinutes = if (done) minutes else null, skipped = skipped,
    )

    private fun focusAt(offset: Int, cat: Category, seconds: Int) = FocusSessionEntity(
        category = cat.name, plannedMinutes = seconds / 60, actualSeconds = seconds,
        startedAt = weekStart.plusDays(offset.toLong()).atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        completed = true, pointsEarned = 0,
    )

    private fun food(offset: Int, protein: Double?) = FoodEntryEntity(
        date = day + offset, meal = "Lunch", name = "x", calories = null, proteinG = protein, carbsG = null, fatG = null, loggedAt = 0,
    )

    private fun build(
        tasks: List<PlannedTaskEntity> = emptyList(),
        focus: List<FocusSessionEntity> = emptyList(),
        workouts: List<WorkoutSessionEntity> = emptyList(),
        foodList: List<FoodEntryEntity> = emptyList(),
        sleep: List<SleepLogEntity> = emptyList(),
        habitItems: List<ChecklistItemEntity> = emptyList(),
        habitLogs: List<ChecklistLogEntity> = emptyList(),
        p: ProfileEntity? = profile,
        today: LocalDate = weekStart.plusDays(6),
    ) = FactSheetBuilder.build(weekStart, today, tasks, focus, workouts, foodList, sleep, habitItems, habitLogs, p).associateBy { it.id }

    // Career 360 + 240 completed + 10 min focus = 610 min; one task outside the week is ignored.
    private fun fixture() = build(
        tasks = listOf(
            task(0, Category.CAREER, 360, true), task(1, Category.CAREER, 240, true),
            task(2, Category.FITNESS, 60, false), task(3, Category.PERSONAL, 30, false, skipped = true),
            task(-1, Category.CAREER, 500, true),
        ),
        focus = listOf(focusAt(2, Category.CAREER, 600), focusAt(3, Category.ACADEMICS, 1800)),
        workouts = listOf(
            WorkoutSessionEntity(date = day, routineId = null, title = "a", startedAt = 0, finishedAt = 1),
            WorkoutSessionEntity(date = day + 1, routineId = null, title = "b", startedAt = 0, finishedAt = null),
        ),
        foodList = listOf(food(0, 110.0), food(1, 60.0), food(1, 5.0), food(2, null)),
        sleep = listOf(
            SleepLogEntity(day, 22 * 60, 6 * 60), SleepLogEntity(day + 1, 60, 7 * 60), SleepLogEntity(day + 2, 22 * 60 + 30, 6 * 60 + 30),
        ),
        habitItems = listOf(ChecklistItemEntity(1, SeedData.HABITS, "a", order = 0), ChecklistItemEntity(2, SeedData.HABITS, "b", order = 1)),
        habitLogs = listOf(ChecklistLogEntity(1, day), ChecklistLogEntity(2, day), ChecklistLogEntity(1, day + 1)),
    )

    private val facts = fixture().values.toList()
    private fun item(text: String, vararg cites: String) = CoachItem("stat", text, cites.toList())

    @Test fun requestAndResponseRoundTrip() {
        val request = CoachRequest("kumo-coach@1", "en-IN", facts, FactSheetBuilder.constraints(profile))
        val encoded = json.encodeToString(CoachRequest.serializer(), request)
        assertEquals(request, json.decodeFromString(CoachRequest.serializer(), encoded))
        assertTrue(encoded.contains("\"sleepStart\":\"22:30\""))
        val response = CoachResponse(1, "m", "kumo-coach@1", listOf(CoachItem("stat", "Gym: 1 finished sessions.", listOf("f_gym_sessions"))), 420)
        assertEquals(response, json.decodeFromString(CoachResponse.serializer(), json.encodeToString(CoachResponse.serializer(), response)))
        val lenient = """{"schemaVersion":1,"model":"m","promptVersion":"p","items":[{"type":"t","text":"x"}],"latencyMs":5,"extra":1}"""
        assertTrue(json.decodeFromString(CoachResponse.serializer(), lenient).items.single().cites.isEmpty())
    }

    @Test fun endpointRulesKeepHttpsOnlyPlusDevHosts() {
        assertEquals("https://a.example/v1/coach/weekly_review", CoachClient.endpointFor("https://a.example/", CoachTask.WEEKLY_REVIEW))
        assertNotNull(CoachClient.endpointFor("http://10.0.2.2:8080", CoachTask.ROADMAP_PLAN))
        assertNotNull(CoachClient.endpointFor("http://localhost:8080", CoachTask.REFLECTION_PROMPTS))
        assertNull(CoachClient.endpointFor("http://example.com", CoachTask.WEEKLY_REVIEW))
    }

    @Test fun factSheetMatchesKnownNumbers() {
        val f = fixture()
        assertEquals("2027-W10", f.getValue(FactIds.CAREER_MIN).period)
        assertEquals(610.0, f.getValue(FactIds.CAREER_MIN).value, 0.0)
        assertEquals("min", f.getValue(FactIds.CAREER_MIN).unit)
        assertEquals(1680.0, f.getValue(FactIds.CAREER_TARGET_MIN).value, 0.0)
        assertEquals(36.0, f.getValue(FactIds.CAREER_PCT).value, 0.0)
        assertEquals(3.0, f.getValue(FactIds.BLOCKS_PLANNED).value, 0.0)
        assertEquals(2.0, f.getValue(FactIds.BLOCKS_DONE).value, 0.0)
        assertEquals(66.0, f.getValue(FactIds.COMPLETION_PCT).value, 0.0)
        assertEquals(40.0, f.getValue(FactIds.FOCUS_MIN_TOTAL).value, 0.0)
        assertEquals(30.0, f.getValue("f_focus_min_academics").value, 0.0)
        assertEquals(1.0, f.getValue(FactIds.GYM_SESSIONS).value, 0.0)
        assertEquals(1.0, f.getValue(FactIds.PROTEIN_DAYS).value, 0.0)
        assertEquals(2.0, f.getValue(FactIds.PROTEIN_LOGGED_DAYS).value, 0.0)
        assertEquals(2.0, f.getValue(FactIds.SLEEP_ON_TARGET).value, 0.0)
        assertEquals(3.0, f.getValue(FactIds.SLEEP_LOGGED).value, 0.0)
        assertEquals(21.0, f.getValue(FactIds.HABIT_RATE_PCT).value, 0.0)
    }

    @Test fun missingDataStaysMissingNotZero() {
        assertEquals(setOf(FactIds.CAREER_MIN, FactIds.GYM_SESSIONS), build(p = null).keys)
        val unlogged = build(foodList = listOf(food(0, null)))
        assertFalse(unlogged.containsKey(FactIds.PROTEIN_DAYS))
        assertFalse(unlogged.containsKey(FactIds.SLEEP_LOGGED))
        assertFalse(unlogged.containsKey(FactIds.HABIT_RATE_PCT))
        assertFalse(unlogged.containsKey(FactIds.BLOCKS_PLANNED))
    }

    @Test fun habitRateOnlyCountsElapsedDays() {
        val f = build(
            habitItems = listOf(ChecklistItemEntity(1, SeedData.HABITS, "a", order = 0)),
            habitLogs = listOf(ChecklistLogEntity(1, day)),
            today = weekStart.plusDays(1),
        )
        assertEquals(50.0, f.getValue(FactIds.HABIT_RATE_PCT).value, 0.0)
    }

    @Test fun validatorKeepsCorrectItemAndDropsFabricatedOrUncited() {
        val good = item("Career: 10.2 h of 28 h target (36%).", FactIds.CAREER_MIN, FactIds.CAREER_TARGET_MIN, FactIds.CAREER_PCT)
        val fabricated = item("Career: 14.5 h this week.", FactIds.CAREER_MIN)
        val uncited = item("You completed 2 blocks.")
        val wrongFact = item("You finished 2 gym sessions.", FactIds.GYM_SESSIONS)
        val noNumbers = CoachItem("prompt", "What went well?")
        val result = CitationValidator.validate(listOf(good, fabricated, uncited, wrongFact, noNumbers), facts)
        assertEquals(listOf(good, noNumbers), result.kept)
        assertEquals(3, result.dropped)
    }

    @Test fun validatorHandlesPercentAndDecimalFormats() {
        val ok = listOf(
            item("Completion was 66%.", FactIds.COMPLETION_PCT),
            item("Completion was 66 %.", FactIds.COMPLETION_PCT),
            item("About 10.17h of work.", FactIds.CAREER_MIN),
            item("610 minutes of career work.", FactIds.CAREER_MIN),
            item("Protein hit on 1 of 2 days.", FactIds.PROTEIN_DAYS, FactIds.PROTEIN_LOGGED_DAYS),
        )
        assertEquals(ok.size, CitationValidator.validate(ok, facts).kept.size)
        val bad = listOf(
            item("Completion was 80%.", FactIds.COMPLETION_PCT),
            item("About 11h of work.", FactIds.CAREER_MIN),
            item("Protein hit on 5 of 7 days.", FactIds.PROTEIN_DAYS, FactIds.PROTEIN_LOGGED_DAYS),
            item("Cites an unknown fact: 5 blocks.", "f_nope"),
        )
        assertEquals(0, CitationValidator.validate(bad, facts).kept.size)
    }

    @Test fun templateOutputAlwaysPassesValidator() {
        val sheets = listOf(facts, build(p = null).values.toList(), emptyList(), build(foodList = listOf(food(0, 120.0))).values.toList())
        for (sheet in sheets) for (task in CoachTask.entries) {
            val items = TemplateCoach.items(task, sheet)
            assertTrue("$task should have content", items.isNotEmpty())
            val result = CitationValidator.validate(items, sheet)
            assertEquals("$task dropped ${items - result.kept.toSet()}", 0, result.dropped)
        }
        assertTrue(TemplateCoach.items(CoachTask.WEEKLY_REVIEW, facts).any { it.text == "Career: 10.2 h of 28 h target (36%)." })
    }
}
