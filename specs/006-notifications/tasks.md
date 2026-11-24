# Tasks: 通知（常駐の接続で受け取る）

**Input**: `specs/006-notifications/`（spec・plan・research・data-model・contracts・quickstart）

**Tests**: 判定（完了・文・失敗・承認の差分）は純粋な関数にして単体テストを書く（憲章）。

`M` = `app/src/main/kotlin/jp/miyavi/android/clawdroid`、`T` = `app/src/test/kotlin/jp/miyavi/android/clawdroid`、`R` = `app/src/main/res`

## Phase 1: Setup

- [x] T001 [P] `R/values/strings.xml`: チャンネル名・説明、常駐の文、応答・承認・自動化の失敗の文、ボタン、設定のセクションの文言（日本語だけ）
- [x] T002 [P] `R/drawable/ic_huge_notification.xml`（既存）を通知の小さなアイコンに使う（単色）。無ければ Hugeicons から足す
- [x] T003 `app/src/main/AndroidManifest.xml`: `ConnectionService`（connectedDevice・exported=false）、`ApprovalActionReceiver`、`BootReceiver`（BOOT_COMPLETED）、`RECEIVE_BOOT_COMPLETED`

## Phase 2: Foundational

- [x] T004 `M/core/notify/NotificationCenter.kt`: チャンネル（常駐・応答のグループとエージェントごと・承認・自動化の失敗）、通知の id、`MainActivity` を開く `PendingIntent`（行き先の付加情報）、ロック画面の表示
- [x] T005 `M/core/notify/OpenTarget.kt` と `M/AppContainer`: 通知から開く行き先（会話・承認・自動化）を流す `MutableSharedFlow`、アプリが前にあるか・開いている会話（`ForegroundState`）
- [x] T006 `M/MainActivity.kt` と `M/navigation/AppNavigation.kt` と `M/feature/chat/ChatViewModel.kt`: 行き先を受けて会話を開く・自動化の詳細へ進む。開いている会話を `ForegroundState` に知らせる
- [x] T007 `M/core/notify/ConnectionService.kt` と `M/ClawdroidApp.kt`: 常駐（connectedDevice）。接続の状態を常駐の通知に写す。設定（常駐・接続情報）と前後の切り替えで始める・止める
- [x] T008 `M/core/data/ChatRepository.kt`: 裏にあるあいだ開いている会話の購読を止め、戻ったら購読し直して取り直す。`M/core/gateway/GatewayClient.kt` の caps に `chat-only-assistant-text`

## Phase 3: US1 応答の完了 (P1) 🎯 MVP

- [x] T009 [P] [US1] `M/core/notify/ReplyCompletion.kt`: 行の移り変わりから完了を判定する純粋な関数と、通知の文（Markdown の記号を除く・300 文字）
- [x] T010 [P] [US1] `T/core/notify/NotificationLogicTest.kt`（完了・応答の選び方・文・自動化の失敗をまとめて）
- [x] T011 [US1] `M/core/notify/ReplyWatcher.kt`: `sessions.changed` を見て、完了なら `chat.history` の最後の応答で通知。開いている会話は外す。同じ応答は重ねない。つなぎ直したら `sessions.list` で補う

## Phase 4: US2 承認 (P1)

- [x] T012 [P] [US2] `M/core/notify/ApprovalNotifier.kt`: `ApprovalsRepository.requests` の差分で出す・消す。全文（5,000 文字で切る）・ボタン（許される決定だけ・ロック解除が要る）
- [x] T013 [US2] `M/core/notify/ApprovalActionReceiver.kt`: ボタンで `resolve`、結果を示してから消す。送れなければ理由
- [ ] T014 [P] [US2] `T/core/notify/ApprovalNotifierTest.kt`（未作成: 差分の処理が Notifier の中にあり、純粋な関数に切り出していない）

## Phase 5: US3 自動化の失敗 (P2)

- [x] T015 [P] [US3] `M/core/notify/AutomationFailure.kt`（判定）と Notifier の中の見張り。テストは NotificationLogicTest

## Phase 6: US4 設定 (P3)

- [x] T016 [US4] `M/feature/settings/SettingsScreen.kt` と AppNavigation の配線: 通知のセクション（常駐・種類ごと・ロック画面・許可・電池の最適化・テスト・端末の通知の設定）
- [x] T017 [US4] `M/core/notify/BootReceiver.kt`: 端末の起動で常駐を再開

## Phase 7: Polish

- [ ] T018 001 の spec の「実装の状況」の US2 を更新、AGENTS.md に通知の見た目（Hugeicons の単色アイコン）を追記するか確認
- [ ] T019 quickstart の実機の 14 項目

## Dependencies

T004〜T008 → US1〜US4。US2・US3 は US1 と独立（NotificationCenter だけに依る）
