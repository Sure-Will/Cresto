package com.nevoit.cresto.feature.daily

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.nevoit.cresto.R
import com.nevoit.cresto.data.routine.HabitSummary
import com.nevoit.cresto.theme.AppColors
import com.nevoit.cresto.theme.AppSpecs
import com.nevoit.glasense.core.component.HGap
import com.nevoit.glasense.core.component.Icon
import com.nevoit.glasense.core.component.Text
import com.nevoit.glasense.theme.GlasenseTheme

@Composable
internal fun timesText(count: Int): String =
    pluralStringResource(R.plurals.habit_times, count, count)

@Composable
internal fun daysText(count: Int): String =
    pluralStringResource(R.plurals.habit_days, count, count)

@Composable
internal fun HabitCard(
    summary: HabitSummary,
    isDragging: Boolean,
    onCheckIn: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val done = summary.todayCount > 0
    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                val scale = if (isDragging) 1.02f else 1f
                scaleX = scale
                scaleY = scale
            }
            .clip(AppSpecs.cardShape)
            .background(AppColors.cardBackground)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CheckInButton(done = done, onClick = onCheckIn)
        HGap(12.dp)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = summary.habit.title,
                style = GlasenseTheme.type.headline,
                color = AppColors.content,
                maxLines = 2
            )
            Text(
                text = if (done) {
                    stringResource(R.string.habit_today_format, timesText(summary.todayCount))
                } else {
                    stringResource(R.string.habit_today_none)
                },
                style = GlasenseTheme.type.subHeadline,
                color = if (done) AppColors.primary else AppColors.contentVariant
            )
            Text(
                text = stringResource(
                    R.string.habit_week_month_format,
                    timesText(summary.weekCount),
                    daysText(summary.monthDays)
                ),
                style = GlasenseTheme.type.footnote,
                color = AppColors.contentVariant
            )
        }
    }
}

@Composable
private fun CheckInButton(done: Boolean, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val container by animateColorAsState(
        if (done) AppColors.primary else AppColors.primary.copy(alpha = 0.12f),
        label = "checkInContainer"
    )
    val content by animateColorAsState(
        if (done) AppColors.onPrimary else AppColors.primary,
        label = "checkInContent"
    )
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(container)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_checkmark),
            contentDescription = stringResource(R.string.habit_check_in),
            modifier = Modifier.size(24.dp),
            tint = content
        )
    }
}

@Composable
internal fun AddHabitCard(
    name: String,
    onNameChange: (String) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val canAdd = name.isNotBlank()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppSpecs.cardShape)
            .background(AppColors.cardBackground)
            .padding(start = 16.dp, end = 4.dp)
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = name,
            onValueChange = onNameChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            textStyle = GlasenseTheme.type.body.copy(color = AppColors.content),
            cursorBrush = SolidColor(AppColors.primary),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (canAdd) onAdd() }),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (name.isEmpty()) {
                        Text(
                            text = stringResource(R.string.habit_name_hint),
                            style = GlasenseTheme.type.body,
                            color = AppColors.contentVariant
                        )
                    }
                    innerTextField()
                }
            }
        )
        Text(
            text = stringResource(R.string.habit_add),
            style = GlasenseTheme.type.bodyEmphasized,
            color = if (canAdd) AppColors.primary else AppColors.contentVariant,
            modifier = Modifier
                .clip(AppSpecs.cardShape)
                .clickable(enabled = canAdd, onClick = onAdd)
                .padding(horizontal = 12.dp, vertical = 12.dp)
        )
    }
}
