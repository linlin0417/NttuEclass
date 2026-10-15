package tw.edu.irika.nttueclass.presentation.dashboard

import tw.edu.irika.nttueclass.domain.model.Announcement
import tw.edu.irika.nttueclass.domain.model.Course
import tw.edu.irika.nttueclass.domain.model.TaskItem
import tw.edu.irika.nttueclass.domain.model.TimetableSlot

sealed interface DashboardUiState {
    data object Loading : DashboardUiState

    data class Success(
        val studentId: String? = null,
        val isLoggedIn: Boolean = false,
        val greeting: String = "同學，你好！",
        val dateSubtitle: String = "",
        val nextSlot: TimetableSlot? = null,
        val todaySlots: List<TimetableSlot> = emptyList(),
        val timetableSlots: List<TimetableSlot> = emptyList(),
        val courses: List<Course> = emptyList(),
        val urgentTask: TaskItem? = null,
        val hasTasks: Boolean = false,
        val latestNotices: List<Announcement> = emptyList(),
        val isSyncing: Boolean = false
    ) : DashboardUiState

    data class Error(val message: String, val canRetry: Boolean = true) : DashboardUiState
}
