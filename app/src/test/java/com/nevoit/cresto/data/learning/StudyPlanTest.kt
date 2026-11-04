package com.nevoit.cresto.data.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate

class StudyPlanTest {

    private val sample = """
        # 示例十二周计划（2026-11-03 起）

        说明文字不影响解析。

        ## 三条规则

        1. 先自己写，再让 **AI** 挑错。
        2. 每个工作日至少一张卡。
        3. 落后时先删 A，再删 B。

        ## 每周结构

        | 时段 | 内容 | 产出 |
        | --- | --- | --- |
        | 周一晚 | 主线：基础 | 一个函数 |
        | 周二晚 | 主线：外设 | 一张图 |
        | 周三晚 | 副线 | 一道计算 |
        | 周四晚 | 综合 | 一次改动 |
        | 周五 45 分钟 | 复盘 | 三行记录 |
        | 每个工作日 | 预测卡 | 至少 1 张 |

        ## 第 1 周逐晚安排（11/03–11/08）

        | 日期 | 任务 | 留下什么 |
        | --- | --- | --- |
        | 11/03 周二 | 画 **框图**；[题一](notes/a.md) | 一张图 |
        | 11/04 周三 | 第一课 | 一道题 |

        ## 十二周

        | 周 | 日期 | 主线晚 | 副线晚（周三） | 本周验收 |
        | --- | --- | --- | --- | --- |
        | 1 | 11/03–11/08 | 主线一 | 副线一 | 验收一 |
        | 2 | 11/09–11/15 | 主线二 | 副线二 | 验收二 |
        | 9 | 12/28–01/03 | 主线九 | 副线九 | 验收九 |
        | 12 | 01/18–01/24 | 主线十二 | 副线十二 | 验收十二 |

        ## 执行记录

        | 周 | 日期 | 状态 | 验收与卡点 |
        | --- | --- | --- | --- |
        | 1 | 11/03–11/08 | 进行中 | |
    """.trimIndent()

    private val plan: StudyPlan by lazy { parseOrFail(sample) }

    @Test
    fun readsHeadingsRulesSlotsAndWeeks() {
        assertEquals("示例十二周计划", plan.title)
        assertEquals(LocalDate.of(2026, 11, 3), plan.startDate)
        assertEquals(
            listOf("先自己写，再让 AI 挑错。", "每个工作日至少一张卡。", "落后时先删 A，再删 B。"),
            plan.rules
        )
        assertEquals(6, plan.slots.size)
        assertEquals(setOf(DayOfWeek.FRIDAY), plan.slots[4].days)
        assertTrue(plan.slots[4].isSession)
        assertTrue(!plan.slots[5].isSession)

        // The execution record table also has 周 and 日期 columns and must be ignored.
        assertEquals(listOf(1, 2, 9, 12), plan.weeks.map { it.number })
        val first = plan.weeks.first()
        assertEquals(listOf("主线晚", "副线晚（周三）"), first.tracks.map { it.name })
        assertEquals(listOf(null, DayOfWeek.WEDNESDAY), first.tracks.map { it.day })
        assertEquals("验收一", first.acceptance)
    }

    @Test
    fun resolvesYearsAcrossNewYear() {
        val week9 = plan.weeks.first { it.number == 9 }
        assertEquals(LocalDate.of(2026, 12, 28), week9.start)
        assertEquals(LocalDate.of(2027, 1, 3), week9.end)
        assertEquals(LocalDate.of(2027, 1, 18), plan.weeks.last().start)
    }

    @Test
    fun stripsInlineMarkdownFromNights() {
        assertEquals(2, plan.nights.size)
        assertEquals("画 框图；题一", plan.nights.first().task)
        assertEquals("11/03 周二", plan.nights.first().label)
    }

    @Test
    fun columnOrderDoesNotMatter() {
        val reordered = sample.replace(
            """
            | 周 | 日期 | 主线晚 | 副线晚（周三） | 本周验收 |
            | --- | --- | --- | --- | --- |
            | 1 | 11/03–11/08 | 主线一 | 副线一 | 验收一 |
            """.trimIndent(),
            """
            | 本周验收 | 副线晚（周三） | 周 | 主线晚 | 日期 |
            | --- | --- | --- | --- | --- |
            | 验收一 | 副线一 | 1 | 主线一 | 11/03–11/08 |
            """.trimIndent()
        )
        val week = parseOrFail(reordered).weeks.first()
        assertEquals(LocalDate.of(2026, 11, 3), week.start)
        assertEquals("验收一", week.acceptance)
        assertEquals(
            "主线一",
            StudyPlanSchedule.sessionOn(parseOrFail(reordered), LocalDate.of(2026, 11, 5))?.task
        )
    }

    @Test
    fun rejectsDocumentsMissingRequiredParts() {
        assertProblem(StudyPlanProblem.MissingStartDate, sample.replace("（2026-11-03 起）", ""))
        assertProblem(StudyPlanProblem.MissingWeeks, sample.replace("## 十二周", "## 计划"))
        assertProblem(
            StudyPlanProblem.MissingWeekColumns,
            sample.replace("| 周 | 日期 | 主线晚", "| 周 | 时间 | 主线晚")
        )
        assertProblem(
            StudyPlanProblem.BadWeekDates,
            sample.replace("| 2 | 11/09–11/15 |", "| 2 | 11/15–11/09 |")
        )
        assertProblem(StudyPlanProblem.MissingStartDate, "not a plan")
    }

