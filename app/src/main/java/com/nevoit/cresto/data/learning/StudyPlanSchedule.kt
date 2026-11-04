package com.nevoit.cresto.data.learning

import java.time.LocalDate

/** Which line of the plan a session belongs to. [index] orders the lines and picks a colour. */
data class StudyStrand(val name: String, val index: Int, val isReview: Boolean)

/** What a single evening asks for. */
data class StudySession(
    val label: String,
    val task: String,
    val focus: String?,
    val output: String,
    val strand: StudyStrand
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
        val slot = plan.slots.firstOrNull { it.isSession && date.dayOfWeek in it.days }
        val track = if (slot == null || slot.isEvening) {
            week.tracks.firstOrNull { it.day == date.dayOfWeek }
                ?: week.tracks.firstOrNull { it.day == null }
        } else {
            null
        }
        val strand = strandOf(week, slot, track)

        plan.nights.firstOrNull { it.date == date }?.let { night ->
            return StudySession(
                label = night.label,
                task = night.task,
                focus = null,
                output = night.output,
                strand = strand
            )
        }

        slot ?: return null
        val trackText = track?.text?.takeIf { it.isNotBlank() }
        return StudySession(
            label = slot.label,
            task = trackText ?: slot.content,
            focus = slot.content.takeIf { trackText != null && it.isNotBlank() },
            output = slot.output,
            strand = strand
        )
    }

    /** Every day of [week] in order, with the session it asks for, or null on a free day. */
    fun sessionsIn(plan: StudyPlan, week: StudyWeek): List<Pair<LocalDate, StudySession?>> =
        generateSequence(week.start) { it.plusDays(1) }
            .takeWhile { !it.isAfter(week.end) }
            .map { it to sessionOn(plan, it) }
            .toList()

    private fun strandOf(week: StudyWeek, slot: StudySlot?, track: StudyTrack?): StudyStrand =
        when {
            slot != null && !slot.isEvening -> StudyStrand(
                name = slot.content.ifBlank { slot.label },
                index = week.tracks.size,
                isReview = true
            )

            track != null -> StudyStrand(
                name = track.name.substringBefore('（').substringBefore('(').trim(),
                index = week.tracks.indexOf(track),
                isReview = false
            )

            else -> StudyStrand(name = slot?.label.orEmpty(), index = 0, isReview = false)
        }

    private fun nextSession(plan: StudyPlan, from: LocalDate): UpcomingSession? {
        return (0 until LOOKAHEAD_DAYS)
            .asSequence()
            .map { from.plusDays(it) }
            .firstNotNullOfOrNull { date -> sessionOn(plan, date)?.let { UpcomingSession(date, it) } }
    }
}
