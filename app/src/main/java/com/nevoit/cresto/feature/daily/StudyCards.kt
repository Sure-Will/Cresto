package com.nevoit.cresto.feature.daily

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule
import com.nevoit.cresto.R
import com.nevoit.cresto.data.learning.StudyDay
import com.nevoit.cresto.data.learning.StudyPlan
import com.nevoit.cresto.data.learning.StudyPlanSchedule
import com.nevoit.cresto.data.learning.StudyPlanState
import com.nevoit.cresto.data.learning.StudyPlanSyncError
import com.nevoit.cresto.data.learning.StudySession
import com.nevoit.cresto.data.learning.StudySlot
import com.nevoit.cresto.data.learning.StudyStrand
import com.nevoit.cresto.data.learning.StudyWeek
import com.nevoit.cresto.data.routine.StudyLog
import com.nevoit.cresto.theme.AppColors
import com.nevoit.cresto.theme.AppSpecs
import com.nevoit.glasense.core.component.Icon
import com.nevoit.glasense.core.component.Text
import com.nevoit.glasense.theme.GlasenseTheme
import java.time.LocalDate

/** Done and planned evenings of a week, counting only days that have a session. */
internal data class WeekProgress(val done: Int, val total: Int) {
    val fraction: Float get() = if (total == 0) 0f else done.toFloat() / total
}

internal fun weekProgress(plan: StudyPlan, week: StudyWeek, logs: Map<LocalDate, StudyLog>): WeekProgress {
    val sessions = StudyPlanSchedule.sessionsIn(plan, week).filter { it.second != null }
    return WeekProgress(
        done = sessions.count { (date, _) -> logs[date]?.done == true },
        total = sessions.size
    )
}

@Composable
internal fun StudyTonightCard(
    state: StudyPlanState,
    today: LocalDate,
    logs: Map<LocalDate, StudyLog>,
    onOpenDay: (LocalDate) -> Unit,
    onOpenPlan: () -> Unit,
    onToggleDone: (LocalDate, StudySession, Boolean) -> Unit
) {
    val plan = state.plan
    val day = plan?.let { StudyPlanSchedule.dayOf(it, today) }
    val session = (day as? StudyDay.InWeek)?.session
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppSpecs.cardShape)
            .background(AppColors.cardBackground)
            .clickable { if (session != null) onOpenDay(today) else onOpenPlan() }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when {
            plan == null -> {
                val error = state.error?.takeUnless { it == StudyPlanSyncError.NotConfigured }
                Text(
                    text = error?.message(state.problem) ?: stringResource(R.string.study_not_configured),
                    style = GlasenseTheme.type.body,
                    color = AppColors.contentVariant
                )
            }

            day is StudyDay.InWeek -> InWeekContent(
                plan = plan,
                day = day,
                log = logs[today],
                logs = logs,
                onOpenDay = onOpenDay,
                onToggleDone = onToggleDone
            )

            day != null -> OutsideWeekContent(day)
        }
        if (plan != null) {
            state.statusMessage()?.let { status ->
                Text(
                    text = status,
                    style = GlasenseTheme.type.footnote,
                    color = AppColors.contentVariant
                )
            }
        }
    }
}

