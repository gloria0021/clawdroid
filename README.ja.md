# Clawdroid

[English](README.md) | 日本語

[OpenClaw](https://github.com/openclaw/openclaw)の非公式Androidクライアントです。OpenClawでのスマホAIコーディングを快適にするために作りました。ファイルツリー、ファイルビューワー、ターミナル、並行開発(WorkTree)のしやすさを意識しています。

※ OpenClawの全機能を網羅することは目指していません

[デモ動画を見る](docs/media/demo-movie.mp4)

## 対応バージョン

- OpenClaw Gateway: 2026.11.10
- Android 13 以上

## 特徴
- Material3 Expressiveデザインのネイティブアプリ
- テーマ機能
- Gateway接続機能（Tailscale推奨）
- サイドパネルで各エージェントにクイックアクセス
- エージェント設定
- セッション表示
- モデル・エフォートの切り替え、使用量・ホストのリソース確認
- セッションのショートカット作成
- ファイルツリー（日付/サイズ/アップロード/ファイル・フォルダ作成）
- ファイルビューアー（表示/編集/削除）
- ターミナル
- 並行開発ボタン(Worktreeをクイック作成)
- 自動化の一覧・設定・実行
- 通知

## スクリーンショット

画像は英語表示です。アプリは日本語にも対応しています。

### 設定とテーマ

<p>
  <img src="docs/media/settings.png" width="220" alt="外観・テーマ・透過効果などの設定">
</p>

<details>
<summary>テーマのライト・ダーク比較</summary>


| テーマ | ライト | ダーク |
| --- | --- | --- |
| OpenClaw | <img src="docs/media/theme-openclaw-light.png" width="220" alt="OpenClaw — light"> | <img src="docs/media/theme-openclaw-dark.png" width="220" alt="OpenClaw — dark"> |
| Monochrome | <img src="docs/media/theme-monochrome-light.png" width="220" alt="Monochrome — light"> | <img src="docs/media/theme-monochrome-dark.png" width="220" alt="Monochrome — dark"> |
| Dynamic(Wallpaper) | <img src="docs/media/theme-dynamic-light.png" width="220" alt="Dynamic Color — light"> | <img src="docs/media/theme-dynamic-dark.png" width="220" alt="Dynamic Color — dark"> |
| カスタム | <img src="docs/media/theme-custom-light.png" width="220" alt="カスタム — light"> | <img src="docs/media/theme-custom-dark.png" width="220" alt="カスタム — dark"> |

</details>

### サイドパネル
- チャットモードと開発モードが切替可能
- 開発モードはGitDiff、ブランチ、ワークツリー表示
- エージェントはのクイック切り替えタブ有り（最大4体）

<p>
  <img src="docs/media/side-panel-chat.png" width="220" alt="チャット一覧">
  <img src="docs/media/side-panel-dev.png" width="220" alt="プロジェクト別の開発一覧">
  <img src="docs/media/side-panel-agent-settings.png" width="220" alt="表示するエージェントの設定">
</p>

### セッション画面

<p>
  <img src="docs/media/session-menu.png" width="220" alt="会話のメニュー">
  <img src="docs/media/session-model-usage.png" width="220" alt="モデル・使用量・ホストのリソース">
</p>

### ファイル・ターミナル・並行開発

<p>
  <img src="docs/media/file-tree-menu.png" width="220" alt="ファイルツリーと操作メニュー">
  <img src="docs/media/file-editor.png" width="220" alt="ファイルの表示・編集">
  <img src="docs/media/terminal.png" width="220" alt="ターミナル">
  <img src="docs/media/parallel-work-create.png" width="220" alt="並行開発用のワークツリー作成">
</p>

### エージェント設定

<p>
  <img src="docs/media/agent-overview.png" width="220" alt="エージェントの概要">
  <img src="docs/media/agent-persona.png" width="220" alt="性格を定義するファイル">
  <img src="docs/media/agent-skills.png" width="220" alt="スキルの管理">
</p>

### 自動化

<p>
  <img src="docs/media/automation-list.png" width="220" alt="自動化の一覧">
  <img src="docs/media/automation-details.png" width="220" alt="自動化の詳細と実行設定">
</p>

### ホーム画面のショートカット

<p>
  <img src="docs/media/launcher-shortcuts.png" width="220" alt="ホーム画面から開くショートカット">
  <img src="docs/media/launcher-shortcut-editor.png" width="220" alt="ショートカットの編集">
</p>

## プライバシーポリシー

[プライバシーポリシー](docs/privacy-policy.md)（日本語 / English）

## ライセンス

[MIT License](LICENSE)

同梱しているライブラリ・フォント・アイコンの許諾は、アプリの「設定 → 情報 → オープンソースのライセンス」で読めます。`reference/openclaw/` は [OpenClaw](https://github.com/openclaw/openclaw)（MIT License）のソースと資料の写しで、OpenClaw の許諾（`reference/openclaw/LICENSE`）に従います。
