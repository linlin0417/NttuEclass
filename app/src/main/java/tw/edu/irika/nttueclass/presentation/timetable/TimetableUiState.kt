package tw.edu.irika.nttueclass.presentation.timetable

import tw.edu.irika.nttueclass.domain.model.Course
import tw.edu.irika.nttueclass.domain.model.TimetableSlot

sealed interface TimetableUiState {
    data object Loading : TimetableUiState

    data class Success(
        val slots: List<TimetableSlot> = emptyList(),
        val courses: List<Course> = emptyList(),
        val selectedDay: Int = 1,
        val isWeekView: Boolean = false,
        val currentDayOfWeek: Int = 1,
        val selectedSlotForDetail: TimetableSlot? = null,
        val showAddDialog: Boolean = false,
        val editingSlot: TimetableSlot? = null,
        val isSyncing: Boolean = false
    ) : TimetableUiState

    data class Error(val message: String, val canRetry: Boolean = true) : TimetableUiState
}
