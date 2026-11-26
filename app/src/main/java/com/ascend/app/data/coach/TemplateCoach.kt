package com.ascend.app.data.coach

import com.ascend.app.domain.Category
import com.ascend.app.domain.FactIds
import java.util.Locale

/** Offline fallback: fixed wording over the same facts, always citing what it quotes. */
object TemplateCoach {

    fun items(task: CoachTask, facts: List<Fact>): List<CoachItem> = when (task) {
        CoachTask.WEEKLY_REVIEW -> weeklyReview(facts)
        CoachTask.ROADMAP_PLAN -> roadmapPlan(facts)
        CoachTask.REFLECTION_PROMPTS -> reflectionPrompts(facts)
    }

    private fun Double.num(): String = if (this % 1.0 == 0.0) toLong().toString() else String.format(Locale.US, "%.1f", this)
    private fun Double.hours(): String = String.format(Locale.US, "%.1f", this / 60.0).removeSuffix(".0") + " h"

    private fun weeklyReview(facts: List<Fact>): List<CoachItem> {
        val f = facts.associateBy { it.id }
        fun v(id: String) = f[id]?.value
        return buildList {
            val career = v(FactIds.CAREER_MIN)
            val target = v(FactIds.CAREER_TARGET_MIN)
            val pct = v(FactIds.CAREER_PCT)
            if (career != null && target != null && pct != null) {
                add(CoachItem("stat", "Career: ${career.hours()} of ${target.hours()} target (${pct.num()}%).", listOf(FactIds.CAREER_MIN, FactIds.CAREER_TARGET_MIN, FactIds.CAREER_PCT)))
            } else if (career != null) {
                add(CoachItem("stat", "Career: ${career.hours()} logged.", listOf(FactIds.CAREER_MIN)))
            }
            val done = v(FactIds.BLOCKS_DONE)
            val planned = v(FactIds.BLOCKS_PLANNED)
            val completion = v(FactIds.COMPLETION_PCT)
            if (done != null && planned != null && completion != null) {
                add(CoachItem("stat", "Blocks: ${done.num()} of ${planned.num()} completed (${completion.num()}%).", listOf(FactIds.BLOCKS_DONE, FactIds.BLOCKS_PLANNED, FactIds.COMPLETION_PCT)))
            }
            v(FactIds.FOCUS_MIN_TOTAL)?.let { total ->
                val byCategory = Category.entries.mapNotNull { c -> v(FactIds.focusMinutes(c))?.let { c to it } }
                val detail = byCategory.joinToString(", ") { (c, m) -> "${c.label} ${m.hours()}" }
                add(CoachItem("stat", "Focus: ${total.hours()} in total ($detail).", listOf(FactIds.FOCUS_MIN_TOTAL) + byCategory.map { FactIds.focusMinutes(it.first) }))
            }
            v(FactIds.GYM_SESSIONS)?.let {
                add(CoachItem("stat", "Gym: ${it.num()} finished sessions.", listOf(FactIds.GYM_SESSIONS)))
            }
            val proteinDays = v(FactIds.PROTEIN_DAYS)
            val proteinLogged = v(FactIds.PROTEIN_LOGGED_DAYS)
            if (proteinDays != null && proteinLogged != null) {
                add(CoachItem("stat", "Protein: in range on ${proteinDays.num()} of ${proteinLogged.num()} logged days.", listOf(FactIds.PROTEIN_DAYS, FactIds.PROTEIN_LOGGED_DAYS)))
            } else {
                add(CoachItem("note", "Protein: no days with protein logged this week."))
            }
            val sleepOn = v(FactIds.SLEEP_ON_TARGET)
            val sleepLogged = v(FactIds.SLEEP_LOGGED)
            if (sleepOn != null && sleepLogged != null) {
                add(CoachItem("stat", "Sleep: ${sleepOn.num()} of ${sleepLogged.num()} logged nights on target.", listOf(FactIds.SLEEP_ON_TARGET, FactIds.SLEEP_LOGGED)))
            } else {
                add(CoachItem("note", "Sleep: no nights logged this week."))
            }
            v(FactIds.HABIT_RATE_PCT)?.let {
                add(CoachItem("stat", "Habits: ${it.num()}% completed so far this week.", listOf(FactIds.HABIT_RATE_PCT)))
            }
        }
    }

    private fun roadmapPlan(facts: List<Fact>): List<CoachItem> {
        val f = facts.associateBy { it.id }
        return buildList {
            val career = f[FactIds.CAREER_MIN]
            val target = f[FactIds.CAREER_TARGET_MIN]
            if (career != null && target != null) {
                add(CoachItem("stat", "Career so far: ${career.value.hours()} of ${target.value.hours()} weekly target.", listOf(FactIds.CAREER_MIN, FactIds.CAREER_TARGET_MIN)))
                if (career.value < target.value) {
                    add(CoachItem("advice", "You are under target. Book the next unfinished roadmap topic into a free career block."))
                } else {
                    add(CoachItem("advice", "Target reached. Use spare career time to practise a topic you have only learned."))
                }
            } else {
                add(CoachItem("advice", "Pick the next unfinished roadmap topic and book it into a free career block."))
            }
            add(CoachItem("advice", "Keep sessions to a sensible length and leave a buffer before the next block."))
        }
    }

    private fun reflectionPrompts(facts: List<Fact>): List<CoachItem> {
        val f = facts.associateBy { it.id }
        return buildList {
            val done = f[FactIds.BLOCKS_DONE]
            val planned = f[FactIds.BLOCKS_PLANNED]
            if (done != null && planned != null) {
                add(CoachItem("prompt", "You completed ${done.value.num()} of ${planned.value.num()} blocks. What helped the ones that went well?", listOf(FactIds.BLOCKS_DONE, FactIds.BLOCKS_PLANNED)))
            }
            add(CoachItem("prompt", "Which block felt least worth its time, and could it shrink or move?"))
            add(CoachItem("prompt", "What is one small change that would make next week easier?"))
        }
    }
}
