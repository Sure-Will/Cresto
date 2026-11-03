package com.nevoit.cresto.feature.daily

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nevoit.cresto.R
import com.nevoit.cresto.data.learning.StudyPlanProblem
import com.nevoit.cresto.data.learning.StudyPlanState
import com.nevoit.cresto.data.learning.StudyPlanSyncError
import com.nevoit.cresto.data.learning.StudyWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val monthDayFormatter = DateTimeFormatter.ofPattern("M/d")

internal fun StudyWeek.dateRange(): String =
    "${start.format(monthDayFormatter)}–${end.format(monthDayFormatter)}"

internal fun LocalDate.studyLabel(): String =
    format(DateTimeFormatter.ofPattern("M/d E", Locale.getDefault()))

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
