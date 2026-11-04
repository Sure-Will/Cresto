package com.nevoit.cresto.data.learning

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * A study plan read from a Markdown document that an agent maintains elsewhere (for example in a
 * private notes repository). The phone only reads it; see [StudyPlanParser] for the expected shape.
 */
data class StudyPlan(
    val title: String,
    val startDate: LocalDate,
    val rules: List<String>,
    val slots: List<StudySlot>,
    val weeks: List<StudyWeek>,
    val nights: List<StudyNight>
)

/** A row of the weekly structure table, e.g. "周三晚 | 电路原理 | 一道计算加一个测量预测". */
data class StudySlot(
    val label: String,
    val days: Set<DayOfWeek>,
    val isEvening: Boolean,
    val content: String,
    val output: String
) {
    /** A slot that names exactly one weekday is that day's session; wider ones are daily habits. */
    val isSession: Boolean get() = days.size == 1
}

data class StudyWeek(
    val number: Int,
    val start: LocalDate,
    val end: LocalDate,
    val tracks: List<StudyTrack>,
    val acceptance: String
) {
    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)
}

/** A week column such as "C+MCU 晚" (any evening) or "电路晚（周三）" (only that weekday). */
data class StudyTrack(
    val name: String,
    val day: DayOfWeek?,
    val text: String
)

/** A row of a "第 N 周逐晚安排" table, which replaces the generic slot for that date. */
data class StudyNight(
    val date: LocalDate,
    val label: String,
    val task: String,
    val output: String
)

enum class StudyPlanProblem {
    MissingStartDate,
    MissingWeeks,
    MissingWeekColumns,
    BadWeekDates,
    DuplicateWeekNumber
}

sealed interface StudyPlanParseResult {
    data class Success(val plan: StudyPlan) : StudyPlanParseResult
    data class Failure(val problem: StudyPlanProblem) : StudyPlanParseResult
}

/**
 * Reads the plan from these parts of the document, located by heading and table header names so
 * that column order and surrounding prose may change freely:
 *
 * - `# <title>（YYYY-MM-DD 起）`: the start date anchors the year of every `MM/DD` date.
 * - `## …规则`: a numbered list.
 * - `## 每周结构`: table with 时段 / 内容 / 产出.
 * - `## 十二周`: table with 周 / 日期 / 验收 and any number of track columns. A track whose header
 *   names a weekday (电路晚（周三）) applies only on that day; the others apply on other evenings.
 * - `## 第 N 周逐晚安排…`: optional tables with 日期 / 任务 / 留下什么.
 */
object StudyPlanParser {

    private val isoDate = Regex("""(\d{4})-(\d{1,2})-(\d{1,2})""")
    private val dateToken = Regex("""(\d{4})-(\d{1,2})-(\d{1,2})|(\d{1,2})/(\d{1,2})""")
    private val nightHeading = Regex("""第\s*\d+\s*周逐晚""")
    private val orderedItem = Regex("""^\s*\d+[.、)]\s+(.+)$""")
    private val tableSeparatorCell = Regex("""^:?-{3,}:?$""")
    private val markdownLink = Regex("""\[([^\]]*)]\([^)]*\)""")

