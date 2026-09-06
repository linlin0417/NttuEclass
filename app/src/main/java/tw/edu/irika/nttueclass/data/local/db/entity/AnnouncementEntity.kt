package tw.edu.irika.nttueclass.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import tw.edu.irika.nttueclass.domain.model.Announcement

@Entity(tableName = "announcements")
data class AnnouncementEntity(
    @PrimaryKey val id: String,
    val courseId: String,
    val courseName: String,
    val title: String,
    val date: String,
    val author: String,
    val contentSummary: String,
    val isUnread: Boolean,
    val hasAttachment: Boolean,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): Announcement = Announcement(
        id = id,
        courseId = courseId,
        courseName = courseName,
        title = title,
        date = date,
        author = author,
        contentSummary = contentSummary,
        isUnread = isUnread,
        hasAttachment = hasAttachment
    )

    companion object {
        fun fromDomain(domain: Announcement): AnnouncementEntity = AnnouncementEntity(
            id = domain.id,
            courseId = domain.courseId,
            courseName = domain.courseName,
            title = domain.title,
            date = domain.date,
            author = domain.author,
            contentSummary = domain.contentSummary,
            isUnread = domain.isUnread,
            hasAttachment = domain.hasAttachment
        )
    }
}
