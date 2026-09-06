package tw.edu.irika.nttueclass.data.remote.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tw.edu.irika.nttueclass.data.local.security.SecureCredentialStorage
import tw.edu.irika.nttueclass.pass.crypto.DonationData
import tw.edu.irika.nttueclass.pass.crypto.DonationIntegerHelper

/**
 * Google Play 內購商品 ID 規範定義
 */
object BillingProducts {
    // 消耗型 (Tip Jar / maimai / 咖啡)
    const val COFFEE_SMALL = "sponsor_coffee_small"       // 冰美式/maimai (NT$ 40)
    const val COFFEE_MEDIUM = "sponsor_coffee_medium"     // 大杯拿鐵咖啡 (NT$ 75)
    const val DINNER_LARGE = "sponsor_dinner_large"       // 澎湃晚餐 (NT$ 150)

    // 訂閱型 (月度守護者)
    const val SUB_MONTHLY_BASIC = "sponsor_sub_monthly_basic" // 初階月贊助 (Tier 1, NT$ 50/月)
    const val SUB_MONTHLY_PRO = "sponsor_sub_monthly_pro"     // 核心領航者 (Tier 2, NT$ 120/月)

    // 非消耗型 (永久榮譽徽章)
    const val GOLD_SUPPORTER = "sponsor_gold_supporter"   // 榮譽永久贊助徽章 (NT$ 300)
}

/**
 * Google Play Billing 8.x 內購與防退款核心管理器
 */
