package tw.edu.irika.nttueclass.data.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.Request
import tw.edu.irika.nttueclass.data.local.db.AppDatabase
import tw.edu.irika.nttueclass.data.local.db.entity.AnnouncementEntity
import tw.edu.irika.nttueclass.data.local.db.entity.CourseEntity
import tw.edu.irika.nttueclass.data.local.db.entity.TaskEntity
import tw.edu.irika.nttueclass.data.local.db.entity.TimetableSlotEntity
import tw.edu.irika.nttueclass.data.local.security.SecureCredentialStorage
import tw.edu.irika.nttueclass.data.remote.client.NttuHttpClient
import tw.edu.irika.nttueclass.data.remote.parser.AnnouncementHtmlParser
import tw.edu.irika.nttueclass.data.remote.parser.CourseHtmlParser
import tw.edu.irika.nttueclass.data.remote.parser.TaskHtmlParser
import tw.edu.irika.nttueclass.data.remote.parser.TimetableHtmlParser
import tw.edu.irika.nttueclass.domain.model.AcademicTermHelper
import tw.edu.irika.nttueclass.domain.model.Announcement
import tw.edu.irika.nttueclass.domain.model.Course
import tw.edu.irika.nttueclass.domain.model.TaskItem
import tw.edu.irika.nttueclass.domain.model.TaskStatus
import tw.edu.irika.nttueclass.domain.model.TaskType
import tw.edu.irika.nttueclass.domain.model.TimetableSlot

class EclassRepository(context: Context) {
    private val database = AppDatabase.getInstance(context)
    private val client = NttuHttpClient.client
    private val storage = SecureCredentialStorage(context)

    // ==========================================
    // 離線優先資料流 (Offline-First Flows)
    // 嚴格安全守衛：若使用者未登入，強制輸出空列表，杜絕歷史殘留資料洩漏
    // ==========================================
    fun getTimetableStream(): Flow<List<TimetableSlot>> {
        return database.timetableDao().getAllSlots().map { list ->
            if (!storage.isLoggedIn()) emptyList() else list.map { it.toDomain() }
        }
    }

    fun getCoursesStream(): Flow<List<Course>> {
        return database.courseDao().getAllCourses().map { list ->
            if (!storage.isLoggedIn()) emptyList() else list.map { it.toDomain() }
        }
    }

    fun getAnnouncementsStream(): Flow<List<Announcement>> {
        return database.announcementDao().getAllAnnouncements().map { list ->
            if (!storage.isLoggedIn()) emptyList() else list.map { it.toDomain() }
        }
    }

    fun getTasksStream(): Flow<List<TaskItem>> {
        return database.taskDao().getAllTasks().map { list ->
            if (!storage.isLoggedIn()) emptyList() else list.map { it.toDomain() }
        }
    }

