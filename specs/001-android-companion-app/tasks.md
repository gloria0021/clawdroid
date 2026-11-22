---
description: "Clawdroid — OpenClaw Android コンパニオンアプリの実装タスク"
---

# Tasks: Clawdroid — OpenClaw Android コンパニオンアプリ

**Input**: `specs/001-android-companion-app/` の plan.md・spec.md・research.md・data-model.md・contracts/・quickstart.md

**Tests**: 憲章「開発フローと品質ゲート」が ViewModel とデータ層（Gateway クライアント）の単体テストを求めるため、
各ストーリーにテストのタスクを含める。テストは実装より先に書き、失敗することを確かめてから実装する。

**Organization**: ストーリーごとに分け、各段階で単独に動作を確かめられるようにする。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 並行できる（別のファイルで、未完了のタスクに依存しない）
- **[Story]**: 対応するストーリー（US1〜US9）

## Path Conventions

- `M` = `app/src/main/kotlin/jp/miyavi/android/clawdroid`
- `T` = `app/src/test/kotlin/jp/miyavi/android/clawdroid`
- `R` = `app/src/main/res`
- 参考: `reference/openclaw/apps/android/app/src/main/java/ai/openclaw/app`（以下 `ref-android`）、
  `reference/openclaw/packages/gateway-protocol/src/schema`（以下 `ref-schema`）、Gerbil = `~/Dev/workspace/boards`

---

## Phase 1: Setup（プロジェクトの初期化）

- [ ] T001 Gradle の土台を作る: `settings.gradle.kts`、`build.gradle.kts`、`gradle.properties`、Gradle Wrapper（Gerbil と同じ版）
- [ ] T002 `gradle/libs.versions.toml` を作る（research R15 の版。Gerbil の toml を出発点に、Tink・Termux terminal-view・highlights・multiplatform-markdown-renderer を追加）
- [ ] T003 `app/build.gradle.kts` を作る: `namespace = "jp.miyavi.android.clawdroid"`、minSdk 33、compile/targetSdk 37、JVM 21、Compose、kotlinx.serialization、KSP（Room）、Robolectric・Roborazzi
- [ ] T004 `app/src/main/AndroidManifest.xml` を作る: `INTERNET`・`ACCESS_NETWORK_STATE`・`POST_NOTIFICATIONS`・`RECORD_AUDIO`・`FOREGROUND_SERVICE`・`FOREGROUND_SERVICE_CONNECTED_DEVICE`・`CHANGE_NETWORK_STATE`、`dataExtractionRules`（秘密情報をバックアップから外す）、`localeConfig`
- [ ] T005 [P] `R/xml/network_security_config.xml` と `R/xml/data_extraction_rules.xml`、`R/xml/locales_config.xml`（ja・en）を作る
- [ ] T006 [P] `R/values/strings.xml`（ja）と `R/values-en/strings.xml` の骨組みを作る（`*_cd` の命名規則を含む）
- [ ] T007 [P] Hugeicons Stroke Rounded の VectorDrawable を `R/drawable/ic_huge_*.xml` に置く（menu・new-chat・more・send・stop・mic・attach・folder・file・terminal・settings・refresh・automation・check・close・copy・share・download）と、`app/src/main/assets/licenses/hugeicons-LICENSE`
- [ ] T008 [P] `app/src/main/assets/licenses/openclaw-LICENSE`（MIT、流用した部分の著作権表示）を置く

---

## Phase 2: Foundational（全ストーリーの前提）

**⚠️ このフェーズが終わるまで、どのストーリーにも着手しない**

### デザインシステム（Gerbil から移植）

