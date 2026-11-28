package jp.miyavi.android.sumahoclaw.feature.editor

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import jp.miyavi.android.sumahoclaw.core.data.AGENT_FILE_MAX_BYTES
import jp.miyavi.android.sumahoclaw.core.data.FileRoot
import jp.miyavi.android.sumahoclaw.core.data.FileSource
import jp.miyavi.android.sumahoclaw.core.data.OpenResult
import jp.miyavi.android.sumahoclaw.core.data.OpenedFile
import jp.miyavi.android.sumahoclaw.core.data.PreviewKind
import jp.miyavi.android.sumahoclaw.core.data.SaveResult
import jp.miyavi.android.sumahoclaw.core.text.DecodedText
import jp.miyavi.android.sumahoclaw.core.text.DocumentFormat
import jp.miyavi.android.sumahoclaw.core.text.EncodeResult
import jp.miyavi.android.sumahoclaw.core.text.TextCharset
import jp.miyavi.android.sumahoclaw.core.text.TextDecoding
import jp.miyavi.android.sumahoclaw.feature.files.runCatchingNonCancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/** 読んだ結果。 */
sealed interface LoadResult {
    /** [baseHash] は衝突の確認に使う元の版（Gateway のハッシュ / 端末のファイルは SHA-256）。[bytes] は文字コードを選び直すための元のバイト。 */
    data class Loaded(val decoded: DecodedText, val baseHash: String?, val bytes: ByteArray? = null) : LoadResult
    data object NotText : LoadResult
    data object TooLarge : LoadResult
    data object Missing : LoadResult
    /** 読む許可が無い（端末のファイルで、許可が切れた）。 */
    data object NoPermission : LoadResult
    data object Failed : LoadResult
}

/** 保存の結果。 */
sealed interface SaveOutcome {
    /** [baseHash] は保存後の版。[file] は Gateway のファイルの新しい中身（呼び出し元に返す）。[name] は保存した名前。 */
    data class Saved(val baseHash: String?, val file: OpenedFile? = null, val name: String? = null) : SaveOutcome
    data object Conflict : SaveOutcome
    data object Missing : SaveOutcome
    data class TooLarge(val limitBytes: Long) : SaveOutcome
    data class Unmappable(val offset: Int) : SaveOutcome
    data object ReadOnly : SaveOutcome
    data class Failed(val message: String?) : SaveOutcome
}

/** 対象ごとの読み書き（spec 008 research R4・R8・R9）。1 つのエディターに 1 つ。 */
interface DocumentSource {
    /** 文字コードを選び直せるか（Gateway のファイルは UTF-8 だけ）。 */
    val canChangeCharset: Boolean

    /** 書き戻せるか（false なら「別の名前で保存」だけ）。 */
    val writable: Boolean

    /** 秘密の値を含みそうなファイルか（下書きを端末に残さない。spec 005 FR-068）。 */
    val secret: Boolean get() = false

    suspend fun load(): LoadResult

    /** [overwrite] は衝突のあと「上書き」を選んだとき。[baseHash] は開いたときの版。 */
    suspend fun save(text: String, format: DocumentFormat, baseHash: String?, overwrite: Boolean): SaveOutcome
}

/** 2 MB（端末のファイル。今のビューア・添付のシートと同じ上限。research R8）。 */
const val EDITOR_MAX_BYTES = 2L * 1024 * 1024

/** Gateway の作業フォルダが文字として扱う上限（Gateway 2026.11.10 の `WORKSPACE_PREVIEW_MAX_BYTES`。research R4）。 */
const val SESSION_FILE_MAX_BYTES = 256L * 1024

