# OpenClaw 参考ソース

Clawdroid の実装で参照するための、OpenClaw 本体の一部の写し。**ビルドには含めない。編集しない。**

- 出典: <https://github.com/openclaw/openclaw>
- 版: `v2026.11.6`（コミット `06afb10`）— 開発用 Gateway（OpenClaw 2026.11.6）と同じ版
- ライセンス: MIT（同梱の `LICENSE`）。コードを流用するときは著作権表示を `assets/licenses/` にも残す

## 含めたもの

| パス | 内容 | 主な参照先 |
|---|---|---|
| `apps/android/app/src/main/java/` | 公式 Android アプリの Kotlin | `gateway/GatewaySession.kt`（接続・認証・再接続）、`gateway/DeviceIdentityStore.kt` と `DeviceAuthPayload.kt`（端末の署名）、`ConversationNotifications.kt`（会話の通知・通知からの返信）、`NodeForegroundService.kt`（常駐） |
| `apps/android/app/src/main/AndroidManifest.xml`, `apps/android/app/build.gradle.kts` | 権限・常駐の type・依存 | |
| `packages/gateway-protocol/src/` | Gateway のプロトコル（TypeBox スキーマ） | フレーム・各 RPC の引数と戻り値 |
| `docs/gateway/protocol/` | プロトコルの資料 | 接続・認証・RPC の一覧 |

除いたもの: 画像などのリソース、テスト以外の補助ファイル、AGENTS.md のアクセス禁止パターンに当たるファイル（`secrets.*` など）。

## 更新の手順

Gateway を上げたら、同じ版のタグで取り直す。

```sh
cd <openclaw のクローン>
git fetch --depth=1 origin tag vYYYY.M.D
git archive vYYYY.M.D LICENSE apps/android/app/src/main/java apps/android/app/src/main/AndroidManifest.xml \
  apps/android/app/build.gradle.kts packages/gateway-protocol/src docs/gateway/protocol \
  | tar -x -C <clawdroid>/reference/openclaw --exclude='*/secrets.*' --exclude='*.txt' --exclude='*.pem' --exclude='*.key' --exclude='.env*'
```

この README の「版」を書き換える。