    @Test
    fun rejectsDuplicateWeekNumbers() {
        val duplicated = sample.replace("| 2 | 11/09–11/15 |", "| 1 | 11/09–11/15 |")
        assertProblem(StudyPlanProblem.DuplicateWeekNumber, duplicated)
    }

    @Test
    fun acceptsNonconsecutiveWeekNumbers() {
        assertEquals(listOf(1, 2, 9, 12), parseOrFail(sample).weeks.map { it.number })
    }

    @Test
    fun dayBeforeStartPointsAtFirstSession() {
        val day = StudyPlanSchedule.dayOf(plan, LocalDate.of(2026, 11, 2))
        assertTrue(day is StudyDay.BeforeStart)
        day as StudyDay.BeforeStart
        assertEquals(LocalDate.of(2026, 11, 3), day.startDate)
        assertEquals("画 框图；题一", day.first?.session?.task)
    }

    @Test
    fun nightlyRowOverridesTheWeekdaySlot() {
        val session = inWeek(2026, 11, 3).session!!
        assertEquals("11/03 周二", session.label)
        assertEquals("画 框图；题一", session.task)
        assertEquals("一张图", session.output)
        assertNull(session.focus)
    }

    @Test
    fun eveningsUseTheMatchingTrack() {
        val thursday = inWeek(2026, 11, 5)
        assertEquals("主线一", thursday.session?.task)
        assertEquals("综合", thursday.session?.focus)
        assertEquals("一次改动", thursday.session?.output)
        assertEquals(listOf("每个工作日"), thursday.dailySlots.map { it.label })

        assertEquals("主线二", inWeek(2026, 11, 9).session?.task)
        assertEquals("副线二", inWeek(2026, 11, 11).session?.task)
        assertEquals("副线九", inWeek(2026, 12, 30).session?.task)
    }

    @Test
    fun fridayIsTheReviewWithoutATrack() {
        val session = inWeek(2026, 11, 6).session!!
        assertEquals("周五 45 分钟", session.label)
        assertEquals("复盘", session.task)
        assertNull(session.focus)
        assertEquals("复盘", inWeek(2027, 1, 1).session?.task)
    }

    @Test
    fun weekendShowsTheNextSession() {
        val saturday = inWeek(2026, 11, 7)
        assertNull(saturday.session)
        assertTrue(saturday.dailySlots.isEmpty())
        assertEquals(LocalDate.of(2026, 11, 9), saturday.next?.date)
        assertEquals("主线二", saturday.next?.session?.task)
    }

    @Test
    fun gapsAndTheEndAreReported() {
        assertTrue(
            StudyPlanSchedule.dayOf(plan, LocalDate.of(2026, 11, 20)) is StudyDay.Unscheduled
        )
        val finished = StudyPlanSchedule.dayOf(plan, LocalDate.of(2027, 1, 25))
        assertTrue(finished is StudyDay.Finished)
        assertEquals(12, (finished as StudyDay.Finished).lastWeek.number)
    }

    @Test
    fun readsIsoDatesToo() {
        assertEquals(
            listOf(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 8)),
            StudyPlanParser.datesIn("2026-11-03 至 2026-11-08", LocalDate.of(2026, 1, 1))
        )
    }

    /**
     * Checks a real plan without committing it: run with STUDY_PLAN_FILE=/path/to/plan.md.
     */
    @Test
    fun parsesLocalPlanFileWhenProvided() {
        val path = System.getenv("STUDY_PLAN_FILE")
        assumeTrue(!path.isNullOrBlank())
        val local = parseOrFail(File(path!!).readText())
        assertTrue(local.weeks.isNotEmpty())
        assertTrue(local.slots.any { it.isSession })
        local.weeks.zipWithNext().forEach { (a, b) -> assertTrue(a.end.isBefore(b.start)) }
        println(
            "STUDY_PLAN_FILE: ${local.weeks.size} weeks, ${local.slots.size} slots, " +
                "${local.nights.size} nights, ${local.rules.size} rules, " +
                "${local.weeks.first().start}..${local.weeks.last().end}"
        )
    }

    private fun inWeek(year: Int, month: Int, day: Int): StudyDay.InWeek {
        val result = StudyPlanSchedule.dayOf(plan, LocalDate.of(year, month, day))
        assertTrue("Expected InWeek but was $result", result is StudyDay.InWeek)
        return result as StudyDay.InWeek
    }

    private fun parseOrFail(markdown: String): StudyPlan {
        val result = StudyPlanParser.parse(markdown)
        assertTrue("Expected success but was $result", result is StudyPlanParseResult.Success)
        return (result as StudyPlanParseResult.Success).plan
    }

    private fun assertProblem(expected: StudyPlanProblem, markdown: String) {
        assertEquals(StudyPlanParseResult.Failure(expected), StudyPlanParser.parse(markdown))
    }
}
