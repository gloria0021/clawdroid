# Research: アプリのショートカット

調べた元: インストール済みの OpenClaw 2026.11.6（`dist/chat-send-handler-*.mjs`・`dist/sessions-delete-*.mjs`・`dist/session-change-event-*.mjs`・`docs/`）、
このリポジトリの実装（spec 006 の通知から開く経路・`SessionsRepository`・`SettingsStore`・`Notifier`）、Android の `ShortcutManager` の仕様。

## R1. 登録の仕方と回数の制限

- **Decision**: `androidx.core`（導入済み 1.19.1）の `ShortcutManagerCompat` を使う。呼び方を場面で分ける
  - 設定で枠を変えた（アプリは前にある）: `setDynamicShortcuts(全件)` で丸ごと置き換える
  - 片付け（裏でも起きる）: `removeDynamicShortcuts(ids)` と `disableShortcuts(ids, 理由)`。この 2 つは回数の制限を受けない
  - アーカイブが戻された: `enableShortcuts(ids)` のあと、前にいれば `setDynamicShortcuts` で並べ直す。裏なら前に戻ったときに並べ直す
  - 名前の追いかけ（セッション名が変わった）: `updateShortcuts`。制限中（`isRateLimitingActive`）なら前に戻ったときにまとめて行う
- **無駄な呼び出しを避ける**: 登録する内容（ID・名前・行き先・アイコンの元の URL）を前回と比べ、同じなら呼ばない
- **Rationale**: 動的ショートカットの追加・置き換え・更新は、裏にいるあいだ回数を制限される（前に戻ると解ける）。外す・無効にするは制限されないので、裏で起きる片付けはそれだけで済ませる
- **Alternatives**: マニフェストの静的ショートカット → 中身を変えられない。`pushDynamicShortcut` → 自動で足す用途（spec の範囲外）

## R2. 押したときの経路

- **Decision**: ショートカットの Intent は `MainActivity` 宛ての `ACTION_VIEW`。extras に、通知と同じ `EXTRA_OPEN` / `EXTRA_AGENT` / `EXTRA_SESSION` に加えて、新しい会話の `EXTRA_CWD` と、使ったことを知らせる `EXTRA_SHORTCUT_ID` を入れる
  - 読み取り（`targetOf`）と書き込みを `NotificationCenter` から `core/notify/OpenIntents.kt` に移し、通知とショートカットで同じものを使う
  - `MainActivity` の `onCreate` / `onNewIntent`（既存の `openFromNotification`。名前を `openFromIntent` に変える）がそのまま受ける。`EXTRA_SHORTCUT_ID` があれば `reportShortcutUsed`
- **起動のフラグ**: ランチャーはショートカットを `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK` 付きで開くとされる。その場合アクティビティは作り直され `onCreate` で受け、そうでなければ `onNewIntent` で受ける。どちらの経路も既にあるので、実機でどちらになるか（作り直しで SC-001 の 1 秒を超えないか）を quickstart で測る。超えたら作り直さない開き方を別に考える
- **Rationale**: 通知から開く経路（spec 006 R7）が、アプリが閉じている・裏にある・別の会話を開いている、のすべてを既に扱っている（spec US1-5）
- **extras は文字列だけ**: ショートカットの Intent は永続化されるので、`PersistableBundle` に入る型（文字列）だけにする

## R3. 行き先の種類と開き方

- **Decision**:
  - **ホーム**: 枠を作るときに、そのエージェントのホームの会話（`SessionListState.home`）のキーを覚え、`OpenTarget.Session(agentId, homeKey)` で開く。押したときに一覧を待たずに済む。ホームの会話はアーカイブできない（Gateway がエージェントのメインの会話を守る。`docs/gateway/protocol/rpc-session-control.md:114`）ので、片付けの対象にしない
  - **既存のセッション**: `OpenTarget.Session(agentId, sessionKey)`（既存）
  - **作業フォルダで新しい会話**: `OpenTarget.NewChat(agentId, cwd)` を足す。`ChatViewModel` は、エージェントを選び（`selectedAgentId` と `ensureLoaded`）、新しい会話を開き（`openSession(null, agentId)`）、`setNewSessionCwd(cwd)` を入れる。セッションは最初の送信で作られる（既存の新しい会話と同じ）
- **画面の側**: `AppNavigation` の `openRequests` の受け取りは、`NewChat` を `Session` と同じく「最初の画面まで戻す」に入れる。`ChatViewModel` の受け取りに `NewChat` を足す
- **Rationale**: 新しい型は 1 つだけ。ホームの会話を「ホームを開く」という別の型にすると、押したときにホームのキーを一覧から引くために待つことになる

## R4. 片付けの判定（アーカイブ・削除・エージェントの消滅）

