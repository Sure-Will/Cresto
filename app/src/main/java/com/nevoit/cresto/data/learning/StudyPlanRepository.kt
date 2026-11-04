package com.nevoit.cresto.data.learning

import android.content.Context
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

/**
 * Keeps the last plan that parsed, so a failed download or a broken edit never blanks the page.
 * Settings live in their own MMKV file, which the backup rules exclude because of the token.
 */
class StudyPlanRepository private constructor(context: Context) {
    private val mmkv = MMKV.mmkvWithID(MMKV_ID)
    private val cacheFile = File(context.applicationContext.filesDir, "study-plan/plan.md")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshMutex = Mutex()

    private val _state = MutableStateFlow(StudyPlanState())
    val state: StateFlow<StudyPlanState> = _state.asStateFlow()

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
        // Wait for cache loading and an in-flight refresh before removing their results.
        refreshMutex.withLock {
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
        }
    }

    /** Refreshes when the last attempt is older than [STALE_AFTER_MS]. */
    fun refreshIfStale() {
        val lastAttempt = mmkv.decodeLong(KEY_LAST_ATTEMPT_AT, 0L)
        if (System.currentTimeMillis() - lastAttempt < STALE_AFTER_MS) return
        refresh()
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

        @Volatile
        private var instance: StudyPlanRepository? = null

        fun get(context: Context): StudyPlanRepository =
            instance ?: synchronized(this) {
                instance ?: StudyPlanRepository(context).also { instance = it }
            }
    }
}
