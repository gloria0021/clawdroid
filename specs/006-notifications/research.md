# Research: 通知（常駐の接続で受け取る）

調べた元: インストール済みの OpenClaw 2026.11.6（`docs/gateway/protocol/*`・`dist/server-cron-*.mjs`）、`reference/openclaw/packages/gateway-protocol/src/schema/*`、このリポジトリの実装。

## R1. 常駐の方式と種類

- **Decision**: `LifecycleService` を継承した `ConnectionService` を、`foregroundServiceType="connectedDevice"` のフォアグラウンドサービスとして動かす。
  サービスは接続そのものを持たず、アプリの `GatewayClient`（`AppContainer` の 1 つ）が生きていられるようにプロセスを保つだけ。
  常駐の通知（低い重要度のチャンネル）に接続の状態を出し、`GatewayClient.state` に合わせて書き換える。
- **始める・止める**: 常駐がオンで接続情報があれば、アプリの起動時（`ClawdroidApp.onCreate`）とアプリが裏に回ったとき（`ProcessLifecycleOwner` の ON_STOP）に始める。
  オフにした・接続情報を消した・接続先を切り替えたら止める。Android 12 以降は裏からフォアグラウンドサービスを始められないので、前にいるうちに始めておく
  （ON_START で始め、ON_STOP では止めない）。
- **端末の起動**: `BOOT_COMPLETED` の受け手から始める。connectedDevice は Android 15 の「起動時に始められない種類」に入っていない
- **Rationale**: 公式の Android アプリと同じ種類。マニフェストには `FOREGROUND_SERVICE_CONNECTED_DEVICE` と、その前提の `CHANGE_NETWORK_STATE` がすでにある。
  dataSync は Android 15 で 1 日 6 時間の上限があり常駐に使えない。
- **Alternatives**: `specialUse` → Play の審査で個別の説明が要る。`remoteMessaging` → 端末間でメッセージを運ぶ用途の種類で、Gateway との接続の維持には公式と揃える方が説明しやすい。

## R2. 応答の完了の見つけ方

- **Decision**: 既存の `sessions.subscribe` が届ける `sessions.changed`（行全体が入る）で、会話ごとの「実行中か」（`hasActiveRun` または `status` が running / queued）を覚えておき、
  **実行中 → 止まった** に変わったら完了とみなす。そのとき `chat.history`（`limit` を小さく）で最後の応答を読み、通知の文にする。
  - 最後の記録が利用者の文・エラー・中断だけなら通知しない（spec Edge Cases）。途中の文があれば通知する
  - 同じ会話で、前に通知した応答（メッセージの識別子）と同じなら出さない（つなぎ直しの重複を防ぐ）
- **つなぎ直し**: 切れていたあいだの完了は `sessions.changed` が来ないので、つなぎ直したら `sessions.list` を 1 回読み、
  切れる前に実行中だった会話が止まっていれば同じ手順で通知する（FR-005）
- **画面で開いている会話**: アプリが前にあり、その会話を開いているときは出さない（FR-011）。アプリが裏なら、開いていた会話でも出す
- **Rationale**: 会話ごとの文字の流れを購読しなくても、全会話の状態の変化が届く（アプリはすでに購読している）。通知の文は記録から読むので、文字の流れを組み立て直さずに済む。
- **Alternatives**: `session.message` の本文で見つける → 応答が途中の一言とツールの前後に分かれて届くので「完了」を判断しにくい。`chat` の `final` → 開いている会話の分しか届かない。

## R3. 裏での受け取りを絞る（FR-004）

- **Decision**:
  - 開いている会話の `sessions.messages.subscribe` は、アプリが裏に回ったら `unsubscribe` し、前に戻ったら購読し直して履歴を取り直す（`ChatSession` に `pause()` / `resume()`）
  - connect の `caps` に `chat-only-assistant-text` を足す。アプリは応答の文字を `chat` の流れだけから描いているので（`ChatRepository` の注記）、`agent` の文字の重複を受け取らずに済む
- **Rationale**: 文字ごとの流れは開いている会話だけに届くので、それを止めれば裏では行の変化と承認・自動化の出来事だけになる。
- **測る**: 電池の消費は実機で 1 時間の待ち受けを測る（quickstart）。超えたら 001 の Assumptions どおり、モバイル回線のときだけの方式を別に考える

## R4. 承認の通知とボタン

