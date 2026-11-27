package jp.miyavi.android.sumahoclaw.feature.files

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import jp.miyavi.android.sumahoclaw.R
import jp.miyavi.android.sumahoclaw.core.data.MAX_UPLOAD_BYTES
import jp.miyavi.android.sumahoclaw.core.data.UploadFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 端末で選んだ 1 件（名前と大きさ）。 */
private data class PickedFile(val uri: Uri, val name: String, val size: Long?)

/** 同じ名前があったときの扱い。 */
private enum class ConflictPolicy { OVERWRITE, RENAME, SKIP }

/**
 * アップロードの流れ（マスター指定 2026-11-26）: 端末のファイル選択（複数可）→ 同じ名前があればまとめて確認
 * （上書き・別名で保存・飛ばす）→ [onUpload]。12 MB を超えるファイルは送らず、最後のお知らせの件数に入れる。
 */
@Composable
internal fun rememberUploadLauncher(
    existingNames: (String) -> List<String>,
    onUpload: (parent: String, files: List<UploadFile>) -> Unit,
): (String) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<Pair<String, List<PickedFile>>?>(null) }
    var parent by remember { mutableStateOf("") }

    fun send(target: String, picked: List<PickedFile>, policy: ConflictPolicy) {
        val resolver = context.contentResolver
        val taken = existingNames(target).toMutableSet()
        val files = picked.mapNotNull { file ->
            val exists = file.name in taken
            val name = when {
                !exists -> file.name
                policy == ConflictPolicy.SKIP -> return@mapNotNull null
                policy == ConflictPolicy.OVERWRITE -> file.name
                else -> freeName(file.name, taken)
            }
            taken += name
            UploadFile(file.name, name, file.size, overwrite = exists && policy == ConflictPolicy.OVERWRITE) {
                withContext(Dispatchers.IO) { readLimited(resolver, file.uri) }
            }
        }
        onUpload(target, files)
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val target = parent
        scope.launch {
            val picked = withContext(Dispatchers.IO) { uris.mapNotNull { describe(context.contentResolver, it) } }
            if (picked.isEmpty()) return@launch
            // 大きすぎるものも送る列に入れ、結果の帯で「容量の上限を超えた」と出す（同じ名前の確認には数えない）
            val names = existingNames(target).toSet()
            if (picked.any { it.name in names && (it.size ?: 0) <= MAX_UPLOAD_BYTES }) pending = target to picked
            else send(target, picked, ConflictPolicy.RENAME)
        }
    }

    pending?.let { (target, picked) ->
        val count = picked.count { it.name in existingNames(target) && (it.size ?: 0) <= MAX_UPLOAD_BYTES }
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.files_upload_conflict_title)) },
            text = { Text(pluralStringResource(R.plurals.files_upload_conflict_body, count, count)) },
            confirmButton = {
                TextButton(onClick = { pending = null; send(target, picked, ConflictPolicy.OVERWRITE) }) {
                    Text(stringResource(R.string.files_upload_overwrite))
                }
                TextButton(onClick = { pending = null; send(target, picked, ConflictPolicy.RENAME) }) {
                    Text(stringResource(R.string.files_upload_rename))
                }
            },
            dismissButton = {
                TextButton(onClick = { pending = null; send(target, picked, ConflictPolicy.SKIP) }) {
                    Text(stringResource(R.string.files_upload_skip))
                }
            },
        )
    }

    return { target ->
        parent = target
        launcher.launch(arrayOf("*/*"))
    }
}

private fun describe(resolver: ContentResolver, uri: Uri): PickedFile? = runCatching {
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
        if (!c.moveToFirst()) return@use null
        val name = c.getString(0)?.substringAfterLast('/')?.takeIf(String::isNotBlank) ?: return@use null
        val size = if (c.isNull(1)) null else c.getLong(1)
        PickedFile(uri, name, size)
    }
}.getOrNull()

/** 上限を 1 バイト超えるところまで読む（超えたかは受け取る側で確かめる。大きさが分からないファイルもここで止まる）。 */
private fun readLimited(resolver: ContentResolver, uri: Uri): ByteArray? = runCatching {
    resolver.openInputStream(uri)?.use { input -> input.readNBytes(MAX_UPLOAD_BYTES + 1) }
}.getOrNull()

/** 「名前 (2).拡張子」のように、まだ無い名前にする。 */
internal fun freeName(name: String, taken: Set<String>): String {
    val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
    val base = name.substring(0, dot)
    val ext = name.substring(dot)
    var n = 2
    while ("$base ($n)$ext" in taken) n++
    return "$base ($n)$ext"
}
