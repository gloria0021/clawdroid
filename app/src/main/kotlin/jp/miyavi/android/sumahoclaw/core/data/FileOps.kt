package jp.miyavi.android.sumahoclaw.core.data

/**
 * ファイルの作成・削除・名前の変更（spec 005 US6）。Gateway のファイルの API に無いので、使い捨てのシェルで
 * 短いスクリプトを 1 回だけ流す（research R7、contracts/file-ops-shell.md）。ここは送る 1 行と結果の読み取りだけの純粋な関数。
 */
sealed interface FileOp {
    data class CreateFile(val parent: String, val name: String) : FileOp
    data class CreateFolder(val parent: String, val name: String) : FileOp
    data class Rename(val path: String, val newName: String) : FileOp
    data class Delete(val path: String, val isDirectory: Boolean) : FileOp
    /**
     * ホストの一時置き場（[staged]）にあるアップロードしたファイルを、[parent] に [name] で置く。
     * [overwrite] なら同じ名前のファイルを置き換える（フォルダは置き換えない）。
     */
    data class Place(val parent: String, val name: String, val staged: String, val overwrite: Boolean) : FileOp
}

sealed interface FileOpResult {
    data object Done : FileOpResult
    data object AlreadyExists : FileOpResult
    data object OutsideRoot : FileOpResult
    data object RootUnavailable : FileOpResult
    data object NotFound : FileOpResult
    data class Failed(val message: String?) : FileOpResult
    /** 時間切れ・切断。結果は一覧を取り直して確かめる。 */
    data object Unknown : FileOpResult
}

enum class NameError { EMPTY, SLASH, DOT, CONTROL, TOO_LONG, EXISTS }

/** 名前の検証（アプリ側。シェル側でも作業フォルダの外を断る）。[siblings] は同じフォルダの読み込み済みの名前。 */
fun validateName(name: String, siblings: Collection<String> = emptyList()): NameError? = when {
    name.isBlank() -> NameError.EMPTY
    '/' in name -> NameError.SLASH
    name == "." || name == ".." -> NameError.DOT
    name.any { it.isISOControl() } -> NameError.CONTROL
    name.toByteArray(Charsets.UTF_8).size > MAX_NAME_BYTES -> NameError.TOO_LONG
    name in siblings -> NameError.EXISTS
    else -> null
}

/** 秘密の値を含みそうなファイル名（下書きを端末に残さない。research R8）。 */
fun isSecretName(name: String): Boolean {
    val n = name.substringAfterLast('/').lowercase()
    return n == ".env" || n.startsWith(".env.") || n in SECRET_NAMES ||
        SECRET_SUFFIXES.any(n::endsWith) || "secret" in n || "credential" in n
}

/** シェルの引数（シングルクォートで包み、中の ' は '\'' にする）。 */
internal fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

/** シェルへ送る入力（最後の改行込み）。[root] は作業フォルダの絶対パス。 */
// shortcut: POSIX のシェル（bash・zsh・sh）前提。fish では $? が使えず結果が Unknown になる。fish を使う Gateway が出たら $status にも対応する
fun fileOpCommand(op: FileOp, root: String, nonce: String): String {
    val (name, rel, arg) = when (op) {
        is FileOp.CreateFile -> Triple("create-file", op.parent.ifEmpty { "." }, op.name)
        is FileOp.CreateFolder -> Triple("create-folder", op.parent.ifEmpty { "." }, op.name)
        is FileOp.Rename -> Triple("rename", op.path, op.newName)
        is FileOp.Delete -> Triple("delete", op.path, "")
        is FileOp.Place -> Triple("place", op.parent.ifEmpty { "." }, op.name)
    }
    val extra = (op as? FileOp.Place)?.let { listOf(it.staged, if (it.overwrite) "overwrite" else "keep") }.orEmpty()
    val args = (listOf(name, root, rel, arg) + extra).joinToString(" ", transform = ::shellQuote)
    // 先に履歴のファイルを外す（使い捨てのシェルの入力を、利用者のシェルの履歴に残さない）
    return "unset HISTFILE\rstty -echo; sh -c ${shellQuote(FILE_OP_SCRIPT)} sumahoclaw-op $args; printf '\\036%s:%s\\036\\n' ${shellQuote(nonce)} \"$?\"\r"
}

/** シェルの出力から結果を読む。印がまだ無ければ null（続きを待つ）。 */
fun parseFileOpOutput(output: String, nonce: String): FileOpResult? {
    val marker = Regex("\u001e" + Regex.escape(nonce) + ":(\\d+)\u001e")
    val match = marker.find(output) ?: return null
    return when (val code = match.groupValues[1].toInt()) {
        0 -> FileOpResult.Done
        2 -> FileOpResult.AlreadyExists
        3 -> FileOpResult.OutsideRoot
        4 -> FileOpResult.RootUnavailable
        5 -> FileOpResult.NotFound
        else -> FileOpResult.Failed(
            output.substring(0, match.range.first).replace(ANSI, "").lines().map(String::trim)
                .lastOrNull { it.isNotEmpty() && !it.contains("sumahoclaw-op") } ?: "exit $code",
        )
    }
}

