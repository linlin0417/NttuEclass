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
import tw.edu.irika.nttueclass.data.remote.client.NttuHttpClient
import tw.edu.irika.nttueclass.data.remote.parser.AnnouncementHtmlParser
import tw.edu.irika.nttueclass.data.remote.parser.CourseHtmlParser
import tw.edu.irika.nttueclass.data.remote.parser.TaskHtmlParser
import tw.edu.irika.nttueclass.data.remote.parser.TimetableHtmlParser
import tw.edu.irika.nttueclass.domain.model.Announcement
import tw.edu.irika.nttueclass.domain.model.Course
import tw.edu.irika.nttueclass.domain.model.TaskItem
import tw.edu.irika.nttueclass.domain.model.TaskStatus
import tw.edu.irika.nttueclass.domain.model.TaskType
import tw.edu.irika.nttueclass.domain.model.TimetableSlot

class EclassRepository(context: Context) {
    private val database = AppDatabase.getInstance(context)
    private val client = NttuHttpClient.client

    // ==========================================
    // 離線優先資料流 (Offline-First Flows)
    // ==========================================
    fun getTimetableStream(): Flow<List<TimetableSlot>> {
        return database.timetableDao().getAllSlots().map { list ->
            list.map { it.toDomain() }
        }
    }

    fun getCoursesStream(): Flow<List<Course>> {
        return database.courseDao().getAllCourses().map { list ->
            list.map { it.toDomain() }
        }
    }

    fun getAnnouncementsStream(): Flow<List<Announcement>> {
        return database.announcementDao().getAllAnnouncements().map { list ->
            list.map { it.toDomain() }
        }
    }

