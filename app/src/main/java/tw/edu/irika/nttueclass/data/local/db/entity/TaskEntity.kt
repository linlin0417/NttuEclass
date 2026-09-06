package tw.edu.irika.nttueclass.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import tw.edu.irika.nttueclass.domain.model.TaskItem
import tw.edu.irika.nttueclass.domain.model.TaskStatus
import tw.edu.irika.nttueclass.domain.model.TaskType

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val courseId: String,
    val courseName: String,
    val title: String,
    val type: String, // ASSIGNMENT, QUIZ, EXAM
    val dueDateTime: String,
    val remainingHours: Long,
    val status: String, // PENDING, URGENT, WARNING, COMPLETED
    val score: String?,
    val isSubmitted: Boolean,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): TaskItem = TaskItem(
        id = id,
        courseId = courseId,
        courseName = courseName,
        title = title,
        type = runCatching { TaskType.valueOf(type) }.getOrDefault(TaskType.ASSIGNMENT),
        dueDateTime = dueDateTime,
        remainingHours = remainingHours,
        status = runCatching { TaskStatus.valueOf(status) }.getOrDefault(TaskStatus.PENDING),
        score = score,
        isSubmitted = isSubmitted
    )

    companion object {
        fun fromDomain(domain: TaskItem): TaskEntity = TaskEntity(
            id = domain.id,
            courseId = domain.courseId,
            courseName = domain.courseName,
            title = domain.title,
            type = domain.type.name,
            dueDateTime = domain.dueDateTime,
            remainingHours = domain.remainingHours,
            status = domain.status.name,
            score = domain.score,
            isSubmitted = domain.isSubmitted
        )
    }
}
