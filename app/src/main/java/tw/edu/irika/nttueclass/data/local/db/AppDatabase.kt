package tw.edu.irika.nttueclass.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import tw.edu.irika.nttueclass.data.local.db.dao.AnnouncementDao
import tw.edu.irika.nttueclass.data.local.db.dao.CourseDao
import tw.edu.irika.nttueclass.data.local.db.dao.CourseMaterialDao
import tw.edu.irika.nttueclass.data.local.db.dao.RollCallRecordDao
import tw.edu.irika.nttueclass.data.local.db.dao.TaskDao
import tw.edu.irika.nttueclass.data.local.db.dao.TimetableDao
import tw.edu.irika.nttueclass.data.local.db.entity.AnnouncementEntity
import tw.edu.irika.nttueclass.data.local.db.entity.CourseEntity
import tw.edu.irika.nttueclass.data.local.db.entity.CourseMaterialEntity
import tw.edu.irika.nttueclass.data.local.db.entity.RollCallRecordEntity
import tw.edu.irika.nttueclass.data.local.db.entity.TaskEntity
import tw.edu.irika.nttueclass.data.local.db.entity.TimetableSlotEntity

@Database(
    entities = [
        CourseEntity::class,
        TimetableSlotEntity::class,
        AnnouncementEntity::class,
        TaskEntity::class,
        RollCallRecordEntity::class,
        CourseMaterialEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun courseDao(): CourseDao
    abstract fun timetableDao(): TimetableDao
    abstract fun announcementDao(): AnnouncementDao
    abstract fun taskDao(): TaskDao
    abstract fun rollCallRecordDao(): RollCallRecordDao
    abstract fun courseMaterialDao(): CourseMaterialDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_announcements_courseId` ON `announcements` (`courseId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_announcements_date` ON `announcements` (`date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_roll_call_records_sessionId_studentId` ON `roll_call_records` (`sessionId`, `studentId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_dueDateTime` ON `tasks` (`dueDateTime`)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "nttu_eclass.db"
                )
                    .addMigrations(MIGRATION_5_6)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
