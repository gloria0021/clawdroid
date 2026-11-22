# Research: Clawdroid — OpenClaw Android コンパニオンアプリ

**Date**: 2026-11-22 | **Plan**: [plan.md](./plan.md)

調査の根拠は、開発用 Gateway と同じ版（OpenClaw 2026.11.6、コミット `06afb10`）の資料とソース。
写しは `reference/openclaw/` にある（以下、`ref/` と略す）。

---

## R1. Gateway との接続方式

- **Decision**: OkHttp の WebSocket で Gateway のプロトコル v4 に接続する。接続は `connect.challenge` を待ってから `connect` を送る。role は `operator`、要求する scope は `operator.read` / `operator.write` / `operator.admin` / `operator.approvals`。`auth.token` にゲートウェイトークン（共有トークン）を入れる
- **Rationale**: 公式 Android アプリと同じ構成（`ref/apps/android/.../gateway/GatewaySession.kt`）。ターミナル（`terminal.*`）と再起動は `operator.admin` が要る。共有トークンでの接続は「信頼された operator」として扱われる（`docs/gateway/operator-scopes`）
- **Alternatives considered**: Ktor の WebSocket（依存が増えるだけで利点がない）、HTTP の RPC（`admin-http-rpc` は購読・イベントを扱えない）

## R2. 端末の識別と承認（ペアリング）

- **Decision**: 端末ごとに Ed25519 の鍵の組を作って保存し、`connect.params.device` に `id`（公開鍵の指紋）・`publicKey`・`signature`・`signedAt`（challenge の `ts`）・`nonce` を入れる。署名する内容は v3 形式（`ref/.../gateway/DeviceAuthPayload.kt` の `buildV3`）。接続が `PAIRING_REQUIRED`（`waitForResolution: true`）で返ったら「承認待ち」を表示してソケットを保ち、`device.pair.resolved` を受けたら接続し直す。承認後に返る `hello-ok.auth.deviceToken` を保存し、次回からはそれで接続する
- **Rationale**: 資料（`docs/gateway/protocol/auth.md`）では、operator の接続も原則として端末の識別が必須。識別を省くと scope が空になる。Tailscale や LAN からの接続は、同じホストでも「リモート」扱いで承認が要る。仕様の FR-004（端末の承認待ちを区別して示す）と一致する
- **署名の実装**: Tink の `Ed25519Sign` / `Ed25519Verify`。秘密鍵は Tink の AEAD（Android Keystore の鍵で暗号化）で包んで保存する。公式アプリは BouncyCastle を使うが、Tink なら秘密情報の暗号化と署名を 1 つの依存で済ませられる
- **Alternatives considered**: Android Keystore の Ed25519（API 33 の全端末で使える保証がない）、BouncyCastle（暗号化に別の仕組みが要る）

## R3. 秘密情報の保存

- **Decision**: ゲートウェイトークン・端末のトークン・Ed25519 の秘密鍵は、Tink の AEAD で暗号化して DataStore に保存する。AEAD の鍵は Android Keystore に置く。バックアップの対象から外す（`dataExtractionRules`）
- **Rationale**: 憲章 IV（認証情報は Keystore で保護）。`androidx.security:security-crypto`（EncryptedSharedPreferences）は非推奨
- **Alternatives considered**: EncryptedSharedPreferences（非推奨）、平文の DataStore（憲章違反）

## R4. 使う RPC とイベント（機能ごと）

公式アプリと Control UI が使っているものに限る。詳細は [contracts/gateway-rpc.md](./contracts/gateway-rpc.md)。

| 機能 | RPC / イベント |
|---|---|
| エージェントのタブ | `agents.list`、`agent.identity.get` |
| セッション一覧 | `sessions.subscribe`（初回の一覧＋変更イベント `sessions.changed`）、`sessions.list`、`sessions.patch`（改名・アーカイブ・既読） |
| 会話 | `chat.startup`、`chat.history`、`chat.message.get`、`chat.send`、`chat.abort`、`sessions.messages.subscribe`（`includeApprovals`）、イベント `chat` / `agent` |
| 新規セッションとフォルダ | `sessions.create`（`cwd`）、`fs.listDir`（`operator.write`） |
| モデル・エフォート | `models.list`、`sessions.patch`（`model` / `thinkingLevel`） |
| 使用量 | `usage.status`（`usage-refreshing` の cap を出す）、`sessions.usage` |
| 承認 | `exec.approval.*`、`plugin.approval.*`、`approval.get` / `approval.resolve` |
| ファイル | `sessions.files.list` / `get` / `set`（`expectedHash` で衝突検出）、`agents.workspace.list` / `get` |
| ターミナル | `terminal.open` / `input` / `resize` / `close` / `list` / `attach`、イベント `terminal.data` / `terminal.exit` |
| 再起動 | `gateway.restart.request` |
| 自動化 | `cron.list` / `get` / `status` / `add` / `update` / `remove` / `run` / `runs`、イベント `cron.inventory.changed` |

