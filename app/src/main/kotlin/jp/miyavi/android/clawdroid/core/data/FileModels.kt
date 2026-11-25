package jp.miyavi.android.clawdroid.core.data

import jp.miyavi.android.clawdroid.core.gateway.array
import jp.miyavi.android.clawdroid.core.gateway.asObject
import jp.miyavi.android.clawdroid.core.gateway.bool
import jp.miyavi.android.clawdroid.core.gateway.long
import jp.miyavi.android.clawdroid.core.gateway.obj
import jp.miyavi.android.clawdroid.core.gateway.string
import kotlinx.serialization.json.JsonObject

/** ファイルを辿る場所（spec 002 FR-002）。 */
sealed interface FileRoot {
    /** 会話の作業フォルダ（`sessions.files.*`、読み書き）。 */
    data class Session(val sessionKey: String, val agentId: String) : FileRoot

    /** エージェントのワークスペース（`agents.workspace.*`、読むだけ）。 */
    data class Workspace(val agentId: String) : FileRoot

    /** エージェントの起動時に読み込まれるファイル（`agents.files.*`、読み書き。spec 004）。一覧は平らで、path = ファイル名。 */
    data class AgentFile(val agentId: String) : FileRoot
}

/** 一覧の 1 行。`path` はルートからの相対。 */
data class FileEntry(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long?,
    val updatedAt: Long?,
)

/** 一覧の結果。 */
data class FileListing(
    val path: String,
    val parentPath: String?,
    val entries: List<FileEntry>,
    /** 作業フォルダの一覧が途中で打ち切られたか。 */
    val truncated: Boolean,
    /** 作業フォルダの絶対パス（`sessions.files.list` の root。ファイルの操作に使う）。 */
    val root: String? = null,
)

/** 開いたファイルの中身の種類。 */
enum class PreviewKind { TEXT, IMAGE, UNSUPPORTED }

/** 開いたファイル（data-model の OpenedFile）。 */
data class OpenedFile(
    val root: FileRoot,
    val path: String,
    val name: String,
    val size: Long?,
    val mimeType: String?,
    val kind: PreviewKind,
    /** 文字のファイルなら内容。 */
    val text: String?,
    /** 画像なら base64。 */
    val imageBase64: String?,
    /** Gateway の内容のハッシュ（衝突の確認に使う。無ければ読むだけ）。 */
    val hash: String?,
    /** `sessions.files.get` の `source`（保存時に `expectedSource` として返す）。 */
    val source: JsonObject?,
    /** `agents.files.get` の `workspace`（保存時に `expectedWorkspace` として返す。表示しない）。 */
    val workspace: String? = null,
    /** まだ無いエージェントのファイルを開いた（保存は `expectedMissing`。spec 004 FR-022）。 */
    val creating: Boolean = false,
    /** 更新日時（`updatedAtMs`。開いた画面の上に出す）。 */
    val updatedAt: Long? = null,
) {
    /**
     * 編集できるか。作業フォルダ（002 FR-012）: ハッシュの付いた UTF-8 の文字のファイル。
     * エージェントのファイル（004 FR-024）: 文字のファイルで、2 MiB 以下、ハッシュがあるか未作成。
     */
    val editable: Boolean get() = kind == PreviewKind.TEXT && text != null && when (root) {
        is FileRoot.Session -> hash != null
        is FileRoot.AgentFile -> (hash != null || creating) && (size ?: 0) <= AGENT_FILE_MAX_BYTES
        is FileRoot.Workspace -> false
    }

    /** エージェントのファイルが大きすぎて編集できない（004 FR-024）。 */
    val tooLargeToEdit: Boolean get() = root is FileRoot.AgentFile && (size ?: 0) > AGENT_FILE_MAX_BYTES
}

/** `agents.files.set` が受け付ける大きさ（2 MiB）。 */
const val AGENT_FILE_MAX_BYTES = 2L * 1024 * 1024

