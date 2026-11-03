package com.nevoit.cresto.feature.daily

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.nevoit.cresto.R
import com.nevoit.cresto.data.routine.HabitRepository
import com.nevoit.cresto.data.routine.HabitStats
import com.nevoit.cresto.feature.calendar.MonthlyPagerCalendar
import com.nevoit.cresto.feature.calendar.WeekDaysIndicator
import com.nevoit.cresto.theme.AppButtonColors
import com.nevoit.cresto.theme.AppColors
import com.nevoit.cresto.theme.AppSpecs
import com.nevoit.cresto.theme.harmonize
import com.nevoit.cresto.ui.components.glasense.DialogItemData
import com.nevoit.cresto.ui.components.glasense.DialogState
import com.nevoit.cresto.ui.components.glasense.GlasenseButton
import com.nevoit.cresto.ui.components.glasense.GlasenseDialog
import com.nevoit.cresto.ui.components.glasense.GlasenseDynamicSmallTitle
import com.nevoit.cresto.ui.components.glasense.isScrolledPast
import com.nevoit.cresto.ui.components.packed.ConfigInfoHeader
import com.nevoit.cresto.ui.components.packed.ConfigTextField
import com.nevoit.cresto.ui.components.packed.TopBarSpacer
import com.nevoit.glasense.component.ListStack
import com.nevoit.glasense.core.component.Icon
import com.nevoit.glasense.core.component.Text
import com.nevoit.glasense.core.component.VGap
import com.nevoit.glasense.theme.GlasenseTheme
import com.nevoit.glasense.theme.tokens.Emerald400
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val CheckInTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun HabitDetailScreen(habitId: String) {
    val activity = LocalActivity.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navigationBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val repository = remember { HabitRepository.get(context) }
    val entries by repository.entries.collectAsStateWithLifecycle(initialValue = null)
    val entry = entries?.firstOrNull { it.habit.id == habitId }
    var today by remember { mutableStateOf(LocalDate.now()) }
    val calendarAnchor = remember { LocalDate.now() }
    var selectedDate by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var name by rememberSaveable { mutableStateOf<String?>(null) }

    LifecycleResumeEffect(Unit) {
        today = LocalDate.now()
        onPauseOrDispose { }
    }
    // Leave once the habit is gone, whether deleted here or never there.
    LaunchedEffect(entries, entry) {
        if (entries != null && entry == null) activity?.finish()
    }
    LaunchedEffect(entry?.habit?.title) {
        if (name == null) name = entry?.habit?.title
    }

    val backgroundColor = AppColors.pageBackground
    val cardColor = AppColors.cardBackground
    val backdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
    val lazyListState = rememberLazyListState()
    val isSmallTitleVisible by lazyListState.isScrolledPast(statusBarHeight + 24.dp)
    var dialogState by remember { mutableStateOf(DialogState()) }
    val dismissDialog = { dialogState = dialogState.copy(isVisible = false) }

    val title = entry?.habit?.title.orEmpty()
    val deleteTitle = stringResource(
        R.string.confirm_action_title,
        stringResource(R.string.habit_delete)
    )
    val deleteMessage = stringResource(
        R.string.habit_delete_message,
        timesText(entry?.checkIns?.size ?: 0)
    )
    val cancelText = stringResource(R.string.cancel)
    val deleteText = stringResource(R.string.delete)

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
        ) {
            if (entry != null) {
                val summary = HabitStats.summarize(entry, today)
                val checkIns = HabitStats.checkInsOn(entry, selectedDate)
                val datesWithCheckIns = HabitStats.datesWithCheckIns(entry)

                ListStack(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding(),
                    cornerRadius = AppSpecs.cardCorner,
                    contentPadding = PaddingValues(bottom = navigationBarHeight)
                ) {
                    TopBarSpacer()
                    item {
                        ConfigInfoHeader(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            color = harmonize(Emerald400),
                            backgroundColor = cardColor,
                            icon = painterResource(R.drawable.ic_clock_cycle),
                            title = title,
                            info = stringResource(
                                R.string.habit_total_format,
                                timesText(summary.totalCount),
                                daysText(summary.totalDays)
                            )
                        )
                    }

                    Section(topSpacing = 24.dp) {
                        Row(trailing = { Text(timesText(summary.todayCount)) }) {
                            Text(stringResource(R.string.habit_today))
                        }
                        Row(trailing = { Text(timesText(summary.weekCount)) }) {
                            Text(stringResource(R.string.habit_this_week))
                        }
                        Row(trailing = { Text(daysText(summary.monthDays)) }) {
                            Text(stringResource(R.string.habit_this_month))
                        }
                    }

                    item {
                        VGap(24.dp)
                        WeekDaysIndicator()
                        Spacer(Modifier.height(4.dp))
                        MonthlyPagerCalendar(
                            selectedDate = selectedDate,
                            datesWithTodo = datesWithCheckIns,
                            onDateSelected = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedDate = it
                            },
                            onMonthChanged = {},
                            collapseFractionProvider = { 0f },
                            initialDate = calendarAnchor
                        )
                    }

                    Section(
                        header = { selectedDate.studyLabel() },
                        footer = {
                            if (checkIns.isEmpty()) {
                                stringResource(R.string.habit_no_check_ins_on_day)
                            } else {
                                null
                            }
                        }
                    ) {
                        checkIns.forEach { checkIn ->
                            Row(
                                key = "check-in-${checkIn.id}",
                                trailing = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_trash),
                                        contentDescription = stringResource(R.string.habit_delete_check_in),
                                        tint = AppColors.error,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                                repository.deleteCheckIn(checkIn.id)
                                            }
                                            .padding(4.dp)
                                    )
                                }
                            ) {
                                Text(checkIn.at.format(CheckInTimeFormatter))
                            }
                        }
                        val canCheckIn = !selectedDate.isAfter(today)
                        Row(
                            key = "check-in-add",
                            enabled = canCheckIn,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                repository.checkIn(habitId, selectedDate)
                            }
                        ) {
                            Text(
                                text = stringResource(
                                    if (selectedDate == today) R.string.habit_check_in else R.string.habit_backfill
                                ),
                                color = if (canCheckIn) AppColors.primary else AppColors.contentVariant
                            )
                        }
                    }

                    item {
                        VGap(24.dp)
                        ConfigTextField(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            title = stringResource(R.string.habit_name),
                            value = name.orEmpty(),
                            onValueChange = {
                                name = it
                                repository.rename(habitId, it)
                            },
                            backgroundColor = cardColor,
                            singleLine = true,
                            decorateText = stringResource(R.string.habit_name_hint),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                        )
                    }

                    Section(topSpacing = 24.dp) {
                        Row(
                            destructive = true,
                            onClick = {
                                dialogState = DialogState(
                                    isVisible = true,
                                    title = deleteTitle,
                                    message = deleteMessage,
                                    items = listOf(
                                        DialogItemData(
                                            cancelText,
                                            onClick = { dismissDialog() },
                                            isPrimary = false
                                        ),
                                        DialogItemData(
                                            deleteText,
                                            onClick = {
                                                repository.delete(habitId)
                                                dismissDialog()
                                                activity?.finish()
                                            },
                                            isPrimary = true,
                                            isDestructive = true
                                        )
                                    )
                                )
                            }
                        ) {
                            Text(
                                text = stringResource(R.string.habit_delete),
                                style = GlasenseTheme.type.body
                            )
                        }
                    }
                    item { VGap() }
                }
            }
        }
        GlasenseDynamicSmallTitle(
            modifier = Modifier.align(Alignment.TopCenter),
            title = title,
            statusBarHeight = statusBarHeight,
            isVisible = isSmallTitleVisible,
            backdrop = backdrop,
            surfaceColor = backgroundColor
        ) {}
        GlasenseButton(
            enabled = true,
            shape = CircleShape,
            onClick = { activity?.finish() },
            modifier = Modifier
                .padding(top = statusBarHeight, start = 12.dp)
                .size(48.dp)
                .align(Alignment.TopStart),
            colors = AppButtonColors.action()
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_forward_nav),
                contentDescription = stringResource(R.string.back),
                modifier = Modifier.width(32.dp)
            )
        }
        GlasenseDialog(
            dialogState = dialogState,
            backdrop = backdrop,
            onDismiss = { dismissDialog() }
        )
    }
}
