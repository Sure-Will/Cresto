package com.nevoit.cresto.data.routine

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/**
 * Owns everything in routine.db: habits and study records. Writes run on the repository's own
 * scope so a check-in or a note survives leaving the screen.
 */
class HabitRepository private constructor(context: Context) {
    private val database = RoutineDatabase.get(context)
    private val dao = database.habitDao()
    private val studyLogDao = database.studyLogDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()

    // Enqueue before returning to the caller, so clearing cannot be overtaken by an earlier write.
    private fun write(action: suspend () -> Unit) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            writeMutex.withLock { withContext(Dispatchers.IO) { action() } }
        }
    }

    val entries: Flow<List<HabitEntry>> =
        combine(dao.observeHabits(), dao.observeCheckIns()) { habits, checkIns ->
            val byHabit = checkIns.groupBy { it.habitId }
            habits.map { habit ->
                HabitEntry(habit, byHabit[habit.id].orEmpty().sortedBy { it.at })
            }
        }

    fun addHabit(title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        write {
            dao.insertHabit(
                Habit(id = "h-${UUID.randomUUID()}", title = trimmed, sortOrder = dao.maxSortOrder() + 1)
            )
        }
    }

    fun rename(id: String, title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        write { dao.rename(id, trimmed) }
    }

    fun delete(id: String) {
        write { dao.deleteHabit(id) }
    }

    fun reorder(ids: List<String>) {
        write { dao.reorder(ids) }
    }

    fun checkIn(habitId: String, date: LocalDate = LocalDate.now()) {
        val at = HabitStats.checkInTimeFor(date, LocalDateTime.now())
        write {
            dao.insertCheckIn(HabitCheckIn(id = "ci-${UUID.randomUUID()}", habitId = habitId, at = at))
        }
    }

    fun deleteCheckIn(id: String) {
        write { dao.deleteCheckIn(id) }
    }

    val studyLogs: Flow<List<StudyLog>> = studyLogDao.observeAll()

    fun setStudyDone(date: LocalDate, done: Boolean, task: String, strand: String) {
        updateStudyLog(date, task, strand) { it.copy(done = done) }
    }

    fun setStudyNote(date: LocalDate, note: String, task: String, strand: String) {
        updateStudyLog(date, task, strand) { it.copy(note = note) }
    }

    /**
     * The first non-blank task and strand stay: a note is about what the plan asked for when it
     * was written, even if the plan is later moved to other dates.
     */
    private fun updateStudyLog(
        date: LocalDate,
        task: String,
        strand: String,
        change: (StudyLog) -> StudyLog
    ) {
        val now = LocalDateTime.now()
        write {
            database.withTransaction {
                val current = studyLogDao.get(date) ?: StudyLog(date = date, updatedAt = now)
                val updated = change(current).copy(
                    task = current.task.ifBlank { task },
                    strand = current.strand.ifBlank { strand },
                    updatedAt = now
                )
                if (updated.isEmpty) studyLogDao.delete(date) else studyLogDao.upsert(updated)
            }
        }
    }

    /** @throws HabitBackupException when [json] is not a habit backup. */
    suspend fun import(json: String): HabitImportResult = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            val backup = HabitBackupCodec.decode(json)
            database.withTransaction {
                dao.merge(backup.habits, backup.checkIns)
                    .copy(studyLogsChanged = studyLogDao.merge(backup.studyLogs))
            }
        }
    }

    suspend fun export(): String = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            HabitBackupCodec.encode(dao.habits(), dao.checkIns(), studyLogDao.all())
        }
    }

    suspend fun clearAll() = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                dao.clearAll()
                studyLogDao.deleteAll()
            }
        }
    }

    companion object {
        @Volatile
        private var instance: HabitRepository? = null

        fun get(context: Context): HabitRepository =
            instance ?: synchronized(this) {
                instance ?: HabitRepository(context).also { instance = it }
            }
    }
}
