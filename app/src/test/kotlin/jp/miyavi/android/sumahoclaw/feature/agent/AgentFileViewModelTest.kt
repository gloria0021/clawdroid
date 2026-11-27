package jp.miyavi.android.sumahoclaw.feature.agent

import jp.miyavi.android.sumahoclaw.core.data.FileListing
import jp.miyavi.android.sumahoclaw.core.data.FileRoot
import jp.miyavi.android.sumahoclaw.core.data.FileSource
import jp.miyavi.android.sumahoclaw.core.data.OpenResult
import jp.miyavi.android.sumahoclaw.core.data.OpenedFile
import jp.miyavi.android.sumahoclaw.core.data.PreviewKind
import jp.miyavi.android.sumahoclaw.core.data.SaveResult
import jp.miyavi.android.sumahoclaw.core.database.FileDraftDao
import jp.miyavi.android.sumahoclaw.core.database.FileDraftEntity
import jp.miyavi.android.sumahoclaw.feature.files.OpenedState
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

/** エージェントのファイル 1 つ（spec 004 US1）。共有した OpenedFileController の、作成・衝突・下書き。 */
@OptIn(ExperimentalCoroutinesApi::class)
class AgentFileViewModelTest {
    private inner class FakeFiles : FileSource {
        var content: String? = "# A"
        var hash: String? = "h1"
        val saves = mutableListOf<String>()
        override suspend fun list(root: FileRoot, path: String) = FileListing("", null, emptyList(), false)
        override suspend fun open(root: FileRoot, path: String): OpenResult {
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

    private class FakeDrafts : FileDraftDao {
        val rows = mutableMapOf<String, FileDraftEntity>()
        override suspend fun get(key: String) = rows[key]
        override suspend fun upsert(draft: FileDraftEntity) { rows[draft.key] = draft }
        override suspend fun delete(key: String) { rows.remove(key) }
    }

    private val files = FakeFiles()
    private val drafts = FakeDrafts()
    private fun ready(vm: AgentFileViewModel) = vm.state.value.opened as OpenedState.Ready

    /** 開くのは Default のスレッドでの解析を挟むので、開き終わるまで待つ。 */
    private fun create(name: String) = AgentFileViewModel("x", name, files, drafts).also { vm ->
        waitUntil { vm.state.value.opened is OpenedState.Ready }
    }

    @Before fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun edit_and_save() {
        val vm = create("AGENTS.md")
        vm.startEditing()
        vm.edit("# B", 3)
        vm.save()
        waitUntil { !ready(vm).editing }
        assertEquals("# B", files.content)
        assertEquals(listOf("h1"), files.saves)
    }

    @Test fun missingFile_startsEditing_andCreatesWithEmptyHash() {
        files.content = null
        files.hash = null
        val vm = create("USER.md")
        assertTrue(ready(vm).editing)
        assertTrue(ready(vm).file.creating)
        vm.edit("# Me", 4)
        vm.save()
        waitUntil { !ready(vm).editing }
        assertEquals(listOf(""), files.saves)
        assertEquals("# Me", files.content)
    }

    @Test fun conflict_thenOverwrite_usesLatestHash() {
        val vm = create("AGENTS.md")
        vm.startEditing()
        vm.edit("# Mine", 6)
        files.hash = "h9" // 別の誰かが書き換えた
        vm.save()
        waitUntil { ready(vm).conflict }
        vm.save(overwrite = true)
        waitUntil { !ready(vm).editing }
        assertEquals(listOf("h1", "h9"), files.saves)
        assertEquals("# Mine", files.content)
    }

    @Test fun back_withChanges_asksBeforeDiscarding() {
        val vm = create("AGENTS.md")
        vm.startEditing()
        vm.edit("# Changed", 9)
        assertTrue(vm.back())
        assertTrue(vm.state.value.confirmDiscard)
        assertTrue(vm.discard())
        assertFalse(ready(vm).editing)
    }

    @Test fun draft_isRestored() {
        drafts.rows["agent:x:AGENTS.md"] = FileDraftEntity("agent:x:AGENTS.md", "# Draft", "h1", 7, 0)
        val vm = create("AGENTS.md")
        assertEquals("# Draft", ready(vm).editText)
        assertTrue(ready(vm).restoredDraft)
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue(condition())
    }
}
