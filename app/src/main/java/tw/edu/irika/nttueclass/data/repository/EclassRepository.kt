package tw.edu.irika.nttueclass.data.repository

import android.content.Context
import kotlinx.coroutines.flow.Flow
import tw.edu.irika.nttueclass.data.local.db.AppDatabase
import tw.edu.irika.nttueclass.data.local.security.SecureCredentialStorage
import tw.edu.irika.nttueclass.data.remote.auth.AuthLockoutManager
import tw.edu.irika.nttueclass.data.remote.auth.AuthManager
import tw.edu.irika.nttueclass.data.remote.client.NttuHttpClient
import tw.edu.irika.nttueclass.domain.model.Announcement
import tw.edu.irika.nttueclass.domain.model.Course
import tw.edu.irika.nttueclass.domain.model.CourseMaterial
import tw.edu.irika.nttueclass.domain.model.TaskItem
import tw.edu.irika.nttueclass.domain.model.TimetableSlot
import tw.edu.irika.nttueclass.domain.repository.IAnnouncementRepository
import tw.edu.irika.nttueclass.domain.repository.ICourseMaterialRepository
import tw.edu.irika.nttueclass.domain.repository.ICourseRepository
import tw.edu.irika.nttueclass.domain.repository.IDataSyncCoordinator
import tw.edu.irika.nttueclass.domain.repository.ITaskRepository
import tw.edu.irika.nttueclass.domain.repository.ITimetableRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * 輕量化向前相容委派 Repository (Backward-Compatible Delegate)
 * 內部將所有業務邏輯全面委派至模組化之 Domain Repository 實作
 */
