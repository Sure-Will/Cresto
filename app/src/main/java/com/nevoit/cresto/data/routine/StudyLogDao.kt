package com.nevoit.cresto.data.routine

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface StudyLogDao {
    @Query("SELECT * FROM study_logs ORDER BY date")
    fun observeAll(): Flow<List<StudyLog>>

    @Query("SELECT * FROM study_logs ORDER BY date")
    suspend fun all(): List<StudyLog>

    @Query("SELECT * FROM study_logs WHERE date = :date")
    suspend fun get(date: LocalDate): StudyLog?

    @Upsert
    suspend fun upsert(log: StudyLog)

    @Query("DELETE FROM study_logs WHERE date = :date")
    suspend fun delete(date: LocalDate)

    @Query("DELETE FROM study_logs")
    suspend fun deleteAll()

    /**
     * Keeps whichever copy of each day was edited last; returns how many days changed. A newer
     * copy without a task or strand keeps the one already recorded for that day.
     */
    @Transaction
    suspend fun merge(logs: List<StudyLog>): Int {
        var changed = 0
        logs.forEach { incoming ->
            val existing = get(incoming.date)
            if (existing == null || incoming.updatedAt.isAfter(existing.updatedAt)) {
                upsert(
                    incoming.copy(
                        task = incoming.task.ifBlank { existing?.task.orEmpty() },
                        strand = incoming.strand.ifBlank { existing?.strand.orEmpty() }
                    )
                )
                changed++
            }
        }
        return changed
    }
}
