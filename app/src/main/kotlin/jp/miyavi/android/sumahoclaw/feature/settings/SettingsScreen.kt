package jp.miyavi.android.sumahoclaw.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import jp.miyavi.android.sumahoclaw.R
import jp.miyavi.android.sumahoclaw.core.designsystem.ConnectedChoiceRow
import jp.miyavi.android.sumahoclaw.core.designsystem.AppTitleBar
import jp.miyavi.android.sumahoclaw.core.designsystem.DefaultCustomSeed
import jp.miyavi.android.sumahoclaw.core.designsystem.Dimens
import jp.miyavi.android.sumahoclaw.core.designsystem.GlassSnackbarHost
import jp.miyavi.android.sumahoclaw.core.designsystem.ProvideScreenHaze
import jp.miyavi.android.sumahoclaw.core.designsystem.Spacing
import jp.miyavi.android.sumahoclaw.core.gateway.ConnectionState
import jp.miyavi.android.sumahoclaw.core.settings.AppFont
import jp.miyavi.android.sumahoclaw.core.settings.AppTheme
import jp.miyavi.android.sumahoclaw.core.settings.Appearance
import jp.miyavi.android.sumahoclaw.core.settings.SurfaceStyle
import jp.miyavi.android.sumahoclaw.core.settings.ThemeSettings
import kotlin.math.roundToInt

/** 設定（1 画面に縦に並べる。AGENTS.md 2.7）。 */
data class SettingsState(
    val theme: ThemeSettings,
    val connection: ConnectionState,
    val url: String?,
    val version: String,
    /** 版の番号（ストアの表示と突き合わせる）。 */
    val versionCode: Int = 0,
    /** サイドパネルに出しているエージェントの数 / すべての数。 */
    val agentsShown: Int = 0,
    val agentsTotal: Int = 0,
    /** 通知の設定（spec 006。null なら節を出さない）。 */
    val notifications: jp.miyavi.android.sumahoclaw.core.settings.NotificationSettings? = null,
    /** ランチャーのショートカットの数（spec 007）。 */
    val shortcuts: Int = 0,
    /** 選べる言語のタグ（アプリ単位の言語）。 */
    val languages: List<String> = emptyList(),
    /** 選んでいる言語のタグ。null なら端末の言語。 */
    val language: String? = null,
    /** 書き換えた考え中のひとりごとの数。null なら既定のまま。 */
    val thinkingPhrases: Int? = null,
)

