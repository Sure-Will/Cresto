package com.nevoit.cresto.feature.daily

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush.Companion.sweepGradient
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.nevoit.cresto.R
import com.nevoit.cresto.data.learning.StudyPlan
import com.nevoit.cresto.data.learning.StudyPlanRepository
import com.nevoit.cresto.data.learning.StudyPlanSchedule
import com.nevoit.cresto.data.learning.StudyWeek
import com.nevoit.cresto.theme.AppButtonColors
import com.nevoit.cresto.theme.AppColors
import com.nevoit.cresto.theme.AppSpecs
import com.nevoit.cresto.theme.harmonize
import com.nevoit.cresto.ui.components.glasense.GlasenseButton
import com.nevoit.cresto.ui.components.glasense.GlasenseDynamicSmallTitle
import com.nevoit.cresto.ui.components.glasense.isScrolledPast
import com.nevoit.cresto.ui.components.packed.CardWithTitle
import com.nevoit.cresto.ui.components.packed.ConfigInfoHeader
import com.nevoit.cresto.ui.components.packed.ConfigTextField
import com.nevoit.cresto.ui.components.packed.TopBarSpacer
import com.nevoit.glasense.component.ListScope
import com.nevoit.glasense.component.ListStack
import com.nevoit.glasense.core.component.Icon
import com.nevoit.glasense.core.component.Text
import com.nevoit.glasense.core.component.VGap
import com.nevoit.glasense.theme.GlasenseTheme
import com.nevoit.glasense.theme.tokens.Blue500
import com.nevoit.glasense.theme.tokens.Teal500
import java.time.LocalDate

