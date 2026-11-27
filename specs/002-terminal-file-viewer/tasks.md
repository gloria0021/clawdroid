# Tasks: ターミナルとファイルビューア

**Input**: `specs/002-terminal-file-viewer/`（spec・plan・research・data-model・contracts・quickstart）

**Tests**: 憲章「ViewModel とデータ層には単体テストを書く」に従い、Repository と ViewModel のテストを含める。

`M` = `app/src/main/kotlin/jp/miyavi/android/sumahoclaw`、`T` = `app/src/test/kotlin/jp/miyavi/android/sumahoclaw`

## Phase 1: Setup

- [x] T001 `app/build.gradle.kts` に `libs.termux.terminal.view` と `libs.highlights` を足す（版の定義と jitpack の登録は済み）。`terminal-emulator` が推移で入るかを確かめる
- [x] T002 [P] Hugeicons Stroke Rounded の不足分を `app/src/main/res/drawable/ic_huge_*.xml` に足す（save・keyboard・arrow-up/left/right・image・file-unknown。既存: folder・file・terminal・download・edit・arrow-down） （新しいアイコンは不要だった: 保存 = check、矢印 = arrow-left を回して使う）
- [x] T003 [P] 文言を `app/src/main/res/values/strings.xml` と `values-en/strings.xml` に足す（ファイル・ターミナルの見出し、ルートの切り替え、保存・衝突・捨てる確認・ダウンロード・各エラー、ターミナルの状態、特殊キーの contentDescription）

## Phase 2: Foundational

- [x] T004 `M/core/database/SumahoClawDatabase.kt`: `FileDraftEntity`（`key` PK、`content`、`baseHash`、`cursor`、`updatedAt`）と `FileDraftDao`（get / upsert / delete）を足し、DB 版を 2 に（テーブルの追加だけの移行。`app/schemas` に 2.json を出す）
- [x] T005 [P] `M/core/data/FileModels.kt`: `FileRoot`（`Session(sessionKey, agentId)` / `Workspace(agentId)`）、`FileEntry`、`OpenedFile`（data-model の項目と `editable` の条件「`root is Session && hash != null && previewKind == text && encoding == utf8`」）と、一覧の並び（フォルダが先、名前の自然順・大文字小文字を区別しない）

## Phase 3: User Story 1 — 辿って読む (P1) 🎯 MVP

**Goal**: 会話から作業フォルダ／ワークスペースのファイルを辿り、コードを色分けで、画像を全画面で読める
**Independent Test**: quickstart 1

- [x] T006 [P] [US1] `T/core/data/FilesRepositoryTest.kt`: `sessions.files.list` の browser の解析、`sessions.files.get` の text / image / unsupported、`agents.workspace.list` のページ送り、並び順（FakeGateway）
- [x] T007 [US1] `M/core/data/FilesRepository.kt`: `list(root, path)`（作業フォルダは `sessions.files.list` の `browser`、ワークスペースは `agents.workspace.list` を `offset` で最後まで）、`open(root, path)`（`sessions.files.get` / `agents.workspace.get` → `OpenedFile`）。contracts/gateway-rpc.md の項目だけを読む
- [x] T008 [P] [US1] `M/feature/files/CodeHighlighter.kt`: highlights の結果を `AnnotatedString` に（キーワード = `primary`、文字列 = `tertiary`、コメント = `onSurfaceVariant`、数 = `secondary`）。拡張子から言語を選び、分からなければ色分けしない
- [x] T009 [US1] `M/feature/files/FilesViewModel.kt`: ルート・今の場所・一覧・開いたファイルの状態（不変の data class）、フォルダの移動と 1 段戻る、ルートの切り替え（作業フォルダが無い会話はワークスペースから）、色分けは `Dispatchers.Default` で一度だけ、失敗と再試行
- [x] T010 [P] [US1] `T/feature/files/FilesViewModelTest.kt`: 移動と戻る、ルートの切り替え、開いたファイルの種類ごとの状態、失敗からの再試行
- [x] T011 [US1] `M/feature/files/FileList.kt`: `LazyColumn` ＋ `ListItem`（フォルダ／ファイルのアイコン、名前、大きさ・更新時刻を `labelMedium`）、`key = path`、今の場所の表示、空・読み込み中（プレースホルダー）・失敗（再試行）
- [x] T012 [US1] `M/feature/files/FileViewer.kt`: 読む（等幅、`SelectionContainer`、色分けの `AnnotatedString`、本文の下地の面）、画像は既存の `ImageOverlay`、表示できないファイルは種類・大きさとダウンロードの操作
- [x] T013 [US1] `M/feature/files/FilesScreen.kt`: ViewModel をつなぐ部分と `FilesContent`。共通の TopAppBar（タイトル = 今の場所、ルートの切り替えは `SegmentedButton`）、一覧 ⇄ 開いたファイルの切り替え（新しい側だけフェード）、`BackHandler` で 1 段戻る（根のときは画面の戻る）
- [x] T014 [US1] `M/navigation/AppNavigation.kt`: `FilesRoute` を `FilesScreen` に（`ComingSoon` を外す）。ViewModel の factory に FilesRepository と会話の作業フォルダ（SessionSummary.cwd）を渡す

