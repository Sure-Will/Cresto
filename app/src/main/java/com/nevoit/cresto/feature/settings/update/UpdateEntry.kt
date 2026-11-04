package com.nevoit.cresto.feature.settings.update

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nevoit.cresto.R
import com.nevoit.cresto.feature.settings.util.SettingsViewModel
import com.nevoit.cresto.theme.AppColors
import com.nevoit.cresto.theme.harmonize
import com.nevoit.glasense.component.Switch
import com.nevoit.glasense.core.component.Icon
import com.nevoit.glasense.core.component.Text
import com.nevoit.glasense.theme.GlasenseTheme
import com.nevoit.glasense.theme.tokens.Blue500
import com.nevoit.glasense.theme.tokens.Slate500
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class UpdateEntryState internal constructor(private val scope: CoroutineScope) {
    var result by mutableStateOf<UpdateCheckResult?>(null)
        private set
    var isChecking by mutableStateOf(false)
        private set
    var showSheet by mutableStateOf(false)
        private set
    private var checkJob: Job? = null

    fun onClick() {
        if (isChecking) return
        if (result is UpdateCheckResult.HasUpdate) {
            showSheet = true
            return
        }
        isChecking = true
        checkJob = scope.launch {
            try {
                result = UpdateChecker.check()
                showSheet = result is UpdateCheckResult.HasUpdate
            } finally {
                isChecking = false
            }
        }
    }

    fun dismissSheet() {
        if ((result as? UpdateCheckResult.HasUpdate)?.updateInfo?.isRequired == false) {
            showSheet = false
        }
    }

    /**
     * Leaving the page forgets the result, except while the update sheet is open: it stays up
     * through the trip to "install unknown apps" and resumes the install when the user returns.
     */
    internal fun onStop() {
        if (!showSheet) reset()
    }

    private fun reset() {
        checkJob?.cancel()
        checkJob = null
        isChecking = false
        result = null
        showSheet = false
    }
}

@Composable
fun rememberUpdateEntryState(): UpdateEntryState {
    val scope = rememberCoroutineScope()
    val state = remember(scope) { UpdateEntryState(scope) }
    // Keep results across list scrolling, but not across visits to this Activity.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { state.onStop() }
    return state
}

@Composable
fun UpdateEntry(
    state: UpdateEntryState,
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val checkOnStartup by settingsViewModel.isCheckUpdatesOnStartup
    val result = state.result
    val status = when {
        state.isChecking -> stringResource(R.string.checking_for_updates)
        result is UpdateCheckResult.HasUpdate -> stringResource(
            R.string.update_status_available_format,
            "${result.updateInfo.manifest.versionName} (${result.updateInfo.manifest.versionCode})"
        )
        result is UpdateCheckResult.NoUpdate -> stringResource(R.string.update_status_latest)
        result is UpdateCheckResult.Failed -> stringResource(R.string.update_status_failed)
        result is UpdateCheckResult.NotConfigured -> stringResource(R.string.update_status_debug)
        else -> null
    }
    val statusColor = when {
        state.isChecking -> AppColors.contentVariant
        result is UpdateCheckResult.Failed -> AppColors.error
        result is UpdateCheckResult.HasUpdate -> AppColors.primary
        else -> AppColors.contentVariant
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .clickable(enabled = !state.isChecking, onClick = state::onClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UpdateEntryIcon(R.drawable.ic_arrow_counterclockwise, harmonize(Blue500))
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.check_for_updates),
                style = GlasenseTheme.type.body,
                modifier = Modifier.weight(1f)
            )
            if (status != null) {
                Spacer(Modifier.width(12.dp))
                Text(
                    text = status,
                    color = statusColor,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.End,
                    modifier = Modifier.widthIn(max = 144.dp)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .toggleable(
                    value = checkOnStartup,
                    role = Role.Switch,
                    onValueChange = settingsViewModel::onCheckUpdatesOnStartupChanged
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UpdateEntryIcon(R.drawable.ic_clock_cycle, harmonize(Slate500))
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.check_for_updates_on_startup),
                style = GlasenseTheme.type.body,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = checkOnStartup,
                onCheckedChange = settingsViewModel::onCheckUpdatesOnStartupChanged
            )
        }
    }
    if (state.showSheet && result is UpdateCheckResult.HasUpdate) {
        UpdateBottomSheet(updateInfo = result.updateInfo, onDismissed = state::dismissSheet)
    }
}

@Composable
private fun UpdateEntryIcon(@DrawableRes icon: Int, color: Color) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(color, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.fillMaxSize()
        )
    }
}
