package jp.miyavi.android.sumahoclaw

import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.miyavi.android.sumahoclaw.core.designsystem.SumahoClawTheme
import jp.miyavi.android.sumahoclaw.core.designsystem.isDark
import jp.miyavi.android.sumahoclaw.core.designsystem.playSplashReveal
import jp.miyavi.android.sumahoclaw.core.notify.OpenIntents
import jp.miyavi.android.sumahoclaw.navigation.AppNavigation

class MainActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        (application as SumahoClawApp).container.gateway.onForeground()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        openFromIntent(intent)
    }

    /** 通知・ランチャーのショートカットから開く行き先（spec 006 R7・spec 007 R2）。 */
    private fun openFromIntent(intent: android.content.Intent?) {
        OpenIntents.targetOf(intent)?.let {
            (application as SumahoClawApp).container.foreground.open(it)
        }
        // ランチャーの並べ方の学習に使われる（FR-012）
        OpenIntents.shortcutIdOf(intent)?.let { ShortcutManagerCompat.reportShortcutUsed(this, it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as SumahoClawApp).container
        // 通知・ショートカットから開いた（回転などで作り直したときは読み直さない）
        if (savedInstanceState == null) openFromIntent(intent)
        var firstThemeDrawn = false
        val content = findViewById<View>(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (!firstThemeDrawn) return false
                content.viewTreeObserver.removeOnPreDrawListener(this)
                return true
            }
        })
        splashScreen.setOnExitAnimationListener { playSplashReveal(this, it) }
        setContent {
            val theme by container.settings.theme.collectAsStateWithLifecycle(initialValue = null)
            val ready = theme ?: return@setContent
            SideEffect { firstThemeDrawn = true }
            // ステータスバーとナビゲーションバーのアイコンの明暗は、端末ではなくアプリの外観の設定に合わせる
            // （既定の enableEdgeToEdge() は端末のダークモードに従うため、ライトの外観でも白いアイコンになっていた）
            val dark = ready.appearance.isDark(isSystemInDarkTheme())
            DisposableEffect(dark) {
                val style = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            SumahoClawTheme(ready) {
                AppNavigation(container)
            }
        }
    }
}
