# Data Model: ターミナルとファイルの磨き込み

## ターミナル

### SpecialKey（既存の enum に足す）

| 値 | 送るもの |
|---|---|
| `INTERRUPT` | `\u0003`（ETX）。Ctrl の一時押しを使わずに直接 |
| `PASTE` | クリップボードの文字を `TerminalEmulator.paste()` に渡す（bracketed paste は emulator が判断） |

### BannerShape（看板の形）

| 値 | 選ぶ条件（桁数 `columns`） | 行数 |
|---|---|---|
| `SIDE_BY_SIDE` | `columns >= 71` | 10 |
| `STACKED` | `47 <= columns < 71` | 10 + 1（空行）+ 3 |
| `MASCOT_ONLY` | `20 <= columns < 47` | 10 + 1 + 1（「OpenClaw」） |
| `TEXT_ONLY` | `columns < 20` | 1 |

- `bannerLines(columns): List<String>` は純粋な関数。どの行も `columns` を超えない（SC-005 の単体テスト）
- 出すかの印: `TerminalViewModel` の `bannerShown: Boolean`（`SavedStateHandle`）。`start()` で新しく開いたときだけ `false → true`、`reattach` では触らない

## ファイルツリー

### FileEntry（既存。Gateway の一覧の 1 項目をそのまま使う）

| フィールド | 型 | 備考 |
|---|---|---|
| `path` | String | 作業フォルダからの相対パス。ツリーの key |
| `name` | String | |
| `isDirectory` | Boolean | |
| `size` / `updatedAt` | Long? | |

### FileTreeState

| フィールド | 型 | 保持 |
|---|---|---|
| `root` | `FileRoot`（作業フォルダ / ワークスペース。既存） | SavedStateHandle |
| `expanded` | `Set<String>`（フォルダの path） | SavedStateHandle |
| `children` | `Map<String, Listing>` | メモリ（取り直せる） |
| `selected` | `String?`（開いているファイルの path） | SavedStateHandle |
| `pendingOp` | `String?`（操作中の path） | メモリ |

`Listing` = `Loading` | `Loaded(items: List<FileEntry>, more: Boolean)` | `Failed(reason)`

### TreeRow（表示用。`flatten()` の結果）

| フィールド | 型 | 備考 |
|---|---|---|
| `key` | String | `path`、読み込み中・失敗の行は `path + "#loading"` / `"#failed"` |
| `depth` | Int | 字下げは `min(depth, 8) × Spacing.l` |
| `entry` | `FileEntry?` | |
| `kind` | `Folder(expanded)` / `File(selected)` / `Loading` / `Failed` / `More` | |

**状態の移り変わり（フォルダ）**: 閉 →（押す）→ 開・`Loading` →（一覧が届く）→ 開・`Loaded` /（失敗）→ 開・`Failed`（再試行で `Loading`）→（押す）→ 閉（`children` は残す）

## コードエディター

### LineIndex（純粋な関数で作る）

| フィールド | 型 | 備考 |
|---|---|---|
| `starts` | `IntArray` | 各論理行の先頭の文字位置。`\n`・`\r\n`・`\r` を行の区切りとする |
| `lineOf(offset)` | Int | 二分探索 |
| `gutterDigits` | Int | 最大の行番号の桁数 |

### EditorPrefs（DataStore、端末に 1 つ）

| フィールド | 型 | 既定 |
|---|---|---|
| `wrap` | Boolean | `false`（コードは折り返さない） |

### EditHistory（`TextFieldState.undoState` が使えないときだけ）

| フィールド | 型 | 備考 |
|---|---|---|
| `undo` / `redo` | `ArrayDeque<Edit>` | 最大 200 件 |
| `Edit` | `(start, removed, inserted, selectionBefore, selectionAfter)` | 1 秒以内の連続の入力はまとめる |

## ファイルの操作

### FileOp

| 値 | 引数 | シェルでの動き |
|---|---|---|
| `CreateFile(parent, name)` | 親の相対パス・名前 | 上書きしない作成 |
| `CreateFolder(parent, name)` | 同上 | `mkdir` |
| `Rename(path, newName)` | 対象・新しい名前（同じフォルダの中） | 先がなければ `mv` |
| `Delete(path, isDirectory)` | 対象 | `rm -f` / `rm -rf` |

### FileOpResult

| 値 | 終了コード | 画面での示し方 |
|---|---|---|
| `Done` | 0 | Snackbar「作成しました」など、ツリーを取り直す |
| `AlreadyExists` | 2 | 名前の入力に戻り「その名前は使われています」 |
| `OutsideRoot` | 3 | Snackbar「作業フォルダの外には作れません」 |
| `RootUnavailable` | 4 | Snackbar「この Gateway ではできません」（サンドボックスなど） |
| `NotFound` | 5 | Snackbar「見つかりません」、ツリーを取り直す |
| `Failed(message)` | その他 | Snackbar に標準エラーの最後の 1 行 |
| `Unknown` | 時間切れ・切断 | ツリーを取り直して結果を確かめ、変わっていれば成功として示す |

### 名前の検証（アプリ側。`validateName(name): NameError?`）

- 空・空白だけ → `EMPTY`
- `/` を含む → `SLASH`
- `.` または `..` → `DOT`
- NUL・改行・その他の制御文字を含む → `CONTROL`
- UTF-8 で 255 バイトを超える → `TOO_LONG`
- 同じフォルダに同じ名前がある（読み込み済みの一覧で分かる範囲）→ `EXISTS`（最終的な判断はシェルの終了コード 2）

### 下書き（既存の `FileDraftEntity` の扱いを変える）

- `isSecretName(name)` に当たるファイルは Room に入れず、ViewModel のメモリだけに持つ（research R8）
- 一致の規則: `.env`・`.env.*`・`*.pem`・`*.key`・`*.p12`・`*.pfx`・`*.jks`・`*secret*`・`*credential*`・`.netrc`・`.npmrc`・`.pypirc`（大文字小文字を区別しない）
