package com.nevoit.cresto.data.routine

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY sortOrder, createdAt")
    fun observeHabits(): Flow<List<Habit>>

    @Query("SELECT * FROM habit_check_ins")
    fun observeCheckIns(): Flow<List<HabitCheckIn>>

    @Query("SELECT * FROM habits ORDER BY sortOrder, createdAt")
    suspend fun habits(): List<Habit>

    @Query("SELECT * FROM habit_check_ins")
    suspend fun checkIns(): List<HabitCheckIn>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM habits")
    suspend fun maxSortOrder(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertHabit(habit: Habit)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCheckIn(checkIn: HabitCheckIn)

    /** Returns -1 for each row whose id already existed. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertHabitsIgnoringExisting(habits: List<Habit>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCheckInsIgnoringExisting(checkIns: List<HabitCheckIn>): List<Long>

    @Query("UPDATE habits SET title = :title WHERE id = :id")
    suspend fun rename(id: String, title: String)

    @Query("UPDATE habits SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setSortOrder(id: String, sortOrder: Int)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun deleteHabitRow(id: String)

    @Query("DELETE FROM habit_check_ins WHERE habitId = :habitId")
    suspend fun deleteCheckInsOf(habitId: String)

    @Query("DELETE FROM habit_check_ins WHERE id = :id")
    suspend fun deleteCheckIn(id: String)

    @Transaction
    suspend fun deleteHabit(id: String) {
        deleteCheckInsOf(id)
        deleteHabitRow(id)
    }

    @Transaction
    suspend fun reorder(ids: List<String>) {
        ids.forEachIndexed { index, id -> setSortOrder(id, index) }
    }

    /** Adds unknown habits after the existing ones and merges check-ins by id. */
    @Transaction
    suspend fun merge(habits: List<Habit>, checkIns: List<HabitCheckIn>): HabitImportResult {
        val base = maxSortOrder() + 1
        val addedHabits = insertHabitsIgnoringExisting(
            habits.mapIndexed { index, habit -> habit.copy(sortOrder = base + index) }
        ).count { it != -1L }
        val addedCheckIns = insertCheckInsIgnoringExisting(checkIns).count { it != -1L }
        return HabitImportResult(
            habitsAdded = addedHabits,
            habitsMerged = habits.size - addedHabits,
            checkInsAdded = addedCheckIns
        )
    }
}

data class HabitImportResult(
    val habitsAdded: Int,
    val habitsMerged: Int,
    val checkInsAdded: Int
)
