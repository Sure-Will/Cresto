package com.nevoit.cresto.data.routine

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.nevoit.cresto.data.utils.Converters

/**
 * Kept apart from the todo database: that one follows upstream versions and falls back to a
 * destructive migration, which must never touch habit history. Every version bump needs an
 * automatic or explicit migration here.
 */
@Database(
    entities = [Habit::class, HabitCheckIn::class, StudyLog::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)]
)
@TypeConverters(Converters::class)
abstract class RoutineDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun studyLogDao(): StudyLogDao

    companion object {
        @Volatile
        private var instance: RoutineDatabase? = null

        fun get(context: Context): RoutineDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context, NAME).also { instance = it }
            }

        internal const val NAME = "routine.db"

        internal fun build(context: Context, name: String): RoutineDatabase =
            Room.databaseBuilder(context.applicationContext, RoutineDatabase::class.java, name).build()
    }
}