/** エージェントのファイルの役割（名前から決める。spec 004 FR-020）。並びはこの順。 */
enum class AgentFileRole(val fileName: String) {
    INSTRUCTIONS("AGENTS.md"),
    SOUL("SOUL.md"),
    IDENTITY("IDENTITY.md"),
    USER("USER.md"),
    HEARTBEAT("HEARTBEAT.md"),
    MEMORY("MEMORY.md"),
    BOOTSTRAP("BOOTSTRAP.md"),
    ;

    companion object {
        fun of(name: String): AgentFileRole? = entries.firstOrNull { it.fileName.equals(name, ignoreCase = true) }
    }
}

/** `agents.files.list` の 1 行。ホストのパスは持たない。 */
data class AgentFileEntry(
    val name: String,
    val missing: Boolean,
    /** 無いのが普通のファイル（USER.md・MEMORY.md など）。 */
    val expectedAbsent: Boolean,
    val size: Long?,
    val updatedAt: Long?,
) {
    val role: AgentFileRole? get() = AgentFileRole.of(name)
}

/** 存在するもの → 未作成、その中は役割の順、知らない名前は最後に名前順。 */
fun sortAgentFiles(files: List<AgentFileEntry>): List<AgentFileEntry> =
    files.sortedWith(
        compareBy<AgentFileEntry> { it.missing }
            .thenBy { it.role?.ordinal ?: Int.MAX_VALUE }
            .thenComparator { a, b -> naturalCompare(a.name, b.name) },
    )

/** フォルダが先、名前の自然順（大文字小文字を区別しない）。 */
fun sortEntries(entries: List<FileEntry>): List<FileEntry> =
    entries.sortedWith(compareBy<FileEntry> { !it.isDirectory }.thenComparator { a, b -> naturalCompare(a.name, b.name) })

/** 数字の並びを数として比べる（file2 < file10）。 */
internal fun naturalCompare(a: String, b: String): Int {
    var i = 0
    var j = 0
    while (i < a.length && j < b.length) {
        val ca = a[i]
        val cb = b[j]
        if (ca.isDigit() && cb.isDigit()) {
            val si = i
            val sj = j
            while (i < a.length && a[i].isDigit()) i++
            while (j < b.length && b[j].isDigit()) j++
            val na = a.substring(si, i).trimStart('0')
            val nb = b.substring(sj, j).trimStart('0')
            if (na.length != nb.length) return na.length - nb.length
            val c = na.compareTo(nb)
            if (c != 0) return c
        } else {
            val c = ca.lowercaseChar().compareTo(cb.lowercaseChar())
            if (c != 0) return c
            i++
            j++
        }
    }
    return (a.length - i) - (b.length - j)
}

internal object FileParse {
    fun entry(obj: JsonObject): FileEntry? {
        val name = obj.string("name")?.takeIf(String::isNotBlank) ?: return null
        val path = obj.string("path") ?: return null
        return FileEntry(path, name, obj.string("kind") == "directory", obj.long("size"), obj.long("updatedAtMs"))
    }

    /** `sessions.files.list` の結果。`browser` が無い古い Gateway は、`files`（会話で触れたファイル）を平らに出す。 */
    fun sessionListing(root: JsonObject?, requested: String): FileListing {
        val browser = root?.obj("browser")
        if (browser != null) {
            return FileListing(
                path = browser.string("path") ?: requested,
                parentPath = browser.string("parentPath"),
                entries = sortEntries(browser.array("entries").mapNotNull { it.asObject()?.let(::entry) }),
                truncated = browser.bool("truncated") == true,
                root = root.string("root"),
            )
        }
        val files = root?.array("files").orEmpty().mapNotNull { element ->
            val f = element.asObject() ?: return@mapNotNull null
            if (f.bool("missing") == true) return@mapNotNull null
            val path = f.string("path") ?: return@mapNotNull null
            FileEntry(path, f.string("name") ?: path.substringAfterLast('/'), false, f.long("size"), f.long("updatedAtMs"))
        }
        return FileListing(requested, null, sortEntries(files), truncated = false)
    }

