package jp.miyavi.android.sumahoclaw.core.designsystem

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.miyavi.android.sumahoclaw.core.settings.SettingsStore

/**
 * アプリのテーマと窓の組み立て（edge-to-edge・外観に合わせたシステムバー・SumahoClawTheme）。
 * 会話の画面とエディター（spec 008）の両方の Activity で使う。テーマを読み終えて初めて描いたら [onThemeDrawn]。
 */
fun ComponentActivity.setAppContent(settings: SettingsStore, onThemeDrawn: () -> Unit = {}, content: @Composable () -> Unit) {
    setContent {
        val theme by settings.theme.collectAsStateWithLifecycle(initialValue = null)
        val ready = theme ?: return@setContent
        SideEffect(onThemeDrawn)
        // ステータスバーとナビゲーションバーのアイコンの明暗は、端末ではなくアプリの外観の設定に合わせる
        // （既定の enableEdgeToEdge() は端末のダークモードに従うため、ライトの外観でも白いアイコンになっていた）
        val dark = ready.appearance.isDark(isSystemInDarkTheme())
        DisposableEffect(dark) {
            val style = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark }
            enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            onDispose {}
        }
        SumahoClawTheme(ready) { content() }
    }
}
