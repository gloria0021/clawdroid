package jp.miyavi.android.clawdroid.feature.chat.message

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalWindowInfo

internal fun webUri(url: String): Uri? = Uri.parse(url).takeIf {
    (it.scheme.equals("https", ignoreCase = true) || it.scheme.equals("http", ignoreCase = true)) &&
        !it.host.isNullOrBlank()
}

/** Gerbil と同じ Partial Custom Tabs でブラウザを開く。 */
@Composable
internal fun rememberChatLinkOpener(onFailure: () -> Unit): (Uri) -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    val toolbar = MaterialTheme.colorScheme.surfaceContainer.toArgb()
    val height = (LocalWindowInfo.current.containerSize.height * BROWSER_HEIGHT_FRACTION).toInt().coerceAtLeast(1)
    return { uri ->
        val tab = CustomTabsIntent.Builder()
            .setInitialActivityHeightPx(height, CustomTabsIntent.ACTIVITY_HEIGHT_ADJUSTABLE)
            .setToolbarCornerRadiusDp(MAX_TOOLBAR_CORNER_DP)
            .setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder().setToolbarColor(toolbar).build())
            .setShowTitle(true)
            .build()
        try {
            launcher.launch(tab.intent.setData(uri).addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY))
        } catch (_: ActivityNotFoundException) {
            onFailure()
        }
    }
}

private const val BROWSER_HEIGHT_FRACTION = 0.85f
private const val MAX_TOOLBAR_CORNER_DP = 16
