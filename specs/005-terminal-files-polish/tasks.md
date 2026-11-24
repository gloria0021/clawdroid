# Tasks: ターミナルとファイルの磨き込み

**Input**: `specs/005-terminal-files-polish/`（spec・plan・research・data-model・contracts・quickstart）

**Tests**: 憲章「ViewModel とデータ層には単体テストを書く」に従い、純粋な関数（看板・ツリー・行・名前の検証）と Repository（ファイル操作）のテストを含める。

`M` = `app/src/main/kotlin/jp/miyavi/android/clawdroid`、`T` = `app/src/test/kotlin/jp/miyavi/android/clawdroid`、`R` = `app/src/main/res`

ストーリーの順は優先度順（US1 P1 → US2・US4・US5・US6 P2 → US3 P3）。

## Phase 1: Setup

- [x] T001 [P] `R/drawable/`: Hugeicons Stroke Rounded から足りないアイコンを VectorDrawable にする（色は固定しない）: 貼り付け（clipboard）・ツリー（folder-tree 系）・行へ移る・取り消し・やり直し・折り返し・新しいファイル・新しいフォルダ。既存で足りるもの（フォルダ・ファイル・削除・編集）は使い回す
- [x] T002 [P] `R/values/strings.xml`: contracts/ui.md の「追加する文言」を日本語だけで足す
- [x] T003 [P] `assets/licenses/`: OpenClaw の MIT ライセンス表示を足し、ライセンスの一覧から開けるようにする（research R3）

## Phase 2: Foundational（すべてのストーリーの前提）

- [x] T004 `M/feature/files/FilesViewModel.kt`: ルートの絶対パス（`sessions.files.list` の `root`）を状態に持つ（ファイル操作と、コードの画面のパスの表示に使う）。既存の `FilesViewModelTest` が通ること
- [x] T005 [P] Compose の `TextFieldBuffer.addStyle`（`OutputTransformation`）が BOM 2026.09.00 の foundation で使えるか、小さな試作で確かめ、research R6 に結果を書く（使えなければ `VisualTransformation` の方式で US5 を進める） （使える: foundation 1.12.1 の `TextFieldBuffer.addStyle(SpanStyle, Int, Int)`、`TextFieldState.undoState`）

**Checkpoint**: 既存のターミナルとファイルが今までどおり動く

## Phase 3: User Story 1 — 走っているコマンドをすぐ止める (P1) 🎯 MVP

**Goal**: 「^C」の 1 操作と外付けキーボードの Ctrl+C で割り込みを送る
**Independent Test**: quickstart 4・5

- [x] T006 [US1] `M/feature/terminal/TerminalViewModel.kt`: `SpecialKey.INTERRUPT` を足し、`sendKey` で `\u0003` を直接送る（Ctrl の一時押しを使わない、消費もしない）。シェルが終わっている・つながっていないときは送らない
- [x] T007 [US1] `M/feature/terminal/SpecialKeysRow.kt`: `Esc · Tab · Ctrl · ^C · ← ↑ ↓ →` に並べ替え、行を `horizontalScroll` にする。^C は `TextKey`、TalkBack の説明付き。押せないときは `onSurfaceVariant` 0.6
- [ ] T008 [US1] 実機: Shift なしの外付けキーボードの Ctrl+C が、今の `TerminalView` の既定の処理で ETX になることを確かめる（ならなければ T013 の `onKeyDown` で ETX を送る）

**Checkpoint**: キーボードを閉じたまま、操作 1 回でコマンドが止まる

## Phase 4: User Story 2 — 外付けキーボードでコピーと貼り付け (P2)

**Goal**: Ctrl+Shift+C / V と、特殊キーの行の「貼り付け」
**Independent Test**: quickstart 6