    fun getTasksStream(): Flow<List<TaskItem>> {
        return database.taskDao().getAllTasks().map { list ->
            list.map { it.toDomain() }
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
        if (slots.isNotEmpty()) {
            database.timetableDao().insertAll(slots.map { TimetableSlotEntity.fromDomain(it) })
        }
    }

    private suspend fun syncCourses() {
        val request = Request.Builder()
            .url("${NttuHttpClient.BASE_URL}/app/course/")
            .build()
        val response = client.newCall(request).execute()
        val html = response.body?.string() ?: return
        val courses = CourseHtmlParser.parse(html)
        if (courses.isNotEmpty()) {
            database.courseDao().insertAll(courses.map { CourseEntity.fromDomain(it) })
        }
    }

    private suspend fun syncAnnouncements() {
        val request = Request.Builder()
            .url("${NttuHttpClient.BASE_URL}/app/bulletin/")
            .build()
        val response = client.newCall(request).execute()
        val html = response.body?.string() ?: return
        val announcements = AnnouncementHtmlParser.parse(html)
        if (announcements.isNotEmpty()) {
            database.announcementDao().insertAll(announcements.map { AnnouncementEntity.fromDomain(it) })
        }
    }

    private suspend fun syncTasks() {
        val request = Request.Builder()
            .url("${NttuHttpClient.BASE_URL}/app/homework/")
            .build()
        val response = client.newCall(request).execute()
        val html = response.body?.string() ?: return
        val tasks = TaskHtmlParser.parse(html, defaultType = TaskType.ASSIGNMENT)
        if (tasks.isNotEmpty()) {
            database.taskDao().insertAll(tasks.map { TaskEntity.fromDomain(it) })
        }
    }

    /**
     * 預載展示資料（供初次安裝、訪客或離線模式使用）
     */
    suspend fun seedInitialDataIfEmpty() = withContext(Dispatchers.IO) {
        val existingSlots = database.timetableDao().getAllSlots().firstOrNull()
        if (existingSlots.isNullOrEmpty()) {
            val sampleSlots = listOf(
                TimetableSlot(1, 3, "c1", "演算法", "理工 C303", "張教授"),
                TimetableSlot(1, 4, "c1", "演算法", "理工 C303", "張教授"),
                TimetableSlot(1, 7, "c2", "資料庫系統", "知本校區 產學301", "李副教授"),
                TimetableSlot(1, 8, "c2", "資料庫系統", "知本校區 產學301", "李副教授"),
                TimetableSlot(2, 2, "c3", "計算機網路", "理工 B204", "王教授"),
                TimetableSlot(2, 3, "c3", "計算機網路", "理工 B204", "王教授"),
                TimetableSlot(2, 6, "c4", "高等軟體工程", "理工 C102", "陳助理教授"),
                TimetableSlot(3, 5, "c5", "專題演講", "圖資館演講廳", "客座學者"),
                TimetableSlot(3, 7, "c6", "人工智慧概論", "理工 A201", "林教授"),
                TimetableSlot(3, 8, "c6", "人工智慧概論", "理工 A201", "林教授"),
                TimetableSlot(4, 3, "c7", "行動應用開發", "產學 205", "Saya"),
                TimetableSlot(4, 4, "c7", "行動應用開發", "產學 205", "Saya"),
                TimetableSlot(4, 10, "c8", "進修夜間專題 (A節)", "人文學院 H101", "黃講師"),
                TimetableSlot(4, 11, "c8", "進修夜間專題 (B節)", "人文學院 H101", "黃講師"),
                TimetableSlot(5, 2, "c9", "密碼學與資安", "理工 C302", "趙教授"),
                TimetableSlot(5, 3, "c9", "密碼學與資安", "理工 C302", "趙教授")
            )
            database.timetableDao().insertAll(sampleSlots.map { TimetableSlotEntity.fromDomain(it) })

            val sampleCourses = listOf(
                Course("c1", "CSI3011", "演算法", "張教授", "理工 C303", 3, "114-2", 1, 1),
                Course("c2", "CSI3022", "資料庫系統", "李副教授", "產學 301", 3, "114-2", 0, 1),
                Course("c3", "CSI3033", "計算機網路", "王教授", "理工 B204", 3, "114-2", 2, 0),
                Course("c4", "CSI3044", "高等軟體工程", "陳助理教授", "理工 C102", 3, "114-2", 0, 0),
                Course("c5", "CSI3055", "專題演講", "客座學者", "圖資館演講廳", 1, "114-2", 0, 0),
                Course("c6", "CSI3066", "人工智慧概論", "林教授", "理工 A201", 3, "114-2", 1, 1),
                Course("c7", "CSI3077", "行動應用開發", "Saya", "產學 205", 3, "114-2", 3, 1)
            )
            database.courseDao().insertAll(sampleCourses.map { CourseEntity.fromDomain(it) })

            val sampleAnnouncements = listOf(
                Announcement("a1", "c7", "行動應用開發", "期中專案規格書已公告於網路學園", "2026/03/05", "Saya", "本學期期中專案請以 Jetpack Compose 實作輕量化校園工具...", isUnread = true, hasAttachment = true),
                Announcement("a2", "c3", "計算機網路", "下週實習課請攜帶筆電並安裝 Wireshark", "2026/03/04", "王教授", "下週四第三節將進行 Packet Sniffing 封包分析實驗...", isUnread = true, hasAttachment = false),
                Announcement("a3", "c1", "演算法", "第一次小考解答與成績公佈", "2026/03/02", "張教授", "請同學登入網路學園查閱各自成績，有疑義者於週五前提出...", isUnread = false, hasAttachment = true)
            )
            database.announcementDao().insertAll(sampleAnnouncements.map { AnnouncementEntity.fromDomain(it) })

            val sampleTasks = listOf(
                TaskItem("t1", "c1", "演算法", "作業二：動態規劃背包問題實作", TaskType.ASSIGNMENT, "2026/03/06 23:59", 8, TaskStatus.URGENT, isSubmitted = false),
                TaskItem("t2", "c7", "行動應用開發", "隨堂測驗：Compose 雙主題狀態管理", TaskType.QUIZ, "2026/03/08 12:00", 44, TaskStatus.WARNING, isSubmitted = false),
                TaskItem("t3", "c2", "資料庫系統", "實習三：B+ Tree 索引與 SQL 優化", TaskType.ASSIGNMENT, "2026/03/12 23:59", 140, TaskStatus.PENDING, isSubmitted = false),
                TaskItem("t4", "c6", "人工智慧概論", "作業一：卷積神經網路分類器 (CNN)", TaskType.ASSIGNMENT, "2026/02/28 23:59", 0, TaskStatus.COMPLETED, score = "96 分", isSubmitted = true)
            )
            database.taskDao().insertAll(sampleTasks.map { TaskEntity.fromDomain(it) })
        }
    }
}
