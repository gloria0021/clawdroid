# Data Model: アプリに入れるエディター

型の名前は仮。置き場所は [plan.md](./plan.md) の Project Structure。

## EditTarget（編集の対象。Intent と下書きの鍵の元）

```text
sealed interface EditTarget
├── GatewayFile(root: FileRoot.Session | FileRoot.AgentFile, path: String)
├── Attachment(source: String, messageId: String, sessionKey: String, agentId: String?, name: String, mime: String?)
├── PastedText(chatKey: String, textId: String)   # 入力欄のタイル（chatKey = 会話の下書きの鍵）
└── LocalDocument(uri: Uri)                       # ほかのアプリから
```

| 種類 | 下書きの鍵 | 保存先 | 受け付ける文字コード | 上限 |
|---|---|---|---|---|
| GatewayFile（作業フォルダ） | `session:<sessionKey>:<path>`（既存と同じ） | `sessions.files.set` | UTF-8 だけ（BOM は保つ） | 256 KiB（Gateway） |
| GatewayFile（エージェント） | `agent:<agentId>:<path>`（既存と同じ） | `agents.files.set` | UTF-8 だけ | 2 MiB |
| Attachment | `attachment:<messageId>:<source>` | 端末の Downloads（MediaStore） | 開いたときのもの | 2 MB |
| PastedText | `paste:<chatKey>:<textId>` | 入力欄のタイル（メモリ） | UTF-8（文字列のまま） | 既存のタイルと同じ |
| LocalDocument | `uri:<uri>` | 元の URI（書ければ） / 別の名前で保存 | 開いたときのもの | 2 MB |

- ワークスペース（`FileRoot.Workspace`）は読むだけなので対象にしない
- Intent への詰め方は [contracts/editor-intents.md](./contracts/editor-intents.md)

## DocumentFormat（開いた文書の書式）

```text
data class DocumentFormat(
    charset: TextCharset,        # UTF_8 / UTF_16LE / UTF_16BE / SHIFT_JIS / EUC_JP
    bom: Boolean,                # 読むときに外し、書くときに戻す
    lineSeparator: LineSeparator,# LF / CRLF / CR / MIXED
    detected: Boolean,           # 判定したか、利用者が選んだか
)
```

- `MIXED`: 読むときは改行を LF にそろえて Sora に渡し、多い方を覚える。保存の前に「改行を <多い方> にそろえて保存します」と確認する
- そろっているとき（LF / CRLF / CR）は、保存のときにその改行で書き戻す。直していない行のバイトは変わらない（SC-004）

## EditorUiState（EditorViewModel が出す、不変の状態）

```text
data class EditorUiState(
    target: EditTarget,
    title: String,                    # ファイル名
    phase: Loading | Ready | Failed(reason),
    format: DocumentFormat,
    language: CodeLanguage?,          # null = 色分けなし
    languageOverridden: Boolean,
    wrap: Boolean,
    readOnly: Boolean,                # LocalDocument で書けないとき
    dirty: Boolean,                   # 開いた時点 / 最後の保存から変わったか
    saving: Boolean,
    status: StatusLine,               # 行・列・文字数・選択の文字数・行数
    dialog: None | ConfirmLeave | Conflict | Missing | ConfirmLineSeparator | Unmappable(offset) | TooLarge(limit),
    search: FileSearch?,              # 既存の型を使い回す
    restoredDraft: Boolean,
)
```

- 本文そのものは状態に入れない（2 MB を毎打鍵で写さない）。本文は Sora の `Content` が持ち、`EditorViewModel` は部品の状態（`Content`・取り消しの履歴・カーソル・スクロール）を構成の変更をまたいで保つ（research R7）
- `StatusLine` は、カーソル・選択・本文が変わったときに、打鍵の流れとは別に作り直す（文字数の数え直しは裏の糸で、100ms 止まってから）

## StatusLine（情報の帯）

```text
data class StatusLine(line: Int, column: Int, chars: Int, selectedChars: Int?, lines: Int)
```

- `chars`: 書記素（見た目の 1 文字）の数。改行は数えない（FR-031）。`BreakIterator.getCharacterInstance()` で数える
- `column`: 行の頭からの書記素の数 + 1
- `lines`: 論理行の数（最後が改行で終わるときは、その後ろの空の行を数えない）

## 下書き（`file_drafts`、データベースの版 3）

既存の列に足す（既定値つき。版 2 の下書きはそのまま読める）:

| 列 | 型 | 既定 | 用途 |
|---|---|---|---|
| `key` | TEXT（主キー） | | 上の表の鍵 |
| `content` | TEXT | | 本文（BOM を外し、改行は LF） |
| `baseHash` | TEXT | | 開いたときの元のハッシュ（Gateway のハッシュ / 端末のファイルは SHA-256） |
| `cursor` | INTEGER | | カーソル |
| `updatedAt` | INTEGER | | |
| `charset` | TEXT | `'UTF_8'` | 追加 |
| `bom` | INTEGER | `0` | 追加 |
| `lineSeparator` | TEXT | `'LF'` | 追加 |
| `savedUri` | TEXT | `NULL` | 追加。添付を一度保存したときの Downloads の URI |

## エディターの好み（`SettingsStore`）

| 鍵 | 型 | 既定 | 用途 |
|---|---|---|---|
| `editor_wrap_code` | Boolean | false | コードの折り返し |
| `editor_wrap_prose` | Boolean | true | 文章（md・txt・未知）の折り返し |
| `editor_languages` | String（JSON） | `{}` | 対象の鍵 → 選び直した言語。200 件を超えたら古いものから消す |

## EditorHandoff・EditorResults・OpenEditors（AppContainer に 1 つずつ）

```text
EditorHandoff   put(content: HandoffContent): String（ID）/ take(id): HandoffContent?   # プロセスのメモリだけ。取り出したら消す
HandoffContent  = GatewayFile の OpenedFile（本文・ハッシュ・source・workspace）| PastedText の本文 | Attachment のバイト
EditorResults   SharedFlow<EditorResult>
EditorResult    = Saved(target, OpenedFile?) | PasteEdited(chatKey, textId, text)
OpenEditors     register(key, closeAndKeepDraft: () -> Unit) / unregister(key)   # FR-007
```

## 状態の移り変わり

```text
Loading ──読めた──▶ Ready ──保存──▶ Saving ──成功──▶ Ready(dirty=false)
   │                  │                    ├─衝突──▶ Conflict ──上書き / 読み直す──▶ Saving / Loading
   │                  │                    ├─無い──▶ Missing ──作り直して保存 / 捨てる──▶ Saving / 閉じる
   │                  │                    └─失敗──▶ Ready（Snackbar に再試行）
   └─読めない──▶ Failed（文字でない・大きすぎる・許可が無い。理由を示し、下書きがあれば「別の名前で保存」）
```