- **Decision**: `ShortcutPublisher`（`AppContainer` で 1 つ）が、枠の設定・`SessionsRepository.sessions`・`SessionsRepository.agents` を合わせて見張り、変わるたびに純粋な関数 `reconcile(slots, lists, agents)` で判定する
  - 一覧の状態が `loading == false` かつ `error == null` のエージェントだけを判定する（FR-024。読み込んでいない・失敗したことを「消えた」と扱わない）
  - 行き先のキーが `sessions` にある → 有効。`archived` にある → アーカイブ済み。どちらにも無い → 削除済み
  - エージェントは、`agents` の読み込みが成功していて（`loading == false`・`error == null`・空でない）、その中に無いときだけ「消えた」とする
- **削除が一覧に届くことの確認**: Gateway は削除のとき `sessions.changed` を `{ sessionKey, sessionId, agentId, reason: "delete" }` の形で送り、行（`session`）を含めない（`dist/sessions-delete-DsxOP-5p.mjs:243-249`）。
  SumahoClaw は行の無い通知で読み込み済みの一覧を取り直す（`SessionsRepository.kt:168-171`）ので、取り直した一覧から消えて削除と判定できる
- **取りこぼし**: `sessions.changed` は遅い接続で捨てられることがある（`dropIfSlow`）。接続し直したときの取り直し（既存）で追いつく
- **Rationale**: 一覧は既存の契機で読み込まれ・更新される。片付けはそれを見るだけにして、読み込みを足さない（FR-021）

## R5. アイコン

- **Decision**: `Notifier` にあるエージェントの顔の読み込み（Coil の `SingletonImageLoader`・`allowHardware(false)`・`data:` と http(s) の URL）を `core/data/AgentAvatarBitmaps.kt` に移し、通知とショートカットで使い回す
  - ショートカットには丸く切らない正方形を読み、108dp 相当の正方形いっぱいに敷いて `IconCompat.createWithAdaptiveBitmap` にする（ランチャーが丸などの形に切る。外周は切られるので顔は少し寄って見える）
  - 読めなければ SumahoClaw のアイコン（`IconCompat.createWithResource(R.mipmap.ic_launcher)`）
- **見え方の確認**: 端末のランチャーで、顔が切れすぎないかを quickstart で見る。切れすぎるなら内側 72dp に縮めて周りを `surfaceContainerHigh` 相当の色で埋める（テーマの外で色を決めることになるので、そのときは相談する）

## R6. 名前

- **Decision**: 枠はマスターが付けた名前（`customLabel`、空なら無し）を持つ。表示名は `customLabel` → 行き先からの名前（ホーム = エージェント名、作業フォルダ = フォルダの最後の名前、セッション = 一覧のセッション名）の順で決める。
  短い名前と長い名前は同じ文字列にし、切り詰めはランチャーに任せる
- **追いかけ**: `customLabel` が無いセッションの枠は、一覧のセッション名が変わったら R1 の `updateShortcuts` で直す。一覧を読んでいないあいだは前の名前のまま
- **Alternatives**: 「キノに聞く」のような文を自動で作る → 動詞（聞く・依頼する）はマスターの好みなので自動にしない。書き換えで付ける

## R7. 保存と接続先の切り替え

- **Decision**: `SettingsStore` に `shortcuts`（JSON の文字列。`AGENT_LAYOUT` と同じやり方）を足し、枠のリストと、作ったときの接続先の URL（`gatewayUrl`）を入れる
  - `GatewayClient.state` が `NotConfigured`（接続情報を消した）になったら、枠を全部消して `removeAllDynamicShortcuts` と、ホーム画面の分の `disableShortcuts`
  - 接続したときの URL が `gatewayUrl` と違えば、同じく全部消す
- **Rationale**: 別の Gateway の会話を指したままにしない（spec Edge Cases）。接続情報そのものは Keystore にあるので、比べるための URL だけを設定に持つ

## R8. 設定の画面

- **Decision**: 設定に「ショートカット」の行を足し、押すと `ShortcutSettingsRoute`（1 段の画面。`AgentLayoutRoute`・`NotificationSettingsRoute` と同じ）を開く
  - 枠の一覧は `ListItem`（顔・名前・行き先の説明。説明は `labelMedium` / `onSurfaceVariant`。アーカイブ済みは「アーカイブ済み」と示し、文字を `onSurfaceVariant` 0.6 に）
  - 並べ替えは `AgentLayoutScreen` と同じ長押しのドラッグと、TalkBack の「上へ」「下へ」の操作
  - 足すのは画面下に浮くピルの「追加」（`AgentLayoutScreen` の `FloatingIconButton` と同じ置き方）。上限なら押すと Snackbar で理由を出す
  - 作る・直すはボトムシート（共通部品）。エージェント → 行き先の種類（`ButtonGroup` の 3 択）→ 作業フォルダ（既存の `FolderPickerSheet` を積む）またはセッション（そのエージェントのアクティブな一覧）→ 名前（`TextField`）。シートの下に「外す」
- **Rationale**: 枠の一覧と並べ替えは `AgentLayoutScreen` に前例がある。設定画面の中に直接並べると、並べ替えのドラッグと設定画面のスクロールがぶつかる
- **AGENTS.md との関係**: 設定は「1 画面に縦に並べる。階層の深いサブ画面を作らない」。1 段の画面は既存の エージェント・通知 と同じ扱いで、深い階層にはしない
