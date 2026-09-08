package tw.edu.irika.nttueclass.data.local.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureCredentialStorage(context: Context) {
    private val prefs: SharedPreferences

    // 記憶體快取：避免每次 Flow emit 都觸發 EncryptedSharedPreferences AES-GCM 解密
    @Volatile
    private var cachedLoggedIn: Boolean? = null

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        prefs = EncryptedSharedPreferences.create(
            context,
            "nttu_secure_credentials_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        // 初始化時預載快取，後續讀取不再觸發解密
        cachedLoggedIn = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    companion object {
        private const val KEY_STUDENT_ID = "student_id"
        private const val KEY_PASSWORD = "password"
        private const val KEY_LAST_LOGIN_TIMESTAMP = "last_login_timestamp"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_SPECIAL_TOKEN = "special_token"
        private const val KEY_DONATION_INTEGER = "donation_integer"
    }

    fun getSpecialToken(): String? = prefs.getString(KEY_SPECIAL_TOKEN, null)

    fun saveSpecialToken(token: String?) {
        prefs.edit()
            .putString(KEY_SPECIAL_TOKEN, token?.trim())
            .apply()
    }

    fun getDonationInteger(): Long = prefs.getLong(KEY_DONATION_INTEGER, 0L)

    fun saveDonationInteger(value: Long) {
        prefs.edit()
            .putLong(KEY_DONATION_INTEGER, value)
            .apply()
    }

    fun saveCredentials(studentId: String, password: String) {
        if (studentId.isBlank() || password.isBlank()) return
        prefs.edit()
            .putString(KEY_STUDENT_ID, studentId.trim())
            .putString(KEY_PASSWORD, password)
            .putLong(KEY_LAST_LOGIN_TIMESTAMP, System.currentTimeMillis())
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .apply()
        cachedLoggedIn = true
    }

    fun getStudentId(): String? = prefs.getString(KEY_STUDENT_ID, null)

    fun getPassword(): String? = prefs.getString(KEY_PASSWORD, null)

    fun isLoggedIn(): Boolean {
        return cachedLoggedIn ?: prefs.getBoolean(KEY_IS_LOGGED_IN, false).also { cachedLoggedIn = it }
    }

    fun clearCredentials() {
        prefs.edit()
            .remove(KEY_STUDENT_ID)
            .remove(KEY_PASSWORD)
            .remove(KEY_LAST_LOGIN_TIMESTAMP)
            .putBoolean(KEY_IS_LOGGED_IN, false)
            .apply()
        cachedLoggedIn = false
    }
}
