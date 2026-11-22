package jp.miyavi.android.clawdroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.miyavi.android.clawdroid.core.designsystem.ClawdroidTheme
import jp.miyavi.android.clawdroid.core.settings.ThemeSettings
import jp.miyavi.android.clawdroid.navigation.AppNavigation

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as ClawdroidApp).container
        setContent {
            val theme by container.settings.theme.collectAsStateWithLifecycle(initialValue = ThemeSettings())
            ClawdroidTheme(theme) {
                AppNavigation(container)
            }
        }
    }
}