- [x] T009 [US2] `M/feature/terminal/TerminalViewModel.kt`: `paste()` を足す（クリップボードの文字を `bridge.emulator.paste` へ。空・文字でなければ何もしない）。既存の `onPasteTextFromClipboard` も同じ関数を通す
- [x] T010 [US2] `M/feature/terminal/TerminalScreen.kt`: `TerminalViewClient.onKeyDown` で Ctrl+Shift+C（`getSelectedText()` → 無ければ `getStoredSelectedText()`、空なら何もしない、入れたら `stopTextSelectionMode()` と既存の `onCopy`）と Ctrl+Shift+V（T009）を取り、`true` を返す。それ以外は `false`（既定の処理）
- [x] T011 [US2] `M/feature/terminal/SpecialKeysRow.kt`: 行の末尾に「貼り付け」のアイコン（T001）を足す

**Checkpoint**: PC の端末と同じキーでコピーと貼り付けができる

## Phase 5: User Story 4 — ファイルツリーとコードを並べて見る (P2)

**Goal**: その場で開閉するツリー。スマホは引き出すパネル、2 ペインを置ける幅は左に出したまま
**Independent Test**: quickstart 7〜10

- [x] T012 [P] [US4] `M/feature/files/FileTreeState.kt`: data-model の `FileTreeState`・`Listing`・`TreeRow` と、純粋な関数 `flatten(state): List<TreeRow>`（字下げは 8 段で頭打ち）・`toggle`・`onListed`・`onFailed`
- [x] T013 [P] [US4] `T/feature/files/FileTreeStateTest.kt`: 展開・折りたたみ・読み込み中・失敗の行・深さ 9 以上・`expanded` と `selected` の保存と復元（quickstart の単体テスト）
- [x] T014 [US4] `M/feature/files/FilesViewModel.kt`: フォルダの 1 段ずつの移動をやめ、`FileTreeState` を持つ。フォルダを開いたときに初めて一覧を取る（続きのページも読む）。`expanded`・`selected` を `SavedStateHandle` に入れる。ルートの切り替え（002 FR-002）でツリーを作り直す
- [x] T015 [US4] `M/feature/files/FileTree.kt`: ツリーの `LazyColumn`（`key = TreeRow.key`、`animateItem` にトークン）。行は会話のサイドパネルの行の高さ・選んだ行の面にそろえる。開閉の矢印・アイコン・名前（1 行）。ルートの切り替えを頭に置く
- [x] T016 [US4] `M/feature/files/FilesScreen.kt`: `NavDisplay` のページをやめる。`calculatePaneScaffoldDirective(currentWindowAdaptiveInfo()).maxHorizontalPartitions >= 2` なら左にツリー・右にコード、1 なら `ModalNavigationDrawer` でツリーをパネルに（会話の画面の `ModalDrawerSheet` と同じ色と形）。ファイルを押したらパネルを閉じる。ファイルを開いていなければパネルを開いて始める。タイトルバーにツリーを開くボタン（1 ペインのときだけ）とファイル名、その下にフォルダのパス
- [x] T017 [US4] `M/feature/files/FilesScreen.kt`: 戻る操作の順（パネルが開いていれば閉じる → 保存していない変更の確認 → 画面を閉じる）を `BackHandler` で組み直し、予測型「戻る」を壊さない （パネルの戻るは M3 の ModalNavigationDrawer に任せた。読んでいるときの戻るは画面を閉じる）
- [x] T018 [US4] `M/feature/files/FileList.kt` を削除し、参照を T015 に置き換える
- [ ] T019 [US4] 実機: コードの横スクロールとパネルを開くスワイプがぶつからないこと（quickstart 9、FR-045）。ぶつかるなら、折り返さない表示のときだけドロワーのジェスチャーを左端の狭い帯に限る

**Checkpoint**: ツリーでファイルを行き来でき、展開の状態が回転・強制終了を越えて残る

## Phase 6: User Story 5 — コードエディターのように読み書きする (P2)

**Goal**: 行番号・色分け（編集中も）・今の行・折り返し・行へ移る・取り消し / やり直し・記号の行
**Independent Test**: quickstart 11・12

