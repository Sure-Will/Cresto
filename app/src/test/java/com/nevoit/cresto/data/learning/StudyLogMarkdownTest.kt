package com.nevoit.cresto.data.learning

import com.nevoit.cresto.data.routine.StudyLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class StudyLogMarkdownTest {

    private val planPath = "10-Area/self-learning-and-career/c-mcu-and-circuits-twelve-week-plan.md"

    private fun log(
        date: String,
        done: Boolean = false,
        note: String = "",
        task: String = "",
        strand: String = ""
    ) = StudyLog(
        date = LocalDate.parse(date),
        done = done,
        note = note,
        task = task,
        strand = strand,
        updatedAt = LocalDateTime.parse("${date}T22:30:00")
    )

    @Test
    fun placesTheLogBesideThePlan() {
        assertEquals(
            "10-Area/self-learning-and-career/cresto-study-log.md",
            StudyLogMarkdown.pathBesidePlan(planPath)
        )
        assertEquals("notes/cresto-study-log.md", StudyLogMarkdown.pathBesidePlan(" /notes/plan.md/ "))
        assertEquals("cresto-study-log.md", StudyLogMarkdown.pathBesidePlan("plan.md"))
        assertEquals("cresto-study-log.md", StudyLogMarkdown.pathBesidePlan(""))
    }

    @Test
    fun rendersRecordsInDateOrder() {
        val markdown = StudyLogMarkdown.render(
            listOf(
                log("2026-11-04", done = true, task = "电路第一课", strand = "电路"),
                log("2026-11-03", done = true, note = "五框图画完了", task = "FanControl 五框图", strand = "C+MCU")
            ),
            planPath
        )

        val expected = """
            # Cresto 学习记录

            由 Cresto 根据手机「每日 › 学习记录」生成，每次同步整篇覆盖，请不要手改。要调整安排，请改 [c-mcu-and-circuits-twelve-week-plan.md](c-mcu-and-circuits-twelve-week-plan.md)。

            ## 2026-11-03 周二 · C+MCU

            - 任务：FanControl 五框图
            - 完成：是

            > 五框图画完了

            ## 2026-11-04 周三 · 电路

            - 任务：电路第一课
            - 完成：是

        """.trimIndent()
        assertEquals(expected, markdown)
    }

    @Test
    fun quotesEveryLineOfANote() {
        val markdown = StudyLogMarkdown.render(
            listOf(log("2026-11-05", note = "  # 不是标题\r\n\r\n- 第二段  \n", task = "C 自测\n题二")),
            planPath
        )

        assertTrue(markdown.contains("## 2026-11-05 周四\n\n- 任务：C 自测 题二\n- 完成：否\n"))
        assertTrue(markdown.endsWith("\n> # 不是标题\n>\n> - 第二段\n"))
        assertFalse(markdown.contains("\r"))
        assertFalse(markdown.contains("\n# 不是标题"))
    }

    @Test
    fun skipsEmptyRecordsAndSaysWhenThereAreNone() {
        val markdown = StudyLogMarkdown.render(listOf(log("2026-11-06")), "plan.md")

        assertTrue(markdown.endsWith("\n还没有记录。\n"))
        assertFalse(markdown.contains("## "))
    }

    @Test
    fun sameRecordsGiveTheSameFileAndFingerprint() {
        val logs = listOf(log("2026-11-03", done = true, note = "a"), log("2026-11-04", note = "b"))
        val first = StudyLogMarkdown.render(logs, planPath)
        val second = StudyLogMarkdown.render(logs.reversed(), planPath)

        assertEquals(first, second)
        assertEquals(
            StudyLogMarkdown.fingerprint("o/r", "a.md", first),
            StudyLogMarkdown.fingerprint("o/r", "a.md", second)
        )
        assertNotEquals(
            StudyLogMarkdown.fingerprint("o/r", "a.md", first),
            StudyLogMarkdown.fingerprint("o/r", "b.md", first)
        )
        assertEquals(64, StudyLogMarkdown.fingerprint("o/r", "a.md", first).length)
    }
}
