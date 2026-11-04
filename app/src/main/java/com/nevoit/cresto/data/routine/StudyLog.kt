package com.nevoit.cresto.data.routine

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * What happened on one study evening. [task] and [strand] copy what the plan asked for when the
 * record was written, so old notes still read correctly after the plan file changes.
 */
@Entity(tableName = "study_logs")
data class StudyLog(
    @PrimaryKey val date: LocalDate,
    val done: Boolean = false,
    val note: String = "",
    val task: String = "",
    val strand: String = "",
    val updatedAt: LocalDateTime
) {
    val isEmpty: Boolean get() = !done && note.isBlank()
}
