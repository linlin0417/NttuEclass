package tw.edu.irika.nttueclass.presentation.donation

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalActivity
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tw.edu.irika.nttueclass.data.remote.billing.BillingManager
import tw.edu.irika.nttueclass.data.remote.billing.BillingProducts
import tw.edu.irika.nttueclass.pass.crypto.DonationData
import tw.edu.irika.nttueclass.pass.crypto.DonationIntegerHelper

@Composable
fun DonationScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val billingManager = remember { BillingManager(context) }

    val isConnected by billingManager.isConnected.collectAsState()
    val donationData by billingManager.donationData.collectAsState()
    val statusMessage by billingManager.statusMessage.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // 標題與簡介
        item {
            DonationHeader(isConnected = isConnected)
        }

        // 當前贊助整數與徽章狀態卡片 (Live Status)
        item {
            LiveDonationStatusCard(
                donationData = donationData,
                statusMessage = statusMessage
            )
        }

        // 月贊助訂閱 (強調卡片)
        item {
            Text(
                text = "月度守護者訂閱 (Monthly Supporter)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        item {
            MonthlySubscriptionCard(
                title = "初階校園守護者",
                desc = "點亮校園 Pass 初階金色徽章，贊助伺服器運算",
                price = "NT$ 50 / 月",
                isCurrentTier = donationData.tier == 1,
                onSubscribe = {
                    activity?.let {
                        billingManager.launchPurchase(it, BillingProducts.SUB_MONTHLY_BASIC, isSubscription = true)
                    }
                }
            )
        }

        item {
            MonthlySubscriptionCard(
                title = "核心領航者月贊助",
                desc = "點亮頂級動態榮譽徽章，全方位支持開源開發",
                price = "NT$ 120 / 月",
                isCurrentTier = donationData.tier == 2,
                onSubscribe = {
                    activity?.let {
                        billingManager.launchPurchase(it, BillingProducts.SUB_MONTHLY_PRO, isSubscription = true)
                    }
                }
            )
        }

        // 單次自由贊助項目 (消耗型 Tip Jar)
        item {
            Text(
                text = "單次自由贊助 (Tip Jar)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        item {
            TipOptionCard(
                icon = Icons.Default.LocalActivity,
                title = "請作者打一道 maimai / 冰美式",
                desc = "累計次數寫入贊助整數 (已累計 ${donationData.coffeeCount} 次)",
                price = "NT$ 40",
                onPurchase = {
                    activity?.let {
                        billingManager.launchPurchase(it, BillingProducts.COFFEE_SMALL)
                    }
                }
            )
        }

        item {
            TipOptionCard(
                icon = Icons.Default.Coffee,
                title = "請作者喝一杯大杯拿鐵",
                desc = "熬夜解析學園系統改版之精神食糧",
                price = "NT$ 75",
                onPurchase = {
                    activity?.let {
                        billingManager.launchPurchase(it, BillingProducts.COFFEE_MEDIUM)
                    }
                }
            )
        }

        item {
            TipOptionCard(
                icon = Icons.Default.Restaurant,
                title = "請作者吃一頓澎湃晚餐",
                desc = "爆肝修復與維護之最高能量補給",
                price = "NT$ 150",
                onPurchase = {
                    activity?.let {
                        billingManager.launchPurchase(it, BillingProducts.DINNER_LARGE)
                    }
                }
            )
        }

        // 永久榮譽贊助徽章 (非消耗型)
        item {
            Text(
                text = "永久榮譽專屬徽章",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        item {
            PermanentBadgeCard(
                isUnlocked = donationData.isFounder || donationData.isLifetime,
                onUnlock = {
                    activity?.let {
                        billingManager.launchPurchase(it, BillingProducts.GOLD_SUPPORTER)
                    }
                }
            )
        }

        // 贊助整數與防退款安全機制卡片
        item {
            DonationSecurityCard(
                onRevalidate = {
                    billingManager.revalidatePurchases { success, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun DonationHeader(isConnected: Boolean) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "支持與贊助 NttuEclass",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!isConnected) {
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            "離線模式",
                            fontSize = 10.sp
                        )
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "本應用為開源社群維護工具。感謝每一位贊助者的支持，讓 NttuEclass 持續提供更好的功能與服務！",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LiveDonationStatusCard(
    donationData: DonationData,
    statusMessage: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.WorkspacePremium,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "當前贊助成就狀態",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "• 當前月贊助：${donationData.tierName}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "• 累計咖啡/maimai：${donationData.coffeeCount} 次 | 晚餐/拿鐵：${donationData.dinnerCount} 次",
                style = MaterialTheme.typography.bodyMedium
            )
            if (donationData.badgeList.isNotEmpty()) {
                Text(
                    text = "• 榮譽徽章：${donationData.badgeList.joinToString("、")}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            val encodedLong = DonationIntegerHelper.encode(donationData)
            Text(
                text = "64-bit 贊助整數: $encodedLong (已同步至 NttuDataPass)",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (statusMessage.isNotBlank()) {
                Text(
                    text = "系統日誌: $statusMessage",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun MonthlySubscriptionCard(
    title: String,
    desc: String,
    price: String,
    isCurrentTier: Boolean,
    onSubscribe: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = price,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onSubscribe,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isCurrentTier,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = if (isCurrentTier) "已訂閱該階層" else "訂閱加入",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun TipOptionCard(
    icon: ImageVector,
    title: String,
    desc: String,
    price: String,
    onPurchase: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = onPurchase,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(text = price, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PermanentBadgeCard(
    isUnlocked: Boolean,
    onUnlock: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (isUnlocked) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "榮譽永久贊助者標記",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "終身點亮 NttuDataPass 創始支持者榮譽勳章",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = onUnlock,
                enabled = !isUnlocked,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(text = if (isUnlocked) "已永久解鎖" else "NT$ 300", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DonationSecurityCard(
    onRevalidate: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "防退款安全機制與查單校驗",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "所有贊助依 Google 規範動態查單。若訂單遭 Google 退款或作廢 (Voided)，系統將自動收回月贊助與勳章，杜絕退款騙取特權。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onRevalidate,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("重新檢查訂單有效性", fontSize = 13.sp)
            }
        }
    }
}
