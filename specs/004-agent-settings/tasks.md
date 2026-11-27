# Tasks: エージェントの設定（概要・ファイル・スキル）

**Input**: `specs/004-agent-settings/`（spec・plan・research・data-model・contracts・quickstart）

**Tests**: 憲章「ViewModel とデータ層には単体テストを書く」に従い、Repository・Controller・ViewModel のテストを含める。

`M` = `app/src/main/kotlin/jp/miyavi/android/sumahoclaw`、`T` = `app/src/test/kotlin/jp/miyavi/android/sumahoclaw`、`R` = `app/src/main/res`

## Phase 1: Setup

- [x] T001 [P] `R/drawable/ic_huge_skill.xml`: Hugeicons Stroke Rounded の公式データからスキルのタブ用のアイコンを 1 つ VectorDrawable にする（色は固定しない）。概要 = 既存の `ic_huge_info`、ファイル = 既存の `ic_huge_file`
- [x] T002 [P] `R/values/strings.xml`: 文言を日本語だけで足す（`agent_settings_*`: タブ・見出し・概要の行・ワークスペースを開く・名前と雰囲気を直す・Gateway 全体の注記・使えません・失敗と再試行、`agent_settings_open_cd`、`agent_file_role_*` 7 件、`agent_file_not_created`・`agent_file_too_large`、`skill_state_*` 3 件、`skill_missing_*`（コマンド・どれかのコマンド・環境変数・設定・OS・許可リスト・エージェントの絞り込み）、`skill_toggled_on` / `off`・`skill_undo`・`skill_toggle_failed`、`skill_search_cd`）

## Phase 2: Foundational（すべてのストーリーの前提）

- [x] T003 `M/feature/files/OpenedFileController.kt`: `FilesViewModel` の開いたファイルの状態（読む・プレビューとコードの切り替え・検索・編集・保存・上書き・読み直し・下書きの保存と復元）を、`CoroutineScope`・`FileSource`・`FileDraftDao`・下書きの鍵の関数を受け取る普通のクラスに切り出す。状態の型は既存の `OpenedState` のまま。保存できた・失敗のイベントを `Flow` で出す
- [x] T004 `M/feature/files/FilesViewModel.kt`: 開いたファイルの処理を T003 に任せる（公開する関数と振る舞いは変えない）
- [x] T005 `M/feature/files/FilesScreen.kt`: `FilePage` を `FilesUiState` ではなく `OpenedState` と操作のラムダを受ける `internal` の部品にする
- [ ] T006 `T/feature/files/FilesViewModelTest.kt` と実機: 002 の既存テストが変更なしで通ること、実機で 002 の保存・衝突・下書きを 1 回ずつ確かめる（plan のリスク 1。壊れたら切り出しをやめ、plan に記した代わりのやり方に切り替える） （テストは変更なしで 10 件通過。実機の確認はまだ）
- [x] T007 [P] `T/feature/files/OpenedFileControllerTest.kt`: 開く → 編集 → 保存、衝突 → 上書き / 読み直し、閉じたときの下書き、下書きからの復元、編集できないファイル
- [x] T008 [P] `M/core/data/Models.kt` と `M/core/data/SessionsRepository.kt`: `Agent` に `createdVia`・`creatorAgentId`・`createdAt` を足し、`agents.list` から読む（無ければ null）。`T/core/data/ParseTest.kt` に 1 件
- [x] T009 `M/navigation/AppNavigation.kt`: `AgentSettingsRoute(agentId)`・`AgentFileRoute(agentId, name)` を足す（中身は仮の画面。`rememberNavGate` を使う既存の型） （仮の画面を置かず、本物の画面で足した）

**Checkpoint**: 002 が今までどおり動き、新しい行き先が開ける

## Phase 3: User Story 1 — 指示のファイルを読んで直す (P1) 🎯 MVP

**Goal**: サイドパネルとアバターから設定を開き、AGENTS.md などを読む・直す・作る・衝突を扱う
**Independent Test**: quickstart 1・2・4〜7

