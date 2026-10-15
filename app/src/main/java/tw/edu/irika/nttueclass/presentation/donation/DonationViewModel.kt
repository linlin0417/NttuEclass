package tw.edu.irika.nttueclass.presentation.donation

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import tw.edu.irika.nttueclass.data.remote.billing.BillingManager
import tw.edu.irika.nttueclass.pass.crypto.DonationData
import tw.edu.irika.nttueclass.pass.crypto.DonationIntegerHelper
import javax.inject.Inject

@HiltViewModel
class DonationViewModel @Inject constructor(
    private val billingManager: BillingManager
) : ViewModel() {

    val uiState: StateFlow<DonationUiState> = combine(
        billingManager.isConnected,
        billingManager.donationData,
        billingManager.statusMessage
    ) { isConnected, donationData, statusMessage ->
        DonationUiState.Success(
            isConnected = isConnected,
            donationData = donationData,
            donationInteger = DonationIntegerHelper.encode(donationData),
            statusMessage = statusMessage
        ) as DonationUiState
    }.catch { e ->
        emit(DonationUiState.Error(e.message ?: "無法載入贊助資訊"))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = DonationUiState.Loading
    )

    fun launchPurchase(activity: Activity, productId: String, isSubscription: Boolean = false) {
        billingManager.launchPurchase(activity, productId, isSubscription)
    }

    fun revalidatePurchases(onResult: ((Boolean, String) -> Unit)? = null) {
        billingManager.revalidatePurchases(onResult)
    }

    override fun onCleared() {
        super.onCleared()
        billingManager.endConnection()
    }
}

