package jp.miyavi.android.sumahoclaw.feature.files

import jp.miyavi.android.sumahoclaw.feature.editor.EditTarget
import jp.miyavi.android.sumahoclaw.feature.editor.EditorResult
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import jp.miyavi.android.sumahoclaw.core.data.DownloadResult
import jp.miyavi.android.sumahoclaw.core.data.DownloadSource
import jp.miyavi.android.sumahoclaw.core.data.FileEntry
import jp.miyavi.android.sumahoclaw.core.data.FileListing
import jp.miyavi.android.sumahoclaw.core.data.FileRoot
import jp.miyavi.android.sumahoclaw.core.data.FileSource
import jp.miyavi.android.sumahoclaw.core.data.OpenResult
import jp.miyavi.android.sumahoclaw.core.data.OpenedFile
import jp.miyavi.android.sumahoclaw.core.data.PreviewKind
import jp.miyavi.android.sumahoclaw.core.data.SaveResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

    private val files = FakeFiles()
    private val results = MutableSharedFlow<EditorResult>(extraBufferCapacity = 4)
    private fun viewModel(hasWorkingFolder: Boolean = true) = FilesViewModel(
        files, { _, _ -> }, "agent:a:s1", "a", hasWorkingFolder, editorResults = results,
    )

    // 下書きの間引き（delay）を実時間で進めるため、仮想時間のテスト用ではなく素の Unconfined を使う
    @Before fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun waitFor(condition: () -> Boolean) = runBlocking {
        withTimeout(5_000) { while (!condition()) delay(10) }
    }

    private val FilesViewModel.rootListing get() = state.value.tree.children[""]

    @Test fun treeExpandsInPlace_andBackLeavesTheScreen() {
        val vm = viewModel()
        waitFor { vm.rootListing is ListingState.Loaded }
        vm.openEntry(FileEntry("src", "src", true, null, null))
        waitFor { vm.state.value.tree.children["src"] is ListingState.Loaded }
        assertEquals(listOf("src", "src/a.kt", "README.md"), vm.state.value.rows.map { it.key })
        vm.openEntry(FileEntry("src/a.kt", "a.kt", false, 9, null))
        waitFor { vm.state.value.opened is OpenedState.Ready }
        // 読んでいるときの戻るは画面を閉じてよい（ツリーはパネル）
        assertFalse(vm.back())
        // 閉じても中身は残し、開き直しで読み直さない
        vm.toggleFolder("src")
        assertEquals(listOf("src", "README.md"), vm.state.value.rows.map { it.key })
        assertTrue(vm.state.value.tree.children["src"] is ListingState.Loaded)
    }

    @Test fun expandedFoldersAndOpenedFile_surviveProcessDeath() {
        val saved = androidx.lifecycle.SavedStateHandle()
        val vm = FilesViewModel(files, { _, _ -> }, "agent:a:s1", "a", true, saved = saved)
        waitFor { vm.rootListing is ListingState.Loaded }
        vm.toggleFolder("src")
        vm.openEntry(FileEntry("src/a.kt", "a.kt", false, 9, null))
        waitFor { vm.state.value.opened is OpenedState.Ready }
        val again = FilesViewModel(files, { _, _ -> }, "agent:a:s1", "a", true, saved = saved)
        waitFor { again.state.value.tree.children["src"] is ListingState.Loaded && again.state.value.opened is OpenedState.Ready }
        assertEquals(setOf("src"), again.state.value.tree.expanded)
        assertEquals("src/a.kt", again.state.value.opened?.path)
    }

    @Test fun noWorkingFolder_startsInWorkspace_andWorkspaceIsReadOnly() {
        val vm = viewModel(hasWorkingFolder = false)
        assertEquals(workspace, vm.state.value.root)
        waitFor { vm.rootListing is ListingState.Loaded }
        vm.openEntry(FileEntry("NOTES.md", "NOTES.md", false, 3, null))
        waitFor { vm.state.value.opened is OpenedState.Ready }
        val ready = vm.state.value.opened as OpenedState.Ready
        assertFalse(ready.file.editable)
        // 読むだけのファイルではエディターを開かない
        val event = runBlocking { kotlinx.coroutines.withTimeoutOrNull(300) { vm.startEditing(); vm.events.first() } }
        assertNull(event)
    }

    @Test fun edit_opensEditor_andSavedResultReplacesTheFile() {
        val vm = viewModel()
        waitFor { vm.rootListing is ListingState.Loaded }
        vm.openEntry(FileEntry("README.md", "README.md", false, 10, null))
        waitFor { vm.state.value.opened is OpenedState.Ready }
        val opened = runBlocking {
            val event = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { vm.events.first { it is FilesEvent.OpenEditor } }
            vm.startEditing()
            withTimeout(5_000) { event.await() }
        } as FilesEvent.OpenEditor
        assertEquals("README.md", opened.file.path)

        // エディターで保存した中身に差し替わる
        val file = opened.file
        results.tryEmit(EditorResult.Saved(EditTarget.GatewayFile(file.root, file.path), file.copy(text = "val x = 2", hash = "h2")))
        waitFor { (vm.state.value.opened as? OpenedState.Ready)?.file?.text == "val x = 2" }
    }

    @Test fun savedResultForAnotherFile_isIgnored() {
        val vm = viewModel()
        waitFor { vm.rootListing is ListingState.Loaded }
        vm.openEntry(FileEntry("README.md", "README.md", false, 10, null))
        waitFor { vm.state.value.opened is OpenedState.Ready }
        val file = (vm.state.value.opened as OpenedState.Ready).file
        results.tryEmit(EditorResult.Saved(EditTarget.GatewayFile(file.root, "other.md"), file.copy(path = "other.md", text = "x")))
        Thread.sleep(100)
        assertEquals("val x = 1", (vm.state.value.opened as OpenedState.Ready).file.text)
    }

    @Test fun markdown_opensAsPreview_andTogglesToCode_keepingSearch() {
        files.content = "# Title\n\nfirst apple\n\n- apple pie\n- banana\n"
        val vm = viewModel()
        waitFor { vm.rootListing is ListingState.Loaded }
        vm.openEntry(FileEntry("README.md", "README.md", false, 10, null))
        waitFor { vm.state.value.opened is OpenedState.Ready }
        val ready = vm.state.value.opened as OpenedState.Ready
        assertEquals(PreviewFormat.MARKDOWN, ready.preview)
        assertFalse(ready.showCode)
        assertEquals(3, ready.blocks.size) // 見出し・段落・リスト

        vm.openSearch()
        vm.setSearchQuery("APPLE")
        waitFor { (vm.state.value.opened as OpenedState.Ready).search?.matches?.size == 2 }
        vm.moveMatch(1)
        vm.toggleCode()
        val toggled = vm.state.value.opened as OpenedState.Ready
        assertTrue(toggled.showCode)
        assertEquals(1, toggled.search?.current)
        // いまの一致は 3 つ目の塊（リスト）の中
        assertEquals(2, blockIndexOf(toggled.blocks, toggled.search!!.currentOffset!!))

        // 端では反対側に回る。戻るはまず検索を閉じる
        vm.moveMatch(1)
        assertEquals(0, (vm.state.value.opened as OpenedState.Ready).search?.current)
        assertTrue(vm.back())
        assertNull((vm.state.value.opened as OpenedState.Ready).search)
    }

    @Test fun plainText_hasNoPreview() {
        val vm = viewModel()
        waitFor { vm.rootListing is ListingState.Loaded }
        vm.openEntry(FileEntry("src/a.kt", "a.kt", false, 9, null))
        waitFor { vm.state.value.opened is OpenedState.Ready }
        val ready = vm.state.value.opened as OpenedState.Ready
        assertNull(ready.preview)
        assertTrue(ready.showCode)
        vm.toggleCode()
        assertTrue((vm.state.value.opened as OpenedState.Ready).showCode)
    }

    @Test fun search_helpers() {
        assertEquals(listOf(0, 4, 8), findMatches("abc ABC aBc", "abc"))
        assertEquals(listOf(0, 2), findMatches("aaaa", "aa")) // 重ならない
        assertEquals(emptyList<Int>(), findMatches("abc", ""))
        assertEquals(PreviewFormat.HTML, previewFormatOf("index.HTM"))
        assertNull(previewFormatOf("Makefile"))
    }

    @Test fun listFailure_thenRetry() {
        files.failList = true
        val vm = viewModel()
        waitFor { vm.rootListing == ListingState.Failed }
        files.failList = false
        vm.retry()
        waitFor { vm.rootListing is ListingState.Loaded }
    }

    private fun FilesViewModel.collectEvents(): MutableList<FilesEvent> {
        val events = mutableListOf<FilesEvent>()
        kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined).launch { this@collectEvents.events.collect { events += it } }
        return events
    }

    @Test fun refresh_replacesChangedFile_inPlace_andKeepsTheView() {
        val vm = viewModel()
        val events = vm.collectEvents()
        vm.openEntry(FileEntry("README.md", "README.md", false, 10, null))
        waitFor { vm.state.value.opened is OpenedState.Ready }
        vm.openSearch()
        files.content = "val y = 2"
        files.hash = "h9"
        vm.refreshOpened()
        // 読み直すあいだも読み込み中に替えず、今の中身を出したまま
        waitFor { (vm.state.value.opened as? OpenedState.Ready)?.file?.text == "val y = 2" }
        val ready = vm.state.value.opened as OpenedState.Ready
        assertFalse(ready.refreshing)
        assertEquals("h9", ready.file.hash)
        assertTrue(ready.search != null)
        // 変わっていたことを Snackbar で知らせる
        waitFor { events.isNotEmpty() }
        assertEquals(listOf<FilesEvent>(FilesEvent.Refreshed(changed = true)), events)
    }

    @Test fun refresh_tellsWhenNothingChanged() {
        val vm = viewModel()
        val events = vm.collectEvents()
        vm.openEntry(FileEntry("README.md", "README.md", false, 10, null))
        waitFor { vm.state.value.opened is OpenedState.Ready }
        vm.refreshOpened()
        waitFor { events.isNotEmpty() }
        assertEquals(listOf<FilesEvent>(FilesEvent.Refreshed(changed = false)), events)
    }

    @Test fun unreadableFile_changesBySizeOrTime() {
        val apk = OpenedFile(session, "a.apk", "a.apk", 10, "application/vnd.android.package-archive", PreviewKind.UNSUPPORTED, null, null, null, null, updatedAt = 1)
        assertFalse(apk.differsFrom(apk.copy()))
        assertTrue(apk.differsFrom(apk.copy(size = 11)))
        assertTrue(apk.differsFrom(apk.copy(updatedAt = 2)))
        // ハッシュがあれば、そちらを優先する
        assertFalse(apk.copy(hash = "h").differsFrom(apk.copy(hash = "h", updatedAt = 2)))
        assertTrue(apk.copy(hash = "h").differsFrom(apk.copy(hash = "g")))
    }

    @Test fun refreshTree_keepsTheOldListing_whenItFails() {
        val vm = viewModel()
        waitFor { vm.rootListing is ListingState.Loaded }
        files.failList = true
        vm.refreshTree()
        waitFor { !vm.state.value.treeRefreshing }
        assertTrue(vm.rootListing is ListingState.Loaded)
        files.failList = false
        vm.refreshTree()
        waitFor { !vm.state.value.treeRefreshing }
        assertTrue(vm.rootListing is ListingState.Loaded)
    }
}
