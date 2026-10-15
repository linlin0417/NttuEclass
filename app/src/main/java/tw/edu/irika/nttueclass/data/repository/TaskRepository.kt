package tw.edu.irika.nttueclass.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import tw.edu.irika.nttueclass.data.local.db.dao.CourseDao
import tw.edu.irika.nttueclass.data.local.db.dao.TaskDao
import tw.edu.irika.nttueclass.data.local.db.entity.TaskEntity
import tw.edu.irika.nttueclass.data.local.security.SecureCredentialStorage
import tw.edu.irika.nttueclass.data.remote.client.NttuHttpClient
import tw.edu.irika.nttueclass.data.remote.parser.TaskHtmlParser
import tw.edu.irika.nttueclass.domain.model.TaskItem
import tw.edu.irika.nttueclass.domain.model.TaskType
import tw.edu.irika.nttueclass.domain.repository.ICourseRepository
import tw.edu.irika.nttueclass.domain.repository.ITaskRepository
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class TaskRepository @Inject constructor(
    private val taskDao: TaskDao,
    private val courseDao: CourseDao,
    private val sessionManager: SessionManager,
    private val storage: SecureCredentialStorage,
    private val courseRepositoryProvider: Provider<ICourseRepository>
) : ITaskRepository {

    override fun getTasksStream(): Flow<List<TaskItem>> {
        return taskDao.getAllTasks().map { list ->
            if (!storage.isLoggedIn()) emptyList() else list.map { it.toDomain() }
        }
    }

    override suspend fun syncTasks(): Boolean = withContext(Dispatchers.IO) {
        val courses = courseDao.getAllCoursesList()
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
                                val html = sessionManager.fetchHtmlWithAuth(url)
                                TaskHtmlParser.parse(html, defaultCourseId = course.id, defaultCourseName = course.name, defaultType = TaskType.ASSIGNMENT)
                            }.getOrDefault(emptyList())
                        }
                    },
                    async {
                        semaphore.withPermit {
                            runCatching {
                                val url = "${NttuHttpClient.BASE_URL}/course/examList/${course.id}"
                                val html = sessionManager.fetchHtmlWithAuth(url)
                                TaskHtmlParser.parse(html, defaultCourseId = course.id, defaultCourseName = course.name, defaultType = TaskType.QUIZ)
                            }.getOrDefault(emptyList())
                        }
                    },
                    async {
                        semaphore.withPermit {
                            runCatching {
                                val url = "${NttuHttpClient.BASE_URL}/course/questionnaireList/${course.id}"
                                val html = sessionManager.fetchHtmlWithAuth(url)
                                TaskHtmlParser.parse(html, defaultCourseId = course.id, defaultCourseName = course.name, defaultType = TaskType.QUESTIONNAIRE)
                            }.getOrDefault(emptyList())
                        }
                    }
                )
            }.awaitAll().flatten()
        }

        if (allTasks.isNotEmpty()) {
            taskDao.deleteAll()
            taskDao.insertAll(allTasks.map { TaskEntity.fromDomain(it) })
            return@withContext true
        }
        false
    }

    override suspend fun syncCourseTasks(courseId: String, courseName: String): Result<List<TaskItem>> = withContext(Dispatchers.IO) {
        if (courseId.isBlank() || courseId.startsWith("c_") || !courseId.all { it.isDigit() }) {
            return@withContext Result.success(emptyList())
        }

        try {
            val hwUrl = "${NttuHttpClient.BASE_URL}/course/homeworkList/$courseId"
            val examUrl = "${NttuHttpClient.BASE_URL}/course/examList/$courseId"
            val surveyUrl = "${NttuHttpClient.BASE_URL}/course/questionnaireList/$courseId"

            val resolvedCourseName = courseName.ifBlank {
                courseDao.getCourseById(courseId)?.name.orEmpty()
            }

            val (hwList, examList, surveyList) = coroutineScope {
                val hwJob = async {
                    runCatching {
                        val html = sessionManager.fetchHtmlWithAuth(hwUrl)
                        TaskHtmlParser.parse(html, defaultCourseId = courseId, defaultCourseName = resolvedCourseName, defaultType = TaskType.ASSIGNMENT)
                    }.getOrDefault(emptyList())
                }
                val examJob = async {
                    runCatching {
                        val html = sessionManager.fetchHtmlWithAuth(examUrl)
                        TaskHtmlParser.parse(html, defaultCourseId = courseId, defaultCourseName = resolvedCourseName, defaultType = TaskType.QUIZ)
                    }.getOrDefault(emptyList())
                }
                val surveyJob = async {
                    runCatching {
                        val html = sessionManager.fetchHtmlWithAuth(surveyUrl)
                        TaskHtmlParser.parse(html, defaultCourseId = courseId, defaultCourseName = resolvedCourseName, defaultType = TaskType.QUESTIONNAIRE)
                    }.getOrDefault(emptyList())
                }
                Triple(hwJob.await(), examJob.await(), surveyJob.await())
            }

            val courseAllTasks = hwList + examList + surveyList
            if (courseAllTasks.isNotEmpty()) {
                taskDao.insertAll(courseAllTasks.map { TaskEntity.fromDomain(it) })
                courseRepositoryProvider.get().updateCourseCounters()
            }
            Result.success(courseAllTasks)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
