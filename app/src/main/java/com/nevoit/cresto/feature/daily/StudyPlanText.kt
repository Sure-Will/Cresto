package com.nevoit.cresto.feature.daily

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.nevoit.cresto.R
import com.nevoit.cresto.data.learning.StudyPlanProblem
import com.nevoit.cresto.data.learning.StudyPlanState
import com.nevoit.cresto.data.learning.StudyPlanSyncError
import com.nevoit.cresto.data.learning.StudyStrand
import com.nevoit.cresto.data.learning.StudyWeek
import com.nevoit.cresto.theme.harmonize
import com.nevoit.glasense.theme.tokens.Amber500
import com.nevoit.glasense.theme.tokens.Blue500
import com.nevoit.glasense.theme.tokens.Pink500
import com.nevoit.glasense.theme.tokens.Teal500
import com.nevoit.glasense.theme.tokens.Violet500
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val monthDayFormatter = DateTimeFormatter.ofPattern("M/d")

internal fun StudyWeek.dateRange(): String =
    "${start.format(monthDayFormatter)}–${end.format(monthDayFormatter)}"

internal fun LocalDate.studyLabel(): String =
    format(DateTimeFormatter.ofPattern("M/d E", Locale.getDefault()))

internal fun DayOfWeek.narrowLabel(): String = getDisplayName(TextStyle.NARROW, Locale.getDefault())

/** Monday to Sunday of the calendar week containing this date. */
internal fun LocalDate.calendarWeek(): List<LocalDate> {
    val monday = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return (0L until 7L).map { monday.plusDays(it) }
}

private val trackPalette = listOf(Blue500, Teal500, Violet500, Pink500)

/** Colour for the n-th track of the weeks table, in the same order as its columns. */
@Composable
internal fun trackColor(index: Int): Color =
    harmonize(trackPalette[index.coerceAtLeast(0) % trackPalette.size])

@Composable
internal fun StudyStrand.color(): Color = if (isReview) harmonize(Amber500) else trackColor(index)

internal fun formatSyncedAt(millis: Long): String =
    Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("M/d HH:mm", Locale.getDefault()))

@Composable
internal fun StudyPlanSyncError.message(problem: StudyPlanProblem?): String {
    return when (this) {
        StudyPlanSyncError.NotConfigured -> stringResource(R.string.study_error_not_configured)
        StudyPlanSyncError.InvalidSource -> stringResource(R.string.study_error_invalid_source)
        StudyPlanSyncError.Unauthorized -> stringResource(R.string.study_error_unauthorized)
        StudyPlanSyncError.Forbidden -> stringResource(R.string.study_error_forbidden)
        StudyPlanSyncError.RateLimited -> stringResource(R.string.study_error_rate_limited)
        StudyPlanSyncError.NotFound -> stringResource(R.string.study_error_not_found)
        StudyPlanSyncError.Network -> stringResource(R.string.study_error_network)
        StudyPlanSyncError.Server -> stringResource(R.string.study_error_server)
        StudyPlanSyncError.TooLarge -> stringResource(R.string.study_error_too_large)
        StudyPlanSyncError.Format -> stringResource(
            R.string.study_error_format,
            when (problem) {
                StudyPlanProblem.MissingStartDate, null -> {
                    stringResource(R.string.study_problem_missing_start_date)
                }

                StudyPlanProblem.MissingWeeks -> stringResource(R.string.study_problem_missing_weeks)
                StudyPlanProblem.MissingWeekColumns -> {
                    stringResource(R.string.study_problem_missing_week_columns)
                }

                StudyPlanProblem.BadWeekDates -> stringResource(R.string.study_problem_bad_week_dates)
                StudyPlanProblem.DuplicateWeekNumber -> {
                    stringResource(R.string.study_problem_duplicate_week_number)
                }
            }
        )
    }
}

/** One line explaining a failed sync, or null when the last sync succeeded. */
@Composable
internal fun StudyPlanState.statusMessage(): String? {
    if (isSyncing) return stringResource(R.string.study_syncing)
    val failure = error?.message(problem) ?: return null
    val shownAt = syncedAt?.takeIf { plan != null } ?: return failure
    return failure + " " + stringResource(R.string.study_showing_cached_format, formatSyncedAt(shownAt))
}
