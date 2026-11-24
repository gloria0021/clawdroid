package jp.miyavi.android.clawdroid.feature.chat.message

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.contextmenu.builder.item
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuItem
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuKeys
import androidx.compose.foundation.text.contextmenu.modifier.appendTextContextMenuComponents
import androidx.compose.foundation.text.contextmenu.modifier.filterTextContextMenuComponents
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import jp.miyavi.android.clawdroid.R

/**
 * 吹き出しの文字を選んだときのメニューに「プロンプトに入力」を足す（このアプリの中だけ。マスター指定 2026-11-25）。
 * 選んだ範囲は外から読めないので、標準の「コピー」の動きを借り、そのときだけクリップボードに書かずに受け取る。
 */
@Composable
internal fun PromptFromSelection(onInsert: (String) -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val system = LocalClipboard.current
    val clipboard = remember(system) { CapturingClipboard(system) }
    val latest by rememberUpdatedState(onInsert)
    val label = stringResource(R.string.process_text_label)
    // メニューを組み立てるたびに、その選択の「コピー」を覚えておく
    val copy = remember { arrayOfNulls<TextContextMenuItem>(1) }
    CompositionLocalProvider(LocalClipboard provides clipboard) {
        Box(
            modifier
                .filterTextContextMenuComponents { component ->
                    if (component is TextContextMenuItem && component.key == TextContextMenuKeys.CopyKey) copy[0] = component
                    true
                }
                .appendTextContextMenuComponents {
                    item(key = PromptKey, label = label) {
                        val copyItem = copy[0] ?: return@item close()
                        clipboard.captureNext { text -> if (text.isNotBlank()) latest(text) }
                        copyItem.onClick(this)
                    }
                },
        ) { content() }
    }
}

private object PromptKey

/** 次の 1 回だけ、書き込みをクリップボードに渡さずに受け取る。それ以外はそのまま端末のクリップボードへ。 */
private class CapturingClipboard(private val delegate: Clipboard) : Clipboard {
    @Volatile private var capture: ((String) -> Unit)? = null

    fun captureNext(block: (String) -> Unit) { capture = block }

    override suspend fun getClipEntry(): ClipEntry? = delegate.getClipEntry()

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        val take = capture
        if (take != null) {
            capture = null
            take(clipEntry?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString().orEmpty())
            return
        }
        delegate.setClipEntry(clipEntry)
    }

    override val nativeClipboard get() = delegate.nativeClipboard
}
