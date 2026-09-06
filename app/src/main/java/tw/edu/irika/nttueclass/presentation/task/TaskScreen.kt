package tw.edu.irika.nttueclass.presentation.task

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tw.edu.irika.nttueclass.domain.model.TaskItem
import tw.edu.irika.nttueclass.domain.model.TaskStatus
import tw.edu.irika.nttueclass.domain.model.TaskType
import tw.edu.irika.nttueclass.ui.theme.StatusSuccess
import tw.edu.irika.nttueclass.ui.theme.StatusUrgent
import tw.edu.irika.nttueclass.ui.theme.StatusWarning

@Composable
fun TaskScreen(
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    var selectedFilter by remember { mutableIntStateOf(0) } // 0: 全部, 1: 待繳交, 2: 即將截止, 3: 已完成

    val sampleTasks = remember {
        listOf(
            TaskItem(
                id = "t1",
                courseId = "c1",
                courseName = "演算法",
                title = "作業二：動態規劃背包問題實作",
                type = TaskType.ASSIGNMENT,
                dueDateTime = "2026/03/06 23:59",
                remainingHours = 8,
                status = TaskStatus.URGENT,
                isSubmitted = false
            ),
            TaskItem(
                id = "t2",
                courseId = "c7",
                courseName = "行動應用開發",
                title = "隨堂測驗：Compose 雙主題狀態管理",
                type = TaskType.QUIZ,
                dueDateTime = "2026/03/08 12:00",
                remainingHours = 44,
                status = TaskStatus.WARNING,
                isSubmitted = false
            ),
            TaskItem(
                id = "t3",
                courseId = "c2",
                courseName = "資料庫系統",
                title = "實習三：B+ Tree 索引與 SQL 優化",
                type = TaskType.ASSIGNMENT,
                dueDateTime = "2026/03/12 23:59",
                remainingHours = 140,
                status = TaskStatus.PENDING,
                isSubmitted = false
            ),
            TaskItem(
                id = "t4",
                courseId = "c6",
                courseName = "人工智慧概論",
                title = "作業一：卷積神經網路分類器 (CNN)",
                type = TaskType.ASSIGNMENT,
                dueDateTime = "2026/02/28 23:59",
                remainingHours = 0,
                status = TaskStatus.COMPLETED,
                score = "96 分",
                isSubmitted = true
            ),
            TaskItem(
                id = "t5",
                courseId = "c3",
                courseName = "計算機網路",
                title = "第一次小考：TCP 三向交握與滑動窗口",
                type = TaskType.QUIZ,
                dueDateTime = "2026/02/25 10:00",
                remainingHours = 0,
                status = TaskStatus.COMPLETED,
                score = "90 分",
                isSubmitted = true
            )
        )
    }

    val filterOptions = listOf("全部 (5)", "待繳交 (3)", "即將截止 (2)", "已完成 (2)")

    val filteredTasks = sampleTasks.filter { task ->
        when (selectedFilter) {
            1 -> !task.isSubmitted
            2 -> task.status == TaskStatus.URGENT || task.status == TaskStatus.WARNING
            3 -> task.isSubmitted
            else -> true
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // 篩選 Chip 橫列
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filterOptions.indices.toList()) { index ->
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

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(filteredTasks) { task ->
                TaskCard(task = task, isDark = isDark)
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun TaskCard(task: TaskItem, isDark: Boolean) {
    val statusColor = when (task.status) {
        TaskStatus.URGENT -> if (isDark) Color(0xFFF87171) else StatusUrgent
        TaskStatus.WARNING -> if (isDark) Color(0xFFFBBF24) else StatusWarning
        TaskStatus.COMPLETED -> if (isDark) Color(0xFF34D399) else StatusSuccess
        TaskStatus.PENDING -> MaterialTheme.colorScheme.outline
    }

    val statusLabel = when (task.status) {
        TaskStatus.URGENT -> "倒數 ${task.remainingHours} 小時"
        TaskStatus.WARNING -> "剩 ${task.remainingHours / 24} 天截止"
        TaskStatus.COMPLETED -> task.score?.let { "已評分 · $it" } ?: "已繳交"
        TaskStatus.PENDING -> "進行中"
    }

    Card(
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
                            text = if (task.type == TaskType.ASSIGNMENT) "作業" else "測驗",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = task.courseName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
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
                        text = "截止時間：${task.dueDateTime}",
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
