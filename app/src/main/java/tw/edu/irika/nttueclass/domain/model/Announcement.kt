package tw.edu.irika.nttueclass.domain.model

data class Announcement(
    val id: String,
    val courseId: String,
    val courseName: String,
    val title: String,
    val date: String,
    val author: String,
    val contentSummary: String,
    val isUnread: Boolean = false,
    val hasAttachment: Boolean = false
)