- [ ] T009 [P] `M/core/designsystem/Spacing.kt` を作る（`Spacing.xs/s/m/l/xl` = 4/8/12/16/24dp、`Dimens.*`。Gerbil の Spacing.kt を移植）
- [ ] T010 [P] `M/core/designsystem/Motion.kt` を作る（M3 Expressive の motionScheme、画面遷移用の標準スキーム、大きさの割合の名前付き定数。Gerbil の `AppMotionScheme` を移植）
- [ ] T011 `M/core/designsystem/Theme.kt` を作る: 4 テーマ（OpenClaw = 種色 ライト `#dc2626`／ダーク `#ff5c5c`・TonalSpot、Modern = `PaletteStyle.Monochrome` で `error` を残す、Dynamic = `dynamicLight/DarkColorScheme`、Custom = `PaletteStyle.Fidelity` ＋ `withContrastedSecondary`）、背景の `primaryContainer` 寄せ、リンク色のコントラスト 4.6 以上（Gerbil の Theme.kt を移植）
- [ ] T012 [P] `T/core/designsystem/ThemeTest.kt` を書く: 全テーマ × ライト/ダークで本文・リンクのコントラストが WCAG AA 以上、Custom に白・灰色を入れても無彩色のまま（FR-087・088）
- [ ] T013 `M/core/designsystem/Glass.kt` を作る: `Modifier.hazeSurface()` / `Modifier.sheetHazeSurface()`、`SurfaceStyle`（Glass / Blur）、不透明度は AGENTS.md 2.10 の固定値（シート・メニュー 90%、その他 50%、ぼかし 30%）。Gerbil の FloatingGlassBar.kt を移植し、強さのスライダーは移さない
- [ ] T014 [P] `M/core/designsystem/components/` に共通部品を作る: `GlassPill`、`FloatingIconButton`（浮く丸いボタン）、`GlassSnackbarHost`、`GlassDropdownMenu`、`AppBottomSheet`（シートの共通部品。AGENTS.md 2.5）、`Placeholder`（光の帯）

### 設定と秘密情報

- [ ] T015 [P] `M/core/security/SecretStore.kt` を作る: Tink の AEAD（Android Keystore の鍵）で暗号化した DataStore。`GatewayConnection` と `DeviceIdentity` を保存（data-model 1。「`gatewayToken` は空は不可。画面とログに出さない」）
- [ ] T016 [P] `T/core/security/SecretStoreTest.kt` を書く（保存・読み出し・消去、平文で残らないこと）
- [ ] T017 [P] `M/core/settings/SettingsStore.kt` を作る: `ThemeSettings`（初期値 System / OpenClaw / 白 / Glass）、`NotificationSettings`（初期値 常駐オン・各種オン・FollowSystem）、`UiSelection`（data-model 3・4）

### Gateway クライアント

- [ ] T018 `M/core/gateway/Frames.kt` を作る: `req` / `res` / `event` のフレームと JSON の符号化（kotlinx.serialization。`ref-schema/frames.ts` に合わせる）
- [ ] T019 [P] `T/core/gateway/FramesTest.kt` を書く（`ref-schema` の例と往復一致）
- [ ] T020 `M/core/gateway/DeviceIdentity.kt` を作る: Tink の Ed25519 で鍵の作成・保存、`deviceId`（公開鍵の指紋）、v3 の署名内容（`ref-android/gateway/DeviceAuthPayload.kt` の `buildV3` と同じ並び）
- [ ] T021 [P] `T/core/gateway/DeviceIdentityTest.kt` を書く（v3 の文字列が公式実装と一致、署名の検証）
- [ ] T022 `T/core/gateway/FakeGateway.kt` を作る: MockWebServer の WebSocket で challenge・connect・hello-ok・PAIRING_REQUIRED・AUTH 拒否・イベントの送信・切断を再現する
- [ ] T023 `T/core/gateway/GatewayClientTest.kt` を書く: 接続の成功、承認待ち → `device.pair.resolved` → 再接続、認証の拒否で自動再接続を止める、到達不能で段階的に延ばす、`tick` が `tickIntervalMs × 2` 途絶えたら閉じる（data-model 1 の状態遷移）
- [ ] T024 `M/core/gateway/GatewayClient.kt` を作る: OkHttp の WebSocket（`pingInterval` 0）、`connect.challenge` を待って `connect`（role・scopes・caps は contracts/gateway-rpc.md）、`request()` の応答の対応付け、イベントの Flow、`ConnectionState`、再接続、`deviceToken` の保存（`ref-android/gateway/GatewaySession.kt` を参考）
- [ ] T025 `M/core/gateway/GatewayErrors.kt` を作る: `error.details.code` と `recommendedNextStep` から `AuthRejected` / `Unreachable` / `AwaitingApproval` / `MissingScope` を判定

### 土台の画面とデータ

