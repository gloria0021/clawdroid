# Implementation Plan: アプリに入れるエディター（独立した編集の画面）

**Branch**: `008-code-editor`（ワークツリー `openclaw/codeeditor` で進め、2026-11-28 に main にマージした） | **Date**: 2026-11-28 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/008-code-editor/spec.md`

## Summary

編集を、ファイルビューアの中の切り替えから、独立した `EditorActivity` に移す。本文の部品は **Sora Editor（本体のモジュールだけ）** を `AndroidView` で埋め込み、色分けはビューアと同じ `CodeHighlighter` をつなぐ。上はガラスの TopAppBar、下はガラスの情報の帯（行:列・文字数・行数・文字コード・改行・言語）、キーボードが出たら今の記号の行。

- **入口**: ファイルビューアの「編集」（作業フォルダ・エージェントのファイル）、会話の添付のシート（保存先は Downloads）、コピーしたテキストのシート（保存先はタイル）、ほかのアプリの「アプリで開く」「編集」（元の URI に書き戻す）
- **保存**: Gateway のファイルは今の `FilesRepository.save`（衝突・無い・失敗の判定もそのまま）。Gateway は UTF-8 しか受け付けず、作業フォルダは 256 KiB まで（2026.11.10 で確認）。端末のファイルと添付は、開いたときの文字コード・BOM・改行で書き戻す
- **受け渡し**: Intent には短い文字列だけ。読み込み済みの中身はプロセス内の `EditorHandoff`、保存の結果は `EditorResults` で呼び出し元に返す
- **今の編集は外す**: `OpenedFileController` の編集の状態と `CodeEditorField` を消し、下書き（`file_drafts`）は同じ鍵で引き継ぐ

調査は [research.md](./research.md)。**Sora Editor を足すことはマスターの承認が要る**（research R1。承認が無ければ、R1 の退路の「今の部品を伸ばし、対象を 256 KiB に絞る」で plan を組み直す）。

## Technical Context

**Language/Version**: Kotlin 2.4.21、JVM 21（001〜007 と同じ）

**Primary Dependencies**: 既存（Compose・M3 Expressive・Haze・Room・DataStore・kotlinx.serialization・highlights）＋ **新規 `io.github.rosemoe:editor` 0.24.7**（LGPL-2.1。依存は androidx.annotation・androidx.collection・kotlin-stdlib だけ。承認待ち）

**Storage**: Room の `file_drafts` を版 3 に（文字コード・BOM・改行・保存先の URI の列を足す）。`SettingsStore` に折り返し 2 つと言語の選び直しの表

**Testing**: JUnit 4 ＋ Robolectric（データベースの移行）。文字コードの判定・文字数・Intent・ViewModel・色分けの橋渡し（[quickstart.md](./quickstart.md)）。Sora の動きは実機の関門 G1〜G6

**Target Platform**: Android 13（API 33）以上

**Project Type**: mobile-app（単一の `app` モジュール）

**Performance Goals**: 「編集」からエディターの中身まで 0.5 秒以内（SC-001。受け渡し口で読み直しを省く）。1 万行・2 MB で打鍵の遅れが見えない（SC-002。実機の関門 G2）

**Constraints**: Gateway のファイルは UTF-8 だけ、作業フォルダは 256 KiB・エージェントのファイルは 2 MiB まで。添付と端末のファイルは 2 MB まで。Intent の extras に本文を載せない。本文を UI の状態に写さない（毎打鍵で 2 MB を作り直さない）

**Scale/Scope**: Activity 1・ViewModel 1・部品の橋渡し（Sora・色分け・色）・文字コードの小さな部品・入口 4 つ。ストーリー 5 本、要件 FR-001〜081

## Constitution Check

| 原則 | 判定 | 根拠 |
|---|---|---|
| I. 動きの心地よさ | 適合 | 打鍵の滑らかさを理由に部品を選ぶ（R1）。色分け・文字数の数え直しは裏の糸で、止まってから。開く動きは標準のアクティビティの出入り。読み込みはプレースホルダーとフェード |
| II. AGENTS.md | 条件付きで適合 | 上・下・記号の行はガラスの共通部品。色は `colorScheme` から写す（R6）。区切り線を描かない。TopAppBar のアイコンは 2 つ ＋ ⋮。**依存の追加（Sora）は承認が要る**。Sora の本文は View で描くが、ガラスの面ではない（本文）ので Haze の対象外で問題ない。上のガラスが Sora を透かせるかは G6 で確かめる |
| III. Gateway が唯一の正 | 適合 | Gateway のファイルは今の `files.set` で書き、衝突の判定は Gateway のハッシュに任せる。UTF-8・256 KiB の制約は Gateway の実装に合わせる（推測で API を作らない） |
| IV. 安全 | 適合 | 外から来た Intent は `content://` の VIEW / EDIT だけを受け、アプリの中の extras は読まない（ほかのアプリが Gateway のファイルを開かせられない）。端末のファイルは渡された許可の範囲だけ。アプリの権限を足さない |
| V. 状態を失わない | 適合 | 下書きは既存の `file_drafts` を版 3 にして引き継ぐ。構成の変更では取り消しの履歴も保つ（R7） |
| VI. 簡潔さ | 適合 | 部品を自作しない（大きいファイル・折り返さない編集は自作では重い）。文字コードの判定は対象が 5 種類なので自作で数十行（R5）。色分け・記号の行・保存・衝突は使い回す |
| VII. 持ち歩く道具 | 適合（半歩外あり） | 中心は「エージェントの出力を小さく直す」。ほかのアプリのファイルは OpenClaw の外だが、マスターの判断（2026-11-28）で入れ、入口を「アプリで開く」だけにしてアプリの画面を増やさない。補完・置換・タブ・差分は範囲外。ビューアの中の編集を外して、直す手段を 1 つにする |

