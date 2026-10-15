package tw.edu.irika.nttueclass.presentation.task

import tw.edu.irika.nttueclass.domain.model.TaskItem

sealed interface TaskUiState {
    data object Loading : TaskUiState

    data class Success(
        val allTasks: List<TaskItem> = emptyList(),
        val filteredTasks: List<TaskItem> = emptyList(),
        val selectedCategory: Int = 0, // 0: 作業, 1: 測驗, 2: 問卷
        val selectedFilter: Int = 0,   // 0: 全部, 1: 待繳交, 2: 即將截止, 3: 已逾期, 4: 已完成
        val categoryTabs: List<String> = emptyList(),
        val filterOptions: List<String> = emptyList(),
        val isCurrentCategoryEmpty: Boolean = true,
        val categoryName: String = "作業",
        val selectedTaskForDetail: TaskItem? = null,
        val isSyncing: Boolean = false
    ) : TaskUiState

    data class Error(val message: String, val canRetry: Boolean = true) : TaskUiState
}