- [ ] T026 `M/core/database/ClawdroidDatabase.kt` を作る（Room。エンティティは各ストーリーで追加）
- [ ] T027 `M/ClawdroidApp.kt` と `M/MainActivity.kt` を作る: `enableEdgeToEdge()`、テーマの適用、`GatewayClient` と DB の生成（手書きの依存の受け渡し）
- [ ] T028 `M/navigation/AppNavigation.kt` を作る: Navigation 3 の `NavDisplay`、行き先（contracts/ui-navigation.md）、画面遷移の `transitionSpec` / `popTransitionSpec` / `predictivePopTransitionSpec`（上の画面が右から重なり、下は暗くなる）、入り終わるまで次の遷移を待つ処理

**Checkpoint**: テーマ・ガラス・Gateway への接続（偽の Gateway で）が動く

---

## Phase 3: User Story 1 — 接続してエージェントのセッションと会話する (P1) 🎯 MVP

**Goal**: トークンで接続し、エージェントのタブでセッション一覧を切り替え、会話する

**Independent Test**: quickstart 検証 1・2

### Tests

- [ ] T029 [P] [US1] `T/feature/connect/ConnectViewModelTest.kt`（入力の検証「`ws://` はプライベートアドレスだけ」、失敗の理由の区別、入力を残す）
- [ ] T030 [P] [US1] `T/core/data/SessionsRepositoryTest.kt`（`sessions.subscribe` の初回の一覧と `sessions.changed` の反映、エージェントごとの保持）
- [ ] T031 [P] [US1] `T/core/data/ChatRepositoryTest.kt`（履歴の遡り、送信の冪等キー、逐次イベントの組み立て、停止、承認）
- [ ] T032 [P] [US1] `T/feature/chat/ChatViewModelTest.kt`（送信で即座に会話に加わる、上にいるとき位置を動かさず「最下部へ」を出す、Markdown の解析を ViewModel で一度だけ）

### Implementation

