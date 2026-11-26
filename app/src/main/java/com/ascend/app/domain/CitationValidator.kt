package com.ascend.app.domain

import com.ascend.app.data.coach.CoachItem
import com.ascend.app.data.coach.Fact
import kotlin.math.abs

data class ValidationResult(val kept: List<CoachItem>, val dropped: Int)

/**
 * Enforces grounding: every number in an item's text must match (within display rounding) the value
 * of a fact that the item cites. Items with an uncited or mismatched number are dropped.
 */
object CitationValidator {
    // Skips digits glued to letters or dots (ids like "W10", "v1.2") so only quoted figures count.
    private val number = Regex("""(?<![A-Za-z0-9.])(\d{1,3}(?:,\d{3})+|\d+)(\.\d+)?(\s?%)?""")

    private const val DECIMAL_TOLERANCE = 0.06
    private const val PERCENT_TOLERANCE = 0.5 + 1e-9

    fun validate(items: List<CoachItem>, facts: List<Fact>): ValidationResult {
        val byId = facts.associateBy { it.id }
        val kept = items.filter { isGrounded(it, byId) }
        return ValidationResult(kept, items.size - kept.size)
    }

    fun isGrounded(item: CoachItem, factsById: Map<String, Fact>): Boolean {
        val cited = item.cites.mapNotNull { factsById[it] }
        return numbersIn(item.text).all { (value, isPercent) ->
            val tolerance = if (isPercent) PERCENT_TOLERANCE else DECIMAL_TOLERANCE
            cited.any { fact -> acceptedValues(fact).any { abs(it - value) <= tolerance } }
        }
    }

    /** Minutes may be quoted as hours, so both forms of a minute fact are valid. */
    private fun acceptedValues(fact: Fact): List<Double> =
        if (fact.unit == "min") listOf(fact.value, fact.value / 60.0) else listOf(fact.value)

    private fun numbersIn(text: String): List<Pair<Double, Boolean>> =
        number.findAll(text).map { m ->
            val whole = m.groupValues[1].replace(",", "")
            (whole + m.groupValues[2]).toDouble() to m.groupValues[3].isNotEmpty()
        }.toList()
}
