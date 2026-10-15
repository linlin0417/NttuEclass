package tw.edu.irika.nttueclass.presentation.pass

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
import tw.edu.irika.nttueclass.pass.crypto.DonationData
import tw.edu.irika.nttueclass.pass.crypto.DonationIntegerHelper
import tw.edu.irika.nttueclass.testutil.FakeSecureCredentialStorage
import tw.edu.irika.nttueclass.testutil.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class PassViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakeStorage = FakeSecureCredentialStorage()

    private fun createViewModel(): PassViewModel {
        return PassViewModel(storage = fakeStorage)
    }

    @Test
    fun testInitialUiStateLoadsStorageCredentials() = runTest {
        val testData = DonationData(coffeeCount = 2, dinnerCount = 1, isFounder = true)
        val testDonationInt = DonationIntegerHelper.encode(testData)

        fakeStorage.saveCredentials("11411188", "test_pass")
        fakeStorage.saveDonationInteger(testDonationInt)
        fakeStorage.saveSpecialToken("vip_token_123")

        val viewModel = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        assertTrue(viewModel.uiState.value is PassUiState.Success)
        val state = viewModel.uiState.value as PassUiState.Success
        assertEquals("11411188", state.studentId)
        assertEquals("vip_token_123", state.specialToken)
        assertEquals(testDonationInt, state.donationInt)
        assertEquals(2, state.donationData.coffeeCount)
        assertTrue(state.donationData.isFounder)
        assertTrue(state.isMaxBrightness)
        assertFalse(state.isTokenEditorOpen)

        job.cancel()
    }

    @Test
    fun testTabAndBrightnessControls() = runTest {
        val viewModel = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        viewModel.onTabSelect(1)
        var state = viewModel.uiState.value as PassUiState.Success
        assertEquals(1, state.selectedTab)

        viewModel.toggleMaxBrightness()
        state = viewModel.uiState.value as PassUiState.Success
        assertFalse(state.isMaxBrightness)

        viewModel.setMaxBrightness(true)
        state = viewModel.uiState.value as PassUiState.Success
        assertTrue(state.isMaxBrightness)

        job.cancel()
    }

    @Test
    fun testTokenEditorSaveAndReset() = runTest {
        val viewModel = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        viewModel.toggleTokenEditor()
        var state = viewModel.uiState.value as PassUiState.Success
        assertTrue(state.isTokenEditorOpen)

        viewModel.onTokenInputChange("  custom_token_999  ")
        viewModel.saveToken()

        state = viewModel.uiState.value as PassUiState.Success
        assertFalse(state.isTokenEditorOpen)
        assertEquals("custom_token_999", state.specialToken)
        assertEquals("custom_token_999", fakeStorage.getSpecialToken())

        // 重設 Token
        viewModel.clearAndResetToken()
        state = viewModel.uiState.value as PassUiState.Success
        assertEquals("", state.specialToken)
        assertEquals("", fakeStorage.getSpecialToken())

        job.cancel()
    }

    @Test
    fun testRefreshQrCodeReloadsFromStorage() = runTest {
        val viewModel = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        // 外部更新儲存區 (例如登入完成或贊助更新)
        fakeStorage.saveCredentials("11411999", "pass")
        val updatedDonation = DonationIntegerHelper.encode(DonationData(tier = 2))
        fakeStorage.saveDonationInteger(updatedDonation)

        viewModel.refreshQrCode()

        val state = viewModel.uiState.value as PassUiState.Success
        assertEquals("11411999", state.studentId)
        assertEquals(2, state.donationData.tier)

        job.cancel()
    }
}
