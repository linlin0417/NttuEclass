package tw.edu.irika.nttueclass.presentation.course

import androidx.lifecycle.SavedStateHandle
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
import tw.edu.irika.nttueclass.domain.model.CourseMaterial
import tw.edu.irika.nttueclass.domain.model.MaterialType
import tw.edu.irika.nttueclass.domain.model.TaskItem
import tw.edu.irika.nttueclass.domain.model.TimetableSlot
import tw.edu.irika.nttueclass.domain.repository.IAnnouncementRepository
import tw.edu.irika.nttueclass.domain.repository.ICourseMaterialRepository
import tw.edu.irika.nttueclass.domain.repository.ICourseRepository
import tw.edu.irika.nttueclass.domain.repository.ITaskRepository
import tw.edu.irika.nttueclass.domain.repository.ITimetableRepository
import java.io.File
import javax.inject.Inject

@HiltViewModel
class CourseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val courseMaterialRepository: ICourseMaterialRepository,
    private val courseRepository: ICourseRepository,
    private val announcementRepository: IAnnouncementRepository,
    private val taskRepository: ITaskRepository,
    private val timetableRepository: ITimetableRepository
) : ViewModel() {

    val courseId: String = savedStateHandle.get<String>("courseId").orEmpty()

    constructor(
        courseId: String,
        courseMaterialRepository: ICourseMaterialRepository,
        courseRepository: ICourseRepository,
        announcementRepository: IAnnouncementRepository,
        taskRepository: ITaskRepository,
        timetableRepository: ITimetableRepository
    ) : this(
        SavedStateHandle(mapOf("courseId" to courseId)),
        courseMaterialRepository,
        courseRepository,
        announcementRepository,
        taskRepository,
        timetableRepository
    )

    private val _selectedTab = MutableStateFlow(0)
    private val _searchQuery = MutableStateFlow("")
    private val _selectedTypeFilter = MutableStateFlow<MaterialType?>(null)
    private val _showOnlyDownloaded = MutableStateFlow(false)
    private val _isSyncingMaterials = MutableStateFlow(false)
    private val _isSyncingTasks = MutableStateFlow(false)
    private val _isSyncingAnnouncements = MutableStateFlow(false)
    private val _showAddMaterialDialog = MutableStateFlow(false)
    private val _selectedAnnouncementForDetail = MutableStateFlow<Announcement?>(null)
    private val _selectedTaskForDetail = MutableStateFlow<TaskItem?>(null)
    private val _downloadProgressMap = MutableStateFlow<Map<String, Float>>(emptyMap())

    private data class DetailControls(
        val selectedTab: Int,
        val searchQuery: String,
        val selectedTypeFilter: MaterialType?,
        val showOnlyDownloaded: Boolean,
        val isSyncingMaterials: Boolean,
        val isSyncingTasks: Boolean,
        val isSyncingAnnouncements: Boolean,
        val showAddMaterialDialog: Boolean,
        val selectedAnnouncementForDetail: Announcement?,
        val selectedTaskForDetail: TaskItem?,
        val downloadProgressMap: Map<String, Float>
    )

    private val controlsFlow = combine(
        _selectedTab,
        _searchQuery,
        _selectedTypeFilter,
        _showOnlyDownloaded,
        _isSyncingMaterials
    ) { tab, search, typeFilter, onlyDownloaded, syncMat ->
        Tuple5(tab, search, typeFilter, onlyDownloaded, syncMat)
    }.combine(
        combine(
            _isSyncingTasks,
            _isSyncingAnnouncements,
            _showAddMaterialDialog,
            _selectedAnnouncementForDetail,
            _selectedTaskForDetail
        ) { syncTasks, syncAnn, showAdd, annDetail, taskDetail ->
            Tuple5(syncTasks, syncAnn, showAdd, annDetail, taskDetail)
        }
    ) { c1, c2 ->
        Pair(c1, c2)
    }.combine(_downloadProgressMap) { (c1, c2), progressMap ->
        DetailControls(
            selectedTab = c1.a,
            searchQuery = c1.b,
            selectedTypeFilter = c1.c,
            showOnlyDownloaded = c1.d,
            isSyncingMaterials = c1.e,
            isSyncingTasks = c2.a,
            isSyncingAnnouncements = c2.b,
            showAddMaterialDialog = c2.c,
            selectedAnnouncementForDetail = c2.d,
            selectedTaskForDetail = c2.e,
            downloadProgressMap = progressMap
        )
    }

    val uiState: StateFlow<CourseDetailUiState> = combine(
        courseRepository.getCoursesStream(),
        courseMaterialRepository.getMaterialsStream(courseId),
        announcementRepository.getAnnouncementsStream(),
        taskRepository.getTasksStream()
    ) { courses, materials, announcements, tasks ->
        RawDetailData(courses, materials, announcements, tasks)
    }.combine(timetableRepository.getTimetableStream()) { raw, timetableSlots ->
        Pair(raw, timetableSlots)
    }.combine(controlsFlow) { (raw, timetableSlots), controls ->
        val course = raw.courses.firstOrNull { it.id == courseId } ?: Course(
            id = courseId,
            code = "",
            name = "課程專區",
            instructor = "",
            classroom = "",
            credits = 0,
            semester = ""
        )

        val courseAnnouncements = raw.announcements.filter {
            it.courseName.contains(course.name) || course.name.contains(it.courseName) || it.courseId == course.id
        }

        val courseTasks = raw.tasks.filter {
            it.courseName.contains(course.name) || course.name.contains(it.courseName) || it.courseId == course.id
        }

        val courseSlots = timetableSlots.filter {
            it.courseName.contains(course.name) || course.name.contains(it.courseName)
        }.sortedWith(compareBy({ it.dayOfWeek }, { it.periodNumber }))

        val filteredMaterials = raw.materials.filter { material ->
            val matchesSearch = controls.searchQuery.isBlank() ||
                material.title.contains(controls.searchQuery, ignoreCase = true) ||
                material.chapterName.contains(controls.searchQuery, ignoreCase = true) ||
                material.fileExtension.contains(controls.searchQuery, ignoreCase = true)

            val matchesType = controls.selectedTypeFilter == null || material.type == controls.selectedTypeFilter
            val matchesDownloaded = !controls.showOnlyDownloaded || material.isFilePresentOnDisk

            matchesSearch && matchesType && matchesDownloaded
        }

        CourseDetailUiState.Success(
            course = course,
            materials = raw.materials,
            filteredMaterials = filteredMaterials,
            courseAnnouncements = courseAnnouncements,
            courseTasks = courseTasks,
            courseSlots = courseSlots,
            selectedTab = controls.selectedTab,
            searchQuery = controls.searchQuery,
            selectedTypeFilter = controls.selectedTypeFilter,
            showOnlyDownloaded = controls.showOnlyDownloaded,
            isSyncingMaterials = controls.isSyncingMaterials,
            isSyncingTasks = controls.isSyncingTasks,
            isSyncingAnnouncements = controls.isSyncingAnnouncements,
            showAddMaterialDialog = controls.showAddMaterialDialog,
            selectedAnnouncementForDetail = controls.selectedAnnouncementForDetail,
            selectedTaskForDetail = controls.selectedTaskForDetail,
            downloadProgressMap = controls.downloadProgressMap
        ) as CourseDetailUiState
    }.catch { e ->
        emit(CourseDetailUiState.Error(e.message ?: "無法載入課程詳情"))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = CourseDetailUiState.Loading
    )

    private data class RawDetailData(
        val courses: List<Course>,
        val materials: List<CourseMaterial>,
        val announcements: List<Announcement>,
        val tasks: List<TaskItem>
    )

    fun onTabSelect(tab: Int) {
        _selectedTab.value = tab
    }

    fun onSearchChange(query: String) {
        _searchQuery.value = query
    }

    fun onTypeFilterSelect(filter: MaterialType?) {
        _selectedTypeFilter.value = filter
    }

    fun onToggleOnlyDownloaded() {
        _showOnlyDownloaded.value = !_showOnlyDownloaded.value
    }

    fun setShowAddMaterialDialog(show: Boolean) {
        _showAddMaterialDialog.value = show
    }

    fun selectAnnouncementForDetail(announcement: Announcement?) {
        _selectedAnnouncementForDetail.value = announcement
    }

    fun selectTaskForDetail(task: TaskItem?) {
        _selectedTaskForDetail.value = task
    }

    fun syncMaterials(onCompleted: ((Result<List<CourseMaterial>>) -> Unit)? = null) {
        if (_isSyncingMaterials.value) return
        viewModelScope.launch {
            _isSyncingMaterials.value = true
            try {
                val res = courseMaterialRepository.syncCourseMaterials(courseId)
                onCompleted?.invoke(res)
            } catch (e: Exception) {
                onCompleted?.invoke(Result.failure(e))
            } finally {
                _isSyncingMaterials.value = false
            }
        }
    }

    fun syncAnnouncements(onCompleted: ((Result<List<Announcement>>) -> Unit)? = null) {
        if (_isSyncingAnnouncements.value) return
        viewModelScope.launch {
            _isSyncingAnnouncements.value = true
            try {
                val res = announcementRepository.syncCourseAnnouncements(courseId)
                onCompleted?.invoke(res)
            } catch (e: Exception) {
                onCompleted?.invoke(Result.failure(e))
            } finally {
                _isSyncingAnnouncements.value = false
            }
        }
    }

    fun syncTasks(courseName: String = "", onCompleted: ((Result<List<TaskItem>>) -> Unit)? = null) {
        if (_isSyncingTasks.value) return
        viewModelScope.launch {
            _isSyncingTasks.value = true
            try {
                val res = taskRepository.syncCourseTasks(courseId, courseName)
                onCompleted?.invoke(res)
            } catch (e: Exception) {
                onCompleted?.invoke(Result.failure(e))
            } finally {
                _isSyncingTasks.value = false
            }
        }
    }

    fun downloadMaterial(material: CourseMaterial, onResult: (Result<File>) -> Unit) {
        viewModelScope.launch {
            _downloadProgressMap.value = _downloadProgressMap.value + (material.id to 0.05f)
            try {
                val res = courseMaterialRepository.downloadMaterial(material) { p ->
                    _downloadProgressMap.value = _downloadProgressMap.value + (material.id to p)
                }
                onResult(res)
            } catch (e: Exception) {
                onResult(Result.failure(e))
            } finally {
                _downloadProgressMap.value = _downloadProgressMap.value - material.id
            }
        }
    }

    fun deleteDownloadedMaterial(material: CourseMaterial) {
        viewModelScope.launch {
            try {
                courseMaterialRepository.deleteDownloadedMaterial(material)
            } catch (_: Exception) {
            }
        }
    }

    fun addCustomMaterial(material: CourseMaterial) {
        viewModelScope.launch {
            try {
                courseMaterialRepository.addCustomMaterial(material)
                _showAddMaterialDialog.value = false
            } catch (_: Exception) {
            }
        }
    }

    fun deleteMaterial(materialId: String) {
        viewModelScope.launch {
            try {
                courseMaterialRepository.deleteMaterial(materialId)
            } catch (_: Exception) {
            }
        }
    }

    private data class Tuple5<A, B, C, D, E>(
        val a: A, val b: B, val c: C, val d: D, val e: E
    )
}
