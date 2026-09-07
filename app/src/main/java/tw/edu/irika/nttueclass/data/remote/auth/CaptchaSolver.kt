package tw.edu.irika.nttueclass.data.remote.auth

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class CaptchaSolver(private val context: Context) {
    private val tessDataPath: String = context.filesDir.absolutePath + "/tesseract/"
    
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
        return@withContext true
    }
    
    suspend fun solve(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        var tessBaseAPI: TessBaseAPI? = null
        try {
            tessBaseAPI = TessBaseAPI()
            val success = tessBaseAPI.init(tessDataPath, "eng", TessBaseAPI.OEM_DEFAULT)
            if (!success) {
                return@withContext ""
            }
            
            tessBaseAPI.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "0123456789")
            tessBaseAPI.pageSegMode = TessBaseAPI.PageSegMode.PSM_SINGLE_WORD
            
            // Draw on white background to prevent transparency issues
            val whiteBgBitmap = android.graphics.Bitmap.createBitmap(bitmap.width, bitmap.height, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(whiteBgBitmap)
            canvas.drawColor(android.graphics.Color.WHITE)
            canvas.drawBitmap(bitmap, 0f, 0f, null)
            
            tessBaseAPI.setImage(whiteBgBitmap)
            val result = tessBaseAPI.utF8Text ?: ""
            return@withContext result.replace("\\s+".toRegex(), "")
        } catch (e: Exception) {
            Log.e("CaptchaSolver", "Solve error", e)
            return@withContext ""
        } finally {
            tessBaseAPI?.recycle()
        }
    }
}