設計後の再確認: 変わらず（II の承認だけが残る）。

## Project Structure

### Documentation (this feature)

```text
specs/008-code-editor/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── editor-intents.md    # アプリの中・外からの開き方
│   └── editor-ui.md         # screens.md に足す「エディター」の節の下書き・文言
├── checklists/
│   └── requirements.md
└── tasks.md                 # /speckit-tasks で作る
```

### Source Code (repository root)

```text
gradle/libs.versions.toml                        # soraEditor = "0.24.7"、sora-editor（承認後）
app/build.gradle.kts                             # implementation(libs.sora.editor)
app/src/main/AndroidManifest.xml                 # EditorActivity と intent-filter
app/src/main/res/values/strings.xml              # editor_* の文言（訳は scripts/translate-strings.py）
docs/design/screens.md                           # 「エディター」の節（contracts/editor-ui.md から）
app/src/main/kotlin/jp/miyavi/android/sumahoclaw/
├── SumahoClawApp.kt                              # AppContainer に EditorHandoff・EditorResults・OpenEditors
├── MainActivity.kt                               # テーマと窓の組み立てを共通の関数に切り出す
├── core/designsystem/AppWindow.kt                # 新規: edge-to-edge・外観に合わせたシステムバー・SumahoClawTheme の組み立て（両 Activity で使う）
├── core/text/
│   ├── TextDecoding.kt                           # 新規: 文字コード・BOM・改行の判定、読み・書き（research R5）
│   └── StatusCounter.kt                          # 新規: 書記素の数・行・列
├── core/database/SumahoClawDatabase.kt           # file_drafts の列を足す、版 3 と移行
├── core/settings/SettingsStore.kt                # editor_wrap_code / editor_wrap_prose / editor_languages
├── feature/editor/
│   ├── EditorActivity.kt                         # 新規: Intent → EditTarget、窓の組み立て、OpenEditors への登録
│   ├── EditorIntents.kt                          # 新規: extras の書き読み（contracts/editor-intents.md）
│   ├── EditorViewModel.kt                        # 新規: 読み込み・保存・下書き・好み・状態（data-model）
│   ├── EditorSources.kt                          # 新規: 対象ごとの読み書き（Gateway・添付・タイル・端末の URI）
│   ├── EditorScreen.kt                           # 新規: *Content（TopAppBar・本文・記号の行・情報の帯・確認）
│   ├── SoraEditorView.kt                         # 新規: AndroidView の橋渡し、色・字体・設定（research R6）
│   ├── HighlightLanguage.kt                      # 新規: CodeHighlighter → Sora の色の区間（research R2）
│   ├── EditorStatusBar.kt                        # 新規: 情報の帯
│   └── EditorHandoff.kt                          # 新規: EditorHandoff・EditorResults・OpenEditors
├── feature/files/
│   ├── OpenedFileController.kt                   # 編集の状態と save を外す。EditorResults を受けて差し替える
│   ├── FilesViewModel.kt                         # OpenedState.Ready から編集の項目を外す
│   ├── FilesScreen.kt / FileViewer.kt            # 「編集」はエディターを開くだけ。編集中の表示を消す
│   ├── CodeEditor.kt                             # CodeEditorField を消す（CodeReader・indentUnit は残す）
│   └── SymbolRow.kt                              # TextFieldState ではなく「入れる・取り消す・やり直す」の口を受けるように一般化
├── feature/agent/AgentFileScreen.kt / AgentFileViewModel.kt   # 同じく「編集」はエディターを開くだけ
├── feature/chat/message/FilePreviewSheet.kt      # 「編集」を足す（2 MB 以下の文字の添付）
└── feature/chat/ChatScreen.kt / ChatViewModel.kt # コピーしたテキストのシートに「編集」、EditorResults でタイルを差し替える
app/src/test/kotlin/jp/miyavi/android/sumahoclaw/
    ├── core/text/TextDecodingTest.kt
    ├── core/text/StatusCounterTest.kt
    ├── core/database/EditorDraftMigrationTest.kt
    ├── feature/editor/EditorIntentsTest.kt
    ├── feature/editor/EditorViewModelTest.kt
    └── feature/editor/HighlightBridgeTest.kt
```

