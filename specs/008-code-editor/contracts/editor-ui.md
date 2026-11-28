# Contract: エディターの画面（`docs/design/screens.md` に足す「エディター」の節の下書き）

実装と同じ変更で、下の「screens.md に足す文」を `docs/design/screens.md` に写す。

## 骨格

```text
┌ ガラスの TopAppBar（共通部品）──────────────────────┐
│ ←  FileName.kt                  [保存] [検索]  ⋮ │
├──────────────────────────────────────────────────┤
│  1 │ package jp.miyavi…                          │  ← Sora（背景 surface、行番号 onSurfaceVariant）
│  2 │                                             │     今の行は本文の下地の色
│ …  │                                             │
├──────────────────────────────────────────────────┤
│ ⟲ ⟳ │ ⇥ ( ) { } [ ] …        ← 記号の行（キーボードが出ているときだけ。ガラスのピル） │
│ 12:5 · 1,234 文字 · 88 行 · UTF-8 · LF · Kotlin   ← 情報の帯（ガラス。常に出す）   │
└──────────────────────────────────────────────────┘
```

## screens.md に足す文

## エディター（spec 008）

- 独立した Activity。上は共通のガラスの TopAppBar（タイトル = ファイル名、`titleMedium` 2 行まで）
- TopAppBar のアイコン: 保存と検索。⋮ のメニュー: 折り返し（チェック付き）・言語・文字コード（端末のファイルと添付だけ）・ダウンロード（Gateway のファイル）または 別の名前で保存（端末のファイル）
  - 保存は、変更が無いあいだは押せない。保存中は `SaveStateIcon` で示す
  - 読むだけで開いた端末のファイルは、保存の代わりに「別の名前で保存」
- 本文は Sora Editor（research R1）。背景 `surface`、行番号 `onSurfaceVariant`（論理行の先頭だけ、横スクロールしても左に留める）、今の行は本文の下地の色。区切り線・ブロックの線は描かない。字句の色はビューアと同じ（`codeColors`）
- 下に情報の帯（ガラスの面。`SURFACE_OPACITY`、高さは 32dp 以上）。`labelMedium` / `onSurfaceVariant` で「行:列 · 文字数 · 行数 · 文字コード · 改行 · 言語」。選んでいるあいだは文字数を「選択 n 文字」に替える
  - 文字コードと言語は押せる（押すとガラスのドロップダウンで選び直す。それぞれ 32dp 以上の押せる範囲）
  - 文字が 200% で収まらないときは、言語 → 改行 → 文字コード → 行数の順に省き、文字数は残す
- キーボードが出ているあいだは、帯の上に記号の行（今の `SymbolRow`。左に取り消し・やり直し、区切って Tab と記号）
- 確認は `AlertDialog`（保存していない変更・衝突・無い・改行をそろえる・保存できない文字）。保存の成否は Snackbar
- 予測型「戻る」: 変更が無ければアクティビティをまたぐ標準の動き。変更があるあいだは確認を出す（そのあいだは「戻る」ののぞき見が出ない）

## 動き

- 開くとき: アクティビティの標準の出入り（独自の遷移を作らない）。中身の読み込みが要るときは、本文の面にプレースホルダー（行の形の帯）を出し、そろったらフェードで重ねる（AGENTS.md 2.8）
- 折り返しの切り替え・言語の選び直し: 動かさずに描き直す（表示の切り替え）
- 検索の一致へ移る: 利用者が送ったときだけ動かす（Sora のスクロール）

## 文言（`strings.xml` に足すもの。案）

- `editor_launcher_label`「SumahoClaw エディター」
- `editor_save_cd`「保存」、`editor_search_cd`「検索」
- `editor_menu_wrap`「折り返す」、`editor_menu_language`「言語」、`editor_menu_charset`「文字コード」、`editor_menu_save_as`「別の名前で保存」
- `editor_status_selected`「選択 %1$s 文字」、`editor_status_chars`「%1$s 文字」、`editor_status_lines`「%1$s 行」
- `editor_leave_title`「保存していない変更があります」、`editor_leave_save`「保存する」、`editor_leave_discard`「捨てる」、`editor_leave_keep`「続ける」
- `editor_line_separator_confirm`「改行を %1$s にそろえて保存します」
- `editor_unmappable`「%1$s では保存できない文字があります」
- `editor_too_large`「%1$s を超えるので保存できません」
- `editor_not_text`「文字のファイルとして開けませんでした」
- `editor_read_only`「このファイルは書き換えられません。別の名前で保存できます」
- `editor_paste_gone`「元のタイルがありません。全文をコピーできます」
- 衝突・無い・失敗は、今のビューアの文言を使い回す
