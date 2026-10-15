package tw.edu.irika.nttueclass.presentation.pass

import android.graphics.Bitmap
import tw.edu.irika.nttueclass.pass.crypto.DonationData

sealed interface PassUiState {
    data object Loading : PassUiState

    data class Success(
        val studentId: String?,
        val selectedTab: Int = 0,
        val specialToken: String = "",
        val tokenInput: String = "",
        val isTokenEditorOpen: Boolean = false,
        val donationInt: Long = 0L,
        val donationData: DonationData,
        val barcode1DBitmap: Bitmap? = null,
        val barcode1DContent: String = "",
        val qrCodeBitmap: Bitmap? = null,
        val qrPayloadPreview: String = "",
        val tokenStatusText: String = "",
        val isMaxBrightness: Boolean = true
    ) : PassUiState

    data class Error(val message: String, val canRetry: Boolean = true) : PassUiState
}
