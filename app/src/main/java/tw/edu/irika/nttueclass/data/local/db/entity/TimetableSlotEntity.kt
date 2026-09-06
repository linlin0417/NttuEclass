package tw.edu.irika.nttueclass.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import tw.edu.irika.nttueclass.domain.model.TimetableSlot

@Entity(tableName = "timetable_slots")
data class TimetableSlotEntity(
    @PrimaryKey val id: String, // 格式：${dayOfWeek}_${periodNumber}
    val dayOfWeek: Int,
    val periodNumber: Int,
    val courseId: String,
    val courseName: String,
    val classroom: String,
    val instructor: String,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): TimetableSlot = TimetableSlot(
        dayOfWeek = dayOfWeek,
        periodNumber = periodNumber,
        courseId = courseId,
        courseName = courseName,
        classroom = classroom,
        instructor = instructor
    )

    companion object {
        fun fromDomain(domain: TimetableSlot): TimetableSlotEntity = TimetableSlotEntity(
            id = "${domain.dayOfWeek}_${domain.periodNumber}",
            dayOfWeek = domain.dayOfWeek,
            periodNumber = domain.periodNumber,
            courseId = domain.courseId,
            courseName = domain.courseName,
            classroom = domain.classroom,
            instructor = domain.instructor
        )
    }
}
