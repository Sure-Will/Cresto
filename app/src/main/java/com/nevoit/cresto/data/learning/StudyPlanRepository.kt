package com.nevoit.cresto.data.learning

import android.content.Context
import com.nevoit.cresto.data.routine.StudyLog
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

data class StudyPlanState(
    val plan: StudyPlan? = null,
    /** When the shown plan was last downloaded. */
    val syncedAt: Long? = null,
    val isSyncing: Boolean = false,
    val error: StudyPlanSyncError? = null,
    /** Why the latest download was rejected when [error] is [StudyPlanSyncError.Format]. */
    val problem: StudyPlanProblem? = null
)

data class StudyLogSyncState(
    /** When the study log file last matched the records on this device. */
    val syncedAt: Long? = null,
    val error: StudyLogSyncError? = null
)

enum class StudyLogPushOutcome {
    Uploaded,
    UpToDate,
    /** No token, or no records: an empty file would replace the history kept in the repository. */
    Skipped,
    /** Worth trying again later: network, rate limit or a GitHub hiccup. */
    Retry,
    /** Needs the user to fix the token, repository or path. */
    Failed
}

/**
 * Keeps the last plan that parsed, so a failed download or a broken edit never blanks the page.
 * Settings live in their own MMKV file, which the backup rules exclude because of the token.
 */
class StudyPlanRepository private constructor(context: Context) {
    private val mmkv = MMKV.mmkvWithID(MMKV_ID)
    private val cacheFile = File(context.applicationContext.filesDir, "study-plan/plan.md")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshMutex = Mutex()
    private val pushMutex = Mutex()

    private val _state = MutableStateFlow(StudyPlanState())
    val state: StateFlow<StudyPlanState> = _state.asStateFlow()

    private val _logSync = MutableStateFlow(
        StudyLogSyncState(
            syncedAt = mmkv.decodeLong(KEY_LOG_SYNCED_AT, 0L).takeIf { it > 0L },
            error = mmkv.decodeString(KEY_LOG_ERROR)?.let { name ->
                StudyLogSyncError.entries.firstOrNull { it.name == name }
            }
        )
    )
    val logSync: StateFlow<StudyLogSyncState> = _logSync.asStateFlow()

    private val _token = MutableStateFlow(mmkv.decodeString(KEY_TOKEN, "").orEmpty())
    val token: StateFlow<String> = _token.asStateFlow()

    private val _repository =
        MutableStateFlow(mmkv.decodeString(KEY_REPOSITORY, DEFAULT_REPOSITORY) ?: DEFAULT_REPOSITORY)
    val repository: StateFlow<String> = _repository.asStateFlow()

    private val _path = MutableStateFlow(mmkv.decodeString(KEY_PATH, DEFAULT_PATH) ?: DEFAULT_PATH)
    val path: StateFlow<String> = _path.asStateFlow()

    init {
        scope.launch { loadCache() }
    }

    fun setToken(value: String) {
        val trimmed = value.trim()
        _token.value = trimmed
        mmkv.encode(KEY_TOKEN, trimmed)
    }

    fun setRepository(value: String) {
        val trimmed = value.trim()
        _repository.value = trimmed
        mmkv.encode(KEY_REPOSITORY, trimmed)
    }

    fun setPath(value: String) {
        val trimmed = value.trim()
        _path.value = trimmed
        mmkv.encode(KEY_PATH, trimmed)
    }

    suspend fun reset() = withContext(Dispatchers.IO) {
        // Wait for cache loading, an in-flight refresh and an upload before removing their results.
        refreshMutex.withLock {
            pushMutex.withLock {
                for (file in listOf(cacheFile, File(cacheFile.parentFile, "${cacheFile.name}.part"))) {
                    if (file.exists() && !file.delete()) {
                        throw IOException("Could not remove the study plan cache")
                    }
                }
                mmkv.clearAll()
                _token.value = ""
                _repository.value = DEFAULT_REPOSITORY
                _path.value = DEFAULT_PATH
                _state.value = StudyPlanState()
                _logSync.value = StudyLogSyncState()
            }
        }
    }

    /**
     * Writes [logs] to the study log file beside the plan, unless that exact content was the last
     * upload to the same place. Blocking network work runs on IO.
     */
    suspend fun pushStudyLog(logs: List<StudyLog>): StudyLogPushOutcome = withContext(Dispatchers.IO) {
        pushMutex.withLock {
            val token = _token.value
            if (token.isBlank() || logs.all { it.isEmpty }) return@withLock StudyLogPushOutcome.Skipped

            val repository = _repository.value
            val planPath = _path.value
            val path = StudyLogMarkdown.pathBesidePlan(planPath)
            val markdown = StudyLogMarkdown.render(logs, planPath)
            val fingerprint = StudyLogMarkdown.fingerprint(repository, path, markdown)
            if (fingerprint == mmkv.decodeString(KEY_LOG_FINGERPRINT)) {
                recordLogError(null)
                return@withLock StudyLogPushOutcome.UpToDate
            }

            val target = "$repository\n$path"
            val knownSha = mmkv.decodeString(KEY_LOG_SHA)
                ?.takeIf { mmkv.decodeString(KEY_LOG_TARGET) == target }
            when (val result = StudyLogRemote.push(repository, path, token, markdown, knownSha)) {
                is StudyLogPushResult.Success -> {
                    val now = System.currentTimeMillis()
                    mmkv.encode(KEY_LOG_FINGERPRINT, fingerprint)
                    mmkv.encode(KEY_LOG_SHA, result.sha)
                    mmkv.encode(KEY_LOG_TARGET, target)
                    mmkv.encode(KEY_LOG_SYNCED_AT, now)
                    mmkv.removeValueForKey(KEY_LOG_ERROR)
                    _logSync.value = StudyLogSyncState(syncedAt = now)
                    StudyLogPushOutcome.Uploaded
                }

                is StudyLogPushResult.Failure -> {
                    recordLogError(result.error)
                    when (result.error) {
                        StudyLogSyncError.Network,
                        StudyLogSyncError.Server,
                        StudyLogSyncError.RateLimited -> StudyLogPushOutcome.Retry

                        else -> StudyLogPushOutcome.Failed
                    }
                }
            }
        }
    }