- [ ] T033 [US1] `M/feature/connect/ConnectViewModel.kt` と `ConnectScreen.kt`（`ConnectContent`）: 接続先・トークンの入力（マスク・貼り付け可）、接続中・承認待ち（承認の方法の案内）・失敗の表示（FR-001・004）
- [ ] T034 [US1] `M/core/data/AgentsRepository.kt`: `agents.list`、`agent-kind` で `system` を分ける
- [ ] T035 [US1] `M/core/data/SessionsRepository.kt`: `sessions.subscribe`（`sortBy: "activity"`）、`sessions.patch`（改名・アーカイブ・既読）、エージェントごとの一覧、Room のキャッシュ `M/core/database/SessionEntity.kt`
- [ ] T036 [US1] `M/feature/chat/sidebar/SidebarContent.kt`: エージェントのタブ（ガラスの面の `PrimaryScrollableTabRow`）＋ `HorizontalPager` のセッション一覧（タブと Pager を同じ位置の値に追従、research R7）、項目は `ListItem`（タイトル 2 行・更新時刻・未読と実行中の目印）、`key` 付き、下部に 自動化・ターミナル・設定
- [ ] T037 [US1] `M/feature/chat/sidebar/SessionItemMenu.kt`: 長押しの `GlassDropdownMenu`（改名・アーカイブ・アーカイブ解除、`onLongClickLabel`）（FR-013）
- [ ] T038 [US1] `M/core/data/ChatRepository.kt`: `chat.startup`・`chat.history`（遡り）・`chat.message.get`・`sessions.messages.subscribe`（`includeApprovals`）・`chat` / `agent` イベントの組み立て・`chat.abort`、Room のキャッシュ `M/core/database/MessageEntity.kt`
- [ ] T039 [US1] `M/core/data/SendQueue.kt`: 送信待ちの列（`PendingSend`、状態「`queued` → `sending` → `sent` / `failed`」、冪等キーを `chat.send` に渡す）、`M/core/database/PendingSendEntity.kt`
- [ ] T040 [US1] `M/feature/chat/ChatViewModel.kt`: 会話の状態、送信・停止、Markdown と日付の整形を先に済ませた表示用のモデル
- [ ] T041 [US1] `M/feature/chat/ChatScreen.kt`（`ChatContent`）: 浮く丸いボタン（メニュー・新規・⋮）、ヘッダのセッション名とフォルダ名、`ModalNavigationDrawer`、会話の `LazyColumn`（`key` 付き）、最下部へ戻るボタン
- [ ] T042 [P] [US1] `M/feature/chat/message/UserMessage.kt` と `AssistantMessage.kt`: 右寄せの角丸の面／全幅の素の文字、`bodyLarge`（行の高さ 1.5）、コードは等幅、応答の下の操作行（コピー・共有）（FR-023・025）
- [ ] T043 [P] [US1] `M/feature/chat/message/ToolCallRow.kt`: 折りたたみの行、展開で引数と出力、巨大な出力は先頭だけ（FR-024）
- [ ] T044 [P] [US1] `M/feature/chat/message/ImageOverlay.kt`: 画像の全画面オーバーレイ（`MutableTransitionState` で消え終わってから閉じる）
- [ ] T045 [US1] `M/feature/chat/composer/ComposerPill.kt`: 下部に浮くガラスのピル（添付 ｜ 入力 ｜ マイク／送信、生成中は停止）、画像の添付（`policy.attachments` の上限）（FR-021・022・026）
- [ ] T046 [US1] `M/feature/chat/approval/ApprovalSheet.kt`: 承認・拒否（`approval.get` / `approval.resolve`）（FR-027）
- [ ] T047 [US1] `M/core/database/DraftEntity.kt`・`ReadingPositionEntity.kt` と保存の処理: 入力ごとの下書き（間引く）、読んでいた位置、選択中のエージェントとセッション（FR-028、SC-008）
- [ ] T048 [US1] `M/feature/chat/composer/VoiceInput.kt`: `SpeechRecognizer`、`isRecognitionAvailable()` が false ならマイクを出さない、結果は入力欄に入れるだけ（FR-029）
- [ ] T049 [US1] `M/feature/chat/ConnectionBanner.kt` で接続の状態を表示: 再接続中の控えめな表示（上部）、認証の失効で接続画面へ誘導（Edge Cases）
- [ ] T050 [US1] `M/feature/chat/EmptyStates.kt` で空の状態: セッションが 0 件のときの説明と新規チャットの導線、アーカイブ済みのセッションでは入力欄を無効にして解除の操作（Edge Cases）

**Checkpoint**: 実機で quickstart 検証 1・2 が通る。ここで一度コミットし、手元で使い始める

---

## Phase 4: User Story 2 — エージェントごとに通知を受け取る (P2)

**Goal**: 常駐して接続を保ち、応答の完了・承認待ち・自動化の失敗をエージェントごとに内容付きで通知する

**Independent Test**: quickstart 検証 3・4

### Tests

- [ ] T051 [P] [US2] `T/core/notifications/CompletionDetectorTest.kt`: `sessions.changed` の `hasActiveRun` の true → false で完了を検知、開いている会話は通知しない、同じセッションは 1 つにまとめる、切断中の完了を再接続後にまとめる（FR-100・105・107）
- [ ] T052 [P] [US2] `T/core/notifications/NotificationReplyTest.kt`: 通知からの返信が送信待ちの列に入り、結果が通知に返る（FR-104）

### Implementation

