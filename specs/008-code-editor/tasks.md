# Tasks: アプリに入れるエディター（独立した編集の画面）

**Input**: `specs/008-code-editor/`（spec・plan・research・data-model・contracts・quickstart）

**Tests**: 文字コードの判定・文字数・Intent の書き読み・色分けの橋渡し・下書きの移行は純粋な関数 / Robolectric で単体テストを書く（憲章）。Sora の動きは実機の関門（quickstart G1〜G6）。

`M` = `app/src/main/kotlin/jp/miyavi/android/sumahoclaw`、`T` = `app/src/test/kotlin/jp/miyavi/android/sumahoclaw`、`R` = `app/src/main/res`

Sora Editor の採用はマスター承認済み（2026-11-28。LGPL-2.1、OSS で公開したまま Play に出す前提）。

## Phase 1: Setup

- [x] T001 `gradle/libs.versions.toml`・`app/build.gradle.kts`: `soraEditor = "0.24.7"`、`io.github.rosemoe:editor`（BOM は使わず版を固定）。ビルドが通ること・ライセンスの一覧に LGPL が出ること
- [x] T002 [P] `R/values/strings.xml`: `contracts/editor-ui.md` の文言（日本語だけ）

## Phase 2: Foundational

- [x] T003 [P] `M/core/text/TextDecoding.kt`: BOM・UTF-8 / UTF-16 / Shift_JIS（windows-31j）/ EUC-JP の判定、NUL、改行の判定（LF / CRLF / CR / 混在）、読む（BOM を外し LF にそろえる）・書く（文字コード・BOM・改行を戻す、書けない文字の位置）
- [x] T004 [P] `T/core/text/TextDecodingTest.kt`
- [x] T005 [P] `M/core/text/StatusCounter.kt`: 書記素の数（改行を数えない）・行・列 ＋ `T/core/text/StatusCounterTest.kt`
- [x] T006 `M/core/database/SumahoClawDatabase.kt`: `file_drafts` に `charset`・`bom`・`lineSeparator`・`savedUri`、版 3 と移行 ＋ `T/core/database/EditorDraftMigrationTest.kt`
- [x] T007 [P] `M/core/settings/SettingsStore.kt`: `editor_wrap_code`・`editor_wrap_prose`・`editor_languages`
- [x] T008 [P] `M/feature/editor/EditorHandoff.kt`: `EditTarget`・`EditorHandoff`・`EditorResults`・`OpenEditors`。`M/SumahoClawApp.kt` の `AppContainer` に足す
- [x] T009 [P] `M/feature/editor/EditorIntents.kt` ＋ `T/feature/editor/EditorIntentsTest.kt`（`contracts/editor-intents.md`）
- [x] T010 `M/core/designsystem/AppWindow.kt`: テーマと窓の組み立て（edge-to-edge・システムバー・`SumahoClawTheme`）を `MainActivity` から切り出す

## Phase 3: 関門（spike）

- [x] T011 `M/feature/editor/SoraEditorView.kt`: `AndroidView` の橋渡し（色・字体・折り返し・行番号・今の行・拡大の無効化・記号の口）。`M/feature/editor/EditorActivity.kt` とマニフェストの登録
- [ ] T012 実機で quickstart の G1〜G6。通らなければ止めて相談（research R1 の退路）

## Phase 4: US1 ファイルビューアから開いて保存 (P1) 🎯 MVP

- [x] T013 [US1] `M/feature/editor/EditorSources.kt`: Gateway のファイルの読み込み（受け渡し口 → 下書き → 読み直し）と保存（`FilesRepository.save`、256 KiB / 2 MiB の上限、BOM の戻し）
- [x] T014 [US1] `M/feature/editor/EditorViewModel.kt`: 状態（data-model）、下書きの書き込み（1 秒止まったら・裏に回ったら）、保存・衝突・無い・失敗、戻るときの確認、`EditorResults` への通知
- [x] T015 [US1] `M/feature/editor/EditorScreen.kt`: `EditorContent`（TopAppBar・本文・確認・Snackbar・プレースホルダー）
- [x] T016 [US1] `M/feature/files/*`・`M/feature/agent/*`: 「編集」はエディターを開くだけにする。`OpenedFileController` から編集の状態と `save` を外し、`EditorResults` で差し替える。`CodeEditorField` を消す。新しいファイルを作ったらエディターを開く
- [x] T017 [P] [US1] `T/feature/editor/EditorViewModelTest.kt`（読み込み・保存・衝突・無い・上限・下書きの復元・BOM）。既存のテストのうち編集に関わるものを付け替える

## Phase 5: US2 見た目と道具 (P1)

- [x] T018 [P] [US2] `M/feature/editor/HighlightLanguage.kt`: `CodeHighlighter` → Sora の色の区間 ＋ `T/feature/editor/HighlightBridgeTest.kt`
- [x] T019 [US2] 言語の選び直し（帯とメニュー）・ファイルごとに覚える
- [x] T020 [US2] 折り返しの切り替え（種類ごとの好み）
- [x] T021 [P] [US2] `M/feature/editor/EditorStatusBar.kt`: 情報の帯（選択の文字数、200% で省く順）
- [x] T022 [US2] `M/feature/files/SymbolRow.kt` を一般化し、エディターでキーボードが出ているあいだ出す
- [x] T023 [US2] 検索（既存の `FileSearch` の考え方で、Sora の検索を使う）・外部キーボードの Ctrl+S

## Phase 6: US3 文字コードと改行 (P2)

- [x] T024 [US3] 帯に文字コード・改行。文字コードの選び直し（端末のファイルと添付だけ）、書けない文字・改行の混在の確認

## Phase 7: US4 ほかのアプリのファイル (P2)

- [x] T025 [US4] マニフェストの intent-filter、`LocalDocument` の読み書き（書けるか・`"wt"`・読み直しで衝突）、読むだけのときの「別の名前で保存」、許可が切れたときの下書き

## Phase 8: US5 会話のプレビューから (P3)

- [x] T026 [US5] `FilePreviewSheet` に「編集」。添付のバイトを受け渡し、保存は Downloads（2 回目からは同じ URI）
- [x] T027 [US5] コピーしたテキストのシートに「編集」。`EditorResults` でタイルを差し替え、元が無ければ全文のコピー

## Phase 9: Polish

- [x] T028 `docs/design/screens.md` に「エディター」の節（`contracts/editor-ui.md`）。ファイルビューアの節の「編集中は保存とダウンロード」を直す
- [x] T029 `scripts/translate-strings.py` で訳を作り直す
- [x] T030 `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`、`grep -rn "Color(0x\|\.sp\b" app/src/main`
- [ ] T031 実機で quickstart の受け入れ（1〜9）と AGENTS.md 4

## Dependencies

- Phase 1 → 2 → 3（関門）→ 4（MVP）→ 5 → 6・7（並べて可）→ 8 → 9
- T016（今の編集の撤去）は T013〜T015 ができてから。それまでビューアの編集は残す
- 実機が無いあいだは T012・T031 を後に回し、ほかを進める（関門で退路になったら Phase 3 以降を作り直す）

## Implementation Strategy

- MVP は Phase 4 まで（作業フォルダ・エージェントのファイルを開いて直して保存）。ここで実機の関門を必ず通す
- その後 US2 → US3・US4 → US5 の順に足し、各段階でビルドとテストを通す
