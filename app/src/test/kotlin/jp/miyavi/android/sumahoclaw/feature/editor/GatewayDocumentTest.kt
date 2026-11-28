package jp.miyavi.android.sumahoclaw.feature.editor

import jp.miyavi.android.sumahoclaw.core.data.FileListing
import jp.miyavi.android.sumahoclaw.core.data.FileRoot
import jp.miyavi.android.sumahoclaw.core.data.FileSource
import jp.miyavi.android.sumahoclaw.core.data.OpenResult
import jp.miyavi.android.sumahoclaw.core.data.OpenedFile
import jp.miyavi.android.sumahoclaw.core.data.PreviewKind
import jp.miyavi.android.sumahoclaw.core.data.SaveResult
import jp.miyavi.android.sumahoclaw.core.text.TextDecoding
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Gateway のファイルの読み書き（spec 008 research R4。衝突・作成・上限・BOM と改行）。 */
class GatewayDocumentTest {
    private class FakeFiles(var content: String?, var hash: String?) : FileSource {
        val saves = mutableListOf<Pair<String, String>>()
        override suspend fun list(root: FileRoot, path: String) = FileListing("", null, emptyList(), false)
        override suspend fun open(root: FileRoot, path: String): OpenResult = OpenResult.Opened(
            OpenedFile(root, path, path, content?.length?.toLong(), "text/plain", PreviewKind.TEXT, content ?: "", null, hash, null,
                creating = content == null && root is FileRoot.AgentFile),
        )
        override suspend fun save(file: OpenedFile, content: String, expectedHash: String): SaveResult {
            saves += expectedHash to content
            if (expectedHash != (hash ?: "")) return SaveResult.Conflict
            this.content = content
            hash = "h${saves.size + 1}"
            return SaveResult.Saved(file.copy(text = content, hash = hash, creating = false))
        }
    }

    private val session = EditTarget.GatewayFile(FileRoot.Session("agent:a:s1", "a"), "a.txt")
    private val agent = EditTarget.GatewayFile(FileRoot.AgentFile("a"), "USER.md")

    @Test fun keepsBomAndCrlf() = runBlocking {
        val files = FakeFiles("\uFEFFone\r\ntwo\r\n", "h1")
        val doc = GatewayDocument(files, session, null)
        val loaded = doc.load() as LoadResult.Loaded
        assertEquals("one\ntwo\n", loaded.decoded.text)
        val saved = doc.save("one\nTWO\n", loaded.decoded.format, loaded.baseHash, overwrite = false)
        assertTrue(saved is SaveOutcome.Saved)
        assertEquals("\uFEFFone\r\nTWO\r\n", files.content)
        assertEquals("h1", files.saves.single().first)
    }

    @Test fun conflict_thenOverwriteUsesLatestHash() = runBlocking {
        val files = FakeFiles("a", "h1")
        val doc = GatewayDocument(files, session, null)
        val loaded = doc.load() as LoadResult.Loaded
        files.hash = "h9"
        assertEquals(SaveOutcome.Conflict, doc.save("b", loaded.decoded.format, loaded.baseHash, overwrite = false))
        assertTrue(doc.save("b", loaded.decoded.format, loaded.baseHash, overwrite = true) is SaveOutcome.Saved)
        assertEquals("h9", files.saves.last().first)
        assertEquals("b", files.content)
    }

    @Test fun missingAgentFile_isCreatedWithEmptyHash() = runBlocking {
        val files = FakeFiles(null, null)
        val doc = GatewayDocument(files, agent, null)
        val loaded = doc.load() as LoadResult.Loaded
        assertTrue(doc.save("# Me", loaded.decoded.format, loaded.baseHash, overwrite = false) is SaveOutcome.Saved)
        assertEquals("", files.saves.single().first)
    }

    @Test fun sessionFileOverGatewayLimit_isNotSent() = runBlocking {
        val files = FakeFiles("a", "h1")
        val doc = GatewayDocument(files, session, null)
        val big = "x".repeat((SESSION_FILE_MAX_BYTES + 1).toInt())
        assertEquals(SaveOutcome.TooLarge(SESSION_FILE_MAX_BYTES), doc.save(big, TextDecoding.fromGatewayText("").format, "h1", overwrite = false))
        assertTrue(files.saves.isEmpty())
    }

    @Test fun handedOffFile_isNotReadAgain() = runBlocking {
        val files = FakeFiles("from gateway", "h1")
        val handed = (files.open(session.root, session.path) as OpenResult.Opened).file.copy(text = "handed")
        val loaded = GatewayDocument(files, session, handed).load() as LoadResult.Loaded
        assertEquals("handed", loaded.decoded.text)
    }
}
