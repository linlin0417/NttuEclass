package tw.edu.irika.nttueclass.data.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException
import tw.edu.irika.nttueclass.data.local.db.AppDatabase
import tw.edu.irika.nttueclass.data.local.db.entity.AnnouncementEntity
import tw.edu.irika.nttueclass.data.local.db.entity.CourseEntity
import tw.edu.irika.nttueclass.data.local.db.entity.TaskEntity
import tw.edu.irika.nttueclass.data.local.db.entity.TimetableSlotEntity
import tw.edu.irika.nttueclass.data.local.security.SecureCredentialStorage
import tw.edu.irika.nttueclass.data.remote.auth.AuthManager
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

class SessionExpiredException(message: String) : Exception(message)

class EclassRepository(context: Context) {
    private val database = AppDatabase.getInstance(context)
    private val client = NttuHttpClient.client
    val storage = SecureCredentialStorage(context)
    private val authManager = AuthManager(context)

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
            // 1. 先同步課表（取得最詳盡之節次、教室與授課教師資料）
            val timetableSuccess = syncTimetable()
            // 2. 再同步課程列表（由最新課表資料互補缺少的選修課與授課教師）
            val coursesSuccess = syncCourses()
            // 3. 雙向互補：課表與課程資料庫中的授課教師與教室資訊進行最終校準補齊
            crossEnrichCoursesAndTimetable()

            // 4. 並行同步公告與作業
            val (announcementsSuccess, tasksSuccess) = coroutineScope {
                val announcementsJob = async { syncAnnouncements() }
                val tasksJob = async { syncTasks() }
                Pair(announcementsJob.await(), tasksJob.await())
            }
            updateCourseCounters()

            // 5. 真實成功檢核：若全部模組均未獲取到任何有效伺服器資料，判定為同步未達成
            if (!timetableSuccess && !coursesSuccess && !announcementsSuccess && !tasksSuccess) {
                throw IOException("無法從學校伺服器取得最新資料，請檢查網路連線")
            }