@Composable
private fun InWeekContent(
    plan: StudyPlan,
    day: StudyDay.InWeek,
    log: StudyLog?,
    logs: Map<LocalDate, StudyLog>,
    onOpenDay: (LocalDate) -> Unit,
    onToggleDone: (LocalDate, StudySession, Boolean) -> Unit
) {
    val session = day.session
    val progress = weekProgress(plan, day.week, logs)
    val accent = session?.strand?.color() ?: AppColors.primary

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (session != null) {
            StrandChip(session.strand)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = day.date.studyLabel(),
            style = GlasenseTheme.type.footnote,
            color = AppColors.contentVariant
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = stringResource(
                R.string.study_week_progress_format,
                day.week.number,
                progress.done,
                progress.total
            ),
            style = GlasenseTheme.type.footnote,
            color = AppColors.contentVariant
        )
    }
    ProgressBar(fraction = progress.fraction, color = accent)

    if (session != null) {
        Text(
            text = session.task,
            style = GlasenseTheme.type.title3Emphasized,
            color = AppColors.content
        )
        session.focus?.let { focus ->
            Text(
                text = focus,
                style = GlasenseTheme.type.subHeadline,
                color = AppColors.contentVariant
            )
        }
        if (session.output.isNotBlank()) OutputLine(session.output)
    } else {
        Text(
            text = stringResource(R.string.study_no_session_today),
            style = GlasenseTheme.type.title3Emphasized,
            color = AppColors.content
        )
        day.next?.let { next ->
            Text(
                text = stringResource(
                    R.string.study_next_session_format,
                    next.date.studyLabel(),
                    next.session.task
                ),
                style = GlasenseTheme.type.subHeadline,
                color = AppColors.contentVariant,
                modifier = Modifier
                    .clip(AppSpecs.cardShape)
                    .clickable { onOpenDay(next.date) }
            )
        }
    }
    day.dailySlots.forEach { slot -> DailySlotLine(slot) }

    if (session != null) {
        val done = log?.done == true
        Row(
            modifier = Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DoneButton(
                done = done,
                color = accent,
                onClick = { onToggleDone(day.date, session, !done) },
                modifier = Modifier.weight(1f)
            )
            ActionButton(
                icon = R.drawable.ic_square_and_pencil,
                text = stringResource(
                    if (log?.note.isNullOrBlank()) R.string.study_write_note else R.string.study_edit_note
                ),
                onClick = { onOpenDay(day.date) },
                modifier = Modifier.weight(1f)
            )
        }
        log?.note?.takeIf { it.isNotBlank() }?.let { note ->
            Text(
                text = note,
                style = GlasenseTheme.type.footnote,
                color = AppColors.contentVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun OutsideWeekContent(day: StudyDay) {
    when (day) {
        is StudyDay.BeforeStart -> {
            Text(
                text = stringResource(R.string.study_before_start_format, day.startDate.studyLabel()),
                style = GlasenseTheme.type.title3Emphasized,
                color = AppColors.content
            )
            day.first?.let { first ->
                Text(
                    text = stringResource(
                        R.string.study_next_session_format,
                        first.date.studyLabel(),
                        first.session.task
                    ),
                    style = GlasenseTheme.type.subHeadline,
                    color = AppColors.contentVariant
                )
            }
        }

        is StudyDay.Finished -> Text(
            text = stringResource(R.string.study_finished_format, day.lastWeek.number),
            style = GlasenseTheme.type.title3Emphasized,
            color = AppColors.content
        )

        is StudyDay.Unscheduled -> Text(
            text = stringResource(R.string.study_unscheduled),
            style = GlasenseTheme.type.title3Emphasized,
            color = AppColors.content
        )

        is StudyDay.InWeek -> Unit
    }
}

/** Monday to Sunday of this week; each day opens its record. */
@Composable
internal fun StudyWeekStrip(
    plan: StudyPlan,
    today: LocalDate,
    logs: Map<LocalDate, StudyLog>,
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppSpecs.cardShape)
            .background(AppColors.cardBackground)
            .padding(horizontal = 8.dp, vertical = 10.dp)
    ) {
        today.calendarWeek().forEach { date ->
            val session = StudyPlanSchedule.sessionOn(plan, date)
            val log = logs[date]
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(AppSpecs.cardShape)
                    .clickable { onOpenDay(date) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = date.dayOfWeek.narrowLabel(),
                    style = GlasenseTheme.type.footnote,
                    color = if (date == today) AppColors.primary else AppColors.contentVariant
                )
                DayDot(
                    date = date,
                    strandColor = session?.strand?.color(),
                    done = log?.done == true,
                    isToday = date == today
                )
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(
                            if (log?.note.isNullOrBlank()) Color.Transparent else AppColors.contentVariant
                        )
                )
            }
        }
    }
}

@Composable
private fun DayDot(date: LocalDate, strandColor: Color?, done: Boolean, isToday: Boolean) {
    val base = strandColor ?: AppColors.contentVariant
    val background by animateColorAsState(
        when {
            done -> base
            strandColor != null -> base.copy(alpha = 0.16f)
            else -> Color.Transparent
        },
        label = "dayDot"
    )
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(background)
            .then(
                if (isToday && !done) Modifier.border(2.dp, base, CircleShape) else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        if (done) {
            Icon(
                painter = painterResource(R.drawable.ic_checkmark),
                contentDescription = stringResource(R.string.study_done),
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        } else {
            Text(
                text = date.dayOfMonth.toString(),
                style = GlasenseTheme.type.footnoteEmphasized,
                color = if (strandColor != null) base else AppColors.contentVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
internal fun StrandChip(strand: StudyStrand) {
    val color = strand.color()
    Text(
        text = strand.name,
        style = GlasenseTheme.type.footnoteEmphasized,
        color = color,
        maxLines = 1,
        modifier = Modifier
            .clip(Capsule())
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    )
}

@Composable
internal fun ProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(fraction, label = "weekProgress")
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(Capsule())
            .background(AppColors.contentVariant.copy(alpha = 0.15f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .height(4.dp)
                .clip(Capsule())
                .background(color)
        )
    }
}

@Composable
internal fun OutputLine(output: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            painter = painterResource(R.drawable.ic_flag),
            contentDescription = null,
            tint = AppColors.contentVariant,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.study_output_format, output),
            style = GlasenseTheme.type.subHeadline,
            color = AppColors.contentVariant
        )
    }
}

@Composable
private fun DailySlotLine(slot: StudySlot) {
    val detail = listOf(slot.content, slot.output).filter { it.isNotBlank() }.joinToString("：")
    Text(
        text = stringResource(R.string.study_daily_slot_format, slot.label, detail),
        style = GlasenseTheme.type.footnote,
        color = AppColors.contentVariant
    )
}

@Composable
private fun DoneButton(
    done: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val background by animateColorAsState(
        if (done) color else color.copy(alpha = 0.14f),
        label = "doneBackground"
    )
    val content by animateColorAsState(if (done) Color.White else color, label = "doneContent")
    ButtonFrame(
        background = background,
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            onClick()
        },
        modifier = modifier
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_checkmark),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(if (done) R.string.study_done else R.string.study_mark_done),
            style = GlasenseTheme.type.subHeadlineEmphasized,
            color = content
        )
    }
}

@Composable
internal fun ActionButton(
    icon: Int,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ButtonFrame(
        background = AppColors.contentVariant.copy(alpha = 0.12f),
        onClick = onClick,
        modifier = modifier
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = AppColors.content,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = GlasenseTheme.type.subHeadlineEmphasized,
            color = AppColors.content
        )
    }
}

@Composable
private fun ButtonFrame(
    background: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(Capsule())
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) { content() }
}
