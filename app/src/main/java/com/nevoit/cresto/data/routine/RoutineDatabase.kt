package com.nevoit.cresto.data.routine

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.nevoit.cresto.data.utils.Converters

/**
 * Kept apart from the todo database: that one follows upstream versions and falls back to a
 * destructive migration, which must never touch habit history. Add explicit migrations here.
 */
@Database(
    entities = [Habit::class, HabitCheckIn::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class RoutineDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao

    companion object {
        @Volatile
        private var instance: RoutineDatabase? = null

        fun get(context: Context): RoutineDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    RoutineDatabase::class.java,
                    "routine.db"
                ).build().also { instance = it }
            }
    }
}
