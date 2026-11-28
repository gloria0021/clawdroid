# Research: アプリに入れるエディター

調べた元:
- インストール済みの OpenClaw 2026.11.10 の配布物（`dist/sessions-files-*.mjs`・`dist/workspace-files-*.mjs`・`dist/workspace-fs-*.mjs`・`dist/workspace-file-limits-*.mjs`）
- 同梱の gateway-protocol 2026.11.6（`reference/openclaw/packages/gateway-protocol/src/schema/sessions.ts`・`agents-files.ts`）
- このリポジトリの実装（`feature/files/*`・`feature/chat/message/FilePreviewSheet.kt`・`core/data/InboundImages.kt`・`core/database/SumahoClawDatabase.kt`・`SumahoClawApp.kt`・`MainActivity.kt`）
- Sora Editor の公開情報（Maven Central の 0.24.7 の POM、公式ドキュメント、GitHub の README と Issue #883）、Monaco Editor の公式サイト

実機・エミュレーターは、この調査の時点ではつながっていない（`adb devices` は空、system image が無い）。実機で測る項目は R1 の確認の関門（G1〜G6）として [quickstart.md](./quickstart.md) に置いた。

## R1. エディターの部品（いちばん大きな判断）

- **Decision（提案・マスターの承認待ち）**: **Sora Editor（`io.github.rosemoe:editor` 0.24.7）の本体のモジュールだけ**を使う。TextMate・tree-sitter・LSP のモジュールは入れない。Compose の画面には `AndroidView` で埋め込む。色分けは手持ちの `CodeHighlighter` を、Sora の言語の差し込み口につなぐ（R2）
- **Rationale**:
  - **大きいファイル**: 今の編集（Compose の `BasicTextField`）は、1 文字打つたびに全文の配置を作り直す。行ごとに少しずつ作り直す仕組みが無いので、256 KiB を超えると打鍵が目に見えて遅れ、2 MB では実用にならない（今も 5,000 行を超えると編集中の色分けを止めている）。Sora は行ごとに配置・描画し、長いファイルを前提に作られている
  - **折り返さない編集**: `BasicTextField` の複数行は横スクロールを持たない（`CodeEditor.kt` の shortcut のコメント）。Sora は折り返しのオン / オフを持ち、オフのとき横にスクロールして編集できる
  - **要る道具がそろっている**: 行番号、今の行の強調、取り消し / やり直し、検索、記号を入れる窓口（`SymbolChannel`）、選択のつまみと拡大鏡、外部キーボードのショートカット
  - **依存が軽い**: 0.24.7 の POM の依存は `androidx.annotation`・`androidx.collection`・`kotlin-stdlib` だけ。最低の SDK は 21、Java 17 で動く（このアプリは 33 以上・JVM 21）
- **リスクと対処**:
  - **日本語入力**: 変換中の文節の強調が出ない（Issue #883。Gboard が付ける背景色の span を捨てている）。ハードウェアキーボードで文節を区切り直すときに困るが、変換・確定・削除そのものはできる。実機の関門（quickstart の G1）で、ソフトウェアキーボードでの変換・確定・削除・予測の挿入を確かめる。通らなければ採用しない
  - **見た目**: View で作られた部品なので、色は `EditorColorScheme` に `MaterialTheme.colorScheme` から写す（R6）。字体は `codeTextStyle()` と同じ等幅。選択のつまみと拡大鏡は Sora が描くので、色だけを `primary` に合わせる
  - **ライセンス（LGPL-2.1）**: 改変せずに AAR のまま使う。アプリは MIT で GitHub に公開しているので、利用者がライブラリを差し替えて作り直せる（LGPL の条件を満たす）。ライセンスの表示は既存の aboutLibraries が依存から作る。Sora を改変したくなったら、フォークせずに上流へ出す
  - **開発の状況**: README に「Work In Progress」とあり、開発者は実質 1 人。版は `libs.versions.toml` で固定し、上げるときは quickstart を流し直す
  - **AGENTS.md との関係**: 新しい依存を足すので、マスターの承認が要る（termux・highlights と同じ扱い）。自作の部品ではない（2.7 の「自作の部品は相談」には当たらない）
