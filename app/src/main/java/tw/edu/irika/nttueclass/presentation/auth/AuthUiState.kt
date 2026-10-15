package tw.edu.irika.nttueclass.presentation.auth

sealed interface AuthUiState {
    data object Loading : AuthUiState

    data class Success(
        val isLoggedIn: Boolean,
        val studentId: String?,
        val isLockedOut: Boolean = false,
        val remainingAttempts: Int = 5,
        val shouldWarn: Boolean = false,
        val isLoggingIn: Boolean = false,
        val loginErrorMessage: String? = null
    ) : AuthUiState

    data class Error(val message: String, val canRetry: Boolean = true) : AuthUiState
}
