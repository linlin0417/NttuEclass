package tw.edu.irika.nttueclass.domain.model

enum class TaskType {
    ASSIGNMENT, // 作業
    QUIZ,       // 測驗
    EXAM        // 考試
}

enum class TaskStatus {
    PENDING,    // 待繳交
    URGENT,     // 小於 24 小時截止
    WARNING,    // 小於 3 天截止
    COMPLETED   // 已繳交 / 已評分
}

data class TaskItem(
    val id: String,
    val courseId: String,
    val courseName: String,
    val title: String,
    val type: TaskType,
    val dueDateTime: String,
    val remainingHours: Long,
    val status: TaskStatus,
    val score: String? = null,
    val isSubmitted: Boolean = false
)
