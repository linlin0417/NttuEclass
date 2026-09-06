package tw.edu.irika.nttueclass.presentation.pass.verifier

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tw.edu.irika.nttueclass.data.local.db.AppDatabase
import tw.edu.irika.nttueclass.data.local.db.entity.RollCallRecordEntity
import tw.edu.irika.nttueclass.data.local.security.SecureCredentialStorage
import tw.edu.irika.nttueclass.pass.crypto.DonationData
import tw.edu.irika.nttueclass.pass.crypto.DonationIntegerHelper
import tw.edu.irika.nttueclass.pass.crypto.NttuCryptoManager
import tw.edu.irika.nttueclass.pass.crypto.NttuPassPayload
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 預載學生名單項目資料模型
 */
data class RosterItem(
    val studentId: String,
    val name: String,
    val departmentClass: String = "",
    val initialLeaveStatus: String = "" // "", "公假", "病假", "事假"
)

/**
 * 隱藏式 ECC-256 解密核銷與課程點名系統
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiddenVerifierScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val db = remember { AppDatabase.getInstance(context) }
    val dao = remember { db.rollCallRecordDao() }
    val storage = remember { SecureCredentialStorage(context) }

    // 點名場次 ID (例如當前課程或日期)
    var sessionId by remember { mutableStateOf("演算法分析 09/06") }

    // 預載名單 (學號 -> RosterItem)
    var rosterMap by remember {
        mutableStateOf(
            mapOf(
                "11411188" to RosterItem("11411188", "林同學", "資工三A"),
                "11411101" to RosterItem("11411101", "張小明", "資工三A"),
                "11411102" to RosterItem("11411102", "李小華", "資工三A"),
                "11411103" to RosterItem("11411103", "王大明", "資工三A", "公假"),
                "11411104" to RosterItem("11411104", "陳小美", "資工三A", "病假"),
                "11411105" to RosterItem("11411105", "趙大同", "資工三A")
            )
        )
    }

    // 歷史紀錄從 Room 資料庫即時觀察
    val recordsFromDb by dao.getRecordsBySession(sessionId).collectAsState(initial = emptyList())

    // 掃描與解密狀態
    var lastScannedPayload by remember { mutableStateOf<NttuPassPayload?>(null) }
    var lastDecodedDonation by remember { mutableStateOf<DonationData?>(null) }
    var lastError by remember { mutableStateOf<String?>(null) }
    var lastScanTime by remember { mutableLongStateOf(0L) }

    // 手動輸入 / 模擬掃描輸入框
    var manualInput by remember { mutableStateOf("") }
    var showRosterImportDialog by remember { mutableStateOf(false) }
    var rosterImportText by remember { mutableStateOf("") }

    // 相機權限
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            scope.launch { snackbarHostState.showSnackbar("未授予相機權限，可使用下方手動/模擬輸入") }
        }
    }

    // 儀表板分頁過濾 (0: 全部, 1: 未到, 2: 請假, 3: 已到與額外)
    var selectedFilterTab by remember { mutableIntStateOf(0) }

    // 核心核銷處理函式 (支援重複掃描覆蓋更新)
    fun processRawScan(rawContent: String) {
        val result = NttuCryptoManager.parseAnyPass(rawContent)
        if (result.isSuccess) {
            val payload = result.getOrThrow()
            lastScannedPayload = payload
            lastError = null
            lastScanTime = System.currentTimeMillis()

            val donation = DonationIntegerHelper.decode(payload.donationInteger)
            lastDecodedDonation = donation

            // 比對預載名單
            val rosterStudent = rosterMap[payload.studentId]
            val isExternal = rosterStudent == null
            val studentName = rosterStudent?.name ?: "外部學員"
            val deptClass = rosterStudent?.departmentClass ?: "非名單人員"
            val initialLeave = rosterStudent?.initialLeaveStatus ?: ""

            scope.launch(Dispatchers.IO) {
                // 檢查是否已有簽到紀錄 (重複掃描檢查)
                val existingRecord = dao.findRecord(sessionId, payload.studentId)
                if (existingRecord != null) {
                    // 覆蓋更新時間與條碼類型
                    val updated = existingRecord.copy(
                        scannedAt = System.currentTimeMillis(),
                        barcodeType = if (payload.is1D) "1D" else "2D",
                        verificationStatus = "SUCCESS",
                        isPresent = true,
                        donationTier = donation.tier,
                        badges = donation.badgesRaw
                    )
                    dao.updateRecord(updated)
                    snackbarHostState.showSnackbar("學號 ${payload.studentId} 簽到時間已覆蓋更新")
                } else {
                    // 新增簽到紀錄
                    val newRecord = RollCallRecordEntity(
                        sessionId = sessionId,
                        studentId = payload.studentId,
                        studentName = studentName,
                        departmentClass = deptClass,
                        barcodeType = if (payload.is1D) "1D" else "2D",
                        verificationStatus = "SUCCESS",
                        leaveStatus = initialLeave,
                        isPresent = true,
                        isExternal = isExternal,
                        donationTier = donation.tier,
                        badges = donation.badgesRaw,
                        scannedAt = System.currentTimeMillis()
                    )
                    dao.insertRecord(newRecord)
                    snackbarHostState.showSnackbar("學號 ${payload.studentId} 簽到成功！")
                }
            }
        } else {
            lastError = result.exceptionOrNull()?.message ?: "條碼解密或格式解析失敗"
            lastScannedPayload = null
            lastDecodedDonation = null
            scope.launch {
                snackbarHostState.showSnackbar("核銷失敗: $lastError")
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "NttuDataPass 核銷驗證台",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "活動與課程點名系統 · $sessionId",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showRosterImportDialog = true }) {
                        Icon(Icons.Default.GroupAdd, contentDescription = "預載名單")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ==================== 1. 掃描鏡頭 / 模擬輸入卡片 ====================
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (hasCameraPermission) {
                            // CameraX 預覽窗
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                AndroidView(
                                    factory = { ctx ->
                                        val previewView = PreviewView(ctx)
                                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                        cameraProviderFuture.addListener({
                                            val cameraProvider = cameraProviderFuture.get()
                                            val preview = Preview.Builder().build().also {
                                                it.setSurfaceProvider(previewView.surfaceProvider)
                                            }

                                            var isScanning = true
                                            val imageAnalysis = ImageAnalysis.Builder()
                                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                                .build()

                                            val reader = MultiFormatReader()
                                            imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                                                if (isScanning) {
                                                    try {
                                                        val plane = imageProxy.planes[0]
                                                        val buffer = plane.buffer
                                                        val bytes = ByteArray(buffer.remaining())
                                                        buffer.get(bytes)

                                                        val source = PlanarYUVLuminanceSource(
                                                            bytes,
                                                            imageProxy.width,
                                                            imageProxy.height,
                                                            0,
                                                            0,
                                                            imageProxy.width,
                                                            imageProxy.height,
                                                            false
                                                        )
                                                        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
                                                        val scanResult = reader.decodeWithState(binaryBitmap)
                                                        if (scanResult != null && scanResult.text.isNotBlank()) {
                                                            isScanning = false
                                                            processRawScan(scanResult.text)
                                                            // 1.5 秒防抖
                                                            previewView.postDelayed({ isScanning = true }, 1500)
                                                        }
                                                    } catch (_: Exception) {
                                                    } finally {
                                                        imageProxy.close()
                                                    }
                                                } else {
                                                    imageProxy.close()
                                                }
                                            }

                                            try {
                                                cameraProvider.unbindAll()
                                                cameraProvider.bindToLifecycle(
                                                    context as androidx.lifecycle.LifecycleOwner,
                                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                                    preview,
                                                    imageAnalysis
                                                )
                                            } catch (_: Exception) {}
                                        }, ContextCompat.getMainExecutor(ctx))
                                        previewView
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        } else {
                            OutlinedButton(
                                onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("啟用 CameraX 條碼掃描相機")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 模擬掃描與快速貼上手動輸入
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = manualInput,
                                onValueChange = { manualInput = it },
                                label = { Text("模擬掃描輸入 (1D 條碼或 2D 密文)") },
                                placeholder = { Text("例如 1141118800 或 ECC 密文") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (manualInput.isNotBlank()) {
                                        processRawScan(manualInput)
                                        manualInput = ""
                                    }
                                }
                            ) {
                                Text("核銷")
                            }
                        }

                        // 快速測試按鈕
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val studentId = storage.getStudentId() ?: "11411188"
                                    manualInput = NttuCryptoManager.generate1DBarcodeContent(studentId)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("填入 1D 碼", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    val studentId = storage.getStudentId() ?: "11411188"
                                    val token = storage.getSpecialToken()
                                    val donation = storage.getDonationInteger()
                                    manualInput = NttuCryptoManager.encryptPayload(studentId, token, donation)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("填入 2D 密文", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // ==================== 2. 實時解密與贊助狀態卡片 (Security Status Banner) ====================
            if (lastScannedPayload != null) {
                val payload = lastScannedPayload!!
                val donation = lastDecodedDonation ?: DonationData()
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "核驗成功 (學號: ${payload.studentId})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // 金鑰模式狀態
                            Text(
                                text = if (payload.is1D) "條碼模式：校園一維條碼 (1D Code 128)"
                                else "解密模式：ECC-256 私鑰驗證成功 (NIST P-256 Active)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )

                            // 特殊憑證狀態
                            val tokenLabel = if (NttuCryptoManager.isPlaceholder(payload.token)) {
                                "特殊憑證：未綁定 (預設佔位符)"
                            } else {
                                "特殊憑證：已驗證有效 (${NttuCryptoManager.maskToken(payload.token)})"
                            }
                            Text(
                                text = tokenLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                            // 贊助整數拆解展示
                            Text(
                                text = "贊助資訊解算 (Donation Integer: ${payload.donationInteger})：",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "• 等級：${donation.tierName} (Tier ${donation.tier})",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "• 冰美式/maimai：${donation.coffeeCount} 杯 | 拿鐵/晚餐：${donation.dinnerCount} 次",
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (donation.badgeList.isNotEmpty()) {
                                Text(
                                    text = "• 榮譽徽章：${donation.badgeList.joinToString(", ")}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // ==================== 3. 點名統計儀表板與匯出 ====================
            item {
                // 統計數據
                val totalEnrolled = rosterMap.size
                val scannedSet = recordsFromDb.map { it.studentId }.toSet()
                val presentCount = recordsFromDb.count { it.isPresent && !it.isExternal }
                val leaveCount = rosterMap.values.count { it.initialLeaveStatus.isNotBlank() && !scannedSet.contains(it.studentId) }
                val absentCount = rosterMap.values.count { it.initialLeaveStatus.isBlank() && !scannedSet.contains(it.studentId) }
                val externalCount = recordsFromDb.count { it.isExternal }

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "點名儀表板",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            // 一鍵通訊軟體複製摘要
                            OutlinedButton(
                                onClick = {
                                    val summaryText = buildRollCallReport(
                                        session = sessionId,
                                        roster = rosterMap,
                                        records = recordsFromDb
                                    )
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("點名摘要", summaryText))
                                    Toast.makeText(context, "已複製點名完整報告！", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("複製報告摘要", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 統計橫向展示
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            StatColumn("應到", "$totalEnrolled 人", MaterialTheme.colorScheme.onSurface)
                            StatColumn("實到", "$presentCount 人", Color(0xFF2E7D32))
                            StatColumn("請假", "$leaveCount 人", Color(0xFFE65100))
                            StatColumn("未到", "$absentCount 人", Color(0xFFC62828))
                            StatColumn("額外", "$externalCount 人", Color(0xFF00838F))
                        }
                    }
                }
            }

            // ==================== 4. 分頁過濾標籤 ====================
            item {
                TabRow(
                    selectedTabIndex = selectedFilterTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(selected = selectedFilterTab == 0, onClick = { selectedFilterTab = 0 }, text = { Text("全部 (${rosterMap.size + recordsFromDb.count { it.isExternal }})") })
                    Tab(selected = selectedFilterTab == 1, onClick = { selectedFilterTab = 1 }, text = { Text("未到") })
                    Tab(selected = selectedFilterTab == 2, onClick = { selectedFilterTab = 2 }, text = { Text("請假") })
                    Tab(selected = selectedFilterTab == 3, onClick = { selectedFilterTab = 3 }, text = { Text("已到/額外") })
                }
            }

            // ==================== 5. 名單列表展示 ====================
            val scannedMap = recordsFromDb.associateBy { it.studentId }

            // 組合展示清單
            val displayItems = buildList {
                // 預載名單
                rosterMap.values.forEach { roster ->
                    val record = scannedMap[roster.studentId]
                    val isPresent = record != null && record.isPresent
                    val leave = roster.initialLeaveStatus
                    when (selectedFilterTab) {
                        0 -> add(RollCallUiItem(roster.studentId, roster.name, roster.departmentClass, isPresent, leave, false, record?.scannedAt))
                        1 -> if (!isPresent && leave.isBlank()) add(RollCallUiItem(roster.studentId, roster.name, roster.departmentClass, false, "", false, null))
                        2 -> if (!isPresent && leave.isNotBlank()) add(RollCallUiItem(roster.studentId, roster.name, roster.departmentClass, false, leave, false, null))
                        3 -> if (isPresent) add(RollCallUiItem(roster.studentId, roster.name, roster.departmentClass, true, leave, false, record?.scannedAt))
                    }
                }
                // 額外人員
                recordsFromDb.filter { it.isExternal }.forEach { ext ->
                    if (selectedFilterTab == 0 || selectedFilterTab == 3) {
                        add(RollCallUiItem(ext.studentId, ext.studentName, ext.departmentClass, true, "", true, ext.scannedAt))
                    }
                }
            }

            items(displayItems) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = item.studentId,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace
                                )
                                if (item.isExternal) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    AssistChip(
                                        onClick = {},
                                        label = { Text("非名單人員", fontSize = 10.sp) }
                                    )
                                }
                            }
                            if (item.departmentClass.isNotBlank()) {
                                Text(
                                    text = item.departmentClass,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (item.scannedAt != null) {
                                val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(item.scannedAt))
                                Text(
                                    text = "簽到時間: $timeStr",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF2E7D32)
                                )
                            }
                        }

                        // 狀態標籤與手動簽到按鈕
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (item.isPresent) {
                                AssistChip(
                                    onClick = {},
                                    label = { Text("已到", color = Color(0xFF2E7D32)) }
                                )
                            } else if (item.leaveStatus.isNotBlank()) {
                                AssistChip(
                                    onClick = {},
                                    label = { Text(item.leaveStatus, color = Color(0xFFE65100)) }
                                )
                            } else {
                                TextButton(
                                    onClick = {
                                        // 手動簽到
                                        scope.launch(Dispatchers.IO) {
                                            val record = RollCallRecordEntity(
                                                sessionId = sessionId,
                                                studentId = item.studentId,
                                                studentName = item.name,
                                                departmentClass = item.departmentClass,
                                                barcodeType = "MANUAL",
                                                verificationStatus = "SUCCESS",
                                                isPresent = true,
                                                manualNote = "助教手動補簽到"
                                            )
                                            dao.insertRecord(record)
                                        }
                                    }
                                ) {
                                    Text("補簽到", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // 名單匯入 Dialog
    if (showRosterImportDialog) {
        AlertDialog(
            onDismissRequest = { showRosterImportDialog = false },
            title = { Text("批次預載選課/報名名單") },
            text = {
                Column {
                    Text(
                        text = "請輸入學生清單，支援一行一筆：\n格式：學號,姓名,班級,請假狀態 (請假狀態可為公假/病假/事假，或留空)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rosterImportText,
                        onValueChange = { rosterImportText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        placeholder = {
                            Text("11411188,林同學,資工三A,\n11411101,張小明,資工三A,\n11411102,李小華,資工三A,公假")
                        }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = parseRosterInput(rosterImportText)
                        if (parsed.isNotEmpty()) {
                            rosterMap = parsed
                            Toast.makeText(context, "成功載入 ${parsed.size} 筆名單！", Toast.LENGTH_SHORT).show()
                        }
                        showRosterImportDialog = false
                    }
                ) {
                    Text("確認匯入")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showRosterImportDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

private data class RollCallUiItem(
    val studentId: String,
    val name: String,
    val departmentClass: String,
    val isPresent: Boolean,
    val leaveStatus: String,
    val isExternal: Boolean,
    val scannedAt: Long?
)

@Composable
private fun StatColumn(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
    }
}

/**
 * 解析名單字串
 */
