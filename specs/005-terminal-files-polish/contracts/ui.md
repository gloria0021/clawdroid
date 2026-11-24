# Contract: 画面

## ターミナル

- 特殊キーの行（下部に浮くガラスのピル。横にスクロール）: `Esc · Tab · Ctrl · ^C · ← ↑ ↓ → · 貼り付け`
  - `^C`: 文字のボタン。TalkBack「割り込み（Ctrl+C）」。押すと `\u0003`
  - 貼り付け: Hugeicons のアイコン。TalkBack「貼り付け」
  - シェルが終わっている・つながっていないときは、`^C`・貼り付けを押せない見た目（`onSurfaceVariant` 0.6）
- 外付けキーボード:
  | キー | 動き |
  |---|---|
  | Ctrl+C | 割り込み（`\u0003`） |
  | Ctrl+Shift+C | 選択をコピー → 選択を解く → Snackbar「コピーしました」。選択が無ければ何もしない |
  | Ctrl+Shift+V | 貼り付け（bracketed paste は端末の状態に従う） |
  | Esc | ESC を送る（割り込みにはしない） |
- 看板: 新しく開いたときだけ、最初の出力の前に描く。形は data-model の `BannerShape`

## ファイル

- 構成
  - 2 ペインを置けるとき: 左にツリー、右にコード。ツリーは出したまま
  - 1 ペインのとき: コードが全面。ツリーは左から引き出すパネル（会話のサイドパネルと同じ見た目）。左端からのスワイプとタイトルバーのボタンで開く。ファイルを押すと閉じる。ファイルを開いていなければ開いた状態で始まる
- ツリーの行（`ListItem` ではなく、会話のサイドパネルの行の高さにそろえる。48dp 以上）
  - 字下げ・開閉の矢印（フォルダ）・アイコン（フォルダ / ファイル）・名前（1 行、はみ出しは省略記号）
  - 選んでいるファイルは選んだ行の面（会話のサイドパネルと同じ）
  - 長押しで透過効果のメニュー（`onLongClickLabel` 付き）: ファイル → 名前を変更・削除 / フォルダ → 新しいファイル・新しいフォルダ・名前を変更・削除
  - パネルの頭（ルートの切り替えの下）に「新しいファイル」「新しいフォルダ」（最上位に作る）
  - 作業フォルダの外・ワークスペース・権限が無いときは、作成・名前の変更・削除を出さない
- コードの画面
  - タイトルバー: ツリーを開くボタン（1 ペインのときだけ）・ファイル名、その下にフォルダのパス（`labelMedium` / `onSurfaceVariant`）
  - 本文: 行番号の列（等幅・`onSurfaceVariant`・右寄せ）＋本文（等幅・色分け）。編集中は今の行に `onSurface` 5% の面
  - 下部のピル（既存）に道具を足す: 折り返し・行へ移る、編集中は 取り消し・やり直し
  - 編集中はキーボードの上に記号の行（ガラスのピル、横スクロール）
- ダイアログ（`AlertDialog`）
  - 新しいファイル / 新しいフォルダ / 名前を変更: 名前の欄 1 つ。検証のエラーは欄の下に。確定は検証を通るまで押せない
  - 削除: 「<名前> を削除しますか？」。フォルダなら「中身もすべて消えます」。「元に戻せません。Git で管理していないファイルは復元できません」。確定のボタンは `error` の色
  - 行へ移る: 数字の欄 1 つ（1〜最終行）

## 追加する文言（`values/strings.xml`、日本語だけ）

ターミナル: `terminal_key_interrupt`（^C）・`terminal_key_interrupt_cd`・`terminal_key_paste_cd`・`terminal_copied`（既存があれば流用）
ファイル: `files_tree_open_cd`・`files_new_file`・`files_new_folder`・`files_rename`・`files_delete`・`files_delete_title`・`files_delete_folder_note`・`files_delete_irreversible`・
`files_name_error_empty`・`files_name_error_slash`・`files_name_error_dot`・`files_name_error_control`・`files_name_error_too_long`・`files_name_error_exists`・
`files_op_created`・`files_op_renamed`・`files_op_deleted`・`files_op_outside`・`files_op_unavailable`・`files_op_not_found`・`files_op_failed`・
`files_wrap`・`files_go_to_line`・`files_go_to_line_title`・`files_undo`・`files_redo`