- **Alternatives considered**:
  - **今の `BasicTextField` を伸ばす**: 依存は増えない。ただし大きいファイルの打鍵の遅れと、折り返さない編集が解けない。行ごとに入力欄を分ける作りは、行をまたぐ選択・貼り付け・日本語の変換が壊れる。対象を 256 KiB 以下に絞るなら成り立つので、**G1 が通らなかったときの退路**にする（そのときは spec の 2 MB を 256 KiB に下げる）
  - **Monaco（WebView）**: 公式に「モバイルのブラウザには対応しない」と書かれている。候補から外す
  - **CodeMirror 6（WebView）**: モバイルに対応している。ただし 2 MB の中身を JS との橋渡しで行き来させること、色を CSS に二重に持つこと、WebView の上ではガラスの面（Haze）が中身を透かせないことから外す
  - **Sora の TextMate のモジュール**: 色分けは正確で、行ごとに少しずつ作り直せる。ただし言語ごとの文法ファイルと正規表現のエンジンでアプリが重くなり、ビューアと色の付き方が変わる。ビューアと同じ `CodeHighlighter` を使う方を選ぶ

## R2. 色分け（ビューアと同じ規則を使い回す）

- **Decision**: Sora の言語（`Language`）を 1 つ作り、その解析で手持ちの `CodeHighlighter.spans(fileName, text)` を呼ぶ。返ってきた `TokenSpan`（文字の位置）を、`LineIndex` で行・列に直して Sora の色の区間に詰める。色の ID は `TokenKind` ごとに 1 つ（Sora の既存の ID に当て、足りなければ独自の ID を足す）
  - 解析は Sora の裏の糸で、全文をまとめて行う（打鍵が止まってから。今の `HIGHLIGHT_DEBOUNCE_MS` = 150ms と同じ考え方）
  - 言語は拡張子から決め（`CodeHighlighter.languageOf` / `markupOf`）、手で選び直したらそれを使う（R7）
  - 大きいファイルは編集中だけ色分けをやめる。境目は実機の関門 G2 で決める（今の 5,000 行から、Sora で打鍵が遅れない行数まで上げる）。読み込んだ直後の 1 回だけは色を付ける
- **Rationale**: ビューアとエディターで同じ色になる。新しい文法の資産を持たない
- **Alternatives**: 行ごとの状態を持つ解析（Sora の `AsyncIncrementalAnalyzeManager`）に書き直す → 速いが、`highlights` ライブラリが全文を前提にしているので作り直しになる。必要になったら足す

## R3. 独立した Activity（起動・戻り方・結果の返し方）

- **Decision**: `EditorActivity` を 1 つ足す。`launchMode` は既定（standard）
  - **アプリの中から**: ファイルビューア・添付のシート・コピーしたテキストのシートが、同じタスクの上に開く。戻ると呼び出し元に戻る。予測型「戻る」は、マニフェストの `enableOnBackInvokedCallback="true"`（設定済み）で、アクティビティをまたぐ標準の動き（下の画面がのぞく）になる
  - **ほかのアプリから**: `ACTION_VIEW` / `ACTION_EDIT` を受け、呼び出し元のタスクの上に開く（エディターの一般的な開き方）。戻ると呼び出し元のアプリに戻る
  - **テーマと窓**: `MainActivity` と同じく `enableEdgeToEdge()`、外観の設定に合わせたシステムバー、`SumahoClawTheme`。`windowSoftInputMode="adjustResize"`。この組み立ては `MainActivity` から共通の関数に切り出して両方で使う
  - **保存していない変更**: 変更があるあいだだけ `BackHandler` で戻るを受け、確認の `AlertDialog` を出す（変更が無ければ予測型「戻る」がそのまま効く）
