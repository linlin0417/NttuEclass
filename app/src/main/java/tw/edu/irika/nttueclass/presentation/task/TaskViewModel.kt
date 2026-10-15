package tw.edu.irika.nttueclass.presentation.task

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
import tw.edu.irika.nttueclass.domain.model.TaskItem
import tw.edu.irika.nttueclass.domain.model.TaskStatus
import tw.edu.irika.nttueclass.domain.model.TaskType
import tw.edu.irika.nttueclass.domain.repository.ITaskRepository
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class TaskViewModel @Inject constructor(
    private val taskRepository: ITaskRepository
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow(0)
    private val _selectedFilter = MutableStateFlow(0)
    private val _selectedTaskForDetail = MutableStateFlow<TaskItem?>(null)
    private val _isSyncing = MutableStateFlow(false)

    val uiState: StateFlow<TaskUiState> = combine(
        taskRepository.getTasksStream(),
        _selectedCategory,
        _selectedFilter,
        _selectedTaskForDetail,
        _isSyncing
    ) { tasks, selectedCategory, selectedFilter, selectedTaskForDetail, isSyncing ->
        val assignmentTasks = tasks.filter { it.type == TaskType.ASSIGNMENT }
        val quizTasks = tasks.filter { it.type == TaskType.QUIZ || it.type == TaskType.EXAM }
        val surveyTasks = tasks.filter { it.type == TaskType.QUESTIONNAIRE }

        val categoryTabs = listOf(
            "作業 (${assignmentTasks.size})",
            "測驗 (${quizTasks.size})",
            "問卷 (${surveyTasks.size})"
        )

        val currentCategoryTasks = when (selectedCategory) {
            0 -> assignmentTasks
            1 -> quizTasks
            2 -> surveyTasks
            else -> assignmentTasks
        }

        val pendingCount = currentCategoryTasks.count { !it.isSubmitted && it.status != TaskStatus.OVERDUE }
        val urgentCount = currentCategoryTasks.count { !it.isSubmitted && (it.status == TaskStatus.URGENT || it.status == TaskStatus.WARNING) }
        val overdueCount = currentCategoryTasks.count { !it.isSubmitted && it.status == TaskStatus.OVERDUE }
        val completedCount = currentCategoryTasks.count { it.isSubmitted }

        val filterOptions = listOf(
            "全部 (${currentCategoryTasks.size})",
            "待繳交 ($pendingCount)",
            "即將截止 ($urgentCount)",
            "已逾期 ($overdueCount)",
            "已完成 ($completedCount)"
        )

        val filteredTasks = currentCategoryTasks.filter { task ->
            when (selectedFilter) {
                1 -> !task.isSubmitted && task.status != TaskStatus.OVERDUE
                2 -> !task.isSubmitted && (task.status == TaskStatus.URGENT || task.status == TaskStatus.WARNING)
                3 -> !task.isSubmitted && task.status == TaskStatus.OVERDUE
                4 -> task.isSubmitted
                else -> true
            }
        }

        val categoryName = when (selectedCategory) {
            0 -> "作業"
            1 -> "測驗"
            2 -> "問卷"
            else -> "項目"
        }

        TaskUiState.Success(
            allTasks = tasks,
            filteredTasks = filteredTasks,
            selectedCategory = selectedCategory,
            selectedFilter = selectedFilter,
            categoryTabs = categoryTabs,
            filterOptions = filterOptions,
            isCurrentCategoryEmpty = currentCategoryTasks.isEmpty(),
            categoryName = categoryName,
            selectedTaskForDetail = selectedTaskForDetail,
            isSyncing = isSyncing
        ) as TaskUiState
    }.catch { e ->
        emit(TaskUiState.Error(e.message ?: "無法載入待辦任務"))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = TaskUiState.Loading
    )

    fun onCategorySelect(category: Int) {
        _selectedCategory.value = category
        _selectedFilter.value = 0
    }

    fun onFilterSelect(filter: Int) {
        _selectedFilter.value = filter
    }

    fun selectTaskForDetail(task: TaskItem?) {
        _selectedTaskForDetail.value = task
    }

    fun syncTasks(onCompleted: ((Result<Unit>) -> Unit)? = null) {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val ok = taskRepository.syncTasks()
                if (ok) {
                    onCompleted?.invoke(Result.success(Unit))
                } else {
                    onCompleted?.invoke(Result.failure(IOException("作業與測驗同步失敗，請檢查網路連線")))
                }
            } catch (e: Exception) {
                onCompleted?.invoke(Result.failure(e))
            } finally {
                _isSyncing.value = false
            }
        }
    }
}
