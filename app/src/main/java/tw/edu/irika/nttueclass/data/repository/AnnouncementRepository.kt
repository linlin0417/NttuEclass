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
import tw.edu.irika.nttueclass.data.local.db.dao.AnnouncementDao
import tw.edu.irika.nttueclass.data.local.db.dao.CourseDao
import tw.edu.irika.nttueclass.data.local.db.entity.AnnouncementEntity
import tw.edu.irika.nttueclass.data.local.security.SecureCredentialStorage
import tw.edu.irika.nttueclass.data.remote.client.NttuHttpClient
import tw.edu.irika.nttueclass.data.remote.parser.AnnouncementHtmlParser
import tw.edu.irika.nttueclass.domain.model.Announcement
import tw.edu.irika.nttueclass.domain.repository.IAnnouncementRepository
import tw.edu.irika.nttueclass.domain.repository.ICourseRepository
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class AnnouncementRepository @Inject constructor(
    private val announcementDao: AnnouncementDao,
    private val courseDao: CourseDao,
    private val sessionManager: SessionManager,
    private val storage: SecureCredentialStorage,
    private val courseRepositoryProvider: Provider<ICourseRepository>
) : IAnnouncementRepository {

    override fun getAnnouncementsStream(): Flow<List<Announcement>> {
        return announcementDao.getAllAnnouncements().map { list ->
            if (!storage.isLoggedIn()) emptyList() else list.map { it.toDomain() }
        }
    }

    override suspend fun syncAnnouncements(): Boolean = withContext(Dispatchers.IO) {
        var anySuccess = false

        // ── 第一階段：逐課程並行抓取各課程公告 ──
        val courses = courseDao.getAllCoursesList()
        val validCourses = courses.filter { it.id.isNotBlank() && !it.id.startsWith("c_") && it.id.all { ch -> ch.isDigit() } }

        if (validCourses.isNotEmpty()) {
            val semaphore = Semaphore(3)
            val perCourseResults = coroutineScope {
                validCourses.map { course ->
                    async {
                        semaphore.withPermit {
                            runCatching {
                                val url = "${NttuHttpClient.BASE_URL}/course/bulletin/${course.id}"
                                val html = sessionManager.fetchHtmlWithAuth(url)
                                val announcements = AnnouncementHtmlParser.parse(
                                    html,
                                    defaultCourseId = course.id,
                                    defaultCourseName = course.name
                                )
                                if (announcements.isNotEmpty()) {
                                    announcementDao.deleteByCourseId(course.id)
                                    announcementDao.insertAll(announcements.map { AnnouncementEntity.fromDomain(it) })
                                }
                                announcements
                            }.getOrDefault(emptyList())
                        }
                    }
                }.awaitAll()
            }
            if (perCourseResults.any { it.isNotEmpty() }) {
                anySuccess = true
            }
        }

        // ── 第二階段：全站公告總覽作為補充（擷取未歸屬特定課程的公告）──
        val globalUrls = listOf(
            "${NttuHttpClient.BASE_URL}/dashboard/latestBulletin",
            "${NttuHttpClient.BASE_URL}/bulletin",
            "${NttuHttpClient.BASE_URL}/app/bulletin/"
        )
        for (url in globalUrls) {
            try {
                val html = sessionManager.fetchHtmlWithAuth(url)
                if (html.isBlank()) continue
                val globalAnnouncements = AnnouncementHtmlParser.parse(html)
                if (globalAnnouncements.isNotEmpty()) {
                    // UPSERT：利用 OnConflictStrategy.REPLACE 合併，不先 deleteAll
                    announcementDao.insertAll(globalAnnouncements.map { AnnouncementEntity.fromDomain(it) })
                    anySuccess = true
                    break
                }
            } catch (e: SessionExpiredException) {
                throw e
            } catch (_: Exception) {
            }
        }

        anySuccess
    }

    override suspend fun syncCourseAnnouncements(courseId: String, courseName: String): Result<List<Announcement>> = withContext(Dispatchers.IO) {
        if (courseId.isBlank() || courseId.startsWith("c_") || !courseId.all { it.isDigit() }) {
            return@withContext Result.success(emptyList())
        }

        try {
            val resolvedCourseName = courseName.ifBlank {
                courseDao.getCourseById(courseId)?.name.orEmpty()
            }

            val url = "${NttuHttpClient.BASE_URL}/course/bulletin/$courseId"
            val html = sessionManager.fetchHtmlWithAuth(url)
            val announcements = AnnouncementHtmlParser.parse(
                html,
                defaultCourseId = courseId,
                defaultCourseName = resolvedCourseName
            )

            if (announcements.isNotEmpty()) {
                announcementDao.deleteByCourseId(courseId)
                announcementDao.insertAll(announcements.map { AnnouncementEntity.fromDomain(it) })
                courseRepositoryProvider.get().updateCourseCounters()
            }
            Result.success(announcements)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