private val ANSI = Regex("\u001b\\[[0-9;?]*[ -/]*[@-~]|\u001b\\][^\u0007]*\u0007|\r")
private const val MAX_NAME_BYTES = 255
private val SECRET_NAMES = setOf(".netrc", ".npmrc", ".pypirc")
private val SECRET_SUFFIXES = listOf(".pem", ".key", ".p12", ".pfx", ".jks")

/** contracts/file-ops-shell.md のスクリプト（2026-11-24 に dash で確かめたもの）。 */
internal val FILE_OP_SCRIPT = """
op=${"$"}1 root=${"$"}2 rel=${"$"}3 name=${"$"}4
real_root=${"$"}(cd -P -- "${"$"}root" 2>/dev/null && pwd -P) || exit 4
inside() { case "${"$"}1/" in "${"$"}real_root"/*) return 0 ;; *) return 1 ;; esac; }
cd -P -- "${"$"}real_root" || exit 4
case "${"$"}op" in
  create-file|create-folder)
    dir=${"$"}(cd -P -- "${"$"}rel" 2>/dev/null && pwd -P) || exit 5
    inside "${"$"}dir" || exit 3
    target="${"$"}dir/${"$"}name"
    { [ -e "${"$"}target" ] || [ -L "${"$"}target" ]; } && exit 2
    if [ "${"$"}op" = create-file ]; then (set -C; : > "${"$"}target") || exit 1
    else mkdir -- "${"$"}target" || exit 1; fi ;;
  rename)
    parent=${"$"}(dirname -- "${"$"}rel"); base=${"$"}(basename -- "${"$"}rel")
    case "${"$"}base" in .|..) exit 3 ;; esac
    dir=${"$"}(cd -P -- "${"$"}parent" 2>/dev/null && pwd -P) || exit 5
    inside "${"$"}dir" || exit 3
    { [ -e "${"$"}dir/${"$"}base" ] || [ -L "${"$"}dir/${"$"}base" ]; } || exit 5
    { [ -e "${"$"}dir/${"$"}name" ] || [ -L "${"$"}dir/${"$"}name" ]; } && exit 2
    mv -n -- "${"$"}dir/${"$"}base" "${"$"}dir/${"$"}name" || exit 1
    if [ -e "${"$"}dir/${"$"}base" ] || [ -L "${"$"}dir/${"$"}base" ]; then exit 2; fi ;;
  delete)
    parent=${"$"}(dirname -- "${"$"}rel"); base=${"$"}(basename -- "${"$"}rel")
    case "${"$"}base" in .|..) exit 3 ;; esac
    dir=${"$"}(cd -P -- "${"$"}parent" 2>/dev/null && pwd -P) || exit 5
    inside "${"$"}dir" || exit 3
    [ "${"$"}dir/${"$"}base" = "${"$"}real_root" ] && exit 3
    { [ -e "${"$"}dir/${"$"}base" ] || [ -L "${"$"}dir/${"$"}base" ]; } || exit 5
    if [ -d "${"$"}dir/${"$"}base" ] && [ ! -L "${"$"}dir/${"$"}base" ]; then rm -rf -- "${"$"}dir/${"$"}base" || exit 1
    else rm -f -- "${"$"}dir/${"$"}base" || exit 1; fi ;;
  place)
    staged=${"$"}5 mode=${"$"}6
    dir=${"$"}(cd -P -- "${"$"}rel" 2>/dev/null && pwd -P) || exit 5
    inside "${"$"}dir" || exit 3
    [ -f "${"$"}staged" ] || exit 5
    target="${"$"}dir/${"$"}name"
    if [ -e "${"$"}target" ] || [ -L "${"$"}target" ]; then
      [ "${"$"}mode" = overwrite ] && [ -f "${"$"}target" ] && [ ! -L "${"$"}target" ] || exit 2
    fi
    # 一時ファイルは作業フォルダの中に、まだ無い名前で新しく作る（mktemp。既にあるリンクの先に書かない）
    tmp=${"$"}(mktemp "${"$"}dir/.sumahoclaw-upload.XXXXXXXX") || exit 1
    cat -- "${"$"}staged" > "${"$"}tmp" || { rm -f -- "${"$"}tmp"; exit 1; }
    chmod "${"$"}(printf '%o' ${"$"}((0666 & ~0${"$"}(umask))))" "${"$"}tmp" 2>/dev/null
    # 置く先は 1 つの名前として確定する（-T。確かめたあとにフォルダやフォルダへのリンクに変わっても、その中に置かない）。
    # -T の無い環境では安全に確定できないので、置かずに失敗にする（CJ レビュー 2026-11-26）
    if [ "${"$"}mode" = overwrite ]; then
      mv -fT -- "${"$"}tmp" "${"$"}target" 2>/dev/null || { rm -f -- "${"$"}tmp"; exit 1; }
    else
      # 上書きしないときは、置く瞬間にも既にある名前を置き換えない（ln は同じ名前があると失敗する）
      if ln -T -- "${"$"}tmp" "${"$"}target" 2>/dev/null; then rm -f -- "${"$"}tmp"
      else rm -f -- "${"$"}tmp"; { [ -e "${"$"}target" ] || [ -L "${"$"}target" ]; } && exit 2; exit 1; fi
    fi
    rm -f -- "${"$"}staged" ;;
  *) exit 64 ;;
esac
exit 0
""".trim()
