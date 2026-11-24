# Contract: 使い捨てのシェルで行うファイルの操作

ファイルの作成・削除・名前の変更は、Gateway の `terminal.*` で使い捨てのシェルを開いて 1 回だけ流す（research R7）。
この文書は、アプリが送る 1 行と、シェルが返す結果の形を決める。

## 流れ

| 段 | RPC / イベント | 中身 |
|---|---|---|
| 1 | `terminal.open` | `{ agentId, cols: 200, rows: 24 }`（`sessionKey` は付けない） |
| 2 | `terminal.input` | `{ sessionId, data: <下の 1 行> + "\r" }` |
| 3 | `terminal.data` | 結果の印が来るまで受け取る（最大 15 秒） |
| 4 | `terminal.close` | `{ sessionId }`（成功・失敗・時間切れに関わらず必ず） |

## 送る 1 行

```sh
stty -echo; sh -c '<SCRIPT>' clawdroid-op '<OP>' '<ROOT>' '<ARG1>' '<ARG2>'; printf '\036%s:%s\036\n' '<NONCE>' "$?"
```

- `<SCRIPT>` は下のスクリプト（中の `'` は `'\''` に置き換える）。`<OP>` は `create-file` / `create-folder` / `rename` / `delete`
- `<ROOT>` は `sessions.files.list` が返した作業フォルダの絶対パス
- `<ARG1>` は対象の相対パス（作成なら親のフォルダ、最上位は `.`）。`<ARG2>` は名前（作成・名前の変更）
- 引数はすべてシングルクォートで囲み、中の `'` は `'\''` に置き換える。アプリの名前の検証（data-model）を通ったものだけを渡す
- `<NONCE>` は毎回の乱数（16 桁の英数字）。エコーされたコマンド行には `%s` の書式しか現れないので、本物の印と取り違えない

## スクリプト（POSIX sh）

```sh
op=$1 root=$2 rel=$3 name=$4
real_root=$(cd -P -- "$root" 2>/dev/null && pwd -P) || exit 4
inside() { case "$1/" in "$real_root"/*) return 0 ;; *) return 1 ;; esac; }
cd -P -- "$real_root" || exit 4
case "$op" in
  create-file|create-folder)
    dir=$(cd -P -- "$rel" 2>/dev/null && pwd -P) || exit 5
    inside "$dir" || exit 3
    target="$dir/$name"
    { [ -e "$target" ] || [ -L "$target" ]; } && exit 2
    if [ "$op" = create-file ]; then (set -C; : > "$target") || exit 1
    else mkdir -- "$target" || exit 1; fi ;;
  rename)
    parent=$(dirname -- "$rel"); base=$(basename -- "$rel")
    case "$base" in .|..) exit 3 ;; esac
    dir=$(cd -P -- "$parent" 2>/dev/null && pwd -P) || exit 5
    inside "$dir" || exit 3
    { [ -e "$dir/$base" ] || [ -L "$dir/$base" ]; } || exit 5
    { [ -e "$dir/$name" ] || [ -L "$dir/$name" ]; } && exit 2
    mv -n -- "$dir/$base" "$dir/$name" || exit 1
    if [ -e "$dir/$base" ] || [ -L "$dir/$base" ]; then exit 2; fi ;;
  delete)
    parent=$(dirname -- "$rel"); base=$(basename -- "$rel")
    case "$base" in .|..) exit 3 ;; esac
    dir=$(cd -P -- "$parent" 2>/dev/null && pwd -P) || exit 5
    inside "$dir" || exit 3
    [ "$dir/$base" = "$real_root" ] && exit 3
    { [ -e "$dir/$base" ] || [ -L "$dir/$base" ]; } || exit 5
    if [ -d "$dir/$base" ] && [ ! -L "$dir/$base" ]; then rm -rf -- "$dir/$base" || exit 1
    else rm -f -- "$dir/$base" || exit 1; fi ;;
  *) exit 64 ;;
esac
exit 0
```

- 親のフォルダは `cd -P` で実体に解決してから作業フォルダの実体の下にあるかを確かめる。外を指すシンボリックリンクの先は 3
- 対象そのものがシンボリックリンクなら、名前の変更・削除はリンクだけに効く（`rm -f`・`mv` はリンクを辿らない）。フォルダとして中身ごと消すのは、リンクでない実体のフォルダだけ
- 作業フォルダそのものは消せない（3）。`.`・`..` を対象にした名前の変更・削除も 3
- `cmd && exit N` の形は使わない（条件が偽のとき、その偽がスクリプトの終了コードになるため）。最後は `exit 0`
- 2026-11-24 に手元の `sh`（dash）で、作成・重複・外を指すリンク・`../`・名前の変更・削除・`.` の削除・無い対象を確かめた

## 結果

標準出力の中の `\036<NONCE>:<code>\036` を探す。`<code>` の意味は data-model の `FileOpResult` の表。
`<code>` が 1 のときは、印の直前の標準エラーの最後の空でない行を理由として示す（ANSI の色の並びは取り除く）。

## 時間切れ・切断

印が 15 秒以内に来ない・接続が切れたら `Unknown` とし、`terminal.close` を試みたうえで、親のフォルダを `sessions.files.list` で取り直して結果を確かめる。

## 使える条件

- `TerminalRepository.availability()` が null（`operator.admin` の接続で、Gateway がターミナルを許している）
- ルートが「セッションの作業フォルダ」（ワークスペース・作業フォルダの外では出さない。FR-065）
