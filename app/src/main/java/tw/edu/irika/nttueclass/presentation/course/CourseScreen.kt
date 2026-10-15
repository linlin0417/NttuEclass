package tw.edu.irika.nttueclass.presentation.course

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tw.edu.irika.nttueclass.data.remote.client.NttuHttpClient

/**
 * 課程主畫面 (包含本學期課程清單與最新公告分頁)
 */
@Composable
fun CourseScreen(
    viewModel: CourseViewModel = hiltViewModel(),
    isLoggedIn: Boolean,
    onOpenLogin: () -> Unit,
    onSync: () -> Unit,
    onNavigateToCourseDetail: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        is CourseUiState.Loading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        is CourseUiState.Error -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Class,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        is CourseUiState.Success -> {
            val courses = state.courses
            val filteredCourses = state.filteredCourses
            val announcements = state.announcements
            val filteredAnnouncements = state.filteredAnnouncements
            val tasks = state.tasks
            val timetableSlots = state.timetableSlots
            val searchQuery = state.searchQuery
            val selectedTab = state.selectedTab
            val showAddCourseDialog = state.showAddCourseDialog
            val selectedCourseForDetail = state.selectedCourseForDetail
            val selectedAnnouncementForDetail = state.selectedAnnouncementForDetail
            val selectedSemester = state.selectedSemester
            val availableSemesters = state.availableSemesters

            Box(modifier = modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    // 搜尋框
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.onSearchQueryChange(it) },
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
                            onClick = { viewModel.onTabSelect(0) },
                            text = { Text("本學期課程 (${courses.size})") }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { viewModel.onTabSelect(1) },
                            text = { Text("課程公告 (${announcements.size})") }
                        )
                    }

                    if (selectedTab == 0 && availableSemesters.size > 1) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(availableSemesters) { sem ->
                                FilterChip(
                                    selected = selectedSemester == sem,
                                    onClick = { viewModel.onSemesterSelect(sem) },
                                    label = { Text(sem) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (selectedTab == 0) {
                        CourseListTab(
                            filteredCourses = filteredCourses,
                            searchQuery = searchQuery,
                            isLoggedIn = isLoggedIn,
                            onCourseClick = { course ->
                                if (onNavigateToCourseDetail != null) {
                                    onNavigateToCourseDetail(course.id)
                                } else {
                                    viewModel.selectCourseForDetail(course)
                                }
                            },
                            onOpenMaterials = { course ->
                                if (onNavigateToCourseDetail != null) {
                                    onNavigateToCourseDetail(course.id)
                                } else {
                                    viewModel.selectCourseForDetail(course)
                                }
                            },
                            onAddCourseClick = { viewModel.showAddCourseDialog(true) },
                            onSync = onSync,
                            onOpenLogin = onOpenLogin
                        )
                    } else {
                        AnnouncementListTab(
                            filteredAnnouncements = filteredAnnouncements,
                            searchQuery = searchQuery,
                            isLoggedIn = isLoggedIn,
                            onSelectAnnouncement = { viewModel.selectAnnouncementForDetail(it) },
                            onSync = onSync,
                            onOpenLogin = onOpenLogin
                        )
                    }
                }

                // 浮動新增課程按鈕 (FAB)
                if (isLoggedIn && selectedTab == 0) {
                    FloatingActionButton(
                        onClick = { viewModel.showAddCourseDialog(true) },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(20.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "手動新增課程")
                    }
                }
            }

            // 課程詳細資訊對話框 (支援刪除)
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
                    onOpenMaterials = {
                        viewModel.selectCourseForDetail(null)
                        onNavigateToCourseDetail?.invoke(course.id)
                    },
                    onOpenWeb = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("${NttuHttpClient.BASE_URL}/course"))
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            Toast.makeText(context, "無法開啟瀏覽器", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onSelectAnnouncement = { ann ->
                        viewModel.selectCourseForDetail(null)
                        viewModel.selectAnnouncementForDetail(ann)
                    },
                    onDelete = {
                        viewModel.deleteCourse(course.id)
                        Toast.makeText(context, "已刪除「${course.name}」課程", Toast.LENGTH_SHORT).show()
                    },
                    onDismiss = { viewModel.selectCourseForDetail(null) }
                )
            }

            // 新增自訂課程對話框
            if (showAddCourseDialog) {
                AddCourseDialog(
                    onSave = { newCourse ->
                        viewModel.addCourse(newCourse)
                        Toast.makeText(context, "已新增「${newCourse.name}」課程", Toast.LENGTH_SHORT).show()
                    },
                    onDismiss = { viewModel.showAddCourseDialog(false) }
                )
            }

            // 公告詳細資訊對話框
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
        }
    }
}