**Structure Decision**: エディターは画面の機能なので `feature/editor` にまとめる。文字コードと文字数は画面に依存しないので `core/text` に置き、単体テストする。Sora に触れるのは `SoraEditorView.kt` と `HighlightLanguage.kt` だけにし、部品を差し替えるとき（R1 の退路）の影響をそこに閉じる。

## 進め方（tasks の下書き）

1. **関門（spike）**: Sora を足し、空の `EditorActivity` に `big.kt` を読ませて quickstart の G1〜G6 を実機で流す。通らなければ止めて、マスターと R1 の退路を相談する
2. **土台**: `AppWindow` の切り出し、`TextDecoding`・`StatusCounter` とテスト、`file_drafts` の版 3、`EditorIntents`・`EditorHandoff`
3. **US1（P1）**: `EditorViewModel`（Gateway のファイルの読み込み・保存・衝突・下書き）・`EditorScreen`・ビューアの「編集」の付け替え・今の編集の撤去・`EditorResults` での差し替え
4. **US2（P1）**: 色分けの橋渡し・言語の選び直し・折り返しの好み・情報の帯・記号の行の一般化・検索・外部キーボード
5. **US3（P2）**: 文字コード・BOM・改行の表示と保持、選び直し、書けない文字・改行の混在の確認
6. **US4（P2）**: intent-filter・`LocalDocument` の読み書き・書けるかの判定・別の名前で保存・衝突
7. **US5（P3）**: 添付のシートの「編集」と Downloads への保存、コピーしたテキストのシートの「編集」
8. **仕上げ**: 文言と訳（`scripts/translate-strings.py`）、`docs/design/screens.md`、quickstart の全項目、AGENTS.md 4 の確認

## Complexity Tracking

| 逸脱 | なぜ必要か | より単純な案を退けた理由 |
|---|---|---|
| Navigation 3 の外に Activity を足す（技術スタックは Navigation 3 で画面を移る） | マスターの指定。ほかのアプリの「アプリで開く」の受け口にもなる | Navigation 3 の画面 → 外からの入口を作れない |
| 新しい依存（Sora Editor、LGPL-2.1） | 2 MB の打鍵の滑らかさと折り返さない編集（憲章 I・FR-012） | 今の `BasicTextField` → 全文の配置を毎打鍵で作り直し、横スクロールを持たない。退路としては残す（R1） |
| 本文が View（Compose ではない） | 上と同じ | 色・字体を `colorScheme` から写す橋渡しを 1 か所に閉じる（R6） |