            storage.saveLastSyncTimestamp(System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun fetchHtmlWithAuth(url: String): String {
        // 1. 若尚未有有效 Session，且已儲存帳密，先嘗試靜默登入獲取 Cookie
        if (!NttuHttpClient.cookieJar.hasValidSession() && storage.isLoggedIn()) {
            authManager.silentRefreshSession()
        }

        // 2. 執行 GET 請求
        val req = Request.Builder().url(url).build()
        val resp = client.newCall(req).execute()
        val requestUrl = resp.request.url.toString()
        val html = resp.use { r ->
            if (r.isSuccessful) r.body?.string().orEmpty() else ""
        }

        // 3. 檢測是否被重導向至登入頁或回傳登入 HTML (Session 逾期或無效)
        val isLoginRedirect = requestUrl.contains("/index/login") || isLoginPageHtml(html)
        if (isLoginRedirect) {
            // 嘗試靜默重新整理 Session
            if (storage.isLoggedIn()) {
                val reloginSuccess = authManager.silentRefreshSession()
                if (reloginSuccess) {
                    // 重試原請求
                    val retryReq = Request.Builder().url(url).build()
                    val retryResp = client.newCall(retryReq).execute()
                    val retryHtml = retryResp.use { r ->
                        if (r.isSuccessful) r.body?.string().orEmpty() else ""
                    }
                    if (!isLoginPageHtml(retryHtml) && !retryResp.request.url.toString().contains("/index/login")) {
                        return retryHtml
                    }
                }
            }
            throw SessionExpiredException("學校學園系統登入已逾期，請重新登入")
        }

        return html
    }

    private suspend fun syncTimetable(): Boolean {
        val urls = listOf(
            "${NttuHttpClient.BASE_URL}/dashboard/myTimeTable",
            "${NttuHttpClient.BASE_URL}/schedule",
            "${NttuHttpClient.BASE_URL}/app/course/schedule.php"
        )
        for (url in urls) {
            try {
                val html = fetchHtmlWithAuth(url)
                if (html.isBlank()) continue
                val slots = TimetableHtmlParser.parse(html)
                if (slots.isNotEmpty()) {
                    // 若本地現有課程已有教師資料，自動對照補齊課表缺少的授課教師
                    val existingCourses = database.courseDao().getAllCoursesList()
                    val enrichedSlots = slots.map { slot ->
                        if (slot.instructor.isBlank()) {
                            val match = existingCourses.firstOrNull { c ->
                                isCourseMatch(c.id, c.name, slot.courseId, slot.courseName)
                            }
                            if (match != null && match.instructor.isNotBlank()) {
                                slot.copy(
                                    instructor = match.instructor,
                                    classroom = slot.classroom.ifBlank { match.classroom }
                                )
                            } else slot
                        } else slot
                    }

                    database.timetableDao().deleteAll()
                    database.timetableDao().insertAll(enrichedSlots.map { TimetableSlotEntity.fromDomain(it) })
                    return true
                }
            } catch (e: SessionExpiredException) {
                throw e
            } catch (_: Exception) {
                // 防禦性容錯，嘗試下一個可能路徑
            }
        }
        return false
    }

    private suspend fun syncCourses(): Boolean {
        val urls = listOf(
            "${NttuHttpClient.BASE_URL}/dashboard",
            "${NttuHttpClient.BASE_URL}/course",
            "${NttuHttpClient.BASE_URL}/app/course/"
        )
        var parsedCourses: List<Course> = emptyList()
        for (url in urls) {
            try {
                val html = fetchHtmlWithAuth(url)
                if (html.isBlank()) continue
                val list = CourseHtmlParser.parse(html)
                if (list.isNotEmpty()) {
                    parsedCourses = list
                    break
                }
            } catch (e: SessionExpiredException) {
                throw e
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
                val matchingSlot = slots.firstOrNull { isCourseMatch(c.id, c.name, it.courseId, it.courseName) }
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
            return true
        }
        return false
    }

    /**
     * 雙向互補課表與課程資料庫中的授課教師與教室資訊
     * 優化：僅 upsert 實際有修改的記錄，避免不必要的 deleteAll + insertAll
     */
    private suspend fun crossEnrichCoursesAndTimetable() {
        val courses = database.courseDao().getAllCoursesList()
        val slots = database.timetableDao().getAllSlotsList()
        if (courses.isEmpty() && slots.isEmpty()) return

        // 1. 若課表節次缺少教師，由已解析的課程對應補齊
        val modifiedSlots = mutableListOf<TimetableSlotEntity>()
        val enrichedSlots = slots.map { slot ->
            if (slot.instructor.isBlank() || slot.classroom.isBlank()) {
                val match = courses.firstOrNull { c ->
                    isCourseMatch(c.id, c.name, slot.courseId, slot.courseName)
                }
                if (match != null) {
                    val newInstructor = slot.instructor.ifBlank { match.instructor }
                    val newClassroom = slot.classroom.ifBlank { match.classroom }
                    if (newInstructor != slot.instructor || newClassroom != slot.classroom) {
                        val updated = slot.copy(instructor = newInstructor, classroom = newClassroom)
                        modifiedSlots.add(updated)
                        updated
                    } else slot
                } else slot
            } else slot
        }

        // 僅 upsert 有修改的 slots（利用 REPLACE 策略）
        if (modifiedSlots.isNotEmpty()) {
            database.timetableDao().insertAll(modifiedSlots)
        }

        // 2. 若課程清單缺少教師或教室，由課表插槽補齊
        val modifiedCourses = mutableListOf<CourseEntity>()
        enrichedSlots // 使用已補齊的 slots 做比對
        courses.forEach { course ->
            if (course.instructor.isBlank() || course.classroom.isBlank()) {
                val match = enrichedSlots.firstOrNull { s ->
                    isCourseMatch(course.id, course.name, s.courseId, s.courseName)
                }
                if (match != null) {
                    val newInstructor = course.instructor.ifBlank { match.instructor }
                    val newClassroom = course.classroom.ifBlank { match.classroom }
                    if (newInstructor != course.instructor || newClassroom != course.classroom) {
                        modifiedCourses.add(course.copy(instructor = newInstructor, classroom = newClassroom))
                    }
                }
            }
        }

        // 僅 upsert 有修改的 courses
        if (modifiedCourses.isNotEmpty()) {
            database.courseDao().insertAll(modifiedCourses)
        }
    }

    private fun isCourseMatch(id1: String, name1: String, id2: String, name2: String): Boolean {
        if (id1.isNotBlank() && id2.isNotBlank() && !id1.startsWith("c_") && !id2.startsWith("c_") && id1 == id2) {
            return true
        }
        val n1 = normalizeName(name1)
        val n2 = normalizeName(name2)
        if (n1.isNotBlank() && n2.isNotBlank()) {
            if (n1 == n2) return true
            if (n1.contains(n2) || n2.contains(n1)) return true
        }
        return false
    }

    companion object {
        // 預編譯 Regex 常數：避免在 O(n²) 迴圈中反覆建立 Regex 物件
        private val REGEX_BRACKETS = Regex("""\([^\)]*\)|\[[^\]]*\]|（[^）]*）|【[^】]*】""")
        private val REGEX_SPECIAL_CHARS = Regex("""[*＊\s\-_]""")

        fun isLoginPageHtml(html: String): Boolean {
            if (html.isBlank()) return false
            return html.contains("id=\"login_form\"") ||
                    html.contains("name=\"anticsrf\"") ||
                    html.contains("action=\"/index/login\"") ||
                    html.contains("secimg.php") ||
                    (html.contains("登入") && html.contains("密碼") && html.contains("驗證碼"))
        }
    }

    private fun normalizeName(name: String): String {
        return name
            .replace(REGEX_BRACKETS, "")
            .replace(REGEX_SPECIAL_CHARS, "")
            .trim()
    }

    private suspend fun syncAnnouncements(): Boolean {
        val urls = listOf(
            "${NttuHttpClient.BASE_URL}/dashboard/latestBulletin",
            "${NttuHttpClient.BASE_URL}/bulletin",
            "${NttuHttpClient.BASE_URL}/app/bulletin/"
        )
        for (url in urls) {
            try {
                val html = fetchHtmlWithAuth(url)
                if (html.isBlank()) continue
                val announcements = AnnouncementHtmlParser.parse(html)
                if (announcements.isNotEmpty()) {
                    database.announcementDao().deleteAll()
                    database.announcementDao().insertAll(announcements.map { AnnouncementEntity.fromDomain(it) })
                    return true
                }
            } catch (e: SessionExpiredException) {
                throw e
            } catch (_: Exception) {
            }
        }
        return false
    }

    private suspend fun syncTasks(): Boolean {
        var assignmentsSynced = false
        var examsSynced = false

        val homeworkUrls = listOf(
            "${NttuHttpClient.BASE_URL}/homework",
            "${NttuHttpClient.BASE_URL}/app/homework/"
        )
        var assignments: List<TaskItem> = emptyList()
        for (url in homeworkUrls) {
            try {
                val homeworkHtml = fetchHtmlWithAuth(url)
                if (homeworkHtml.isBlank()) continue
                val list = TaskHtmlParser.parse(homeworkHtml, defaultType = TaskType.ASSIGNMENT)
                if (list.isNotEmpty()) {
                    assignments = list
                    assignmentsSynced = true
                    break
                }
            } catch (e: SessionExpiredException) {
                throw e
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
                val examHtml = fetchHtmlWithAuth(url)
                if (examHtml.isBlank()) continue
                val list = TaskHtmlParser.parse(examHtml, defaultType = TaskType.QUIZ)
                if (list.isNotEmpty()) {
                    exams = list
                    examsSynced = true
                    break
                }
            } catch (e: SessionExpiredException) {
                throw e
            } catch (_: Exception) {
            }
        }

        val allTasks = assignments + exams
        if (allTasks.isNotEmpty()) {
            database.taskDao().deleteAll()
            database.taskDao().insertAll(allTasks.map { TaskEntity.fromDomain(it) })
        }
        return assignmentsSynced || examsSynced
    }

    // ==========================================
    // 本地課程與課表 CRUD 管理 (Local Schedule & Course CRUD)
    // ==========================================
    suspend fun addTimetableSlot(slot: TimetableSlot) = withContext(Dispatchers.IO) {
        // 若該節次教師或教室為空，嘗試由現有課程補齊
        val existing = database.courseDao().getCourseById(slot.courseId)
            ?: database.courseDao().getAllCoursesList().firstOrNull { isCourseMatch(it.id, it.name, slot.courseId, slot.courseName) }
        val resolvedInstructor = slot.instructor.ifBlank { existing?.instructor.orEmpty() }
        val resolvedClassroom = slot.classroom.ifBlank { existing?.classroom.orEmpty() }
        val finalSlot = slot.copy(instructor = resolvedInstructor, classroom = resolvedClassroom)

        database.timetableDao().insertSlot(TimetableSlotEntity.fromDomain(finalSlot))
        // 同步自動註冊/更新對應之課程
        val currentSem = AcademicTermHelper.getCurrentSemesterCode()
        if (existing == null) {
            database.courseDao().insertCourse(
                CourseEntity(
                    id = finalSlot.courseId,
                    code = "",
                    name = finalSlot.courseName,
                    instructor = finalSlot.instructor,
                    classroom = finalSlot.classroom,
                    credits = 0,
                    semester = currentSem
                )
            )
        } else {
            database.courseDao().insertCourse(
                existing.copy(
                    classroom = finalSlot.classroom.ifBlank { existing.classroom },
                    instructor = finalSlot.instructor.ifBlank { existing.instructor }
                )
            )
        }
        crossEnrichCoursesAndTimetable()
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
