package jp.miyavi.android.sumahoclaw.feature.editor

import jp.miyavi.android.sumahoclaw.core.data.FileListing
import jp.miyavi.android.sumahoclaw.core.data.FileRoot
import jp.miyavi.android.sumahoclaw.core.data.FileSource
import jp.miyavi.android.sumahoclaw.core.data.OpenResult
import jp.miyavi.android.sumahoclaw.core.data.OpenedFile
import jp.miyavi.android.sumahoclaw.core.data.PreviewKind
import jp.miyavi.android.sumahoclaw.core.data.SaveResult
import jp.miyavi.android.sumahoclaw.core.database.FileDraftDao
import jp.miyavi.android.sumahoclaw.core.database.FileDraftEntity
import jp.miyavi.android.sumahoclaw.core.text.TextCharset
import jp.miyavi.android.sumahoclaw.feature.files.CodeLanguage
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** エディターの状態の移り変わり（spec 008 data-model。読み込み・下書き・保存・戻る・好み）。 */
@RunWith(RobolectricTestRunner::class)
class EditorViewModelTest {
    private class FakeFiles(var content: String = "fun a() {}\n", var hash: String = "h1") : FileSource {
        val saves = mutableListOf<String>()
        override suspend fun list(root: FileRoot, path: String) = FileListing("", null, emptyList(), false)
        override suspend fun open(root: FileRoot, path: String) =
            OpenResult.Opened(OpenedFile(root, path, path, content.length.toLong(), "text/plain", PreviewKind.TEXT, content, null, hash, null))
        override suspend fun save(file: OpenedFile, content: String, expectedHash: String): SaveResult {
            if (expectedHash != hash) return SaveResult.Conflict
            saves += content
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

    private class FakePrefs : EditorPrefs {
        val wraps = MutableStateFlow(mapOf(true to true, false to false))
        override fun wrap(prose: Boolean): Flow<Boolean> = wraps.map { it.getValue(prose) }
        override suspend fun setWrap(prose: Boolean, wrap: Boolean) { wraps.value = wraps.value + (prose to wrap) }
        override val languages = MutableStateFlow<Map<String, String>>(emptyMap())
        override suspend fun setLanguage(key: String, language: String?) { languages.value = languages.value + (key to language.orEmpty()) }
    }

    private val target = EditTarget.GatewayFile(FileRoot.Session("agent:a:s1", "a"), "src/Main.kt")
    private val files = FakeFiles()
    private val drafts = FakeDrafts()
    private val prefs = FakePrefs()
    private val results = EditorResults()

    private fun create() = EditorViewModel(target, GatewayDocument(files, target, null), drafts, prefs, results) { _, _, _ -> SaveOutcome.Failed(null) }
        .also { vm -> waitFor { vm.state.value.phase == EditorPhase.READY } }

    private fun waitFor(condition: () -> Boolean) = runBlocking {
        withTimeout(5_000) { while (!condition()) { org.robolectric.shadows.ShadowLooper.idleMainLooper(); delay(10) } }
    }

    private fun EditorViewModel.type(text: String) {
        content.value!!.insert(0, 0, text)
        onChanged()
    }

    @Before fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun opens_withLanguageFromNameAndCodeWrapOff() {
        val vm = create()
        val s = vm.state.value
        assertEquals(CodeLanguage.KOTLIN, s.language)
        assertFalse(s.wrap)
        assertFalse(s.canChangeCharset)
        assertEquals(TextCharset.UTF_8, s.format.charset)
        assertEquals("fun a() {}\n", vm.content.value.toString())
    }

    @Test fun typing_writesDraft_andSaveClearsIt() = runBlocking {
        val vm = create()
        vm.type("// ")
        assertTrue(vm.state.value.dirty)
        vm.flushDraft()
        waitFor { drafts.rows[target.draftKey] != null }
        assertEquals("// fun a() {}\n", drafts.rows.getValue(target.draftKey).content)

        val saved = async(start = CoroutineStart.UNDISPATCHED) { results.results.first() }
        vm.save()
        waitFor { !vm.state.value.dirty && !vm.state.value.saving }
        assertEquals("// fun a() {}\n", files.content)
        assertNull(drafts.rows[target.draftKey])
        assertEquals("// fun a() {}\n", (withTimeout(5_000) { saved.await() } as EditorResult.Saved).file.text)
    }

    @Test fun draft_isRestoredOnReopen() {
        drafts.rows[target.draftKey] = FileDraftEntity(target.draftKey, "draft body", "h1", 2, 0)
        val vm = create()
        assertTrue(vm.state.value.restoredDraft)
        assertTrue(vm.state.value.dirty)
        assertEquals("draft body", vm.content.value.toString())
    }

    @Test fun back_withChanges_asks_andDiscardRemovesDraft() {
        val vm = create()
        assertTrue(vm.requestClose())
        vm.type("x")
        assertFalse(vm.requestClose())
        assertEquals(EditorDialog.ConfirmLeave, vm.state.value.dialog)
        vm.flushDraft()
        waitFor { drafts.rows.isNotEmpty() }
        vm.discardAndClose()
        waitFor { drafts.rows.isEmpty() }
    }

    @Test fun conflict_thenOverwrite() {
        val vm = create()
        vm.type("x")
        files.hash = "other"
        vm.save()
        waitFor { vm.state.value.dialog == EditorDialog.Conflict }
        vm.overwrite()
        waitFor { files.saves.isNotEmpty() }
        assertEquals("xfun a() {}\n", files.content)
    }

    @Test fun wrapAndLanguage_areRemembered() {
        val vm = create()
        vm.toggleWrap()
        waitFor { vm.state.value.wrap }
        vm.setLanguage(null)
        waitFor { prefs.languages.value[target.draftKey] == "" }
        // 色分けなし = 文章なので、文章の折り返し（既定でオン）に従う
        assertTrue(vm.state.value.wrap)
    }
}
