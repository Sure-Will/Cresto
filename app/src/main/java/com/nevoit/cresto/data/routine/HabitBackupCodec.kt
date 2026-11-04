package com.nevoit.cresto.data.routine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

data class HabitBackup(
    val habits: List<Habit>,
    val checkIns: List<HabitCheckIn>,
    val studyLogs: List<StudyLog> = emptyList()
)

class HabitBackupException(val problem: Problem) : Exception(problem.name) {
    enum class Problem { NotJson, NoHabits }
}

/**
 * Reads and writes the Daily-Track backup format, so either app can restore the other's export:
 * `{"version": 2, "savedAt": …, "habits": [{"id", "title", "checkIns": [{"id", "at"}]}]}`.
 * Times are local ISO date-times; older Daily-Track files list days in `completedDates`.
 * Cresto adds a top-level `studyLogs` list, which Daily-Track ignores.
 */
object HabitBackupCodec {
    private const val UNTITLED = "未命名习惯"

    private val json = Json { prettyPrint = true }

    fun decode(
        text: String,
        createdAt: LocalDateTime = LocalDateTime.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): HabitBackup {
        val root = runCatching { Json.parseToJsonElement(text) }.getOrElse {
            throw HabitBackupException(HabitBackupException.Problem.NotJson)
        }
        val items = when (root) {
            is JsonObject -> root["habits"] as? JsonArray
            is JsonArray -> root
            else -> null
        }
        val studyLogs = ((root as? JsonObject)?.get("studyLogs") as? JsonArray)
            ?.let { decodeStudyLogs(it, zone) }
            .orEmpty()
        if (items == null && studyLogs.isEmpty()) {
            throw HabitBackupException(HabitBackupException.Problem.NoHabits)
        }

        val habits = mutableListOf<Habit>()
        val checkIns = LinkedHashMap<String, HabitCheckIn>()
        items.orEmpty().filterIsInstance<JsonObject>().forEachIndexed { index, item ->
            val title = item.string("title")?.trim()?.ifEmpty { null } ?: UNTITLED
            val id = item.string("id")?.trim()?.ifEmpty { null } ?: "imported-$index-$title"
            if (habits.any { it.id == id }) return@forEachIndexed
            habits += Habit(id = id, title = title, sortOrder = habits.size, createdAt = createdAt)

            val rawCheckIns = item["checkIns"] as? JsonArray
            if (rawCheckIns != null) {
                rawCheckIns.filterIsInstance<JsonObject>().forEach { raw ->
                    val at = raw.string("at")?.let { parseTime(it, zone) } ?: return@forEach
                    val checkInId = raw.string("id")?.trim()?.ifEmpty { null } ?: "ci-$id-$at"
                    checkIns.putIfAbsent(checkInId, HabitCheckIn(checkInId, id, at))
                }
            } else {
                (item["completedDates"] as? JsonArray).orEmpty().forEach { raw ->
                    val day = (raw as? JsonPrimitive)?.contentOrNull?.trim() ?: return@forEach
                    val date = runCatching { LocalDate.parse(day) }.getOrNull() ?: return@forEach
                    val checkInId = "legacy-$id-$date"
                    checkIns.putIfAbsent(checkInId, HabitCheckIn(checkInId, id, date.atTime(12, 0)))
                }
            }
        }
        if (habits.isEmpty() && studyLogs.isEmpty()) {
            throw HabitBackupException(HabitBackupException.Problem.NoHabits)
        }
        return HabitBackup(habits, checkIns.values.toList(), studyLogs)
    }

    /** A day without a readable edit time counts as the oldest copy, so it never overwrites. */
    private fun decodeStudyLogs(items: JsonArray, zone: ZoneId): List<StudyLog> {
        val byDate = LinkedHashMap<LocalDate, StudyLog>()
        items.filterIsInstance<JsonObject>().forEach { item ->
            val date = item.string("date")?.trim()
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: return@forEach
            val log = StudyLog(
                date = date,
                done = (item["done"] as? JsonPrimitive)?.booleanOrNull ?: false,
                note = item.string("note").orEmpty(),
                task = item.string("task").orEmpty(),
                strand = item.string("strand").orEmpty(),
                updatedAt = item.string("updatedAt")?.let { parseTime(it, zone) }
                    ?: date.atStartOfDay()
            )
            if (log.isEmpty) return@forEach
            val existing = byDate[date]
            if (existing == null || log.updatedAt.isAfter(existing.updatedAt)) byDate[date] = log
        }
        return byDate.values.toList()
    }

    fun encode(
        habits: List<Habit>,
        checkIns: List<HabitCheckIn>,
        studyLogs: List<StudyLog> = emptyList(),
        savedAt: LocalDateTime = LocalDateTime.now()
    ): String {
        val byHabit = checkIns.groupBy { it.habitId }
        val root = buildJsonObject {
            put("version", 2)
            put("savedAt", savedAt.toString())
            putJsonArray("habits") {
                habits.sortedBy { it.sortOrder }.forEach { habit ->
                    addJsonObject {
                        put("id", habit.id)
                        put("title", habit.title)
                        putJsonArray("checkIns") {
                            byHabit[habit.id].orEmpty().sortedBy { it.at }.forEach { checkIn ->
                                addJsonObject {
                                    put("id", checkIn.id)
                                    put("at", checkIn.at.toString())
                                }
                            }
                        }
                    }
                }
            }
            if (studyLogs.isNotEmpty()) {
                putJsonArray("studyLogs") {
                    studyLogs.sortedBy { it.date }.forEach { log ->
                        addJsonObject {
                            put("date", log.date.toString())
                            put("done", log.done)
                            put("note", log.note)
                            put("task", log.task)
                            put("strand", log.strand)
                            put("updatedAt", log.updatedAt.toString())
                        }
                    }
                }
            }
        }
        return json.encodeToString(JsonElement.serializer(), root)
    }

    /** Local ISO times are kept as written; times with an offset are moved into [zone]. */
    private fun parseTime(value: String, zone: ZoneId): LocalDateTime? {
        val trimmed = value.trim()
        return runCatching { LocalDateTime.parse(trimmed) }.getOrNull()
            ?: runCatching {
                OffsetDateTime.parse(trimmed).atZoneSameInstant(zone).toLocalDateTime()
            }.getOrNull()
            ?: runCatching { LocalDate.parse(trimmed).atTime(12, 0) }.getOrNull()
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()
}
