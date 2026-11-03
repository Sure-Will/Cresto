package com.nevoit.cresto.data.routine

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/** Writes run on the repository's own scope so a check-in survives leaving the screen. */
class HabitRepository private constructor(context: Context) {
    private val dao = RoutineDatabase.get(context).habitDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
        scope.launch {
            dao.insertHabit(
                Habit(id = "h-${UUID.randomUUID()}", title = trimmed, sortOrder = dao.maxSortOrder() + 1)
            )
        }
    }

    fun rename(id: String, title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        scope.launch { dao.rename(id, trimmed) }
    }

    fun delete(id: String) {
        scope.launch { dao.deleteHabit(id) }
    }

    fun reorder(ids: List<String>) {
        scope.launch { dao.reorder(ids) }
    }

    fun checkIn(habitId: String, date: LocalDate = LocalDate.now()) {
        val at = HabitStats.checkInTimeFor(date, LocalDateTime.now())
        scope.launch {
            dao.insertCheckIn(HabitCheckIn(id = "ci-${UUID.randomUUID()}", habitId = habitId, at = at))
        }
    }

    fun deleteCheckIn(id: String) {
        scope.launch { dao.deleteCheckIn(id) }
    }

    /** @throws HabitBackupException when [json] is not a habit backup. */
    suspend fun import(json: String): HabitImportResult = withContext(Dispatchers.IO) {
        val backup = HabitBackupCodec.decode(json)
        dao.merge(backup.habits, backup.checkIns)
    }

    suspend fun export(): String = withContext(Dispatchers.IO) {
        HabitBackupCodec.encode(dao.habits(), dao.checkIns())
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
