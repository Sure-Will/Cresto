package com.nevoit.cresto.feature.daily

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.Capsule
import com.nevoit.cresto.R
import com.nevoit.cresto.data.learning.StudyDay
import com.nevoit.cresto.data.learning.StudyPlanRepository
import com.nevoit.cresto.data.learning.StudyPlanSchedule
import com.nevoit.cresto.data.learning.StudyPlanState
import com.nevoit.cresto.data.learning.StudyPlanSyncError
import com.nevoit.cresto.data.learning.StudySession
import com.nevoit.cresto.data.learning.StudySlot
import com.nevoit.cresto.data.routine.HabitRepository
import com.nevoit.cresto.data.routine.HabitStats
import com.nevoit.cresto.theme.AppButtonColors
import com.nevoit.cresto.theme.AppColors
import com.nevoit.cresto.theme.AppSpecs
import com.nevoit.cresto.ui.components.glasense.GlasenseButtonToolBar
import com.nevoit.cresto.ui.components.glasense.GlasenseDynamicSmallTitle
import com.nevoit.cresto.ui.components.glasense.MainPageActionIcon
import com.nevoit.cresto.ui.components.glasense.MainPageActionIconOptics
import com.nevoit.cresto.ui.components.glasense.MainPageActionTouchTargetSize
import com.nevoit.cresto.ui.components.glasense.MainPageActionVisualInset
import com.nevoit.cresto.ui.components.glasense.MainPageTitle
import com.nevoit.cresto.ui.components.glasense.mainPageTopBarPlacement
import com.nevoit.cresto.ui.components.glasense.mainPageTopContentSpacer
import com.nevoit.cresto.ui.components.packed.CardWithTitle
import com.nevoit.cresto.ui.components.packed.PageContent
import com.nevoit.glasense.component.paddingItem
import com.nevoit.glasense.core.component.Text
import com.nevoit.glasense.theme.GlasenseTheme
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.time.LocalDate

private const val HABIT_KEY_PREFIX = "habit/"

