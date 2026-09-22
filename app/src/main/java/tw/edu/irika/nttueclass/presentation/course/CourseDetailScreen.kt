package tw.edu.irika.nttueclass.presentation.course

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import tw.edu.irika.nttueclass.data.remote.client.CourseMaterialDownloader
import tw.edu.irika.nttueclass.data.remote.client.NttuHttpClient
import tw.edu.irika.nttueclass.data.repository.EclassRepository
import tw.edu.irika.nttueclass.domain.model.Announcement
import tw.edu.irika.nttueclass.domain.model.Course
import tw.edu.irika.nttueclass.domain.model.CourseMaterial
import tw.edu.irika.nttueclass.domain.model.MaterialDownloadStatus
import tw.edu.irika.nttueclass.domain.model.MaterialType
import tw.edu.irika.nttueclass.domain.model.StandardPeriods
import tw.edu.irika.nttueclass.domain.model.TaskItem
import tw.edu.irika.nttueclass.domain.model.TaskStatus
import tw.edu.irika.nttueclass.domain.model.TaskType
import tw.edu.irika.nttueclass.domain.model.TimetableSlot
import tw.edu.irika.nttueclass.domain.util.ClassroomHelper
import tw.edu.irika.nttueclass.ui.theme.StatusUrgent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailScreen(
    courseId: String,
    repository: EclassRepository,
    isLoggedIn: Boolean,
    onNavigateBack: () -> Unit,
    onOpenLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 訂閱課程清單，並依 courseId 找到當前課程
    val courses by repository.getCoursesStream().collectAsStateWithLifecycle(initialValue = emptyList())
    val course = remember(courses, courseId) {
        courses.firstOrNull { it.id == courseId } ?: Course(
            id = courseId,
            code = "",
            name = "課程專區",
            instructor = "",
            classroom = "",
            credits = 0,
            semester = ""
        )
    }

    // 訂閱教材資料流
    val materials by repository.getMaterialsStream(courseId).collectAsStateWithLifecycle(initialValue = emptyList())
    val announcements by repository.getAnnouncementsStream().collectAsStateWithLifecycle(initialValue = emptyList())
    val tasks by repository.getTasksStream().collectAsStateWithLifecycle(initialValue = emptyList())
    val timetableSlots by repository.getTimetableStream().collectAsStateWithLifecycle(initialValue = emptyList())

    // 篩選與當前課程相關的公告、作業與課表節次
    val courseAnnouncements = remember(announcements, course) {
        announcements.filter { it.courseName.contains(course.name) || course.name.contains(it.courseName) || it.courseId == course.id }
    }
    val courseTasks = remember(tasks, course) {
        tasks.filter { it.courseName.contains(course.name) || course.name.contains(it.courseName) || it.courseId == course.id }
    }
    val courseSlots = remember(timetableSlots, course) {
        timetableSlots.filter { it.courseName.contains(course.name) || course.name.contains(it.courseName) }
            .sortedWith(compareBy({ it.dayOfWeek }, { it.periodNumber }))
    }

    // 狀態管理
    var selectedTab by remember { mutableIntStateOf(0) } // 0: 教材專區, 1: 課程公告, 2: 待繳作業, 3: 課程資訊
    var searchQuery by remember { mutableStateOf("") }
    var selectedTypeFilter by remember { mutableStateOf<MaterialType?>(null) }
    var showOnlyDownloaded by remember { mutableStateOf(false) }
    var isSyncingMaterials by remember { mutableStateOf(false) }
    var isSyncingTasks by remember { mutableStateOf(false) }
    var showAddMaterialDialog by remember { mutableStateOf(false) }
    var selectedAnnouncementForDetail by remember { mutableStateOf<Announcement?>(null) }
    var selectedTaskForDetail by remember { mutableStateOf<TaskItem?>(null) }

    // 即時下載進度暫存 (materialId -> progress 0f..1f)
    val downloadProgressMap = remember { mutableStateMapOf<String, Float>() }

    // 進入時若已登入且本地無教材，自動發起一次背景同步
    LaunchedEffect(courseId, isLoggedIn) {
        if (isLoggedIn && materials.isEmpty()) {
            isSyncingMaterials = true
            try {
                repository.syncCourseMaterials(courseId)
            } finally {
                isSyncingMaterials = false
            }
        }
    }

    val triggerSyncMaterials: () -> Unit = {
        if (isLoggedIn && !isSyncingMaterials) {
            isSyncingMaterials = true
            scope.launch {
                val res = repository.syncCourseMaterials(courseId)
                isSyncingMaterials = false
                if (res.isSuccess) {
                    Toast.makeText(context, "教材已同步最新資料", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "同步失敗：${res.exceptionOrNull()?.message ?: "連線異常"}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val triggerSyncTasks: () -> Unit = {
        if (isLoggedIn && !isSyncingTasks) {
            isSyncingTasks = true
            scope.launch {
                val res = repository.syncCourseTasks(course.id, course.name)
                isSyncingTasks = false
                if (res.isSuccess) {
                    Toast.makeText(context, "已同步該門課最新作業、測驗與問卷", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "同步失敗：${res.exceptionOrNull()?.message ?: "連線異常"}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = course.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (course.instructor.isNotBlank()) {
                            Text(
                                text = "授課教師：${course.instructor}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回課程列表"
                        )
                    }
                },
                actions = {
                    val isSyncingCurrent = if (selectedTab == 2) isSyncingTasks else isSyncingMaterials
                    IconButton(
                        onClick = {
                            if (selectedTab == 2) triggerSyncTasks()
                            else triggerSyncMaterials()
                        },
                        enabled = !isSyncingCurrent
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = if (selectedTab == 2) "同步作業" else "同步教材",
                            tint = if (isSyncingCurrent) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = {
                            try {
                                val url = "${NttuHttpClient.BASE_URL}/course/${course.id.removePrefix("c_")}"
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                Toast.makeText(context, "無法開啟瀏覽器", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInBrowser,
                            contentDescription = "開啟網頁版 eClass"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            if (selectedTab == 0 && isLoggedIn) {
                FloatingActionButton(
                    onClick = { showAddMaterialDialog = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "新增本機教材或筆記")
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 頂部課程概覽卡片
            CourseHeaderCard(
                course = course,
                materialsCount = materials.size,
                downloadedCount = materials.count { it.isFilePresentOnDisk },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // 分頁標籤
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("課程教材 (${materials.size})", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("公告 (${courseAnnouncements.size})", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("作業 (${courseTasks.size})", fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal) }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("資訊", fontWeight = if (selectedTab == 3) FontWeight.Bold else FontWeight.Normal) }
                )
            }

            if (isSyncingMaterials) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            when (selectedTab) {
                0 -> {
                    // 教材分頁內容
                    CourseMaterialsTabContent(
                        materials = materials,
                        searchQuery = searchQuery,
                        onSearchChange = { searchQuery = it },
                        selectedTypeFilter = selectedTypeFilter,
                        onTypeFilterSelect = { selectedTypeFilter = it },
                        showOnlyDownloaded = showOnlyDownloaded,
                        onToggleOnlyDownloaded = { showOnlyDownloaded = !showOnlyDownloaded },
                        downloadProgressMap = downloadProgressMap,
                        onDownloadMaterial = { material ->
                            scope.launch {
                                downloadProgressMap[material.id] = 0.05f
                                val res = repository.downloadMaterial(material) { p ->
                                    downloadProgressMap[material.id] = p
                                }
                                downloadProgressMap.remove(material.id)
                                if (res.isSuccess) {
                                    Toast.makeText(context, "「${material.title}」下載完成", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "下載失敗：${res.exceptionOrNull()?.localizedMessage ?: "請稍後重試"}", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        onOpenMaterial = { material ->
                            CourseMaterialDownloader.openFile(context, material)
                        },
                        onDeleteLocalFile = { material ->
                            scope.launch {
                                repository.deleteDownloadedMaterial(material)
                                Toast.makeText(context, "已刪除「${material.title}」本機下載檔案", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onSync = triggerSyncMaterials,
                        onOpenLogin = onOpenLogin,
                        isLoggedIn = isLoggedIn
                    )
                }
                1 -> {
                    // 課程公告分頁
                    CourseAnnouncementsTabContent(
                        announcements = courseAnnouncements,
                        onSelectAnnouncement = { selectedAnnouncementForDetail = it }
                    )
                }
                2 -> {
                    // 課程作業分頁
                    CourseTasksTabContent(
                        tasks = courseTasks,
                        onSelectTask = { selectedTaskForDetail = it }
                    )
                }
                3 -> {
                    // 課程詳細資訊分頁
                    CourseInfoTabContent(
                        course = course,
                        slots = courseSlots
                    )
                }
            }
        }
    }

    // 公告詳情對話框
    selectedAnnouncementForDetail?.let { announcement ->
        AnnouncementDetailDialog(
            announcement = announcement,
            onOpenWeb = {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("${NttuHttpClient.BASE_URL}/bulletin"))
                    context.startActivity(intent)
                } catch (_: Exception) {
                    Toast.makeText(context, "無法開啟瀏覽器", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { selectedAnnouncementForDetail = null }
        )
    }

    // 作業詳情對話框
    selectedTaskForDetail?.let { task ->
        val typeName = if (task.type == TaskType.ASSIGNMENT) "作業" else "測驗"
        AlertDialog(
            onDismissRequest = { selectedTaskForDetail = null },
            title = {
                Column {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = typeName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "所屬課程：${course.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (task.dueDateTime.isNotBlank()) "截止時間：${task.dueDateTime}" else "無具體截止期限",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val statusText = when {
                            task.isSubmitted -> "已繳交"
                            task.status == TaskStatus.OVERDUE -> "未繳交 (已逾期)"
                            else -> "未繳交"
                        }
                        val statusColor = when {
                            task.isSubmitted -> Color(0xFF2E7D32)
                            task.status == TaskStatus.OVERDUE -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.error
                        }
                        val statusIcon = when {
                            task.isSubmitted -> Icons.Default.CheckCircle
                            task.status == TaskStatus.OVERDUE -> Icons.Default.EventBusy
                            else -> Icons.AutoMirrored.Filled.Assignment
                        }
                        Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "繳交狀態：$statusText",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }
                    if (task.score != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Quiz, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "評定成績：${task.score}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val path = when (task.type) {
                            TaskType.ASSIGNMENT -> "homeworkList"
                            TaskType.QUESTIONNAIRE -> "questionnaireList"
                            else -> "examList"
                        }
                        val targetUrl = when {
                            task.url.isNotBlank() -> task.url
                            course.id.isNotBlank() && !course.id.startsWith("c_") -> "${NttuHttpClient.BASE_URL}/course/$path/${course.id}"
                            else -> "${NttuHttpClient.BASE_URL}/course"
                        }
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            Toast.makeText(context, "無法開啟瀏覽器", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("前往 eClass 繳交/查閱")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedTaskForDetail = null }) {
                    Text("關閉")
                }
            }
        )
    }

    // 手動新增本機筆記/教材對話框
    if (showAddMaterialDialog) {
        AddCustomMaterialDialog(
            courseId = course.id,
            onDismiss = { showAddMaterialDialog = false },
            onSave = { newMaterial ->
                scope.launch {
                    repository.addCustomMaterial(newMaterial)
                    Toast.makeText(context, "已新增教材項目", Toast.LENGTH_SHORT).show()
                }
                showAddMaterialDialog = false
            }
        )
    }
}

/**
 * 頂部課程概覽卡片
 */
@Composable
private fun CourseHeaderCard(
    course: Course,
    materialsCount: Int,
    downloadedCount: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (course.credits > 0) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "${course.credits} 學分",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    if (course.code.isNotBlank()) {
                        Text(
                            text = course.code,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (course.semester.isNotBlank()) {
                    Text(
                        text = course.semester,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = course.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (course.instructor.isNotBlank()) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = course.instructor,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    if (course.classroom.isNotBlank()) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = course.classroom,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // 下載狀況標籤
                Surface(
                    color = if (downloadedCount > 0) Color(0xFF2E7D32).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "已下載 $downloadedCount / $materialsCount 份",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (downloadedCount > 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

/**
 * 核心教材專區清單內容 (依章節分組 + 搜尋 + 類型篩選)
 */
@Composable
private fun CourseMaterialsTabContent(
    materials: List<CourseMaterial>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedTypeFilter: MaterialType?,
    onTypeFilterSelect: (MaterialType?) -> Unit,
    showOnlyDownloaded: Boolean,
    onToggleOnlyDownloaded: () -> Unit,
    downloadProgressMap: Map<String, Float>,
    onDownloadMaterial: (CourseMaterial) -> Unit,
    onOpenMaterial: (CourseMaterial) -> Unit,
    onDeleteLocalFile: (CourseMaterial) -> Unit,
    onSync: () -> Unit,
    onOpenLogin: () -> Unit,
    isLoggedIn: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // 搜尋欄位
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("搜尋教材標題、章節或副檔名") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "清除搜尋")
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )

        // 類型篩選 Chip (橫向捲動)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedTypeFilter == null && !showOnlyDownloaded,
                    onClick = {
                        onTypeFilterSelect(null)
                        if (showOnlyDownloaded) onToggleOnlyDownloaded()
                    },
                    label = { Text("全部") }
                )
            }
            item {
                FilterChip(
                    selected = showOnlyDownloaded,
                    onClick = onToggleOnlyDownloaded,
                    leadingIcon = {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    label = { Text("已下載") }
                )
            }
            items(listOf(MaterialType.PDF, MaterialType.SLIDES, MaterialType.DOCUMENT, MaterialType.ARCHIVE, MaterialType.VIDEO)) { type ->
                FilterChip(
                    selected = selectedTypeFilter == type,
                    onClick = {
                        onTypeFilterSelect(if (selectedTypeFilter == type) null else type)
                    },
                    label = { Text(type.displayName) }
                )
            }
        }

        // 篩選符合條件之教材清單
        val filteredMaterials = remember(materials, searchQuery, selectedTypeFilter, showOnlyDownloaded) {
            materials.filter { material ->
                val matchesSearch = searchQuery.isBlank() ||
                        material.title.contains(searchQuery, ignoreCase = true) ||
                        material.chapterName.contains(searchQuery, ignoreCase = true) ||
                        material.fileExtension.contains(searchQuery, ignoreCase = true)

                val matchesType = selectedTypeFilter == null || material.type == selectedTypeFilter
                val matchesDownloaded = !showOnlyDownloaded || material.isFilePresentOnDisk

                matchesSearch && matchesType && matchesDownloaded
            }
        }

        if (filteredMaterials.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) "查無符合條件之教材" else "目前尚無教材檔案",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) "請嘗試更換關鍵字或取消類型篩選。"
                        else if (isLoggedIn) "學校 eClass 伺服器可能尚未上傳本門課教材，或可點擊下方同步刷新。"
                        else "請先登入學號以載入該課程的最新教材與講義檔案。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    if (isLoggedIn) {
                        Button(
                            onClick = onSync,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("立即從 eClass 同步教材", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = onOpenLogin,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("登入帳號載入教材", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            // 依章節分組
            val grouped = remember(filteredMaterials) {
                filteredMaterials.groupBy { it.chapterName.ifBlank { "一般講義與課程資源" } }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                grouped.forEach { (chapterTitle, chapterMaterials) ->
                    item {
                        ChapterSectionHeader(
                            title = chapterTitle,
                            count = chapterMaterials.size
                        )
                    }
                    items(chapterMaterials, key = { it.id }) { material ->
                        MaterialItemCard(
                            material = material,
                            downloadProgress = downloadProgressMap[material.id],
                            onDownload = { onDownloadMaterial(material) },
                            onOpen = { onOpenMaterial(material) },
                            onDelete = { onDeleteLocalFile(material) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 章節標題
 */
@Composable
private fun ChapterSectionHeader(
    title: String,
    count: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "$count 項資源",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 個別教材卡片 (色彩識別、進度條、一鍵開啟)
 */
@Composable
private fun MaterialItemCard(
    material: CourseMaterial,
    downloadProgress: Float?,
    onDownload: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val isDownloaded = material.isFilePresentOnDisk
    val isDownloading = downloadProgress != null || material.downloadStatus == MaterialDownloadStatus.DOWNLOADING

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 依檔案類型呈現色彩圖示
                FileTypeBadge(type = material.type, extension = material.fileExtension)

                Spacer(modifier = Modifier.width(12.dp))

                // 教材資訊
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = material.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (material.formattedSize.isNotBlank()) {
                            Text(
                                text = material.formattedSize,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (material.uploadDate.isNotBlank()) {
                            Text(
                                text = "·  ${material.uploadDate}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isDownloaded) {
                            Surface(
                                color = Color(0xFF2E7D32).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "已下載",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }

                // 更多動作選單 (重新下載 / 刪除)
                if (isDownloaded) {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多操作")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("重新下載") },
                                onClick = {
                                    showMenu = false
                                    onDownload()
                                },
                                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("刪除本機檔案", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                            )
                        }
                    }
                }
            }

            // 下載中即時進度條
            if (isDownloading) {
                Spacer(modifier = Modifier.height(10.dp))
                val progress = downloadProgress ?: 0f
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("正在下載...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            } else {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isDownloaded) {
                        Button(
                            onClick = onOpen,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("開啟檔案", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        FilledTonalButton(
                            onClick = onDownload,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (material.formattedSize.isNotBlank()) "下載 (${material.formattedSize})" else "下載教材",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 檔案類型識別圖示與色彩徽章
 */
@Composable
private fun FileTypeBadge(
    type: MaterialType,
    extension: String,
    modifier: Modifier = Modifier
) {
    val (bgColor, iconColor, icon) = when (type) {
        MaterialType.PDF -> Triple(Color(0xFFFFEBEE), Color(0xFFD32F2F), Icons.Default.PictureAsPdf)
        MaterialType.SLIDES -> Triple(Color(0xFFFFF3E0), Color(0xFFE65100), Icons.Default.Slideshow)
        MaterialType.DOCUMENT -> Triple(Color(0xFFE3F2FD), Color(0xFF1976D2), Icons.Default.Description)
        MaterialType.SPREADSHEET -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), Icons.Default.TableChart)
        MaterialType.ARCHIVE -> Triple(Color(0xFFF3E5F5), Color(0xFF7B1FA2), Icons.Default.FolderZip)
        MaterialType.VIDEO -> Triple(Color(0xFFE0F2F1), Color(0xFF00796B), Icons.Default.VideoFile)
        MaterialType.AUDIO -> Triple(Color(0xFFEDE7F6), Color(0xFF512DA8), Icons.Default.AudioFile)
        MaterialType.IMAGE -> Triple(Color(0xFFFBE9E7), Color(0xFFD84315), Icons.Default.Image)
        MaterialType.LINK -> Triple(Color(0xFFE0F7FA), Color(0xFF00838F), Icons.Default.Link)
        MaterialType.OTHER -> Triple(Color(0xFFF5F5F5), Color(0xFF616161), Icons.Default.Description)
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.size(44.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * 課程公告分頁內容
 */
@Composable
private fun CourseAnnouncementsTabContent(
    announcements: List<Announcement>,
    onSelectAnnouncement: (Announcement) -> Unit
) {
    if (announcements.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Campaign,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "本門課程目前暫無公告",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(announcements) { ann ->
                Card(
                    onClick = { onSelectAnnouncement(ann) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = ann.date,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (ann.author.isNotBlank()) {
                                Text(
                                    text = ann.author,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = ann.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (ann.contentSummary.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = ann.contentSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 課程作業分頁內容
 */
@Composable
private fun CourseTasksTabContent(
    tasks: List<TaskItem>,
    onSelectTask: (TaskItem) -> Unit
) {
    var selectedTypeFilter by remember { mutableIntStateOf(0) } // 0: 全部, 1: 作業, 2: 測驗, 3: 問卷
    val assignmentCount = tasks.count { it.type == TaskType.ASSIGNMENT }
    val quizCount = tasks.count { it.type == TaskType.QUIZ || it.type == TaskType.EXAM }
    val surveyCount = tasks.count { it.type == TaskType.QUESTIONNAIRE }

    val typeFilters = listOf(
        "全部 (${tasks.size})",
        "作業 ($assignmentCount)",
        "測驗 ($quizCount)",
        "問卷 ($surveyCount)"
    )

    val filteredTasks = tasks.filter {
        when (selectedTypeFilter) {
            1 -> it.type == TaskType.ASSIGNMENT
            2 -> it.type == TaskType.QUIZ || it.type == TaskType.EXAM
            3 -> it.type == TaskType.QUESTIONNAIRE
            else -> true
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 類型切換 Chip (作業 / 測驗 / 問卷)
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(typeFilters.size) { index ->
                val isSelected = selectedTypeFilter == index
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedTypeFilter = index },
                    label = {
                        Text(
                            text = typeFilters[index],
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        if (filteredTasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Assignment,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "本分類目前無項目",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredTasks, key = { it.id }) { task ->
                    val (statusLabel, statusColor) = when {
                        task.isSubmitted -> Pair(task.score?.let { "已評分 · $it" } ?: "已繳交", Color(0xFF2E7D32))
                        task.status == TaskStatus.OVERDUE -> Pair("已逾期", MaterialTheme.colorScheme.error)
                        task.status == TaskStatus.URGENT -> Pair(if (task.remainingHours > 0) "倒數 ${task.remainingHours}h" else "即將截止", StatusUrgent)
                        task.status == TaskStatus.WARNING -> Pair("剩 ${task.remainingHours / 24} 天", MaterialTheme.colorScheme.primary)
                        else -> Pair("待繳交", MaterialTheme.colorScheme.outline)
                    }

                    Card(
                        onClick = { onSelectTask(task) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                val typeTag = when (task.type) {
                                    TaskType.ASSIGNMENT -> "作業"
                                    TaskType.QUESTIONNAIRE -> "問卷"
                                    else -> "測驗"
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = typeTag,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = task.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "截止期限：${task.dueDateTime.ifBlank { "無明確期限" }}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                color = statusColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = statusLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = statusColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

/**
 * 課程詳細節次與教室資訊分頁
 */
@Composable
private fun CourseInfoTabContent(
    course: Course,
    slots: List<TimetableSlot>
) {
    val weekdayNames = listOf("", "週一", "週二", "週三", "週四", "週五", "週六", "週日")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("授課與學分資訊", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("• 課程名稱：${course.name}", style = MaterialTheme.typography.bodyMedium)
                if (course.code.isNotBlank()) Text("• 課程課號：${course.code}", style = MaterialTheme.typography.bodyMedium)
                if (course.credits > 0) Text("• 應得學分：${course.credits} 學分", style = MaterialTheme.typography.bodyMedium)
                if (course.instructor.isNotBlank()) Text("• 授課教師：${course.instructor}", style = MaterialTheme.typography.bodyMedium)
                if (course.classroom.isNotBlank()) {
                    val building = ClassroomHelper.getBuildingName(course.classroomCode)
                    val locDesc = if (building.isNotBlank()) "${course.classroom} ($building)" else course.classroom
                    Text("• 主要教室：$locDesc", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("每週上課時段與節次", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                if (slots.isEmpty()) {
                    Text("目前無課表登記時段", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    slots.forEach { slot ->
                        val period = StandardPeriods.getByPeriodNumber(slot.periodNumber)
                        val dayLabel = weekdayNames.getOrElse(slot.dayOfWeek) { "週${slot.dayOfWeek}" }
                        val timeInfo = if (period != null) "第 ${period.periodCode} 節 (${period.startTime} ~ ${period.endTime})" else "第 ${slot.periodNumber} 節"
                        val roomInfo = if (slot.classroom.isNotBlank() && slot.classroom != course.classroom) " · ${slot.classroom}" else ""
                        Text("• $dayLabel $timeInfo$roomInfo", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

/**
 * 手動新增自訂教材/筆記對話框
 */
@Composable
private fun AddCustomMaterialDialog(
    courseId: String,
    onDismiss: () -> Unit,
    onSave: (CourseMaterial) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var chapterName by remember { mutableStateOf("") }
    var downloadUrl by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(MaterialType.DOCUMENT) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增課程教材 / 筆記項目") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("教材標題 *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = chapterName,
                    onValueChange = { chapterName = it },
                    label = { Text("所屬章節 / 週次 (選填)") },
                    placeholder = { Text("例如：第 3 週講義、期中複習") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = downloadUrl,
                    onValueChange = { downloadUrl = it },
                    label = { Text("線上連結或下載網址 (選填)") },
                    placeholder = { Text("https://...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) return@Button
                    val ext = if (title.contains(".")) title.substringAfterLast(".") else "txt"
                    onSave(
                        CourseMaterial(
                            id = "custom_${System.currentTimeMillis()}",
                            courseId = courseId,
                            title = title.trim(),
                            chapterName = chapterName.trim().ifBlank { "自訂筆記與教材" },
                            downloadUrl = downloadUrl.trim(),
                            fileExtension = ext,
                            type = CourseMaterial.inferTypeFromExtension(ext),
                            uploadDate = "今天",
                            downloadStatus = MaterialDownloadStatus.NOT_DOWNLOADED
                        )
                    )
                },
                enabled = title.isNotBlank()
            ) {
                Text("新增")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
