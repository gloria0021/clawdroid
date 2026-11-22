package jp.miyavi.android.clawdroid.feature.files

import jp.miyavi.android.clawdroid.core.data.DownloadResult
import jp.miyavi.android.clawdroid.core.data.DownloadSource
import jp.miyavi.android.clawdroid.core.data.FileEntry
import jp.miyavi.android.clawdroid.core.data.FileListing
import jp.miyavi.android.clawdroid.core.data.FileRoot
import jp.miyavi.android.clawdroid.core.data.FileSource
import jp.miyavi.android.clawdroid.core.data.OpenResult
import jp.miyavi.android.clawdroid.core.data.OpenedFile
import jp.miyavi.android.clawdroid.core.data.PreviewKind
import jp.miyavi.android.clawdroid.core.data.SaveResult
import jp.miyavi.android.clawdroid.core.database.FileDraftDao
import jp.miyavi.android.clawdroid.core.database.FileDraftEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** ファイルビューアの状態の移り変わり（spec 002 US1・US2、data-model の OpenedFile）。 */
@OptIn(ExperimentalCoroutinesApi::class)
class FilesViewModelTest {
    private val session = FileRoot.Session("agent:a:s1", "a")
    private val workspace = FileRoot.Workspace("a")

    private inner class FakeFiles : FileSource {
        var content = "val x = 1"
        var hash = "h1"
        var failList = false
        val saves = mutableListOf<Pair<String, String>>()

        override suspend fun list(root: FileRoot, path: String): FileListing {
            if (failList) error("offline")
            val entries = if (root is FileRoot.Workspace) listOf(FileEntry("NOTES.md", "NOTES.md", false, 3, null))
            else if (path.isEmpty()) listOf(FileEntry("src", "src", true, null, null), FileEntry("README.md", "README.md", false, 10, null))
            else listOf(FileEntry("src/a.kt", "a.kt", false, 9, null))
            return FileListing(path, if (path.isEmpty()) null else "", entries, truncated = false)
        }

        override suspend fun open(root: FileRoot, path: String): OpenResult = OpenResult.Opened(
            OpenedFile(root, path, path.substringAfterLast('/'), content.length.toLong(), "text/plain", PreviewKind.TEXT, content, null,
                hash.takeIf { root is FileRoot.Session }, null),
        )

        override suspend fun save(file: OpenedFile, content: String, expectedHash: String): SaveResult {
            saves += expectedHash to content
            if (expectedHash != hash) return SaveResult.Conflict
            this.content = content
            hash = "h${saves.size + 1}"
            return SaveResult.Saved(file.copy(text = content, hash = hash))
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
    private fun viewModel(hasWorkingFolder: Boolean = true) = FilesViewModel(
        files, { _, _ -> DownloadResult.Failed(DownloadSource.Reason.FAILED) }, drafts, "agent:a:s1", "a", hasWorkingFolder,
    )

    // 下書きの間引き（delay）を実時間で進めるため、仮想時間のテスト用ではなく素の Unconfined を使う
    @Before fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun waitFor(condition: () -> Boolean) = runBlocking {
        withTimeout(5_000) { while (!condition()) delay(10) }
    }

    @Test fun navigateDownAndBack_oneLevelAtATime() {
        val vm = viewModel()
        waitFor { vm.state.value.listing is ListingState.Loaded }
        vm.openEntry(FileEntry("src", "src", true, null, null))
        waitFor { (vm.state.value.listing as? ListingState.Loaded)?.listing?.path == "src" }
        vm.openEntry(FileEntry("src/a.kt", "a.kt", false, 9, null))
        waitFor { vm.state.value.opened is OpenedState.Ready }
        assertTrue(vm.back()) // ファイル → 一覧
        assertNull(vm.state.value.opened)
        assertTrue(vm.back()) // src → 根
        waitFor { vm.state.value.atRoot }
        assertFalse(vm.back()) // 根では画面を閉じてよい
    }

    @Test fun noWorkingFolder_startsInWorkspace_andWorkspaceIsReadOnly() {
        val vm = viewModel(hasWorkingFolder = false)
        assertEquals(workspace, vm.state.value.root)
        waitFor { vm.state.value.listing is ListingState.Loaded }
        vm.openEntry(FileEntry("NOTES.md", "NOTES.md", false, 3, null))
        waitFor { vm.state.value.opened is OpenedState.Ready }
        val ready = vm.state.value.opened as OpenedState.Ready
        assertFalse(ready.file.editable)
        vm.startEditing()
        assertFalse((vm.state.value.opened as OpenedState.Ready).editing)
    }

    @Test fun editSave_thenConflictChoices() {
        val vm = viewModel()
        waitFor { vm.state.value.listing is ListingState.Loaded }
        vm.openEntry(FileEntry("README.md", "README.md", false, 10, null))
        waitFor { vm.state.value.opened is OpenedState.Ready }
        vm.startEditing()
        vm.edit("val x = 2", 9)
        vm.save()
        waitFor { (vm.state.value.opened as? OpenedState.Ready)?.editing == false }
        assertEquals("val x = 2", files.content)

        // 開いたあとに他で書き換えられた
        vm.startEditing()
        vm.edit("val x = 3", 9)
        files.hash = "changed"
        vm.save()
        waitFor { (vm.state.value.opened as? OpenedState.Ready)?.conflict == true }
        // 上書き: 最新のハッシュを取り直して書く
        vm.save(overwrite = true)
        waitFor { (vm.state.value.opened as? OpenedState.Ready)?.editing == false }
        assertEquals("val x = 3", files.content)
        assertEquals("changed", files.saves.last().first)
    }

    @Test fun unsavedEdits_survive_asDraft_andBackAsksToDiscard() {
        val vm = viewModel()
        waitFor { vm.state.value.listing is ListingState.Loaded }
        vm.openEntry(FileEntry("README.md", "README.md", false, 10, null))
        waitFor { vm.state.value.opened is OpenedState.Ready }
        vm.startEditing()
        vm.edit("draft text", 5)
        waitFor { drafts.rows.isNotEmpty() }
        assertTrue(vm.back())
        assertTrue(vm.state.value.confirmDiscard)

        // 開き直すと（プロセスが終わったあとも）下書きが戻る
        val again = viewModel()
        waitFor { again.state.value.listing is ListingState.Loaded }
        again.openEntry(FileEntry("README.md", "README.md", false, 10, null))
        waitFor { again.state.value.opened is OpenedState.Ready }
        val restored = again.state.value.opened as OpenedState.Ready
        assertEquals("draft text", restored.editText)
        assertTrue(restored.restoredDraft)

        again.discardAndClose()
        waitFor { drafts.rows.isEmpty() }
    }

    @Test fun listFailure_thenRetry() {
        files.failList = true
        val vm = viewModel()
        waitFor { vm.state.value.listing == ListingState.Failed }
        files.failList = false
        vm.retry()
        waitFor { vm.state.value.listing is ListingState.Loaded }
    }
}
