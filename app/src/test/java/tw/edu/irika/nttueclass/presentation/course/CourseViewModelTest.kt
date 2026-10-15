package tw.edu.irika.nttueclass.presentation.course

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
import tw.edu.irika.nttueclass.testutil.FakeAnnouncementRepository
import tw.edu.irika.nttueclass.testutil.FakeCourseRepository
import tw.edu.irika.nttueclass.testutil.FakeTaskRepository
import tw.edu.irika.nttueclass.testutil.FakeTimetableRepository
import tw.edu.irika.nttueclass.testutil.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class CourseViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakeCourseRepo = FakeCourseRepository()
    private val fakeAnnouncementRepo = FakeAnnouncementRepository()
    private val fakeTaskRepo = FakeTaskRepository()
    private val fakeTimetableRepo = FakeTimetableRepository()

    private fun createViewModel(): CourseViewModel {
        return CourseViewModel(
            courseRepository = fakeCourseRepo,
            announcementRepository = fakeAnnouncementRepo,
            taskRepository = fakeTaskRepo,
            timetableRepository = fakeTimetableRepo
        )
    }

    @Test
    fun testInitialUiStateIsSuccess() = runTest {
        val course1 = Course("c1", "CS101", "資料結構", "張教授", "E101", 3, "113-1")
        fakeCourseRepo.coursesFlow.value = listOf(course1)

        val viewModel = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        assertTrue(viewModel.uiState.value is CourseUiState.Success)
        val state = viewModel.uiState.value as CourseUiState.Success
        assertEquals(1, state.courses.size)
        assertEquals(1, state.filteredCourses.size)
        assertEquals("全部", state.selectedSemester)
        assertTrue(state.availableSemesters.contains("113-1"))

        job.cancel()
    }

    @Test
    fun testSearchQueryFiltersCoursesAndAnnouncements() = runTest {
        val course1 = Course("c1", "CS101", "演算法", "王教授", "E101", 3, "113-1")
        val course2 = Course("c2", "CS102", "資料結構", "李教授", "E202", 3, "113-1")
        fakeCourseRepo.coursesFlow.value = listOf(course1, course2)

        val ann1 = Announcement(
            id = "a1", courseId = "c1", courseName = "演算法", title = "期中考須知",
            date = "2026/10/01", author = "王教授", contentSummary = "請帶計算機"
        )
        val ann2 = Announcement(
            id = "a2", courseId = "c2", courseName = "資料結構", title = "第一次作業繳交",
            date = "2026/10/02", author = "李教授", contentSummary = "透過系統繳交"
        )
        fakeAnnouncementRepo.announcementsFlow.value = listOf(ann1, ann2)

        val viewModel = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        // 搜尋 "演算法"
        viewModel.onSearchQueryChange("演算法")
        var state = viewModel.uiState.value as CourseUiState.Success
        assertEquals(1, state.filteredCourses.size)
        assertEquals("演算法", state.filteredCourses[0].name)
        assertEquals(1, state.filteredAnnouncements.size)
        assertEquals("期中考須知", state.filteredAnnouncements[0].title)

        // 搜尋教授名稱
        viewModel.onSearchQueryChange("李教授")
        state = viewModel.uiState.value as CourseUiState.Success
        assertEquals(1, state.filteredCourses.size)
        assertEquals("資料結構", state.filteredCourses[0].name)
        assertEquals(1, state.filteredAnnouncements.size)

        job.cancel()
    }

    @Test
    fun testSemesterFiltering() = runTest {
        val course1 = Course("c1", "101", "微積分", "陳教授", "R1", 3, "112-2")
        val course2 = Course("c2", "102", "線性代數", "林教授", "R2", 3, "113-1")
        fakeCourseRepo.coursesFlow.value = listOf(course1, course2)

        val viewModel = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        viewModel.onSemesterSelect("112-2")
        val state = viewModel.uiState.value as CourseUiState.Success
        assertEquals(1, state.filteredCourses.size)
        assertEquals("微積分", state.filteredCourses[0].name)

        job.cancel()
    }

    @Test
    fun testAddAndDeleteCourse() = runTest {
        val course = Course("c_new", "999", "人工智慧導論", "黃教授", "AI館", 3, "113-1")
        val viewModel = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        viewModel.showAddCourseDialog(true)
        var state = viewModel.uiState.value as CourseUiState.Success
        assertTrue(state.showAddCourseDialog)

        viewModel.addCourse(course)
        assertEquals(1, fakeCourseRepo.addedCourses.size)
        assertEquals(course, fakeCourseRepo.addedCourses[0])

        viewModel.selectCourseForDetail(course)
        state = viewModel.uiState.value as CourseUiState.Success
        assertEquals(course, state.selectedCourseForDetail)

        viewModel.deleteCourse("c_new")
        assertEquals(1, fakeCourseRepo.deletedCourseIds.size)
        assertEquals("c_new", fakeCourseRepo.deletedCourseIds[0])
        state = viewModel.uiState.value as CourseUiState.Success
        assertNull(state.selectedCourseForDetail)

        job.cancel()
    }

    @Test
    fun testSyncCoursesSuccessAndFailure() = runTest {
        val viewModel = createViewModel()
        var callbackSuccess = false

        fakeCourseRepo.syncResult = true
        viewModel.syncCourses { res ->
            callbackSuccess = res.isSuccess
        }
        assertTrue(callbackSuccess)

        fakeCourseRepo.syncResult = false
        var callbackFailure = false
        viewModel.syncCourses { res ->
            callbackFailure = res.isFailure
        }
        assertTrue(callbackFailure)
    }

    @Test
    fun testRepositoryErrorEmitsErrorState() = runTest {
        fakeCourseRepo.shouldThrowError = true
        val viewModel = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        assertTrue(viewModel.uiState.value is CourseUiState.Error)
        val err = viewModel.uiState.value as CourseUiState.Error
        assertTrue(err.message.contains("Fake Courses Error"))

        job.cancel()
    }
}
