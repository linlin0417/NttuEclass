package tw.edu.irika.nttueclass.presentation.common

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import tw.edu.irika.nttueclass.BuildConfig
import tw.edu.irika.nttueclass.presentation.auth.AuthViewModel
import tw.edu.irika.nttueclass.presentation.auth.LoginDialog
import java.security.MessageDigest

/**
 * SHA-256 驗證合法管理員 PIN 碼雜湊
 * 預計算值對應合法的管理員授權 PIN
 */
val VALID_PIN_HASHES: Set<String> = setOf(
    "77f33cb3e69fcae2880e62588ea996ed1a89c06c1a9b6c70c0fdcf261df10f60",
    "9af15b336e6a9619928537df30b2e6a2376569fcf9d7e773eccede65606529a0"
)

/**
 * 驗證輸入的 PIN 碼是否符合預設雜湊值
 */
fun verifyPin(pin: String): Boolean {
    val md = MessageDigest.getInstance("SHA-256")
    val digest = md.digest(pin.trim().toByteArray(Charsets.UTF_8))
    val hash = digest.joinToString("") { "%02x".format(it) }
    return hash in VALID_PIN_HASHES
}

/**
 * 全域對話框宿主 (GlobalDialogHost)
 * 統一管理登入、登出、關於、管理員 PIN 驗證對話框
 */
@Composable
fun GlobalDialogHost(
    showLoginDialog: Boolean,
    showLogoutDialog: Boolean,
    showAboutDialog: Boolean,
    showAdminPinDialog: Boolean,
    currentStudentId: String?,
    displayVersionName: String,
    displayVersionCode: Long,
    authViewModel: AuthViewModel,
    onLoginSuccess: (String) -> Unit,
    onDismissLogin: () -> Unit,
    onLogoutConfirm: () -> Unit,
    onDismissLogout: () -> Unit,
    onDismissAbout: () -> Unit,
    onTriggerAdminPin: () -> Unit,
    onDismissAdminPin: () -> Unit,
    onAdminPinVerified: () -> Unit
) {
    if (showLoginDialog) {
        LoginDialog(
            authViewModel = authViewModel,
            onLoginSuccess = onLoginSuccess,
            onDismiss = onDismissLogin
        )
    }

    if (showLogoutDialog) {
        LogoutDialog(
            studentId = currentStudentId,
            onConfirm = onLogoutConfirm,
            onDismiss = onDismissLogout
        )
    }

    if (showAboutDialog) {
        AboutDialog(
            displayVersionName = displayVersionName,
            displayVersionCode = displayVersionCode,
            onDismiss = onDismissAbout,
            onTriggerAdminPin = onTriggerAdminPin
        )
    }

    if (showAdminPinDialog) {
        AdminPinDialog(
            onDismiss = onDismissAdminPin,
            onVerified = onAdminPinVerified
        )
    }
}

/**
 * 帳號資訊與登出確認對話框
 */
@Composable
fun LogoutDialog(
    studentId: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("已登入學號：${studentId ?: "同學"}") },
        text = { Text("目前處於已驗證狀態。登出將清除本機加密 Session 並抹除所有課表與課程快取以維護資訊安全，是否確認？") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("確認登出")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 關於與版本資訊對話框 (包含連續點擊 7 次喚醒管理核銷 Easter Egg)
 */
@Composable
fun AboutDialog(
    displayVersionName: String,
    displayVersionCode: Long,
    onDismiss: () -> Unit,
    onTriggerAdminPin: () -> Unit
) {
    val context = LocalContext.current
    var versionTapCount by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("關於 NttuEclass 網路學園") },
        text = {
            Column {
                Text(
                    text = "專為國立臺東大學師生打造之次世代學園助理，提供課表提醒、作業追蹤、NttuEPass 校園條碼與雙主題體驗。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "版本號：$displayVersionName (Build $displayVersionCode)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        if (BuildConfig.DEBUG) {
                            versionTapCount++
                            if (versionTapCount in 3..6) {
                                Toast.makeText(
                                    context,
                                    "再連續點擊 ${7 - versionTapCount} 次以解鎖管理核銷驗證台",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else if (versionTapCount >= 7) {
                                versionTapCount = 0
                                onTriggerAdminPin()
                            }
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
            TextButton(onClick = onDismiss) {
                Text("關閉")
            }
        }
    )
}

/**
 * 管理員 PIN 碼驗證對話框 (使用 SHA-256 安全雜湊比對)
 */
@Composable
fun AdminPinDialog(
    onDismiss: () -> Unit,
    onVerified: () -> Unit
) {
    var adminPinInput by remember { mutableStateOf("") }
    var adminPinError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
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
                    placeholder = { Text("請輸入 PIN 碼") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = adminPinError,
                    supportingText = {
                        if (adminPinError) {
                            Text("PIN 碼驗證失敗，請重新輸入")
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (verifyPin(adminPinInput)) {
                        onVerified()
                    } else {
                        adminPinError = true
                    }
                }
            ) {
                Text("驗證並進入")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
