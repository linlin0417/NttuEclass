package tw.edu.irika.nttueclass.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tw.edu.irika.nttueclass.domain.model.AcademicTermHelper
import tw.edu.irika.nttueclass.domain.model.Announcement
import tw.edu.irika.nttueclass.domain.model.Course
import tw.edu.irika.nttueclass.domain.model.StandardPeriods
import tw.edu.irika.nttueclass.domain.model.TaskItem
import tw.edu.irika.nttueclass.domain.model.TaskStatus
import tw.edu.irika.nttueclass.domain.model.TimetableSlot
import tw.edu.irika.nttueclass.data.local.security.SecureCredentialStorage
import tw.edu.irika.nttueclass.domain.repository.IAnnouncementRepository
import tw.edu.irika.nttueclass.domain.repository.ICourseRepository
import tw.edu.irika.nttueclass.domain.repository.IDataSyncCoordinator
import tw.edu.irika.nttueclass.domain.repository.ITaskRepository
import tw.edu.irika.nttueclass.domain.repository.ITimetableRepository
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val timetableRepository: ITimetableRepository,
    private val courseRepository: ICourseRepository,
    private val taskRepository: ITaskRepository,
    private val announcementRepository: IAnnouncementRepository,
    private val dataSyncCoordinator: IDataSyncCoordinator,
    private val storage: SecureCredentialStorage
) : ViewModel() {

    private val _isSyncing = MutableStateFlow(false)

    private val tickerFlow = flow {
        while (true) {
            emit(LocalTime.now())
            delay(60_000L)
        }
    }

    private data class DashboardRawData(
        val timetableSlots: List<TimetableSlot>,
        val courses: List<Course>,
        val tasks: List<TaskItem>,
        val announcements: List<Announcement>
    )

    val uiState: StateFlow<DashboardUiState> = combine(
        timetableRepository.getTimetableStream(),
        courseRepository.getCoursesStream(),
        taskRepository.getTasksStream(),
        announcementRepository.getAnnouncementsStream()
    ) { slots, courses, tasks, announcements ->
        DashboardRawData(slots, courses, tasks, announcements)
    }.combine(tickerFlow) { rawData, currentLocalTime ->
        computeSuccessState(rawData, currentLocalTime, _isSyncing.value)
    }.combine(_isSyncing) { state, syncing ->
        state.copy(isSyncing = syncing) as DashboardUiState
    }.catch { e ->
        emit(DashboardUiState.Error(e.message ?: "無法載入首頁資料"))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = DashboardUiState.Loading
    )

    private fun computeSuccessState(
        rawData: DashboardRawData,
        currentLocalTime: LocalTime,
        isSyncing: Boolean
    ): DashboardUiState.Success {
        val now = LocalDate.now()
        val todayWeekday = now.dayOfWeek.value // 1=Mon .. 7=Sun
        val weekdayNames = listOf("週一", "週二", "週三", "週四", "週五", "週六", "週日")
        val weekdayStr = weekdayNames.getOrElse(todayWeekday - 1) { "今日" }
        val semesterDisplay = AcademicTermHelper.getCurrentSemesterDisplay(now)
        val dateSubtitle = "$semesterDisplay · 今日$weekdayStr · ${now.monthValue}月${now.dayOfMonth}日"

        val todaySlots = rawData.timetableSlots
            .filter { it.dayOfWeek == todayWeekday }
            .sortedBy { it.periodNumber }

        val nextSlot = todaySlots.firstOrNull { slot ->
            val period = StandardPeriods.getByPeriodNumber(slot.periodNumber)
            if (period != null) {
                val parts = period.endTime.split(":")
                val endHour = parts.getOrNull(0)?.toIntOrNull() ?: 23
                val endMinute = parts.getOrNull(1)?.toIntOrNull() ?: 59
                val endTime = LocalTime.of(endHour, endMinute)
                currentLocalTime.isBefore(endTime)
            } else true
        }

        val urgentTask = run {
            val activeTasks = rawData.tasks.filter { !it.isSubmitted && it.status != TaskStatus.OVERDUE }
            if (activeTasks.isNotEmpty()) {
                activeTasks.sortedWith(
                    compareBy(
                        { it.status != TaskStatus.URGENT },
                        { it.status != TaskStatus.WARNING },
                        { it.remainingHours }
                    )
                ).firstOrNull()
            } else {
                rawData.tasks.filter { !it.isSubmitted && it.status == TaskStatus.OVERDUE }
                    .maxByOrNull { it.remainingHours }
            }
        }

        val latestNotices = rawData.announcements.take(2)
        val isLoggedIn = storage.isLoggedIn()
        val currentStudentId = storage.getStudentId()
        val greeting = if (isLoggedIn && !currentStudentId.isNullOrBlank()) {
            "${currentStudentId} 同學，你好！"
        } else {
            "同學，你好！"
        }

        return DashboardUiState.Success(
            studentId = currentStudentId,
            isLoggedIn = isLoggedIn,
            greeting = greeting,
            dateSubtitle = dateSubtitle,
            nextSlot = nextSlot,
            todaySlots = todaySlots,
            timetableSlots = rawData.timetableSlots,
            courses = rawData.courses,
            urgentTask = urgentTask,
            hasTasks = rawData.tasks.isNotEmpty(),
            latestNotices = latestNotices,
            isSyncing = isSyncing
        )
    }

    fun syncData(onCompleted: ((Result<Unit>) -> Unit)? = null) {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val result = dataSyncCoordinator.syncAllData()
                onCompleted?.invoke(result)
            } catch (e: Exception) {
                onCompleted?.invoke(Result.failure(e))
            } finally {
                _isSyncing.value = false
            }
        }
    }
}
