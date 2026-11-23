package jp.miyavi.android.clawdroid.core.designsystem

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush

/** 上端のフェードの、ステータスバーの下端での背景色の不透明度。 */
private const val STATUS_FADE_ALPHA = 0.85f

/**
 * 上端のフェード（会話の画面。マスター指定 2026-11-23）。付けた要素の後ろに、背景色（`surface`）を上から溶かして敷く。
 * ステータスバーの下端までは濃く（85%）、要素の下端で透明になる。色は足さず不透明度だけ。
 * 描画の中で作るので、下でスクロールしても組み立て直さない。ステータスバーの余白は付けた要素の側で取る。
 */
@Composable
fun Modifier.statusBarFade(): Modifier {
    val color = MaterialTheme.colorScheme.surface
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    return drawBehind {
        val statusEnd = (statusTop.toPx() / size.height).coerceIn(0f, 1f)
        drawRect(
            Brush.verticalGradient(
                0f to color,
                statusEnd to color.copy(alpha = STATUS_FADE_ALPHA),
                1f to color.copy(alpha = 0f),
            ),
        )
    }
}
