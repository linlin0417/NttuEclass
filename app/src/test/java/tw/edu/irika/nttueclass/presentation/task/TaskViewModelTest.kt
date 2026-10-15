package tw.edu.irika.nttueclass.presentation.task

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import tw.edu.irika.nttueclass.domain.model.TaskItem
import tw.edu.irika.nttueclass.domain.model.TaskStatus
import tw.edu.irika.nttueclass.domain.model.TaskType
import tw.edu.irika.nttueclass.testutil.FakeTaskRepository
import tw.edu.irika.nttueclass.testutil.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class TaskViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakeTaskRepo = FakeTaskRepository()

    private fun createViewModel(): TaskViewModel {
        return TaskViewModel(taskRepository = fakeTaskRepo)
    }

    @Test
    fun testCategoryTabsSeparation() = runTest {
        val assignment = TaskItem("t1", "c1", "課1", "作業1", TaskType.ASSIGNMENT, "2026/10/20", 50, TaskStatus.PENDING, isSubmitted = false)
        val quiz = TaskItem("t2", "c1", "課1", "期中小考", TaskType.QUIZ, "2026/10/21", 60, TaskStatus.PENDING, isSubmitted = false)
        val exam = TaskItem("t3", "c1", "課1", "期末大考", TaskType.EXAM, "2026/10/22", 70, TaskStatus.PENDING, isSubmitted = false)
        val survey = TaskItem("t4", "c1", "課1", "教學問卷", TaskType.QUESTIONNAIRE, "2026/10/23", 80, TaskStatus.PENDING, isSubmitted = false)

        fakeTaskRepo.tasksFlow.value = listOf(assignment, quiz, exam, survey)

        val viewModel = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        assertTrue(viewModel.uiState.value is TaskUiState.Success)
        var state = viewModel.uiState.value as TaskUiState.Success
        // 預設為作業類別 (category 0)
        assertEquals(0, state.selectedCategory)
        assertEquals(1, state.filteredTasks.size)
        assertEquals("作業1", state.filteredTasks[0].title)

        // 切換至測驗 (category 1)
        viewModel.onCategorySelect(1)
        state = viewModel.uiState.value as TaskUiState.Success
        assertEquals(1, state.selectedCategory)
        assertEquals(2, state.filteredTasks.size) // quiz + exam

        // 切換至問卷 (category 2)
        viewModel.onCategorySelect(2)
        state = viewModel.uiState.value as TaskUiState.Success
        assertEquals(2, state.selectedCategory)
        assertEquals(1, state.filteredTasks.size) // survey

        job.cancel()
    }

    @Test
    fun testTaskStatusFilters() = runTest {
        val pendingTask = TaskItem("t1", "c1", "課", "待繳作業", TaskType.ASSIGNMENT, "2026/10/20", 100, TaskStatus.PENDING, isSubmitted = false)
        val urgentTask = TaskItem("t2", "c1", "課", "即將截止作業", TaskType.ASSIGNMENT, "2026/10/13", 6, TaskStatus.URGENT, isSubmitted = false)
        val overdueTask = TaskItem("t3", "c1", "課", "已逾期作業", TaskType.ASSIGNMENT, "2026/10/01", -20, TaskStatus.OVERDUE, isSubmitted = false)
        val completedTask = TaskItem("t4", "c1", "課", "已完成作業", TaskType.ASSIGNMENT, "2026/10/10", 10, TaskStatus.COMPLETED, isSubmitted = true)

        fakeTaskRepo.tasksFlow.value = listOf(pendingTask, urgentTask, overdueTask, completedTask)

        val viewModel = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        // Filter 1: 待繳交 (非逾期且未提交)
        viewModel.onFilterSelect(1)
        var state = viewModel.uiState.value as TaskUiState.Success
        assertEquals(2, state.filteredTasks.size) // pendingTask, urgentTask

        // Filter 2: 即將截止 (URGENT 或 WARNING)
        viewModel.onFilterSelect(2)
        state = viewModel.uiState.value as TaskUiState.Success
        assertEquals(1, state.filteredTasks.size)
        assertEquals("t2", state.filteredTasks[0].id)

        // Filter 3: 已逾期
        viewModel.onFilterSelect(3)
        state = viewModel.uiState.value as TaskUiState.Success
        assertEquals(1, state.filteredTasks.size)
        assertEquals("t3", state.filteredTasks[0].id)

        // Filter 4: 已完成
        viewModel.onFilterSelect(4)
        state = viewModel.uiState.value as TaskUiState.Success
        assertEquals(1, state.filteredTasks.size)
        assertEquals("t4", state.filteredTasks[0].id)

        job.cancel()
    }

    @Test
    fun testSelectTaskForDetail() = runTest {
        val task = TaskItem("t1", "c1", "課", "標題", TaskType.ASSIGNMENT, "2026/10/20", 10, TaskStatus.PENDING, isSubmitted = false)
        val viewModel = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        viewModel.selectTaskForDetail(task)
        var state = viewModel.uiState.value as TaskUiState.Success
        assertEquals(task, state.selectedTaskForDetail)

        viewModel.selectTaskForDetail(null)
        state = viewModel.uiState.value as TaskUiState.Success
        assertNull(state.selectedTaskForDetail)

        job.cancel()
    }

    @Test
    fun testSyncTasksSuccessAndFailure() = runTest {
        val viewModel = createViewModel()

        fakeTaskRepo.syncResult = true
        var successCalled = false
        viewModel.syncTasks { res -> successCalled = res.isSuccess }
        assertTrue(successCalled)

        fakeTaskRepo.syncResult = false
        var failureCalled = false
        viewModel.syncTasks { res -> failureCalled = res.isFailure }
        assertTrue(failureCalled)
    }

    @Test
    fun testRepositoryErrorEmitsErrorState() = runTest {
        fakeTaskRepo.shouldThrowError = true
        val viewModel = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        assertTrue(viewModel.uiState.value is TaskUiState.Error)
        val err = viewModel.uiState.value as TaskUiState.Error
        assertTrue(err.message.contains("Fake Tasks Error"))

        job.cancel()
    }
}
