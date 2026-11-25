package jp.miyavi.android.clawdroid.feature.settings

import android.content.ClipData
import android.net.Uri
import android.os.Build
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.gateway.ConnectionState
import jp.miyavi.android.clawdroid.feature.chat.message.rememberChatLinkOpener
import kotlinx.coroutines.launch

/**
 * 設定の「情報」（spec 003 US4・FR-030〜032）。アプリの版・接続中の Gateway の版・Android の版・端末の機種と、
 * それをまとめてコピーする行、プライバシーポリシー、オープンソースのライセンス。
 * コピーする文には接続先の URL・トークン・セッション名・パスを含めない（FR-031）。
 */
@Composable
internal fun AboutSection(state: SettingsState, actions: SettingsActions, snackbar: SnackbarHostState) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val gatewayVersion = (state.connection as? ConnectionState.Connected)?.serverVersion
    val appLine = stringResource(R.string.about_app_version, state.version, state.versionCode)
    val gatewayLine = gatewayVersion ?: stringResource(R.string.about_gateway_offline)
    val androidLine = stringResource(R.string.about_android_version, Build.VERSION.RELEASE, Build.VERSION.SDK_INT)
    val deviceLine = deviceName()
    val copiedText = stringResource(R.string.copied)
    val report = listOf(
        "${stringResource(R.string.app_name)} $appLine",
        "${stringResource(R.string.about_gateway)}: $gatewayLine",
        androidLine,
        "${stringResource(R.string.about_device)}: $deviceLine",
    ).joinToString("\n")
    val openLink = rememberChatLinkOpener(onFailure = {})
    val language = LocalConfiguration.current.locales[0].language

    InfoRow(R.drawable.ic_huge_info, stringResource(R.string.app_name), appLine)
    InfoRow(R.drawable.ic_huge_server, stringResource(R.string.about_gateway), gatewayLine)
    // 端末の行: 機種と Android の版を 1 行に（合う Hugeicons が無い行を増やさない）
    InfoRow(R.drawable.ic_huge_launcher_glyph, stringResource(R.string.about_device), "$deviceLine · $androidLine")
    ActionRow(R.drawable.ic_huge_copy, R.string.about_copy) {
        scope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, report)))
            snackbar.showSnackbar(copiedText)
        }
    }
    ActionRow(R.drawable.ic_huge_globe, R.string.about_privacy_policy) { openLink(Uri.parse(privacyPolicyUrl(language))) }
    ActionRow(R.drawable.ic_huge_file, R.string.about_licenses, onClick = actions.openLicenses)
}

@Composable
private fun InfoRow(icon: Int, title: String, value: String) {
    ListItem(
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(value) },
        colors = transparentItem(),
    )
}

@Composable
private fun ActionRow(icon: Int, title: Int, onClick: () -> Unit) {
    ListItem(
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        headlineContent = { Text(stringResource(title)) },
        colors = transparentItem(),
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    )
}

/** 「Google Pixel 11 Pro」。機種名がメーカー名で始まるときは重ねない。 */
private fun deviceName(): String {
    val maker = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
    val model = Build.MODEL
    return if (model.startsWith(maker, ignoreCase = true)) model else "$maker $model"
}

/**
 * プライバシーポリシーの公開ページ（このレポジトリの docs/privacy-policy.md。spec 003 FR-013・032）。
 * 1 つの文書に日本語と英語があるので、表示の言語の見出しへ飛ぶ。
 */
internal fun privacyPolicyUrl(language: String): String =
    PRIVACY_POLICY_URL + if (language == "ja") "#日本語" else "#english"

private const val PRIVACY_POLICY_URL = "https://github.com/gloria0021/clawdroid/blob/main/docs/privacy-policy.md"
