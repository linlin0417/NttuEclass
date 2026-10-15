package tw.edu.irika.nttueclass.presentation.auth

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
import tw.edu.irika.nttueclass.data.remote.auth.AuthResult
import tw.edu.irika.nttueclass.testutil.FakeAuthManager
import tw.edu.irika.nttueclass.testutil.FakeDataSyncCoordinator
import tw.edu.irika.nttueclass.testutil.FakeSecureCredentialStorage
import tw.edu.irika.nttueclass.testutil.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakeStorage = FakeSecureCredentialStorage()
    private val fakeAuthManager = FakeAuthManager(fakeStorage = fakeStorage)
    private val fakeSyncCoordinator = FakeDataSyncCoordinator()

    private fun createViewModel(): AuthViewModel {
        return AuthViewModel(
            authManager = fakeAuthManager,
            dataSyncCoordinator = fakeSyncCoordinator
        )
    }

    @Test
    fun testInitialUiStateNotLoggedIn() = runTest {
        val viewModel = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        assertTrue(viewModel.uiState.value is AuthUiState.Success)
        val state = viewModel.uiState.value as AuthUiState.Success
        assertFalse(state.isLoggedIn)
        assertNull(state.studentId)
        assertFalse(state.isLockedOut)
        assertFalse(state.isLoggingIn)
        assertNull(state.loginErrorMessage)

        job.cancel()
    }

    @Test
    fun testEmptyCredentialsSetsErrorImmediately() = runTest {
        val viewModel = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        viewModel.login("", "")
        var state = viewModel.uiState.value as AuthUiState.Success
        assertEquals("學號與密碼不能為空", state.loginErrorMessage)

        viewModel.clearErrorMessage()
        state = viewModel.uiState.value as AuthUiState.Success
        assertNull(state.loginErrorMessage)

        job.cancel()
    }

    @Test
    fun testSuccessfulLoginUpdatesStateAndTriggersCallback() = runTest {
        fakeAuthManager.loginResult = AuthResult.Success("11411188")
        val viewModel = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        var successCallbackId: String? = null
        viewModel.login("11411188", "correct_pwd") { sId ->
            successCallbackId = sId
        }

        assertEquals("11411188", successCallbackId)
        val state = viewModel.uiState.value as AuthUiState.Success
        assertTrue(state.isLoggedIn)
        assertEquals("11411188", state.studentId)
        assertNull(state.loginErrorMessage)

        job.cancel()
    }

    @Test
    fun testInvalidCredentialsSetsErrorMessage() = runTest {
        fakeAuthManager.loginResult = AuthResult.InvalidCredentials(remainingAttempts = 3, message = "學號或密碼錯誤")
        val viewModel = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        viewModel.login("11411188", "wrong_pwd")

        val state = viewModel.uiState.value as AuthUiState.Success
        assertFalse(state.isLoggedIn)
        assertEquals("學號或密碼錯誤", state.loginErrorMessage)

        job.cancel()
    }

    @Test
    fun testLockedOutResultSetsLockedErrorMessage() = runTest {
        fakeAuthManager.loginResult = AuthResult.LockedOut(remainingSeconds = 1800, message = "帳號保護鎖定中")
        val viewModel = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        viewModel.login("11411188", "wrong_pwd")

        val state = viewModel.uiState.value as AuthUiState.Success
        assertFalse(state.isLoggedIn)
        assertEquals("帳號保護鎖定中", state.loginErrorMessage)

        job.cancel()
    }

    @Test
    fun testLogoutClearsCredentialsAndData() = runTest {
        fakeStorage.saveCredentials("11411188", "pass")
        val viewModel = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        var logoutCompleted = false
        viewModel.logout {
            logoutCompleted = true
        }

        assertTrue(logoutCompleted)
        assertEquals(1, fakeAuthManager.logoutCalls)
        assertEquals(1, fakeSyncCoordinator.clearCalls)

        val state = viewModel.uiState.value as AuthUiState.Success
        assertFalse(state.isLoggedIn)
        assertNull(state.studentId)

        job.cancel()
    }

    @Test
    fun testSilentRefreshSession() = runTest {
        val viewModel = createViewModel()
        fakeAuthManager.silentRefreshResult = true

        var refreshSuccess = false
        viewModel.silentRefresh { result ->
            refreshSuccess = result
        }

        assertTrue(refreshSuccess)
    }

    @Test
    fun testCheckSmartSyncTriggersDataSyncOnVersionUpgrade() = runTest {
        fakeStorage.saveCredentials("11411188", "pass")
        fakeStorage.saveLastAppVersionCode(100L)

        val viewModel = createViewModel()
        viewModel.checkSmartSync(displayVersionCode = 101L) // Upgrade detected

        assertEquals(1, fakeSyncCoordinator.syncCalls)
        assertEquals(101L, fakeStorage.getLastAppVersionCode())
    }
}