- **中身の受け渡し**: Intent には短い文字列だけを入れる（対象の種類・エージェント・セッション・パス・開く行）。読み込み済みの中身は、`AppContainer` の `EditorHandoff`（プロセスの中のメモリの受け渡し口）に置いて ID だけを渡す。2 MB を Intent に載せると Binder の上限を超えるため
  - エディターは受け渡し口から受け取る。無ければ（プロセスが終わった後など）、下書きか元から読み直す
- **結果の返し方**: `AppContainer` の `EditorResults`（`SharedFlow`）に「保存した」を流す。ファイルビューアの `OpenedFileController` がそれを受けて、開いているファイルを保存後の内容に差し替える。コピーしたテキストは `ChatViewModel` が受けてタイルを差し替える。`ActivityResult` は使わない（呼び出し元の画面が `ViewModel` で、プロセス内の流れで足りる）
- **同じ対象を 2 つ開いた（FR-007）**: アプリの中の流れは「ビューア → エディター」の一本道なので、1 つのタスクの中では重ならない。分割画面や、ほかのアプリから 2 回開いた場合だけ起きる。プロセスの中の `OpenEditors`（対象 → 開いている画面）で見つけたら、前の画面に下書きを書かせて閉じ、新しい画面がその下書きを引き継ぐ。「前に出す」は、タスクをまたぐと確実に行えないため（spec FR-007 を合わせて直す）
- **Gateway への接続**: アプリのプロセスが前にあれば、今までどおり Notifier が接続を決める（`SumahoClawApp` の `ProcessLifecycleOwner`）。エディターは接続を待たない。端末のファイルは接続と関係なく開いて保存できる（FR-072）
- **Alternatives**:
  - Navigation 3 の画面にする → マスターの指定（独立した Activity）と、ほかのアプリから開く入口の両方に合わない
  - `documentLaunchMode="intoExisting"` で、ファイルごとに最近のアプリの項目を分ける → 同じファイルを前に出せるが、戻るとホームに戻る動き（予測型「戻る」もホームへの動き）になり、「戻るとビューアに戻る」に合わない

## R4. Gateway のファイルの文字コードと大きさ（2026.11.10 で確認）

- **読む（`sessions.files.get`）**: 中身を文字として返すのは、**厳密に UTF-8 として読めて、256 KiB（`WORKSPACE_PREVIEW_MAX_BYTES` = 262144）以下**のときだけ（`populateSessionFilePreview`）。そのとき `contentEncoding: "utf8"` とハッシュが付く。UTF-8 でないもの・大きいものは `previewKind: "unsupported"` か中身なしで、ハッシュも無い
  - BOM: `TextDecoder("utf-8", { fatal: true, ignoreBOM: true })` で読むので、**BOM は文字列の先頭に U+FEFF として残る**
- **書く（`sessions.files.set`）**: 中身は文字列で、UTF-8 で書く。**256 KiB を超えると `session_file_too_large`**、NUL を含むと `session_file_unsafe`、元が UTF-8 でなければ `unsafe`。ハッシュが合わなければ `session_file_conflict`
- **エージェントのファイル（`agents.files.set`）**: 文字列で、上限 2 MiB（アプリの `AGENT_FILE_MAX_BYTES`）
- **Decision**:
  - Gateway のファイルは常に UTF-8 として扱う。文字コードの選び直しは出さない（帯には「UTF-8」「UTF-8（BOM）」を示すだけ）。UTF-8 でないファイルは Gateway が文字として渡さないので、今までどおり編集できない（spec US3-5 の「UTF-8 に変えて保存する確認」は起きない。spec を直す）
  - BOM は、読み込むときに先頭の U+FEFF を外して覚え、保存するときに戻す。保存したバイトは元と同じになる
  - 作業フォルダのファイルは 256 KiB を超えて保存できない。保存の前に大きさを数え、超えるなら保存せずに理由を示す（`session_file_too_large` を受けたときも同じ文言）。spec の Edge Cases の「2 MB」は、Gateway の作業フォルダについては 256 KiB と直す
  - 中に NUL を打ち込むことは実質無いが、`session_file_unsafe` を受けたら理由を示す