- [x] T020 [P] [US5] `M/feature/files/LineIndex.kt`: data-model の `LineIndex`（LF / CRLF / CR、`lineOf` は二分探索、`gutterDigits`）
- [x] T021 [P] [US5] `T/feature/files/LineIndexTest.kt`: 区切りの 3 種・空・最後の改行の有無
- [x] T022 [US5] `M/feature/files/CodeEditor.kt`（読む）: 行ごとの `LazyColumn`（`key = 行番号`）。左に行番号の列（等幅・`onSurfaceVariant`・右寄せ・幅は `gutterDigits`）。字句の範囲は ViewModel で一度作り、行ごとの `AnnotatedString` は `remember(行番号, 版)`。折り返さないときは全体を 1 つの横スクロールに載せる。検索の一致の色（既存の `withMatches`）を行ごとに当てる
- [x] T023 [US5] `M/feature/files/CodeEditor.kt`（編集）: `BasicTextField(TextFieldState)` に色分け（T005 の結果の方式）。字句の範囲は入力が止まって 150ms 後に裏で作り直す。行番号は `onTextLayout` の各論理行の先頭の位置に描く（折り返した行は先頭だけ）。今の行に `onSurface` 5% の面。5,000 行を超えたら編集中だけ色分けを止める （編集中はいつも折り返す。shortcut のコメントに記録）
- [x] T024 [US5] `M/feature/files/FileViewer.kt`: 既存の `CodeView`・`Editor` を T022・T023 に置き換える（Markdown・HTML のプレビューと画像はそのまま）
- [x] T025 [US5] `M/core/settings/SettingsStore.kt` と `M/feature/files/FileControls.kt`: 折り返しの選択を DataStore に足し、下部のピルに 折り返し、編集中は 取り消し・やり直し を足す（行へ移るは 2026-11-24 に外した）
- [x] T026 [US5] 取り消し / やり直し: `TextFieldState.undoState` を使う。使えない方式に落とした場合だけ `M/feature/files/EditHistory.kt` と `T/feature/files/EditHistoryTest.kt` を作る（data-model） （undoState を使ったので EditHistory は作らない）
- [x] T027 [US5] `M/feature/files/SymbolRow.kt`: 編集中だけ、キーボードの上にガラスのピルで記号の行（横スクロール）。Tab はファイルの先頭の空白の最頻値に合わせる（無ければ 4 の空白）
- [ ] T028 [US5] 実機: 3,000 行のファイルで読む・編集する（SC-008・009）。カクつけば字句の範囲の作り直しを見えている範囲に限る

**Checkpoint**: 行番号付きで読み書きでき、編集中も色分けが残る

## Phase 7: User Story 6 — ファイルを作る・消す・名前を変える (P2)

**Goal**: 使い捨てのシェルで作成・削除・名前の変更。作業フォルダの外へは出ない
**Independent Test**: quickstart 13〜17

- [x] T029 [P] [US6] `M/core/data/FileOps.kt`: data-model の `FileOp`・`FileOpResult`・`validateName`・`isSecretName`、contracts/file-ops-shell.md の 1 行とスクリプトを組み立てる純粋な関数（シングルクォートの扱い、nonce）、結果の印を読む関数（ANSI の除去、エコーされた行と取り違えない）
- [x] T030 [US6] `M/core/data/TerminalRepository.kt`: 使い捨てのシェルの低い層 `runOnce(agentId, line, timeoutMs): String?`（open → input → data を印まで集める → 必ず close）。既存の会話のターミナルの id の記録には触れない
- [x] T031 [US6] `M/core/data/FileOpsRepository.kt`: `run(op, root): FileOpResult`。`availability()` が null のときだけ。`Mutex` で 1 つずつ。時間切れ・切断は `Unknown`
- [x] T032 [P] [US6] `T/core/data/FileOpsRepositoryTest.kt`: 偽の Gateway の `terminal.data` で、終了コード 0〜5・1 の理由・時間切れ・エコーの取り違え、`'` を含む名前、`validateName` の各エラー、`isSecretName` （`T/core/data/FileOpsTest.kt`。本物の sh に流す確認を含む。偽の Gateway での往復は作っていない）
- [x] T033 [US6] `M/feature/files/FilesViewModel.kt`: 作成・名前の変更・削除の操作。操作中の行の印（`pendingOp`）、終わったら親のフォルダを取り直す（`Unknown` のときも）、作ったファイルは編集できる状態で開く、名前を変えたファイルが開いていれば新しい名前で開き直す。保存していない変更があれば先に確かめる。結果は Snackbar のイベント
- [x] T034 [US6] `M/feature/files/FileOpsDialogs.kt`: 新しいファイル / 新しいフォルダ / 名前を変更（名前の欄 1 つ、検証のエラーは欄の下、通るまで確定できない）、削除の確認（フォルダの注記・元に戻せない注記、確定は `error` の色）
- [x] T035 [US6] `M/feature/files/FileTree.kt`: 行の長押しで透過効果のメニュー（`onLongClickLabel` 付き）、パネルの頭に 新しいファイル・新しいフォルダ。ワークスペース・作業フォルダの外・権限が無いときは出さない
- [x] T036 [US6] `M/feature/files/OpenedFileController.kt`: `isSecretName` のファイルの下書きを Room に入れず、メモリだけに持つ。保存できたら下書きを消す（research R8。002 FR-011 の例外としてコメントに残す）

