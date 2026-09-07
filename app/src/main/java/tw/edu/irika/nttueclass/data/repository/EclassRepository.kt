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
        val urls = listOf(
            "${NttuHttpClient.BASE_URL}/dashboard/myTimeTable",
            "${NttuHttpClient.BASE_URL}/schedule",
            "${NttuHttpClient.BASE_URL}/app/course/schedule.php"
        )
        for (url in urls) {
            try {
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val html = response.body?.string().orEmpty()
                    val slots = TimetableHtmlParser.parse(html)
                    if (slots.isNotEmpty()) {
                        // 若本地現有課程已有教師資料，自動對照補齊課表缺少的授課教師
                        val existingCourses = database.courseDao().getAllCoursesList()
                        val enrichedSlots = slots.map { slot ->
                            if (slot.instructor.isBlank()) {
                                val match = existingCourses.firstOrNull { it.id == slot.courseId || it.name == slot.courseName }
                                if (match != null && match.instructor.isNotBlank()) {
                                    slot.copy(instructor = match.instructor)
                                } else slot
                            } else slot
                        }

                        database.timetableDao().deleteAll()
                        database.timetableDao().insertAll(enrichedSlots.map { TimetableSlotEntity.fromDomain(it) })
                        break
                    }
                }
            } catch (_: Exception) {
                // 防禦性容錯，嘗試下一個可能路徑
            }
        }
    }

    private suspend fun syncCourses() {
        val urls = listOf(
            "${NttuHttpClient.BASE_URL}/dashboard",
            "${NttuHttpClient.BASE_URL}/course",
            "${NttuHttpClient.BASE_URL}/app/course/"
        )
        var parsedCourses: List<Course> = emptyList()
        for (url in urls) {
            try {
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val html = response.body?.string().orEmpty()
                    val list = CourseHtmlParser.parse(html)
                    if (list.isNotEmpty()) {
                        parsedCourses = list
                        break
                    }
                }
            } catch (_: Exception) {
            }
        }

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

        // 僅在獲取到有效課程時更新，絕不因網路異常抹除現有課表建立的課程
        if (mergedCourses.isNotEmpty()) {
            database.courseDao().deleteAll()
            database.courseDao().insertAll(mergedCourses.map { CourseEntity.fromDomain(it) })
        }
    }

    private suspend fun syncAnnouncements() {
        val urls = listOf(
            "${NttuHttpClient.BASE_URL}/dashboard/latestBulletin",
            "${NttuHttpClient.BASE_URL}/bulletin",
            "${NttuHttpClient.BASE_URL}/app/bulletin/"
        )
        for (url in urls) {
            try {
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val html = response.body?.string().orEmpty()
                    val announcements = AnnouncementHtmlParser.parse(html)
                    if (announcements.isNotEmpty()) {
                        database.announcementDao().deleteAll()
                        database.announcementDao().insertAll(announcements.map { AnnouncementEntity.fromDomain(it) })
                        break
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun syncTasks() {
        val homeworkUrls = listOf(
            "${NttuHttpClient.BASE_URL}/homework",
            "${NttuHttpClient.BASE_URL}/app/homework/"
        )
        var assignments: List<TaskItem> = emptyList()
        for (url in homeworkUrls) {
            try {
                val homeworkReq = Request.Builder().url(url).build()
                val homeworkResp = client.newCall(homeworkReq).execute()
                if (homeworkResp.isSuccessful) {
                    val homeworkHtml = homeworkResp.body?.string().orEmpty()
                    val list = TaskHtmlParser.parse(homeworkHtml, defaultType = TaskType.ASSIGNMENT)
                    if (list.isNotEmpty()) {
                        assignments = list
                        break
                    }
                }
            } catch (_: Exception) {
            }
        }

        val examUrls = listOf(
            "${NttuHttpClient.BASE_URL}/exam",
            "${NttuHttpClient.BASE_URL}/app/exam/"
        )
        var exams: List<TaskItem> = emptyList()
        for (url in examUrls) {
            try {
                val examReq = Request.Builder().url(url).build()
                val examResp = client.newCall(examReq).execute()
                if (examResp.isSuccessful) {
                    val examHtml = examResp.body?.string().orEmpty()
                    val list = TaskHtmlParser.parse(examHtml, defaultType = TaskType.QUIZ)
                    if (list.isNotEmpty()) {
                        exams = list
                        break
                    }
                }
            } catch (_: Exception) {
            }
        }

        val allTasks = assignments + exams
        if (allTasks.isNotEmpty()) {
            database.taskDao().deleteAll()
            database.taskDao().insertAll(allTasks.map { TaskEntity.fromDomain(it) })
        }
    }

    // ==========================================
    // 本地課程與課表 CRUD 管理 (Local Schedule & Course CRUD)
    // ==========================================
    suspend fun addTimetableSlot(slot: TimetableSlot) = withContext(Dispatchers.IO) {
        database.timetableDao().insertSlot(TimetableSlotEntity.fromDomain(slot))
        // 同步自動註冊/更新對應之課程
        val currentSem = AcademicTermHelper.getCurrentSemesterCode()
        val existing = database.courseDao().getCourseById(slot.courseId)
        if (existing == null) {
            database.courseDao().insertCourse(
                CourseEntity(
                    id = slot.courseId,
                    code = "",
                    name = slot.courseName,
                    instructor = slot.instructor,
                    classroom = slot.classroom,
                    credits = 0,
                    semester = currentSem
                )
            )
        } else {
            database.courseDao().insertCourse(
                existing.copy(
                    classroom = slot.classroom.ifBlank { existing.classroom },
                    instructor = slot.instructor.ifBlank { existing.instructor }
                )
            )
        }
        updateCourseCounters()
    }

    suspend fun deleteTimetableSlot(dayOfWeek: Int, periodNumber: Int) = withContext(Dispatchers.IO) {
        val id = "${dayOfWeek}_${periodNumber}"
        database.timetableDao().deleteSlotById(id)
    }

    suspend fun addCourse(course: Course) = withContext(Dispatchers.IO) {
        database.courseDao().insertCourse(CourseEntity.fromDomain(course))
        updateCourseCounters()
    }

    suspend fun deleteCourse(courseId: String) = withContext(Dispatchers.IO) {
        database.courseDao().deleteCourseById(courseId)
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
