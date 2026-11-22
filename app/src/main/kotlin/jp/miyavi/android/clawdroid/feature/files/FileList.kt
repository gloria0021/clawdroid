package jp.miyavi.android.clawdroid.feature.files

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.data.FileEntry
import jp.miyavi.android.clawdroid.core.data.FileListing
import jp.miyavi.android.clawdroid.core.designsystem.Spacing

/** ファイルとフォルダの一覧（spec 002 FR-001・003）。項目はカードにせず ListItem（AGENTS.md 2.1）。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FileList(
    listing: FileListing,
    contentPadding: PaddingValues,
    onOpen: (FileEntry) -> Unit,
    onDownload: (FileEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val downloadLabel = stringResource(R.string.files_download_cd)
    if (listing.entries.isEmpty()) {
        Box(modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.files_empty), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = contentPadding) {
        items(listing.entries, key = { it.path }, contentType = { if (it.isDirectory) "dir" else "file" }) { entry ->
            val meta = listOfNotNull(
                entry.size?.takeIf { !entry.isDirectory }?.let { Formatter.formatShortFileSize(context, it) },
                entry.updatedAt?.let { DateUtils.getRelativeTimeSpanString(it).toString() },
            ).joinToString("  ")
            ListItem(
                headlineContent = { Text(entry.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                supportingContent = meta.takeIf(String::isNotEmpty)?.let { text ->
                    { Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                },
                leadingContent = {
                    Icon(
                        painterResource(if (entry.isDirectory) R.drawable.ic_huge_folder else R.drawable.ic_huge_file),
                        contentDescription = null,
                        tint = if (entry.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.combinedClickable(
                    onClick = { onOpen(entry) },
                    // ファイルは長押しでダウンロード（TalkBack からも呼べるようにラベルを付ける。AGENTS.md 2.11）
                    onLongClick = if (entry.isDirectory) null else ({ onDownload(entry) }),
                    onLongClickLabel = if (entry.isDirectory) null else downloadLabel,
                ),
            )
        }
        if (listing.truncated) {
            item(key = "truncated", contentType = "note") {
                Text(
                    stringResource(R.string.files_truncated),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.m),
                )
            }
        }
    }
}

/** 読み込み中・失敗の表示（一覧と開いたファイルで共通）。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LoadingOrFailed(failed: Boolean, message: Int, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (!failed) {
            LoadingIndicator()
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.padding(Spacing.l)) {
                Text(stringResource(message), style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
            }
        }
    }
}
