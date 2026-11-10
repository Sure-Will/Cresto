package com.nevoit.cresto.data.learning

import com.nevoit.cresto.data.routine.StudyLog
import java.security.MessageDigest
import java.time.DayOfWeek

/**
 * The study log as one Markdown file beside the plan, so Claude or Codex can read what happened
 * each evening before adjusting the plan. Cresto is its only writer and rewrites it whole.
 *
 * Output depends only on the logs and the plan path: the same data always gives the same bytes,
 * which lets a sync skip uploads that would change nothing.
 */
object StudyLogMarkdown {
    const val FILE_NAME = "cresto-study-log.md"

    /** `area/plan.md` -> `area/cresto-study-log.md`; a plan at the root gives the bare file name. */
    fun pathBesidePlan(planPath: String): String {
        val directory = planPath.trim().trim('/').substringBeforeLast('/', missingDelimiterValue = "")
        return if (directory.isEmpty()) FILE_NAME else "$directory/$FILE_NAME"
    }

    fun render(logs: List<StudyLog>, planPath: String): String {
        val planFile = planPath.trim().trim('/').substringAfterLast('/')
        val entries = logs.filterNot { it.isEmpty }.sortedBy { it.date }

        return buildString {
            append("# Cresto 学习记录\n\n")
            append("由 Cresto 根据手机「每日 › 学习记录」生成，每次同步整篇覆盖，请不要手改。")
            if (planFile.isNotEmpty()) {
                append("要调整安排，请改 [")
                append(planFile)
                append("](")
                append(planFile.replace(" ", "%20"))
                append(")。")
            }
            append('\n')

            if (entries.isEmpty()) {
                append("\n还没有记录。\n")
                return@buildString
            }

            entries.forEach { log ->
                append("\n## ")
                append(log.date)
                append(' ')
                append(weekdayNames.getValue(log.date.dayOfWeek))
                val strand = log.strand.singleLine()
                if (strand.isNotEmpty()) {
                    append(" · ")
                    append(strand)
                }
                append("\n\n")

                val task = log.task.singleLine()
                if (task.isNotEmpty()) {
                    append("- 任务：")
                    append(task)
                    append('\n')
                }
                append("- 完成：")
                append(if (log.done) "是" else "否")
                append('\n')

                val note = log.note.replace("\r\n", "\n").replace('\r', '\n').trim()
                if (note.isNotEmpty()) {
                    append('\n')
                    // A quote keeps headings or lists inside a note from changing the file's outline.
                    note.lines().forEach { line ->
                        val trimmed = line.trimEnd()
                        append(if (trimmed.isEmpty()) ">" else "> $trimmed")
                        append('\n')
                    }
                }
            }
        }
    }

    /** Identifies one upload: the same file content sent to the same place. */
    fun fingerprint(repository: String, path: String, markdown: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$repository\n$path\n$markdown".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun String.singleLine(): String = lines().joinToString(" ") { it.trim() }.trim()

    private val weekdayNames = mapOf(
        DayOfWeek.MONDAY to "周一",
        DayOfWeek.TUESDAY to "周二",
        DayOfWeek.WEDNESDAY to "周三",
        DayOfWeek.THURSDAY to "周四",
        DayOfWeek.FRIDAY to "周五",
        DayOfWeek.SATURDAY to "周六",
        DayOfWeek.SUNDAY to "周日"
    )
}
