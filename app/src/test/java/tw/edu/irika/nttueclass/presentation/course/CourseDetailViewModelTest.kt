package tw.edu.irika.nttueclass.presentation.course

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import tw.edu.irika.nttueclass.domain.model.Announcement
import tw.edu.irika.nttueclass.domain.model.Course
import tw.edu.irika.nttueclass.domain.model.CourseMaterial
import tw.edu.irika.nttueclass.domain.model.MaterialType
import tw.edu.irika.nttueclass.domain.model.TaskItem
import tw.edu.irika.nttueclass.domain.model.TaskStatus
import tw.edu.irika.nttueclass.domain.model.TaskType
import tw.edu.irika.nttueclass.domain.model.TimetableSlot
import tw.edu.irika.nttueclass.testutil.FakeAnnouncementRepository
import tw.edu.irika.nttueclass.testutil.FakeCourseMaterialRepository
import tw.edu.irika.nttueclass.testutil.FakeCourseRepository
import tw.edu.irika.nttueclass.testutil.FakeTaskRepository
import tw.edu.irika.nttueclass.testutil.FakeTimetableRepository
import tw.edu.irika.nttueclass.testutil.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class CourseDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakeMaterialRepo = FakeCourseMaterialRepository()
    private val fakeCourseRepo = FakeCourseRepository()
    private val fakeAnnouncementRepo = FakeAnnouncementRepository()
    private val fakeTaskRepo = FakeTaskRepository()
    private val fakeTimetableRepo = FakeTimetableRepository()

    private fun createViewModel(courseId: String = "c101"): CourseDetailViewModel {
        return CourseDetailViewModel(
            courseId = courseId,
            courseMaterialRepository = fakeMaterialRepo,
            courseRepository = fakeCourseRepo,
            announcementRepository = fakeAnnouncementRepo,
            taskRepository = fakeTaskRepo,
            timetableRepository = fakeTimetableRepo
        )
    }

    @Test
    fun testInitialUiStateMatchesCourseAndRelatedEntities() = runTest {
        val course = Course("c101", "101", "數位系統導論", "陳老師", "E101", 3, "113-1")
        fakeCourseRepo.coursesFlow.value = listOf(course)

        val ann = Announcement(
            id = "a1",
            courseId = "c101",
            courseName = "數位系統導論",
            title = "期中公告",
            date = "2026/10/01",
            author = "陳老師",
            contentSummary = "內容"
        )
        fakeAnnouncementRepo.announcementsFlow.value = listOf(ann)

        val task = TaskItem(
            id = "t1", courseId = "c101", courseName = "數位系統導論", title = "作業1",
            type = TaskType.ASSIGNMENT, dueDateTime = "2026/10/20", remainingHours = 50,
            status = TaskStatus.PENDING, isSubmitted = false
        )
        fakeTaskRepo.tasksFlow.value = listOf(task)

        val slot = TimetableSlot(1, 1, "c101", "數位系統導論", "E101", "陳老師")
        fakeTimetableRepo.slotsFlow.value = listOf(slot)

        val material = CourseMaterial(
            id = "m1",
            courseId = "c101",
            title = "投影片1",
            chapterName = "第1章",
            fileExtension = "pdf",
            fileSize = 1024L,
            type = MaterialType.DOCUMENT,
            isFilePresentOnDisk = false
        )
        fakeMaterialRepo.materialsFlow.value = listOf(material)

        val viewModel = createViewModel("c101")
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        assertTrue(viewModel.uiState.value is CourseDetailUiState.Success)
        val state = viewModel.uiState.value as CourseDetailUiState.Success
        assertEquals("數位系統導論", state.course.name)
        assertEquals(1, state.materials.size)
        assertEquals(1, state.courseAnnouncements.size)
        assertEquals(1, state.courseTasks.size)
        assertEquals(1, state.courseSlots.size)

        job.cancel()
    }

    @Test
    fun testMaterialSearchAndTypeFiltering() = runTest {
        val m1 = CourseMaterial(
            id = "m1",
            courseId = "c101",
            title = "Ch1 簡介",
            chapterName = "第1章",
            fileExtension = "pdf",
            fileSize = 1024L,
            type = MaterialType.DOCUMENT,
            isFilePresentOnDisk = true
        )
        val m2 = CourseMaterial(
            id = "m2",
            courseId = "c101",
            title = "實驗程式碼",
            chapterName = "程式",
            fileExtension = "zip",
            fileSize = 2048L,
            type = MaterialType.ARCHIVE,
            isFilePresentOnDisk = false
        )
        val m3 = CourseMaterial(
            id = "m3",
            courseId = "c101",
            title = "Ch2 電路邏輯",
            chapterName = "第2章",
            fileExtension = "pdf",
            fileSize = 3072L,
            type = MaterialType.DOCUMENT,
            isFilePresentOnDisk = false
        )
        fakeMaterialRepo.materialsFlow.value = listOf(m1, m2, m3)

        val viewModel = createViewModel("c101")
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        // 搜尋 "電路"
        viewModel.onSearchChange("電路")
        var state = viewModel.uiState.value as CourseDetailUiState.Success
        assertEquals(1, state.filteredMaterials.size)
        assertEquals("m3", state.filteredMaterials[0].id)

        // 重設搜尋並套用類型篩選
        viewModel.onSearchChange("")
        viewModel.onTypeFilterSelect(MaterialType.ARCHIVE)
        state = viewModel.uiState.value as CourseDetailUiState.Success
        assertEquals(1, state.filteredMaterials.size)
        assertEquals("m2", state.filteredMaterials[0].id)

        // 僅顯示已下載檔案
        viewModel.onTypeFilterSelect(null)
        viewModel.onToggleOnlyDownloaded()
        state = viewModel.uiState.value as CourseDetailUiState.Success
        assertEquals(1, state.filteredMaterials.size)
        assertEquals("m1", state.filteredMaterials[0].id)

        job.cancel()
    }

    @Test
    fun testDownloadMaterialWorkflow() = runTest {
        val material = CourseMaterial(
            id = "m1",
            courseId = "c101",
            title = "講義",
            chapterName = "章節",
            fileExtension = "pdf",
            fileSize = 100L,
            type = MaterialType.DOCUMENT,
            isFilePresentOnDisk = false
        )
        fakeMaterialRepo.materialsFlow.value = listOf(material)

        val viewModel = createViewModel("c101")
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        var downloadResultSuccess = false
        viewModel.downloadMaterial(material) { result ->
            downloadResultSuccess = result.isSuccess
        }

        assertTrue(downloadResultSuccess)
        val state = viewModel.uiState.value as CourseDetailUiState.Success
        // 下載完成後 progress map 應清空
        assertFalse(state.downloadProgressMap.containsKey("m1"))

        job.cancel()
    }

    @Test
    fun testAddCustomMaterialAndDelete() = runTest {
        val customMaterial = CourseMaterial(
            id = "m_custom",
            courseId = "c101",
            title = "考古題",
            chapterName = "自訂",
            fileExtension = "pdf",
            fileSize = 0L,
            type = MaterialType.DOCUMENT,
            isFilePresentOnDisk = false
        )
        val viewModel = createViewModel("c101")
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        viewModel.setShowAddMaterialDialog(true)
        var state = viewModel.uiState.value as CourseDetailUiState.Success
        assertTrue(state.showAddMaterialDialog)

        viewModel.addCustomMaterial(customMaterial)
        assertEquals(1, fakeMaterialRepo.addedMaterials.size)
        assertEquals(customMaterial, fakeMaterialRepo.addedMaterials[0])

        viewModel.deleteMaterial("m_custom")
        assertEquals(1, fakeMaterialRepo.deletedMaterialIds.size)
        assertEquals("m_custom", fakeMaterialRepo.deletedMaterialIds[0])

        job.cancel()
    }

    @Test
    fun testSyncOperations() = runTest {
        val viewModel = createViewModel("c101")

        var matSyncCalled = false
        viewModel.syncMaterials { res -> matSyncCalled = res.isSuccess }
        assertTrue(matSyncCalled)

        var annSyncCalled = false
        viewModel.syncAnnouncements { res -> annSyncCalled = res.isSuccess }
        assertTrue(annSyncCalled)

        var taskSyncCalled = false
        viewModel.syncTasks("數位系統") { res -> taskSyncCalled = res.isSuccess }
        assertTrue(taskSyncCalled)
    }

    @Test
    fun testRepositoryErrorEmitsErrorState() = runTest {
        fakeCourseRepo.shouldThrowError = true
        val viewModel = createViewModel("c101")
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        assertTrue(viewModel.uiState.value is CourseDetailUiState.Error)
        val err = viewModel.uiState.value as CourseDetailUiState.Error
        assertTrue(err.message.contains("Fake Courses Error"))

        job.cancel()
    }
}
