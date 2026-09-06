package tw.edu.irika.nttueclass.presentation.common

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import tw.edu.irika.nttueclass.data.remote.auth.AuthManager
import tw.edu.irika.nttueclass.data.repository.EclassRepository
import tw.edu.irika.nttueclass.presentation.auth.LoginDialog
import tw.edu.irika.nttueclass.presentation.course.CourseScreen
import tw.edu.irika.nttueclass.presentation.dashboard.DashboardScreen
import tw.edu.irika.nttueclass.presentation.donation.DonationScreen
import tw.edu.irika.nttueclass.presentation.navigation.Screen
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import tw.edu.irika.nttueclass.presentation.task.TaskScreen
import tw.edu.irika.nttueclass.presentation.timetable.TimetableScreen
import tw.edu.irika.nttueclass.ui.theme.NttuEclassTheme

@Composable
fun MainScaffold(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val authManager = remember { AuthManager(context) }
    val repository = remember { EclassRepository(context) }

    val systemInDark = isSystemInDarkTheme()
    var isDarkTheme by remember { mutableStateOf(systemInDark) }
    var showPassPreviewDialog by remember { mutableStateOf(false) }
    var showLoginDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var isLoggedIn by remember { mutableStateOf(authManager.secureStorage.isLoggedIn()) }
    var currentStudentId by remember { mutableStateOf(authManager.secureStorage.getStudentId()) }

    // Android 13+ 通知權限請求管理器
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // 初始化離線預載資料
    LaunchedEffect(Unit) {
        repository.seedInitialDataIfEmpty()
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val currentScreen = when (currentRoute) {
        Screen.Dashboard.route -> Screen.Dashboard
        Screen.Timetable.route -> Screen.Timetable
        Screen.Courses.route -> Screen.Courses
        Screen.Tasks.route -> Screen.Tasks
        Screen.Donation.route -> Screen.Donation
        else -> Screen.Dashboard
    }

    NttuEclassTheme(darkTheme = isDarkTheme) {
        Scaffold(
            topBar = {
                AppTopBar(
                    currentScreen = currentScreen,
                    isDarkTheme = isDarkTheme,
                    isLoggedIn = isLoggedIn,
                    onOpenLogin = {
                        if (isLoggedIn) {
                            showLogoutDialog = true
                        } else {
                            showLoginDialog = true
                        }
                    },
                    onToggleTheme = { isDarkTheme = !isDarkTheme },
                    onOpenDonation = {
                        navController.navigate(Screen.Donation.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenPass = { showPassPreviewDialog = true }
                )
            },
            bottomBar = {
                AppBottomBar(
                    currentRoute = currentRoute,
                    onNavigate = { screen ->
                        navController.navigate(screen.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            },
            modifier = modifier.fillMaxSize()
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Dashboard.route,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                composable(Screen.Dashboard.route) {
                    DashboardScreen(
                        onNavigateToTimetable = { navController.navigate(Screen.Timetable.route) },
                        onNavigateToTasks = { navController.navigate(Screen.Tasks.route) },
                        onNavigateToCourses = { navController.navigate(Screen.Courses.route) },
                        onOpenPass = { showPassPreviewDialog = true }
                    )
                }
                composable(Screen.Timetable.route) {
                    TimetableScreen()
                }
                composable(Screen.Courses.route) {
                    CourseScreen()
                }
                composable(Screen.Tasks.route) {
                    TaskScreen()
                }
                composable(Screen.Donation.route) {
                    DonationScreen()
                }
            }
        }
    }

    // 登入對話框
    if (showLoginDialog) {
        LoginDialog(
            authManager = authManager,
            onLoginSuccess = { studentId ->
                isLoggedIn = true
                currentStudentId = studentId
                showLoginDialog = false
                checkAndRequestNotificationPermission()
            },
            onDismiss = { showLoginDialog = false }
        )
    }

    // 帳號資訊與登出對話框
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("已登入學號：${currentStudentId ?: "同學"}") },
            text = { Text("目前處於已驗證狀態。是否要清除本機加密 Session 並登出？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        authManager.logout()
                        isLoggedIn = false
                        currentStudentId = null
                        showLogoutDialog = false
                    }
                ) {
                    Text("確認登出")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // NttuEPass 快速出示預覽
    if (showPassPreviewDialog) {
        AlertDialog(
            onDismissRequest = { showPassPreviewDialog = false },
            title = { Text("NttuEPass 校園通行條碼") },
            text = {
                Text(
                    if (isLoggedIn) "已綁定學號：${currentStudentId}\n\nNttuPass (Code 128 一維借書條碼：${currentStudentId}00) 與 NttuDataPass (ECC-256 二維碼) 將於 Phase 5 完整整合。"
                    else "請先完成學號登入，即可啟用校內借書條碼與特權憑據功能。"
                )
            },
            confirmButton = {
                TextButton(onClick = { showPassPreviewDialog = false }) {
                    Text("知道了")
                }
            }
        )
    }
}