- **未確定**: 各 RPC の引数と戻り値の細部は、実装時に `ref/packages/gateway-protocol/src/schema/` の TypeBox スキーマで確かめる。推測で項目を作らない（憲章 III）

## R5. 通知（常駐）

- **Decision**: フォアグラウンドサービス（type は `connectedDevice`、公式アプリと同じ）で接続を保つ。常駐の通知は重要度 MIN のチャンネルに出す。会話の通知はエージェントごとの通知チャンネル（`NotificationChannelGroup` ＝ Gateway、チャンネル ＝ エージェント）と `MessagingStyle` で作り、`RemoteInput` で返信する。返信は Room の送信待ちの列に入れ、接続後に `chat.send`（冪等キー付き）で送る
- **Rationale**: 公式アプリの `ConversationNotifications.kt` と同じ考え方。エージェントごとのチャンネルにすると、オン・オフと通知音を Android の設定で個別に変えられる（FR-102）
- **Play ストアでの申告**: 後回し（マスターの判断、2026-11-22）。まず手元での安定運用を優先する。申告するときに type を見直す（`connectedDevice` は「外部機器との接続」が本来の用途。チャットの窓口だけの本アプリでは、`remoteMessaging` や `specialUse` も比べる）
- **Alternatives considered**: FCM ＋ 自前の中継、UnifiedPush（いずれも 2026-11-22 に却下。経緯は spec の Assumptions）

## R6. 電池（生存確認の間隔）

- **事実**（v2026.11.6 のソース）: Gateway は WebSocket の ping を **25 秒ごと**に送る（`src/gateway/websocket-keepalive.ts`）。さらに `tick` イベントを 30 秒ごとに全クライアントへ流す（`src/gateway/server-maintenance.ts`）。公式アプリは OkHttp の `pingInterval(30s)` も設定している
- **Decision**: アプリ側から ping は送らない（OkHttp の `pingInterval` は 0。サーバーの ping に pong を返すだけ）。裏にいるあいだは `sessions.messages.subscribe` を解除し、セッションの変更（`sessions.changed`）と承認・自動化のイベントだけを受ける。応答の完了は `sessions.changed` の実行中フラグ（`hasActiveRun`）の変化で検知し、必要な分だけ `chat.history` で取りに行く。`tick` が `tickIntervalMs × 2` 届かなければ接続を閉じて再接続する（公式クライアントと同じ判定）
- **Rationale**: サーバー側の 25 秒・30 秒はアプリから変えられない。アプリ側で増やす通信をなくし、イベントの量を絞る
- **検証**: quickstart の「電池の計測」で、Wi-Fi とモバイル回線それぞれ 24 時間を `dumpsys batterystats` で測る。SC-013（3% 以下）を超えたら、モバイル回線のときだけ裏では定期的につなぎ直す方式を追加で検討する

## R7. UI の骨格と動き

- **Decision**: ChatGPT 型の骨格を、AGENTS.md の部品で組む
  - 左のサイドバー: `ModalNavigationDrawer`（スマホ）／ Material 3 Adaptive の list-detail（大画面）。上部にエージェントのタブ（`PrimaryScrollableTabRow` をガラスの面に載せる）、下にセッションの一覧
  - タブの切り替え: 一覧は `HorizontalPager` で横に送る。タブの選択面と一覧が同じ位置の値に追従するので、指でのスワイプとタブのタップが同じ動きになる
  - 上部: 浮く丸いボタン（左にメニュー、右に新規チャットと ⋮）。TopAppBar は使わず、ガラスの面に載せる
  - 下部: 浮くガラスのピル型の入力欄（左に添付、中央に入力、右にマイク／送信）。モデルとエフォートはピルの上の行
  - 会話: 利用者のメッセージは右寄せの角丸の面、応答は全幅の素の文字
- **Rationale**: 憲章 I・II。タブと一覧を同じ値に追従させると、途中での取り消しやスワイプからの引き継ぎが自然につながる（AGENTS.md 2.8）
- **要相談**: AGENTS.md 2.7 は「標準部品で表現できないものは作る前に相談する」としている。浮く丸いボタン群は「ガラス風の面」の範囲に入ると判断した。下の R9・R10 の 2 つは自作に近いので、tasks の前に確認する

