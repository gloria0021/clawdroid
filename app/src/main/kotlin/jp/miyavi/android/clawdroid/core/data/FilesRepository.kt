package jp.miyavi.android.clawdroid.core.data

import jp.miyavi.android.clawdroid.core.gateway.GatewayClient
import jp.miyavi.android.clawdroid.core.gateway.GatewayRpcException
import jp.miyavi.android.clawdroid.core.gateway.array
import jp.miyavi.android.clawdroid.core.gateway.asObject
import jp.miyavi.android.clawdroid.core.gateway.long
import jp.miyavi.android.clawdroid.core.gateway.string
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 保存の結果。 */
sealed interface SaveResult {
    data class Saved(val file: OpenedFile) : SaveResult
    /** 開いたあとに Gateway 側で書き換えられた（FR-009）。 */
    data object Conflict : SaveResult
    /** ファイルが消えた。 */
    data object Missing : SaveResult
    data class Failed(val message: String?) : SaveResult
}

/** ファイルを開いた結果。大きすぎるファイルは中身なしで開く（FR-007）。 */
sealed interface OpenResult {
    data class Opened(val file: OpenedFile) : OpenResult
    data object Missing : OpenResult
}

/** ダウンロードのもと（FR-013・015）。 */
sealed interface DownloadSource {
    /** Gateway からの相対の短命リンク（`sessions.files.download`）。 */
    data class Link(val url: String, val filename: String, val mimeType: String) : DownloadSource
    /** 中身をそのまま持っている（ワークスペース）。 */
    data class Inline(val bytes: ByteArray, val filename: String, val mimeType: String) : DownloadSource
    /** 渡せない（理由つき）。 */
    data class Unavailable(val reason: Reason) : DownloadSource
    enum class Reason { TOO_LARGE, NO_HTTP, MISSING, FAILED }
}

/** ファイルの読み書きの窓口（ViewModel のテストで偽物に差し替える）。 */
interface FileSource {
    suspend fun list(root: FileRoot, path: String): FileListing
    suspend fun open(root: FileRoot, path: String): OpenResult
    suspend fun save(file: OpenedFile, content: String, expectedHash: String = file.hash.orEmpty()): SaveResult

    /** HTML の相対の資材（CSS・JS・画像）を data: の URL で返す。取れないものは入らない。 */
    suspend fun htmlAssets(root: FileRoot, path: String, refs: List<String>): Map<String, String> = emptyMap()
}

/**
 * 作業フォルダ（`sessions.files.*`）とワークスペース（`agents.workspace.*`）の読み書き（spec 002、contracts/gateway-rpc.md）。
 * 画面から GatewayClient を直接呼ばない（AGENTS.md）。
 */
class FilesRepository(private val gateway: GatewayClient) : FileSource {

    override suspend fun list(root: FileRoot, path: String): FileListing = when (root) {
        is FileRoot.Session -> FileParse.sessionListing(
            gateway.request("sessions.files.list", buildJsonObject {
                put("sessionKey", root.sessionKey)
                put("agentId", root.agentId)
                if (path.isNotEmpty()) put("path", path)
            }).asObject(),
            path,
        )
        is FileRoot.Workspace -> {
            // ページを最後まで読む（1 ページ WORKSPACE_PAGE 件）
            val entries = mutableListOf<FileEntry>()
            var offset = 0
            var first: FileListing? = null
            do {
                val (page, total, at) = FileParse.workspaceListing(
                    gateway.request("agents.workspace.list", buildJsonObject {
                        put("agentId", root.agentId)
                        if (path.isNotEmpty()) put("path", path)
                        put("offset", offset)
                        put("limit", WORKSPACE_PAGE)
                    }).asObject(),
                    path,
                )
                if (first == null) first = page
                entries += page.entries
                offset = at + page.entries.size
            } while (page.entries.isNotEmpty() && offset < total && entries.size < WORKSPACE_MAX_ENTRIES)
            first!!.copy(entries = sortEntries(entries), truncated = entries.size >= WORKSPACE_MAX_ENTRIES)
        }
        is FileRoot.AgentFile -> FileListing(
            path = "",
            parentPath = null,
            entries = agentFiles(root.agentId).filterNot { it.missing }.map { FileEntry(it.name, it.name, false, it.size, it.updatedAt) },
            truncated = false,
        )
    }

    /** エージェントのファイルの一覧（`agents.files.list`。spec 004 FR-020）。 */
    suspend fun agentFiles(agentId: String): List<AgentFileEntry> =
        FileParse.agentFiles(gateway.request("agents.files.list", buildJsonObject { put("agentId", agentId) }).asObject())

