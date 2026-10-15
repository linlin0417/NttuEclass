package tw.edu.irika.nttueclass.domain.repository

import kotlinx.coroutines.flow.Flow
import tw.edu.irika.nttueclass.domain.model.Announcement

interface IAnnouncementRepository {
    fun getAnnouncementsStream(): Flow<List<Announcement>>
    suspend fun syncAnnouncements(): Boolean
    suspend fun syncCourseAnnouncements(courseId: String, courseName: String = ""): Result<List<Announcement>>
}
