package jp.miyavi.android.clawdroid.feature.files

import jp.miyavi.android.clawdroid.core.data.FileEntry

/**
 * ファイルのツリー（spec 005 US4、data-model の FileTreeState）。フォルダはその場で開閉し、開いたときに初めて中身を読む。
 * 表示は [flattenTree] で平らな行の一覧にして 1 つの LazyColumn に渡す（research R4）。
 */
data class FileTreeState(
    /** 開いているフォルダ（ルートは常に開いている）。 */
    val expanded: Set<String> = emptySet(),
    /** フォルダごとの中身（"" がルート）。閉じても残す（開き直しで読み直さない）。 */
    val children: Map<String, ListingState> = emptyMap(),
) {
    fun toggle(path: String): FileTreeState =
        copy(expanded = if (path in expanded) expanded - path else expanded + path)

    fun loading(path: String): FileTreeState = copy(children = children + (path to ListingState.Loading))

    fun loaded(path: String, state: ListingState): FileTreeState = copy(children = children + (path to state))

    /** 開いているのに中身が無い・失敗したフォルダ（読み直す対象）。 */
    fun needsLoad(): List<String> = (listOf("") + expanded).filter { children[it] == null || children[it] == ListingState.Failed }

    /** フォルダの中身を捨てて読み直させる（ファイルの操作のあと）。 */
    fun invalidate(path: String): FileTreeState = copy(children = children - path)

    /** 名前の変更・削除で無くなったパスと、その下を外す。 */
    fun forget(path: String): FileTreeState = copy(
        expanded = expanded.filterNot { it == path || it.startsWith("$path/") }.toSet(),
        children = children.filterKeys { it != path && !it.startsWith("$path/") },
    )

    /** 読み込み済みの、あるフォルダの中の名前（同じ名前の確認に使う）。 */
    fun namesIn(path: String): List<String> =
        (children[path] as? ListingState.Loaded)?.listing?.entries?.map { it.name }.orEmpty()

    /** 読み込み済みの一覧にある、あるパスの項目。 */
    fun entry(path: String): FileEntry? =
        (children[path.substringBeforeLast('/', "")] as? ListingState.Loaded)?.listing?.entries?.firstOrNull { it.path == path }
}

/** ツリーの 1 行。 */
sealed interface TreeRow {
    val key: String
    val depth: Int

    data class Item(val entry: FileEntry, override val depth: Int, val expanded: Boolean) : TreeRow {
        override val key: String get() = entry.path
    }
    data class Loading(val parent: String, override val depth: Int) : TreeRow {
        override val key: String get() = "$parent#loading"
    }
    data class Failed(val parent: String, override val depth: Int) : TreeRow {
        override val key: String get() = "$parent#failed"
    }
    data class Empty(val parent: String, override val depth: Int) : TreeRow {
        override val key: String get() = "$parent#empty"
    }
    data class Truncated(val parent: String, override val depth: Int) : TreeRow {
        override val key: String get() = "$parent#truncated"
    }
}

/** 開いているフォルダの中身を深さ付きで並べる。 */
fun flattenTree(tree: FileTreeState): List<TreeRow> = buildList {
    fun walk(path: String, depth: Int) {
        when (val listing = tree.children[path]) {
            null, ListingState.Loading -> add(TreeRow.Loading(path, depth))
            ListingState.Failed -> add(TreeRow.Failed(path, depth))
            is ListingState.Loaded -> {
                if (listing.listing.entries.isEmpty()) add(TreeRow.Empty(path, depth))
                listing.listing.entries.forEach { entry ->
                    val open = entry.isDirectory && entry.path in tree.expanded
                    add(TreeRow.Item(entry, depth, open))
                    if (open) walk(entry.path, depth + 1)
                }
                if (listing.listing.truncated) add(TreeRow.Truncated(path, depth))
            }
        }
    }
    walk("", 0)
}

/** 字下げの段数（深い階層でも画面幅を食い尽くさない）。 */
fun indentLevel(depth: Int): Int = depth.coerceAtMost(MAX_INDENT)

private const val MAX_INDENT = 8
