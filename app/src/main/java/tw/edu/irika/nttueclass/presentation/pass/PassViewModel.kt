package tw.edu.irika.nttueclass.presentation.pass

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tw.edu.irika.nttueclass.data.local.security.SecureCredentialStorage
import tw.edu.irika.nttueclass.pass.barcode.BarcodeGenerator
import tw.edu.irika.nttueclass.pass.crypto.DonationData
import tw.edu.irika.nttueclass.pass.crypto.DonationIntegerHelper
import tw.edu.irika.nttueclass.pass.crypto.NttuCryptoManager
import javax.inject.Inject

@HiltViewModel
class PassViewModel @Inject constructor(
    private val storage: SecureCredentialStorage
) : ViewModel() {

    private val _studentId = MutableStateFlow<String?>(storage.getStudentId())
    private val _donationInt = MutableStateFlow<Long>(storage.getDonationInteger())

    private val _selectedTab = MutableStateFlow(0)
    private val _specialToken = MutableStateFlow(storage.getSpecialToken() ?: "")
    private val _tokenInput = MutableStateFlow(storage.getSpecialToken() ?: "")
    private val _isTokenEditorOpen = MutableStateFlow(false)
    private val _isMaxBrightness = MutableStateFlow(true)
    private val _barcode1DBitmap = MutableStateFlow<Bitmap?>(null)
    private val _qrCodeBitmap = MutableStateFlow<Bitmap?>(null)
    private val _qrPayloadPreview = MutableStateFlow("")

    val uiState: StateFlow<PassUiState> = combine(
        _studentId,
        _donationInt,
        _selectedTab,
        _specialToken,
        _tokenInput
    ) { currentStudentId, currentDonationInt, tab, specToken, tokInput ->
        Tuple5(currentStudentId, currentDonationInt, tab, specToken, tokInput)
    }.combine(
        combine(_isTokenEditorOpen, _isMaxBrightness, _barcode1DBitmap) { isEditorOpen, maxBrightness, b1d ->
            Triple(isEditorOpen, maxBrightness, b1d)
        }
    ) { t5, (isEditorOpen, maxBrightness, b1d) ->
        PassControls(
            studentId = t5.a,
            donationInt = t5.b,
            selectedTab = t5.c,
            specialToken = t5.d,
            tokenInput = t5.e,
            isTokenEditorOpen = isEditorOpen,
            isMaxBrightness = maxBrightness,
            barcode1DBitmap = b1d
        )
    }.combine(
        combine(_qrCodeBitmap, _qrPayloadPreview) { qr, preview ->
            Pair(qr, preview)
        }
    ) { controls, (qr, preview) ->
        val barcodeContent = if (!controls.studentId.isNullOrBlank()) {
            NttuCryptoManager.generate1DBarcodeContent(controls.studentId)
        } else ""

        val tokenStatusText = NttuCryptoManager.maskToken(
            NttuCryptoManager.normalizeToken(controls.specialToken)
        )

        val donationData = DonationIntegerHelper.decode(controls.donationInt)

        PassUiState.Success(
            studentId = controls.studentId,
            selectedTab = controls.selectedTab,
            specialToken = controls.specialToken,
            tokenInput = controls.tokenInput,
            isTokenEditorOpen = controls.isTokenEditorOpen,
            donationInt = controls.donationInt,
            donationData = donationData,
            barcode1DBitmap = controls.barcode1DBitmap,
            barcode1DContent = barcodeContent,
            qrCodeBitmap = qr,
            qrPayloadPreview = preview,
            tokenStatusText = tokenStatusText,
            isMaxBrightness = controls.isMaxBrightness
        ) as PassUiState
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = PassUiState.Loading
    )

    private data class PassControls(
        val studentId: String?,
        val donationInt: Long,
        val selectedTab: Int,
        val specialToken: String,
        val tokenInput: String,
        val isTokenEditorOpen: Boolean,
        val isMaxBrightness: Boolean,
        val barcode1DBitmap: Bitmap?
    )

    private data class Tuple5<A, B, C, D, E>(
        val a: A, val b: B, val c: C, val d: D, val e: E
    )

    init {
        generateBarcodes()
    }

    fun onTabSelect(tab: Int) {
        _selectedTab.value = tab
    }

    fun onTokenInputChange(input: String) {
        _tokenInput.value = input
    }

    fun toggleTokenEditor() {
        _isTokenEditorOpen.value = !_isTokenEditorOpen.value
    }

    fun setMaxBrightness(enabled: Boolean) {
        _isMaxBrightness.value = enabled
    }

    fun toggleMaxBrightness() {
        _isMaxBrightness.value = !_isMaxBrightness.value
    }

    fun clearAndResetToken() {
        _tokenInput.value = ""
        _specialToken.value = ""
        storage.saveSpecialToken("")
        generateQrCode()
    }

    fun saveToken() {
        val trimmed = _tokenInput.value.trim()
        _specialToken.value = trimmed
        storage.saveSpecialToken(trimmed)
        _isTokenEditorOpen.value = false
        generateQrCode()
    }

    fun refreshQrCode() {
        _studentId.value = storage.getStudentId()
        _donationInt.value = storage.getDonationInteger()
        generateBarcodes()
    }

    private fun generateBarcodes() {
        val currentId = _studentId.value
        if (currentId.isNullOrBlank()) return
        generate1DBarcode()
        generateQrCode()
    }

    private fun generate1DBarcode() {
        val currentId = _studentId.value ?: return
        if (currentId.isBlank()) return
        viewModelScope.launch(Dispatchers.Default) {
            val content = NttuCryptoManager.generate1DBarcodeContent(currentId)
            val result = BarcodeGenerator.generateCode128(content, width = 850, height = 260)
            _barcode1DBitmap.value = result.getOrNull()
        }
    }

    private fun generateQrCode() {
        val currentId = _studentId.value ?: return
        if (currentId.isBlank()) return
        val currentDonation = _donationInt.value
        viewModelScope.launch(Dispatchers.Default) {
            val encryptedPayload = NttuCryptoManager.encryptPayload(
                studentId = currentId,
                specialToken = _specialToken.value,
                donationInteger = currentDonation,
                timestamp = System.currentTimeMillis()
            )
            _qrPayloadPreview.value = encryptedPayload
            val result = BarcodeGenerator.generateQrCode(encryptedPayload, size = 560)
            _qrCodeBitmap.value = result.getOrNull()
        }
    }
}
