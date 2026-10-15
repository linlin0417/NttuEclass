package tw.edu.irika.nttueclass.domain.repository

import kotlinx.coroutines.flow.Flow
import tw.edu.irika.nttueclass.domain.model.TaskItem

interface ITaskRepository {
    fun getTasksStream(): Flow<List<TaskItem>>
    suspend fun syncTasks(): Boolean
    suspend fun syncCourseTasks(courseId: String, courseName: String = ""): Result<List<TaskItem>>
}