- [ ] T053 [US2] **先に確認**: 実 Gateway で、裏にいるあいだ `sessions.changed` だけで完了を取りこぼさないかを確かめ、結果を research R6 に追記する（plan のリスク 1。外れたら開いている会話の購読を残す方式に切り替える）
- [ ] T054 [US2] `M/core/notifications/GatewayConnectionService.kt`: フォアグラウンドサービス（type `connectedDevice`）、常駐の通知（重要度 MIN、接続の状態だけ）、設定でオン・オフ（FR-120、FR-122）
- [ ] T055 [US2] `M/core/data/BackgroundSubscriptionPolicy.kt`: 裏に回ったら `sessions.messages.unsubscribe`、戻ったら購読し直して差分を取る（FR-122）
- [ ] T056 [US2] `M/core/notifications/NotificationChannels.kt`: Gateway ごとの `NotificationChannelGroup`、エージェントごとのチャンネル（エージェントの追加・削除に追従）、承認・自動化の失敗のチャンネル（FR-102・106）
- [ ] T057 [US2] `M/core/notifications/CompletionDetector.kt`: 完了の検知 → `chat.history` で最新の応答を取得 → 通知
- [ ] T058 [US2] `M/core/notifications/ConversationNotifier.kt`: `MessagingStyle`（エージェント名・セッション名・本文の冒頭）、エージェントごとのグループ、ロック画面の表示（FR-101・108）、開くと該当の位置へ（FR-103。`ref-android/ConversationNotifications.kt` を参考）
- [ ] T059 [US2] `M/core/notifications/NotificationReplyReceiver.kt`: `RemoteInput` の返信を送信待ちの列へ、結果を通知に反映（FR-104）
- [ ] T060 [US2] `M/core/notifications/AttentionNotifier.kt`: 承認待ち（開くと承認のシート）と自動化の失敗（開くと該当の実行）（FR-106）
- [ ] T061 [US2] `M/feature/settings/NotificationSettingsSection.kt`: 常駐のオン・オフ、種類ごとのオン・オフ、通知の許可、電池の最適化の状態と解除の導線、テスト通知（FR-109・120・121）
- [ ] T062 [US2] 電池の計測（quickstart 検証 4）を Wi-Fi・モバイル回線で 24 時間ずつ行い、結果を research R6 に追記する

**Checkpoint**: 通知が届き、電池の計測結果が出ている

---

## Phase 5: User Story 3 — フォルダを指定して新しいセッションを作る (P3)

**Independent Test**: quickstart 検証 5

- [ ] T063 [P] [US3] `T/feature/chat/NewSessionTest.kt`（フォルダ未指定は既定、指定は `cwd` で作成、作成後に一覧の先頭）
- [ ] T064 [US3] `M/core/data/SessionsRepository.kt` に `sessions.create`（`agentId`・`cwd`・最初のメッセージ）を追加
- [ ] T065 [US3] `M/feature/chat/newsession/FolderPickerSheet.kt`: `fs.listDir` でホストのフォルダを辿るシート（返ったパスをそのまま渡す）
- [ ] T066 [US3] `M/feature/chat/ChatViewModel.kt` と `ChatScreen.kt` に新規チャットの流れを追加: 空の会話を開いて入力欄にフォーカス、最初の送信でセッションを作成、一覧の先頭に位置の移動を伴って入る（`animateItem`）（FR-030〜032）

---

## Phase 6: User Story 4 — モデル・エフォート・使用量 (P4)

**Independent Test**: quickstart 検証 6

- [ ] T067 [P] [US4] `T/core/data/ModelsRepositoryTest.kt`（エフォートの選択肢が空なら出さない、推測しない）
- [ ] T068 [P] [US4] `T/core/data/UsageRepositoryTest.kt`（`usage-refreshing` の再取得、取得できない欄だけ誤りにする）
- [ ] T069 [US4] `M/core/data/ModelsRepository.kt`: `models.list`、`sessions.patch`（`model` / `thinkingLevel`）（FR-040・041）
- [ ] T070 [US4] `M/core/data/UsageRepository.kt`: `usage.status`・`sessions.usage`（FR-042〜045）
- [ ] T071 [US4] `M/feature/chat/composer/ModelEffortRow.kt` と `ModelPickerSheet.kt`: ピルの上のモデルとエフォートの選択
- [ ] T072 [US4] `M/feature/chat/usage/UsageSheet.kt`: コンテキストの使用量・最新の実行のトークン・プロバイダの使用枠（割合と戻る時刻）

---

## Phase 7: User Story 5 — ファイルを見て、直して、保存する (P5)

**Independent Test**: quickstart 検証 7

