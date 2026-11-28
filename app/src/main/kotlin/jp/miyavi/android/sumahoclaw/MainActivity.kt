package jp.miyavi.android.sumahoclaw

import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.content.pm.ShortcutManagerCompat
import jp.miyavi.android.sumahoclaw.core.designsystem.playSplashReveal
import jp.miyavi.android.sumahoclaw.core.designsystem.setAppContent
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
        setAppContent(container.settings, onThemeDrawn = { firstThemeDrawn = true }) {
            AppNavigation(container)
        }
    }
}
