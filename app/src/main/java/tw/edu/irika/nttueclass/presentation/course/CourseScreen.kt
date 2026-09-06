package tw.edu.irika.nttueclass.presentation.course

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tw.edu.irika.nttueclass.data.remote.client.NttuHttpClient
import tw.edu.irika.nttueclass.data.repository.EclassRepository
import tw.edu.irika.nttueclass.domain.model.AcademicTermHelper
import tw.edu.irika.nttueclass.domain.model.Announcement
import tw.edu.irika.nttueclass.domain.model.Course
import tw.edu.irika.nttueclass.domain.model.StandardPeriods
import tw.edu.irika.nttueclass.domain.model.TaskItem
import tw.edu.irika.nttueclass.domain.model.TimetableSlot

@Composable
fun CourseScreen(
    repository: EclassRepository,
    isLoggedIn: Boolean,
    onOpenLogin: () -> Unit,
    onSync: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: 本學期課程, 1: 最新公告

    // 從真實 Room 資料庫訂閱所有相關資料流
    val courses by repository.getCoursesStream().collectAsState(initial = emptyList())
    val announcements by repository.getAnnouncementsStream().collectAsState(initial = emptyList())
    val tasks by repository.getTasksStream().collectAsState(initial = emptyList())
    val timetableSlots by repository.getTimetableStream().collectAsState(initial = emptyList())

    // 詳情彈窗狀態
    var selectedCourseForDetail by remember { mutableStateOf<Course?>(null) }
    var selectedAnnouncementForDetail by remember { mutableStateOf<Announcement?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // 搜尋框
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("搜尋課程名稱、教師或公告內容") },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Search, contentDescription = null)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            ),
            singleLine = true
        )

        // 分頁標籤 (本學期課程 / 全部公告)
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("本學期課程 (${courses.size})") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("課程公告 (${announcements.size})") }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (selectedTab == 0) {
            val filteredCourses = courses.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        it.instructor.contains(searchQuery, ignoreCase = true) ||
                        it.classroom.contains(searchQuery, ignoreCase = true)
            }

            if (filteredCourses.isEmpty()) {
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
                            imageVector = Icons.Default.Class,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "查無符合條件之課程" else "目前尚無課程資料",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "請嘗試更換關鍵字重新搜尋。"
                            else if (isLoggedIn) "已登入學生帳號，請點擊下方按鈕同步本學期課程。"
                            else "尚未登入學生帳號，請先登入以載入本學期選修課程。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        if (searchQuery.isBlank()) {
                            Spacer(modifier = Modifier.height(20.dp))
                            if (isLoggedIn) {
                                Button(
                                    onClick = onSync,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("立即同步課程", fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Button(
                                    onClick = onOpenLogin,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("登入帳號載入課程", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredCourses) { course ->
                        CourseCard(
                            course = course,
                            onClick = { selectedCourseForDetail = course }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        } else {
            val filteredAnnouncements = announcements.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                        it.courseName.contains(searchQuery, ignoreCase = true) ||
                        it.author.contains(searchQuery, ignoreCase = true)
            }

            if (filteredAnnouncements.isEmpty()) {
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
                            imageVector = Icons.Default.Campaign,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "查無符合條件之公告" else "目前暫無最新公告",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "請嘗試更換關鍵字重新搜尋。"
                            else if (isLoggedIn) "已登入學生帳號，請點擊下方按鈕拉取最新公告。"
                            else "尚未登入學生帳號，請先登入以載入最新課程公告。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        if (searchQuery.isBlank()) {
                            Spacer(modifier = Modifier.height(20.dp))
                            if (isLoggedIn) {
                                Button(
                                    onClick = onSync,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("立即同步公告", fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Button(
                                    onClick = onOpenLogin,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("登入帳號載入公告", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredAnnouncements) { announcement ->
                        AnnouncementCard(
                            announcement = announcement,
                            onClick = { selectedAnnouncementForDetail = announcement }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }

    // 課程詳細資訊對話框
    selectedCourseForDetail?.let { course ->
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

        CourseDetailDialog(
            course = course,
            slots = courseSlots,
            announcements = courseAnnouncements,
            tasks = courseTasks,
            onOpenWeb = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("${NttuHttpClient.BASE_URL}/app/course/"))
                context.startActivity(intent)
            },
            onSelectAnnouncement = { ann ->
                selectedCourseForDetail = null
                selectedAnnouncementForDetail = ann
            },
            onDismiss = { selectedCourseForDetail = null }
        )
    }

    // 公告詳細資訊對話框
    selectedAnnouncementForDetail?.let { announcement ->
        AnnouncementDetailDialog(
            announcement = announcement,
            onOpenWeb = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("${NttuHttpClient.BASE_URL}/app/bulletin/"))
                context.startActivity(intent)
            },
            onDismiss = { selectedAnnouncementForDetail = null }
        )
    }
}

@Composable
private fun CourseCard(
    course: Course,
    onClick: () -> Unit
) {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (course.credits > 0) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "${course.credits} 學分",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
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

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (course.unreadAnnouncementsCount > 0) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "${course.unreadAnnouncementsCount} 則新公告",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (course.pendingTasksCount > 0) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "${course.pendingTasksCount} 項待繳",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = course.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (course.instructor.isNotBlank()) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = course.instructor,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                }
                if (course.classroom.isNotBlank()) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = course.classroom,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun AnnouncementCard(
    announcement: Announcement,
    onClick: () -> Unit
) {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (announcement.isUnread) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    if (announcement.courseName.isNotBlank()) {
                        Text(
                            text = announcement.courseName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Text(
                    text = announcement.date,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = announcement.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = announcement.contentSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (announcement.hasAttachment) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "附帶附件檔案",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * 完整課程詳細資料對話框
 */
@Composable
private fun CourseDetailDialog(
    course: Course,
    slots: List<TimetableSlot>,
    announcements: List<Announcement>,
    tasks: List<TaskItem>,
    onOpenWeb: () -> Unit,
    onSelectAnnouncement: (Announcement) -> Unit,
    onDismiss: () -> Unit
) {
    val weekdayNames = listOf("", "週一", "週二", "週三", "週四", "週五", "週六", "週日")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = course.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                val metaParts = mutableListOf<String>()
                if (course.credits > 0) metaParts.add("${course.credits} 學分")
                if (course.code.isNotBlank()) metaParts.add(course.code)
                if (course.semester.isNotBlank()) metaParts.add(course.semester)
                if (metaParts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = metaParts.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 基本資訊列
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (course.instructor.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("授課教師：${course.instructor}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (course.classroom.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("上課教室：${course.classroom}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                HorizontalDivider()

                // 排課節次與時段
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("每週上課時段", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    if (slots.isEmpty()) {
                        Text("尚未排定課表或未在課表登記節次", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        slots.forEach { slot ->
                            val period = StandardPeriods.getByPeriodNumber(slot.periodNumber)
                            val dayLabel = weekdayNames.getOrElse(slot.dayOfWeek) { "週${slot.dayOfWeek}" }
                            val timeInfo = if (period != null) "第 ${period.periodCode} 節 (${period.startTime} ~ ${period.endTime})" else "第 ${slot.periodNumber} 節"
                            val roomInfo = if (slot.classroom.isNotBlank() && slot.classroom != course.classroom) " · ${slot.classroom}" else ""
                            Text(
                                text = "• $dayLabel $timeInfo$roomInfo",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                HorizontalDivider()

                // 待繳作業與測驗
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.Assignment, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("課程作業與測驗 (${tasks.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    if (tasks.isEmpty()) {
                        Text("目前無待繳作業或已全數繳交", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        tasks.forEach { task ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(task.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("截止：${task.dueDateTime}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(
                                        text = if (task.isSubmitted) "已繳" else "待繳",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (task.isSubmitted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                // 課程公告
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Campaign, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("課程最新公告 (${announcements.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    if (announcements.isEmpty()) {
                        Text("本門課程目前無公告", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        announcements.take(3).forEach { ann ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectAnnouncement(ann) }
                                    .padding(vertical = 3.dp)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(ann.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                        Text(ann.date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (ann.contentSummary.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(ann.contentSummary, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onOpenWeb) {
                Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("開啟 eClass 頁面")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("關閉")
            }
        }
    )
}

/**
 * 完整公告詳細資料對話框
 */
@Composable
private fun AnnouncementDetailDialog(
    announcement: Announcement,
    onOpenWeb: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = announcement.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (announcement.courseName.isNotBlank()) {
                        Text(
                            text = announcement.courseName,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = announcement.date,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (announcement.author.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("發布者：${announcement.author}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                HorizontalDivider()

                Text(
                    text = announcement.contentSummary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (announcement.hasAttachment) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AttachFile, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("本則公告含有附件檔案，請點擊下方按鈕至學校系統下載", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onOpenWeb) {
                Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("開啟 eClass 公告")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("關閉")
            }
        }
    )
}
