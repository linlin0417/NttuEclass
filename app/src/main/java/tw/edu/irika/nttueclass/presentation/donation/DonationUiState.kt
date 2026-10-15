package tw.edu.irika.nttueclass.presentation.donation

import tw.edu.irika.nttueclass.pass.crypto.DonationData

sealed interface DonationUiState {
    data object Loading : DonationUiState

    data class Success(
        val isConnected: Boolean = false,
        val donationData: DonationData,
        val donationInteger: Long = 0L,
        val statusMessage: String = ""
    ) : DonationUiState

    data class Error(val message: String, val canRetry: Boolean = true) : DonationUiState
}