    private fun recordLogError(error: StudyLogSyncError?) {
        if (error == null) mmkv.removeValueForKey(KEY_LOG_ERROR) else mmkv.encode(KEY_LOG_ERROR, error.name)
        _logSync.update { it.copy(error = error) }
    }

    /** Refreshes when the last attempt is older than [STALE_AFTER_MS]; true when it started one. */
    fun refreshIfStale(): Boolean {
        val lastAttempt = mmkv.decodeLong(KEY_LAST_ATTEMPT_AT, 0L)
        if (System.currentTimeMillis() - lastAttempt < STALE_AFTER_MS) return false
        refresh()
        return true
    }

    fun refresh() {
        scope.launch { refreshNow() }
    }

    private suspend fun refreshNow() {
        if (_state.value.isSyncing) return
        refreshMutex.withLock {
            if (_token.value.isBlank()) {
                _state.update { it.copy(error = StudyPlanSyncError.NotConfigured, problem = null) }
                return
            }
            _state.update { it.copy(isSyncing = true) }
            mmkv.encode(KEY_LAST_ATTEMPT_AT, System.currentTimeMillis())

            val result = StudyPlanRemote.fetch(_repository.value, _path.value, _token.value)
            _state.update { current -> apply(current, result) }
        }
    }

    private fun apply(current: StudyPlanState, result: StudyPlanFetchResult): StudyPlanState {
        val idle = current.copy(isSyncing = false)
        if (result is StudyPlanFetchResult.Failure) {
            return idle.copy(error = result.error, problem = null)
        }
        val markdown = (result as StudyPlanFetchResult.Success).markdown
        return when (val parsed = StudyPlanParser.parse(markdown)) {
            is StudyPlanParseResult.Failure -> {
                idle.copy(error = StudyPlanSyncError.Format, problem = parsed.problem)
            }

            is StudyPlanParseResult.Success -> {
                val now = System.currentTimeMillis()
                writeCache(markdown)
                mmkv.encode(KEY_SYNCED_AT, now)
                StudyPlanState(plan = parsed.plan, syncedAt = now)
            }
        }
    }

    private suspend fun loadCache() = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            val markdown = runCatching { cacheFile.takeIf { it.exists() }?.readText() }.getOrNull()
                ?: return@withLock
            val plan = (StudyPlanParser.parse(markdown) as? StudyPlanParseResult.Success)?.plan
                ?: return@withLock
            val syncedAt = mmkv.decodeLong(KEY_SYNCED_AT, 0L).takeIf { it > 0L }
            _state.update { current ->
                if (current.plan != null) current else current.copy(plan = plan, syncedAt = syncedAt)
            }
        }
    }

    private fun writeCache(markdown: String) {
        runCatching {
            cacheFile.parentFile?.mkdirs()
            val partial = File(cacheFile.parentFile, "${cacheFile.name}.part")
            partial.writeText(markdown)
            if (!partial.renameTo(cacheFile)) {
                cacheFile.writeText(markdown)
                partial.delete()
            }
        }
    }

    companion object {
        const val MMKV_ID = "study_plan"
        const val DEFAULT_REPOSITORY = "Sure-Will/Sure-Atlas"
        const val DEFAULT_PATH =
            "10-Area/self-learning-and-career/c-mcu-and-circuits-twelve-week-plan.md"

        private const val KEY_TOKEN = "token"
        private const val KEY_REPOSITORY = "repository"
        private const val KEY_PATH = "path"
        private const val KEY_SYNCED_AT = "synced_at"
        private const val KEY_LAST_ATTEMPT_AT = "last_attempt_at"
        private const val STALE_AFTER_MS = 30L * 60L * 1000L
        private const val KEY_LOG_FINGERPRINT = "log_fingerprint"
        private const val KEY_LOG_SHA = "log_sha"
        private const val KEY_LOG_TARGET = "log_target"
        private const val KEY_LOG_SYNCED_AT = "log_synced_at"
        private const val KEY_LOG_ERROR = "log_error"

        @Volatile
        private var instance: StudyPlanRepository? = null

        fun get(context: Context): StudyPlanRepository =
            instance ?: synchronized(this) {
                instance ?: StudyPlanRepository(context).also { instance = it }
            }
    }
}
