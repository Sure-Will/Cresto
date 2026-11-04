package com.nevoit.cresto.feature.daily

import android.content.Context
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.core.app.ActivityOptionsCompat
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nevoit.cresto.R
import com.nevoit.cresto.data.learning.StudyPlanRepository
import com.nevoit.cresto.data.routine.HabitBackupCodec
import com.nevoit.cresto.data.routine.HabitRepository
import com.nevoit.cresto.data.routine.RoutineDatabase
import com.nevoit.cresto.feature.settings.DataStorageScreen
import com.nevoit.cresto.theme.GlasenseTheme
import com.tencent.mmkv.MMKV
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DailyRegressionInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val habitRepository get() = HabitRepository.get(context)
    private val planRepository get() = StudyPlanRepository.get(context)
    private val dao get() = RoutineDatabase.get(context).habitDao()
    private val studyLogDao get() = RoutineDatabase.get(context).studyLogDao()
    private val planCache get() = File(context.filesDir, "study-plan/plan.md")
    private val temporaryFiles = mutableListOf<File>()
    private val verticalList = hasScrollToIndexAction() and
        SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)

    @Before
    fun prepareSyntheticData() {
        removeTestData()
        runBlocking { habitRepository.import(backup(checkInId = "regression-check-in")) }
    }

    @After
    fun cleanUp() {
        removeTestData()
        temporaryFiles.forEach { it.delete() }
    }

    @Test
    fun clearAllData_removesHabitsCheckInsAndIndependentStudyStorage() {
        configureStudyPlan()
        habitRepository.setStudyNote(STUDY_LOG_DATE, "Regression note", "", "")
        compose.waitUntil(5_000) { runBlocking { studyLogDao.get(STUDY_LOG_DATE) != null } }
        showStorage()

        clickStorageRow(R.string.clear_all_data)
        compose.onNode(hasText(context.getString(R.string.clear)) and hasClickAction()).performClick()

        compose.waitUntil(5_000) {
            runBlocking {
                dao.habits().isEmpty() && dao.checkIns().isEmpty() && studyLogDao.all().isEmpty()
            } && studyPlanIsReset()
        }
        assertStudyPlanReset()
    }

    @Test
    fun confirmedClearAllData_survivesStateRestorationWhileHabitDatabaseIsBusy() {
        configureStudyPlan()
        val marker = "daily-regression-clear-started"
        val settings = MMKV.defaultMMKV()
        settings.encode(marker, true)
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            CompositionLocalProvider(LocalActivity provides null) {
                GlasenseTheme { DataStorageScreen() }
            }
        }

        val enteredTransaction = CompletableDeferred<Unit>()
        val releaseTransaction = CompletableDeferred<Unit>()
        val lockScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val lockedTransaction = lockScope.async {
            withTimeout(30_000) {
                RoutineDatabase.get(context).withTransaction {
                    enteredTransaction.complete(Unit)
                    releaseTransaction.await()
                }
            }
        }
        try {
            runBlocking { withTimeout(5_000) { enteredTransaction.await() } }
            clickStorageRow(R.string.clear_all_data)
            compose.onNode(hasText(context.getString(R.string.clear)) and hasClickAction())
                .performClick()

            // Todo clearing finishes first; the habit transaction is still held by this test.
            compose.waitUntil(5_000) { !settings.containsKey(marker) }
            restoration.emulateSavedInstanceStateRestore()
            releaseTransaction.complete(Unit)
            runBlocking { withTimeout(5_000) { lockedTransaction.await() } }

            compose.waitUntil(5_000) {
                runBlocking {
                dao.habits().isEmpty() && dao.checkIns().isEmpty() && studyLogDao.all().isEmpty()
            } && studyPlanIsReset()
            }
            assertStudyPlanReset()
        } finally {
            releaseTransaction.complete(Unit)
            try {
                runBlocking { withTimeout(5_000) { lockedTransaction.join() } }
            } finally {
                lockScope.cancel()
                settings.removeValueForKey(marker)
            }
        }
    }

    @Test
    fun resetAllSettings_resetsStudyStorageAndKeepsHabitHistory() {
        configureStudyPlan()
        showStorage()

        clickStorageRow(R.string.reset_all_settings)
        compose.onNode(hasText(context.getString(R.string.reset)) and hasClickAction()).performClick()

        compose.waitUntil(5_000) { studyPlanIsReset() }
        assertStudyPlanReset()
        runBlocking {
            assertEquals(listOf(HABIT_ID), dao.habits().map { it.id })
            assertEquals(listOf("regression-check-in"), dao.checkIns().map { it.id })
        }
    }

    @Test
    fun exportAfterStateRestoration_writesReadableHabitBackup() {
        val registry = CapturingRegistry()
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry: ActivityResultRegistry = registry
        }
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            CompositionLocalProvider(
                LocalActivity provides null,
                LocalActivityResultRegistryOwner provides owner
            ) {
                GlasenseTheme { DataStorageScreen() }
            }
        }
        clickStorageRow(R.string.habit_export)
        compose.waitUntil(5_000) { registry.requestCode != null }
        val requestCode = registry.requestCode
        assertNotNull(requestCode)

        // The document picker stays outside the composition while the screen is recreated.
        restoration.emulateSavedInstanceStateRestore()
        val destination = File.createTempFile("daily-regression-export-", ".json", context.cacheDir)
            .also(temporaryFiles::add)
        compose.runOnIdle {
            registry.dispatchResult(requestCode!!, Uri.fromFile(destination))
        }

        compose.waitUntil(5_000) { destination.length() > 0 }
        val decoded = HabitBackupCodec.decode(destination.readText())
        assertEquals(listOf(HABIT_ID), decoded.habits.map { it.id })
        assertEquals(listOf("regression-check-in"), decoded.checkIns.map { it.id })
    }

    @Test
    fun importedCheckInNamedAdd_rendersHistoryAndAllowsAnotherCheckIn() {
        runBlocking {
            dao.habits().forEach { dao.deleteHabit(it.id) }
            habitRepository.import(backup(checkInId = "add"))
        }
        compose.setContent {
            CompositionLocalProvider(LocalActivity provides null) {
                GlasenseTheme { HabitDetailScreen(HABIT_ID) }
            }
        }
        compose.waitUntil(5_000) {
            compose.onAllNodes(verticalList).fetchSemanticsNodes().isNotEmpty()
        }
        val checkInLabel = context.getString(R.string.habit_check_in)
        compose.onNode(verticalList).performScrollToNode(hasText(checkInLabel))
        compose.onNodeWithText(checkInLabel).assertIsDisplayed().performClick()

        compose.waitUntil(5_000) { runBlocking { dao.checkIns().size == 2 } }
        runBlocking { assertTrue(dao.checkIns().any { it.id == "add" }) }
    }

    private fun showStorage() {
        compose.setContent {
            // Keep the Compose test host alive when the real screen finishes after clearing data.
            CompositionLocalProvider(LocalActivity provides null) {
                GlasenseTheme { DataStorageScreen() }
            }
        }
    }

    private fun clickStorageRow(label: Int) {
        val text = context.getString(label)
        compose.onNode(verticalList).performScrollToNode(hasText(text))
        compose.onNodeWithText(text).performClick()
    }

    private fun configureStudyPlan() {
        planRepository.setToken("synthetic-regression-token")
        planRepository.setRepository("regression-owner/regression-repository")
        planRepository.setPath("regression-plan.md")
        planCache.parentFile!!.mkdirs()
        planCache.writeText(
            """
            # Regression plan (2026-11-03)
            ## 十二周
            | 周 | 日期 | 主线晚 | 验收 |
            | --- | --- | --- | --- |
            | 1 | 11/03–11/08 | Synthetic task | Synthetic output |
            """.trimIndent()
        )
    }

    private fun assertStudyPlanReset() {
        assertEquals("", planRepository.token.value)
        assertEquals(StudyPlanRepository.DEFAULT_REPOSITORY, planRepository.repository.value)
        assertEquals(StudyPlanRepository.DEFAULT_PATH, planRepository.path.value)
        assertEquals("", MMKV.mmkvWithID(StudyPlanRepository.MMKV_ID).decodeString("token", ""))
        assertFalse("A cleared plan must not reload from its old cache", planCache.exists())
    }

    private fun studyPlanIsReset(): Boolean =
        planRepository.token.value.isEmpty() &&
            planRepository.repository.value == StudyPlanRepository.DEFAULT_REPOSITORY &&
            planRepository.path.value == StudyPlanRepository.DEFAULT_PATH &&
            MMKV.mmkvWithID(StudyPlanRepository.MMKV_ID).decodeString("token", "").isNullOrEmpty() &&
            !planCache.exists()

    private fun removeTestData() {
        runBlocking {
            dao.habits().forEach { dao.deleteHabit(it.id) }
            studyLogDao.delete(STUDY_LOG_DATE)
        }
        planRepository.setToken("")
        planRepository.setRepository(StudyPlanRepository.DEFAULT_REPOSITORY)
        planRepository.setPath(StudyPlanRepository.DEFAULT_PATH)
        MMKV.mmkvWithID(StudyPlanRepository.MMKV_ID).clearAll()
        planCache.delete()
        File(planCache.parentFile, "plan.md.part").delete()
    }

    private fun backup(checkInId: String): String = """
        {"version":2,"habits":[{"id":"$HABIT_ID","title":"Regression habit",
        "checkIns":[{"id":"$checkInId","at":"${LocalDate.now()}T12:34:00"}]}]}
    """.trimIndent()

    private class CapturingRegistry : ActivityResultRegistry() {
        var requestCode: Int? = null
            private set

        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?
        ) {
            this.requestCode = requestCode
        }
    }

    private companion object {
        const val HABIT_ID = "daily-regression-habit"
        val STUDY_LOG_DATE: LocalDate = LocalDate.of(2000, 1, 1)
    }
}
