package jp.miyavi.android.sumahoclaw.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageScaleTest {
    @Test fun scalesUntilLongSideFits() {
        assertEquals(1, imageSampleSize(2048, 1000))
        assertEquals(2, imageSampleSize(4032, 3024))
        assertEquals(2016 to 1512, scaledImageSize(4032, 3024))
        assertEquals(4, imageSampleSize(8192, 100))
        assertEquals(1, imageSampleSize(100, 100))
    }
}
