package tw.edu.irika.nttueclass.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tw.edu.irika.nttueclass.data.remote.auth.AuthManager
import tw.edu.irika.nttueclass.data.remote.auth.AuthResult
import tw.edu.irika.nttueclass.domain.repository.IDataSyncCoordinator
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authManager: AuthManager,
    private val dataSyncCoordinator: IDataSyncCoordinator
) : ViewModel() {

    private val _isLoggedIn = MutableStateFlow(authManager.secureStorage.isLoggedIn())
    private val _studentId = MutableStateFlow(authManager.secureStorage.getStudentId())
    private val _isLoggingIn = MutableStateFlow(false)
    private val _loginErrorMessage = MutableStateFlow<String?>(null)
    private val _lockoutRefreshTrigger = MutableStateFlow(0)

    val uiState: StateFlow<AuthUiState> = combine(
        _isLoggedIn,
        _studentId,
        _isLoggingIn,
        _loginErrorMessage,
        _lockoutRefreshTrigger
    ) { loggedIn, sId, loggingIn, errorMsg, _ ->
        val isLocked = authManager.lockoutManager.isLockedOut()
        val remainingAttempts = authManager.lockoutManager.getRemainingAttempts()
        val shouldWarn = authManager.lockoutManager.shouldWarnUser()

        AuthUiState.Success(
            isLoggedIn = loggedIn,
            studentId = sId,
            isLockedOut = isLocked,
            remainingAttempts = remainingAttempts,
            shouldWarn = shouldWarn,
            isLoggingIn = loggingIn,
            loginErrorMessage = errorMsg
        ) as AuthUiState
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = AuthUiState.Loading
    )

    fun login(studentId: String, password: String, onSuccess: ((String) -> Unit)? = null) {
        if (_isLoggingIn.value) return
        if (studentId.isBlank() || password.isBlank()) {
            _loginErrorMessage.value = "學號與密碼不能為空"
            return
        }

        viewModelScope.launch {
            _isLoggingIn.value = true
            _loginErrorMessage.value = null
            try {
                when (val result = authManager.login(studentId, password)) {
                    is AuthResult.Success -> {
                        _isLoggedIn.value = true
                        _studentId.value = result.studentId
                        _loginErrorMessage.value = null
                        onSuccess?.invoke(result.studentId)
                    }
                    is AuthResult.InvalidCredentials -> {
                        _loginErrorMessage.value = result.message
                        _lockoutRefreshTrigger.value++
                    }
                    is AuthResult.LockedOut -> {
                        _loginErrorMessage.value = result.message
                        _lockoutRefreshTrigger.value++
                    }
                    is AuthResult.NetworkError -> {
                        _loginErrorMessage.value = result.message
                    }
                    is AuthResult.EmptyInput -> {
                        _loginErrorMessage.value = result.message
                    }
                }
            } catch (e: Exception) {
                _loginErrorMessage.value = "登入發生未預期錯誤：${e.localizedMessage ?: "請稍後重試"}"
            } finally {
                _isLoggingIn.value = false
            }
        }
    }

    fun logout(onCompleted: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                authManager.logout()
                dataSyncCoordinator.clearAllData()
            } catch (_: Exception) {
            } finally {
                _isLoggedIn.value = false
                _studentId.value = null
                _loginErrorMessage.value = null
                _lockoutRefreshTrigger.value++
                onCompleted?.invoke()
            }
        }
    }

    fun silentRefresh(onResult: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            try {
                val success = authManager.silentRefreshSession()
                if (success) {
                    _isLoggedIn.value = true
                    _studentId.value = authManager.secureStorage.getStudentId()
                }
                onResult?.invoke(success)
            } catch (e: Exception) {
                onResult?.invoke(false)
            }
        }
    }

    fun clearErrorMessage() {
        _loginErrorMessage.value = null
    }

    suspend fun syncAllData(): Result<Unit> {
        return dataSyncCoordinator.syncAllData()
    }

    fun checkSmartSync(displayVersionCode: Long) {
        if (authManager.secureStorage.isLoggedIn()) {
            val lastVersion = authManager.secureStorage.getLastAppVersionCode()
            val isUpgrade = lastVersion > 0 && displayVersionCode > lastVersion
            val isFirstRecordedVersion = lastVersion == 0L
            val lastSync = authManager.secureStorage.getLastSyncTimestamp()
            val isStale = (System.currentTimeMillis() - lastSync) > 12 * 60 * 60 * 1000L

            authManager.secureStorage.saveLastAppVersionCode(displayVersionCode)

            if (isUpgrade || isFirstRecordedVersion || isStale) {
                viewModelScope.launch {
                    dataSyncCoordinator.syncAllData()
                }
            }
        }
    }
}
