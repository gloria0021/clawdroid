package jp.miyavi.android.sumahoclaw.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import jp.miyavi.android.sumahoclaw.R
import jp.miyavi.android.sumahoclaw.core.data.Agent
import jp.miyavi.android.sumahoclaw.core.designsystem.AppTitleBar
import jp.miyavi.android.sumahoclaw.core.designsystem.ConnectedChoiceRow
import jp.miyavi.android.sumahoclaw.core.designsystem.ConnectedImageChoiceRow
import jp.miyavi.android.sumahoclaw.core.designsystem.Dimens
import jp.miyavi.android.sumahoclaw.core.designsystem.FloatingIconButton
import jp.miyavi.android.sumahoclaw.core.designsystem.ProvideScreenHaze
import jp.miyavi.android.sumahoclaw.core.designsystem.Spacing
import jp.miyavi.android.sumahoclaw.core.settings.OfficeSettings
import jp.miyavi.android.sumahoclaw.core.settings.OfficeTheme
import jp.miyavi.android.sumahoclaw.feature.chat.AgentAvatar
import jp.miyavi.android.sumahoclaw.feature.chat.sidebar.OFFICE_AVATAR_COUNT
import jp.miyavi.android.sumahoclaw.feature.chat.sidebar.officeAvatarOf

/**
 * バーチャルオフィスの詳しい設定（設定 → エージェント → バーチャルオフィス。spec 009）。1 段だけ深い画面。
 * テーマ（自動・昼・夜）と、サイドパネルに出しているエージェントごとのキャラクター。
 */
@Composable
internal fun VirtualOfficeSettingsScreen(
    settings: OfficeSettings,
    /** サイドパネルに出しているエージェント（最大 4 体。タブと同じ並び）。 */
    agents: List<Agent>,
    onTheme: (OfficeTheme) -> Unit,
    onAvatar: (agentId: String, preset: Int) -> Unit,
    onBack: () -> Unit,
) {
    val hazeState = rememberHazeState()
    ProvideScreenHaze(hazeState) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                AppTitleBar(
                    hazeState = hazeState,
                    title = { Text(stringResource(R.string.settings_virtual_office), style = MaterialTheme.typography.titleMedium, maxLines = 2) },
                    navigationIcon = {
                        FloatingIconButton(
                            hazeState, painterResource(R.drawable.ic_huge_chevron_left), stringResource(R.string.back_cd), onBack,
                            modifier = Modifier.padding(start = Spacing.s),
                        )
                    },
                    actions = {},
                )
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().hazeSource(hazeState),
                contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + Spacing.l),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item(key = "theme") {
                    Column(Modifier.widthIn(max = Dimens.maxReadingWidth).fillMaxWidth()) {
                        Heading(R.string.office_theme)
                        ConnectedChoiceRow(
                            OfficeTheme.entries, settings.theme, onTheme,
                            Modifier.fillMaxWidth().padding(horizontal = Spacing.l),
                        ) { stringResource(themeLabel(it)) }
                        Text(
                            stringResource(R.string.office_theme_desc),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = Spacing.l, end = Spacing.l, top = Spacing.s),
                        )
                        Heading(R.string.office_avatars)
                        Text(
                            stringResource(if (agents.isEmpty()) R.string.office_avatars_empty else R.string.office_avatars_desc),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.l),
                        )
                    }
                }
                items(agents, key = { "avatar:${it.id}" }) { agent ->
                    Column(Modifier.widthIn(max = Dimens.maxReadingWidth).fillMaxWidth().padding(bottom = Spacing.s)) {
                        ListItem(
                            leadingContent = { AgentAvatar(agent) },
                            headlineContent = { Text(agent.name, maxLines = 2) },
                            colors = transparentItem(),
                        )
                        ConnectedImageChoiceRow(
                            (0 until OFFICE_AVATAR_COUNT).toList(),
                            officeAvatarOf(agent.id, settings.avatars),
                            { onAvatar(agent.id, it) },
                            Modifier.fillMaxWidth().padding(horizontal = Spacing.l),
                            description = { stringResource(R.string.office_avatar_cd, it + 1) },
                        ) { painterResource(AVATAR_IMAGES[it]) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Heading(title: Int) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth()
            .padding(start = Spacing.l, end = Spacing.l, top = Spacing.xl, bottom = Spacing.s)
            .semantics { heading() },
    )
}

private fun themeLabel(v: OfficeTheme) = when (v) {
    OfficeTheme.AUTO -> R.string.office_theme_auto
    OfficeTheme.DAY -> R.string.office_theme_day
    OfficeTheme.NIGHT -> R.string.office_theme_night
}

/** キャラクターの見本（assets/office/index.html の PRESETS と同じ並び）。 */
private val AVATAR_IMAGES = listOf(R.drawable.office_avatar_1, R.drawable.office_avatar_2, R.drawable.office_avatar_3, R.drawable.office_avatar_4)
