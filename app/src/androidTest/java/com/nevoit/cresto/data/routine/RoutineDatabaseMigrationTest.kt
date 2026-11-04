package com.nevoit.cresto.data.routine

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Opens a database written by version 1 (habits only) with the current schema, in a separate
 * file so the app's own routine.db is never touched.
 */
@RunWith(AndroidJUnit4::class)
class RoutineDatabaseMigrationTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var database: RoutineDatabase? = null

    @Before
    fun removeOldCopy() {
        context.deleteDatabase(NAME)
    }

    @After
    fun cleanUp() {
        database?.close()
        context.deleteDatabase(NAME)
    }

    @Test
    fun version1HabitsSurviveAndStudyLogsBecomeAvailable() = runBlocking {
        writeVersion1()

        val db = RoutineDatabase.build(context, NAME).also { database = it }
        assertEquals(listOf("habit-1"), db.habitDao().habits().map { it.id })
        assertEquals(listOf("check-in-1"), db.habitDao().checkIns().map { it.id })

        val log = StudyLog(
            date = LocalDate.of(2026, 11, 4),
            done = true,
            note = "迁移后写入",
            updatedAt = LocalDateTime.of(2026, 11, 4, 22, 0)
        )
        db.studyLogDao().upsert(log)
        assertEquals(listOf(log), db.studyLogDao().all())
    }

    @Test
    fun mergeKeepsTheLatestEditOfEachDay() = runBlocking {
        val db = RoutineDatabase.build(context, NAME).also { database = it }
        val dao = db.studyLogDao()
        val day = LocalDate.of(2026, 11, 4)
        val local = StudyLog(day, note = "本机", task = "第一课", strand = "副线", updatedAt = day.atTime(22, 0))
        dao.upsert(local)

        val older = local.copy(note = "旧备份", updatedAt = day.atTime(21, 0))
        val otherDay = StudyLog(day.plusDays(1), done = true, updatedAt = day.plusDays(1).atTime(20, 0))
        assertEquals(1, dao.merge(listOf(older, otherDay)))
        assertEquals("本机", dao.get(day)?.note)

        val newer = StudyLog(day, note = "新备份", updatedAt = day.atTime(23, 0))
        assertEquals(1, dao.merge(listOf(newer)))
        assertEquals(newer.copy(task = "第一课", strand = "副线"), dao.get(day))

        dao.deleteAll()
        assertNull(dao.get(day))
    }

    /** The version 1 schema exactly as exported in schemas/.../1.json. */
    private fun writeVersion1() {
        val file = context.getDatabasePath(NAME)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `habits` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                    "`sortOrder` INTEGER NOT NULL, `createdAt` TEXT NOT NULL, PRIMARY KEY(`id`))"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `habit_check_ins` (`id` TEXT NOT NULL, " +
                    "`habitId` TEXT NOT NULL, `at` TEXT NOT NULL, PRIMARY KEY(`id`))"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_habit_check_ins_habitId` " +
                    "ON `habit_check_ins` (`habitId`)"
            )
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id,identity_hash) " +
                    "VALUES(42, 'd1770745ff2e0edc71e06b779bcfe03f')"
            )
            db.execSQL(
                "INSERT INTO habits VALUES ('habit-1', 'Synthetic habit', 0, '2026-11-01T09:00')"
            )
            db.execSQL(
                "INSERT INTO habit_check_ins VALUES ('check-in-1', 'habit-1', '2026-11-02T08:00')"
            )
            db.version = 1
        }
    }

    private companion object {
        const val NAME = "routine-migration-test.db"
    }
}
