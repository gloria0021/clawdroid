package jp.miyavi.android.sumahoclaw.feature.files

import org.junit.Assert.assertEquals
import org.junit.Test

class FileUploadTest {
    @Test fun freeName_addsNumberBeforeExtension() {
        assertEquals("a (2).png", freeName("a.png", setOf("a.png")))
        assertEquals("a (3).png", freeName("a.png", setOf("a.png", "a (2).png")))
        assertEquals("README (2)", freeName("README", setOf("README")))
        assertEquals(".env (2)", freeName(".env", setOf(".env")))
    }
}
