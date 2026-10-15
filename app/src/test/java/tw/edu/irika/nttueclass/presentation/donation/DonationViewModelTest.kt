package tw.edu.irika.nttueclass.presentation.donation

import android.app.Activity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import tw.edu.irika.nttueclass.data.remote.billing.BillingProducts
import tw.edu.irika.nttueclass.pass.crypto.DonationData
import tw.edu.irika.nttueclass.testutil.FakeBillingManager
import tw.edu.irika.nttueclass.testutil.MainDispatcherRule
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class DonationViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakeBillingManager = FakeBillingManager()

    private fun createViewModel(): DonationViewModel {
        return DonationViewModel(billingManager = fakeBillingManager)
    }

    private fun createFakeActivity(): Activity {
        return tw.edu.irika.nttueclass.testutil.FakeActivity()
    }

    @Test
    fun testInitialUiStateIsSuccessWithBillingData() = runTest {
        fakeBillingManager.isConnectedFlow.value = true
        fakeBillingManager.donationDataFlow.value = DonationData(coffeeCount = 5, isFounder = true)
        fakeBillingManager.statusMessageFlow.value = "已就緒"

        val viewModel = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        assertTrue(viewModel.uiState.value is DonationUiState.Success)
        val state = viewModel.uiState.value as DonationUiState.Success
        assertTrue(state.isConnected)
        assertEquals(5, state.donationData.coffeeCount)
        assertTrue(state.donationData.isFounder)
        assertEquals("已就緒", state.statusMessage)

        job.cancel()
    }

    @Test
    fun testLaunchPurchaseDelegatesToBillingManager() = runTest {
        val viewModel = createViewModel()
        val activity = createFakeActivity()

        viewModel.launchPurchase(activity, BillingProducts.COFFEE_SMALL, isSubscription = false)
        assertEquals(BillingProducts.COFFEE_SMALL, fakeBillingManager.lastPurchasedProductId)

        viewModel.launchPurchase(activity, BillingProducts.SUB_MONTHLY_PRO, isSubscription = true)
        assertEquals(BillingProducts.SUB_MONTHLY_PRO, fakeBillingManager.lastPurchasedProductId)
    }

    @Test
    fun testRevalidatePurchasesCallback() = runTest {
        val viewModel = createViewModel()
        fakeBillingManager.revalidateResult = true to "所有訂單皆有效"

        var callbackSuccess = false
        var callbackMessage = ""
        viewModel.revalidatePurchases { success, msg ->
            callbackSuccess = success
            callbackMessage = msg
        }

        assertTrue(callbackSuccess)
        assertEquals("所有訂單皆有效", callbackMessage)
    }
}