@Composable
fun BoxScope.DailyScreen() {
    val context = LocalContext.current
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val lazyListState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current

    val repository = remember { StudyPlanRepository.get(context) }
    val planState by repository.state.collectAsStateWithLifecycle()
    var today by remember { mutableStateOf(LocalDate.now()) }

    val habitRepository = remember { HabitRepository.get(context) }
    val entries by habitRepository.entries.collectAsStateWithLifecycle(initialValue = null)
    var isAdding by rememberSaveable { mutableStateOf(false) }
    var newHabitName by rememberSaveable { mutableStateOf("") }

    // The list follows the database except while a card is being dragged.
    var orderedIds by remember { mutableStateOf(emptyList<String>()) }
    var isDragging by remember { mutableStateOf(false) }
    var pendingOrder by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(entries) {
        if (!isDragging) orderedIds = entries.orEmpty().map { it.habit.id }
    }
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromId = (from.key as? String)?.removePrefix(HABIT_KEY_PREFIX)
        val toId = (to.key as? String)?.removePrefix(HABIT_KEY_PREFIX)
        val fromIndex = orderedIds.indexOf(fromId)
        val toIndex = orderedIds.indexOf(toId)
        if (fromIndex == -1 || toIndex == -1 || fromIndex == toIndex) {
            return@rememberReorderableLazyListState
        }
        orderedIds = orderedIds.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        pendingOrder = orderedIds
        haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    LifecycleResumeEffect(repository) {
        today = LocalDate.now()
        repository.refreshIfStale()
        onPauseOrDispose { }
    }

    fun addHabit() {
        habitRepository.addHabit(newHabitName)
        newHabitName = ""
        isAdding = false
        focusManager.clearFocus()
    }

    val backgroundColor = AppColors.pageBackground
    val backdrop = rememberLayerBackdrop {
        drawRect(
            color = backgroundColor,
            size = Size(this.size.width * 3, this.size.height * 3),
            topLeft = Offset(-this.size.width, -this.size.height)
        )
        drawContent()
    }

    PageContent(
        state = lazyListState,
        modifier = Modifier.layerBackdrop(backdrop),
        tabPadding = true
    ) {
        item(key = "top") {
            Spacer(modifier = Modifier.mainPageTopContentSpacer())
        }
        item(key = "tonight") {
            TonightCard(
                state = planState,
                today = today,
                onClick = { context.startActivity(StudyPlanActivity.createIntent(context)) }
            )
        }
        item(key = "habits-title") {
            Text(
                text = stringResource(R.string.habits),
                style = GlasenseTheme.type.title3Emphasized,
                color = AppColors.content,
                modifier = Modifier.padding(start = 12.dp, top = 24.dp, bottom = 12.dp)
            )
        }
        if (isAdding) {
            item(key = "habit-add") {
                AddHabitCard(
                    name = newHabitName,
                    onNameChange = { newHabitName = it },
                    onAdd = ::addHabit,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }
        val loaded = entries
        if (loaded != null) {
            if (loaded.isEmpty() && !isAdding) {
                item(key = "habit-empty") {
                    Text(
                        text = stringResource(R.string.habit_empty),
                        style = GlasenseTheme.type.subHeadline,
                        color = AppColors.contentVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AppColors.cardBackground, AppSpecs.cardShape)
                            .padding(16.dp)
                    )
                }
            }
            val byId = loaded.associateBy { it.habit.id }
            orderedIds.mapNotNull(byId::get).forEach { entry ->
                val key = HABIT_KEY_PREFIX + entry.habit.id
                item(key = key) {
                    ReorderableItem(state = reorderState, key = key) { dragging ->
                        HabitCard(
                            summary = HabitStats.summarize(entry, today),
                            isDragging = dragging,
                            onCheckIn = { habitRepository.checkIn(entry.habit.id) },
                            onClick = {
                                context.startActivity(
                                    HabitDetailActivity.createIntent(context, entry.habit.id)
                                )
                            },
                            modifier = Modifier
                                .padding(bottom = 8.dp)
                                .longPressDraggableHandle(
                                    enabled = orderedIds.size > 1,
                                    onDragStarted = {
                                        isDragging = true
                                        haptic.performHapticFeedback(
                                            HapticFeedbackType.GestureThresholdActivate
                                        )
                                    },
                                    onDragStopped = {
                                        isDragging = false
                                        pendingOrder?.let(habitRepository::reorder)
                                        pendingOrder = null
                                    }
                                )
                        )
                    }
                }
            }
        }
        paddingItem(lazyListState)
    }
    GlasenseDynamicSmallTitle(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .mainPageTopBarPlacement(),
        title = null,
        statusBarHeight = statusBarHeight,
        isVisible = true,
        backdrop = backdrop
    ) {
        MainPageTitle(
            title = stringResource(R.string.daily),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(end = 64.dp)
        )
        GlasenseButtonToolBar(
            enabled = true,
            interactionSource = remember { MutableInteractionSource() },
            shape = Capsule(),
            onClick = {
                isAdding = !isAdding
                if (!isAdding) focusManager.clearFocus()
            },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(MainPageActionTouchTargetSize),
            colors = AppButtonColors.action(),
            visualInset = MainPageActionVisualInset
        ) {
            MainPageActionIcon(
                painter = painterResource(id = R.drawable.ic_add_large),
                contentDescription = stringResource(R.string.habit_add),
                optics = MainPageActionIconOptics.Add,
                tint = AppColors.primary
            )
        }
    }
}

@Composable
private fun TonightCard(
    state: StudyPlanState,
    today: LocalDate,
    onClick: () -> Unit
) {
    CardWithTitle(
        icon = painterResource(R.drawable.ic_mountain),
        title = stringResource(R.string.study_tonight),
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppSpecs.cardShape)
            .clickable(onClick = onClick)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val plan = state.plan
            if (plan == null) {
                val error = state.error?.takeUnless { it == StudyPlanSyncError.NotConfigured }
                Text(
                    text = error?.message(state.problem)
                        ?: stringResource(R.string.study_not_configured),
                    style = GlasenseTheme.type.body,
                    color = AppColors.contentVariant
                )
            } else {
                StudyDayContent(StudyPlanSchedule.dayOf(plan, today))
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
}

@Composable
private fun StudyDayContent(day: StudyDay) {
    when (day) {
        is StudyDay.InWeek -> {
            Text(
                text = stringResource(
                    R.string.study_week_format,
                    day.week.number,
                    day.week.dateRange()
                ),
                style = GlasenseTheme.type.footnote,
                color = AppColors.contentVariant
            )
            val session = day.session
            if (session != null) {
                StudySessionBlock(session)
            } else {
                Text(
                    text = stringResource(R.string.study_no_session_today),
                    style = GlasenseTheme.type.headline,
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
                        color = AppColors.contentVariant
                    )
                }
            }
            day.dailySlots.forEach { slot -> DailySlotLine(slot) }
            if (day.week.acceptance.isNotBlank()) {
                Text(
                    text = stringResource(R.string.study_acceptance_format, day.week.acceptance),
                    style = GlasenseTheme.type.footnote,
                    color = AppColors.contentVariant
                )
            }
        }

        is StudyDay.BeforeStart -> {
            Text(
                text = stringResource(R.string.study_before_start_format, day.startDate.studyLabel()),
                style = GlasenseTheme.type.headline,
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
            style = GlasenseTheme.type.headline,
            color = AppColors.content
        )

        is StudyDay.Unscheduled -> Text(
            text = stringResource(R.string.study_unscheduled),
            style = GlasenseTheme.type.headline,
            color = AppColors.content
        )
    }
}

@Composable
internal fun StudySessionBlock(session: StudySession) {
    Text(
        text = session.label,
        style = GlasenseTheme.type.subHeadlineEmphasized,
        color = AppColors.primary
    )
    Text(
        text = session.task,
        style = GlasenseTheme.type.headline,
        color = AppColors.content
    )
    session.focus?.let { focus ->
        Text(
            text = focus,
            style = GlasenseTheme.type.subHeadline,
            color = AppColors.contentVariant
        )
    }
    if (session.output.isNotBlank()) {
        Text(
            text = stringResource(R.string.study_output_format, session.output),
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
