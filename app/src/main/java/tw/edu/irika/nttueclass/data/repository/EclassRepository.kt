package tw.edu.irika.nttueclass.data.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException
import tw.edu.irika.nttueclass.data.local.db.AppDatabase
import tw.edu.irika.nttueclass.data.local.db.entity.AnnouncementEntity
import tw.edu.irika.nttueclass.data.local.db.entity.CourseEntity
import tw.edu.irika.nttueclass.data.local.db.entity.CourseMaterialEntity
import tw.edu.irika.nttueclass.data.local.db.entity.TaskEntity
import tw.edu.irika.nttueclass.data.local.db.entity.TimetableSlotEntity
import tw.edu.irika.nttueclass.data.local.security.SecureCredentialStorage
import tw.edu.irika.nttueclass.data.remote.auth.AuthManager
import tw.edu.irika.nttueclass.data.remote.client.CourseMaterialDownloader
import tw.edu.irika.nttueclass.data.remote.client.NttuHttpClient
import tw.edu.irika.nttueclass.data.remote.parser.AnnouncementHtmlParser
import tw.edu.irika.nttueclass.data.remote.parser.CourseHtmlParser
import tw.edu.irika.nttueclass.data.remote.parser.CourseMaterialParser
import tw.edu.irika.nttueclass.data.remote.parser.TaskHtmlParser
import tw.edu.irika.nttueclass.data.remote.parser.TimetableHtmlParser
import tw.edu.irika.nttueclass.domain.model.AcademicTermHelper
import tw.edu.irika.nttueclass.domain.model.Announcement
import tw.edu.irika.nttueclass.domain.model.Course
import tw.edu.irika.nttueclass.domain.model.CourseMaterial
import tw.edu.irika.nttueclass.domain.model.MaterialDownloadStatus
import tw.edu.irika.nttueclass.domain.model.TaskItem
import tw.edu.irika.nttueclass.domain.model.TaskStatus
import tw.edu.irika.nttueclass.domain.model.TaskType
import tw.edu.irika.nttueclass.domain.model.TimetableSlot
import java.io.File

class SessionExpiredException(message: String) : Exception(message)

class EclassRepository(context: Context) {
    val appContext: Context = context.applicationContext
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

