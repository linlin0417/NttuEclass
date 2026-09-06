package tw.edu.irika.nttueclass.data.local.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureCredentialStorage(context: Context) {
    private val prefs: SharedPreferences

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
    }

    companion object {
        private const val KEY_STUDENT_ID = "student_id"
        private const val KEY_PASSWORD = "password"
        private const val KEY_LAST_LOGIN_TIMESTAMP = "last_login_timestamp"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
    }

    fun saveCredentials(studentId: String, password: String) {
        if (studentId.isBlank() || password.isBlank()) return
        prefs.edit()
            .putString(KEY_STUDENT_ID, studentId.trim())
            .putString(KEY_PASSWORD, password)
            .putLong(KEY_LAST_LOGIN_TIMESTAMP, System.currentTimeMillis())
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .apply()
    }

    fun getStudentId(): String? = prefs.getString(KEY_STUDENT_ID, null)

    fun getPassword(): String? = prefs.getString(KEY_PASSWORD, null)

    fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_IS_LOGGED_IN, false)

    fun clearCredentials() {
        prefs.edit()
            .remove(KEY_STUDENT_ID)
            .remove(KEY_PASSWORD)
            .remove(KEY_LAST_LOGIN_TIMESTAMP)
            .putBoolean(KEY_IS_LOGGED_IN, false)
            .apply()
    }
}