- [ ] T073 [P] [US5] `T/core/data/FilesRepositoryTest.kt`（`expectedHash` の衝突の検出、バイナリ・巨大ファイルは編集不可）
- [ ] T074 [US5] `M/core/data/FilesRepository.kt`: `sessions.files.list` / `get` / `set`、`agents.workspace.list` / `get`（FR-050〜052）
- [ ] T075 [US5] `M/feature/files/FileListScreen.kt`（`FileListContent`）: 一覧（`ListItem`、`key` 付き）と辿る操作
- [ ] T076 [US5] `M/feature/files/FileEditorScreen.kt`: `BasicTextField`（等幅）、`highlights` の色分けを ViewModel で計算して `AnnotatedString` で渡す、1MB 超は読み取り専用で色分けなし（research R10）
- [ ] T077 [US5] `M/core/database/FileDraftEntity.kt` と破棄の確認ダイアログ（「`baseHash` を `expectedHash` として送る」）（FR-052、Edge Cases）
- [ ] T078 [US5] `M/feature/files/FileDownloader.kt`: `MediaStore.Downloads` に保存し Snackbar で保存先（FR-053）
- [ ] T079 [US5] `M/feature/files/FileListScreen.kt` から、画像ファイルを T044 の `ImageOverlay` で開く（FR-054）

---

## Phase 8: User Story 6 — ターミナル (P6)

**Independent Test**: quickstart 検証 8

- [ ] T080 [P] [US6] `T/core/data/TerminalRepositoryTest.kt`（`terminal.data` の受信、`attach` での再開、権限なしの理由）
- [ ] T081 [US6] `M/core/data/TerminalRepository.kt`: `terminal.open` / `input` / `resize` / `close` / `list` / `attach`（FR-060・062）
- [ ] T082 [US6] `M/feature/terminal/TerminalScreen.kt`: Termux の `TerminalView` を `AndroidView` で埋め込み、Gateway の入出力につなぐ（research R9）
- [ ] T083 [US6] `M/feature/terminal/SpecialKeysRow.kt`: Ctrl・Esc・Tab・矢印の行（ガラスのピル）（FR-061）
- [ ] T084 [US6] `M/feature/terminal/TerminalViewModel.kt` で再開: アプリに戻ったら `terminal.list` → `attach` で続きを出す（US6 シナリオ 3）

---

## Phase 9: User Story 7 — ワンボタンで Gateway を再起動 (P7)

**Independent Test**: quickstart 検証 9

- [ ] T085 [P] [US7] `T/core/data/GatewayControlRepositoryTest.kt`（連打しても 1 回、再起動中 → 再接続中 → 接続済み、時間切れで失敗）
- [ ] T086 [US7] `M/core/data/GatewayControlRepository.kt`: `gateway.restart.request`（引数は `ref-schema` で確認）、重複の抑止（FR-070・073）
- [ ] T087 [US7] `M/feature/settings/RestartGatewayAction.kt` で再起動の UI: ⋮ メニューと設定のボタン、確認ダイアログ（実行中の作業への影響を説明）、状態の表示、再接続後に元の画面へ、失敗は再試行付きの Snackbar（FR-071・072）

---

## Phase 10: User Story 8 — 自動化（cron）の管理 (P8)

**Independent Test**: quickstart 検証 10

- [ ] T088 [P] [US8] `T/core/data/CronRepositoryTest.kt`（一覧・作成・編集・有効と無効・今すぐ実行・削除・履歴、`Other` の種類は編集不可）
- [ ] T089 [P] [US8] `T/feature/automations/CronExpressionTest.kt`（5・6 項目の式の検証、タイムゾーン付きの次の実行予定）
- [ ] T090 [US8] `M/core/data/CronRepository.kt`: `cron.list` / `get` / `status` / `add` / `update` / `remove` / `run` / `runs`、`cron.inventory.changed` で取り直す（FR-110〜115）
- [ ] T091 [US8] `M/feature/automations/CronExpression.kt`: 保存前の検証と次の実行予定（`cron-utils` か自前かをここで決める。research R12）（FR-116）
- [ ] T092 [US8] `M/feature/automations/AutomationsScreen.kt`: エージェントごとの一覧（名前・スケジュール・`Switch`・次の実行・前回の結果）
- [ ] T093 [US8] `M/feature/automations/AutomationEditorSheet.kt`: 名前・エージェント・スケジュール（日時 / 間隔 / cron 式 ＋ タイムゾーン）・依頼内容
- [ ] T094 [US8] `M/feature/automations/AutomationRunsSheet.kt`: 実行履歴と、実行で生まれた会話を開く操作。削除は確認ダイアログ
- [ ] T095 [US8] `M/feature/chat/sidebar/SidebarContent.kt` と `M/core/data/SessionsRepository.kt` で、自動化の会話を通常の一覧から分ける（FR-117）

