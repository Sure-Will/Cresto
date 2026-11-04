package com.nevoit.cresto.data.routine

import com.nevoit.cresto.data.utils.Converters
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitBackupStudyLogTimeTest {
    private val studyDay = LocalDate.of(2099, 1, 1)
    private val localEdit = LocalDateTime.of(2026, 11, 4, 20, 0)
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun missingEditTime_doesNotOverwriteAnExistingFutureDayRecord() = runBlocking {
        assertUndatedImportPreservesLocalRecord(backup())
    }

    @Test
    fun invalidEditTime_doesNotOverwriteAnExistingFutureDayRecord() = runBlocking {
        assertUndatedImportPreservesLocalRecord(backup("not-a-time"))
    }

    @Test
    fun missingEditTime_doesNotDisplaceADatedCopyWithinTheSameBackup() {
        val incoming = HabitBackupCodec.decode(
            """
            {"studyLogs":[
              {"date":"$studyDay","note":"Dated note","updatedAt":"$localEdit"},
              {"date":"$studyDay","note":"Undated note"}
            ]}
            """.trimIndent(),
            zone = zone
        ).studyLogs.single()

        assertEquals("Dated note", incoming.note)
        assertEquals(localEdit, incoming.updatedAt)
    }

    @Test
    fun readableEditTimes_stillReplaceOlderRecordsAndKeepTheirTimeZoneMeaning() = runBlocking {
        listOf("2026-11-04T21:00:00", "2026-11-04T13:00:00Z").forEach { timestamp ->
            val dao = InMemoryStudyLogDao(localLog())
            val incoming = HabitBackupCodec.decode(backup(timestamp), zone = zone).studyLogs

            assertEquals(localEdit.plusHours(1), incoming.single().updatedAt)
            assertEquals(1, dao.merge(incoming))
            assertEquals("Imported note", dao.get(studyDay)?.note)
        }
    }

    @Test
    fun undatedRecord_canStillBeImportedWhenTheDayHasNoLocalRecord() = runBlocking {
        val dao = InMemoryStudyLogDao()
        val incoming = HabitBackupCodec.decode(backup(), zone = zone).studyLogs

        assertEquals(1, dao.merge(incoming))
        assertEquals("Imported note", dao.get(studyDay)?.note)
    }

    @Test
    fun unknownEditTime_survivesRoomConversionAndBackupRoundTrip() {
        val original = HabitBackupCodec.decode(backup(), zone = zone).studyLogs.single()
        val converters = Converters()
        val storedTime = converters.dateToTimestamp(original.updatedAt)
        assertEquals(original.updatedAt, converters.fromTimestamp(storedTime))

        val exported = HabitBackupCodec.encode(emptyList(), emptyList(), listOf(original))
        val restored = HabitBackupCodec.decode(exported, zone = zone).studyLogs.single()
        assertEquals(original, restored)
        assertTrue(restored.updatedAt.isBefore(localEdit))
    }

    private suspend fun assertUndatedImportPreservesLocalRecord(json: String) {
        val local = localLog()
        val dao = InMemoryStudyLogDao(local)
        val incoming = HabitBackupCodec.decode(json, zone = zone).studyLogs

        assertTrue(
            "An unknown edit time must not become the future study date",
            incoming.single().updatedAt.isBefore(localEdit)
        )
        assertEquals(0, dao.merge(incoming))
        assertEquals(local, dao.get(studyDay))
    }

    private fun localLog() = StudyLog(studyDay, note = "Local note", updatedAt = localEdit)

    private fun backup(updatedAt: String? = null): String {
        val timeField = updatedAt?.let { ",\"updatedAt\":\"$it\"" }.orEmpty()
        return """{"studyLogs":[{"date":"$studyDay","note":"Imported note"$timeField}]}"""
    }

    /** Exercises the production DAO's merge implementation without requiring Android or Room. */
    private class InMemoryStudyLogDao(vararg initial: StudyLog) : StudyLogDao {
        private val records = initial.associateBy { it.date }.toMutableMap()

        override fun observeAll(): Flow<List<StudyLog>> = flowOf(records.values.toList())
        override suspend fun all(): List<StudyLog> = records.values.toList()
        override suspend fun get(date: LocalDate): StudyLog? = records[date]
        override suspend fun upsert(log: StudyLog) { records[log.date] = log }
        override suspend fun delete(date: LocalDate) { records.remove(date) }
        override suspend fun deleteAll() { records.clear() }
    }
}