/** 作業フォルダ・エージェントのファイル。読み込み済みのもの（[handoff]）があれば読み直さない。 */
class GatewayDocument(
    private val files: FileSource,
    private val target: EditTarget.GatewayFile,
    private var meta: OpenedFile?,
) : DocumentSource {
    override val canChangeCharset = false
    override val writable = true
    override val secret: Boolean get() = jp.miyavi.android.sumahoclaw.core.data.isSecretName(target.path)

    private val limit: Long get() = if (target.root is FileRoot.Session) SESSION_FILE_MAX_BYTES else AGENT_FILE_MAX_BYTES

    override suspend fun load(): LoadResult {
        val file = meta?.takeIf { it.text != null } ?: when (val r = runCatchingNonCancel { files.open(target.root, target.path) }) {
            is OpenResult.Opened -> r.file.also { meta = it }
            OpenResult.Missing -> return LoadResult.Missing
            null -> return LoadResult.Failed
        }
        if (file.kind != PreviewKind.TEXT || file.text == null) return if (file.tooLargeToEdit) LoadResult.TooLarge else LoadResult.NotText
        if (!file.editable) return LoadResult.NotText
        return LoadResult.Loaded(TextDecoding.fromGatewayText(file.text), file.hash)
    }

    override suspend fun save(text: String, format: DocumentFormat, baseHash: String?, overwrite: Boolean): SaveOutcome {
        val content = TextDecoding.toGatewayText(text, format)
        if (content.toByteArray().size > limit) return SaveOutcome.TooLarge(limit)
        // 下書きから戻した・読み込みに失敗していたときは、保存の前に元の情報（source・workspace）を取る
        val current = meta ?: when (val r = runCatchingNonCancel { files.open(target.root, target.path) }) {
            is OpenResult.Opened -> r.file.also { meta = it }
            OpenResult.Missing -> return SaveOutcome.Missing
            null -> return SaveOutcome.Failed(null)
        }
        val expected = when {
            overwrite -> when (val latest = runCatchingNonCancel { files.open(target.root, target.path) }) {
                is OpenResult.Opened -> if (latest.file.creating) "" else latest.file.hash
                OpenResult.Missing -> return SaveOutcome.Missing
                null -> return SaveOutcome.Failed(null)
            }
            current.creating -> ""
            else -> baseHash ?: current.hash
        } ?: return SaveOutcome.Missing
        return when (val r = runCatchingNonCancel { files.save(current, content, expected) } ?: SaveResult.Failed(null)) {
            is SaveResult.Saved -> {
                val saved = r.file.copy(text = r.file.text ?: content)
                meta = saved
                SaveOutcome.Saved(saved.hash, saved)
            }
            SaveResult.Conflict -> SaveOutcome.Conflict
            SaveResult.Missing -> SaveOutcome.Missing
            is SaveResult.Failed -> SaveOutcome.Failed(r.message)
        }
    }
}

/** 入力欄の「コピーしたテキスト」のタイル。保存は呼び出し元（ChatViewModel）に返す。 */
class PastedTextDocument(
    private val target: EditTarget.PastedText,
    private val initial: String?,
    private val results: EditorResults,
) : DocumentSource {
    override val canChangeCharset = false

    /** 元のタイルが手元に無い（プロセスが終わった）ときは返す先が無い。 */
    override val writable: Boolean get() = initial != null

    override suspend fun load(): LoadResult = initial?.let { LoadResult.Loaded(TextDecoding.fromGatewayText(it), null) } ?: LoadResult.Missing

    override suspend fun save(text: String, format: DocumentFormat, baseHash: String?, overwrite: Boolean): SaveOutcome {
        if (initial == null) return SaveOutcome.ReadOnly
        results.emit(EditorResult.PasteEdited(target.chatKey, target.textId, text))
        return SaveOutcome.Saved(null)
    }
}