private fun parseRosterInput(text: String): Map<String, RosterItem> {
    val map = mutableMapOf<String, RosterItem>()
    text.lines().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.isNotBlank()) {
            val parts = trimmed.split(",", "，", "\t")
            val id = parts.getOrNull(0)?.trim() ?: ""
            val name = parts.getOrNull(1)?.trim() ?: id
            val dept = parts.getOrNull(2)?.trim() ?: ""
            val leave = parts.getOrNull(3)?.trim() ?: ""
            if (id.isNotBlank()) {
                map[id] = RosterItem(id, name, dept, leave)
            }
        }
    }
    return map
}

/**
 * 依規範產出通訊軟體完整複製摘要
 */
private fun buildRollCallReport(
    session: String,
    roster: Map<String, RosterItem>,
    records: List<RollCallRecordEntity>
): String {
    val dateStr = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date())
    val scannedSet = records.map { it.studentId }.toSet()

    val total = roster.size
    val present = records.count { it.isPresent && !it.isExternal }
    val leave = roster.values.count { it.initialLeaveStatus.isNotBlank() && !scannedSet.contains(it.studentId) }
    val absent = roster.values.count { it.initialLeaveStatus.isBlank() && !scannedSet.contains(it.studentId) }
    val external = records.count { it.isExternal }

    val absentList = roster.values.filter { it.initialLeaveStatus.isBlank() && !scannedSet.contains(it.studentId) }
    val leaveList = roster.values.filter { it.initialLeaveStatus.isNotBlank() && !scannedSet.contains(it.studentId) }

    return buildString {
        appendLine("【$session 課程點名報告 - $dateStr】")
        appendLine("應到 $total 人 | 實到 $present 人 | 請假 $leave 人 | 未到 $absent 人 | 額外 $external 人 (旁聽/跨班)")
        appendLine()
        appendLine("未到人員 ($absent 人)：")
        if (absentList.isEmpty()) {
            appendLine("- 無")
        } else {
            absentList.forEach {
                val dept = if (it.departmentClass.isNotBlank()) " (${it.departmentClass})" else ""
                appendLine("- ${it.studentId} ${it.name}$dept")
            }
        }
        appendLine()
        appendLine("請假人員 ($leave 人)：")
        if (leaveList.isEmpty()) {
            appendLine("- 無")
        } else {
            leaveList.forEach {
                val reason = if (it.initialLeaveStatus.isNotBlank()) " (${it.initialLeaveStatus})" else ""
                val dept = if (it.departmentClass.isNotBlank()) " [${it.departmentClass}]" else ""
                appendLine("- ${it.studentId} ${it.name}$dept$reason")
            }
        }
    }
}
