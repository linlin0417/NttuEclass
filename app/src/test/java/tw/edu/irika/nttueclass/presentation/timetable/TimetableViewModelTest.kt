package tw.edu.irika.nttueclass.presentation.timetable

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
import tw.edu.irika.nttueclass.domain.model.Course
import tw.edu.irika.nttueclass.domain.model.TimetableSlot
import tw.edu.irika.nttueclass.testutil.FakeCourseRepository
import tw.edu.irika.nttueclass.testutil.FakeTimetableRepository
import tw.edu.irika.nttueclass.testutil.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class TimetableViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakeTimetableRepo = FakeTimetableRepository()
    private val fakeCourseRepo = FakeCourseRepository()

    private fun createViewModel(): TimetableViewModel {
        return TimetableViewModel(
            timetableRepository = fakeTimetableRepo,
            courseRepository = fakeCourseRepo
        )
    }

    @Test
    fun testInitialUiStateIsSuccessWithDefaultDay() = runTest {
        val slot = TimetableSlot(1, 1, "c1", "微積分", "E101", "張教授")
        fakeTimetableRepo.slotsFlow.value = listOf(slot)

        val viewModel = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        assertTrue(viewModel.uiState.value is TimetableUiState.Success)
        val state = viewModel.uiState.value as TimetableUiState.Success
        assertEquals(1, state.slots.size)
        assertEquals("微積分", state.slots[0].courseName)
        assertFalse(state.isWeekView)
        assertNull(state.selectedSlotForDetail)
        assertFalse(state.showAddDialog)

        job.cancel()
    }

    @Test
    fun testSelectDayAndToggleViewMode() = runTest {
        val viewModel = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        viewModel.selectDay(4)
        var state = viewModel.uiState.value as TimetableUiState.Success
        assertEquals(4, state.selectedDay)

        viewModel.toggleViewMode()
        state = viewModel.uiState.value as TimetableUiState.Success
        assertTrue(state.isWeekView)

        viewModel.setWeekView(false)
        state = viewModel.uiState.value as TimetableUiState.Success
        assertFalse(state.isWeekView)

        viewModel.backToToday()
        state = viewModel.uiState.value as TimetableUiState.Success
        assertEquals(viewModel.currentDayOfWeek, state.selectedDay)

        job.cancel()
    }

    @Test
    fun testSlotDetailAndAddDialogControls() = runTest {
        val slot = TimetableSlot(2, 3, "c2", "演算法", "E202", "李教授")
        val viewModel = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        viewModel.selectSlotForDetail(slot)
        var state = viewModel.uiState.value as TimetableUiState.Success
        assertEquals(slot, state.selectedSlotForDetail)

        viewModel.openAddDialog(slot)
        state = viewModel.uiState.value as TimetableUiState.Success
        assertTrue(state.showAddDialog)
        assertEquals(slot, state.editingSlot)

        viewModel.dismissAddDialog()
        state = viewModel.uiState.value as TimetableUiState.Success
        assertFalse(state.showAddDialog)
        assertNull(state.editingSlot)

        job.cancel()
    }

    @Test
    fun testAddOrUpdateSlotDelegatesToRepository() = runTest {
        val slot = TimetableSlot(3, 4, "c3", "資料結構", "E303", "陳教授")
        val viewModel = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        viewModel.openAddDialog()
        viewModel.addOrUpdateSlot(slot)

        assertEquals(1, fakeTimetableRepo.addedSlots.size)
        assertEquals(slot, fakeTimetableRepo.addedSlots[0])

        val state = viewModel.uiState.value as TimetableUiState.Success
        assertFalse(state.showAddDialog)
        assertNull(state.selectedSlotForDetail)

        job.cancel()
    }

    @Test
    fun testUpdateSlotWithChangedTimeDeletesOldSlotFirst() = runTest {
        val oldSlot = TimetableSlot(1, 1, "c1", "物理", "P1", "黃教授")
        val newSlot = TimetableSlot(1, 2, "c1", "物理", "P1", "黃教授")

        val viewModel = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        viewModel.updateSlot(oldSlot, newSlot)

        assertEquals(1, fakeTimetableRepo.deletedSlots.size)
        assertEquals(1 to 1, fakeTimetableRepo.deletedSlots[0])
        assertEquals(1, fakeTimetableRepo.addedSlots.size)
        assertEquals(newSlot, fakeTimetableRepo.addedSlots[0])

        job.cancel()
    }

    @Test
    fun testDeleteSlotDelegatesToRepository() = runTest {
        val viewModel = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        viewModel.deleteSlot(5, 7)
        assertEquals(1, fakeTimetableRepo.deletedSlots.size)
        assertEquals(5 to 7, fakeTimetableRepo.deletedSlots[0])

        job.cancel()
    }

    @Test
    fun testSyncTimetableSuccessAndFailure() = runTest {
        val viewModel = createViewModel()
        var callbackSuccess = false

        fakeTimetableRepo.syncResult = true
        viewModel.syncTimetable { res ->
            callbackSuccess = res.isSuccess
        }
        assertTrue(callbackSuccess)

        fakeTimetableRepo.syncResult = false
        var callbackFailed = false
        viewModel.syncTimetable { res ->
            callbackFailed = res.isFailure
        }
        assertTrue(callbackFailed)
    }

    @Test
    fun testRepositoryErrorEmitsErrorState() = runTest {
        fakeTimetableRepo.shouldThrowError = true
        val viewModel = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        assertTrue(viewModel.uiState.value is TimetableUiState.Error)
        val err = viewModel.uiState.value as TimetableUiState.Error
        assertTrue(err.message.contains("Fake Timetable Error"))

        job.cancel()
    }
}
