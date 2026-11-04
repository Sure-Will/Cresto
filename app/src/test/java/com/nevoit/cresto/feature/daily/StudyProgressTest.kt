package com.nevoit.cresto.feature.daily

import com.nevoit.cresto.data.learning.StudyPlan
import com.nevoit.cresto.data.learning.StudyPlanParseResult
import com.nevoit.cresto.data.learning.StudyPlanParser
import com.nevoit.cresto.data.routine.StudyLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class StudyProgressTest {

    private val plan: StudyPlan = run {
        val result = StudyPlanParser.parse(
            """
            # 示例计划（2026-11-02 起）

            ## 每周结构

            | 时段 | 内容 | 产出 |
            | --- | --- | --- |
            | 周一晚 | 主线 | 一个函数 |
            | 周三晚 | 副线 | 一道计算 |
            | 周五 45 分钟 | 复盘 | 三行记录 |

            ## 十二周

            | 周 | 日期 | 主线晚 | 副线晚（周三） | 本周验收 |
            | --- | --- | --- | --- | --- |
            | 1 | 11/02–11/08 | 主线一 | 副线一 | 验收一 |
            """.trimIndent()
        )
        assertTrue("$result", result is StudyPlanParseResult.Success)
        (result as StudyPlanParseResult.Success).plan
    }

    private fun log(day: Int, done: Boolean, note: String = "") = StudyLog(
        date = LocalDate.of(2026, 11, day),
        done = done,
        note = note,
        updatedAt = LocalDate.of(2026, 11, day).atTime(22, 0)
    )

    @Test
    fun countsOnlyDaysThatHaveASession() {
        val week = plan.weeks.single()
        val logs = listOf(
            log(2, done = true),
            log(4, done = false, note = "只写了心得"),
            // Saturday has no session; finishing it anyway does not move the bar.
            log(7, done = true)
        ).associateBy { it.date }

        val progress = weekProgress(plan, week, logs)
        assertEquals(WeekProgress(done = 1, total = 3), progress)
        assertEquals(1f / 3, progress.fraction, 0.0001f)
    }

    @Test
    fun emptyWeekHasNoProgress() {
        assertEquals(0f, WeekProgress(0, 0).fraction, 0f)
    }

    @Test
    fun calendarWeekRunsMondayToSunday() {
        val week = LocalDate.of(2026, 11, 4).calendarWeek()
        assertEquals(7, week.size)
        assertEquals(DayOfWeek.MONDAY, week.first().dayOfWeek)
        assertEquals(LocalDate.of(2026, 11, 2), week.first())
        assertEquals(LocalDate.of(2026, 11, 8), week.last())
    }
}