data class SettingsActions(
    val onBack: () -> Unit,
    val setAppearance: (Appearance) -> Unit,
    val setTheme: (AppTheme) -> Unit,
    val setFont: (AppFont) -> Unit,
    val setFontSize: (Int) -> Unit,
    val setCustomSeed: (Int) -> Unit,
    val setSurfaceStyle: (SurfaceStyle) -> Unit,
    val setGlassRefraction: (Int) -> Unit,
    val setBlurStrength: (Int) -> Unit,
    val reconnect: () -> Unit,
    val disconnect: () -> Unit,
    val forget: () -> Unit,
    val restart: () -> Unit,
    val openAgentLayout: () -> Unit = {},
    val setNotifications: (jp.miyavi.android.sumahoclaw.core.settings.NotificationSettings) -> Unit = {},
    val openNotificationSettings: () -> Unit = {},
    val openShortcuts: () -> Unit = {},
    val openThinkingPhrases: () -> Unit = {},
    val openLicenses: () -> Unit = {},
    /** 言語を切り替える（null なら端末の言語）。 */
    val setLanguage: (String?) -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(state: SettingsState, actions: SettingsActions, snackbar: SnackbarHostState) {
    val hazeState = rememberHazeState()
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    ProvideScreenHaze(hazeState) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = MaterialTheme.colorScheme.surface,
            snackbarHost = { GlassSnackbarHost(snackbar) },
            topBar = {
                AppTitleBar(
                    hazeState = hazeState,
                    title = { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleMedium) },
                    navigationIcon = {
                        IconButton(onClick = actions.onBack) {
                            Icon(painterResource(R.drawable.ic_huge_arrow_left), contentDescription = stringResource(R.string.back_cd))
                        }
                    },
                    actions = {},
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { padding ->
            LazyColumn(
                Modifier.fillMaxSize().hazeSource(hazeState),
                contentPadding = padding,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 並び: 通知・表示・エージェント・接続・情報（マスター指定 2026-11-25）
                // 通知（spec 006）
                // 設定を読み込む前から行を置く。後から先頭に差し込むと、LazyColumn が見えていた「表示」の位置を保とうとして、
                // 開いたときに通知が上に隠れていたため（マスター指定 2026-11-26: 先頭から出す）
                item(key = "notifications") {
                    val notifications = state.notifications
                    run {
                        // 設定の画面では通知のオン・オフだけ。行を押すと詳しい設定（マスター指定 2026-11-24）
                        Section(R.string.settings_section_notifications) {
                            ListItem(
                                leadingContent = { Icon(painterResource(R.drawable.ic_huge_notification), contentDescription = null) },
                                headlineContent = { Text(stringResource(R.string.settings_notify_enabled)) },
                                supportingContent = { Text(stringResource(R.string.settings_notify_details)) },
                                // スイッチの左に縦の区切り線: 行を押すと詳しい設定、スイッチはオン・オフだけ（マスター指定 2026-11-24）
                                trailingContent = {
                                    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                                        androidx.compose.material3.VerticalDivider(
                                            Modifier.height(Spacing.xl).padding(end = Spacing.l),
                                            color = MaterialTheme.colorScheme.outlineVariant,
                                        )
                                        // 読み込むまでは場所だけ取っておく（仮の値を一瞬見せない）
                                        NotificationMasterSwitch(notifications) { actions.setNotifications(it) }
                                    }
                                },
                                colors = transparentItem(),
                                modifier = Modifier.clickable(role = Role.Button, onClick = actions.openNotificationSettings),
                            )
                        }
                    }
                }
                // 表示
                item(key = "display") {
                    Section(R.string.settings_section_appearance) {
                        // アプリ単位の言語。節の先頭（既定は端末の言語。マスター指定 2026-11-26）
                        if (state.languages.isNotEmpty()) {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_language)) },
                                supportingContent = { Text(languageLabel(state.language)) },
                                colors = transparentItem(),
                                modifier = Modifier.clickable(role = Role.Button) { dialog = "language" },
                            )
                        }
                        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.s)) {
                            Text(stringResource(R.string.settings_appearance_label), style = MaterialTheme.typography.bodyLarge)
                            ConnectedChoiceRow(
                                Appearance.entries, state.theme.appearance, actions.setAppearance,
                                Modifier.fillMaxWidth().padding(top = Spacing.s),
                            ) { stringResource(appearanceLabel(it)) }
                        }
                        ChoiceItem(
                            icon = R.drawable.ic_huge_palette,
                            title = stringResource(R.string.settings_section_theme),
                            value = stringResource(themeLabel(state.theme.theme)),
                        ) { dialog = "theme" }
                        if (state.theme.theme == AppTheme.CUSTOM) {
                            val seed = state.theme.customSeedArgb ?: DefaultCustomSeed.toArgb()
                            ListItem(
                                leadingContent = { Box(Modifier.size(Spacing.xl).background(Color(seed), CircleShape)) },
                                headlineContent = { Text(stringResource(R.string.settings_custom_color)) },
                                supportingContent = { Text(colorHex(seed)) },
                                colors = transparentItem(),
                                modifier = Modifier.clickable(role = Role.Button) { dialog = "custom" },
                            )
                        }
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.settings_font)) },
                            supportingContent = { Text(stringResource(fontLabel(state.theme.font))) },
                            colors = transparentItem(),
                            modifier = Modifier.clickable(role = Role.Button) { dialog = "font" },
                        )
                        FontSizeSlider(state.theme.fontSizeSp, actions.setFontSize)
                        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.s)) {
                            Text(stringResource(R.string.settings_surface_style), style = MaterialTheme.typography.bodyLarge)
                            ConnectedChoiceRow(
                                SurfaceStyle.entries, state.theme.surfaceStyle, actions.setSurfaceStyle,
                                Modifier.fillMaxWidth().padding(top = Spacing.s),
                            ) { stringResource(if (it == SurfaceStyle.GLASS) R.string.settings_surface_glass else R.string.settings_surface_blur) }
                        }
                        // 選んでいる透過効果の強さ（10 段階。Gerbil と同じ）
                        if (state.theme.surfaceStyle == SurfaceStyle.GLASS) {
                            LevelSlider(R.string.settings_glass_refraction, R.string.settings_glass_refraction_desc, state.theme.glassRefraction, actions.setGlassRefraction)
                        } else {
                            LevelSlider(R.string.settings_blur_strength, R.string.settings_blur_strength_desc, state.theme.blurStrength, actions.setBlurStrength)
                        }
                    }
                }
                // エージェント: サイドパネルに出すエージェントと並び
                item(key = "agents") {
                    Section(R.string.settings_section_agents) {
                        ChoiceItem(
                            icon = R.drawable.ic_huge_layers,
                            title = stringResource(R.string.settings_agents_layout),
                            value = stringResource(R.string.settings_agents_layout_value, state.agentsShown, state.agentsTotal),
                            onClick = actions.openAgentLayout,
                        )
                        // ランチャーの長押しメニューの項目（spec 007）
                        ChoiceItem(
                            icon = R.drawable.ic_huge_flash,
                            title = stringResource(R.string.settings_shortcuts),
                            value = stringResource(R.string.settings_shortcuts_value, state.shortcuts, jp.miyavi.android.sumahoclaw.core.shortcut.MAX_SHORTCUTS),
                            onClick = actions.openShortcuts,
                        )
                        // 会話の考え中に出すひとりごと（マスター指定 2026-11-28）
                        ChoiceItem(
                            icon = R.drawable.ic_huge_ai_chat,
                            title = stringResource(R.string.settings_thinking_phrases),
                            value = state.thinkingPhrases?.let { stringResource(R.string.settings_thinking_phrases_count, it) }
                                ?: stringResource(R.string.settings_thinking_phrases_default),
                            onClick = actions.openThinkingPhrases,
                        )
                    }
                }
                // 接続: 1 行に接続先と状態。再接続・切断・接続情報の削除は右のアイコン
                item(key = "connection") {
                    // 見出しは「Gateway」。中の行は Gateway のことと分かるので、行の文言から「Gateway」を外す（マスター指定 2026-11-25）
                    Section(R.string.settings_section_gateway) {
                        val connected = state.connection as? ConnectionState.Connected
                        ListItem(
                            leadingContent = {
                                Icon(
                                    painterResource(R.drawable.ic_huge_connection),
                                    contentDescription = null,
                                    tint = if (connected != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            headlineContent = {
                                Text(state.url?.removePrefix("wss://")?.removePrefix("ws://")?.trimEnd('/') ?: "—", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = {
                                Text(
                                    connected?.let { stringResource(R.string.settings_connection_state_connected, it.serverVersion) }
                                        ?: stringResource(R.string.connection_offline),
                                )
                            },
                            trailingContent = {
                                Row {
                                    // 接続と切断は同じ場所で入れ替える（再生と停止のように）。つないでいる・つなごうとしているあいだは切断、
                                    // 切れている・止まっているあいだは接続し直す（マスター指定 2026-11-25）
                                    val active = state.connection !is ConnectionState.Disconnected && state.connection !is ConnectionState.Failed &&
                                        state.connection !is ConnectionState.NotConfigured
                                    if (active) {
                                        IconButton(onClick = actions.disconnect) {
                                            Icon(painterResource(R.drawable.ic_huge_unlink), contentDescription = stringResource(R.string.settings_disconnect))
                                        }
                                    } else {
                                        IconButton(onClick = actions.reconnect) {
                                            Icon(painterResource(R.drawable.ic_huge_refresh), contentDescription = stringResource(R.string.settings_reconnect))
                                        }
                                    }
                                    IconButton(onClick = { dialog = "forget" }) {
                                        Icon(
                                            painterResource(R.drawable.ic_huge_logout),
                                            contentDescription = stringResource(R.string.settings_forget),
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            },
                            colors = transparentItem(),
                        )
                        ListItem(
                            leadingContent = { Icon(painterResource(R.drawable.ic_huge_restart), contentDescription = null) },
                            headlineContent = { Text(stringResource(R.string.settings_restart)) },
                            supportingContent = { Text(stringResource(R.string.settings_restart_desc)) },
                            colors = transparentItem(),
                            modifier = Modifier.clickable(role = Role.Button, onClick = actions.restart),
                        )
                    }
                }
                // 情報（spec 003 US4）: 版と環境、コピー、プライバシーポリシー、オープンソースのライセンス
                item(key = "about") {
                    Section(R.string.settings_section_about) {
                        AboutSection(state, actions, snackbar)
                    }
                }
            }
        }
    }
    when (dialog) {
        "theme" -> ChoiceDialog(
            title = stringResource(R.string.settings_section_theme),
            options = AppTheme.entries,
            selected = state.theme.theme,
            label = { stringResource(themeLabel(it)) },
            description = { stringResource(themeDescription(it)) },
            onDismiss = { dialog = null },
        ) { theme ->
            dialog = if (theme == AppTheme.CUSTOM && state.theme.customSeedArgb == null) "custom" else null
            if (theme != AppTheme.CUSTOM || state.theme.customSeedArgb != null) actions.setTheme(theme)
        }
        "language" -> ChoiceDialog(
            title = stringResource(R.string.settings_language),
            options = listOf<String?>(null) + state.languages,
            selected = state.language?.takeIf { it in state.languages },
            label = { languageLabel(it) },
            onDismiss = { dialog = null },
        ) { tag ->
            dialog = null
            if (tag != state.language) actions.setLanguage(tag)
        }
        "font" -> ChoiceDialog(
            title = stringResource(R.string.settings_font),
            options = AppFont.entries,
            selected = state.theme.font,
            label = { stringResource(fontLabel(it)) },
            onDismiss = { dialog = null },
        ) { font ->
            actions.setFont(font)
            dialog = null
        }
        "custom" -> CustomColorDialog(
            initial = state.theme.customSeedArgb ?: DefaultCustomSeed.toArgb(),
            onDismiss = { dialog = null },
            onSave = { dialog = null; actions.setCustomSeed(it) },
        )
        "forget" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.settings_forget)) },
            text = { Text(stringResource(R.string.settings_forget_confirm)) },
            confirmButton = { TextButton(onClick = { dialog = null; actions.forget() }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun Section(title: Int, content: @Composable () -> Unit) {
    Column(Modifier.widthIn(max = Dimens.maxReadingWidth).fillMaxWidth()) {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = Spacing.l, end = Spacing.l, top = Spacing.xl, bottom = Spacing.s).semantics { heading() },
        )
        content()
    }
}

@Composable
private fun ChoiceItem(icon: Int, title: String, value: String, onClick: () -> Unit) {
    ListItem(
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(value) },
        colors = transparentItem(),
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    )
}

/** 選択のダイアログ（ラジオ）。選ぶと閉じる（Gerbil と同じ）。 */
@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    description: (@Composable (T) -> String)? = null,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    ListItem(
                        headlineContent = { Text(label(option)) },
                        supportingContent = description?.let { d -> { Text(d(option)) } },
                        leadingContent = { RadioButton(selected = option == selected, onClick = null) },
                        colors = transparentItem(),
                        modifier = Modifier.fillMaxWidth().selectable(selected = option == selected, role = Role.RadioButton) { onSelect(option) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

@Composable
private fun languageLabel(tag: String?): String =
    tag?.let { remember(it) { jp.miyavi.android.sumahoclaw.core.settings.AppLanguage.displayName(it) } }
        ?: stringResource(R.string.settings_language_system)

private fun fontLabel(font: AppFont) = when (font) {
    AppFont.LINE_SEED_JP -> R.string.settings_font_line_seed_jp
    AppFont.SYSTEM -> R.string.settings_appearance_system
}

private fun appearanceLabel(v: Appearance) = when (v) {
    Appearance.SYSTEM -> R.string.settings_appearance_system
    Appearance.LIGHT -> R.string.settings_appearance_light
    Appearance.DARK -> R.string.settings_appearance_dark
}

private fun themeLabel(v: AppTheme) = when (v) {
    AppTheme.OPENCLAW -> R.string.theme_openclaw
    AppTheme.MODERN -> R.string.theme_modern
    AppTheme.DYNAMIC -> R.string.theme_dynamic
    AppTheme.CUSTOM -> R.string.theme_custom
}

private fun themeDescription(v: AppTheme) = when (v) {
    AppTheme.OPENCLAW -> R.string.theme_openclaw_desc
    AppTheme.MODERN -> R.string.theme_modern_desc
    AppTheme.DYNAMIC -> R.string.theme_dynamic_desc
    AppTheme.CUSTOM -> R.string.theme_custom_desc
}

@Composable
private fun FontSizeSlider(size: Int, onSizeChange: (Int) -> Unit) {
    val title = stringResource(R.string.settings_font_size)
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.s)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.settings_font_size_value, size), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = size.toFloat(),
            onValueChange = { onSizeChange(it.roundToInt()) },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = title },
            valueRange = ThemeSettings.MIN_FONT_SIZE_SP.toFloat()..ThemeSettings.MAX_FONT_SIZE_SP.toFloat(),
            steps = ThemeSettings.MAX_FONT_SIZE_SP - ThemeSettings.MIN_FONT_SIZE_SP - 1,
        )
    }
}

