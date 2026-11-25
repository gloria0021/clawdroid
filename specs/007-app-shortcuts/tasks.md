# Tasks: アプリのショートカット（ランチャーの長押しメニュー）

**Input**: `specs/007-app-shortcuts/`（spec・plan・research・data-model・contracts・quickstart）

**Tests**: 判定（片付け・名前）と保存・Intent の書き読みは純粋な関数にして単体テストを書く（憲章）。

`M` = `app/src/main/kotlin/jp/miyavi/android/clawdroid`、`T` = `app/src/test/kotlin/jp/miyavi/android/clawdroid`、`R` = `app/src/main/res`

## Phase 1: Setup

- [x] T001 [P] `R/values/strings.xml`: 設定の行・画面の題・枠の説明（ホーム / フォルダ / セッション・アーカイブ済み）・追加・上限・シート（エージェント・行き先・名前・外す・保存）・無効の理由の文言（日本語だけ）

## Phase 2: Foundational

- [x] T002 `M/core/notify/OpenIntents.kt`: extras の定数・`OpenTarget` → extras の書き込み・`targetOf` を `NotificationCenter` から移す。`new_chat`（`CWD`）と `SHORTCUT_ID` を足す。`NotificationCenter` はこれを使う
- [x] T003 `M/core/notify/ForegroundState.kt`: `OpenTarget.NewChat(agentId, cwd)`
- [x] T004 [P] `T/core/notify/OpenIntentsTest.kt`（書いて読み戻す・足りない extras は null）
- [x] T005 `M/core/data/AgentAvatarBitmaps.kt`: 顔の画像の読み込み（丸く切る / 切らない）と使い回しを `Notifier` から切り出す。`Notifier` はこれを使う

## Phase 3: US1 押して開く (P1) 🎯 MVP

- [x] T006 [P] [US1] `M/core/shortcut/ShortcutModels.kt`: `ShortcutSlot`・`ShortcutDestination`（Home / Folder / Session）・`ShortcutSettings`（@Serializable）、`MAX_SHORTCUTS = 4`
- [x] T007 [US1] `M/core/settings/SettingsStore.kt`: `shortcuts` の読み書き（JSON）
- [x] T008 [US1] `M/MainActivity.kt`: `openFromIntent`（`OpenIntents.targetOf`）。`SHORTCUT_ID` があれば `reportShortcutUsed`
- [x] T009 [US1] `M/navigation/AppNavigation.kt` と `M/feature/chat/ChatViewModel.kt`: `NewChat` を受ける（最初の画面まで戻し、エージェントを選んで新しい会話を開き、作業フォルダを入れる）

## Phase 4: US3 片付けの判定 (P2。US2 の画面より先に判定を作る)

- [x] T010 [P] [US3] `M/core/shortcut/ShortcutReconcile.kt`: `reconcile(slots, lists, agents)` と名前の決め方（純粋な関数）
- [x] T011 [P] [US3] `T/core/shortcut/ShortcutReconcileTest.kt`・`ShortcutLabelTest.kt`・`ShortcutSettingsJsonTest.kt`
- [x] T012 [US1/US3] `M/core/shortcut/ShortcutPublisher.kt` と `M/ClawdroidApp.kt`: 枠・一覧・エージェント・接続の状態を見張る。登録（前回と違うときだけ）・外す・無効・有効に戻す・接続先の切り替えで全消去・前に戻ったときの並べ直し

## Phase 5: US2 設定 (P1)

- [x] T013 [US2] `M/feature/settings/ShortcutSettingsScreen.kt`: 枠の一覧（`ListItem`・顔・名前・行き先の説明・アーカイブ済み）、長押しのドラッグと TalkBack の並べ替え、浮くピルの追加、上限の Snackbar
- [x] T014 [US2] `M/feature/settings/ShortcutEditSheet.kt`: エージェント → 行き先（`ButtonGroup`）→ フォルダ（`FolderPickerSheet`）/ セッション → 名前 → 保存・外す
- [x] T015 [US2] `M/feature/settings/SettingsScreen.kt` と `M/navigation/AppNavigation.kt`: 「ショートカット」の行と `ShortcutSettingsRoute`

## Phase 6: Polish

- [x] T016 ビルド・単体テスト・`grep -rn "Color(0x\|\.sp\b"` の確認
- [ ] T017 quickstart の実機の 17 項目（特に 15 アイコンの切れ方・16 起動の経路と時間）

## Dependencies

T002〜T005 → US1。T006・T010 → T012。T012 → US2 の画面（保存すれば反映される）