## R5. 文字コードの判定（端末のファイル・会話の添付）

- **Decision**: 小さな判定を自作する（`core/text/TextDecoding.kt`）。対象は spec FR-040 の範囲だけ
  1. BOM を見る: UTF-8（EF BB BF）・UTF-16LE（FF FE）・UTF-16BE（FE FF）
  2. NUL を含む（UTF-16 の BOM が無い）→ 文字でないとして開かない（FR-044）
  3. 厳密に UTF-8 として読める → UTF-8
  4. Shift_JIS（`windows-31j` があればそれ。Windows で作られたファイルの丸数字・機種依存文字を読むため）と EUC-JP を、それぞれ厳密に読む。片方だけ読めればそれ。両方読めたら、半角カナの割合が少ない方（EUC-JP を Shift_JIS で読むと半角カナだらけになる）
  5. どれも読めない → 開かない（FR-044）。ただし利用者が帯から文字コードを選べば、その文字コードで読み直す（読めない文字は置き換え文字で表示し、保存は止める）
- **保存できない文字**: 保存するときに、元の文字コードで書けない文字（Shift_JIS に絵文字など）があれば保存せず、最初の位置へカーソルを移して理由を示す
- **Rationale**: 判定したい文字コードが 5 種類に限られ、数十行で書けて、単体テストで確かめられる（憲章 VI）
- **Alternatives**: juniversalchardet（依存が増える。日本語以外の推測は要らない）。ICU の文字コードの推測（Android で使えるかを当てにしない）
- **確かめること**: Android の実機で `windows-31j`・`Shift_JIS`・`EUC-JP` の `Charset` が使えるか（quickstart の G4。Robolectric は JVM の文字コードなので当てにならない）

## R6. 見た目（Sora を AGENTS.md に合わせる）

- **Decision**:
  - 色: `EditorColorScheme` を `MaterialTheme.colorScheme` から作る。本文の背景 = `surface`、文字 = `onSurface`、行番号 = `onSurfaceVariant`、行番号の列の背景 = `surface`、今の行 = 本文の下地の色（`bodyContainerColor()`）、選択 = `primary` の薄い色（M3 の文字選択と同じ）、カーソル・つまみ = `primary`、検索の一致 = `tertiaryContainer`、字句の色 = `codeColors(colors)`（ビューアと同じ）。テーマ・外観が変わったら作り直す（Sora は `EditorColorScheme` を 1 つの部品にしか渡せないので、毎回新しく作る）
  - 字体と大きさ: `codeTextStyle()` の字体と大きさを px にして渡す（端末の文字サイズの設定に従う）。行の高さも同じ
  - 区切り線・行番号の列の線・ブロックの線・スクロールバー: 線は描かない（2.4「区切り線を引かない」）。スクロールバーは Sora のものを細く `onSurfaceVariant` で使う（高速スクロールバーは今回作らない）
  - 拡大・縮小のジェスチャー: 切る（文字の大きさは端末とアプリの設定に従う）
  - 補完・自動の字下げ・括弧の自動の閉じ: 切る（spec の範囲外）。改行したときに前の行の字下げを引き継ぐのだけは残す
- **画面の骨格**（[contracts/editor-ui.md](./contracts/editor-ui.md)）: 上は共通のガラスの TopAppBar、下はガラスの情報の帯、キーボードが出たら記号の行（今の `SymbolRow` を一般化）。`docs/design/screens.md` に「エディター」の節を足す

## R7. 好みと下書きの保存

