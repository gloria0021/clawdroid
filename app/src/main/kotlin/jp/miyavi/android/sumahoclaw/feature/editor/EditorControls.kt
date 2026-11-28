package jp.miyavi.android.sumahoclaw.feature.editor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.chrisbanes.haze.HazeState
import jp.miyavi.android.sumahoclaw.R
import jp.miyavi.android.sumahoclaw.core.designsystem.Dimens
import jp.miyavi.android.sumahoclaw.core.designsystem.FIELD_OPACITY
import jp.miyavi.android.sumahoclaw.core.designsystem.GlassDropdownMenu
import jp.miyavi.android.sumahoclaw.core.designsystem.GlassPill
import jp.miyavi.android.sumahoclaw.core.designsystem.SURFACE_OPACITY
import jp.miyavi.android.sumahoclaw.core.designsystem.SaveStateIcon
import jp.miyavi.android.sumahoclaw.core.designsystem.Spacing
import jp.miyavi.android.sumahoclaw.feature.files.CodeLanguage
import jp.miyavi.android.sumahoclaw.feature.files.FileControlsActions
import jp.miyavi.android.sumahoclaw.feature.files.FileControlsState
import jp.miyavi.android.sumahoclaw.feature.files.FileSearch
import jp.miyavi.android.sumahoclaw.feature.files.SearchRow

/** 下に浮くピルに出すもの（値だけ）。 */
data class EditorControlsState(
    val writable: Boolean,
    val canSave: Boolean,
    val saving: Boolean,
    val savedTick: Int,
    val wrap: Boolean,
    val language: CodeLanguage?,
    val searching: Boolean,
    val query: String,
    val matchIndex: Int,
    val matchCount: Int,
)

class EditorControlsActions(
    val onSave: () -> Unit,
    val onToggleWrap: () -> Unit,
    val onSelectLanguage: (CodeLanguage?) -> Unit,
    val onOpenSearch: () -> Unit,
    val onQuery: (String) -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onCloseSearch: () -> Unit,
)

/**
 * エディターの下に浮くピル（AGENTS.md 2.7。Gerbil のスレ画面の下部と同じ形）。右寄せの 1 本に、左に主操作（保存）、
 * 縦線で区切って右に道具（折り返し・言語・検索）。言語は押すとガラスのポップアップで選ぶ。
 * 検索中はピルが横いっぱいの検索欄（前後の送り付き）に形を変える。
 */
@Composable
fun EditorControls(hazeState: HazeState, state: EditorControlsState, actions: EditorControlsActions, modifier: Modifier = Modifier) {
    val motion = MaterialTheme.motionScheme
    // 2 つの姿は、ばねで伸び縮みしながら入れ替わる（同じ面が姿を変えて見えるように）
    AnimatedContent(
        targetState = state.searching,
        transitionSpec = {
            (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = SWAP_SCALE)) togetherWith
                fadeOut(motion.fastEffectsSpec()) using SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec() }
        },
        contentAlignment = Alignment.BottomEnd,
        modifier = modifier.fillMaxWidth(),
        label = "editorControls",
    ) { searching ->
        if (searching) {
            // 検索欄は入力中の文字が読めるよう、入力欄と同じ不透明度
            GlassPill(hazeState = hazeState, modifier = Modifier.fillMaxWidth(), fixedOpacity = FIELD_OPACITY) {
                SearchRow(
                    state = FileControlsState(
                        canToggleCode = false,
                        showCode = true,
                        editable = false,
                        search = FileSearch(query = state.query),
                        htmlCounts = (state.matchIndex + 1).coerceAtLeast(0) to state.matchCount,
                    ),
                    actions = FileControlsActions(
                        onToggleCode = {},
                        onEdit = {},
                        onOpenSearch = {},
                        onQuery = actions.onQuery,
                        onPrevious = actions.onPrevious,
                        onNext = actions.onNext,
                        onCloseSearch = actions.onCloseSearch,
                    ),
                )
            }
        } else {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                GlassPill(hazeState = hazeState, fixedOpacity = SURFACE_OPACITY) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                        SaveButton(state, actions.onSave)
                        VerticalDivider(
                            modifier = Modifier.height(Spacing.xl).padding(horizontal = Spacing.xs),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        IconToggleButton(checked = state.wrap, onCheckedChange = { actions.onToggleWrap() }) {
                            Icon(painterResource(R.drawable.ic_huge_wrap), contentDescription = stringResource(R.string.editor_menu_wrap))
                        }
                        LanguageButton(state.language, actions.onSelectLanguage)
                        IconButton(onClick = actions.onOpenSearch) {
                            Icon(painterResource(R.drawable.ic_huge_search), contentDescription = stringResource(R.string.files_search_cd))
                        }
                    }
                }
            }
        }
    }
}

/** 主操作の保存（アイコンと文字）。書き換えられないファイルは「別の名前で保存」。 */
@Composable
private fun SaveButton(state: EditorControlsState, onSave: () -> Unit) {
    val label = stringResource(if (state.writable) R.string.files_save_cd else R.string.editor_menu_save_as)
    TextButton(
        onClick = onSave,
        enabled = state.canSave,
        contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
    ) {
        SaveStateIcon(saving = state.saving, savedTick = state.savedTick) {
            Icon(painterResource(R.drawable.ic_huge_check), contentDescription = null)
        }
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(label, maxLines = 1)
    }
}

/** 言語のボタン。押すとガラスのポップアップで選ぶ（今の言語に印）。 */
@Composable
private fun LanguageButton(language: CodeLanguage?, onSelect: (CodeLanguage?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(R.drawable.ic_huge_source_code), contentDescription = stringResource(R.string.editor_language_cd))
        }
        LanguageMenu(open, { open = false }, onSelect, selected = language, heading = true)
    }
}

/** 言語を選び直すメニュー（「なし」を先頭に。今の言語に印）。情報の帯からも開く。 */
@Composable
internal fun LanguageMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onSelect: (CodeLanguage?) -> Unit,
    selected: CodeLanguage? = null,
    heading: Boolean = false,
) {
    GlassDropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (heading) {
            Text(
                stringResource(R.string.editor_menu_language),
                modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s).semantics { heading() },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }
        val check: @Composable (Boolean) -> Unit = { on ->
            if (on) Icon(painterResource(R.drawable.ic_huge_check), contentDescription = stringResource(R.string.editor_selected_cd))
            else Spacer(Modifier.width(Dimens.smallIcon))
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.editor_language_none)) },
            leadingIcon = { check(selected == null) },
            onClick = { onDismiss(); onSelect(null) },
        )
        CodeLanguage.entries.forEach { lang ->
            DropdownMenuItem(
                text = { Text(stringResource(lang.label)) },
                leadingIcon = { check(selected == lang) },
                onClick = { onDismiss(); onSelect(lang) },
            )
        }
    }
}

/** 入れ替わるときに、新しい姿が少し小さい所から広がる割合。 */
private const val SWAP_SCALE = 0.92f
