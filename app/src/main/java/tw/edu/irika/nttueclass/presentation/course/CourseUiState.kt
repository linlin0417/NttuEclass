package tw.edu.irika.nttueclass.presentation.course

import tw.edu.irika.nttueclass.domain.model.Announcement
import tw.edu.irika.nttueclass.domain.model.Course
import tw.edu.irika.nttueclass.domain.model.TaskItem
import tw.edu.irika.nttueclass.domain.model.TimetableSlot

sealed interface CourseUiState {
    data object Loading : CourseUiState

    data class Success(
        val courses: List<Course> = emptyList(),
        val filteredCourses: List<Course> = emptyList(),
        val announcements: List<Announcement> = emptyList(),
        val filteredAnnouncements: List<Announcement> = emptyList(),
        val tasks: List<TaskItem> = emptyList(),
        val timetableSlots: List<TimetableSlot> = emptyList(),
        val searchQuery: String = "",
        val selectedSemester: String = "全部",
        val availableSemesters: List<String> = emptyList(),
        val selectedTab: Int = 0,
        val showAddCourseDialog: Boolean = false,
        val selectedCourseForDetail: Course? = null,
        val selectedAnnouncementForDetail: Announcement? = null,
        val isSyncing: Boolean = false
    ) : CourseUiState

    data class Error(val message: String, val canRetry: Boolean = true) : CourseUiState
}
