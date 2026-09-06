package tw.edu.irika.nttueclass.presentation.common

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
    var showLoginDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showAdminPinDialog by remember { mutableStateOf(false) }
    var adminPinInput by remember { mutableStateOf("") }
    var adminPinError by remember { mutableStateOf(false) }
    var versionTapCount by remember { mutableIntStateOf(0) }
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
        Screen.Pass.route -> Screen.Pass
        Screen.Verifier.route -> Screen.Verifier
        else -> Screen.Dashboard
    }

    NttuEclassTheme(darkTheme = isDarkTheme) {
        Scaffold(
            topBar = {
                if (currentScreen != Screen.Pass && currentScreen != Screen.Verifier) {
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
                        onOpenPass = { navController.navigate(Screen.Pass.route) },
                        onOpenAbout = {
                            versionTapCount = 0
                            showAboutDialog = true
                        }
                    )
                }
            },
            bottomBar = {
                if (currentScreen != Screen.Pass && currentScreen != Screen.Verifier) {
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
                }
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
                        onOpenPass = { navController.navigate(Screen.Pass.route) }
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
                composable(Screen.Pass.route) {
                    tw.edu.irika.nttueclass.presentation.pass.NttuEPassScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable(Screen.Verifier.route) {
                    tw.edu.irika.nttueclass.presentation.pass.verifier.HiddenVerifierScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
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

    // 關於與版本資訊對話框 (包含連續點擊 7 次喚醒隱藏核銷 Easter egg)
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("關於 NttuEclass 網路學園") },
            text = {
                Column {
                    Text(
                        text = "專為國立臺東大學師生打造之次世代學園助理，提供課表提醒、作業追蹤、NttuEPass 校園條碼與雙主題體驗。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "版本號：1.0.0 (Build 36)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            versionTapCount++
                            if (versionTapCount in 3..6) {
                                Toast.makeText(
                                    context,
                                    "再連續點擊 ${7 - versionTapCount} 次以解鎖管理核銷驗證台",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else if (versionTapCount >= 7) {
                                versionTapCount = 0
                                showAboutDialog = false
                                showAdminPinDialog = true
                                adminPinInput = ""
                                adminPinError = false
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "© 2026 irika. All rights reserved.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("關閉")
                }
            }
        )
    }

    // 管理員 PIN 碼驗證對話框
    if (showAdminPinDialog) {
        AlertDialog(
            onDismissRequest = { showAdminPinDialog = false },
            title = { Text("管理員身分驗證") },
            text = {
                Column {
                    Text(
                        text = "請輸入管理 PIN 碼以啟動 NttuDataPass 隱藏核銷驗證台：",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = adminPinInput,
                        onValueChange = {
                            adminPinInput = it
                            adminPinError = false
                        },
                        label = { Text("管理 PIN 碼") },
                        placeholder = { Text("例如 0174 或 0000") },
                        isError = adminPinError,
                        supportingText = {
                            if (adminPinError) {
                                Text("PIN 碼驗證失敗，請重新輸入 (預設: 0174 或 0000)")
                            }
                        },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = adminPinInput.trim()
                        if (trimmed == "0174" || trimmed == "0000") {
                            showAdminPinDialog = false
                            navController.navigate(Screen.Verifier.route)
                        } else {
                            adminPinError = true
                        }
                    }
                ) {
                    Text("驗證並進入")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdminPinDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}
