package tw.edu.irika.nttueclass.presentation.course

import tw.edu.irika.nttueclass.domain.model.Announcement
import tw.edu.irika.nttueclass.domain.model.Course
import tw.edu.irika.nttueclass.domain.model.CourseMaterial
import tw.edu.irika.nttueclass.domain.model.MaterialType
import tw.edu.irika.nttueclass.domain.model.TaskItem
import tw.edu.irika.nttueclass.domain.model.TimetableSlot

sealed interface CourseDetailUiState {
    data object Loading : CourseDetailUiState

    data class Success(
        val course: Course,
        val materials: List<CourseMaterial> = emptyList(),
        val filteredMaterials: List<CourseMaterial> = emptyList(),
        val courseAnnouncements: List<Announcement> = emptyList(),
        val courseTasks: List<TaskItem> = emptyList(),
        val courseSlots: List<TimetableSlot> = emptyList(),
        val selectedTab: Int = 0,
        val searchQuery: String = "",
        val selectedTypeFilter: MaterialType? = null,
        val showOnlyDownloaded: Boolean = false,
        val isSyncingMaterials: Boolean = false,
        val isSyncingTasks: Boolean = false,
        val isSyncingAnnouncements: Boolean = false,
        val showAddMaterialDialog: Boolean = false,
        val selectedAnnouncementForDetail: Announcement? = null,
        val selectedTaskForDetail: TaskItem? = null,
        val downloadProgressMap: Map<String, Float> = emptyMap()
    ) : CourseDetailUiState

    data class Error(val message: String, val canRetry: Boolean = true) : CourseDetailUiState
}
