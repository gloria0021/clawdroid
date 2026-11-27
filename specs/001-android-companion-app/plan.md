# Implementation Plan: SumahoClaw — OpenClaw Android コンパニオンアプリ

**Branch**: `001-android-companion-app` | **Date**: 2026-11-22 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-android-companion-app/spec.md`

## Summary

OpenClaw Gateway を操作するネイティブ Android アプリを、Jetpack Compose ＋ Material 3（Expressive の
motionScheme）＋ Haze で作る。Gateway とは WebSocket のプロトコル v4 で直接つなぎ、公式 Android アプリ
（`reference/openclaw/apps/android/`）と同じ接続・認証・常駐の方式を採る。UI は ChatGPT 型の骨格
（サイドバーにエージェントのタブ、浮く入力ピル）で、動きの心地よさを最優先する。
テーマは Gerbil の実装を移植する。調査の結果は [research.md](./research.md)。

## Technical Context

**Language/Version**: Kotlin 2.4.21、JVM 21

**Primary Dependencies**: Compose BOM 2026.09.00、Material 3 1.5.0-alpha22、Material 3 Adaptive 1.3.0、
Navigation 3 1.2.1、Haze 2.1.0（haze / haze-blur / haze-glass / haze-glass-material3）、MaterialKolor 6.0.0、
OkHttp 5.5.1（WebSocket）、kotlinx.serialization 1.13.0、Coroutines 1.11.0、Room 2.8.5、DataStore 1.3.0、
Tink（秘密情報の暗号化と Ed25519 の署名）、Coil 3.6.3（画像）、multiplatform-markdown-renderer（Markdown）。
Termux terminal-view（ターミナル、R9）、highlights（コードの色分け、R10）— いずれも 2026-11-22 に承認

**Storage**: Room（会話のキャッシュ・下書き・送信待ち・読んでいた位置・ファイルの下書き）、
DataStore（設定）、Tink で暗号化した DataStore（トークン・端末の鍵）

**Testing**: JUnit 4 ＋ Robolectric（ViewModel・データ層・プロトコルの符号化）、
Gateway の偽物（MockWebServer の WebSocket）でプロトコルの往復を試す、Roborazzi（画面のスクリーンショット比較）

**Target Platform**: Android 13（API 33）以上、compileSdk / targetSdk 37

**Project Type**: mobile-app（Android 単体。サーバーは作らない）

**Performance Goals**: 操作から 100ms 以内に動きが始まる、中位機種で目に見えるコマ落ちなし（SC-003）、
1,000 件超の会話で途切れないスクロール（SC-004）、起動から一覧まで 2 秒以内（SC-002）

**Constraints**: 動きの途中でも入力を受け付ける（憲章 I）、色・文字・余白の直書き禁止（AGENTS.md）、
常駐の電池は 24 時間で 3% 以下（SC-013）、Gateway の ping 25 秒・tick 30 秒は変えられない（R6）

**Scale/Scope**: 利用者は自分の Gateway 1 つ。画面 6 つ（接続・会話・ファイル・ターミナル・自動化・設定）＋シート群。
ストーリー 9 本、要件 FR-001〜122

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原則 | 判定 | 根拠 |
|---|---|---|
| I. 動きの心地よさが最優先 | ✅ | motionScheme のトークンだけを使う。タブと一覧を同じ位置の値に追従させる（R7）。重い解析は ViewModel で先に済ませる（R10・R11）。検証は quickstart 2 |
| II. デザインは AGENTS.md に従う | ✅ | ターミナルの表示（R9）とコードの色分け（R10）は依存の追加と自作に近い部品のため相談し、2026-11-22 に承認を得た |
| III. Gateway が唯一の正 | ✅ | アプリの正本は設定・下書き・送信待ち・位置だけ（data-model）。RPC は資料とスキーマで確かめたものだけ（contracts） |
| IV. 安全な接続と秘密情報 | ✅ | Tink ＋ Keystore で暗号化（R3）。`ws://` はプライベートアドレスだけ。権限は Gateway に従う |
| V. 状態を失わない | ✅ | 下書き・送信待ち・読んでいた位置・選択を Room / DataStore に保存（data-model 3） |
| VI. 簡潔さ | ⚠️ | 依存はやや多い。それぞれ「数行では書けない」ものに限った（R15）。下の Complexity Tracking |
| 技術的制約（API 33・Compose・Nav3・Adaptive） | ✅ | Technical Context のとおり |

**Phase 1 後の再確認**: data-model・contracts で新たな違反なし。要相談の 2 件は承認済み。

## Project Structure

### Documentation (this feature)

```text
specs/001-android-companion-app/
├── plan.md              # この文書
├── research.md          # 調査（R1〜R15）
├── data-model.md        # データの形と保存先
├── quickstart.md        # 検証の手順
├── contracts/
│   ├── gateway-rpc.md   # 使う RPC・イベントの一覧
│   └── ui-navigation.md # 画面と遷移
└── tasks.md             # /speckit-tasks で作る
```

### Source Code (repository root)

