package jp.miyavi.android.clawdroid

import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.miyavi.android.clawdroid.core.designsystem.ClawdroidTheme
import jp.miyavi.android.clawdroid.core.designsystem.playSplashReveal
import jp.miyavi.android.clawdroid.core.notify.OpenIntents
import jp.miyavi.android.clawdroid.navigation.AppNavigation

class MainActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        (application as ClawdroidApp).container.gateway.onForeground()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        openFromIntent(intent)
    }

    /** 通知・ランチャーのショートカットから開く行き先（spec 006 R7・spec 007 R2）。 */
    private fun openFromIntent(intent: android.content.Intent?) {
        OpenIntents.targetOf(intent)?.let {
            (application as ClawdroidApp).container.foreground.open(it)
        }
        // ランチャーの並べ方の学習に使われる（FR-012）
        OpenIntents.shortcutIdOf(intent)?.let { ShortcutManagerCompat.reportShortcutUsed(this, it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as ClawdroidApp).container
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
            ClawdroidTheme(ready) {
                AppNavigation(container)
            }
        }
    }
}
