package com.nevoit.cresto.data.learning

import java.time.LocalDate

/** What a single evening asks for. */
data class StudySession(
    val label: String,
    val task: String,
    val focus: String?,
    val output: String
)

data class UpcomingSession(val date: LocalDate, val session: StudySession)

sealed interface StudyDay {
    data class BeforeStart(val startDate: LocalDate, val first: UpcomingSession?) : StudyDay
    data class Finished(val lastWeek: StudyWeek) : StudyDay

    /** Inside the plan's span but not covered by any week row. */
    data class Unscheduled(val date: LocalDate) : StudyDay

    data class InWeek(
        val date: LocalDate,
        val week: StudyWeek,
        val session: StudySession?,
        val dailySlots: List<StudySlot>,
        /** The next session when there is none today. */
        val next: UpcomingSession?
    ) : StudyDay
}

object StudyPlanSchedule {

    private const val LOOKAHEAD_DAYS = 14L

    fun weekOf(plan: StudyPlan, date: LocalDate): StudyWeek? = plan.weeks.firstOrNull { date in it }

    fun dayOf(plan: StudyPlan, date: LocalDate): StudyDay {
        val first = plan.weeks.first()
        val last = plan.weeks.last()
        if (date.isBefore(first.start)) {
            return StudyDay.BeforeStart(first.start, nextSession(plan, first.start))
        }
        if (date.isAfter(last.end)) return StudyDay.Finished(last)
        val week = weekOf(plan, date) ?: return StudyDay.Unscheduled(date)

        val session = sessionOn(plan, date)
        return StudyDay.InWeek(
            date = date,
            week = week,
            session = session,
            dailySlots = plan.slots.filter { !it.isSession && date.dayOfWeek in it.days },
            next = if (session == null) nextSession(plan, date.plusDays(1)) else null
        )
    }

    /**
     * A "逐晚安排" row wins for its date. Otherwise the weekday's slot supplies the label and the
     * expected output, and an evening slot takes its task from the week's track for that weekday,
     * falling back to the track that is not tied to a weekday.
     */
    fun sessionOn(plan: StudyPlan, date: LocalDate): StudySession? {
        val week = weekOf(plan, date) ?: return null
        plan.nights.firstOrNull { it.date == date }?.let { night ->
            return StudySession(
                label = night.label,
                task = night.task,
                focus = null,
                output = night.output
            )
        }

        val slot = plan.slots.firstOrNull { it.isSession && date.dayOfWeek in it.days }
            ?: return null
        val trackText = if (slot.isEvening) {
            (week.tracks.firstOrNull { it.day == date.dayOfWeek }
                ?: week.tracks.firstOrNull { it.day == null })
                ?.text
                ?.takeIf { it.isNotBlank() }
        } else {
            null
        }
        return StudySession(
            label = slot.label,
            task = trackText ?: slot.content,
            focus = slot.content.takeIf { trackText != null && it.isNotBlank() },
            output = slot.output
        )
    }

    private fun nextSession(plan: StudyPlan, from: LocalDate): UpcomingSession? {
        return (0 until LOOKAHEAD_DAYS)
            .asSequence()
            .map { from.plusDays(it) }
            .firstNotNullOfOrNull { date -> sessionOn(plan, date)?.let { UpcomingSession(date, it) } }
    }
}
