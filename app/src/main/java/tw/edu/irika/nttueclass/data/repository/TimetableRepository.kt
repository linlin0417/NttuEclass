package tw.edu.irika.nttueclass.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import tw.edu.irika.nttueclass.data.local.db.dao.CourseDao
import tw.edu.irika.nttueclass.data.local.db.dao.TimetableDao
import tw.edu.irika.nttueclass.data.local.db.entity.CourseEntity
import tw.edu.irika.nttueclass.data.local.db.entity.TimetableSlotEntity
import tw.edu.irika.nttueclass.data.local.security.SecureCredentialStorage
import tw.edu.irika.nttueclass.data.remote.client.NttuHttpClient
import tw.edu.irika.nttueclass.data.remote.parser.TimetableHtmlParser
import tw.edu.irika.nttueclass.domain.model.AcademicTermHelper
import tw.edu.irika.nttueclass.domain.model.TimetableSlot
import tw.edu.irika.nttueclass.domain.repository.ICourseRepository
import tw.edu.irika.nttueclass.domain.repository.ITimetableRepository
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class TimetableRepository @Inject constructor(
    private val timetableDao: TimetableDao,
    private val courseDao: CourseDao,
    private val sessionManager: SessionManager,
    private val storage: SecureCredentialStorage,
    private val courseRepositoryProvider: Provider<ICourseRepository>
) : ITimetableRepository {

    override fun getTimetableStream(): Flow<List<TimetableSlot>> {
        return timetableDao.getAllSlots().map { list ->
            if (!storage.isLoggedIn()) emptyList() else list.map { it.toDomain() }
        }
    }

    override suspend fun syncTimetable(): Boolean = withContext(Dispatchers.IO) {
        val urls = listOf(
            "${NttuHttpClient.BASE_URL}/dashboard/myTimeTable",
            "${NttuHttpClient.BASE_URL}/schedule",
            "${NttuHttpClient.BASE_URL}/app/course/schedule.php"
        )
        for (url in urls) {
            try {
                val html = sessionManager.fetchHtmlWithAuth(url)
                if (html.isBlank()) continue
                val slots = TimetableHtmlParser.parse(html)
                if (slots.isNotEmpty()) {
                    val existingCourses = courseDao.getAllCoursesList()
                    val enrichedSlots = slots.map { slot ->
                        if (slot.instructor.isBlank()) {
                            val match = existingCourses.firstOrNull { c ->
                                CourseRepository.isCourseMatch(c.id, c.name, slot.courseId, slot.courseName)
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

                    timetableDao.deleteAll()
                    timetableDao.insertAll(enrichedSlots.map { TimetableSlotEntity.fromDomain(it) })
                    return@withContext true
                }
            } catch (e: SessionExpiredException) {
                throw e
            } catch (_: Exception) {
            }
        }
        false
    }

    override suspend fun addTimetableSlot(slot: TimetableSlot) = withContext(Dispatchers.IO) {
        val existing = courseDao.getCourseById(slot.courseId)
            ?: courseDao.getAllCoursesList().firstOrNull { CourseRepository.isCourseMatch(it.id, it.name, slot.courseId, slot.courseName) }
        val resolvedInstructor = slot.instructor.ifBlank { existing?.instructor.orEmpty() }
        val resolvedClassroom = slot.classroom.ifBlank { existing?.classroom.orEmpty() }
        val finalSlot = slot.copy(instructor = resolvedInstructor, classroom = resolvedClassroom)

        timetableDao.insertSlot(TimetableSlotEntity.fromDomain(finalSlot))

        val currentSem = AcademicTermHelper.getCurrentSemesterCode()
        if (existing == null) {
            courseDao.insertCourse(
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
            courseDao.insertCourse(
                existing.copy(
                    classroom = finalSlot.classroom.ifBlank { existing.classroom },
                    instructor = finalSlot.instructor.ifBlank { existing.instructor }
                )
            )
        }
        courseRepositoryProvider.get().crossEnrichCoursesAndTimetable()
        courseRepositoryProvider.get().updateCourseCounters()
    }

    override suspend fun deleteTimetableSlot(dayOfWeek: Int, periodNumber: Int) = withContext(Dispatchers.IO) {
        val id = "${dayOfWeek}_${periodNumber}"
        timetableDao.deleteSlotById(id)
    }
}