    private val weekdayNames = listOf(
        "周一" to DayOfWeek.MONDAY,
        "周二" to DayOfWeek.TUESDAY,
        "周三" to DayOfWeek.WEDNESDAY,
        "周四" to DayOfWeek.THURSDAY,
        "周五" to DayOfWeek.FRIDAY,
        "周六" to DayOfWeek.SATURDAY,
        "周日" to DayOfWeek.SUNDAY,
        "周天" to DayOfWeek.SUNDAY
    )
    private val workdays = setOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY
    )

    fun parse(markdown: String): StudyPlanParseResult {
        val lines = markdown.replace("\r\n", "\n").lines()
        val titleLine = lines.firstOrNull { it.startsWith("# ") }?.removePrefix("# ")?.trim()
            ?: return StudyPlanParseResult.Failure(StudyPlanProblem.MissingStartDate)
        val startDate = isoDate.find(titleLine)?.let { match ->
            val (year, month, day) = match.destructured
            runCatching { LocalDate.of(year.toInt(), month.toInt(), day.toInt()) }.getOrNull()
        } ?: return StudyPlanParseResult.Failure(StudyPlanProblem.MissingStartDate)
        val title = titleLine
            .replace(Regex("""[（(][^）)]*\d{4}-\d{1,2}-\d{1,2}[^）)]*[）)]"""), "")
            .trim()
            .ifEmpty { titleLine }

        val sections = sectionsOf(lines)

        val weeksSection = sections.firstOrNull { it.heading.startsWith("十二周") }
            ?: return StudyPlanParseResult.Failure(StudyPlanProblem.MissingWeeks)
        val weeksTable = tableOf(weeksSection.body)
            ?: return StudyPlanParseResult.Failure(StudyPlanProblem.MissingWeeks)
        val weeks = when (val parsed = parseWeeks(weeksTable, startDate)) {
            is WeeksResult.Failure -> return StudyPlanParseResult.Failure(parsed.problem)
            is WeeksResult.Success -> parsed.weeks
        }

        val rules = sections.firstOrNull { it.heading.endsWith("规则") }
            ?.body
            ?.mapNotNull { orderedItem.find(it)?.groupValues?.get(1)?.let(::plainText) }
            .orEmpty()

        val slots = sections.firstOrNull { it.heading.startsWith("每周结构") }
            ?.let { tableOf(it.body) }
            ?.let(::parseSlots)
            .orEmpty()

        val nights = sections
            .filter { nightHeading.containsMatchIn(it.heading) }
            .flatMap { section -> tableOf(section.body)?.let { parseNights(it, startDate) }.orEmpty() }

        return StudyPlanParseResult.Success(
            StudyPlan(
                title = title,
                startDate = startDate,
                rules = rules,
                slots = slots,
                weeks = weeks,
                nights = nights
            )
        )
    }

    private sealed interface WeeksResult {
        data class Success(val weeks: List<StudyWeek>) : WeeksResult
        data class Failure(val problem: StudyPlanProblem) : WeeksResult
    }

    private fun parseWeeks(table: Table, startDate: LocalDate): WeeksResult {
        val numberColumn = table.header.indexOfFirst { it == "周" }
        val dateColumn = table.header.indexOfFirst { "日期" in it }
        val acceptanceColumn = table.header.indexOfFirst { "验收" in it }
        if (numberColumn < 0 || dateColumn < 0) {
            return WeeksResult.Failure(StudyPlanProblem.MissingWeekColumns)
        }
        val trackColumns = table.header.indices.filter {
            it != numberColumn && it != dateColumn && it != acceptanceColumn
        }

        // Rows are in date order, so each week is anchored to the previous one.
        var anchor = startDate
        val weekNumbers = mutableSetOf<Int>()
        val weeks = table.rows.mapNotNull { row ->
            val number = row.cell(numberColumn).filter(Char::isDigit).toIntOrNull()
                ?: return@mapNotNull null
            if (!weekNumbers.add(number)) {
                return WeeksResult.Failure(StudyPlanProblem.DuplicateWeekNumber)
            }
            val dates = datesIn(row.cell(dateColumn), anchor)
            if (dates.size < 2 || dates[1].isBefore(dates[0])) {
                return WeeksResult.Failure(StudyPlanProblem.BadWeekDates)
            }
            anchor = dates[0]
            StudyWeek(
                number = number,
                start = dates[0],
                end = dates[1],
                tracks = trackColumns.map { column ->
                    val name = table.header[column]
                    StudyTrack(
                        name = name,
                        day = weekdaysIn(name).singleOrNull(),
                        text = row.cell(column)
                    )
                },
                acceptance = if (acceptanceColumn >= 0) row.cell(acceptanceColumn) else ""
            )
        }
        if (weeks.isEmpty()) return WeeksResult.Failure(StudyPlanProblem.MissingWeeks)
        return WeeksResult.Success(weeks.sortedBy { it.start })
    }

    private fun parseSlots(table: Table): List<StudySlot> {
        val labelColumn = table.header.indexOfFirst { "时段" in it }.takeIf { it >= 0 } ?: 0
        val contentColumn = table.header.indexOfFirst { "内容" in it }
        val outputColumn = table.header.indexOfFirst { "产出" in it || "留下" in it }
        return table.rows.mapNotNull { row ->
            val label = row.cell(labelColumn)
            val days = when {
                "工作日" in label -> workdays
                "每天" in label || "每日" in label -> DayOfWeek.entries.toSet()
                else -> weekdaysIn(label)
            }
            if (days.isEmpty()) return@mapNotNull null
            StudySlot(
                label = label,
                days = days,
                isEvening = "晚" in label,
                content = if (contentColumn >= 0) row.cell(contentColumn) else "",
                output = if (outputColumn >= 0) row.cell(outputColumn) else ""
            )
        }
    }

    private fun parseNights(table: Table, startDate: LocalDate): List<StudyNight> {
        val dateColumn = table.header.indexOfFirst { "日期" in it }
        val taskColumn = table.header.indexOfFirst { "任务" in it || "内容" in it }
        val outputColumn = table.header.indexOfFirst { "留下" in it || "产出" in it }
        if (dateColumn < 0 || taskColumn < 0) return emptyList()
        return table.rows.mapNotNull { row ->
            val label = row.cell(dateColumn)
            val date = datesIn(label, startDate).firstOrNull() ?: return@mapNotNull null
            StudyNight(
                date = date,
                label = label,
                task = row.cell(taskColumn),
                output = if (outputColumn >= 0) row.cell(outputColumn) else ""
            )
        }
    }

    /**
     * Resolves every date in [text]. `MM/DD` takes the year that puts it closest to [anchor], so a
     * plan starting in November reads 01/18 as the following January. Each later date in the same
     * cell is anchored to the previous one, which keeps ranges such as 12/28–01/03 in order.
     */
    internal fun datesIn(text: String, anchor: LocalDate): List<LocalDate> {
        var previous = anchor
        return dateToken.findAll(text).mapNotNull { match ->
            val groups = match.groupValues
            val date = runCatching {
                if (groups[1].isNotEmpty()) {
                    LocalDate.of(groups[1].toInt(), groups[2].toInt(), groups[3].toInt())
                } else {
                    nearestYear(groups[4].toInt(), groups[5].toInt(), previous)
                }
            }.getOrNull()
            date?.also { previous = it }
        }.toList()
    }

    private fun nearestYear(month: Int, day: Int, anchor: LocalDate): LocalDate {
        return listOf(anchor.year - 1, anchor.year, anchor.year + 1)
            .mapNotNull { year -> runCatching { LocalDate.of(year, month, day) }.getOrNull() }
            .minBy { kotlin.math.abs(it.toEpochDay() - anchor.toEpochDay()) }
    }

    private fun weekdaysIn(text: String): Set<DayOfWeek> {
        return weekdayNames.filter { (name, _) -> name in text }.map { it.second }.toSet()
    }

    private data class Section(val heading: String, val body: List<String>)

    private fun sectionsOf(lines: List<String>): List<Section> {
        val sections = mutableListOf<Section>()
        var heading: String? = null
        var body = mutableListOf<String>()
        for (line in lines) {
            if (line.startsWith("## ")) {
                heading?.let { sections += Section(it, body) }
                heading = plainText(line.removePrefix("## "))
                body = mutableListOf()
            } else if (heading != null) {
                body += line
            }
        }
        heading?.let { sections += Section(it, body) }
        return sections
    }

    private class Table(val header: List<String>, val rows: List<List<String>>)

    private fun List<String>.cell(index: Int): String = getOrNull(index).orEmpty()

    /** The first pipe table in [body]. */
    private fun tableOf(body: List<String>): Table? {
        val start = body.indexOfFirst { it.trimStart().startsWith("|") }
        if (start < 0) return null
        val tableLines = body.drop(start).takeWhile { it.trimStart().startsWith("|") }
        val cells = tableLines.map(::cellsOf)
        if (cells.size < 2 || !cells[1].all { tableSeparatorCell.matches(it) }) return null
        return Table(
            header = cells[0].map(::plainText),
            rows = cells.drop(2).map { row -> row.map(::plainText) }
        )
    }

    private fun cellsOf(line: String): List<String> {
        return line.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }
    }

    internal fun plainText(text: String): String {
        return markdownLink.replace(text) { it.groupValues[1] }
            .replace("**", "")
            .replace("__", "")
            .replace("`", "")
            .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
            .trim()
    }
}
