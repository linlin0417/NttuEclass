package tw.edu.irika.nttueclass.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import tw.edu.irika.nttueclass.data.local.db.dao.AnnouncementDao
import tw.edu.irika.nttueclass.data.local.db.dao.CourseDao
import tw.edu.irika.nttueclass.data.local.db.dao.CourseMaterialDao
import tw.edu.irika.nttueclass.data.local.db.dao.TaskDao
import tw.edu.irika.nttueclass.data.local.db.dao.TimetableDao
import tw.edu.irika.nttueclass.data.local.security.SecureCredentialStorage
import tw.edu.irika.nttueclass.domain.repository.IAnnouncementRepository
import tw.edu.irika.nttueclass.domain.repository.ICourseRepository
import tw.edu.irika.nttueclass.domain.repository.IDataSyncCoordinator
import tw.edu.irika.nttueclass.domain.repository.ITaskRepository
import tw.edu.irika.nttueclass.domain.repository.ITimetableRepository
import tw.edu.irika.nttueclass.notification.ClassReminderScheduler
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataSyncCoordinator @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val timetableRepository: ITimetableRepository,
    private val courseRepository: ICourseRepository,
    private val announcementRepository: IAnnouncementRepository,
    private val taskRepository: ITaskRepository,
    private val timetableDao: TimetableDao,
    private val courseDao: CourseDao,
    private val announcementDao: AnnouncementDao,
    private val taskDao: TaskDao,
    private val courseMaterialDao: CourseMaterialDao,
    private val storage: SecureCredentialStorage
) : IDataSyncCoordinator {

    override suspend fun syncAllData(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 1. 先同步課表（取得最詳盡之節次、教室與授課教師資料）
            val timetableSuccess = timetableRepository.syncTimetable()
            // 2. 再同步課程列表（由最新課表資料互補缺少的選修課與授課教師）
            val coursesSuccess = courseRepository.syncCourses()
            // 3. 雙向互補：課表與課程資料庫中的授課教師與教室資訊進行最終校準補齊
            courseRepository.crossEnrichCoursesAndTimetable()

            // 4. 並行同步公告與作業
            val (announcementsSuccess, tasksSuccess) = coroutineScope {
                val announcementsJob = async { announcementRepository.syncAnnouncements() }
                val tasksJob = async { taskRepository.syncTasks() }
                Pair(announcementsJob.await(), tasksJob.await())
            }
            courseRepository.updateCourseCounters()

            // 5. 若課表同步成功，重新排程課前提醒
            if (timetableSuccess) {
                try {
                    val slots = timetableDao.getAllSlotsList().map { it.toDomain() }
                    ClassReminderScheduler.rescheduleAll(context, slots)
                } catch (_: Exception) {
                }
            }

            // 6. 真實成功檢核：若全部模組均未獲取到任何有效伺服器資料，判定為同步未達成
            if (!timetableSuccess && !coursesSuccess && !announcementsSuccess && !tasksSuccess) {
                throw IOException("無法從學校伺服器取得最新資料，請檢查網路連線")
            }

            storage.saveLastSyncTimestamp(System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun clearAllData(): Unit = withContext(Dispatchers.IO) {
        timetableDao.deleteAll()
        courseDao.deleteAll()
        announcementDao.deleteAll()
        taskDao.deleteAll()
        courseMaterialDao.deleteAll()
        try {
            val baseDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
            File(baseDir, "materials").deleteRecursively()
        } catch (_: Exception) {
        }
        try {
            for (day in 1..7) {
                for (period in 1..14) {
                    ClassReminderScheduler.cancelClassReminder(context, day, period)
                }
            }
        } catch (_: Exception) {
        }
        Unit
    }
}