## R8. テーマ

- **Decision**: Gerbil（`~/Dev/workspace/boards/app/src/main/kotlin/jp/miyavi/android/gerbil/core/designsystem/Theme.kt`）の仕組みを移植する
  - OpenClaw: 種色は Control UI の赤（ライト `#dc2626`／ダーク `#ff5c5c`）。MaterialKolor の `TonalSpot`
  - モダン: MaterialKolor の `PaletteStyle.Monochrome`。`error` だけは標準のエラー色を残す
  - ダイナミック: `dynamicLight/DarkColorScheme`（Android 12 以上なので最小 SDK 33 で常に使える）
  - カスタム: `PaletteStyle.Fidelity`（無彩色に色が付かない）＋補助の色だけコントラストを上げる（Gerbil の `withContrastedSecondary`）
  - 背景を `primaryContainer` へ少し寄せる処理、リンクの色のコントラストを 4.6 以上に保つ処理も Gerbil から移す
  - ガラス / ぼかし: Gerbil の `FloatingGlassBar.kt` の `hazeSurface` を移植し、不透明度は AGENTS.md 2.10 の固定値にする（Gerbil のスライダーは移さない）
- **Rationale**: 実績のある実装を流用し、FR-084〜088 を満たす

## R9. ターミナルの表示（承認済み 2026-11-22）

- **Decision**: Termux の `terminal-emulator` / `terminal-view`（Apache-2.0）を `AndroidView` で埋め込む。特殊キーの行（Ctrl・Esc・Tab・矢印）は Compose で作り、ピルの上に置く
- **Rationale**: VT100 系の端末の解釈は数行では書けず、実績のある実装を使うのが安全
- **承認**: 依存の追加と `AndroidView` の部品 1 つを、マスターが 2026-11-22 に了承

## R10. コードの表示・編集（承認済み 2026-11-22）

- **Decision**: 表示と編集は `BasicTextField`（等幅）。色分けは Kotlin 製の軽い字句解析ライブラリ（`dev.snipme:highlights`）を ViewModel で走らせ、結果を `AnnotatedString` で渡す（AGENTS.md 1：重い計算は ViewModel で）。大きなファイル（目安 1MB 超）は読み取り専用にし、色分けしない
- **Alternatives considered**: WebView でエディタを動かす（動きとガラスが崩れる）、自前の字句解析（言語ごとに作るのは割に合わない）

## R11. Markdown

- **Decision**: Gerbil と同じ `multiplatform-markdown-renderer`（Material 3 版）。解析は ViewModel で済ませ、項目の Composable では行わない
- **Rationale**: Gerbil で実績がある。表・コード・リンクに対応

## R12. 自動化の cron 式

- **Decision**: 保存前の構文の確認と「次の実行予定」は、端末内の小さな解析で行う（5 項目・6 項目の cron 式と、タイムゾーン）。最終的な判定は Gateway の `cron.add` / `cron.update` の結果に従う
- **未確定**: 解析は `cron-utils` を使うか自前で書くかを、tasks で依存の大きさを見て決める

## R13. 音声入力

- **Decision**: `SpeechRecognizer`（アプリ内で認識し、入力欄の中で聞き取り中の動きを出す）。`SpeechRecognizer.isRecognitionAvailable()` が false ならマイクを出さない。`RECORD_AUDIO` の許可は初めてマイクを押したときに求める
- **Alternatives considered**: `RecognizerIntent`（システムの画面が出て、動きの一貫性が崩れる）

## R14. ファイルのダウンロード

- **Decision**: `MediaStore.Downloads` に保存する（Android 10 以上は権限不要）。保存後に Snackbar で保存先を示す

## R15. 依存の版

- **Decision**: Gerbil（`~/Dev/workspace/boards/gradle/libs.versions.toml`）で動作実績のある版を初期値にする。主なもの: AGP 9.5.1、Kotlin 2.4.21、Compose BOM 2026.09.00、Material 3 1.5.0-alpha22（motionScheme のため）、Adaptive 1.3.0、Navigation 3 1.2.1、Haze 2.1.0、MaterialKolor 6.0.0、OkHttp 5.5.1、Room 2.8.5、DataStore 1.3.0、Coil 3.6.3。compileSdk / targetSdk 37、minSdk 33、JVM 21
- **Rationale**: 同じマスターの環境でビルドと実機確認が済んでいる組み合わせ