## Phase 4: User Story 2 — 直して保存 (P2)

**Goal**: 作業フォルダのテキストを編集・保存し、衝突と未保存を扱える
**Independent Test**: quickstart 2

- [x] T015 [P] [US2] `T/core/data/FilesRepositoryTest.kt` に追記: `sessions.files.set` に `expectedHash` と `expectedSource` を送る、拒否を衝突として返す
- [x] T016 [US2] `M/core/data/FilesRepository.kt`: `save(file, content)` → 成功（新しい `hash`）／衝突／失敗
- [x] T017 [US2] `M/feature/files/FilesViewModel.kt`: `Viewing ⇄ Editing → Saving → (Viewing | Conflict | SaveFailed)`、衝突の「上書き」（取り直した `hash` で再保存）と「読み直し」（下書きを捨てて取り直す）、下書きを 400ms 間引いて `file_drafts` に保存・開いたときに戻す、保存の成功で消す
- [x] T018 [P] [US2] `T/feature/files/FilesViewModelTest.kt` に追記: 編集・保存・衝突の 2 つの選択・下書きの復元・編集できないファイルで編集を出さない
- [x] T019 [US2] `M/feature/files/FileViewer.kt`: 編集の表示（`BasicTextField`、等幅、編集中は色分けしない、カーソルの位置を保つ）、保存の操作（TopAppBar の 2 つまでのアイコン）、保存の Snackbar
- [x] T020 [US2] `M/feature/files/FilesScreen.kt`: 捨てる確認の `AlertDialog`（戻る操作・予測型「戻る」で出す）、衝突の `AlertDialog`（上書き／読み直し）

## Phase 5: User Story 3 — ダウンロード (P3)

**Goal**: ファイルを端末の Download に保存し、そこから開ける
**Independent Test**: quickstart 3

- [x] T021 [P] [US3] `M/core/data/FileDownloader.kt`: 接続先の `ws(s)://host[:port]` から `http(s)://` の起点を作り、`sessions.files.download` の相対 `url` を OkHttp で GET → `MediaStore.Downloads` に書く（同じ名前は別名）。ワークスペースは `agents.workspace.get` の内容（utf8 / base64）を書く。書いた `Uri` を返す
- [x] T022 [US3] `M/core/data/FilesRepository.kt`: `downloadLink(root, path)`（`sessions.files.download`）。`session_file_download_unavailable`・HTTP に届かない・大きすぎるを理由つきの失敗にする
- [x] T023 [US3] `M/feature/files/FilesViewModel.kt` と `FileList.kt` / `FileViewer.kt`: 開いたファイルの ⋮ と一覧の長押し（`onLongClickLabel`）からダウンロード、Snackbar に保存先と「開く」（`ACTION_VIEW`）

