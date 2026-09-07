package tw.edu.irika.nttueclass

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import tw.edu.irika.nttueclass.data.remote.auth.CaptchaSolver

@RunWith(AndroidJUnit4::class)
class CaptchaSolverTest {
    @Test
    fun testSolve() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val solver = CaptchaSolver(context)
        val initialized = solver.init()
        println("Solver init: $initialized")
        
        // Let's download a captcha
        val client = okhttp3.OkHttpClient()
        val req = okhttp3.Request.Builder().url("https://eclass2.nttu.edu.tw/sys/libs/class/capcha/secimg.php?&charLens=6&codeType=num").build()
        val res = client.newCall(req).execute()
        
        // Convert to ARGB_8888
        val options = BitmapFactory.Options()
        options.inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
        val bitmap = BitmapFactory.decodeStream(res.body?.byteStream(), null, options)
        
        println("Bitmap: ${bitmap?.width} x ${bitmap?.height}, config: ${bitmap?.config}")
        
        if (bitmap != null) {
            val code = solver.solve(bitmap)
            println("SOLVED CODE: '$code'")
        }
    }
}
