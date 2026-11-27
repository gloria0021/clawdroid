# SumahoClaw

[English](README.md) | 日本語

[OpenClaw](https://github.com/openclaw/openclaw)の非公式Androidクライアントです。OpenClawでのスマホAIコーディングを快適にするために作りました。ファイルツリー、ファイルビューワー、ターミナル、並行開発(WorkTree)のしやすさを意識しています。
> OpenClawの全機能を網羅することは目指していません

https://github.com/user-attachments/assets/460b1cbb-cf53-4374-a51c-96633a2e692d

## 対応バージョン

- OpenClaw Gateway: 2026.11.10
- Android 13 以上

## 特徴

Material3 Expressiveデザインのネイティブアプリです。

### テーマ

| テーマ名 | ライト | ダーク |
| --- | --- | --- |
| OpenClaw | <img src="docs/media/theme-openclaw-light.png" width="220" alt="OpenClaw — light"> | <img src="docs/media/theme-openclaw-dark.png" width="220" alt="OpenClaw — dark"> |
| Monochrome | <img src="docs/media/theme-monochrome-light.png" width="220" alt="Monochrome — light"> | <img src="docs/media/theme-monochrome-dark.png" width="220" alt="Monochrome — dark"> |
| Dynamic(Wallpaper) | <img src="docs/media/theme-dynamic-light.png" width="220" alt="Dynamic Color — light"> | <img src="docs/media/theme-dynamic-dark.png" width="220" alt="Dynamic Color — dark"> |
| カスタム | <img src="docs/media/theme-custom-light.png" width="220" alt="カスタム — light"> | <img src="docs/media/theme-custom-dark.png" width="220" alt="カスタム — dark"> |

### 接続

Gateway接続はURLとトークンをいれてApprove。
Tailscale推奨です。

<p>
  <img src="docs/media/gateway-connection.png" width="220" alt="Gateway 接続画面">
</p>

### サイドパネル

- チャットモードと開発モードを切り替えられます。
- 開発モードでは Git の差分・ブランチ・ワークツリーを確認できます。
- 最大4体のエージェントをタブですばやく切り替えられます。

<p>
  <img src="docs/media/side-panel-chat.png" width="220" alt="チャット一覧">
  <img src="docs/media/side-panel-dev.png" width="220" alt="プロジェクト別の開発一覧">
  <img src="docs/media/side-panel-agent-settings.png" width="220" alt="表示するエージェントの設定">
</p>

### セッション画面

- セッションの会話を表示しメニューから各機能へアクセスできます。
- モデル・エフォートを切り替え、使用量やホストのメモリ・ディスクも確認できます。

<p>
  <img src="docs/media/session-menu.png" width="220" alt="会話のメニュー">
  <img src="docs/media/session-model-usage.png" width="220" alt="モデル・使用量・ホストのリソース">
</p>

### ファイル・ターミナル・並行開発

- ファイルツリーで日付・サイズを確認し、アップロードやファイル・フォルダの作成ができます。
- ファイルビューアーでファイルを表示・編集・削除できます。
- ターミナルを利用できます。
- 並行開発ボタンからワークツリーをすばやく作成できます。

<p>
  <img src="docs/media/file-tree-menu.png" width="220" alt="ファイルツリーと操作メニュー">
  <img src="docs/media/file-editor.png" width="220" alt="ファイルの表示・編集">
  <img src="docs/media/terminal.png" width="220" alt="ターミナル">
  <img src="docs/media/parallel-work-create.png" width="220" alt="並行開発用のワークツリー作成">
</p>

### エージェント設定

エージェントの概要、正確スキルを確認できます。

<p>
  <img src="docs/media/agent-overview.png" width="220" alt="エージェントの概要">
  <img src="docs/media/agent-persona.png" width="220" alt="性格を定義するファイル">
  <img src="docs/media/agent-skills.png" width="220" alt="スキルの管理">
</p>

### 自動化

自動化の確認・変更・実行ができます。

<p>
  <img src="docs/media/automation-list.png" width="220" alt="自動化の一覧">
  <img src="docs/media/automation-details.png" width="220" alt="自動化の詳細と実行設定">
</p>

### ホーム画面のショートカット

エージェントやセッションを開くショートカットを作成・編集しホーム画面からすばやくアクセスできます。

<p>
  <img src="docs/media/launcher-shortcuts.png" width="220" alt="ホーム画面から開くショートカット">
  <img src="docs/media/launcher-shortcut-editor.png" width="220" alt="ショートカットの編集">
</p>

## プライバシーポリシー

[プライバシーポリシー](docs/privacy-policy.md)（日本語 / English）

## ライセンス

[MIT License](LICENSE)

同梱しているライブラリ・フォント・アイコンの許諾は、アプリの「設定 → 情報 → オープンソースのライセンス」で読めます。`reference/openclaw/` は [OpenClaw](https://github.com/openclaw/openclaw)（MIT License）のソースと資料の写しで、OpenClaw の許諾（`reference/openclaw/LICENSE`）に従います。
