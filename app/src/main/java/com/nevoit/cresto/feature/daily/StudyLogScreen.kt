package com.nevoit.cresto.feature.daily

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.nevoit.cresto.R
import com.nevoit.cresto.data.learning.StudyPlanRepository
import com.nevoit.cresto.data.learning.StudyPlanSchedule
import com.nevoit.cresto.data.routine.HabitRepository
import com.nevoit.cresto.theme.AppButtonColors
import com.nevoit.cresto.theme.AppColors
import com.nevoit.cresto.theme.AppSpecs
import com.nevoit.cresto.ui.components.glasense.GlasenseButton
import com.nevoit.cresto.ui.components.glasense.GlasenseDynamicSmallTitle
import com.nevoit.cresto.ui.components.glasense.isScrolledPast
import com.nevoit.cresto.ui.components.packed.ConfigTextField
import com.nevoit.cresto.ui.components.packed.TopBarSpacer
import com.nevoit.glasense.component.ListStack
import com.nevoit.glasense.core.component.Icon
import com.nevoit.glasense.core.component.Text
import com.nevoit.glasense.core.component.VGap
import com.nevoit.glasense.theme.GlasenseTheme
import java.time.LocalDate

@Composable
fun StudyLogScreen(date: LocalDate) {
    val activity = LocalActivity.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navigationBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val planRepository = remember { StudyPlanRepository.get(context) }
    val habitRepository = remember { HabitRepository.get(context) }
    val planState by planRepository.state.collectAsStateWithLifecycle()
    val logs by habitRepository.studyLogs.collectAsStateWithLifecycle(initialValue = null)

    val plan = planState.plan
    val session = plan?.let { StudyPlanSchedule.sessionOn(it, date) }
    val week = plan?.let { StudyPlanSchedule.weekOf(it, date) }
    val log = logs?.firstOrNull { it.date == date }
    // A record keeps the task it was written for; the plan only fills in a new one.
    val task = log?.task?.takeIf { it.isNotBlank() } ?: session?.task.orEmpty()
    val strandName = log?.strand?.takeIf { it.isNotBlank() } ?: session?.strand?.name.orEmpty()

    var note by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(logs) {
        if (note == null && logs != null) note = log?.note.orEmpty()
    }

    val backgroundColor = AppColors.pageBackground
    val cardColor = AppColors.cardBackground
    val backdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
    val lazyListState = rememberLazyListState()
    val isSmallTitleVisible by lazyListState.isScrolledPast(statusBarHeight + 24.dp)
    val title = date.studyLabel()

    Box(modifier = Modifier.fillMaxSize()) {
        ListStack(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .imePadding(),
            cornerRadius = AppSpecs.cardCorner,
            contentPadding = PaddingValues(bottom = navigationBarHeight)
        ) {
            TopBarSpacer()
            item {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth()
                        .background(cardColor, AppSpecs.cardShape)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        session?.strand?.let { strand ->
                            StrandChip(strand)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text = title,
                            style = GlasenseTheme.type.subHeadlineEmphasized,
                            color = AppColors.content
                        )
                        Spacer(Modifier.weight(1f))
                        week?.let {
                            Text(
                                text = stringResource(R.string.study_week_format, it.number, it.dateRange()),
                                style = GlasenseTheme.type.footnote,
                                color = AppColors.contentVariant
                            )
                        }
                    }
                    if (task.isNotBlank()) {
                        Text(
                            text = task,
                            style = GlasenseTheme.type.title2Emphasized,
                            color = AppColors.content
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.study_no_session_on_day),
                            style = GlasenseTheme.type.body,
                            color = AppColors.contentVariant
                        )
                    }
                    session?.focus?.let { focus ->
                        Text(
                            text = focus,
                            style = GlasenseTheme.type.subHeadline,
                            color = AppColors.contentVariant
                        )
                    }
                    session?.output?.takeIf { it.isNotBlank() }?.let { OutputLine(it) }
                }
            }

            Section(topSpacing = 24.dp) {
                SwitchRow(
                    checked = log?.done == true,
                    onCheckedChange = { done ->
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        habitRepository.setStudyDone(date, done, task, strandName)
                    }
                ) {
                    Text(stringResource(R.string.study_done))
                }
            }

            item {
                VGap(24.dp)
                ConfigTextField(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    title = stringResource(R.string.study_note),
                    value = note.orEmpty(),
                    onValueChange = {
                        note = it
                        habitRepository.setStudyNote(date, it, task, strandName)
                    },
                    backgroundColor = cardColor,
                    singleLine = false,
                    minLines = 8,
                    decorateText = stringResource(R.string.study_note_hint)
                )
            }
            item { VGap() }
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
    }
}
