package tw.edu.irika.nttueclass.presentation.timetable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tw.edu.irika.nttueclass.domain.model.Course
import tw.edu.irika.nttueclass.domain.model.TimetableSlot
import tw.edu.irika.nttueclass.domain.repository.ICourseRepository
import tw.edu.irika.nttueclass.domain.repository.ITimetableRepository
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class TimetableViewModel @Inject constructor(
    private val timetableRepository: ITimetableRepository,
    private val courseRepository: ICourseRepository
) : ViewModel() {

    val currentDayOfWeek: Int
        get() = LocalDate.now().dayOfWeek.value.coerceIn(1, 5)

    private val _selectedDay = MutableStateFlow(LocalDate.now().dayOfWeek.value.coerceIn(1, 5))
    private val _isWeekView = MutableStateFlow(false)
    private val _selectedSlotForDetail = MutableStateFlow<TimetableSlot?>(null)
    private val _showAddDialog = MutableStateFlow(false)
    private val _editingSlot = MutableStateFlow<TimetableSlot?>(null)
    private val _isSyncing = MutableStateFlow(false)

    private data class ViewControlState(
        val selectedDay: Int,
        val isWeekView: Boolean,
        val selectedSlotForDetail: TimetableSlot?,
        val showAddDialog: Boolean,
        val editingSlot: TimetableSlot?,
        val isSyncing: Boolean
    )

    private val controlStateFlow = combine(
        _selectedDay,
        _isWeekView,
        _selectedSlotForDetail,
        _showAddDialog,
        _editingSlot
    ) { day, weekView, detailSlot, addDialog, editSlot ->
        Tuple5(day, weekView, detailSlot, addDialog, editSlot)
    }.combine(_isSyncing) { tuple, syncing ->
        ViewControlState(
            selectedDay = tuple.a,
            isWeekView = tuple.b,
            selectedSlotForDetail = tuple.c,
            showAddDialog = tuple.d,
            editingSlot = tuple.e,
            isSyncing = syncing
        )
    }

    val uiState: StateFlow<TimetableUiState> = combine(
        timetableRepository.getTimetableStream(),
        courseRepository.getCoursesStream(),
        controlStateFlow
    ) { slots, courses, controls ->
        TimetableUiState.Success(
            slots = slots,
            courses = courses,
            selectedDay = controls.selectedDay,
            isWeekView = controls.isWeekView,
            currentDayOfWeek = currentDayOfWeek,
            selectedSlotForDetail = controls.selectedSlotForDetail,
            showAddDialog = controls.showAddDialog,
            editingSlot = controls.editingSlot,
            isSyncing = controls.isSyncing
        ) as TimetableUiState
    }.catch { e ->
        emit(TimetableUiState.Error(e.message ?: "無法載入課表資料"))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = TimetableUiState.Loading
    )

    fun selectDay(day: Int) {
        _selectedDay.value = day
    }

    fun toggleViewMode() {
        _isWeekView.value = !_isWeekView.value
    }

    fun setWeekView(isWeekView: Boolean) {
        _isWeekView.value = isWeekView
    }

    fun backToToday() {
        _selectedDay.value = currentDayOfWeek
    }

    fun selectSlotForDetail(slot: TimetableSlot?) {
        _selectedSlotForDetail.value = slot
    }

    fun openAddDialog(slotToEdit: TimetableSlot? = null) {
        _editingSlot.value = slotToEdit
        _showAddDialog.value = true
    }

    fun dismissAddDialog() {
        _showAddDialog.value = false
        _editingSlot.value = null
    }

    fun addOrUpdateSlot(slot: TimetableSlot) {
        viewModelScope.launch {
            try {
                timetableRepository.addTimetableSlot(slot)
                dismissAddDialog()
                _selectedSlotForDetail.value = null
            } catch (_: Exception) {
            }
        }
    }

    fun updateSlot(oldSlot: TimetableSlot, updatedSlot: TimetableSlot) {
        viewModelScope.launch {
            try {
                if (updatedSlot.dayOfWeek != oldSlot.dayOfWeek || updatedSlot.periodNumber != oldSlot.periodNumber) {
                    timetableRepository.deleteTimetableSlot(oldSlot.dayOfWeek, oldSlot.periodNumber)
                }
                timetableRepository.addTimetableSlot(updatedSlot)
                dismissAddDialog()
                _selectedSlotForDetail.value = null
            } catch (_: Exception) {
            }
        }
    }

    fun deleteSlot(dayOfWeek: Int, periodNumber: Int) {
        viewModelScope.launch {
            try {
                timetableRepository.deleteTimetableSlot(dayOfWeek, periodNumber)
                _selectedSlotForDetail.value = null
            } catch (_: Exception) {
            }
        }
    }

    fun syncTimetable(onCompleted: ((Result<Unit>) -> Unit)? = null) {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val ok = timetableRepository.syncTimetable()
                if (ok) {
                    onCompleted?.invoke(Result.success(Unit))
                } else {
                    onCompleted?.invoke(Result.failure(IOException("課表同步失敗，請檢查網路連線")))
                }
            } catch (e: Exception) {
                onCompleted?.invoke(Result.failure(e))
            } finally {
                _isSyncing.value = false
            }
        }
    }

    private data class Tuple5<A, B, C, D, E>(
        val a: A, val b: B, val c: C, val d: D, val e: E
    )
}