- **折り返し**: `SettingsStore` に、種類（コード / 文章）ごとの 2 つの真偽値を足す。既定はコード = オフ、文章 = オン。種類は「色分けの言語があるか」で決める（md・txt・未知 = 文章）
- **言語の選び直し**: ファイルごとに覚える。`SettingsStore` に「対象の鍵 → 言語」の小さな表（JSON の文字列、最大 200 件で古いものから消す）
- **下書き**: 既存の `file_drafts` を使う。鍵を対象の種類ごとに決め（[data-model.md](./data-model.md)）、文字コード・BOM・改行・言語を足すため、データベースを版 3 にして列を足す（既定値つきの追加だけ）
- **下書きを書く時機**: 打鍵が 1 秒止まったとき、アプリが裏に回ったとき（`onStop`）、画面を閉じるとき。保存したら消す
- **取り消しの履歴**: 構成の変更（回転・折りたたみ・テーマ）では消さない（`Activity` の `configChanges` で作り直しを避けるのではなく、Sora の部品の状態を `ViewModel` に持たせ、作り直した View に戻す）。プロセスの終了を越えては残さない（spec Edge Cases）

## R8. ほかのアプリのファイル（US4）

- **受け口**: `EditorActivity` に `ACTION_VIEW` と `ACTION_EDIT` の intent-filter。スキームは `content` だけ（`file://` はほかのアプリから渡されない）。MIME は `text/*`・`application/json`・`application/xml`・`application/javascript`・`application/x-sh`・`application/x-yaml`・`application/toml`・`application/x-subrip`（字幕）。`application/octet-stream` は入れない（文字でないものまで候補に出るため）
- **読む**: `ContentResolver.openInputStream`。名前と大きさは `OpenableColumns`。2 MB を超えたら開かない
- **書けるか**: 受け取った Intent の `FLAG_GRANT_WRITE_URI_PERMISSION`、または `checkUriPermission(..., MODE_WRITE)`。書けなければ読むだけで開き、保存の代わりに「別の名前で保存」（`ActivityResultContracts.CreateDocument`）
- **書く**: `openOutputStream(uri, "wt")`（`"w"` は切り詰めない提供元があるため）。書く直前に読み直して SHA-256 を比べ、開いたときと違えば衝突を示す（FR-073）
- **許可の寿命**: 一時的な許可は、受け取ったアクティビティが生きているあいだ（プロセスが終わって作り直されても同じ記録なら）続く。`FLAG_GRANT_PERSISTABLE_URI_PERMISSION` が付いていれば `takePersistableUriPermission` で残す。読めなくなったら下書きを「別の名前で保存」できるようにする（FR-074）

## R9. 会話の添付とコピーしたテキスト（US5）

- **添付**: 中身はバイトで取る（`InboundImages` の `bytes(source)`。2 MB を超えるものはシートに「編集」を出さない）。文字コードは R5 で判定する
- **保存**: 既存の `DownloadCenter.startAttachment` と同じ道（MediaStore の Downloads、上部の帯）で書く。初回に得た URI をエディターが覚え、2 回目からはその URI を書き換える（ファイルを増やさない）
- **コピーしたテキスト**: 中身は `ChatViewModel` のメモリにしか無い（`PendingText`。プロセスが終わると入力欄ごと消える）。受け渡し口で渡し、保存したら `EditorResults` で返す。戻ったときに元のタイルが無ければ（入力欄が消えた）、保存の代わりに全文のコピーを出す

## R10. 今のビューアの編集を外す

- **Decision**: `OpenedFileController` から編集の状態（`editText`・`cursor`・`saving`・`conflict` など）と `save` を外し、エディターの側（`EditorViewModel`）に移す。衝突・無い・失敗の判定と、`FilesRepository.save` はそのまま使う
- ビューアの「編集」は、エディターを開くだけにする。作ったばかりのファイル（spec 005 FR-060）も、作ったらエディターを開く
- `CodeEditorField`・`rememberEditorState` は消す。`CodeReader`（読むとき）・`SymbolRow`（一般化して残す）・`indentUnit` は残す
- 既存の下書き（`file_drafts` の `session:` / `agent:` の鍵）は、同じ鍵でエディターが引き継ぐ。更新の前に書いた下書きも失わない