パッケージは `jp.miyavi.android.sumahoclaw`（Gerbil の命名に合わせた仮。公開前に確定）。
1 つの `app` モジュールの中を、Gerbil と同じ `core/` ＋ `feature/` の構成で分ける。

```text
app/src/main/kotlin/jp/miyavi/android/sumahoclaw/
├── MainActivity.kt
├── SumahoClawApp.kt                  # Application。接続・DB の初期化
├── navigation/                      # Navigation 3 の行き先、list-detail
├── core/
│   ├── designsystem/                # Theme.kt（4 テーマ）、Spacing.kt、Motion.kt、Glass.kt（hazeSurface）、共通部品
│   ├── gateway/                     # GatewayClient（WebSocket・接続の状態）、Frames、DeviceIdentity、RPC の型
│   ├── security/                    # Tink（AEAD・Ed25519）、SecretStore
│   ├── data/                        # Repository（Agents / Sessions / Chat / Models / Usage / Files / Cron / Terminal）
│   ├── database/                    # Room（キャッシュ・下書き・送信待ち・位置）
│   ├── settings/                    # DataStore（テーマ・通知・選択）
│   └── notifications/               # 常駐サービス、通知チャンネル、会話の通知、通知からの返信
└── feature/
    ├── connect/                     # US1 接続・承認待ち
    ├── chat/                        # US1・US3・US4 サイドバー・会話・入力ピル・モデル・使用量
    ├── files/                       # US5
    ├── terminal/                    # US6
    ├── automations/                 # US8
    └── settings/                    # US7・US9 と通知の設定

app/src/test/kotlin/...              # ViewModel・Repository・プロトコル（偽の Gateway）
app/src/main/res/drawable/ic_huge_*.xml   # Hugeicons Stroke Rounded
app/src/main/assets/licenses/        # Hugeicons（MIT）、OpenClaw（MIT、流用した部分）ほか
reference/openclaw/                  # 参考ソース（ビルド対象外。README に版と更新手順）
```

**Structure Decision**: 単一の `app` モジュール。各機能は `feature/<名前>` の中で
「ViewModel をつなぐ部分」と「状態とコールバックだけを受ける `*Content`」に分ける（AGENTS.md 1）。
データ層はすべて `core/data` の Repository を通し、画面から `GatewayClient` を直接呼ばない。
Gerbil の `core/designsystem` を出発点にする。

## 実装の順序（tasks の骨子）

ストーリーの優先度に合わせ、各段階で動くものを確かめてから次へ進む。

1. **土台**: Gradle・テーマ（Gerbil から移植）・Glass・Motion・Spacing・Hugeicons・偽の Gateway のテスト基盤
2. **US1 接続と会話**: GatewayClient（接続・署名・承認待ち・再接続・tick の監視）→ エージェントのタブ ＋ セッション一覧 → 会話（履歴・送信・逐次表示・停止・承認・ツールの行・Markdown）→ 下書きと位置の保存 → 音声入力
3. **US2 通知**: 常駐サービス → 裏でのイベントの絞り込み → エージェントごとのチャンネル・MessagingStyle → 通知からの返信（送信待ちの列）→ 電池の計測
4. **US3** 新規セッションとフォルダ → **US4** モデル・エフォート・使用量
5. **US5** ファイル → **US6** ターミナル
6. **US7** 再起動 → **US8** 自動化 → **US9** テーマの設定画面
7. 仕上げ: 大画面の 2 ペイン、文字 200%、AGENTS.md 第 4 章のチェックリスト

## リスクと先に確かめること

| リスク | 確かめ方 | 外れたとき |
|---|---|---|
| 裏で `sessions.changed` だけでは応答の完了を取りこぼす | 段階 3 の最初に、偽の Gateway と実 Gateway の両方で確かめる | 裏でも開いている会話の `sessions.messages.subscribe` を残す（電池は増える） |
| 電池が SC-013 を超える（ping 25 秒・tick 30 秒） | quickstart 4 | モバイル回線のときだけ定期的につなぎ直す方式を追加 |
| 承認後の `deviceToken` で `operator.admin` が落ちる | 段階 2 の接続の実装時 | ターミナル・再起動を「権限がない」として示し、Gateway 側の設定を案内 |
| Gateway の版が上がりプロトコルが変わる | 接続時に `hello-ok.server.version` と `features.methods` を記録 | 使う RPC が無ければその機能を隠す。`reference/openclaw` を取り直す |
| Play ストアの申告（常駐の type） | 後回し（マスターの判断） | 公開の前に `connectedDevice` / `remoteMessaging` / `specialUse` を比べる |

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| ターミナルの表示に外部の部品（Termux terminal-view を `AndroidView` で） | VT100 系の端末の解釈は数千行規模 | 自前で書くのは割に合わず、品質も劣る。Compose だけの端末は実績のある実装がない |
| コードの色分けのライブラリ | 言語ごとの字句解析が要る | 色分けなし（読みにくい）、WebView のエディタ（動きとガラスが崩れる） |
| Tink | 秘密情報の暗号化と Ed25519 の署名を 1 つで賄う | EncryptedSharedPreferences は非推奨。BouncyCastle では暗号化を別に作る必要がある |
