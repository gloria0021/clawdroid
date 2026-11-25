# Implementation Plan: アプリのショートカット（ランチャーの長押しメニュー）

**Branch**: `007-app-shortcuts`（ブランチは切らず main で進める） | **Date**: 2026-11-25 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/007-app-shortcuts/spec.md`

## Summary

設定で決めた最大 4 つの枠を、`ShortcutManagerCompat` の動的ショートカットとしてランチャーに登録する。押したときは、spec 006 の通知から開く経路
（Intent の extras → `OpenTarget` → `ForegroundState.open` → `AppNavigation` / `ChatViewModel`）をそのまま使い、行き先を確かめずにすぐ開く。

- 行き先: ホーム（作るときに覚えたホームの会話のキー）・既存のセッション は既存の `OpenTarget.Session`。作業フォルダで新しい会話 だけ `OpenTarget.NewChat` を足す
- 片付け: `ShortcutPublisher` が、枠・セッションの一覧・エージェントの一覧の変化を見て、純粋な関数 `reconcile` でアーカイブ・削除・エージェントの消滅を判定する。
  外す・無効にするは裏でも回数の制限を受けない API だけで行う。一覧の読み込みは足さない
- 設定: 設定 → ショートカット（1 段の画面）。並べ替えは `AgentLayoutScreen` と同じ、作る・直すはボトムシート

調査は [research.md](./research.md)。

## Technical Context

**Language/Version**: Kotlin 2.4.21、JVM 21（001〜006 と同じ）

**Primary Dependencies**: 既存のみ（Compose・M3 Expressive・Navigation 3・DataStore・kotlinx.serialization・Coil 3・`androidx.core` 1.19.1 の `ShortcutManagerCompat` / `IconCompat`）。新しい依存なし

**Storage**: `SettingsStore` に `shortcuts`（JSON の文字列。`AGENT_LAYOUT` と同じやり方）を足す。中身は [data-model.md](./data-model.md) の `ShortcutSettings`

**Testing**: JUnit 4。`reconcile` の判定、名前の決め方、設定の JSON の行き来、Intent の extras の書き読み（[quickstart.md](./quickstart.md)）

**Target Platform**: Android 13（API 33）以上

**Project Type**: mobile-app（単一の `app` モジュール）

**Performance Goals**: 押してから行き先まで、アプリが裏なら 1 秒以内（SC-001）。設定の変更からランチャーへの反映 1 秒以内（SC-002）。押したときに待ちを出さない（SC-004）

**Constraints**: 片付けのためだけに一覧を読まない（FR-021）。裏での登録は回数を制限されるので、裏では外す・無効にするだけ（R1）。
ショートカットの Intent の extras は文字列だけ（永続化される）

**Scale/Scope**: 部品 1 式（`core/shortcut`）・設定の画面 1・行き先の型 1 つ。ストーリー 3 本、要件 FR-001〜034

## Constitution Check

| 原則 | 判定 | 根拠 |
|---|---|---|
| I. 動きの心地よさ | 適合 | 押したときに待たない（確かめない）。アイコンの読み込みと登録は裏のスコープで、画面の動きと重ねない。並べ替えの動きは `AgentLayoutScreen` と同じ |
| II. AGENTS.md | 適合 | 設定の一覧は `ListItem`、作る・直すは共通のボトムシート、追加は浮くピル、行き先の 3 択は Expressive の `ButtonGroup`。文言は `strings.xml`。1 段の画面は既存の エージェント・通知 と同じ扱い（R8） |
| III. Gateway が唯一の正 | 適合 | 有効・アーカイブ・削除の判定は Gateway から読んだ一覧だけで行う。手元で推測しない（読み込んでいないものは判定しない） |
| IV. 安全 | 適合 | ショートカットには会話のキーと作業フォルダだけを入れ、本文・接続情報は入れない。接続先が変わったら全部消す |
| V. 状態を失わない | 適合 | 枠は DataStore に保つ。アーカイブで外れた枠は残し、戻されたら復活する |
| VI. 簡潔さ | 適合 | 開く経路・顔の読み込み・フォルダの選び方・並べ替えはすべて既存を使い回す。新しい型は `OpenTarget.NewChat` だけ |
| VII. 持ち歩く道具 | 適合 | 1 回の長押しで決まった相手に話しかける、持ち歩きの中心の用途。自動で足す・ウィジェットは範囲外 |

設計後の再確認: 変わらず。

## Project Structure

### Documentation (this feature)

```text
specs/007-app-shortcuts/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── shortcut-intents.md   # Intent の extras・登録の API の使い分け・無効の文言
├── checklists/
│   └── requirements.md
└── tasks.md                  # /speckit-tasks で作る
```

### Source Code (repository root)

```text
app/src/main/
├── res/values/strings.xml                      # 設定の画面・シート・無効の理由の文言
└── kotlin/jp/miyavi/android/clawdroid/
    ├── ClawdroidApp.kt                         # AppContainer に ShortcutPublisher を足す
    ├── MainActivity.kt                         # openFromNotification → openFromIntent。SHORTCUT_ID があれば reportShortcutUsed
    ├── core/shortcut/
    │   ├── ShortcutModels.kt                   # 新規: ShortcutSlot・ShortcutDestination・ShortcutSettings（@Serializable）
    │   ├── ShortcutReconcile.kt                # 新規: reconcile(slots, lists, agents) と名前の決め方（純粋な関数）
    │   └── ShortcutPublisher.kt                # 新規: 見張り・登録・片付け・接続先の切り替え・前に戻ったときの並べ直し
    ├── core/notify/
    │   ├── ForegroundState.kt                  # OpenTarget.NewChat を足す
    │   ├── OpenIntents.kt                      # 新規: extras の定数・書き込み・targetOf（NotificationCenter から移す）
    │   ├── NotificationCenter.kt               # OpenIntents を使う
    │   └── Notifier.kt                         # 顔の読み込みを AgentAvatarBitmaps に移す
    ├── core/data/AgentAvatarBitmaps.kt         # 新規: 顔の画像の読み込み（丸く切る / 切らない）と使い回し
    ├── core/settings/SettingsStore.kt          # shortcuts の読み書き
    ├── feature/settings/
    │   ├── SettingsScreen.kt                   # 「ショートカット」の行
    │   ├── ShortcutSettingsScreen.kt           # 新規: 枠の一覧・並べ替え・追加（*Content と ViewModel を分ける）
    │   └── ShortcutEditSheet.kt                # 新規: エージェント・行き先・フォルダ / セッション・名前・外す
    ├── feature/chat/ChatViewModel.kt           # openRequests で NewChat を受ける（エージェントを選び、新しい会話にフォルダを入れる）
    └── navigation/AppNavigation.kt             # ShortcutSettingsRoute。openRequests の NewChat を Session と同じ扱いに