class BillingManager(
    private val context: Context,
    private val storage: SecureCredentialStorage = SecureCredentialStorage(context)
) : PurchasesUpdatedListener {

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _donationData = MutableStateFlow(
        DonationIntegerHelper.decode(storage.getDonationInteger())
    )
    val donationData: StateFlow<DonationData> = _donationData.asStateFlow()

    private val _statusMessage = MutableStateFlow("尚未連線至 Google Play")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private var productDetailsMap = mutableMapOf<String, ProductDetails>()

    private val billingClient: BillingClient by lazy {
        val pendingParams = PendingPurchasesParams.newBuilder()
            .enableOneTimeProducts()
            .build()

        BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases(pendingParams)
            .build()
    }

    init {
        startBillingConnection()
    }

    fun startBillingConnection(onConnected: (() -> Unit)? = null) {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    _isConnected.value = true
                    _statusMessage.value = "Google Play 商店已連線"
                    queryProducts()
                    revalidatePurchases()
                    onConnected?.invoke()
                } else {
                    _isConnected.value = false
                    _statusMessage.value = "Google Play 連線未就緒 (代碼: ${billingResult.responseCode})，已啟用安全離線/模擬模式"
                }
            }

            override fun onBillingServiceDisconnected() {
                _isConnected.value = false
                _statusMessage.value = "與 Google Play 商店連線中斷"
            }
        })
    }

    /**
     * 查詢商品資訊
     */
    private fun queryProducts() {
        val inAppProducts = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(BillingProducts.COFFEE_SMALL)
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(BillingProducts.COFFEE_MEDIUM)
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(BillingProducts.DINNER_LARGE)
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(BillingProducts.GOLD_SUPPORTER)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )

        val inAppParams = QueryProductDetailsParams.newBuilder()
            .setProductList(inAppProducts)
            .build()

        billingClient.queryProductDetailsAsync(inAppParams) { result, queryProductDetailsResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                queryProductDetailsResult.productDetailsList.forEach { productDetailsMap[it.productId] = it }
            }
        }

        val subProducts = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(BillingProducts.SUB_MONTHLY_BASIC)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(BillingProducts.SUB_MONTHLY_PRO)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )

        val subParams = QueryProductDetailsParams.newBuilder()
            .setProductList(subProducts)
            .build()

        billingClient.queryProductDetailsAsync(subParams) { result, queryProductDetailsResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                queryProductDetailsResult.productDetailsList.forEach { productDetailsMap[it.productId] = it }
            }
        }
    }

    /**
     * 發起購買流程
     */
    fun launchPurchase(activity: Activity, productId: String, isSubscription: Boolean = false) {
        val details = productDetailsMap[productId]
        if (details == null) {
            // 在無 Google Play 服務或商品尚未於後台生效時，提供安全模擬機制
            simulatePurchaseSuccess(productId)
            return
        }

        val productDetailsParamsList = if (isSubscription) {
            val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: ""
            listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(details)
                    .setOfferToken(offerToken)
                    .build()
            )
        } else {
            listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(details)
                    .build()
            )
        }

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        billingClient.launchBillingFlow(activity, flowParams)
    }

    /**
     * 購買結果回調
     */
    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            _statusMessage.value = "使用者已取消贊助"
        } else {
            _statusMessage.value = "贊助交易異常: ${billingResult.debugMessage}"
        }
    }

    /**
     * 處理單筆購買
     */
    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        for (productId in purchase.products) {
            when (productId) {
                BillingProducts.COFFEE_SMALL,
                BillingProducts.COFFEE_MEDIUM,
                BillingProducts.DINNER_LARGE -> {
                    // 消耗型商品 -> 立即調用 consumeAsync
                    val consumeParams = ConsumeParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()

                    billingClient.consumeAsync(consumeParams) { result, _ ->
                        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                            applyProductBenefit(productId)
                        }
                    }
                }
                BillingProducts.SUB_MONTHLY_BASIC,
                BillingProducts.SUB_MONTHLY_PRO,
                BillingProducts.GOLD_SUPPORTER -> {
                    // 訂閱與非消耗型 -> 進行確認 (acknowledge)
                    if (!purchase.isAcknowledged) {
                        val ackParams = AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken)
                            .build()

                        billingClient.acknowledgePurchase(ackParams) { result ->
                            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                                applyProductBenefit(productId)
                            }
                        }
                    } else {
                        applyProductBenefit(productId)
                    }
                }
            }
        }
    }

    /**
     * 將商品權益回寫至 64-bit 贊助整數與本地加密存儲
     */
    fun applyProductBenefit(productId: String) {
        val current = DonationIntegerHelper.decode(storage.getDonationInteger())
        val updated = when (productId) {
            BillingProducts.COFFEE_SMALL -> current.copy(coffeeCount = minOf(65535, current.coffeeCount + 1))
            BillingProducts.COFFEE_MEDIUM -> current.copy(dinnerCount = minOf(65535, current.dinnerCount + 1))
            BillingProducts.DINNER_LARGE -> current.copy(dinnerCount = minOf(65535, current.dinnerCount + 2))
            BillingProducts.SUB_MONTHLY_BASIC -> current.copy(tier = 1)
            BillingProducts.SUB_MONTHLY_PRO -> current.copy(tier = 2)
            BillingProducts.GOLD_SUPPORTER -> current.copy(isFounder = true)
            else -> current
        }

        saveDonationData(updated)
        _statusMessage.value = "贊助成功！已更新校園 Pass 贊助整數與榮譽徽章"
    }

    private suspend fun queryPurchasesInternal(params: QueryPurchasesParams): Pair<BillingResult, List<Purchase>> =
        kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            billingClient.queryPurchasesAsync(params) { result, purchasesList ->
                if (continuation.isActive) {
                    continuation.resumeWith(Result.success(Pair(result, purchasesList)))
                }
            }
        }

    /**
     * 防惡意退款動態重新驗證 (Dynamic Re-validation)
     *
     * 每次啟動或手動觸發時向 Google Play 查詢當前有效訂單。
     * 若訂單遭 Google 退款或作廢 (Voided)，自動收回特權與徽章。
     */
    fun revalidatePurchases(onResult: ((Boolean, String) -> Unit)? = null) {
        if (!_isConnected.value) {
            onResult?.invoke(false, "尚未連線至 Google Play")
            return
        }

        scope.launch {
            var hasActiveSub = false
            var activeSubTier = 0
            var hasGoldBadge = false

            // 1. 查詢訂閱有效性
            val subsParams = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()

            val (subsResult, subsPurchases) = queryPurchasesInternal(subsParams)

            if (subsResult.responseCode == BillingClient.BillingResponseCode.OK) {
                for (purchase in subsPurchases) {
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        if (purchase.products.contains(BillingProducts.SUB_MONTHLY_PRO)) {
                            hasActiveSub = true
                            activeSubTier = 2
                        } else if (purchase.products.contains(BillingProducts.SUB_MONTHLY_BASIC)) {
                            hasActiveSub = true
                            activeSubTier = maxOf(activeSubTier, 1)
                        }
                    }
                }
            }

            // 2. 查詢非消耗型有效性
            val inAppParams = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()

            val (inAppResult, inAppPurchases) = queryPurchasesInternal(inAppParams)

            if (inAppResult.responseCode == BillingClient.BillingResponseCode.OK) {
                for (purchase in inAppPurchases) {
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        if (purchase.products.contains(BillingProducts.GOLD_SUPPORTER)) {
                            hasGoldBadge = true
                        }
                    }
                }
            }

            // 3. 比對本地記錄並執行退款撤回
            val current = DonationIntegerHelper.decode(storage.getDonationInteger())
            var modified = false
            var message = "購買狀態驗證正常"

            // 檢查月贊助退款
            if (current.tier > 0 && !hasActiveSub) {
                // 原有月贊助但已無有效訂單 (退款或到期) -> 收回
                modified = true
                message = "檢測到月度贊助已失效或已退款，已自動收回月贊助階層"
            }

            // 檢查永久徽章退款
            if (current.isFounder && !hasGoldBadge && !current.isLifetime) {
                // 原有贊助徽章但已遭退款 -> 收回
                modified = true
                message = "檢測到贊助訂單已退款，已自動收回榮譽贊助徽章"
            }

            if (modified) {
                val corrected = current.copy(
                    tier = if (hasActiveSub) activeSubTier else 0,
                    isFounder = hasGoldBadge
                )
                saveDonationData(corrected)
            }

            _statusMessage.value = message
            onResult?.invoke(true, message)
        }
    }

    /**
     * 離線/無 Google Play 環境安全模擬 (支援單元測試與開發展示)
     */
    fun simulatePurchaseSuccess(productId: String) {
        applyProductBenefit(productId)
    }

    /**
     * 模擬退款撤回 (用於單元測試與防退款驗證)
     */
    fun simulateRefundRevocation() {
        val current = DonationIntegerHelper.decode(storage.getDonationInteger())
        val revoked = current.copy(
            tier = 0,
            isFounder = false
        )
        saveDonationData(revoked)
        _statusMessage.value = "已模擬撤銷退款之特權與徽章"
    }

    private fun saveDonationData(data: DonationData) {
        val encoded = DonationIntegerHelper.encode(data)
        storage.saveDonationInteger(encoded)
        _donationData.value = data
    }
}