- [x] T010 [US1] `M/core/data/FileModels.kt`: `FileRoot.AgentFile(agentId)`、`AgentFileEntry`・`AgentFileRole`（名前 → 役割、並び順）、`OpenedFile` に `workspace`・`creating`、`editable` の条件（data-model）
- [x] T011 [US1] `M/core/data/FilesRepository.kt`: `agents.files.list` → `List<AgentFileEntry>`、`open(AgentFile, name)`（`agents.files.get`。`missing` なら空の内容で `creating = true`）、`save`（`expectedHash` か `expectedMissing`、`expectedWorkspace`。`agent_file_conflict` → `Conflict`、`details.currentHash` を上書きに使う。無ければ読み直してから上書き）。ホストのパスは画面に渡さない
- [x] T012 [P] [US1] `T/core/data/FilesRepositoryTest.kt`: 一覧の解析と並び順、未作成の取得、保存の条件の組み立て（既存 / 未作成）、衝突の判定（偽の Gateway）
- [x] T013 [US1] `M/feature/agent/AgentFileViewModel.kt`: `OpenedFileController` を `FileRoot.AgentFile` と下書きの鍵 `agent:<agentId>:<name>` で持つ。未作成なら編集から始める
- [x] T014 [US1] `M/feature/agent/AgentFileScreen.kt`: 共通の TopAppBar（タイトル = ファイル名）、T005 の `FilePage`、`FileControls`、衝突の `AlertDialog`、捨てる確認、保存の Snackbar。2 MiB を超えたら編集のボタンを出さず理由を 1 行
- [x] T015 [US1] `M/feature/agent/AgentSettingsViewModel.kt`: `agentId` からエージェント（`agents.list` の既存の流れから取る）とファイルの一覧を読む。失敗・手段が無い（`METHOD_NOT_FOUND`）を区別する。タブの状態は持たない（画面側の `rememberSaveable`）
- [x] T016 [US1] `M/feature/agent/AgentSettingsScreen.kt`: ViewModel をつなぐ部分と `AgentSettingsContent`。共通の TopAppBar（タイトル = エージェント名）、下部に浮く `PillTabs`（概要・ファイル・スキル。概要とスキルはこの段階では仮）、選んだタブだけを描いて `defaultEffectsSpec()` でフェード、タブとスクロール位置を `rememberSaveable`
- [x] T017 [US1] `M/feature/agent/AgentFileList.kt`: `LazyColumn` ＋ `ListItem`（key = name）、役割・大きさ・更新日時、未作成の表示（`expectedAbsent` で色を分ける）、プレースホルダー・失敗・使えません。押すと `AgentFileRoute`
- [x] T018 [US1] `M/feature/chat/sidebar/SidebarContent.kt`: 看板の名前を `Row` にし、右に `IconButton`（`ic_huge_edit`、48dp、`agent_settings_open_cd`）。`onOpenAgentSettings(agent)` を足す
- [x] T019 [US1] `M/feature/chat/message/Messages.kt`: 応答の横の `AgentAvatar` に `clickable(onClickLabel = …)` を付け、`onOpenAgentSettings(agentId)` を呼ぶ（生成中の波は変えない）
- [x] T020 [US1] `M/feature/chat/ChatScreen.kt` と `M/navigation/AppNavigation.kt`: T018・T019 をつなぐ（サイドパネルは閉じてから開く）。T009 の仮の画面を本物に
- [x] T021 [P] [US1] `T/feature/agent/AgentSettingsViewModelTest.kt`: ファイルの一覧の読み込み・失敗・手段が無い

**Checkpoint**: US1 だけで、サイドパネルから AGENTS.md を直して保存できる

## Phase 4: User Story 2 — 概要を見る (P2)

**Goal**: 開いたときにエージェントの顔・名前・モデル・経緯・数が一目で分かる
**Independent Test**: quickstart 3

- [x] T022 [US2] `M/feature/agent/AgentSettingsViewModel.kt`: 概要の値（モデル・経緯・ファイルの数・スキルの数。スキルは US3 の読み込みが無ければ出さない）
- [x] T023 [US2] `M/feature/agent/AgentOverview.kt`: アバター（大）・名前（`headlineSmall`）・絵文字、「名前と雰囲気を直す」→ `AgentFileRoute(agentId, "IDENTITY.md")`、`ListItem` の縦の並び（モデル・経緯（無ければ出さない）・ファイル N 件 → タブ・スキル N / M → タブ・ワークスペースを開く → `FilesRoute` をワークスペースのルートで）
- [x] T024 [US2] `M/navigation/AppNavigation.kt`: ワークスペースを開く行き先（既存の `FilesRoute` に「ワークスペースから開く」の指定が無ければ足す） （会話に紐付かない `WorkspaceFilesRoute(agentId)` を足した。`FilesRoute` の会話ごとの入れ物を使わないため）

## Phase 5: User Story 3 — スキルを確かめ、有効・無効を切り替える (P2)

**Goal**: 使えない理由が 1 回押すだけで分かり、止める・戻すがすぐ効く
**Independent Test**: quickstart 8〜10

