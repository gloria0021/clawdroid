package jp.miyavi.android.clawdroid

import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.miyavi.android.clawdroid.core.designsystem.ClawdroidTheme
import jp.miyavi.android.clawdroid.core.designsystem.playSplashReveal
import jp.miyavi.android.clawdroid.navigation.AppNavigation

class MainActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        (application as ClawdroidApp).container.gateway.onForeground()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        openFromNotification(intent)
    }

    private fun openFromNotification(intent: android.content.Intent?) {
        jp.miyavi.android.clawdroid.core.notify.NotificationCenter.targetOf(intent)?.let {
            (application as ClawdroidApp).container.foreground.open(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as ClawdroidApp).container
        // 通知から開いた（回転などで作り直したときは読み直さない）
        if (savedInstanceState == null) openFromNotification(intent)
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
