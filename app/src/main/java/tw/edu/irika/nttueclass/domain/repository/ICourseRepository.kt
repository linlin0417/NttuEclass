package tw.edu.irika.nttueclass.domain.repository

import kotlinx.coroutines.flow.Flow
import tw.edu.irika.nttueclass.domain.model.Course

interface ICourseRepository {
    fun getCoursesStream(): Flow<List<Course>>
    suspend fun syncCourses(): Boolean
    suspend fun addCourse(course: Course)
    suspend fun deleteCourse(courseId: String)
    suspend fun updateCourseCounters()
    suspend fun crossEnrichCoursesAndTimetable()
}