/** ほかのアプリから渡されたファイル（research R8）。 */
class LocalDocumentSource(
    private val target: EditTarget.LocalDocument,
    private val resolver: ContentResolver,
    /** 渡されたときに書く許可があったか。 */
    private val grantedWrite: Boolean,
) : DocumentSource {
    override val canChangeCharset = true
    override var writable: Boolean = grantedWrite
        private set

    override suspend fun load(): LoadResult = withContext(Dispatchers.IO) {
        // 読む許可だけで渡されても、書けることがある（自分で Download に保存した添付を「開く」から開いたときなど）。
        // "rw" は切り詰めずに開くだけなので、確かめても中身は変わらない
        if (!writable) writable = runCatching { resolver.openFileDescriptor(target.uri, "rw")?.use { true } ?: false }.getOrDefault(false)
        val size = runCatching { querySize(resolver, target.uri) }.getOrNull()
        if (size != null && size > EDITOR_MAX_BYTES) return@withContext LoadResult.TooLarge
        val data = try {
            resolver.openInputStream(target.uri)?.use { input -> input.readNBytes((EDITOR_MAX_BYTES + 1).toInt()) }
        } catch (_: SecurityException) {
            return@withContext LoadResult.NoPermission
        } catch (_: java.io.FileNotFoundException) {
            return@withContext LoadResult.Missing
        } catch (_: Exception) {
            null
        } ?: return@withContext LoadResult.Failed
        if (data.size > EDITOR_MAX_BYTES) return@withContext LoadResult.TooLarge
        val decoded = TextDecoding.detect(data) ?: return@withContext LoadResult.NotText
        LoadResult.Loaded(decoded, sha256(data), data)
    }

    override suspend fun save(text: String, format: DocumentFormat, baseHash: String?, overwrite: Boolean): SaveOutcome {
        if (!writable) return SaveOutcome.ReadOnly
        val encoded = when (val r = withContext(Dispatchers.Default) { TextDecoding.encode(text, format) }) {
            is EncodeResult.Ok -> r.bytes
            is EncodeResult.Unmappable -> return SaveOutcome.Unmappable(r.offset)
        }
        return withContext(Dispatchers.IO) {
            // 書く直前に読み直し、開いたときと違えば上書きしない（FR-073）
            if (!overwrite && baseHash != null) {
                val current = try {
                    resolver.openInputStream(target.uri)?.use { it.readNBytes((EDITOR_MAX_BYTES + 1).toInt()) }
                } catch (_: java.io.FileNotFoundException) {
                    return@withContext SaveOutcome.Missing
                } catch (_: Exception) {
                    null
                }
                if (current != null && sha256(current) != baseHash) return@withContext SaveOutcome.Conflict
            }
            try {
                // "w" は切り詰めない提供元があるので "wt"
                resolver.openOutputStream(target.uri, "wt")?.use { it.write(encoded) } ?: return@withContext SaveOutcome.Failed(null)
                SaveOutcome.Saved(sha256(encoded))
            } catch (_: SecurityException) {
                writable = false
                SaveOutcome.ReadOnly
            } catch (e: Exception) {
                SaveOutcome.Failed(e.message)
            }
        }
    }

    companion object {
        /** 渡された Intent で書く許可があるか。 */
        fun grantedWrite(intent: Intent): Boolean =
            intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0 || intent.action == Intent.ACTION_EDIT
    }
}

/** 「別の名前で保存」（端末のファイルを読むだけで開いたとき・タイルが無いとき）。 */
suspend fun writeTo(resolver: ContentResolver, uri: Uri, text: String, format: DocumentFormat): SaveOutcome {
    val encoded = when (val r = withContext(Dispatchers.Default) { TextDecoding.encode(text, format) }) {
        is EncodeResult.Ok -> r.bytes
        is EncodeResult.Unmappable -> return SaveOutcome.Unmappable(r.offset)
    }
    return withContext(Dispatchers.IO) {
        try {
            resolver.openOutputStream(uri, "wt")?.use { it.write(encoded) } ?: return@withContext SaveOutcome.Failed(null)
            SaveOutcome.Saved(sha256(encoded), name = displayName(resolver, uri))
        } catch (e: Exception) {
            SaveOutcome.Failed(e.message)
        }
    }
}

internal fun displayName(resolver: ContentResolver, uri: Uri): String? = runCatching {
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
}.getOrNull()

private fun querySize(resolver: ContentResolver, uri: Uri): Long? =
    resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
        if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
    }

internal fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/** 文字コードを選び直すときに選べるもの（この端末で使えるものだけ）。 */
val selectableCharsets: List<TextCharset> get() = TextCharset.entries.filter { it.charset != null }
