package tw.edu.irika.nttueclass.presentation.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalDialogHostTest {

    @Test
    fun testVerifyPin_validPins() {
        assertTrue("0174 should be valid", verifyPin("0174"))
        assertTrue("0000 should be valid", verifyPin("0000"))
        assertTrue("PIN with surrounding whitespace should be trimmed and valid", verifyPin(" 0174 "))
        assertTrue("PIN with surrounding whitespace should be trimmed and valid", verifyPin(" 0000\n"))
    }

    @Test
    fun testVerifyPin_invalidPins() {
        assertFalse("Empty string should be invalid", verifyPin(""))
        assertFalse("Whitespace should be invalid", verifyPin("   "))
        assertFalse("1234 should be invalid", verifyPin("1234"))
        assertFalse("0 should be invalid", verifyPin("0"))
        assertFalse("174 should be invalid", verifyPin("174"))
        assertFalse("9999 should be invalid", verifyPin("9999"))
        assertFalse("admin should be invalid", verifyPin("admin"))
    }
}