/** 0〜10 の強さのスライダー。動かしているあいだも即座に反映する。 */
@Composable
private fun LevelSlider(title: Int, description: Int, level: Int, onLevelChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.s)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text("$level / ${ThemeSettings.MAX_LEVEL}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Text(stringResource(description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = level.toFloat(),
            onValueChange = { onLevelChange(it.roundToInt()) },
            valueRange = ThemeSettings.MIN_LEVEL.toFloat()..ThemeSettings.MAX_LEVEL.toFloat(),
            steps = ThemeSettings.MAX_LEVEL - ThemeSettings.MIN_LEVEL - 1,
        )
    }
}

@Composable
internal fun transparentItem() = ListItemDefaults.colors(containerColor = Color.Transparent)

@Composable
private fun CustomColorDialog(initial: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var value by rememberSaveable { mutableStateOf(colorHex(initial)) }
    val parsed = remember(value) { parseHex(value) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_custom_color)) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.settings_custom_color_hint)) },
                        isError = parsed == null,
                        supportingText = if (parsed == null) ({ Text(stringResource(R.string.settings_custom_color_invalid)) }) else null,
                        modifier = Modifier.weight(1f),
                    )
                    Box(Modifier.size(Dimens.minTouchTarget).background(parsed?.let(::Color) ?: Color.Transparent, CircleShape))
                }
                // 赤・緑・青のスライダー（Gerbil と同じ）。動かすとコードの欄も変わる
                if (parsed != null) {
                    Column(Modifier.heightIn(max = Dimens.colorControlsMaxHeight).verticalScroll(rememberScrollState()).padding(top = Spacing.s)) {
                        listOf(R.string.settings_color_red to 16, R.string.settings_color_green to 8, R.string.settings_color_blue to 0).forEach { (label, shift) ->
                            val channel = (parsed shr shift) and 0xFF
                            Text(stringResource(R.string.settings_color_channel, stringResource(label), channel), style = MaterialTheme.typography.labelMedium)
                            Slider(
                                value = channel.toFloat(),
                                valueRange = 0f..255f,
                                onValueChange = { new ->
                                    val changed = (parsed and (0xFF shl shift).inv()) or (new.roundToInt() shl shift)
                                    value = colorHex(changed)
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { parsed?.let(onSave) }, enabled = parsed != null) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

internal fun colorHex(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

internal fun parseHex(text: String): Int? {
    val hex = text.trim().removePrefix("#")
    if (hex.length != 6) return null
    return hex.toIntOrNull(16)?.let { it or (0xFF shl 24) }
}

/**
 * 通知のオン・オフ。端末の通知の許可も合わせて 1 つのスイッチで扱う（マスター指定 2026-11-27）。
 * - 表示: アプリの設定がオンで、端末でも許可されているときだけオン
 * - オンにする: 許可が無ければここで求める。許可されたらオンにし、拒否されたら端末のこのアプリの通知の設定を開く
 *   （2 回拒否すると、端末はもう尋ねないため）。設定から戻ったら確かめ直す
 * - オフにする: アプリの設定だけをオフにする（端末の許可はアプリから取り消せない）
 */
@Composable
private fun NotificationMasterSwitch(
    notifications: jp.miyavi.android.sumahoclaw.core.settings.NotificationSettings?,
    onChange: (jp.miyavi.android.sumahoclaw.core.settings.NotificationSettings) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var refresh by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val permitted = remember(refresh) { notificationsPermitted(context) }
    val request = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        refresh++
        if (granted) {
            notifications?.let { onChange(it.copy(enabled = true)) }
        } else {
            context.startActivity(
                android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        }
    }
    Switch(
        checked = notifications?.enabled == true && permitted,
        onCheckedChange = { on ->
            val current = notifications ?: return@Switch
            when {
                !on -> onChange(current.copy(enabled = false))
                permitted -> onChange(current.copy(enabled = true))
                else -> request.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        enabled = notifications != null,
        modifier = Modifier.graphicsLayer { alpha = if (notifications != null) 1f else 0f },
    )
}

/** 端末で通知が許可されているか（実行時の許可と、このアプリの通知の設定の両方）。 */
internal fun notificationsPermitted(context: android.content.Context): Boolean =
    androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED &&
        androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