@Singleton
class EclassRepository @Inject constructor(
    @param:dagger.hilt.android.qualifiers.ApplicationContext val appContext: Context,
    val timetableRepository: ITimetableRepository,
    val courseRepository: ICourseRepository,
    val announcementRepository: IAnnouncementRepository,
    val taskRepository: ITaskRepository,
    val courseMaterialRepository: ICourseMaterialRepository,
    val dataSyncCoordinator: IDataSyncCoordinator,
    val storage: SecureCredentialStorage
) {
    constructor(context: Context) : this(
        createDelegateComponents(context.applicationContext)
    )

    private constructor(components: DelegateComponents) : this(
        appContext = components.appContext,
        timetableRepository = components.timetableRepository,
        courseRepository = components.courseRepository,
        announcementRepository = components.announcementRepository,
        taskRepository = components.taskRepository,
        courseMaterialRepository = components.courseMaterialRepository,
        dataSyncCoordinator = components.dataSyncCoordinator,
        storage = components.storage
    )

    private class DelegateComponents(
        val appContext: Context,
        val timetableRepository: ITimetableRepository,
        val courseRepository: ICourseRepository,
        val announcementRepository: IAnnouncementRepository,
        val taskRepository: ITaskRepository,
        val courseMaterialRepository: ICourseMaterialRepository,
        val dataSyncCoordinator: IDataSyncCoordinator,
        val storage: SecureCredentialStorage
    )

    companion object {
        fun isLoginPageHtml(html: String): Boolean = SessionManager.isLoginPageHtml(html)

        private fun createDelegateComponents(appContext: Context): DelegateComponents {
            val db = AppDatabase.getInstance(appContext)
            val storage = SecureCredentialStorage(appContext)
            val cookieJar = NttuHttpClient.cookieJar
            val client = NttuHttpClient.client
            val authManager = AuthManager(appContext)
            val sessionManager = SessionManager(client, cookieJar, storage, authManager)

            var courseRepoRef: ICourseRepository? = null
            val courseRepoProvider = Provider<ICourseRepository> { courseRepoRef!! }
            val courseRepo = CourseRepository(
                db.courseDao(),
                db.timetableDao(),
                db.announcementDao(),
                db.taskDao(),
                sessionManager,
                storage
            )
            courseRepoRef = courseRepo

            val timetableRepo = TimetableRepository(
                db.timetableDao(),
                db.courseDao(),
                sessionManager,
                storage,
                courseRepoProvider
            )
            val announcementRepo = AnnouncementRepository(
                db.announcementDao(),
                db.courseDao(),
                sessionManager,
                storage,
                courseRepoProvider
            )
            val taskRepo = TaskRepository(
                db.taskDao(),
                db.courseDao(),
                sessionManager,
                storage,
                courseRepoProvider
            )
            val materialRepo = CourseMaterialRepository(
                appContext,
                db.courseMaterialDao(),
                sessionManager,
                storage,
                client
            )
            val coordinator = DataSyncCoordinator(
                appContext,
                timetableRepo,
                courseRepo,
                announcementRepo,
                taskRepo,
                db.timetableDao(),
                db.courseDao(),
                db.announcementDao(),
                db.taskDao(),
                db.courseMaterialDao(),
                storage
            )

            return DelegateComponents(
                appContext = appContext,
                timetableRepository = timetableRepo,
                courseRepository = courseRepo,
                announcementRepository = announcementRepo,
                taskRepository = taskRepo,
                courseMaterialRepository = materialRepo,
                dataSyncCoordinator = coordinator,
                storage = storage
            )
        }
    }

    // ==========================================
    // 離線優先資料流
    // ==========================================
    fun getTimetableStream(): Flow<List<TimetableSlot>> = timetableRepository.getTimetableStream()
    fun getCoursesStream(): Flow<List<Course>> = courseRepository.getCoursesStream()
    fun getAnnouncementsStream(): Flow<List<Announcement>> = announcementRepository.getAnnouncementsStream()
    fun getTasksStream(): Flow<List<TaskItem>> = taskRepository.getTasksStream()
    fun getMaterialsStream(courseId: String): Flow<List<CourseMaterial>> = courseMaterialRepository.getMaterialsStream(courseId)

    // ==========================================
    // 遠端同步與協調
    // ==========================================
    suspend fun syncAllData(): Result<Unit> = dataSyncCoordinator.syncAllData()
    suspend fun syncTimetable(): Boolean = timetableRepository.syncTimetable()
    suspend fun syncCourses(): Boolean = courseRepository.syncCourses()
    suspend fun syncAnnouncements(): Boolean = announcementRepository.syncAnnouncements()
    suspend fun syncCourseAnnouncements(courseId: String, courseName: String = ""): Result<List<Announcement>> =
        announcementRepository.syncCourseAnnouncements(courseId, courseName)
    suspend fun syncTasks(): Boolean = taskRepository.syncTasks()
    suspend fun syncCourseTasks(courseId: String, courseName: String = ""): Result<List<TaskItem>> =
        taskRepository.syncCourseTasks(courseId, courseName)

    // ==========================================
    // CRUD 與快取管理
    // ==========================================
    suspend fun addTimetableSlot(slot: TimetableSlot) = timetableRepository.addTimetableSlot(slot)
    suspend fun deleteTimetableSlot(dayOfWeek: Int, periodNumber: Int) = timetableRepository.deleteTimetableSlot(dayOfWeek, periodNumber)
    suspend fun addCourse(course: Course) = courseRepository.addCourse(course)
    suspend fun deleteCourse(courseId: String) = courseRepository.deleteCourse(courseId)

    suspend fun syncCourseMaterials(courseId: String): Result<List<CourseMaterial>> = courseMaterialRepository.syncCourseMaterials(courseId)
    suspend fun downloadMaterial(material: CourseMaterial, onProgress: (Float) -> Unit): Result<File> =
        courseMaterialRepository.downloadMaterial(material, onProgress)
    suspend fun deleteDownloadedMaterial(material: CourseMaterial) = courseMaterialRepository.deleteDownloadedMaterial(material)
    suspend fun addCustomMaterial(material: CourseMaterial) = courseMaterialRepository.addCustomMaterial(material)
    suspend fun deleteMaterial(materialId: String) = courseMaterialRepository.deleteMaterial(materialId)

    suspend fun clearAllData() = dataSyncCoordinator.clearAllData()
}