    override suspend fun open(root: FileRoot, path: String): OpenResult = try {
        val file = when (root) {
            is FileRoot.Session -> FileParse.sessionFile(root, gateway.request("sessions.files.get", sessionPath(root, path)).asObject())
            is FileRoot.Workspace -> FileParse.workspaceFile(root, gateway.request("agents.workspace.get", buildJsonObject {
                put("agentId", root.agentId)
                put("path", path)
            }).asObject())
            is FileRoot.AgentFile -> FileParse.agentFile(root, gateway.request("agents.files.get", buildJsonObject {
                put("agentId", root.agentId)
                put("name", path)
            }).asObject())
        }
        (file?.let { if (root is FileRoot.Session) withLargeImage(root, it) else it })?.let(OpenResult::Opened) ?: OpenResult.Missing
    } catch (e: GatewayRpcException) {
        when (e.detailType) {
            "session_file_not_found" -> OpenResult.Missing
            // 大きすぎるファイルは、中身を出さずに種類と大きさだけで開く
            "session_file_too_large" -> OpenResult.Opened(
                OpenedFile(root, path, path.substringAfterLast('/'), e.error.details?.long("size"), null, PreviewKind.UNSUPPORTED, null, null, null, null),
            )
            else -> throw e
        }
    }

    /** 保存（FR-008・009）。衝突の判定は Gateway の `expectedHash` に任せる。 */
    override suspend fun save(file: OpenedFile, content: String, expectedHash: String): SaveResult {
        val agentRoot = file.root as? FileRoot.AgentFile
        if (agentRoot != null) return saveAgentFile(agentRoot, file, content, expectedHash)
        val root = file.root as? FileRoot.Session ?: return SaveResult.Failed(null)
        return try {
            val result = gateway.request("sessions.files.set", buildJsonObject {
                put("sessionKey", root.sessionKey)
                put("agentId", root.agentId)
                put("path", file.path)
                put("content", content)
                put("expectedHash", expectedHash)
                file.source?.let { put("expectedSource", it) }
            }).asObject()
            FileParse.sessionFile(root, result)?.let(SaveResult::Saved) ?: SaveResult.Failed(null)
        } catch (e: GatewayRpcException) {
            when (e.detailType) {
                "session_file_conflict", "session_file_source_changed" -> SaveResult.Conflict
                "session_file_not_found" -> SaveResult.Missing
                else -> SaveResult.Failed(e.error.message)
            }
        }
    }

    /**
     * エージェントのファイルの保存（spec 004 FR-022、contracts/gateway-rpc.md）。[expectedHash] が空なら、
     * まだ無いことを条件に作る（`expectedMissing`）。読んだときの `workspace` を条件に添える。衝突の判定は Gateway に任せる。
     */
    private suspend fun saveAgentFile(root: FileRoot.AgentFile, file: OpenedFile, content: String, expectedHash: String): SaveResult {
        if (content.toByteArray().size > AGENT_FILE_MAX_BYTES) return SaveResult.Failed(null)
        return try {
            val result = gateway.request("agents.files.set", buildJsonObject {
                put("agentId", root.agentId)
                put("name", file.path)
                put("content", content)
                if (expectedHash.isNotEmpty()) put("expectedHash", expectedHash) else put("expectedMissing", true)
                file.workspace?.let { put("expectedWorkspace", it) }
            }).asObject()
            FileParse.agentFile(root, result)?.let(SaveResult::Saved) ?: SaveResult.Failed(null)
        } catch (e: GatewayRpcException) {
            if (e.detailType == "agent_file_conflict") SaveResult.Conflict else SaveResult.Failed(e.error.message)
        }
    }

    /**
     * Gateway が中身を返さなかった画像（中身を添えるのは小さな画像だけ）を、ダウンロードの短命リンクから取って
     * プレビューできるようにする（マスター指定 2026-11-26）。取れなければそのまま（種類と大きさとダウンロード）。
     */
    private suspend fun withLargeImage(root: FileRoot.Session, file: OpenedFile): OpenedFile {
        if (file.kind != PreviewKind.UNSUPPORTED || file.mimeType?.lowercase() !in PREVIEWABLE_IMAGES) return file
        if ((file.size ?: Long.MAX_VALUE) > MAX_IMAGE_PREVIEW_BYTES) return file
        val link = downloadSource(root, file.path) as? DownloadSource.Link ?: return file
        var bytes: ByteArray? = null
        val attempt = runCatching {
            gateway.downloadLink(link.url) { input, _, _ ->
                bytes = input.readNBytes(MAX_IMAGE_PREVIEW_BYTES.toInt() + 1).takeIf { it.size <= MAX_IMAGE_PREVIEW_BYTES }
            }
        }.getOrNull()
        val data = bytes?.takeIf { attempt == GatewayClient.LinkAttempt.COMPLETE } ?: return file
        return file.copy(kind = PreviewKind.IMAGE, imageBase64 = java.util.Base64.getEncoder().encodeToString(data))
    }

