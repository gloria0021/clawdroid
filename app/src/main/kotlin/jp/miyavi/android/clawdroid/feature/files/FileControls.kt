package jp.miyavi.android.clawdroid.feature.files

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntSize
import dev.chrisbanes.haze.HazeState
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.designsystem.FIELD_OPACITY
import jp.miyavi.android.clawdroid.core.designsystem.FloatingIconButton
import jp.miyavi.android.clawdroid.core.designsystem.GlassPill
import jp.miyavi.android.clawdroid.core.designsystem.SURFACE_OPACITY
import jp.miyavi.android.clawdroid.core.designsystem.Spacing

/** 下に浮く操作の、いま出すもの（値だけ。ViewModel は受け取らない）。 */
internal data class FileControlsState(
    val canToggleCode: Boolean,
    val showCode: Boolean,
    val editable: Boolean,
    val search: FileSearch?,
    /** HTML のプレビューでは WebView の数え方を使う。 */
    val htmlCounts: Pair<Int, Int>?,
    /** コードを読んでいる（折り返しを出す。spec 005 FR-053）。 */
    val codeTools: Boolean = false,
    val wrap: Boolean = false,
)

internal class FileControlsActions(
    val onToggleCode: () -> Unit,
    val onEdit: () -> Unit,
    val onOpenSearch: () -> Unit,
    val onQuery: (String) -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onCloseSearch: () -> Unit,
    val onToggleWrap: () -> Unit = {},
)

/**
 * 開いたファイルの下に浮く操作（Web 版と同じ並び）。左にプレビュー ⇄ コード、右のピルに編集 | 検索。
 * 検索中はピルが検索欄・件数・前後の送りに形を変える（AGENTS.md 2.7）。
 */
@Composable
internal fun FileControls(hazeState: HazeState, state: FileControlsState, actions: FileControlsActions, modifier: Modifier = Modifier) {
    val motion = MaterialTheme.motionScheme
    val searching = state.search != null
    Box(modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = state.canToggleCode && !searching,
            enter = fadeIn(motion.defaultEffectsSpec()),
            exit = fadeOut(motion.fastEffectsSpec()),
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            FloatingIconButton(
                hazeState = hazeState,
                icon = painterResource(if (state.showCode) R.drawable.ic_huge_view else R.drawable.ic_huge_source_code),
                contentDescription = stringResource(if (state.showCode) R.string.files_show_preview_cd else R.string.files_show_code_cd),
                onClick = actions.onToggleCode,
            )
        }
        // 検索中のピルは入力欄として不透明度 80%。色はほかの透過面と同じテーマの色味（AGENTS.md 2.10）
        GlassPill(
            hazeState = hazeState,
            modifier = Modifier.align(Alignment.BottomEnd),
            fixedOpacity = if (searching) FIELD_OPACITY else SURFACE_OPACITY,
        ) {
            val sizeSpec = motion.defaultSpatialSpec<IntSize>()
            val enter = fadeIn(motion.defaultEffectsSpec())
            val exit = fadeOut(motion.fastEffectsSpec())
            AnimatedContent(
                targetState = searching,
                transitionSpec = { (enter togetherWith exit).using(SizeTransform { _, _ -> sizeSpec }) },
                contentAlignment = Alignment.CenterEnd,
                label = "fileControlsPill",
            ) { open ->
                if (open) SearchRow(state, actions) else ActionRow(state, actions)
            }
        }
    }
}

@Composable
private fun ActionRow(state: FileControlsState, actions: FileControlsActions) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (state.editable) {
            IconButton(onClick = actions.onEdit) { Icon(painterResource(R.drawable.ic_huge_edit), stringResource(R.string.files_edit_cd)) }
            VerticalDivider(Modifier.height(Spacing.xl))
        }
        if (state.codeTools) {
            androidx.compose.material3.IconToggleButton(checked = state.wrap, onCheckedChange = { actions.onToggleWrap() }) {
                Icon(painterResource(R.drawable.ic_huge_wrap), stringResource(R.string.files_wrap_cd))
            }
        }
        IconButton(onClick = actions.onOpenSearch) { Icon(painterResource(R.drawable.ic_huge_search), stringResource(R.string.files_search_cd)) }
    }
}

@Composable
private fun SearchRow(state: FileControlsState, actions: FileControlsActions) {
    val search = state.search ?: FileSearch()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val (active, count) = state.htmlCounts ?: ((if (search.matches.isEmpty()) 0 else search.current + 1) to search.matches.size)
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            value = search.query,
            onValueChange = actions.onQuery,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { actions.onNext() }),
            modifier = Modifier.weight(1f).padding(start = Spacing.m).focusRequester(focus),
            decorationBox = { field ->
                Box {
                    if (search.query.isEmpty()) {
                        Text(stringResource(R.string.files_search_hint), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                    }
                    field()
                }
            },
        )
        if (search.query.isNotEmpty()) {
            Text(
                stringResource(R.string.files_search_count, active, count),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.s),
            )
        }
        IconButton(onClick = actions.onPrevious, enabled = count > 0) {
            Icon(painterResource(R.drawable.ic_huge_arrow_up), stringResource(R.string.files_search_prev_cd))
        }
        IconButton(onClick = actions.onNext, enabled = count > 0) {
            Icon(painterResource(R.drawable.ic_huge_arrow_down), stringResource(R.string.files_search_next_cd))
        }
        IconButton(onClick = actions.onCloseSearch) {
            Icon(painterResource(R.drawable.ic_huge_close), stringResource(R.string.files_search_close_cd))
        }
    }
}
