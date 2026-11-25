package jp.miyavi.android.clawdroid.feature.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import dev.chrisbanes.haze.hazeSource
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.settings.LockScreenContent
import jp.miyavi.android.clawdroid.core.settings.NotificationSettings

/**
 * 通知の節（spec 006 US4・FR-030〜035）。`ListItem` ＋ `Switch` を縦に並べる（AGENTS.md 2.7）。
 * 許可と電池の最適化は、画面に戻るたびに確かめ直す（端末の設定から戻ってきたとき）。
 */
/** 通知の詳しい設定（設定 → 通知。1 段だけ深い画面）。 */
@Composable
internal fun NotificationSettingsScreen(settings: NotificationSettings, onChange: (NotificationSettings) -> Unit, onTest: () -> Unit, onBack: () -> Unit) {
    val hazeState = dev.chrisbanes.haze.rememberHazeState()
    jp.miyavi.android.clawdroid.core.designsystem.ProvideScreenHaze(hazeState) {
        androidx.compose.material3.Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                jp.miyavi.android.clawdroid.core.designsystem.AppTitleBar(
                    hazeState = hazeState,
                    title = { Text(stringResource(R.string.settings_section_notifications), style = MaterialTheme.typography.titleMedium) },
                    navigationIcon = {
                        jp.miyavi.android.clawdroid.core.designsystem.FloatingIconButton(
                            hazeState, painterResource(R.drawable.ic_huge_chevron_left), stringResource(R.string.back_cd), onBack,
                            modifier = Modifier.padding(start = jp.miyavi.android.clawdroid.core.designsystem.Spacing.s),
                        )
                    },
                    actions = {},
                )
            },
        ) { padding ->
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.fillMaxSize().hazeSource(hazeState),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + jp.miyavi.android.clawdroid.core.designsystem.Spacing.l,
                ),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            ) {
                item(key = "items") {
                    androidx.compose.foundation.layout.Column(
                        Modifier.widthIn(max = jp.miyavi.android.clawdroid.core.designsystem.Dimens.maxReadingWidth).fillMaxWidth(),
                    ) { NotificationSection(settings, onChange, onTest) }
                }
            }
        }
    }
}

@Composable
private fun NotificationSection(settings: NotificationSettings, onChange: (NotificationSettings) -> Unit, onTest: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val permitted = remember(refresh) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    val optimized = remember(refresh) {
        !context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    }
    val appNotificationSettings = {
        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
    }
    // 許可を求め、拒否されたら端末の設定を開く（FR-033）
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        refresh++
        if (!granted) appNotificationSettings()
    }

    SwitchItem(R.drawable.ic_huge_connection, R.string.settings_notify_background, R.string.settings_notify_background_desc, settings.backgroundConnection) {
        onChange(settings.copy(backgroundConnection = it))
    }
    SwitchItem(R.drawable.ic_huge_ai_chat, R.string.settings_notify_replies, null, settings.notifyReplies) { onChange(settings.copy(notifyReplies = it)) }
    SwitchItem(R.drawable.ic_huge_check_circle, R.string.settings_notify_approvals, null, settings.notifyApprovals) { onChange(settings.copy(notifyApprovals = it)) }
    SwitchItem(R.drawable.ic_huge_automation, R.string.settings_notify_automation, null, settings.notifyAutomationFailures) {
        onChange(settings.copy(notifyAutomationFailures = it))
    }
    SwitchItem(
        R.drawable.ic_huge_view, R.string.settings_notify_lock_screen, R.string.settings_notify_lock_screen_desc,
        settings.lockScreenContent != LockScreenContent.HIDE,
    ) { show -> onChange(settings.copy(lockScreenContent = if (show) LockScreenContent.FOLLOW_SYSTEM else LockScreenContent.HIDE)) }
    // 状態の行（許可・電池の最適化）と導線
    StatusItem(
        R.drawable.ic_huge_notification, R.string.settings_notify_permission,
        if (permitted) R.string.settings_notify_permission_ok else R.string.settings_notify_permission_missing, warn = !permitted,
        onClick = if (permitted) null else ({ requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }),
    )
    StatusItem(
        R.drawable.ic_huge_alert_circle, R.string.settings_notify_battery,
        if (optimized) R.string.settings_notify_battery_on else R.string.settings_notify_battery_off, warn = optimized,
        onClick = if (optimized) ({
            @android.annotation.SuppressLint("BatteryLife")
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
            runCatching { context.startActivity(intent) }.onFailure { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }) else ({
            // 外したあとは、このアプリの電池の画面（最適化／制限なし）を開く。公開の入口が無いため、開けなければアプリ情報を開く
            val pkg = Uri.parse("package:${context.packageName}")
            runCatching { context.startActivity(Intent(ACTION_APP_BATTERY_DETAIL, pkg)) }
                .onFailure { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)) }
        }),
    )
    StatusItem(R.drawable.ic_huge_settings, R.string.settings_notify_channels, R.string.settings_notify_channels_desc, warn = false, onClick = appNotificationSettings)
    StatusItem(R.drawable.ic_huge_send, R.string.settings_notify_test, null, warn = false, onClick = if (permitted) onTest else null)
}

@Composable
private fun SwitchItem(icon: Int, title: Int, description: Int?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        headlineContent = { Text(stringResource(title)) },
        supportingContent = description?.let { { Text(stringResource(it)) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = transparentItem(),
        modifier = Modifier.clickable(role = Role.Switch) { onChange(!checked) },
    )
}

@Composable
private fun StatusItem(icon: Int, title: Int, value: Int?, warn: Boolean, onClick: (() -> Unit)?) {
    ListItem(
        leadingContent = {
            Icon(painterResource(icon), contentDescription = null, tint = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        },
        headlineContent = { Text(stringResource(title)) },
        supportingContent = value?.let { { Text(stringResource(it), color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) } },
        colors = transparentItem(),
        modifier = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier,
    )
}

/** アプリごとの電池の画面（AOSP の設定アプリの AdvancedPowerUsageDetail。`Settings` には公開されていない）。 */
private const val ACTION_APP_BATTERY_DETAIL = "android.settings.VIEW_ADVANCED_POWER_USAGE_DETAIL"
