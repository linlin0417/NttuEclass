package tw.edu.irika.nttueclass.presentation.pass

import android.app.Activity
import android.graphics.Bitmap
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tw.edu.irika.nttueclass.data.local.security.SecureCredentialStorage
import tw.edu.irika.nttueclass.pass.barcode.BarcodeGenerator
import tw.edu.irika.nttueclass.pass.crypto.DonationIntegerHelper
import tw.edu.irika.nttueclass.pass.crypto.NttuCryptoManager

/**
 * 臺東大學數位通行證介面 (NttuEPass)
 * 支援 NttuPass (1D Code 128 借書條碼) 與 NttuDataPass (2D ECC-256 特權加密條碼)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NttuEPassScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val storage = remember { SecureCredentialStorage(context) }

    // 學號與憑證資訊
    val studentId = remember { storage.getStudentId() ?: "11411188" }
    var specialToken by remember { mutableStateOf(storage.getSpecialToken() ?: "") }
    var tokenInput by remember { mutableStateOf(specialToken) }
    var isTokenEditorOpen by remember { mutableStateOf(false) }
    val donationInt by remember { mutableLongStateOf(storage.getDonationInteger()) }
    val donationData = remember(donationInt) { DonationIntegerHelper.decode(donationInt) }

    // 0 = 1D NttuPass, 1 = 2D NttuDataPass
    var selectedTab by remember { mutableIntStateOf(0) }
    var refreshKey by remember { mutableIntStateOf(0) }

    // 條碼點陣圖狀態
    var barcode1DBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var qrCodeBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var qrPayloadPreview by remember { mutableStateOf("") }

    // 進入時提高亮度，離開時恢復
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val layoutParams = activity?.window?.attributes
        val originalBrightness = layoutParams?.screenBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE

        layoutParams?.let {
            it.screenBrightness = 1.0f
            activity.window.attributes = it
        }

        onDispose {
            layoutParams?.let {
                it.screenBrightness = originalBrightness
                activity?.window?.attributes = it
            }
        }
    }

    // 非同步生成 1D 條碼
    LaunchedEffect(studentId) {
        withContext(Dispatchers.Default) {
            val content = NttuCryptoManager.generate1DBarcodeContent(studentId)
            val result = BarcodeGenerator.generateCode128(content, width = 850, height = 260)
            barcode1DBitmap = result.getOrNull()
        }
    }

    // 非同步生成 2D QR 碼
    LaunchedEffect(studentId, specialToken, donationInt, refreshKey) {
        withContext(Dispatchers.Default) {
            val encryptedPayload = NttuCryptoManager.encryptPayload(
                studentId = studentId,
                specialToken = specialToken,
                donationInteger = donationInt,
                timestamp = System.currentTimeMillis()
            )
            qrPayloadPreview = encryptedPayload
            val result = BarcodeGenerator.generateQrCode(encryptedPayload, size = 560)
            qrCodeBitmap = result.getOrNull()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "NttuEPass 校園數位通行證",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "學號：$studentId",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    if (selectedTab == 1) {
                        IconButton(onClick = { refreshKey++ }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "重新整理 QR Code"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 分頁切換
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("校園借書碼 (1D)") },
                    icon = { Icon(Icons.Default.ViewWeek, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("加密通行證 (2D)") },
                    icon = { Icon(Icons.Default.QrCode, contentDescription = null) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (selectedTab == 0) {
                // ==================== 1D NttuPass ====================
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "國立臺東大學校園條碼",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "適用於圖書館借書與門禁刷卡機",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // 條碼畫布 (純白底保證紅外線與光學掃描儀辨識率)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(12.dp))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (barcode1DBitmap != null) {
                                Image(
                                    bitmap = barcode1DBitmap!!.asImageBitmap(),
                                    contentDescription = "NttuPass 1D Barcode",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(130.dp)
                                )
                            } else {
                                Text(
                                    text = "條碼生成中...",
                                    color = Color.DarkGray,
                                    modifier = Modifier.padding(24.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = NttuCryptoManager.generate1DBarcodeContent(studentId),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 4.sp
                        )

                        Text(
                            text = "格式：Code 128 (學號 + 00)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // ==================== 2D NttuDataPass ====================
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "NttuDataPass 動態加密證",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "NIST P-256 (secp256r1) + AES-256-GCM 混合加密",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // QR Code 畫布 (純白底保證對比度)
                        Box(
                            modifier = Modifier
                                .size(260.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White)
                                .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(16.dp))
                                .padding(14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (qrCodeBitmap != null) {
                                Image(
                                    bitmap = qrCodeBitmap!!.asImageBitmap(),
                                    contentDescription = "NttuDataPass QR Code",
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    text = "密鑰計算中...",
                                    color = Color.DarkGray
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 贊助整數徽章與資訊
                        if (donationData.tier > 0 || donationData.badgeList.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WorkspacePremium,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = donationData.tierName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                donationData.badgeList.forEach { badge ->
                                    Spacer(modifier = Modifier.width(6.dp))
                                    AssistChip(
                                        onClick = {},
                                        label = { Text(badge, fontSize = 11.sp) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // 特殊憑證 Token 狀態顯示
                        val tokenStatusText = NttuCryptoManager.maskToken(
                            NttuCryptoManager.normalizeToken(specialToken)
                        )
                        Text(
                            text = "特殊憑證：$tokenStatusText",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ==================== 特殊憑證 Token 設定區塊 ====================
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "128 位特殊憑證 Token 設定",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        OutlinedButton(
                            onClick = { isTokenEditorOpen = !isTokenEditorOpen }
                        ) {
                            Text(if (isTokenEditorOpen) "收合" else "展開編輯")
                        }
                    }

                    if (isTokenEditorOpen) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "依據規範，未填寫時系統強制套用全域 128 位防空佔位符號 (128 個 '0')。不足 128 位將自動補 '0'，超出將自動截斷。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = tokenInput,
                            onValueChange = { tokenInput = it },
                            label = { Text("貼入 128 位自訂 Token") },
                            placeholder = { Text("留空自動使用預設佔位符") },
                            modifier = Modifier.fillMaxWidth(),
                            supportingText = {
                                Text(
                                    text = "當前長度: ${tokenInput.trim().length} / 128",
                                    fontFamily = FontFamily.Monospace
                                )
                            },
                            singleLine = false,
                            maxLines = 3
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                onClick = {
                                    tokenInput = ""
                                    specialToken = ""
                                    storage.saveSpecialToken("")
                                    refreshKey++
                                }
                            ) {
                                Text("清除並還原")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    specialToken = tokenInput.trim()
                                    storage.saveSpecialToken(specialToken)
                                    refreshKey++
                                    isTokenEditorOpen = false
                                }
                            ) {
                                Text("儲存憑證")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