    fun getMaterialsStream(courseId: String): Flow<List<CourseMaterial>> {
        return database.courseMaterialDao().getMaterialsByCourseId(courseId).map { list ->
            if (!storage.isLoggedIn()) emptyList() else list.map { entity ->
                val domain = entity.toDomain()
                // 校準本機檔案狀態：若記錄為已下載但實際檔案已被清除，自動校正為未下載
                if (domain.downloadStatus == MaterialDownloadStatus.DOWNLOADED && !domain.isFilePresentOnDisk) {
                    domain.copy(
                        downloadStatus = MaterialDownloadStatus.NOT_DOWNLOADED,
                        localFilePath = null
                    )
                } else domain
            }
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
                            if (match != null) {
                                val resolvedInstructor = slot.instructor.ifBlank { match.instructor }
                                val resolvedClassroom = if (slot.classroom.isBlank() || (slot.classroom.length <= 1 && match.classroom.length > 1)) {
                                    match.classroom
                                } else {
                                    slot.classroom
                                }
                                slot.copy(
                                    instructor = resolvedInstructor,
                                    classroom = resolvedClassroom
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

        // 建立 HashMap 索引，將 O(n²) 降為 O(n)
        val courseById = courses.filter { it.id.isNotBlank() && !it.id.startsWith("c_") }
            .associateBy { it.id }
        val courseByNormalizedName = courses.associateBy { normalizeName(it.name) }

        fun findCourseMatch(targetId: String, targetName: String): CourseEntity? {
            if (targetId.isNotBlank() && !targetId.startsWith("c_")) {
                courseById[targetId]?.let { return it }
            }
            val normalizedTarget = normalizeName(targetName)
            if (normalizedTarget.isNotBlank()) {
                courseByNormalizedName[normalizedTarget]?.let { return it }
                // 含子字串比對 (fallback)
                return courses.firstOrNull {
                    val n = normalizeName(it.name)
                    n.isNotBlank() && (n.contains(normalizedTarget) || normalizedTarget.contains(n))
                }
            }
            return null
        }

        // 1. 若課表節次缺少教師，由已解析的課程對應補齊
        val modifiedSlots = mutableListOf<TimetableSlotEntity>()
        val enrichedSlots = slots.map { slot ->
            if (slot.instructor.isBlank() || slot.classroom.isBlank() || slot.classroom.length <= 1) {
                val match = findCourseMatch(slot.courseId, slot.courseName)
                if (match != null) {
                    val newInstructor = slot.instructor.ifBlank { match.instructor }
                    val newClassroom = if (slot.classroom.isBlank() || (slot.classroom.length <= 1 && match.classroom.length > 1)) {
                        match.classroom
                    } else {
                        slot.classroom
                    }
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

        // 建立已補齊 slots 的索引
        val slotById = enrichedSlots.filter { it.courseId.isNotBlank() && !it.courseId.startsWith("c_") }
            .associateBy { it.courseId }
        val slotByNormalizedName = enrichedSlots.associateBy { normalizeName(it.courseName) }

        fun findSlotMatch(targetId: String, targetName: String): TimetableSlotEntity? {
            if (targetId.isNotBlank() && !targetId.startsWith("c_")) {
                slotById[targetId]?.let { return it }
            }
            val normalizedTarget = normalizeName(targetName)
            if (normalizedTarget.isNotBlank()) {
                slotByNormalizedName[normalizedTarget]?.let { return it }
                return enrichedSlots.firstOrNull {
                    val n = normalizeName(it.courseName)
                    n.isNotBlank() && (n.contains(normalizedTarget) || normalizedTarget.contains(n))
                }
            }
            return null
        }

        // 2. 若課程清單缺少教師或教室，由課表插槽補齊
        val modifiedCourses = mutableListOf<CourseEntity>()
        courses.forEach { course ->
            if (course.instructor.isBlank() || course.classroom.isBlank() || course.classroom.length <= 1) {
                val match = findSlotMatch(course.id, course.name)
                if (match != null) {
                    val newInstructor = course.instructor.ifBlank { match.instructor }
                    val newClassroom = if (course.classroom.isBlank() || (course.classroom.length <= 1 && match.classroom.length > 1)) {
                        match.classroom
                    } else {
                        course.classroom
                    }
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

    /**
     * 依課程獨立並行同步全校作業、測驗與問卷（透過 Semaphore 進行並行控制）
     */
    private suspend fun syncTasks(): Boolean = withContext(Dispatchers.IO) {
        val courses = database.courseDao().getAllCoursesList()
        val validCourses = courses.filter { it.id.isNotBlank() && !it.id.startsWith("c_") && it.id.all { ch -> ch.isDigit() } }
        if (validCourses.isEmpty()) return@withContext false

        val semaphore = Semaphore(3) // 並行上限設為 3 個請求，避免學校伺服器負載過高
        val allTasks = coroutineScope {
            validCourses.flatMap { course ->
                listOf(
                    async {
                        semaphore.withPermit {
                            runCatching {
                                val url = "${NttuHttpClient.BASE_URL}/course/homeworkList/${course.id}"
                                val html = fetchHtmlWithAuth(url)
                                TaskHtmlParser.parse(html, defaultCourseId = course.id, defaultCourseName = course.name, defaultType = TaskType.ASSIGNMENT)
                            }.getOrDefault(emptyList())
                        }
                    },
                    async {
                        semaphore.withPermit {
                            runCatching {
                                val url = "${NttuHttpClient.BASE_URL}/course/examList/${course.id}"
                                val html = fetchHtmlWithAuth(url)
                                TaskHtmlParser.parse(html, defaultCourseId = course.id, defaultCourseName = course.name, defaultType = TaskType.QUIZ)
                            }.getOrDefault(emptyList())
                        }
                    },
                    async {
                        semaphore.withPermit {
                            runCatching {
                                val url = "${NttuHttpClient.BASE_URL}/course/questionnaireList/${course.id}"
                                val html = fetchHtmlWithAuth(url)
                                TaskHtmlParser.parse(html, defaultCourseId = course.id, defaultCourseName = course.name, defaultType = TaskType.QUESTIONNAIRE)
                            }.getOrDefault(emptyList())
                        }
                    }
                )
            }.awaitAll().flatten()
        }

        if (allTasks.isNotEmpty()) {
            database.taskDao().deleteAll()
            database.taskDao().insertAll(allTasks.map { TaskEntity.fromDomain(it) })
            return@withContext true
        }
        return@withContext false
    }

    /**
     * 單一課程隨選即時同步（供 CourseDetailScreen 獨立呼叫）
     */
    suspend fun syncCourseTasks(courseId: String, courseName: String = ""): Result<List<TaskItem>> = withContext(Dispatchers.IO) {
        if (courseId.isBlank() || courseId.startsWith("c_") || !courseId.all { it.isDigit() }) {
            return@withContext Result.success(emptyList())
        }

        try {
            val hwUrl = "${NttuHttpClient.BASE_URL}/course/homeworkList/$courseId"
            val examUrl = "${NttuHttpClient.BASE_URL}/course/examList/$courseId"
            val surveyUrl = "${NttuHttpClient.BASE_URL}/course/questionnaireList/$courseId"

            val resolvedCourseName = courseName.ifBlank {
                database.courseDao().getCourseById(courseId)?.name.orEmpty()
            }

            val (hwList, examList, surveyList) = coroutineScope {
                val hwJob = async {
                    runCatching {
                        val html = fetchHtmlWithAuth(hwUrl)
                        TaskHtmlParser.parse(html, defaultCourseId = courseId, defaultCourseName = resolvedCourseName, defaultType = TaskType.ASSIGNMENT)
                    }.getOrDefault(emptyList())
                }
                val examJob = async {
                    runCatching {
                        val html = fetchHtmlWithAuth(examUrl)
                        TaskHtmlParser.parse(html, defaultCourseId = courseId, defaultCourseName = resolvedCourseName, defaultType = TaskType.QUIZ)
                    }.getOrDefault(emptyList())
                }
                val surveyJob = async {
                    runCatching {
                        val html = fetchHtmlWithAuth(surveyUrl)
                        TaskHtmlParser.parse(html, defaultCourseId = courseId, defaultCourseName = resolvedCourseName, defaultType = TaskType.QUESTIONNAIRE)
                    }.getOrDefault(emptyList())
                }
                Triple(hwJob.await(), examJob.await(), surveyJob.await())
            }

            val courseAllTasks = hwList + examList + surveyList
            if (courseAllTasks.isNotEmpty()) {
                database.taskDao().insertAll(courseAllTasks.map { TaskEntity.fromDomain(it) })
                updateCourseCounters()
            }
            Result.success(courseAllTasks)
        } catch (e: Exception) {
            Result.failure(e)
        }
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

        // 預建索引：依 courseId 分組公告與作業
        val announcementsByCourseId = announcements.groupBy { it.courseId }
        val tasksByCourseId = tasks.groupBy { it.courseId }

        val updatedCourses = courses.map { course ->
            // 快速路徑：先用 courseId 精確比對
            val idMatchedAnnouncements = announcementsByCourseId[course.id] ?: emptyList()
            val idMatchedTasks = tasksByCourseId[course.id] ?: emptyList()

            // 補充路徑：名稱子字串比對 (僅對未被 courseId 比對到的資料)
            val unreadCount = idMatchedAnnouncements.count { it.isUnread } +
                announcements.count { announcement ->
                    announcement.courseId != course.id &&
                    announcement.isUnread &&
                    (announcement.courseName.contains(course.name) ||
                     course.name.contains(announcement.courseName))
                }
            val pendingCount = idMatchedTasks.count { !it.isSubmitted } +
                tasks.count { task ->
                    task.courseId != course.id &&
                    !task.isSubmitted &&
                    (task.courseName.contains(course.name) ||
                     course.name.contains(task.courseName))
                }
            course.copy(
                unreadAnnouncementsCount = unreadCount,
                pendingTasksCount = pendingCount
            )
        }
        database.courseDao().insertAll(updatedCourses)
    }

    // ==========================================
    // 課程教材同步與本機下載管理 (Course Materials & Download Manager)
    // ==========================================
    suspend fun syncCourseMaterials(courseId: String): Result<List<CourseMaterial>> = withContext(Dispatchers.IO) {
        try {
            val urls = listOf(
                "${NttuHttpClient.BASE_URL}/api/courses/$courseId/activities?sub_course_id=0",
                "${NttuHttpClient.BASE_URL}/api/courses/$courseId/syllabus",
                "${NttuHttpClient.BASE_URL}/course/$courseId/content",
                "${NttuHttpClient.BASE_URL}/course/$courseId/learning-activity",
                "${NttuHttpClient.BASE_URL}/app/course/courseware.php?csid=$courseId"
            )

            var parsedMaterials: List<CourseMaterial> = emptyList()
            for (url in urls) {
                try {
                    val raw = fetchHtmlWithAuth(url)
                    if (raw.isBlank()) continue
                    val list = CourseMaterialParser.parse(courseId, raw)
                    if (list.isNotEmpty()) {
                        parsedMaterials = list
                        break
                    }
                } catch (e: SessionExpiredException) {
                    throw e
                } catch (_: Exception) {
                }
            }

            // 與 Room 現有快取合併（妥善保留已下載 localFilePath 與狀態）
            val existing = database.courseMaterialDao().getMaterialsByCourseIdList(courseId)
            val existingMap = existing.associateBy { it.id }

            val merged = parsedMaterials.map { parsed ->
                val local = existingMap[parsed.id]
                if (local != null && local.downloadStatus == MaterialDownloadStatus.DOWNLOADED.ordinal && !local.localFilePath.isNullOrBlank()) {
                    val file = File(local.localFilePath)
                    if (file.exists() && file.length() > 0) {
                        parsed.copy(
                            localFilePath = local.localFilePath,
                            downloadStatus = MaterialDownloadStatus.DOWNLOADED
                        )
                    } else parsed
                } else parsed
            }

            if (merged.isNotEmpty()) {
                database.courseMaterialDao().insertAll(merged.map { CourseMaterialEntity.fromDomain(it) })
            }

            Result.success(merged)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadMaterial(
        material: CourseMaterial,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            database.courseMaterialDao().updateDownloadStatus(
                material.id,
                MaterialDownloadStatus.DOWNLOADING.ordinal,
                null
            )

            val downloadResult = CourseMaterialDownloader.download(appContext, material, onProgress)
            if (downloadResult.isSuccess) {
                val file = downloadResult.getOrThrow()
                database.courseMaterialDao().updateDownloadStatus(
                    material.id,
                    MaterialDownloadStatus.DOWNLOADED.ordinal,
                    file.absolutePath
                )
                Result.success(file)
            } else {
                database.courseMaterialDao().updateDownloadStatus(
                    material.id,
                    MaterialDownloadStatus.FAILED.ordinal,
                    null
                )
                Result.failure(downloadResult.exceptionOrNull() ?: IOException("教材下載失敗"))
            }
        } catch (e: Exception) {
            database.courseMaterialDao().updateDownloadStatus(
                material.id,
                MaterialDownloadStatus.FAILED.ordinal,
                null
            )
            Result.failure(e)
        }
    }

    suspend fun deleteDownloadedMaterial(material: CourseMaterial) = withContext(Dispatchers.IO) {
        CourseMaterialDownloader.deleteLocalFile(material.localFilePath)
        database.courseMaterialDao().updateDownloadStatus(
            material.id,
            MaterialDownloadStatus.NOT_DOWNLOADED.ordinal,
            null
        )
    }

    suspend fun addCustomMaterial(material: CourseMaterial) = withContext(Dispatchers.IO) {
        database.courseMaterialDao().insert(CourseMaterialEntity.fromDomain(material))
    }

    suspend fun deleteMaterial(materialId: String) = withContext(Dispatchers.IO) {
        val existing = database.courseMaterialDao().getMaterialById(materialId)
        if (existing != null) {
            CourseMaterialDownloader.deleteLocalFile(existing.localFilePath)
            database.courseMaterialDao().deleteById(materialId)
        }
    }

    /**
     * 清空本地所有快取資料（用於使用者登出時維護資訊安全）
     */
    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        database.timetableDao().deleteAll()
        database.courseDao().deleteAll()
        database.announcementDao().deleteAll()
        database.taskDao().deleteAll()
        database.courseMaterialDao().deleteAll()
        try {
            val baseDir = appContext.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
            File(baseDir, "materials").deleteRecursively()
        } catch (_: Exception) {}
    }
}
