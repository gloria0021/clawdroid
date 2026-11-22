# Data Model: ターミナルとファイルビューア

正本は Gateway（憲章 III）。端末に残すのは、保存していないファイルの変更（`file_drafts`）と、会話ごとのターミナルの `sessionId` だけ。

## FileRoot

| 値 | 中身 | 書き込み |
|---|---|---|
| `Session(sessionKey, agentId)` | 会話の作業フォルダ（`sessions.files.*`） | できる（`hash` が付くファイルだけ） |
| `Workspace(agentId)` | エージェントのワークスペース（`agents.workspace.*`） | できない |

## FileEntry（一覧の 1 行）

`path`（ルートからの相対）、`name`、`kind`（`file` / `directory`）、`size?`、`updatedAtMs?`

- 並び: フォルダが先、名前の自然順（大文字小文字を区別しない）
- 一覧の状態: `path`、`parentPath?`、`entries`、`truncated`（作業フォルダ）/ `totalEntries` と `offset`（ワークスペースのページ送り）

## OpenedFile

| 項目 | 由来 | 用途 |
|---|---|---|
| `root`, `path`, `name`, `size`, `mimeType` | get の結果 | 表示 |
| `content` | `previewKind = text` のとき utf8 の文字 | 表示・編集 |
| `image` | `previewKind = image` のとき base64 | 全画面オーバーレイ |
| `previewKind` | `text` / `image` / `unsupported`（内容が無い = 大きすぎるも含む） | 表示の切り替え |
| `hash` | get の `file.hash` | 衝突の確認（無ければ読むだけ） |
| `source` | get の `source` | `expectedSource` |
| `editable` | `root is Session && hash != null && previewKind == text && encoding == utf8` | 編集の操作を出すか（FR-012） |
| `highlighted` | ViewModel で一度だけ作る `AnnotatedString` | 読むときの色分け |

状態の移り変わり: `Loading → Viewing ⇄ Editing → Saving → (Viewing | Conflict | SaveFailed)`。`Conflict` から「上書き」→ 取り直して `Saving`、「読み直し」→ 下書きを捨てて `Loading`

## FileDraftEntity（Room `file_drafts`、DB 版 2）

| 列 | 型 | 説明 |
|---|---|---|
| `key` (PK) | TEXT | `session:<sessionKey>:<path>` |
| `content` | TEXT | 保存していない内容 |
| `baseHash` | TEXT | 編集を始めたときの `hash` |
| `cursor` | INTEGER | カーソルの位置 |
| `updatedAt` | INTEGER | 時刻 |

保存に成功したら消す。「変更を捨てる」でも消す。

## TerminalState

| 項目 | 説明 |
|---|---|
| `sessionId` | Gateway の PTY（会話ごとに 1 つ。`terminal_ids` として DataStore に `会話の key → sessionId` を保存） |
| `cwd`, `shell`, `confined` | open / attach の結果 |
| `cols`, `rows` | 画面から計算（`TerminalView` の大きさ ÷ 文字の大きさ） |
| `lastSeq` | 最後に表示した `seq`（重なりを捨てる） |
| `status` | `Opening` / `Connected` / `Reattaching` / `Exited(exitCode)` / `Lost`（保持時間切れ） / `TakenElsewhere` / `Denied(reason)` |

状態の移り変わり:
- 画面を開く → 保存済みの `sessionId` が `terminal.list` にある → `Reattaching` → `attach` → `Connected`
- 無い → `Opening` → `open`（＋作業フォルダへの `cd`）→ `Connected`
- `terminal.exit` の `reason`: `process_exit` / `closed` → `Exited`、`detached` → `TakenElsewhere`、`disconnected` / `error` → 再接続後に `Reattaching`、見つからなければ `Lost`
- 権限・機能が無い → `Denied`