    // ==========================================
    // 遠端同步與快取寫入 (Sync from Remote)
    // ==========================================
    suspend fun syncAllData(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            syncTimetable()
            syncCourses()
            syncAnnouncements()
            syncTasks()
            updateCourseCounters()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun syncTimetable() {
        val request = Request.Builder()
            .url("${NttuHttpClient.BASE_URL}/app/course/schedule.php")
            .build()
        val response = client.newCall(request).execute()
        val html = response.body?.string() ?: return
        val slots = TimetableHtmlParser.parse(html)
        database.timetableDao().deleteAll()
        if (slots.isNotEmpty()) {
            database.timetableDao().insertAll(slots.map { TimetableSlotEntity.fromDomain(it) })
        }
    }

    private suspend fun syncCourses() {
        val request = Request.Builder()
            .url("${NttuHttpClient.BASE_URL}/app/course/")
            .build()
        val response = try { client.newCall(request).execute() } catch (_: Exception) { null }
        val html = response?.body?.string() ?: ""
        val parsedCourses = if (html.isNotBlank()) CourseHtmlParser.parse(html) else emptyList()

        // 從課表插槽互補提取課程，確保離線與任何網路頁面結構變更下選修課均不遺漏
        val slots = database.timetableDao().getAllSlotsList()
        val currentSem = AcademicTermHelper.getCurrentSemesterCode()
        val timetableCourses = slots.map { slot ->
            Course(
                id = slot.courseId,
                code = "",
                name = slot.courseName,
                instructor = slot.instructor,
                classroom = slot.classroom,
                credits = 0,
                semester = currentSem
            )
        }.distinctBy { it.name }

        // 合併兩者：以 parsedCourses 為主，timetableCourses 補充遺漏之課程或細節
        val mergedCourses = if (parsedCourses.isEmpty()) {
            timetableCourses
        } else {
            val parsedNameSet = parsedCourses.map { it.name }.toSet()
            val extraCourses = timetableCourses.filterNot { parsedNameSet.contains(it.name) }
            val enrichedParsed = parsedCourses.map { c ->
                val matchingSlot = slots.firstOrNull { it.courseName == c.name }
                c.copy(
                    instructor = c.instructor.ifBlank { matchingSlot?.instructor.orEmpty() },
                    classroom = c.classroom.ifBlank { matchingSlot?.classroom.orEmpty() }
                )
            }
            enrichedParsed + extraCourses
        }

        database.courseDao().deleteAll()
        if (mergedCourses.isNotEmpty()) {
            database.courseDao().insertAll(mergedCourses.map { CourseEntity.fromDomain(it) })
        }
    }

    private suspend fun syncAnnouncements() {
        val request = Request.Builder()
            .url("${NttuHttpClient.BASE_URL}/app/bulletin/")
            .build()
        val response = client.newCall(request).execute()
        val html = response.body?.string() ?: return
        val announcements = AnnouncementHtmlParser.parse(html)
        database.announcementDao().deleteAll()
        if (announcements.isNotEmpty()) {
            database.announcementDao().insertAll(announcements.map { AnnouncementEntity.fromDomain(it) })
        }
    }

    private suspend fun syncTasks() {
        val homeworkReq = Request.Builder()
            .url("${NttuHttpClient.BASE_URL}/app/homework/")
            .build()
        val homeworkResp = client.newCall(homeworkReq).execute()
        val homeworkHtml = homeworkResp.body?.string() ?: ""
        val assignments = TaskHtmlParser.parse(homeworkHtml, defaultType = TaskType.ASSIGNMENT)

        val examReq = Request.Builder()
            .url("${NttuHttpClient.BASE_URL}/app/exam/")
            .build()
        val examResp = client.newCall(examReq).execute()
        val examHtml = examResp.body?.string() ?: ""
        val exams = TaskHtmlParser.parse(examHtml, defaultType = TaskType.QUIZ)

        val allTasks = assignments + exams
        database.taskDao().deleteAll()
        if (allTasks.isNotEmpty()) {
            database.taskDao().insertAll(allTasks.map { TaskEntity.fromDomain(it) })
        }
    }

    /**
     * 動態統計每門課程對應之未讀公告數與待繳作業數，並更新寫入 Room 資料庫
     */
    private suspend fun updateCourseCounters() {
        val courses = database.courseDao().getAllCoursesList()
        if (courses.isEmpty()) return

        val announcements = database.announcementDao().getAllAnnouncementsList()
        val tasks = database.taskDao().getAllTasksList()

        val updatedCourses = courses.map { course ->
            val unreadCount = announcements.count { announcement ->
                announcement.isUnread && (announcement.courseName.contains(course.name) ||
                        course.name.contains(announcement.courseName) ||
                        announcement.courseId == course.id)
            }
            val pendingCount = tasks.count { task ->
                !task.isSubmitted && (task.courseName.contains(course.name) ||
                        course.name.contains(task.courseName) ||
                        task.courseId == course.id)
            }
            course.copy(
                unreadAnnouncementsCount = unreadCount,
                pendingTasksCount = pendingCount
            )
        }
        database.courseDao().insertAll(updatedCourses)
    }

    /**
     * 清空本地所有快取資料（用於使用者登出時維護資訊安全）
     */
    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        database.timetableDao().deleteAll()
        database.courseDao().deleteAll()
        database.announcementDao().deleteAll()
        database.taskDao().deleteAll()
    }
}
