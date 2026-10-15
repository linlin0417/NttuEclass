package tw.edu.irika.nttueclass.data.remote.auth

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class CaptchaSolver(private val context: Context) : Closeable {
    companion object {
        private val REGEX_WHITESPACE = Regex("""\s+""")
    }
    private val tessDataPath: String = context.filesDir.absolutePath + "/tesseract/"
    
    @Volatile
    private var tessBaseAPI: TessBaseAPI? = null
    private val apiLock = Any()

    suspend fun init(): Boolean = withContext(Dispatchers.IO) {
        val dir = File(tessDataPath, "tessdata")
        if (!dir.exists() && !dir.mkdirs()) {
            return@withContext false
        }
        
        val dataFile = File(dir, "eng.traineddata")
        if (dataFile.exists() && dataFile.length() < 1000000) {
            dataFile.delete() // Force update from old 279KB model to new 2.3MB model
        }
        
        if (!dataFile.exists()) {
            try {
                context.assets.open("tessdata/eng.traineddata").use { input ->
                    FileOutputStream(dataFile).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: IOException) {
                Log.e("CaptchaSolver", "Failed to copy traineddata", e)
                return@withContext false
            }
        }

        // Initialize TessBaseAPI once and reuse across attempts
        synchronized(apiLock) {
            if (tessBaseAPI == null) {
                val api = TessBaseAPI()
                val success = api.init(tessDataPath, "eng", TessBaseAPI.OEM_DEFAULT)
                if (!success) {
                    api.recycle()
                    return@withContext false
                }
                api.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "0123456789")
                api.pageSegMode = TessBaseAPI.PageSegMode.PSM_SINGLE_WORD
                tessBaseAPI = api
            }
        }
        return@withContext true
    }
    
    suspend fun solve(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        val api = synchronized(apiLock) { tessBaseAPI } ?: run {
            if (!init()) return@withContext ""
            synchronized(apiLock) { tessBaseAPI } ?: return@withContext ""
        }

        // Draw on white background to prevent transparency issues
        val whiteBgBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = android.graphics.Canvas(whiteBgBitmap)
            canvas.drawColor(android.graphics.Color.WHITE)
            canvas.drawBitmap(bitmap, 0f, 0f, null)
            
            val result = synchronized(apiLock) {
                api.setImage(whiteBgBitmap)
                val text = api.utF8Text ?: ""
                api.clear()
                text
            }
            return@withContext result.replace(REGEX_WHITESPACE, "")
        } catch (e: Exception) {
            Log.e("CaptchaSolver", "Solve error", e)
            return@withContext ""
        } finally {
            whiteBgBitmap.recycle()
        }
    }

    fun recycle() {
        synchronized(apiLock) {
            tessBaseAPI?.recycle()
            tessBaseAPI = null
        }
    }

    override fun close() {
        recycle()
    }
}
