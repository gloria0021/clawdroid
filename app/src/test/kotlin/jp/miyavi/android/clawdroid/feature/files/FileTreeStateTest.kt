package jp.miyavi.android.clawdroid.feature.files

import jp.miyavi.android.clawdroid.core.data.FileEntry
import jp.miyavi.android.clawdroid.core.data.FileListing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileTreeStateTest {
    private fun dir(path: String) = FileEntry(path, path.substringAfterLast('/'), true, null, null)
    private fun file(path: String) = FileEntry(path, path.substringAfterLast('/'), false, 1, null)
    private fun loaded(path: String, vararg entries: FileEntry) = ListingState.Loaded(FileListing(path, null, entries.toList(), false))

    @Test
    fun expandShowsChildren_collapseHidesButKeeps() {
        var tree = FileTreeState().loaded("", loaded("", dir("src"), file("README.md")))
        assertEquals(listOf("src", "README.md"), flattenTree(tree).map { it.key })
        tree = tree.toggle("src")
        assertEquals(listOf("src", "src#loading", "README.md"), flattenTree(tree).map { it.key })
        tree = tree.loaded("src", loaded("src", file("src/a.kt")))
        val rows = flattenTree(tree)
        assertEquals(listOf("src", "src/a.kt", "README.md"), rows.map { it.key })
        assertEquals(1, rows[1].depth)
        tree = tree.toggle("src")
        assertEquals(listOf("src", "README.md"), flattenTree(tree).map { it.key })
        assertTrue(tree.children["src"] is ListingState.Loaded)
    }

    @Test
    fun failedAndEmptyRows_andNeedsLoad() {
        var tree = FileTreeState(expanded = setOf("a")).loaded("", loaded("", dir("a")))
        assertEquals(listOf("a"), tree.needsLoad())
        tree = tree.loaded("a", ListingState.Failed)
        assertEquals("a#failed", flattenTree(tree)[1].key)
        assertEquals(listOf("a"), tree.needsLoad())
        tree = tree.loaded("a", loaded("a"))
        assertEquals("a#empty", flattenTree(tree)[1].key)
    }

    @Test
    fun forgetRemovesSubtree_andIndentCaps() {
        val tree = FileTreeState(expanded = setOf("a", "a/b", "c")).loaded("a/b", loaded("a/b")).forget("a")
        assertEquals(setOf("c"), tree.expanded)
        assertTrue("a/b" !in tree.children)
        assertEquals(8, indentLevel(12))
        assertEquals(3, indentLevel(3))
    }
}
