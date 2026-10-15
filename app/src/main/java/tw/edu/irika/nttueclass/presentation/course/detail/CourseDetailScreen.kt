package tw.edu.irika.nttueclass.presentation.course.detail

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tw.edu.irika.nttueclass.data.remote.client.CourseMaterialDownloader
import tw.edu.irika.nttueclass.data.remote.client.NttuHttpClient
import tw.edu.irika.nttueclass.presentation.course.AnnouncementDetailDialog
import tw.edu.irika.nttueclass.presentation.course.CourseDetailUiState
import tw.edu.irika.nttueclass.presentation.course.CourseDetailViewModel

/**
 * 課程詳情主畫面 (包含教材、公告、作業、課程資訊分頁)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailScreen(
    courseId: String,
    viewModel: CourseDetailViewModel = hiltViewModel(),
    isLoggedIn: Boolean,
    onNavigateBack: () -> Unit,
    onOpenLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        is CourseDetailUiState.Loading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        is CourseDetailUiState.Error -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = state.message, color = MaterialTheme.colorScheme.error)
            }
        }
        is CourseDetailUiState.Success -> {
            val course = state.course
            val materials = state.materials
            val filteredMaterials = state.filteredMaterials
            val courseAnnouncements = state.courseAnnouncements
            val courseTasks = state.courseTasks
            val courseSlots = state.courseSlots
            val selectedTab = state.selectedTab
            val searchQuery = state.searchQuery
            val selectedTypeFilter = state.selectedTypeFilter
            val showOnlyDownloaded = state.showOnlyDownloaded
            val isSyncingMaterials = state.isSyncingMaterials
            val isSyncingTasks = state.isSyncingTasks
            val isSyncingAnnouncements = state.isSyncingAnnouncements
            val showAddMaterialDialog = state.showAddMaterialDialog
            val selectedAnnouncementForDetail = state.selectedAnnouncementForDetail
            val selectedTaskForDetail = state.selectedTaskForDetail
            val downloadProgressMap = state.downloadProgressMap

            LaunchedEffect(courseId, isLoggedIn) {
                if (isLoggedIn) {
                    if (materials.isEmpty()) {
                        viewModel.syncMaterials()
                    }
                    if (courseAnnouncements.isEmpty()) {
                        viewModel.syncAnnouncements()
                    }
                }
            }

            val triggerSyncMaterials: () -> Unit = {
                if (isLoggedIn && !isSyncingMaterials) {
                    viewModel.syncMaterials()
                }
            }

            val triggerSyncAnnouncements: () -> Unit = {
                if (isLoggedIn && !isSyncingAnnouncements) {
                    viewModel.syncAnnouncements()
                }
            }

            val triggerSyncTasks: () -> Unit = {
                if (isLoggedIn && !isSyncingTasks) {
                    viewModel.syncTasks(course.name) { res ->
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
                            val isSyncingCurrent = when (selectedTab) {
                                0 -> isSyncingMaterials
                                1 -> isSyncingAnnouncements
                                else -> isSyncingTasks
                            }
                            IconButton(
                                onClick = {
                                    when (selectedTab) {
                                        0 -> triggerSyncMaterials()
                                        1 -> triggerSyncAnnouncements()
                                        else -> triggerSyncTasks()
                                    }
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
                            onClick = { viewModel.setShowAddMaterialDialog(true) },
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
                            onClick = { viewModel.onTabSelect(0) },
                            text = { Text("課程教材 (${materials.size})", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { viewModel.onTabSelect(1) },
                            text = { Text("公告 (${courseAnnouncements.size})", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) }
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { viewModel.onTabSelect(2) },
                            text = { Text("作業 (${courseTasks.size})", fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal) }
                        )
                        Tab(
                            selected = selectedTab == 3,
                            onClick = { viewModel.onTabSelect(3) },
                            text = { Text("資訊", fontWeight = if (selectedTab == 3) FontWeight.Bold else FontWeight.Normal) }
                        )
                    }

                    if (isSyncingMaterials) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    when (selectedTab) {
                        0 -> {
                            CourseMaterialsTabContent(
                                filteredMaterials = filteredMaterials,
                                hasAnyMaterials = materials.isNotEmpty(),
                                searchQuery = searchQuery,
                                onSearchChange = { viewModel.onSearchChange(it) },
                                selectedTypeFilter = selectedTypeFilter,
                                onTypeFilterSelect = { viewModel.onTypeFilterSelect(it) },
                                showOnlyDownloaded = showOnlyDownloaded,
                                onToggleOnlyDownloaded = { viewModel.onToggleOnlyDownloaded() },
                                downloadProgressMap = downloadProgressMap,
                                onDownloadMaterial = { material ->
                                    viewModel.downloadMaterial(material) { res ->
                                        if (res.isSuccess) {
                                            Toast.makeText(context, "「${material.title}」下載完成", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "下載失敗：${res.exceptionOrNull()?.localizedMessage ?: "請稍後重試"}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                onOpenMaterial = { material ->
                                    CourseMaterialDownloader.getDownloadedFile(material).fold(
                                        onSuccess = { file ->
                                            try {
                                                val contentUri = FileProvider.getUriForFile(
                                                    context,
                                                    "${context.packageName}.fileprovider",
                                                    file
                                                )
                                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                                    setDataAndType(contentUri, material.mimeType)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                val chooser = Intent.createChooser(intent, "開啟教材：「${material.title}」").apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                context.startActivity(chooser)
                                            } catch (_: Exception) {
                                                Toast.makeText(context, "未找到可開啟此格式 (${material.fileExtension}) 的應用程式", Toast.LENGTH_LONG).show()
                                            }
                                        },
                                        onFailure = { error ->
                                            Toast.makeText(context, error.message ?: "無法開啟檔案", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                },
                                onDeleteLocalFile = { material ->
                                    viewModel.deleteDownloadedMaterial(material)
                                    Toast.makeText(context, "已刪除「${material.title}」本機下載檔案", Toast.LENGTH_SHORT).show()
                                },
                                onDeleteCustomMaterial = { material ->
                                    viewModel.deleteMaterial(material.id)
                                    Toast.makeText(context, "已刪除自訂項目「${material.title}」", Toast.LENGTH_SHORT).show()
                                },
                                onSync = triggerSyncMaterials,
                                onOpenLogin = onOpenLogin,
                                isLoggedIn = isLoggedIn
                            )
                        }
                        1 -> {
                            CourseAnnouncementsTabContent(
                                announcements = courseAnnouncements,
                                onSelectAnnouncement = { viewModel.selectAnnouncementForDetail(it) }
                            )
                        }
                        2 -> {
                            CourseTasksTabContent(
                                tasks = courseTasks,
                                onSelectTask = { viewModel.selectTaskForDetail(it) }
                            )
                        }
                        3 -> {
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
                    onDismiss = { viewModel.selectAnnouncementForDetail(null) }
                )
            }

            // 作業詳情對話框
            selectedTaskForDetail?.let { task ->
                TaskDetailDialog(
                    task = task,
                    course = course,
                    onDismiss = { viewModel.selectTaskForDetail(null) }
                )
            }

            // 手動新增本機筆記/教材對話框
            if (showAddMaterialDialog) {
                AddCustomMaterialDialog(
                    courseId = course.id,
                    onDismiss = { viewModel.setShowAddMaterialDialog(false) },
                    onSave = { newMaterial ->
                        viewModel.addCustomMaterial(newMaterial)
                        Toast.makeText(context, "已新增教材項目", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}
