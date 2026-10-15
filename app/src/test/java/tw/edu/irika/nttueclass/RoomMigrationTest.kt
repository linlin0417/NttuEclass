package tw.edu.irika.nttueclass

import androidx.room.Entity
import androidx.room.Index
import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.edu.irika.nttueclass.data.local.db.AppDatabase
import tw.edu.irika.nttueclass.data.local.db.entity.AnnouncementEntity
import tw.edu.irika.nttueclass.data.local.db.entity.RollCallRecordEntity
import tw.edu.irika.nttueclass.data.local.db.entity.TaskEntity

class RoomMigrationTest {

    @Test
    fun testMigration5To6Versions() {
        assertEquals(5, AppDatabase.MIGRATION_5_6.startVersion)
        assertEquals(6, AppDatabase.MIGRATION_5_6.endVersion)
    }

    @Test
    fun testMigration5To6ExecutesExpectedStatements() {
        val executedSql = mutableListOf<String>()

        // 使用 Proxy 模擬 SupportSQLiteDatabase 避免額外 mock 框架相依
        val handler = java.lang.reflect.InvocationHandler { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedSql.add(args[0] as String)
            }
            null
        }

        val fakeDb = java.lang.reflect.Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
            handler
        ) as SupportSQLiteDatabase

        AppDatabase.MIGRATION_5_6.migrate(fakeDb)

        assertEquals(4, executedSql.size)

        // 1. announcements(courseId)
        assertTrue(
            "Must create index on announcements(courseId)",
            executedSql.any { it.contains("index_announcements_courseId") && it.contains("`announcements`") && it.contains("`courseId`") }
        )

        // 2. announcements(date)
        assertTrue(
            "Must create index on announcements(date)",
            executedSql.any { it.contains("index_announcements_date") && it.contains("`announcements`") && it.contains("`date`") }
        )

        // 3. roll_call_records(sessionId, studentId)
        assertTrue(
            "Must create composite index on roll_call_records(sessionId, studentId)",
            executedSql.any { it.contains("index_roll_call_records_sessionId_studentId") && it.contains("`roll_call_records`") && it.contains("`sessionId`") && it.contains("`studentId`") }
        )

        // 4. tasks(dueDateTime)
        assertTrue(
            "Must create index on tasks(dueDateTime)",
            executedSql.any { it.contains("index_tasks_dueDateTime") && it.contains("`tasks`") && it.contains("`dueDateTime`") }
        )
    }

    @Test
    fun testMigrationMatchesRoomIndexConventions() {
        val executedSql = mutableListOf<String>()
        val handler = java.lang.reflect.InvocationHandler { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedSql.add(args[0] as String)
            }
            null
        }

        val fakeDb = java.lang.reflect.Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
            handler
        ) as SupportSQLiteDatabase

        AppDatabase.MIGRATION_5_6.migrate(fakeDb)

        val expectedIndices = listOf(
            Triple("index_announcements_courseId", "announcements", listOf("courseId")),
            Triple("index_announcements_date", "announcements", listOf("date")),
            Triple("index_roll_call_records_sessionId_studentId", "roll_call_records", listOf("sessionId", "studentId")),
            Triple("index_tasks_dueDateTime", "tasks", listOf("dueDateTime"))
        )

        for ((indexName, tableName, columns) in expectedIndices) {
            val matchingSql = executedSql.firstOrNull { it.contains(indexName) }
            requireNotNull(matchingSql) { "Index $indexName must be created in migration" }
            assertTrue("Index $indexName must target table `$tableName`", matchingSql.contains("`$tableName`"))
            for (col in columns) {
                assertTrue("Index $indexName must include column `$col`", matchingSql.contains("`$col`"))
            }
        }
    }
}