    /** `sessions.files.get` / `set` の結果。 */
    fun sessionFile(root: FileRoot.Session, result: JsonObject?): OpenedFile? {
        val file = result?.obj("file") ?: return null
        val path = file.string("path") ?: return null
        val encoding = file.string("contentEncoding") ?: "utf8"
        val content = file.string("content")
        val mime = file.string("mimeType")
        val kind = when (file.string("previewKind")) {
            "text" -> PreviewKind.TEXT
            "image" -> PreviewKind.IMAGE
            "unsupported" -> PreviewKind.UNSUPPORTED
            else -> if (content == null) PreviewKind.UNSUPPORTED else if (mime?.startsWith("image/") == true) PreviewKind.IMAGE else PreviewKind.TEXT
        }.let { if (content == null) PreviewKind.UNSUPPORTED else it }
        return OpenedFile(
            root = root,
            path = path,
            name = file.string("name") ?: path.substringAfterLast('/'),
            size = file.long("size"),
            mimeType = mime,
            kind = kind,
            text = content.takeIf { kind == PreviewKind.TEXT && encoding == "utf8" },
            imageBase64 = content.takeIf { kind == PreviewKind.IMAGE && encoding == "base64" },
            hash = file.string("hash"),
            source = result.obj("source"),
            updatedAt = file.long("updatedAtMs"),
        )
    }

    /** `agents.files.list` の結果。 */
    fun agentFiles(result: JsonObject?): List<AgentFileEntry> = sortAgentFiles(
        result?.array("files").orEmpty().mapNotNull { element ->
            val f = element.asObject() ?: return@mapNotNull null
            val name = f.string("name")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            AgentFileEntry(name, f.bool("missing") == true, f.bool("expectedAbsent") == true, f.long("size"), f.long("updatedAtMs"))
        },
    )

    /** `agents.files.get` / `set` の結果。未作成なら空の内容で `creating` にする。 */
    fun agentFile(root: FileRoot.AgentFile, result: JsonObject?): OpenedFile? {
        val file = result?.obj("file") ?: return null
        val name = file.string("name") ?: return null
        val missing = file.bool("missing") == true
        val content = file.string("content") ?: if (missing) "" else null
        return OpenedFile(
            root = root,
            path = name,
            name = name,
            size = file.long("size"),
            mimeType = "text/markdown",
            kind = if (content == null) PreviewKind.UNSUPPORTED else PreviewKind.TEXT,
            text = content,
            imageBase64 = null,
            hash = file.string("hash").takeIf { !missing },
            source = null,
            workspace = result.string("workspace"),
            creating = missing,
            updatedAt = file.long("updatedAtMs").takeIf { !missing },
        )
    }

    /** `agents.workspace.list` の結果。 */
    fun workspaceListing(result: JsonObject?, requested: String): Triple<FileListing, Int, Int> {
        val entries = result?.array("entries").orEmpty().mapNotNull { it.asObject()?.let(::entry) }
        val listing = FileListing(result?.string("path") ?: requested, result?.string("parentPath"), entries, truncated = false)
        return Triple(listing, (result?.long("totalEntries") ?: entries.size.toLong()).toInt(), (result?.long("offset") ?: 0).toInt())
    }

    /** `agents.workspace.get` の結果。 */
    fun workspaceFile(root: FileRoot.Workspace, result: JsonObject?): OpenedFile? {
        val file = result?.obj("file") ?: return null
        val path = file.string("path") ?: return null
        val mime = file.string("mimeType")
        val encoding = file.string("encoding")
        val content = file.string("content")
        val kind = when {
            content == null -> PreviewKind.UNSUPPORTED
            encoding == "utf8" -> PreviewKind.TEXT
            mime?.startsWith("image/") == true -> PreviewKind.IMAGE
            else -> PreviewKind.UNSUPPORTED
        }
        return OpenedFile(
            root = root,
            path = path,
            name = file.string("name") ?: path.substringAfterLast('/'),
            size = file.long("size"),
            mimeType = mime,
            kind = kind,
            text = content.takeIf { kind == PreviewKind.TEXT },
            imageBase64 = content.takeIf { kind == PreviewKind.IMAGE },
            hash = null,
            source = null,
            updatedAt = file.long("updatedAtMs"),
        )
    }
}