app/src/test/kotlin/jp/miyavi/android/clawdroid/
    ├── core/shortcut/ShortcutReconcileTest.kt
    ├── core/shortcut/ShortcutLabelTest.kt
    ├── core/shortcut/ShortcutSettingsJsonTest.kt
    └── core/notify/OpenIntentsTest.kt
```

**Structure Decision**: ショートカットは画面から独立した仕組みなので `core/shortcut` に置く（通知の `core/notify` と同じ考え方）。
判定は純粋な関数にしてテストし、Android の API を呼ぶのは `ShortcutPublisher` だけにする。

## 進め方（tasks の下書き）

1. **土台**: `OpenIntents` への移し替えと `OpenTarget.NewChat`（通知の動きが変わらないことを既存のテストで確かめる）→ `AgentAvatarBitmaps` の切り出し
2. **US1（押して開く）**: `ShortcutModels`・`SettingsStore.shortcuts`・`ShortcutPublisher` の登録 → `MainActivity` / `AppNavigation` / `ChatViewModel` の受け取り。
   この段階では枠をテスト用に直接書いて確かめる
3. **US2（設定）**: `ShortcutSettingsScreen`・`ShortcutEditSheet`・設定の行・ルート
4. **US3（片付け）**: `reconcile` とテスト → `ShortcutPublisher` の見張り・無効・接続先の切り替え
5. **仕上げ**: quickstart の実機の確認（特に 15・16）、AGENTS.md 4 の見た目の確認

## Complexity Tracking

| 逸脱 | なぜ必要か | より単純な案を退けた理由 |
|---|---|---|
| 設定の 1 段の画面（AGENTS.md は「1 画面に縦に並べる」） | 枠の並べ替えのドラッグと設定画面のスクロールがぶつかる | 設定画面に直接並べる案 → ドラッグが効かない。既存の エージェント・通知 も同じ 1 段の画面で、前例に合わせる |