- **Decision**: 既存の `ApprovalsRepository.requests`（アプリ全体の承認待ちの一覧。requested / resolved と、つなぎ直したときの読み直しを済ませている）を見張り、
  一覧に増えたものを通知し、消えたもの（ほかで解決・期限切れ）の通知を消す。
  - 通知は `BigTextStyle` に全文（コマンド・作業フォルダや対象・エージェント・会話・警告）を出す。Android の通知の本文は端末が出せる量に限りがあるので、
    長いときは末尾に「続きはアプリで」を付ける（R4-1）
  - ボタンは `allowedDecisions` にあるものだけ（1 回だけ許可・常に許可・拒否）。`NotificationCompat.Action.Builder.setAuthenticationRequired(true)` でロックの解除を求める（FR-024。API 31+）
  - ボタンは `ApprovalActionReceiver`（`BroadcastReceiver`、`goAsync()`）が受け、`ApprovalsRepository.resolve()` を呼ぶ。結果で通知を「許可しました」などに書き換えてから少しあとに消す。
    送れなければ理由を出し、通知を押すとアプリの承認の画面を開く
  - 通知を押す（ボタン以外）とアプリを開き、既存の承認の表示（アプリ全体のシート）が出る
- **R4-1 全文の上限**: `BigTextStyle` の本文は、端末の表示で切れる。目安として 5,000 文字を超える分は切り、「続きはアプリで」とする
- **Rationale**: 承認の取得・解決・つなぎ直しはすでにアプリにあり、通知はその見た目を増やすだけ。

## R5. 自動化の失敗

- **Decision**: `cron` の出来事で `action == "finished"` かつ結果が失敗（出来事の `status` が `error`、無ければ `job.state.lastRunStatus` / `lastStatus` が `error`）のときに通知する。
  文は自動化の名前（`job.name`）と失敗の 1 行（出来事の `error` があれば冒頭）。押すとその自動化の詳細を開く
- **Rationale**: Gateway は自動化の実行の終わりを `cron` で全接続に配る（`dist/server-cron-*.mjs`）。アプリは自動化の画面のためにすでに受けている。

## R6. チャンネルと通知の形

- **Decision**: チャンネルは 4 種類。
  - 常駐（重要度 MIN。音・振動なし）
  - 応答（エージェントごとに 1 つ。`replies-<agentId>`。グループ「応答」の下に作り、音・オン・オフは端末の設定で変えられる。FR-031）
  - 承認待ち（重要度 HIGH）
  - 自動化の失敗（重要度 DEFAULT）
  - 応答の通知は会話ごとに id を固定して書き換える（FR-012）。エージェントごとに `setGroup` でまとめ、要約の通知を 1 つ置く
  - 本文は Markdown の記号を除いた文の冒頭（目安 300 文字）。画像だけの応答は「画像」と示す
  - ~~ロック画面の表示は設定（`lockScreenContent`）に従い、`VISIBILITY_PRIVATE` と公開用の伏せた版を付ける（FR-032）~~ → 2026-11-26: 設定は削除。端末の設定に任せ、公開用の伏せた版だけ常に付ける
- **通知のアイコン**: Hugeicons から単色の VectorDrawable を 1 つ（`ic_huge_*`）。

## R7. 通知から開く行き先

- **Decision**: 通知の `PendingIntent` は `MainActivity` に `ACTION_VIEW` と付加情報（`session` = 会話の key・`agent`、または `approval`、`automation` = 自動化の id）を付けて開く。
  `MainActivity` の `onNewIntent` / 起動時に読み、既存の画面の行き先（会話を開く・自動化の詳細）へ進める。応答の位置は、会話を開くと最新が見える既存の振る舞いに任せる
  （最後の応答 = 通知の応答なので、spec の「その応答が見える位置」を満たす）

## R8. 設定と状態の表示

- **Decision**: 設定画面に「通知」のセクションを足す（`ListItem` ＋ `Switch`）。
  - 常時接続（`backgroundConnection`。2026-11-26 から既定オフ）、応答・承認待ち・自動化の失敗（`notify*`）。ロック画面の項目（`lockScreenContent`）は 2026-11-26 に削除
  - 状態の行: 通知の許可（無ければ「許可する」で要求。拒否済みなら端末の設定を開く）、電池の最適化（対象なら「最適化から外す」で `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`。権限はマニフェストに既にある）、常駐の状態
  - 「テスト通知」の行
  - エージェントごとの音は「端末の通知の設定を開く」の行から（`Settings.ACTION_APP_NOTIFICATION_SETTINGS`）
- **Play の申告**: `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` は Play の方針で用途の説明が要る（常駐の通知を届ける用途として申告する）。リリースのときに 003 の手順に足す