- [x] T025 [US3] マスターの Gateway の `skills.status` の実際の応答を 1 件取り、個人の情報（パス・名前）を伏せて `T` のテストデータにする（plan のリスク 2） （85 件。形は research R4 のとおり）
- [x] T026 [US3] `M/core/data/Skills.kt`: `SkillStatus`・`SkillState`・`SkillMissing` と解析（知らない項目は無視、`eligible`・`disabled` が無ければ状態を決めず切り替えを出さない）、並び順、`SkillsRepository`（`status(agentId)`・`readSkillFile(agentId, filePath)`・`setEnabled(skillKey, enabled)`）
- [x] T027 [P] [US3] `T/core/data/SkillsTest.kt`: T025 のデータの解析、状態の決め方、並び順、`skills.update` の引数（`apiKey`・`env` を送らない）
- [x] T028 [US3] `M/feature/agent/AgentSettingsViewModel.kt`: スキルの読み込み、切り替えの先出し（`pendingToggles`）→ 成功で読み直し / 失敗で戻して理由のイベント、「元に戻す」、絞り込みの語
- [x] T029 [P] [US3] `T/feature/agent/AgentSettingsViewModelTest.kt`: 先出し・成功・失敗で戻る・元に戻す・絞り込み
- [x] T030 [US3] `M/feature/agent/AgentSkills.kt`: 頭の注記、`LazyColumn` ＋ `ListItem`（key = skillKey、絵文字・名前・状態と説明・`Switch`）、状態の色（contracts/ui.md）、Snackbar と「元に戻す」
- [x] T031 [US3] `M/feature/agent/AgentSkills.kt`: 行を押すと共通のボトムシート。足りないもの（`missing` と止められている理由）と、`skills.files.get` の SKILL.md を Markdown のプレビューで（解析は ViewModel で一度だけ。待っているあいだはシートの面にプレースホルダー）
- [x] T032 [US3] `M/feature/agent/AgentSettingsScreen.kt`: スキルのタブで 20 件を超えるとき、下部のピルの右に `VerticalDivider` と検索のアイコン。押すとピルが検索欄に変わり、戻るでタブに戻る

### 追加（2026-11-24 マスター指定: 概要を openclaw.json の定義の表示に）

- [x] T038 [US2] `M/core/data/AgentConfig.kt`: `config.get` からエージェントの定義を読み、既定と重ねる（`Configured(value, inherited)`）。`T/core/data/AgentConfigTest.kt`
- [x] T039 [US2] `M/feature/agent/AgentSettingsViewModel.kt`: 概要の定義の読み込み（失敗・手段が無い・定義が無いを区別）
- [x] T040 [US2] `M/feature/agent/AgentOverview.kt`: モデル / ツール / ワークスペースの区切り、思考の段階のメーター、プロファイルの選択、許可・拒否のチップ、オン・オフ、スキルの帯、「既定」の印

## Phase 6: Polish

- [ ] T033 設定を開いたままサイドパネルで別のエージェントを選んでも、開いている画面は元のまま（quickstart 11） （行き先が agentId を持つので、作りとして元のまま。実機の確認はまだ）
- [ ] T034 保存の途中で切れたとき「保存できたか分からない」を示し、再接続後に読み直す（spec の端のケース） （未着手）
- [x] T035 [P] `./gradlew :app:testDebugUnitTest :app:assembleDebug` を通す
- [ ] T036 AGENTS.md 第 4 章（ライト/ダーク × スマホ/タブレット × 文字 100%/200%、`grep -rn "Color(0x\|\.sp\b" app/src/main`）
- [ ] T037 実機で quickstart 1〜12（予測型「戻る」の途中停止・タブの連打・アニメーター 5x を含む）

## Dependencies

- Phase 2 → すべてのストーリー。T003 → T004 → T005 → T006 の順（T006 が通るまで US1 に入らない）
- US1 → US2（概要は US1 の画面の骨格とファイルの数を使う）
- US3 は US1 の画面の骨格（T016）のあとなら US2 と並行できる
- US2 のスキルの数は、US3 の T028 のあとで出る

## Parallel Examples

- Phase 1: T001 と T002
- Phase 2: T003〜T006 の流れと並行して T008
- US1: T011 のあと T012 と T013 を並行。T017・T018・T019 は別のファイルで並行
- US3: T026 のあと T027 と T028 を並行

## Implementation Strategy

1. **MVP = Phase 1〜3**: サイドパネルから指示のファイルを直せる。ここで一度マスターに見せる
2. US2（小さい）→ US3 の順で足す
3. Phase 6 で仕上げる
