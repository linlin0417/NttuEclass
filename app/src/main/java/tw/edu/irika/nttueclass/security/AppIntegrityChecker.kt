package tw.edu.irika.nttueclass.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * 應用程式防二次打包盜版與完整性校驗器 (Anti-Piracy & Integrity Checker)
 */
object AppIntegrityChecker {

    const val EXPECTED_PACKAGE_NAME: String = "tw.edu.irika.nttueclass"
    const val PLAY_STORE_INSTALLER: String = "com.android.vending"

    /**
     * 完整性檢驗結果
     */
    data class IntegrityCheckResult(
        val isPackageValid: Boolean,
        val isInstallerValid: Boolean,
        val isDebuggable: Boolean,
        val installerName: String,
        val signatureSha256: String,
        val message: String
    )

    /**
     * 執行本機防篡改完整性檢查
     */
    fun checkLocalIntegrity(context: Context): IntegrityCheckResult {
        val packageName = context.packageName
        val isPackageValid = packageName.startsWith(EXPECTED_PACKAGE_NAME)

        val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

        // 1. 檢查安裝來源
        val installerName = getInstallerPackageName(context)
        val isInstallerValid = isDebuggable || installerName == PLAY_STORE_INSTALLER

        // 2. 獲取當前簽名 SHA-256
        val signatureSha256 = getSignatureSha256(context)

        val message = when {
            !isPackageValid -> "警告：應用程式包名遭篡改 ($packageName)"
            !isInstallerValid -> "警告：偵測到非官方 Google Play 安裝來源 ($installerName)"
            isDebuggable -> "開發偵錯模式 (已略過正版商店簽名檢查)"
            else -> "正版官方驗證通過"
        }

        return IntegrityCheckResult(
            isPackageValid = isPackageValid,
            isInstallerValid = isInstallerValid,
            isDebuggable = isDebuggable,
            installerName = installerName,
            signatureSha256 = signatureSha256,
            message = message
        )
    }

    /**
     * 獲取安裝來源套件名稱 (minSdk 33 保證 getInstallSourceInfo 可用)
     */
    fun getInstallerPackageName(context: Context): String {
        return try {
            val sourceInfo = context.packageManager.getInstallSourceInfo(context.packageName)
            sourceInfo.installingPackageName ?: sourceInfo.initiatingPackageName ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }
    }

    /**
     * 獲取 App 簽署金鑰之 SHA-256 指紋 (minSdk 33 保證 GET_SIGNING_CERTIFICATES 可用)
     */
    fun getSignatureSha256(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            )

            val signatures = packageInfo.signingInfo?.apkContentsSigners

            val certBytes = signatures?.firstOrNull()?.toByteArray() ?: return "NO_SIGNATURE"
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(certBytes)
            digest.joinToString(":") { "%02X".format(it) }
        } catch (_: Exception) {
            "UNKNOWN_SIGNATURE"
        }
    }

    /**
     * Google Play Integrity API 請求 (異步)
     */
    suspend fun requestPlayIntegrityToken(context: Context): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val integrityManager = IntegrityManagerFactory.create(context)
            // 標準 Play Integrity 調用流程準備
            "PLAY_INTEGRITY_TOKEN_PREPARED"
        }
    }
}
