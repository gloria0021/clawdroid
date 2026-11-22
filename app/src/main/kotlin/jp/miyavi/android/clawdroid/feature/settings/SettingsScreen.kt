package jp.miyavi.android.clawdroid.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.designsystem.DefaultCustomSeed
import jp.miyavi.android.clawdroid.core.designsystem.Dimens
import jp.miyavi.android.clawdroid.core.designsystem.FloatingIconButton
import jp.miyavi.android.clawdroid.core.designsystem.GlassSnackbarHost
import jp.miyavi.android.clawdroid.core.designsystem.ProvideScreenHaze
import jp.miyavi.android.clawdroid.core.designsystem.Spacing
import jp.miyavi.android.clawdroid.core.gateway.ConnectionState
import jp.miyavi.android.clawdroid.core.settings.AppTheme
import jp.miyavi.android.clawdroid.core.settings.Appearance
import jp.miyavi.android.clawdroid.core.settings.SurfaceStyle
import jp.miyavi.android.clawdroid.core.settings.ThemeSettings

/** 設定（1 画面に縦に並べる。AGENTS.md 2.7）。 */
data class SettingsState(
    val theme: ThemeSettings,
    val connection: ConnectionState,
    val url: String?,
    val version: String,
)

data class SettingsActions(
    val onBack: () -> Unit,
    val setAppearance: (Appearance) -> Unit,
    val setTheme: (AppTheme) -> Unit,
    val setCustomSeed: (Int) -> Unit,
    val setSurfaceStyle: (SurfaceStyle) -> Unit,
    val reconnect: () -> Unit,
    val disconnect: () -> Unit,
    val forget: () -> Unit,
    val restart: () -> Unit,
)

