package tw.edu.irika.nttueclass.presentation.dashboard

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
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tw.edu.irika.nttueclass.data.repository.EclassRepository
import tw.edu.irika.nttueclass.domain.model.AcademicTermHelper
import tw.edu.irika.nttueclass.domain.model.Announcement
import tw.edu.irika.nttueclass.domain.model.StandardPeriods
import tw.edu.irika.nttueclass.domain.model.TaskItem
import tw.edu.irika.nttueclass.domain.model.TaskStatus
import tw.edu.irika.nttueclass.domain.model.TimetableSlot
import tw.edu.irika.nttueclass.ui.theme.StatusSuccess
import tw.edu.irika.nttueclass.ui.theme.StatusUrgent
import tw.edu.irika.nttueclass.ui.theme.StatusWarning
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun DashboardScreen(
    repository: EclassRepository,
    isLoggedIn: Boolean,
    currentStudentId: String?,
    onNavigateToTimetable: () -> Unit,
    onNavigateToTasks: () -> Unit,
    onNavigateToCourses: () -> Unit,
    onOpenPass: () -> Unit,
    onSync: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 訂閱真實資料流 (無任何寫死假資料)
    val timetableSlots by repository.getTimetableStream().collectAsState(initial = emptyList())
    val tasks by repository.getTasksStream().collectAsState(initial = emptyList())
    val announcements by repository.getAnnouncementsStream().collectAsState(initial = emptyList())

    // 計算真實當前日期、星期與學期
    val now = remember { LocalDate.now() }
    val todayWeekday = remember { now.dayOfWeek.value } // 1=Mon .. 7=Sun
    val weekdayNames = listOf("週一", "週二", "週三", "週四", "週五", "週六", "週日")
    val weekdayStr = weekdayNames.getOrElse(todayWeekday - 1) { "今日" }
    val semesterDisplay = remember { AcademicTermHelper.getCurrentSemesterDisplay(now) }
    val dateSubtitle = remember { "$semesterDisplay · 今日$weekdayStr · ${now.monthValue}月${now.dayOfMonth}日" }

    // 動態計算今日課程與下一堂課
    val todaySlots = remember(timetableSlots, todayWeekday) {
        timetableSlots.filter { it.dayOfWeek == todayWeekday }.sortedBy { it.periodNumber }
    }
    val currentLocalTime = remember { LocalTime.now() }
    val nextSlot = remember(todaySlots, currentLocalTime) {
        todaySlots.firstOrNull { slot ->
            val period = StandardPeriods.getByPeriodNumber(slot.periodNumber)
            if (period != null) {
                val parts = period.endTime.split(":")
                val endHour = parts.getOrNull(0)?.toIntOrNull() ?: 23
                val endMinute = parts.getOrNull(1)?.toIntOrNull() ?: 59
                val endTime = LocalTime.of(endHour, endMinute)
                currentLocalTime.isBefore(endTime)
            } else true
        }
    }

    // 動態計算最緊急待辦
    val urgentTask = remember(tasks) {
        tasks.filter { !it.isSubmitted }
            .sortedWith(
                compareBy(
                    { it.status != TaskStatus.URGENT },
                    { it.status != TaskStatus.WARNING },
                    { it.remainingHours }
                )
            )
            .firstOrNull()
    }

    // 動態取得最新前兩筆公告
    val latestNotices = remember(announcements) {
        announcements.take(2)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // 1. 歡迎與學期真實資訊
        item {
            HeaderSection(
                greeting = if (isLoggedIn && !currentStudentId.isNullOrBlank()) "${currentStudentId} 同學，你好！" else "同學，你好！",
                subtitle = dateSubtitle
            )
        }

        // 2. 下一堂課指引卡片 (動態計算真實課堂)
        item {
            NextClassCard(
                slot = nextSlot,
                hasClassesToday = todaySlots.isNotEmpty(),
                isScheduleEmpty = timetableSlots.isEmpty(),
                onClick = onNavigateToTimetable
            )
        }

        // 3. NttuEPass 校園通行快捷卡
        item {
            NttuPassBanner(onOpenPass = onOpenPass)
        }

        // 4. 即將截止作業/測驗預警 (真實待辦)
        item {
            UrgentTasksCard(
                urgentTask = urgentTask,
                hasTasks = tasks.isNotEmpty(),
                onClick = onNavigateToTasks
            )
        }

        // 5. 最新課程公告 (真實公告)
        item {
            LatestNoticesCard(
                notices = latestNotices,
                onClick = onNavigateToCourses
            )
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun HeaderSection(
    greeting: String,
    subtitle: String
) {
    Column {
        Text(
            text = greeting,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NextClassCard(
    slot: TimetableSlot?,
    hasClassesToday: Boolean,
    isScheduleEmpty: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            when {
                isScheduleEmpty -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "個人課表",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "尚未同步個人課表",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "登入學號並同步後，此處將即時引導下一堂課教室與教師資訊。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                !hasClassesToday -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "今日課堂",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "今日無課堂安排",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "享受校園生活或自主學習吧！點擊可檢視本週完整課表。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                slot == null -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "今日課堂",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "今日課程已全部結束",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "今日課堂皆已告一段落，點擊可查閱本週排課規劃。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    val period = StandardPeriods.getByPeriodNumber(slot.periodNumber)
                    val periodLabel = period?.periodCode ?: "${slot.periodNumber}"
                    val timeRange = if (period != null) "${period.startTime} ~ ${period.endTime}" else ""

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "下一堂課 · 第 $periodLabel 節",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        if (timeRange.isNotBlank()) {
                            Text(
                                text = timeRange,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = slot.courseName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = slot.classroom,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = slot.instructor,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NttuPassBanner(onOpenPass: () -> Unit) {
    OutlinedCard(
        onClick = onOpenPass,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "NttuEPass 校園通行碼",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "出示借書條碼與特權加密憑證",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun UrgentTasksCard(
    urgentTask: TaskItem?,
    hasTasks: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (urgentTask != null) Icons.Default.Warning else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (urgentTask != null) StatusUrgent else StatusSuccess,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "待辦作業預警",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "查看全部",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (!hasTasks) {
                Text(
                    text = "目前無待辦作業或測驗，點擊查看全部或同步學校學園資料。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (urgentTask == null) {
                Text(
                    text = "太棒了！所有作業與測驗皆已完成，目前無待繳項目。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = urgentTask.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${urgentTask.courseName} · ${urgentTask.dueDateTime}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        val statusColor = when (urgentTask.status) {
                            TaskStatus.URGENT -> StatusUrgent
                            TaskStatus.WARNING -> StatusWarning
                            else -> MaterialTheme.colorScheme.primary
                        }
                        val statusLabel = when (urgentTask.status) {
                            TaskStatus.URGENT -> "即將截止"
                            TaskStatus.WARNING -> "注意截止"
                            TaskStatus.COMPLETED -> "已完成"
                            TaskStatus.PENDING -> "待繳交"
                        }
                        Surface(
                            color = statusColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (urgentTask.remainingHours > 0) "剩 ${urgentTask.remainingHours} 小時" else statusLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = statusColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LatestNoticesCard(
    notices: List<Announcement>,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Campaign,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "最新課程公告",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "更多",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (notices.isEmpty()) {
                Text(
                    text = "目前暫無最新公告，登入同步後將在此展示學校學園最新消息。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                notices.forEachIndexed { index, notice ->
                    if (index > 0) Spacer(modifier = Modifier.height(8.dp))
                    NoticeItem(
                        course = notice.courseName,
                        title = notice.title,
                        date = notice.date
                    )
                }
            }
        }
    }
}

@Composable
private fun NoticeItem(course: String, title: String, date: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = course,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Text(
                text = date,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