**Checkpoint**: ツリーからファイルを作って書き、名前を変えて消せる。外へは出られない

## Phase 8: User Story 3 — 開いたときに OpenClaw の看板を出す (P3)

**Goal**: 新しく開いたときだけ、桁数に合った形の看板を手元で描く
**Independent Test**: quickstart 1〜3

- [x] T037 [P] [US3] `M/feature/terminal/TerminalBanner.kt`: 本家の `MASCOT_ART`・`WORDMARK_ART` を写し、`bannerShape(columns)`・`bannerLines(columns)`、マスコットを SGR 38;2 で `primary` の RGB にした ANSI の文字列を作る関数
- [x] T038 [P] [US3] `T/feature/terminal/TerminalBannerTest.kt`: 19 / 20 / 46 / 47 / 70 / 71 / 200 桁で形が表どおり、どの行も桁数を超えない
- [x] T039 [US3] `M/feature/terminal/TerminalViewModel.kt`: `start()` で新しく `terminal.open` したときだけ、最初の出力より前に `bridge.append` で看板を流す（`bannerShown` を `SavedStateHandle` に。`reattach`・取り戻しでは出さない） （作業フォルダへ移ったあとの clear の後に描く。来なければ 3 秒で描く）

**Checkpoint**: 開いたときに看板が崩れずに出て、つなぎ直しでは出ない

## Phase 9: Polish

- [x] T040 `AGENTS.md`: 2.7 の表に「ファイルのツリー（スマホは引き出すパネル、広い画面は左に出したまま）」を足し、大画面の出し分けに区画数の判定を使う例外を書く（plan の Complexity Tracking）。2.6 の会話のメニューの並びに「並行開発を終える」を足す
- [x] T041 `specs/002-terminal-file-viewer/spec.md`: 作成・削除・名前の変更の範囲外の前提に「005 で入れた」と注記する
- [ ] T042 AGENTS.md 第4章の確認（ライト / ダーク × スマホ / タブレット × 文字 100% / 200%、`grep -rn "Color(0x\|\.sp\b" app/src/main`）、アニメーター 5x でパネルとツリーの開閉、予測型「戻る」の途中停止
- [ ] T043 quickstart の実機の 17 項目を通す

## Dependencies

- Setup（T001〜003）→ Foundational（T004・T005）→ 各ストーリー
- US1・US2・US3 はターミナルだけで、ファイルのストーリーと独立。US2 は US1 の `SpecialKeysRow` の並べ替え（T007）の後
- US4 → US5（コードの画面はツリーの画面の右 / 全面に載る）→ US6（操作はツリーの行のメニューから）。US6 のデータ層（T029〜T032）は US4 と並行できる
- T005 の結果で T023・T026 の方式が決まる

## Parallel Example

- 最初に並べられる: T001・T002・T003・T005・T012・T013・T020・T021・T029・T032・T037・T038（どれも別のファイルの純粋な部品かテスト）

## Implementation Strategy

1. **MVP**: Setup → Foundational → US1（^C）。ここで 1 度出せる
2. ターミナルを仕上げる: US2 → US3（小さく独立）
3. ファイル: US4 → US5 → US6 の順に 1 ストーリーずつ実機で確かめて進める
4. Polish で AGENTS.md と 002 の文書を合わせる
