package jp.miyavi.android.sumahoclaw.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** ターミナルの配色: 背景は #212121、文字色（0 番の黒を除く）はどれも AA 以上。 */
@RunWith(RobolectricTestRunner::class)
class TerminalPaletteTest {
    @Test fun nuancePalette_isReadableOnDarkGray_forAnyThemeColor() {
        listOf(Color(0xFFDC2626), Color(0xFF6750A4), Color(0xFF808080), Color(0xFF00897B)).forEach { primary ->
            val palette = terminalPalette(primary)
            assertEquals(0xFF212121.toInt(), palette.background)
            assertEquals(16, palette.ansi.size)
            (listOf(palette.foreground, palette.cursor) + palette.ansi.drop(1)).forEach { color ->
                val contrast = ColorUtils.calculateContrast(color, palette.background)
                assertTrue("contrast %.2f for #%06x".format(contrast, color and 0xFFFFFF), contrast >= 4.5)
            }
        }
    }

    @Test fun specialKeys_areReadableOnTheTerminal_inLightTheme() {
        val light = androidx.compose.material3.lightColorScheme()
        val palette = terminalPalette(light.primary)
        val chrome = terminalChromeScheme(light, palette)
        fun contrast(a: Color, b: Color) = ColorUtils.calculateContrast(a.toArgb(), b.toArgb())
        assertTrue(contrast(chrome.onSurface, chrome.surface) >= 4.5)
        assertTrue(contrast(chrome.onSurface, chrome.surfaceContainerHighest) >= 4.5)
        assertTrue(contrast(chrome.onSecondaryContainer, chrome.secondaryContainer) >= 4.5)
        assertTrue(contrast(chrome.onSurfaceVariant, chrome.surface) >= 4.5)
    }

    @Test fun softModern_isLighterButLinksStayReadable() {
        listOf(false, true).forEach { dark ->
            val mono = if (dark) androidx.compose.material3.darkColorScheme(primary = Color.White, primaryContainer = Color(0xFFE0E0E0))
            else androidx.compose.material3.lightColorScheme(primary = Color.Black, primaryContainer = Color(0xFF303030))
            val soft = softModernScheme(mono, dark)
            val contrast = ColorUtils.calculateContrast(soft.primary.toArgb(), soft.surface.toArgb())
            assertTrue("contrast %.2f".format(contrast), contrast >= LINK_MIN_CONTRAST)
            // 真っ黒 / 真っ白ではなく灰色
            val l = FloatArray(3).also { ColorUtils.colorToHSL(soft.primary.toArgb(), it) }[2]
            assertTrue("lightness $l", l in 0.2f..0.85f)
        }
    }
}
