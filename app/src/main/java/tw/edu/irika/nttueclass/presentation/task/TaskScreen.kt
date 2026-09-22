package tw.edu.irika.nttueclass.presentation.task

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import tw.edu.irika.nttueclass.data.remote.client.NttuHttpClient
import tw.edu.irika.nttueclass.data.repository.EclassRepository
import tw.edu.irika.nttueclass.domain.model.TaskItem
import tw.edu.irika.nttueclass.domain.model.TaskStatus
import tw.edu.irika.nttueclass.domain.model.TaskType
import tw.edu.irika.nttueclass.ui.theme.StatusSuccess
import tw.edu.irika.nttueclass.ui.theme.StatusUrgent
import tw.edu.irika.nttueclass.ui.theme.StatusWarning

@Composable
fun TaskScreen(
    repository: EclassRepository,
    isLoggedIn: Boolean,
    onOpenLogin: () -> Unit,
    onSync: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDark = !MaterialTheme.colorScheme.background.luminance().let { it > 0.5f }
    var selectedCategory by rememberSaveable { mutableIntStateOf(0) } // 0: 作業, 1: 測驗, 2: 問卷
    var selectedFilter by rememberSaveable { mutableIntStateOf(0) } // 0: 全部, 1: 待繳交, 2: 即快截止, 3: 已逾期, 4: 已完成
    var selectedTaskForDetail by remember { mutableStateOf<TaskItem?>(null) }

    // 從真實 Room 資料庫訂閱待辦與作業資料流
    val tasks by repository.getTasksStream().collectAsStateWithLifecycle(initialValue = emptyList())

    val assignmentTasks = remember(tasks) { tasks.filter { it.type == TaskType.ASSIGNMENT } }
    val quizTasks = remember(tasks) { tasks.filter { it.type == TaskType.QUIZ || it.type == TaskType.EXAM } }
    val surveyTasks = remember(tasks) { tasks.filter { it.type == TaskType.QUESTIONNAIRE } }

    val categoryTabs = listOf(
        "作業 (${assignmentTasks.size})",
        "測驗 (${quizTasks.size})",
        "問卷 (${surveyTasks.size})"
    )

    val currentCategoryTasks = when (selectedCategory) {
        0 -> assignmentTasks
        1 -> quizTasks
        2 -> surveyTasks
        else -> assignmentTasks
    }

    val pendingCount = currentCategoryTasks.count { !it.isSubmitted && it.status != TaskStatus.OVERDUE }
    val urgentCount = currentCategoryTasks.count { !it.isSubmitted && (it.status == TaskStatus.URGENT || it.status == TaskStatus.WARNING) }
    val overdueCount = currentCategoryTasks.count { !it.isSubmitted && it.status == TaskStatus.OVERDUE }
    val completedCount = currentCategoryTasks.count { it.isSubmitted }

    val filterOptions = listOf(
        "全部 (${currentCategoryTasks.size})",
        "待繳交 ($pendingCount)",
        "即將截止 ($urgentCount)",
        "已逾期 ($overdueCount)",
        "已完成 ($completedCount)"
    )

    val filteredTasks = currentCategoryTasks.filter { task ->
        when (selectedFilter) {
            1 -> !task.isSubmitted && task.status != TaskStatus.OVERDUE
            2 -> !task.isSubmitted && (task.status == TaskStatus.URGENT || task.status == TaskStatus.WARNING)
            3 -> !task.isSubmitted && task.status == TaskStatus.OVERDUE
            4 -> task.isSubmitted
            else -> true
        }
    }

    val categoryName = when (selectedCategory) {
        0 -> "作業"
        1 -> "測驗"
        2 -> "問卷"
        else -> "項目"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // 頂部大分類分頁：作業 / 測驗 / 問卷
        TabRow(
            selectedTabIndex = selectedCategory,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            categoryTabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedCategory == index,
                    onClick = {
                        selectedCategory = index
                        selectedFilter = 0
                    },
                    text = {
                        Text(
                            text = title,
                            fontWeight = if (selectedCategory == index) FontWeight.Bold else FontWeight.Normal,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                )
            }
        }

        // 篩選 Chip 橫列
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filterOptions.size) { index ->
                val isSelected = selectedFilter == index
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedFilter = index },
                    label = {
                        Text(
                            text = filterOptions[index],
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

        Spacer(modifier = Modifier.height(6.dp))

        if (filteredTasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (currentCategoryTasks.isEmpty()) Icons.Default.EventBusy else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (currentCategoryTasks.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else StatusSuccess,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (currentCategoryTasks.isEmpty()) "目前尚無$categoryName" else "太棒了！本分類目前沒有待辦事項",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (currentCategoryTasks.isEmpty()) {
                            if (isLoggedIn) "已登入學生帳號，請點擊下方按鈕同步臺東大學最新${categoryName}。"
                            else "尚未登入學生帳號，請先登入以載入${categoryName}清單。"
                        } else {
                            "所選類別下目前無${categoryName}項目。"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    if (currentCategoryTasks.isEmpty()) {
                        Spacer(modifier = Modifier.height(20.dp))
                        if (isLoggedIn) {
                            Button(
                                onClick = onSync,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("立即同步", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = onOpenLogin,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("登入帳號載入", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredTasks, key = { it.id }) { task ->
                    TaskCard(
                        task = task,
                        isDark = isDark,
                        onClick = { selectedTaskForDetail = task }
                    )
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }

    // 任務詳細資訊對話框
    selectedTaskForDetail?.let { task ->
        TaskDetailDialog(
            task = task,
            isDark = isDark,
            onOpenWeb = {
                val path = when (task.type) {
                    TaskType.ASSIGNMENT -> "homeworkList"
                    TaskType.QUESTIONNAIRE -> "questionnaireList"
                    else -> "examList"
                }
                val targetUrl = when {
                    task.url.isNotBlank() -> task.url
                    task.courseId.isNotBlank() && !task.courseId.startsWith("c_") -> {
                        "${NttuHttpClient.BASE_URL}/course/$path/${task.courseId}"
                    }
                    else -> {
                        "${NttuHttpClient.BASE_URL}/course"
                    }
                }
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                    context.startActivity(intent)
                } catch (_: Exception) {
                    Toast.makeText(context, "無法開啟瀏覽器", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { selectedTaskForDetail = null }
        )
    }
}

@Composable
private fun TaskCard(
    task: TaskItem,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val statusColor = when (task.status) {
        TaskStatus.OVERDUE -> if (isDark) Color(0xFFEF4444) else MaterialTheme.colorScheme.error
        TaskStatus.URGENT -> if (isDark) Color(0xFFF87171) else StatusUrgent
        TaskStatus.WARNING -> if (isDark) Color(0xFFFBBF24) else StatusWarning
        TaskStatus.COMPLETED -> if (isDark) Color(0xFF34D399) else StatusSuccess
        TaskStatus.PENDING -> MaterialTheme.colorScheme.outline
    }

    val statusLabel = when (task.status) {
        TaskStatus.OVERDUE -> "已逾期"
        TaskStatus.URGENT -> if (task.remainingHours > 0) "倒數 ${task.remainingHours} 小時" else "即將截止"
        TaskStatus.WARNING -> if (task.remainingHours > 0) "剩 ${task.remainingHours / 24} 天截止" else "注意截止"
        TaskStatus.COMPLETED -> task.score?.let { "已評分 · $it" } ?: "已繳交"
        TaskStatus.PENDING -> "進行中"
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 頂部狀態列
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = when (task.type) {
                                TaskType.ASSIGNMENT -> "作業"
                                TaskType.QUESTIONNAIRE -> "問卷"
                                else -> "測驗"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (task.courseName.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = task.courseName,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
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
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 標題
            Text(
                text = task.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 截止時間資訊
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (task.dueDateTime.isNotBlank()) "截止時間：${task.dueDateTime}" else "無具體截止時間",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (task.isSubmitted) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = StatusSuccess,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "已完成",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = StatusSuccess
                        )
                    }
                }
            }
        }
    }
}

/**
 * 任務詳細資訊對話框
 */
@Composable
private fun TaskDetailDialog(
    task: TaskItem,
    isDark: Boolean,
    onOpenWeb: () -> Unit,
    onDismiss: () -> Unit
) {
    val typeName = when (task.type) {
        TaskType.ASSIGNMENT -> "作業"
        TaskType.QUESTIONNAIRE -> "問卷"
        else -> "測驗"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
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
                if (task.courseName.isNotBlank()) {
                    Text(
                        text = "所屬課程：${task.courseName}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

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
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(18.dp)
                    )
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
            Button(onClick = onOpenWeb) {
                Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("前往 eClass 繳交/查閱")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("關閉")
            }
        }
    )
}