## Phase 6: User Story 4 — ターミナル (P4)

**Goal**: Gateway の PTY を作業フォルダで開き、特殊キー・つなぎ直し・状態の表示ができる
**Independent Test**: quickstart 4

- [x] T024 [US4] 成立の確認（plan のリスク 1）: Termux の `TerminalSession` を継いで `initializeEmulator` / `write` / `updateSize` を差し替え、端末内のプロセス無しで `TerminalView` に映せるかを試す。無理なら作業を止めてマスターに相談する （机上で成立: `com.termux.terminal.RemoteTerminalBridge` で、プロセス無しの TerminalSession にエミュレータを先に入れ、入力のキューを Gateway へ流す。実機の確認は T034）
- [x] T025 [P] [US4] `T/core/data/TerminalRepositoryTest.kt`: `terminal.open` に `sessionKey` を送らない、`cols`/`rows`、`terminal.data` の `seq` の重なりを捨てる、`attach` の `buffer` から続ける、`terminal.exit` の `reason` ごとの状態、権限が無いときの理由（FakeGateway）
- [x] T026 [US4] `M/core/data/TerminalRepository.kt`: open / input / resize / list / attach / close、イベントの購読、会話ごとの `sessionId` を DataStore に、作業フォルダへの `cd -- '<cwd>' && clear`（中の `'` は `'\''`）
- [x] T027 [US4] `M/feature/terminal/RemoteTerminalSession.kt`: T024 の結果で、受信データを `TerminalEmulator.append`、キー入力と貼り付けを `terminal.input`、大きさの変化を `terminal.resize` （実装は `M/../com/termux/terminal/RemoteTerminalBridge.kt`。Termux のパッケージ内の項目に触るため、ファイルの場所を変えた）
- [x] T028 [US4] `M/feature/terminal/TerminalViewModel.kt`: `Opening / Connected / Reattaching / Exited / Lost / TakenElsewhere / Denied` の移り変わり、アプリに戻ったとき・再接続のときのつなぎ直し、取り戻す・開き直す
- [x] T029 [P] [US4] `M/feature/terminal/SpecialKeysRow.kt`: ガラスのピルに Esc・Tab・Ctrl（次の 1 文字と組み合わせる切り替え）・↑↓←→、各 48dp 以上・`contentDescription`
- [x] T030 [US4] `M/feature/terminal/TerminalScreen.kt`: `TerminalView` を `AndroidView` で、等幅・端末の文字の大きさ、背景と文字色を `surface` / `onSurface`、キーボードの上に収める（`imePadding`）、状態ごとの表示と操作（開き直す・取り戻す・理由）、長押しの選択とコピー
- [x] T031 [US4] `M/navigation/AppNavigation.kt`: `TerminalRoute` を `TerminalScreen` に。会話の作業フォルダとエージェントを渡す

## Phase 7: Polish

- [ ] T032 大画面: ファイルを list-detail（一覧と開いたファイルを左右）に（Material 3 Adaptive）
- [ ] T033 AGENTS.md 第 4 章: ライト/ダーク × スマホ/タブレット × 文字 100%/200%、直書きの grep、禁止事項
- [ ] T034 quickstart の 1〜5 を実機で通し、結果を記録する

## Dependencies

- Setup → Foundational → US1 → US2 → US3（US3 の FileDownloader は US1 の後ならいつでも）
- US4 は Foundational の後なら US1〜3 と独立（T024 が通ってから T027 以降）

## Parallel

- US1: T006・T008・T010 は別ファイルで並行できる
- US4: T025・T029 は T026 と並行できる

## Implementation Strategy

1. MVP = US1（読めるだけでも価値がある）→ 実機で確かめる
2. US2 → US3 → US4 の順に足し、各段階でインストールして確かめる
3. T024 が通らなければ US4 を止め、相談の結果で R6 を改める
