package com.nevoit.cresto.feature.daily

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nevoit.cresto.R
import com.nevoit.cresto.data.learning.StudyPlanRepository
import com.nevoit.cresto.data.routine.HabitRepository
import com.nevoit.cresto.data.routine.RoutineDatabase
import com.nevoit.cresto.data.routine.StudyLog
import com.nevoit.cresto.theme.GlasenseTheme
import java.io.File
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Uses a separate database and synthetic plan; no network or personal data is needed. */
@RunWith(AndroidJUnit4::class)
class StudyLogRegressionInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    private val pastMonday = LocalDate.now().calendarWeek().first().minusWeeks(1)
    private val cache get() = File(context.filesDir, "study-plan/plan.md")
    private val visible = mutableStateOf(true)
    private val blockNextRead = AtomicBoolean(false)
    private val queryStarted = CountDownLatch(1)
    private val releaseQuery = CountDownLatch(1)
    private val savedInstances = mutableListOf<Pair<java.lang.reflect.Field, Any?>>()
    private var oldCache: ByteArray? = null
    private var openedActivity: Activity? = null
    private lateinit var database: RoutineDatabase
    private val editable = SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)
    private val verticalList = hasScrollToIndexAction() and
        SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, RoutineDatabase::class.java)
            .setQueryCallback({ sql, _ ->
                if (sql.contains("SELECT * FROM study_logs ORDER BY date") &&
                    blockNextRead.compareAndSet(true, false)
                ) {
                    queryStarted.countDown()
                    check(releaseQuery.await(30, TimeUnit.SECONDS)) { "Timed out waiting to release initial read" }
                }
            }, { it.run() })
            .build()
        // Swap only the singleton references, restoring them after disposal. The delayed read
        // is a real Room query, so the production screen and autosave path are exercised.
        replaceInstance(RoutineDatabase::class.java, database)
        replaceInstance(HabitRepository::class.java, null)
        oldCache = cache.takeIf { it.exists() }?.readBytes()
        cache.parentFile!!.mkdirs()
        cache.writeText(planMarkdown())
        replaceInstance(StudyPlanRepository::class.java, null)
        val plan = StudyPlanRepository.get(context)
        compose.waitUntil(5_000) { plan.state.value.plan != null }
    }

    @After
    fun tearDown() {
        releaseQuery.countDown()
        openedActivity?.let { instrumentation.runOnMainSync { it.finish() } }
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        savedInstances.asReversed().forEach { (field, value) -> field.set(null, value) }
        oldCache?.let { cache.writeBytes(it) } ?: cache.delete()
        database.close()
    }

    @Test
    fun initialReadMustFinishBeforeEditingAndSavedNoteSurvivesRecreation() {
        val day = pastMonday
        val original = "Previously saved synthetic note"
        runBlocking {
            database.studyLogDao().upsert(StudyLog(day, note = original, updatedAt = day.atTime(21, 0)))
        }
        blockNextRead.set(true)
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            if (visible.value) {
                CompositionLocalProvider(LocalActivity provides null) {
                    GlasenseTheme { StudyLogScreen(day) }
                }
            }
        }
        assertTrue("The initial Room read must actually be delayed", queryStarted.await(5, TimeUnit.SECONDS))
        try {
            compose.onNode(verticalList).performScrollToNode(editable)
            compose.onNode(editable).assertIsNotEnabled()
        } finally {
            releaseQuery.countDown()
        }
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText(original)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(editable).assertIsEnabled().assertTextEquals(original)
        assertEquals(original, runBlocking { database.studyLogDao().get(day)?.note })
        val revised = "$original with an edit"
        compose.onNode(editable).performTextReplacement(revised)
        compose.waitUntil(5_000) { runBlocking { database.studyLogDao().get(day)?.note == revised } }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNode(verticalList).performScrollToNode(editable)
        compose.waitUntil(5_000) {
            compose.onAllNodes(editable and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(editable).assertIsEnabled().assertTextEquals(revised)
        capture("loaded-note.png")
    }

    @Test
    fun previousWeekDoneOnlyRecordCanBeOpenedAnnotatedAndUnmarked() {
        runBlocking {
            database.studyLogDao().upsert(
                StudyLog(pastMonday, done = true, updatedAt = pastMonday.atTime(21, 0))
            )
        }
        openPastDay(pastMonday)
        compose.onNode(verticalList).performScrollToNode(editable)
        compose.onNode(editable).performTextReplacement("Added after the week ended")
        compose.waitUntil(5_000) {
            runBlocking { database.studyLogDao().get(pastMonday)?.note == "Added after the week ended" }
        }
        val done = hasText(context.getString(R.string.study_done)) and hasClickAction()
        compose.onNode(verticalList).performScrollToNode(done)
        compose.onNode(done).performClick()
        compose.waitUntil(5_000) { runBlocking { database.studyLogDao().get(pastMonday)?.done == false } }
        capture("historical-note.png")
    }

    @Test
    fun previousWeekUnrecordedDayCanBeOpenedAndSaved() {
        val day = pastMonday.plusDays(1)
        openPastDay(day)
        compose.onNode(verticalList).performScrollToNode(editable)
        compose.onNode(editable).performTextReplacement("First note for a past day")
        compose.waitUntil(5_000) {
            runBlocking { database.studyLogDao().get(day)?.note == "First note for a past day" }
        }
    }

    @Test
    fun previousWeekFreeDayCanAlsoBeOpenedAndSaved() {
        val day = pastMonday.plusDays(6)
        openPastDay(day)
        compose.onNode(verticalList).performScrollToNode(editable)
        compose.onNode(editable).performTextReplacement("Reflection on a free day")
        compose.waitUntil(5_000) {
            runBlocking { database.studyLogDao().get(day)?.note == "Reflection on a free day" }
        }
    }

    private fun openPastDay(day: LocalDate) {
        compose.setContent {
            if (visible.value) GlasenseTheme { StudyPlanScreen() }
        }
        val week = StudyPlanRepository.get(context).state.value.plan!!.weeks.first()
        val weekLabel = context.getString(R.string.study_week_format, week.number, week.dateRange())
        compose.onNode(verticalList).performScrollToNode(hasText(weekLabel))
        compose.onNode(hasText(weekLabel) and hasClickAction()).performClick()
        val dayLink = hasText(day.studyLabel(), substring = true) and hasClickAction()
        compose.onNode(verticalList).performScrollToNode(dayLink)
        capture("historical-week.png")
        val monitor = instrumentation.addMonitor(StudyLogActivity::class.java.name, null, false)
        try {
            compose.onNode(dayLink).performClick()
            openedActivity = instrumentation.waitForMonitorWithTimeout(monitor, 5_000)
            assertNotNull("The historical row must open the real record activity", openedActivity)
            assertEquals(day.toString(), openedActivity!!.intent.getStringExtra("date"))
            compose.waitForIdle()
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    private fun replaceInstance(type: Class<*>, value: Any?) {
        val field = type.getDeclaredField("instance").apply { isAccessible = true }
        savedInstances += field to field.get(null)
        field.set(null, value)
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val directory = File(context.getExternalFilesDir(null), "study-regression").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    private fun planMarkdown() = """
        # Synthetic study plan ($pastMonday)
        ## 每周结构
        | 时段 | 内容 | 产出 |
        | --- | --- | --- |
        | 周一晚 | Study | Notes |
        | 周二晚 | Study | Notes |
        ## 十二周
        | 周 | 日期 | 主线晚 | 验收 |
        | --- | --- | --- | --- |
        | 1 | $pastMonday–${pastMonday.plusDays(6)} | Historical task | Historical output |
        | 2 | ${pastMonday.plusWeeks(1)}–${pastMonday.plusDays(13)} | Current task | Current output |
    """.trimIndent()
}