@Composable
fun StudyPlanScreen() {
    val activity = LocalActivity.current
    val context = LocalContext.current
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navigationBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val repository = remember { StudyPlanRepository.get(context) }
    val state by repository.state.collectAsStateWithLifecycle()
    val repositoryName by repository.repository.collectAsStateWithLifecycle()
    val path by repository.path.collectAsStateWithLifecycle()
    val token by repository.token.collectAsStateWithLifecycle()
    var today by remember { mutableStateOf(LocalDate.now()) }

    LifecycleResumeEffect(Unit) {
        today = LocalDate.now()
        onPauseOrDispose { }
    }

    val backgroundColor = AppColors.pageBackground
    val cardColor = AppColors.cardBackground
    val backdrop = rememberLayerBackdrop {
        drawRect(
            color = backgroundColor,
            size = Size(this.size.width * 3, this.size.height * 3),
            topLeft = Offset(-this.size.width, -this.size.height)
        )
        drawContent()
    }
    val lazyListState = rememberLazyListState()
    val isSmallTitleVisible by lazyListState.isScrolledPast(statusBarHeight + 24.dp)
    val plan = state.plan
    val title = plan?.title ?: stringResource(R.string.study_plan)

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
                ConfigInfoHeader(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    brush = sweepGradient(
                        colorStops = arrayOf(
                            0f to harmonize(Blue500),
                            0.5f to harmonize(Teal500),
                            1f to harmonize(Blue500)
                        )
                    ),
                    backgroundColor = cardColor,
                    icon = painterResource(R.drawable.ic_mountain),
                    title = title,
                    info = state.syncedAt?.let {
                        stringResource(R.string.study_synced_at_format, formatSyncedAt(it))
                    } ?: stringResource(R.string.study_not_configured)
                )
            }
            item { VGap(24.dp) }

            if (plan != null) {
                planSections(plan, today)
            }

            item {
                Text(
                    text = stringResource(R.string.study_sync),
                    style = GlasenseTheme.type.title3Emphasized,
                    color = AppColors.content,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }
            item {
                ConfigTextField(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    title = stringResource(R.string.study_repository),
                    value = repositoryName,
                    onValueChange = repository::setRepository,
                    backgroundColor = cardColor,
                    singleLine = true,
                    decorateText = "owner/name",
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    )
                )
                VGap(24.dp)
            }
            item {
                ConfigTextField(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    title = stringResource(R.string.study_path),
                    value = path,
                    onValueChange = repository::setPath,
                    backgroundColor = cardColor,
                    singleLine = false,
                    decorateText = "path/to/plan.md",
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    )
                )
                VGap(24.dp)
            }
            item {
                ConfigTextField(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    title = stringResource(R.string.study_token),
                    value = token,
                    onValueChange = repository::setToken,
                    backgroundColor = cardColor,
                    singleLine = true,
                    decorateText = "github_pat_…",
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    )
                )
                VGap(24.dp)
            }
            Section(
                footer = {
                    state.error?.message(state.problem) ?: stringResource(R.string.study_token_hint)
                }
            ) {
                Row(
                    enabled = !state.isSyncing,
                    onClick = repository::refresh,
                    trailing = {
                        Text(
                            if (state.isSyncing) {
                                stringResource(R.string.study_syncing)
                            } else {
                                state.syncedAt?.let(::formatSyncedAt).orEmpty()
                            }
                        )
                    }
                ) {
                    Text(stringResource(R.string.study_sync_now))
                }
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

private fun ListScope.planSections(plan: StudyPlan, today: LocalDate) {
    val currentWeek = StudyPlanSchedule.weekOf(plan, today)

    currentWeek?.let { week ->
        item {
            CardWithTitle(
                title = stringResource(R.string.study_current_week) + " · " +
                    stringResource(R.string.study_week_format, week.number, week.dateRange()),
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                WeekDetails(week)
            }
            VGap(24.dp)
        }
    }

    Section(header = { stringResource(R.string.study_weeks) }) {
        plan.weeks.forEach { week ->
            Row(key = "week-${week.number}") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val isCurrent = week == currentWeek
                    val isPast = week.end.isBefore(today)
                    Text(
                        text = stringResource(R.string.study_week_format, week.number, week.dateRange()),
                        style = GlasenseTheme.type.subHeadlineEmphasized,
                        color = when {
                            isCurrent -> AppColors.primary
                            isPast -> AppColors.contentVariant
                            else -> AppColors.content
                        }
                    )
                    WeekDetails(week, dimmed = isPast)
                }
            }
        }
    }

    if (plan.slots.isNotEmpty()) {
        Section(header = { stringResource(R.string.study_weekly_structure) }) {
            plan.slots.forEachIndexed { index, slot ->
                Row(key = "slot-$index") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = slot.label,
                            style = GlasenseTheme.type.subHeadlineEmphasized,
                            color = AppColors.content
                        )
                        Text(
                            text = slot.content,
                            style = GlasenseTheme.type.subHeadline,
                            color = AppColors.content
                        )
                        if (slot.output.isNotBlank()) {
                            Text(
                                text = stringResource(R.string.study_output_format, slot.output),
                                style = GlasenseTheme.type.footnote,
                                color = AppColors.contentVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (plan.rules.isNotEmpty()) {
        Section(header = { stringResource(R.string.study_rules) }) {
            plan.rules.forEachIndexed { index, rule ->
                Row(key = "rule-$index") {
                    Text(
                        text = "${index + 1}. $rule",
                        style = GlasenseTheme.type.subHeadline,
                        color = AppColors.content,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            }
        }
    }
    item { VGap(24.dp) }
}

@Composable
private fun WeekDetails(week: StudyWeek, dimmed: Boolean = false) {
    val color = if (dimmed) AppColors.contentVariant else AppColors.content
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        week.tracks.filter { it.text.isNotBlank() }.forEach { track ->
            Text(
                text = "${track.name}：${track.text}",
                style = GlasenseTheme.type.subHeadline,
                color = color
            )
        }
        if (week.acceptance.isNotBlank()) {
            Text(
                text = stringResource(R.string.study_acceptance_format, week.acceptance),
                style = GlasenseTheme.type.footnote,
                color = AppColors.contentVariant
            )
        }
    }
}
