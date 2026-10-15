package tw.edu.irika.nttueclass.presentation.dashboard

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
import tw.edu.irika.nttueclass.domain.model.Announcement
import tw.edu.irika.nttueclass.domain.model.Course
import tw.edu.irika.nttueclass.domain.model.TaskItem
import tw.edu.irika.nttueclass.domain.model.TaskStatus
import tw.edu.irika.nttueclass.domain.model.TaskType
import tw.edu.irika.nttueclass.domain.model.TimetableSlot
import tw.edu.irika.nttueclass.testutil.FakeAnnouncementRepository
import tw.edu.irika.nttueclass.testutil.FakeCourseRepository
import tw.edu.irika.nttueclass.testutil.FakeDataSyncCoordinator
import tw.edu.irika.nttueclass.testutil.FakeSecureCredentialStorage
import tw.edu.irika.nttueclass.testutil.FakeTaskRepository
import tw.edu.irika.nttueclass.testutil.FakeTimetableRepository
import tw.edu.irika.nttueclass.testutil.MainDispatcherRule
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakeTimetableRepo = FakeTimetableRepository()
    private val fakeCourseRepo = FakeCourseRepository()
    private val fakeTaskRepo = FakeTaskRepository()
    private val fakeAnnouncementRepo = FakeAnnouncementRepository()
    private val fakeSyncCoordinator = FakeDataSyncCoordinator()
    private val fakeStorage = FakeSecureCredentialStorage()

    private fun createViewModel(): DashboardViewModel {
        return DashboardViewModel(
            timetableRepository = fakeTimetableRepo,
            courseRepository = fakeCourseRepo,
            taskRepository = fakeTaskRepo,
            announcementRepository = fakeAnnouncementRepo,
            dataSyncCoordinator = fakeSyncCoordinator,
            storage = fakeStorage
        )
    }

    @Test
    fun testInitialUiStateIsLoadingAndTransitionsToSuccess() = runTest {
        val viewModel = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        assertTrue(viewModel.uiState.value is DashboardUiState.Success)
        val success = viewModel.uiState.value as DashboardUiState.Success
        assertFalse(success.isLoggedIn)
        assertEquals("同學，你好！", success.greeting)
        assertFalse(success.hasTasks)
        assertTrue(success.latestNotices.isEmpty())

        job.cancel()
    }

    @Test
    fun testLoggedInGreetingWithStudentId() = runTest {
        fakeStorage.saveCredentials("11411188", "test_pass")

        val viewModel = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        val state = viewModel.uiState.value as DashboardUiState.Success
        assertTrue(state.isLoggedIn)
        assertEquals("11411188", state.studentId)
        assertEquals("11411188 同學，你好！", state.greeting)

        job.cancel()
    }

    @Test
    fun testUrgentTaskPriorityLogic() = runTest {
        val normalTask = TaskItem(
            id = "t1", courseId = "c1", courseName = "微積分", title = "習題1",
            type = TaskType.ASSIGNMENT, dueDateTime = "2026/10/20 23:59",
            remainingHours = 100, status = TaskStatus.PENDING, isSubmitted = false
        )
        val urgentTask = TaskItem(
            id = "t2", courseId = "c1", courseName = "微積分", title = "期中作業",
            type = TaskType.ASSIGNMENT, dueDateTime = "2026/10/13 23:59",
            remainingHours = 8, status = TaskStatus.URGENT, isSubmitted = false
        )
        val warningTask = TaskItem(
            id = "t3", courseId = "c2", courseName = "線性代數", title = "小考",
            type = TaskType.QUIZ, dueDateTime = "2026/10/15 23:59",
            remainingHours = 30, status = TaskStatus.WARNING, isSubmitted = false
        )

        fakeTaskRepo.tasksFlow.value = listOf(normalTask, urgentTask, warningTask)

        val viewModel = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        val state = viewModel.uiState.value as DashboardUiState.Success
        assertTrue(state.hasTasks)
        assertNotNull(state.urgentTask)
        assertEquals("t2", state.urgentTask?.id)
        assertEquals(TaskStatus.URGENT, state.urgentTask?.status)

        job.cancel()
    }

    @Test
    fun testSubmittedTasksIgnoredInUrgentCalculation() = runTest {
        val submittedUrgentTask = TaskItem(
            id = "t1", courseId = "c1", courseName = "微積分", title = "作業",
            type = TaskType.ASSIGNMENT, dueDateTime = "2026/10/13 23:59",
            remainingHours = 2, status = TaskStatus.URGENT, isSubmitted = true
        )
        fakeTaskRepo.tasksFlow.value = listOf(submittedUrgentTask)

        val viewModel = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        val state = viewModel.uiState.value as DashboardUiState.Success
        assertNull(state.urgentTask)

        job.cancel()
    }

    @Test
    fun testLatestAnnouncementsTakesFirstTwo() = runTest {
        fakeAnnouncementRepo.announcementsFlow.value = listOf(
            Announcement("a1", "c1", "微積分", "公告1", "內容1", "2026/10/01", "張教授"),
            Announcement("a2", "c1", "微積分", "公告2", "內容2", "2026/10/02", "張教授"),
            Announcement("a3", "c2", "線代", "公告3", "內容3", "2026/10/03", "李教授")
        )

        val viewModel = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        val state = viewModel.uiState.value as DashboardUiState.Success
        assertEquals(2, state.latestNotices.size)
        assertEquals("a1", state.latestNotices[0].id)
        assertEquals("a2", state.latestNotices[1].id)

        job.cancel()
    }

    @Test
    fun testSyncDataTriggersDataSyncCoordinator() = runTest {
        val viewModel = createViewModel()
        var callbackInvoked = false
        var syncSuccess = false

        viewModel.syncData { res ->
            callbackInvoked = true
            syncSuccess = res.isSuccess
        }

        assertEquals(1, fakeSyncCoordinator.syncCalls)
        assertTrue(callbackInvoked)
        assertTrue(syncSuccess)
    }

    @Test
    fun testErrorInRepositoryStreamEmitsErrorState() = runTest {
        fakeTimetableRepo.shouldThrowError = true

        val viewModel = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        assertTrue(viewModel.uiState.value is DashboardUiState.Error)
        val err = viewModel.uiState.value as DashboardUiState.Error
        assertTrue(err.message.contains("Fake Timetable Error"))

        job.cancel()
    }
}
