package tw.edu.irika.nttueclass.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import tw.edu.irika.nttueclass.data.local.db.dao.AnnouncementDao
import tw.edu.irika.nttueclass.data.local.db.dao.CourseDao
import tw.edu.irika.nttueclass.data.local.db.dao.TaskDao
import tw.edu.irika.nttueclass.data.local.db.dao.TimetableDao
import tw.edu.irika.nttueclass.data.local.db.entity.CourseEntity
import tw.edu.irika.nttueclass.data.local.db.entity.TimetableSlotEntity
import tw.edu.irika.nttueclass.data.local.security.SecureCredentialStorage
import tw.edu.irika.nttueclass.data.remote.client.NttuHttpClient
import tw.edu.irika.nttueclass.data.remote.parser.CourseHtmlParser
import tw.edu.irika.nttueclass.domain.model.AcademicTermHelper
import tw.edu.irika.nttueclass.domain.model.Course
import tw.edu.irika.nttueclass.domain.repository.ICourseRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CourseRepository @Inject constructor(
    private val courseDao: CourseDao,
    private val timetableDao: TimetableDao,
    private val announcementDao: AnnouncementDao,
    private val taskDao: TaskDao,
    private val sessionManager: SessionManager,
    private val storage: SecureCredentialStorage
) : ICourseRepository {

    override fun getCoursesStream(): Flow<List<Course>> {
        return courseDao.getAllCourses().map { list ->
            if (!storage.isLoggedIn()) emptyList() else list.map { it.toDomain() }
        }
    }

    override suspend fun syncCourses(): Boolean = withContext(Dispatchers.IO) {
        val urls = listOf(
            "${NttuHttpClient.BASE_URL}/dashboard",
            "${NttuHttpClient.BASE_URL}/course",
            "${NttuHttpClient.BASE_URL}/app/course/"
        )
        var parsedCourses: List<Course> = emptyList()
        for (url in urls) {
            try {
                val html = sessionManager.fetchHtmlWithAuth(url)
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
        val slots = timetableDao.getAllSlotsList()
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
            courseDao.deleteAll()
            courseDao.insertAll(mergedCourses.map { CourseEntity.fromDomain(it) })
            return@withContext true
        }
        false
    }

    override suspend fun addCourse(course: Course) = withContext(Dispatchers.IO) {
        courseDao.insertCourse(CourseEntity.fromDomain(course))
        updateCourseCounters()
    }

    override suspend fun deleteCourse(courseId: String) = withContext(Dispatchers.IO) {
        courseDao.deleteCourseById(courseId)
    }

    /**
     * 雙向互補課表與課程資料庫中的授課教師與教室資訊
     * 優化：僅 upsert 實際有修改的記錄，避免不必要的 deleteAll + insertAll
     */
    override suspend fun crossEnrichCoursesAndTimetable() = withContext(Dispatchers.IO) {
        val courses = courseDao.getAllCoursesList()
        val slots = timetableDao.getAllSlotsList()
        if (courses.isEmpty() && slots.isEmpty()) return@withContext

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
                return courses.firstOrNull {
                    val n = normalizeName(it.name)
                    n.isNotBlank() && (n.contains(normalizedTarget) || normalizedTarget.contains(n))
                }
            }
            return null
        }

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

        if (modifiedSlots.isNotEmpty()) {
            timetableDao.insertAll(modifiedSlots)
        }

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

        if (modifiedCourses.isNotEmpty()) {
            courseDao.insertAll(modifiedCourses)
        }
    }

    override suspend fun updateCourseCounters() = withContext(Dispatchers.IO) {
        val courses = courseDao.getAllCoursesList()
        if (courses.isEmpty()) return@withContext

        val announcements = announcementDao.getAllAnnouncementsList()
        val tasks = taskDao.getAllTasksList()

        val announcementsByCourseId = announcements.groupBy { it.courseId }
        val tasksByCourseId = tasks.groupBy { it.courseId }

        val updatedCourses = courses.map { course ->
            val idMatchedAnnouncements = announcementsByCourseId[course.id] ?: emptyList()
            val idMatchedTasks = tasksByCourseId[course.id] ?: emptyList()

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
        courseDao.insertAll(updatedCourses)
    }

    companion object {
        private val REGEX_BRACKETS = Regex("""\([^\)]*\)|\[[^\]]*\]|（[^）]*）|【[^】]*】""")
        private val REGEX_SPECIAL_CHARS = Regex("""[*＊\s\-_]""")

        fun normalizeName(name: String): String {
            return name
                .replace(REGEX_BRACKETS, "")
                .replace(REGEX_SPECIAL_CHARS, "")
                .trim()
        }

        fun isCourseMatch(id1: String, name1: String, id2: String, name2: String): Boolean {
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
    }
}
