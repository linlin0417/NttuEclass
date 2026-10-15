package tw.edu.irika.nttueclass.presentation.course.detail

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Quiz
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tw.edu.irika.nttueclass.data.remote.client.NttuHttpClient
import tw.edu.irika.nttueclass.domain.model.Course
import tw.edu.irika.nttueclass.domain.model.TaskItem
import tw.edu.irika.nttueclass.domain.model.TaskStatus
import tw.edu.irika.nttueclass.domain.model.TaskType
import tw.edu.irika.nttueclass.ui.theme.StatusUrgent

/**
 * 課程作業與測驗分頁內容
 */
@Composable
fun CourseTasksTabContent(
    tasks: List<TaskItem>,
    onSelectTask: (TaskItem) -> Unit,
    modifier: Modifier = Modifier
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

    Column(modifier = modifier.fillMaxSize()) {
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
 * 作業/測驗詳細對話框
 */
@Composable
fun TaskDetailDialog(
    task: TaskItem,
    course: Course,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val typeName = if (task.type == TaskType.ASSIGNMENT) "作業" else "測驗"

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
            TextButton(onClick = onDismiss) {
                Text("關閉")
            }
        }
    )
}
