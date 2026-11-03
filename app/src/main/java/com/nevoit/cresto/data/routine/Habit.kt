package com.nevoit.cresto.data.routine

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

/** A habit counted by timestamped check-ins; ids are strings so Daily-Track imports keep theirs. */
@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey
    val id: String,
    val title: String,
    val sortOrder: Int = 0,
    val createdAt: LocalDateTime = LocalDateTime.now()
)

/** One check-in at local wall-clock time; a habit may have several per day. */
@Entity(
    tableName = "habit_check_ins",
    indices = [Index(value = ["habitId"])]
)
data class HabitCheckIn(
    @PrimaryKey
    val id: String,
    val habitId: String,
    val at: LocalDateTime
)

data class HabitEntry(
    val habit: Habit,
    val checkIns: List<HabitCheckIn>
)
