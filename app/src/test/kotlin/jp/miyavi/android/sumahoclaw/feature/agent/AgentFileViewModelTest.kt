package jp.miyavi.android.sumahoclaw.feature.agent

import jp.miyavi.android.sumahoclaw.core.data.FileListing
import jp.miyavi.android.sumahoclaw.core.data.FileRoot
import jp.miyavi.android.sumahoclaw.core.data.FileSource
import jp.miyavi.android.sumahoclaw.core.data.OpenResult
import jp.miyavi.android.sumahoclaw.core.data.OpenedFile
import jp.miyavi.android.sumahoclaw.core.data.PreviewKind
import jp.miyavi.android.sumahoclaw.core.data.SaveResult
import jp.miyavi.android.sumahoclaw.feature.editor.EditTarget
import jp.miyavi.android.sumahoclaw.feature.editor.EditorResult
import jp.miyavi.android.sumahoclaw.feature.files.FilesEvent
import jp.miyavi.android.sumahoclaw.feature.files.OpenedState
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** エージェントのファイル 1 つ（spec 004 US1）。編集はエディターの画面に渡す（spec 008）。保存・衝突はエディターのテスト。 */
@OptIn(ExperimentalCoroutinesApi::class)
class AgentFileViewModelTest {
    private inner class FakeFiles : FileSource {
        var content: String? = "# A"
        var hash: String? = "h1"
        val saves = mutableListOf<String>()
        /** 開くのを止めておく（知らせを待ち始めてから開かせる）。 */
        var gate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
        override suspend fun list(root: FileRoot, path: String) = FileListing("", null, emptyList(), false)
        override suspend fun open(root: FileRoot, path: String): OpenResult {
            gate?.await()
            val text = content
            return OpenResult.Opened(
                OpenedFile(root, path, path, text?.length?.toLong(), "text/markdown", PreviewKind.TEXT, text ?: "", null, hash, null,
                    workspace = "w", creating = text == null),
            )
        }
        override suspend fun save(file: OpenedFile, content: String, expectedHash: String): SaveResult {
            saves += expectedHash
            // 空のハッシュ = まだ無いことを条件に作る
            if (expectedHash != (hash ?: "")) return SaveResult.Conflict
            this.content = content
            hash = "h${saves.size + 1}"
            return SaveResult.Saved(file.copy(text = content, hash = hash, creating = false))
        }
    }

    private val files = FakeFiles()
    private val results = MutableSharedFlow<EditorResult>(extraBufferCapacity = 4)
    private fun ready(vm: AgentFileViewModel) = vm.state.value.opened as OpenedState.Ready

    /** 開くのは Default のスレッドでの解析を挟むので、開き終わるまで待つ。 */
    private fun create(name: String) = AgentFileViewModel("x", name, files, results).also { vm ->
        waitUntil { vm.state.value.opened is OpenedState.Ready }
    }

    @Before fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun startEditing_opensEditorWithLoadedFile() = runBlocking {
        val vm = create("AGENTS.md")
        val event = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { vm.events.first() }
        vm.startEditing()
        val opened = withTimeout(5_000) { event.await() } as FilesEvent.OpenEditor
        assertEquals("AGENTS.md", opened.file.path)
        assertEquals("# A", opened.file.text)
    }

    @Test fun missingFile_opensEditorRightAway() = runBlocking {
        files.content = null
        files.hash = null
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        files.gate = gate
        val vm = AgentFileViewModel("x", "USER.md", files, results)
        val event = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { vm.events.first() }
        gate.complete(Unit)
        val opened = withTimeout(5_000) { event.await() } as FilesEvent.OpenEditor
        assertTrue(opened.file.creating)
    }

    @Test fun savedInEditor_replacesShownFile() {
        val vm = create("AGENTS.md")
        val file = ready(vm).file
        results.tryEmit(EditorResult.Saved(EditTarget.GatewayFile(file.root, file.path), file.copy(text = "# B", hash = "h2")))
        waitUntil { ready(vm).file.text == "# B" }
        assertEquals("h2", ready(vm).file.hash)
    }

    @Test fun back_closesSearchFirst() {
        val vm = create("AGENTS.md")
        vm.openSearch()
        assertTrue(vm.back())
        assertFalse(vm.back())
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue(condition())
    }
}
