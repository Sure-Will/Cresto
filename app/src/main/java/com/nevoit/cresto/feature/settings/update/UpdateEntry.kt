package com.nevoit.cresto.feature.settings.update

import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.nevoit.cresto.R
import com.nevoit.cresto.theme.AppColors
import com.nevoit.cresto.theme.harmonize
import com.nevoit.cresto.ui.components.packed.ConfigEntryItem
import com.nevoit.glasense.core.component.Text
import com.nevoit.glasense.theme.GlasenseTheme
import com.nevoit.glasense.theme.tokens.Blue500
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
fun UpdateEntry(state: UpdateEntryState) {
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

    ConfigEntryItem(
        color = harmonize(Blue500),
        icon = painterResource(R.drawable.ic_twotone_arrow_counterclockwise),
        title = stringResource(R.string.check_for_updates),
        trailing = if (status != null) {
            {
                Text(
                    text = status,
                    color = statusColor,
                    style = GlasenseTheme.type.body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 160.dp)
                )
            }
        } else null,
        onClick = state::onClick
    )
    if (state.showSheet && result is UpdateCheckResult.HasUpdate) {
        UpdateBottomSheet(updateInfo = result.updateInfo, onDismissed = state::dismissSheet)
    }
}
