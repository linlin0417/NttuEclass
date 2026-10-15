package tw.edu.irika.nttueclass.presentation.course

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
import tw.edu.irika.nttueclass.domain.model.Announcement
import tw.edu.irika.nttueclass.domain.model.Course
import tw.edu.irika.nttueclass.domain.model.TaskItem
import tw.edu.irika.nttueclass.domain.model.TimetableSlot
import tw.edu.irika.nttueclass.domain.repository.IAnnouncementRepository
import tw.edu.irika.nttueclass.domain.repository.ICourseRepository
import tw.edu.irika.nttueclass.domain.repository.ITaskRepository
import tw.edu.irika.nttueclass.domain.repository.ITimetableRepository
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class CourseViewModel @Inject constructor(
    private val courseRepository: ICourseRepository,
    private val announcementRepository: IAnnouncementRepository,
    private val taskRepository: ITaskRepository,
    private val timetableRepository: ITimetableRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedSemester = MutableStateFlow("全部")
    private val _selectedTab = MutableStateFlow(0)
    private val _showAddCourseDialog = MutableStateFlow(false)
    private val _selectedCourseForDetail = MutableStateFlow<Course?>(null)
    private val _selectedAnnouncementForDetail = MutableStateFlow<Announcement?>(null)
    private val _isSyncing = MutableStateFlow(false)
    private val _retryTrigger = MutableStateFlow(0)

    private data class CourseRawData(
        val courses: List<Course>,
        val announcements: List<Announcement>,
        val tasks: List<TaskItem>,
        val timetableSlots: List<TimetableSlot>
    )

    private data class CourseControlState(
        val searchQuery: String,
        val selectedSemester: String,
        val selectedTab: Int,
        val showAddCourseDialog: Boolean,
        val selectedCourseForDetail: Course?,
        val selectedAnnouncementForDetail: Announcement?,
        val isSyncing: Boolean
    )

    private val controlStateFlow = combine(
        _searchQuery,
        _selectedSemester,
        _selectedTab,
        _showAddCourseDialog,
        _selectedCourseForDetail
    ) { query, semester, tab, showAdd, detailCourse ->
        Tuple5(query, semester, tab, showAdd, detailCourse)
    }.combine(
        combine(_selectedAnnouncementForDetail, _isSyncing) { detailAnn, syncing ->
            Pair(detailAnn, syncing)
        }
    ) { t5, (detailAnn, syncing) ->
        CourseControlState(
            searchQuery = t5.a,
            selectedSemester = t5.b,
            selectedTab = t5.c,
            showAddCourseDialog = t5.d,
            selectedCourseForDetail = t5.e,
            selectedAnnouncementForDetail = detailAnn,
            isSyncing = syncing
        )
    }

    val uiState: StateFlow<CourseUiState> = combine(
        courseRepository.getCoursesStream(),
        announcementRepository.getAnnouncementsStream(),
        taskRepository.getTasksStream(),
        timetableRepository.getTimetableStream()
    ) { courses, announcements, tasks, timetableSlots ->
        CourseRawData(courses, announcements, tasks, timetableSlots)
    }.combine(controlStateFlow) { rawData, controls ->
        val query = controls.searchQuery.trim()
        val distinctSemesters = rawData.courses.map { it.semester }.filter { it.isNotBlank() }.distinct().sortedDescending()
        val availableSemesters = if (distinctSemesters.isNotEmpty()) listOf("全部") + distinctSemesters else listOf("全部")

        val semesterFiltered = if (controls.selectedSemester == "全部" || controls.selectedSemester.isBlank()) {
            rawData.courses
        } else {
            rawData.courses.filter { it.semester == controls.selectedSemester }
        }

        val filteredCourses = if (query.isBlank()) {
            semesterFiltered
        } else {
            semesterFiltered.filter {
                it.name.contains(query, ignoreCase = true) ||
                    it.instructor.contains(query, ignoreCase = true) ||
                    it.classroom.contains(query, ignoreCase = true)
            }
        }

        val filteredAnnouncements = if (query.isBlank()) {
            rawData.announcements
        } else {
            rawData.announcements.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.courseName.contains(query, ignoreCase = true) ||
                    it.author.contains(query, ignoreCase = true)
            }
        }

        CourseUiState.Success(
            courses = rawData.courses,
            filteredCourses = filteredCourses,
            announcements = rawData.announcements,
            filteredAnnouncements = filteredAnnouncements,
            tasks = rawData.tasks,
            timetableSlots = rawData.timetableSlots,
            searchQuery = controls.searchQuery,
            selectedSemester = controls.selectedSemester,
            availableSemesters = availableSemesters,
            selectedTab = controls.selectedTab,
            showAddCourseDialog = controls.showAddCourseDialog,
            selectedCourseForDetail = controls.selectedCourseForDetail,
            selectedAnnouncementForDetail = controls.selectedAnnouncementForDetail,
            isSyncing = controls.isSyncing
        ) as CourseUiState
    }.catch { e ->
        emit(CourseUiState.Error(e.message ?: "無法載入課程與公告資料"))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = CourseUiState.Loading
    )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onSemesterSelect(semester: String) {
        _selectedSemester.value = semester
    }

    fun onTabSelect(tab: Int) {
        _selectedTab.value = tab
    }

    fun showAddCourseDialog(show: Boolean) {
        _showAddCourseDialog.value = show
    }

    fun selectCourseForDetail(course: Course?) {
        _selectedCourseForDetail.value = course
    }

    fun selectAnnouncementForDetail(announcement: Announcement?) {
        _selectedAnnouncementForDetail.value = announcement
    }

    fun addCourse(course: Course) {
        viewModelScope.launch {
            try {
                courseRepository.addCourse(course)
                _showAddCourseDialog.value = false
            } catch (_: Exception) {
            }
        }
    }

    fun deleteCourse(courseId: String) {
        viewModelScope.launch {
            try {
                courseRepository.deleteCourse(courseId)
                _selectedCourseForDetail.value = null
            } catch (_: Exception) {
            }
        }
    }

    fun syncCourses(onCompleted: ((Result<Unit>) -> Unit)? = null) {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val ok = courseRepository.syncCourses()
                if (ok) {
                    onCompleted?.invoke(Result.success(Unit))
                } else {
                    onCompleted?.invoke(Result.failure(IOException("課程同步失敗，請檢查網路連線")))
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