---

## Phase 11: User Story 9 — テーマを選ぶ (P9)

**Independent Test**: quickstart 検証 11

- [ ] T096 [P] [US9] `T/feature/settings/SettingsViewModelTest.kt`（変更の即時反映と保持）
- [ ] T097 [US9] `M/feature/settings/SettingsScreen.kt`: 1 画面に縦に並べる（接続・通知・外観・テーマ・透かし方・言語・再起動）、`SegmentedButton` で外観と透かし方
- [ ] T098 [US9] `M/feature/settings/ThemePicker.kt`: OpenClaw / モダン / ダイナミック / カスタム の選択と、カスタムの色の入力（Gerbil の `CustomColorForm` を移植）（FR-080〜087）
- [ ] T099 [US9] `M/feature/settings/ConnectionSection.kt` で接続の変更・削除（切断）を設定に置く（FR-005）

---

## Phase 12: Polish & Cross-Cutting

- [ ] T100 [P] `M/navigation/AppNavigation.kt` で大画面: Material 3 Adaptive の list-detail でサイドバーと会話の 2 ペイン、本文の最大幅 720dp（FR-014）
- [ ] T101 [P] `R/values-en/strings.xml` の英語の文言をそろえ、アプリ単位の言語設定に対応（FR-094）
- [ ] T102 [P] `M/feature/**` のアクセシビリティ: TalkBack の説明（`*_cd`）、長押しの `onLongClickLabel`、文字 200% での崩れの確認（SC-009）
- [ ] T103 Roborazzi で主要画面のスクリーンショット比較（ライト / ダーク × スマホ / タブレット × 文字 100% / 200%）を `T/screenshots/` に作る
- [ ] T104 `checklists/validation.md` に記録しながら動きを確認: アニメーター再生時間スケール 5x で、連打・途中での取り消し・予測型「戻る」の途中停止（憲章「品質ゲート」、SC-005）
- [ ] T105 `app/src/main` に対して AGENTS.md 第 4 章のチェックリスト（直書きの grep、禁止事項、長い一覧のスクロール）を通す
- [ ] T106 quickstart の検証 1〜11 を実機で通し、結果を `specs/001-android-companion-app/checklists/validation.md` に記録する

---

## Dependencies & Execution Order

### フェーズの依存

- Setup（Phase 1）→ Foundational（Phase 2）→ 各ストーリー
- **US1 が他の全ストーリーの前提**（接続・会話・サイドバーの上に載るため）。US1 の完了後は、US2〜US9 は互いに独立
- 推奨の順序: US1 → US2（必須の通知）→ US3 → US4 → US5 → US6 → US7 → US8 → US9 → Polish
- US9 のテーマの土台（T011・T013）は Phase 2 にあるので、設定画面（Phase 11）を待たずに全画面に効く

### ストーリー内の順序

テスト（失敗を確認）→ Repository → ViewModel → 画面 → 結合

### 並行できるもの

- Phase 1: T005〜T008
- Phase 2: T009・T010・T015・T016・T017・T019・T021（T011→T012、T018→T024 は順に）
- 各ストーリーのテスト（[P] 付き）は同時に書ける
- US1 の T042〜T044（メッセージの部品）は同時に作れる
- US1 の完了後、US3・US4・US5・US8 は担当を分ければ並行できる

## Parallel Example: User Story 1

```text
同時に: T029 ConnectViewModelTest / T030 SessionsRepositoryTest / T031 ChatRepositoryTest / T032 ChatViewModelTest
同時に: T042 UserMessage・AssistantMessage / T043 ToolCallRow / T044 ImageOverlay
```

## Implementation Strategy

### MVP（まず手元で使い始める）

1. Phase 1・2 を終える
2. Phase 3（US1）を終え、quickstart 検証 1・2 を実機で通す
3. **ここで止めて手元で使う**。動きと接続の安定を確かめる
4. Phase 4（US2）の通知を足し、電池を測る

### その後

US3〜US9 を 1 つずつ足し、各段階で quickstart の該当する検証を通してからコミットする。
Play ストアの申告（常駐の type）は Polish の後、公開を決めた段階で行う。