@Composable
fun SettingsContent(state: SettingsState, actions: SettingsActions, snackbar: SnackbarHostState) {
    val hazeState = rememberHazeState()
    var colorDialog by rememberSaveable { mutableStateOf(false) }
    var forgetDialog by rememberSaveable { mutableStateOf(false) }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
    ProvideScreenHaze(hazeState) {
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().hazeSource(hazeState),
                    contentPadding = PaddingValues(top = topInset + Dimens.floatingButtonSize + Spacing.xl, bottom = bottomInset + Spacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    item(key = "connection") {
                        Section(R.string.settings_section_connection) {
                            val connected = state.connection as? ConnectionState.Connected
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_connection_address), style = MaterialTheme.typography.titleMedium) },
                                supportingContent = {
                                    Text(
                                        listOfNotNull(
                                            state.url,
                                            connected?.let { stringResource(R.string.settings_connection_state_connected, it.serverVersion) },
                                        ).joinToString("\n"),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                colors = transparentItem(),
                            )
                            ActionItem(R.string.settings_reconnect, onClick = actions.reconnect)
                            ActionItem(R.string.settings_disconnect, onClick = actions.disconnect)
                            ActionItem(R.string.settings_forget, onClick = { forgetDialog = true }, danger = true)
                        }
                    }
                    item(key = "gateway") {
                        Section(R.string.settings_section_gateway) {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_restart), style = MaterialTheme.typography.titleMedium) },
                                supportingContent = { Text(stringResource(R.string.settings_restart_desc), style = MaterialTheme.typography.labelMedium) },
                                colors = transparentItem(),
                                modifier = Modifier.clickable(onClick = actions.restart),
                            )
                        }
                    }
                    item(key = "appearance") {
                        Section(R.string.settings_section_appearance) {
                            SegmentedRow(
                                options = listOf(
                                    Appearance.SYSTEM to R.string.settings_appearance_system,
                                    Appearance.LIGHT to R.string.settings_appearance_light,
                                    Appearance.DARK to R.string.settings_appearance_dark,
                                ),
                                selected = state.theme.appearance,
                                onSelect = actions.setAppearance,
                            )
                        }
                    }
                    item(key = "theme") {
                        Section(R.string.settings_section_theme) {
                            listOf(
                                Triple(AppTheme.OPENCLAW, R.string.theme_openclaw, R.string.theme_openclaw_desc),
                                Triple(AppTheme.MODERN, R.string.theme_modern, R.string.theme_modern_desc),
                                Triple(AppTheme.DYNAMIC, R.string.theme_dynamic, R.string.theme_dynamic_desc),
                                Triple(AppTheme.CUSTOM, R.string.theme_custom, R.string.theme_custom_desc),
                            ).forEach { (theme, title, desc) ->
                                val selected = state.theme.theme == theme
                                ListItem(
                                    headlineContent = { Text(stringResource(title), style = MaterialTheme.typography.titleMedium) },
                                    supportingContent = { Text(stringResource(desc), style = MaterialTheme.typography.labelMedium) },
                                    leadingContent = { RadioButton(selected = selected, onClick = null) },
                                    colors = transparentItem(),
                                    modifier = Modifier.selectable(selected = selected, role = Role.RadioButton) {
                                        if (theme == AppTheme.CUSTOM && state.theme.customSeedArgb == null) colorDialog = true else actions.setTheme(theme)
                                    },
                                )
                            }
                            if (state.theme.theme == AppTheme.CUSTOM) {
                                val seed = state.theme.customSeedArgb ?: DefaultCustomSeed.toArgb()
                                ListItem(
                                    headlineContent = { Text(stringResource(R.string.settings_custom_color), style = MaterialTheme.typography.titleMedium) },
                                    supportingContent = { Text(colorHex(seed), style = MaterialTheme.typography.labelMedium) },
                                    trailingContent = { Box(Modifier.size(Dimens.minTouchTarget / 2).background(Color(seed), CircleShape)) },
                                    colors = transparentItem(),
                                    modifier = Modifier.clickable { colorDialog = true },
                                )
                            }
                            Text(
                                stringResource(R.string.settings_surface_style),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(start = Spacing.l, top = Spacing.m, bottom = Spacing.s),
                            )
                            SegmentedRow(
                                options = listOf(SurfaceStyle.GLASS to R.string.settings_surface_glass, SurfaceStyle.BLUR to R.string.settings_surface_blur),
                                selected = state.theme.surfaceStyle,
                                onSelect = actions.setSurfaceStyle,
                            )
                        }
                    }
                    item(key = "about") {
                        Section(R.string.settings_section_about) {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_version, state.version), style = MaterialTheme.typography.titleMedium) },
                                colors = transparentItem(),
                            )
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = Spacing.l, vertical = Spacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    FloatingIconButton(
                        hazeState = hazeState,
                        icon = painterResource(R.drawable.ic_huge_chevron_left),
                        contentDescription = stringResource(R.string.back_cd),
                        onClick = actions.onBack,
                    )
                    Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                GlassSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing))
            }
        }
    }
    if (colorDialog) {
        CustomColorDialog(
            initial = state.theme.customSeedArgb ?: DefaultCustomSeed.toArgb(),
            onDismiss = { colorDialog = false },
            onSave = { colorDialog = false; actions.setCustomSeed(it) },
        )
    }
    if (forgetDialog) {
        AlertDialog(
            onDismissRequest = { forgetDialog = false },
            title = { Text(stringResource(R.string.settings_forget)) },
            text = { Text(stringResource(R.string.settings_forget_confirm)) },
            confirmButton = { TextButton(onClick = { forgetDialog = false; actions.forget() }) { Text(stringResource(R.string.action_delete)) } },
            dismissButton = { TextButton(onClick = { forgetDialog = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun Section(title: Int, content: @Composable () -> Unit) {
    Column(Modifier.widthIn(max = Dimens.maxReadingWidth).fillMaxWidth().padding(bottom = Spacing.l)) {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s).semantics { heading() },
        )
        content()
    }
}

@Composable
private fun ActionItem(label: Int, onClick: () -> Unit, danger: Boolean = false) {
    ListItem(
        headlineContent = {
            Text(
                stringResource(label),
                style = MaterialTheme.typography.titleMedium,
                color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        },
        colors = transparentItem(),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun <T> SegmentedRow(options: List<Pair<T, Int>>, selected: T, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = Spacing.l)) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                label = { Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}

@Composable
private fun transparentItem() = ListItemDefaults.colors(containerColor = Color.Transparent)

@Composable
private fun CustomColorDialog(initial: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var value by rememberSaveable { mutableStateOf(colorHex(initial)) }
    val parsed = remember(value) { parseHex(value) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_custom_color)) },
        text = {
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
