package com.nevoit.cresto.data.routine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class HabitTest {

    private val created = LocalDateTime.of(2026, 11, 3, 9, 0)
    private val shanghai = ZoneId.of("Asia/Shanghai")

    private val dailyTrackExport = """
        {
          "version": 2,
          "savedAt": "2026-07-19T15:09:35.937539",
          "habits": [
            {
              "id": "shower",
              "title": "洗澡",
              "checkIns": [
                {"id": "ci-1", "at": "2026-07-19T15:09:35.937539"},
                {"id": "ci-2", "at": "2026-07-19T22:00:00.000"},
                {"id": "ci-1", "at": "2026-07-19T15:09:35.937539"},
                {"id": "ci-3", "at": "2026-07-18T23:30:00.000Z"},
                {"at": "2026-07-17T08:00:00"},
                {"id": "broken", "at": "yesterday"}
              ]
            },
            {
              "id": "gym",
              "title": "健身",
              "completedDates": ["2026-05-26", "2026-05-27", "not a date", "2026-05-26"]
            },
            {"title": "  "}
          ]
        }
    """.trimIndent()

    @Test
    fun decodesDailyTrackExport() {
        val backup = HabitBackupCodec.decode(dailyTrackExport, created, shanghai)

        assertEquals(listOf("shower", "gym", "imported-2-未命名习惯"), backup.habits.map { it.id })
        assertEquals(listOf("洗澡", "健身", "未命名习惯"), backup.habits.map { it.title })
        assertEquals(listOf(0, 1, 2), backup.habits.map { it.sortOrder })

        val shower = backup.checkIns.filter { it.habitId == "shower" }
        // Duplicate ids collapse, unreadable times are dropped, a missing id gets a stable one.
        assertEquals(
            setOf("ci-1", "ci-2", "ci-3", "ci-shower-2026-07-17T08:00"),
            shower.map { it.id }.toSet()
        )
        assertEquals(
            LocalDateTime.of(2026, 7, 19, 15, 9, 35, 937_539_000),
            shower.first { it.id == "ci-1" }.at
        )
        // UTC times move into the local zone: 23:30Z is 07:30 the next morning in Shanghai.
        assertEquals(LocalDateTime.of(2026, 7, 19, 7, 30), shower.first { it.id == "ci-3" }.at)

        val gym = backup.checkIns.filter { it.habitId == "gym" }.sortedBy { it.at }
        assertEquals(listOf("legacy-gym-2026-05-26", "legacy-gym-2026-05-27"), gym.map { it.id })
        assertEquals(LocalDateTime.of(2026, 5, 26, 12, 0), gym.first().at)
    }

    @Test
    fun reimportYieldsTheSameIds() {
        val first = HabitBackupCodec.decode(dailyTrackExport, created, shanghai)
        val second = HabitBackupCodec.decode(dailyTrackExport, created.plusDays(1), shanghai)
        assertEquals(first.checkIns.map { it.id }, second.checkIns.map { it.id })
        assertEquals(first.habits.map { it.id }, second.habits.map { it.id })
    }

    @Test
    fun exportRoundTrips() {
        val habits = listOf(
            Habit("b", "第二", sortOrder = 1, createdAt = created),
            Habit("a", "第一", sortOrder = 0, createdAt = created)
        )
        val checkIns = listOf(
            HabitCheckIn("c2", "a", LocalDateTime.of(2026, 11, 3, 21, 0, 5)),
            HabitCheckIn("c1", "a", LocalDateTime.of(2026, 11, 3, 8, 0))
        )
        val text = HabitBackupCodec.encode(habits, checkIns, LocalDateTime.of(2026, 11, 3, 22, 0))
        assertTrue(text.contains("\"version\": 2"))

        val decoded = HabitBackupCodec.decode(text, created, shanghai)
        assertEquals(listOf("a", "b"), decoded.habits.map { it.id })
        assertEquals(listOf("c1", "c2"), decoded.checkIns.map { it.id })
        assertEquals(checkIns.sortedBy { it.at }, decoded.checkIns)
    }

    @Test
    fun acceptsABareHabitArray() {
        val backup = HabitBackupCodec.decode("""[{"id":"x","title":"喝水","checkIns":[]}]""")
        assertEquals(listOf("x"), backup.habits.map { it.id })
    }

    @Test
    fun rejectsFilesWithoutHabits() {
        assertProblem(HabitBackupException.Problem.NotJson, "not json")
        assertProblem(HabitBackupException.Problem.NoHabits, """{"todos": []}""")
        assertProblem(HabitBackupException.Problem.NoHabits, """{"habits": []}""")
        assertProblem(HabitBackupException.Problem.NoHabits, "42")
    }

    @Test
    fun summarizesTodayWeekMonthAndTotal() {
        val habit = Habit("h", "预测卡", createdAt = created)
        fun at(month: Int, day: Int, hour: Int) =
            HabitCheckIn("$month-$day-$hour", "h", LocalDateTime.of(2026, month, day, hour, 0))

        val entry = HabitEntry(
            habit,
            listOf(
                at(10, 31, 9), // Saturday before: other week, other month
                at(11, 1, 9), // Sunday: previous week, this month
                at(11, 2, 9), // Monday: this week
                at(11, 4, 9), // today
                at(11, 4, 15), // today again
                at(11, 8, 23), // Sunday: still this week
                at(11, 9, 0) // next Monday
            )
        )
        val summary = HabitStats.summarize(entry, LocalDate.of(2026, 11, 4))
        assertEquals(2, summary.todayCount)
        assertEquals(4, summary.weekCount)
        assertEquals(5, summary.monthDays)
        assertEquals(7, summary.totalCount)
        assertEquals(6, summary.totalDays)

        assertEquals(
            listOf("11-4-9", "11-4-15"),
            HabitStats.checkInsOn(entry, LocalDate.of(2026, 11, 4)).map { it.id }
        )
        assertEquals(6, HabitStats.datesWithCheckIns(entry).size)
    }

    @Test
    fun backfillKeepsTheTimeOfDay() {
        val now = LocalDateTime.of(2026, 11, 4, 21, 15, 30)
        assertEquals(now, HabitStats.checkInTimeFor(LocalDate.of(2026, 11, 4), now))
        assertEquals(
            LocalDateTime.of(2026, 11, 1, 21, 15, 30),
            HabitStats.checkInTimeFor(LocalDate.of(2026, 11, 1), now)
        )
    }

    /** Checks a real Daily-Track file without committing it: DAILY_TRACK_FILE=/path/to/file.json. */
    @Test
    fun decodesLocalDailyTrackFileWhenProvided() {
        val path = System.getenv("DAILY_TRACK_FILE")
        assumeTrue(!path.isNullOrBlank())
        val backup = HabitBackupCodec.decode(File(path!!).readText())
        val counts = backup.habits.map { habit -> backup.checkIns.count { it.habitId == habit.id } }
        println("DAILY_TRACK_FILE: ${backup.habits.size} habits, check-ins per habit $counts")
        assertTrue(backup.habits.isNotEmpty())
    }

    private fun assertProblem(expected: HabitBackupException.Problem, text: String) {
        val error = assertThrows(HabitBackupException::class.java) { HabitBackupCodec.decode(text) }
        assertEquals(expected, error.problem)
    }
}
