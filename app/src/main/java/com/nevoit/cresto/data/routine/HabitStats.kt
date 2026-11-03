package com.nevoit.cresto.data.routine

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

data class HabitSummary(
    val habit: Habit,
    val todayCount: Int,
    /** Check-ins from Monday to Sunday of the current week. */
    val weekCount: Int,
    /** Distinct days with a check-in in the current month. */
    val monthDays: Int,
    val totalCount: Int,
    val totalDays: Int
)

object HabitStats {

    fun summarize(entry: HabitEntry, today: LocalDate): HabitSummary {
        val days = entry.checkIns.map { it.at.toLocalDate() }
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekEnd = weekStart.plusDays(6)
        val month = YearMonth.from(today)
        return HabitSummary(
            habit = entry.habit,
            todayCount = days.count { it == today },
            weekCount = days.count { !it.isBefore(weekStart) && !it.isAfter(weekEnd) },
            monthDays = days.filter { YearMonth.from(it) == month }.toSet().size,
            totalCount = days.size,
            totalDays = days.toSet().size
        )
    }

    fun datesWithCheckIns(entry: HabitEntry): Set<LocalDate> =
        entry.checkIns.map { it.at.toLocalDate() }.toSet()

    fun checkInsOn(entry: HabitEntry, date: LocalDate): List<HabitCheckIn> =
        entry.checkIns.filter { it.at.toLocalDate() == date }.sortedBy { it.at }

    /**
     * A backfilled check-in keeps the current time of day on the chosen date, as Daily-Track does;
     * today's check-ins use the exact moment.
     */
    fun checkInTimeFor(date: LocalDate, now: LocalDateTime): LocalDateTime =
        if (date == now.toLocalDate()) now else date.atTime(now.toLocalTime())
}
