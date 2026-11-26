package com.ascend.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

object Dates {
    fun today(): LocalDate = LocalDate.now()
    fun todayEpoch(): Long = today().toEpochDay()
    fun nowMinutes(): Int = LocalTime.now().let { it.hour * 60 + it.minute }
    fun weekStart(date: LocalDate = today()): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    fun of(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)
    fun startOfDayMs(date: LocalDate): Long = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    fun endOfDayMs(date: LocalDate): Long = startOfDayMs(date.plusDays(1)) - 1
    fun epochDayOfMs(ms: Long): Long =
        java.time.Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

    /** Days left until 1 Jan 2028 (inclusive of today when inside 2027), or until 2027 starts if earlier. */
    fun daysRemainingIn2027(today: LocalDate = today()): Pair<Long, Boolean> {
        val start = LocalDate.of(2027, 1, 1)
        val end = LocalDate.of(2028, 1, 1)
        return when {
            today.isBefore(start) -> ChronoUnit.DAYS.between(today, start) to false
            today.isBefore(end) -> ChronoUnit.DAYS.between(today, end) to true
            else -> 0L to true
        }
    }

    fun greeting(hour: Int = LocalTime.now().hour): String = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Late night"
    }

    val dayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")
    val longFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM")
    val shortFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
}
