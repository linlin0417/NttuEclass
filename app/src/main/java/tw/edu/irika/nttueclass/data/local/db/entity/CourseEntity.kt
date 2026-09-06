package tw.edu.irika.nttueclass.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import tw.edu.irika.nttueclass.domain.model.Course

@Entity(tableName = "courses")
data class CourseEntity(
    @PrimaryKey val id: String,
    val code: String,
    val name: String,
    val instructor: String,
    val classroom: String,
    val credits: Int,
    val semester: String,
    val unreadAnnouncementsCount: Int = 0,
    val pendingTasksCount: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): Course = Course(
        id = id,
        code = code,
        name = name,
        instructor = instructor,
        classroom = classroom,
        credits = credits,
        semester = semester,
        unreadAnnouncementsCount = unreadAnnouncementsCount,
        pendingTasksCount = pendingTasksCount
    )

    companion object {
        fun fromDomain(domain: Course): CourseEntity = CourseEntity(
            id = domain.id,
            code = domain.code,
            name = domain.name,
            instructor = domain.instructor,
            classroom = domain.classroom,
            credits = domain.credits,
            semester = domain.semester,
            unreadAnnouncementsCount = domain.unreadAnnouncementsCount,
            pendingTasksCount = domain.pendingTasksCount
        )
    }
}