    /** ダウンロードのもと。作業フォルダは短命リンク、ワークスペースは中身。 */
    suspend fun downloadSource(root: FileRoot, path: String): DownloadSource = try {
        when (root) {
            is FileRoot.Session -> {
                val result = gateway.request("sessions.files.download", sessionPath(root, path)).asObject()
                val url = result?.string("url")
                if (url == null) DownloadSource.Unavailable(DownloadSource.Reason.FAILED)
                else DownloadSource.Link(url, result.string("filename") ?: path.substringAfterLast('/'), result.string("mimeType") ?: OCTET_STREAM)
            }
            is FileRoot.Workspace -> {
                val file = gateway.request("agents.workspace.get", buildJsonObject {
                    put("agentId", root.agentId)
                    put("path", path)
                }).asObject()?.let { it["file"] as? JsonObject }
                val content = file?.string("content")
                if (file == null || content == null) DownloadSource.Unavailable(DownloadSource.Reason.TOO_LARGE)
                else DownloadSource.Inline(
                    bytes = if (file.string("encoding") == "base64") android.util.Base64.decode(content, android.util.Base64.DEFAULT) else content.toByteArray(),
                    filename = file.string("name") ?: path.substringAfterLast('/'),
                    mimeType = file.string("mimeType") ?: OCTET_STREAM,
                )
            }
            is FileRoot.AgentFile -> {
                val file = (open(root, path) as? OpenResult.Opened)?.file
                val text = file?.text
                if (file == null || text == null || file.creating) DownloadSource.Unavailable(DownloadSource.Reason.MISSING)
                else DownloadSource.Inline(text.toByteArray(), file.name, "text/markdown")
            }
        }
    } catch (e: GatewayRpcException) {
        DownloadSource.Unavailable(
            when (e.detailType) {
                "session_file_download_unavailable" -> DownloadSource.Reason.NO_HTTP
                "session_file_not_found" -> DownloadSource.Reason.MISSING
                "session_file_too_large" -> DownloadSource.Reason.TOO_LARGE
                else -> DownloadSource.Reason.FAILED
            },
        )
    }

    override suspend fun htmlAssets(root: FileRoot, path: String, refs: List<String>): Map<String, String> {
        if (root !is FileRoot.Session || refs.isEmpty()) return emptyMap()
        val result = runCatching {
            gateway.request("sessions.files.assets", buildJsonObject {
                put("sessionKey", root.sessionKey)
                put("agentId", root.agentId)
                put("path", path)
                put("refs", kotlinx.serialization.json.buildJsonArray { refs.take(HTML_ASSET_MAX_REFS).forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
            }).asObject()
        }.getOrNull() ?: return emptyMap()
        return result.array("assets").mapNotNull { it.asObject() }.mapNotNull { asset ->
            val ref = asset.string("ref") ?: return@mapNotNull null
            val mime = asset.string("mimeType") ?: return@mapNotNull null
            val content = asset.string("content") ?: return@mapNotNull null
            ref to "data:$mime;base64,$content"
        }.toMap()
    }

    private fun sessionPath(root: FileRoot.Session, path: String) = buildJsonObject {
        put("sessionKey", root.sessionKey)
        put("agentId", root.agentId)
        put("path", path)
    }

    private val GatewayRpcException.detailType: String? get() = error.details?.string("type")

    private companion object {
        /** sessions.files.assets が 1 回に受ける参照の数。 */
        const val HTML_ASSET_MAX_REFS = 64
        const val WORKSPACE_PAGE = 200
        /** ワークスペースの 1 つのフォルダで読む上限。 */
        const val WORKSPACE_MAX_ENTRIES = 2_000
        const val OCTET_STREAM = "application/octet-stream"
        /** ダウンロードのリンクから取ってプレビューする画像の上限。 */
        const val MAX_IMAGE_PREVIEW_BYTES = 20L * 1024 * 1024
        /** 端末で表示できる画像の種類。 */
        val PREVIEWABLE_IMAGES = setOf("image/png", "image/jpeg", "image/jpg", "image/gif", "image/webp", "image/bmp", "image/heic", "image/heif", "image/avif")
    }
}
