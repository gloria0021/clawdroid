# SumahoClaw プライバシーポリシー / Privacy Policy

最終更新日 / Last updated: 2026-11-24

[日本語](#日本語) ・ [English](#english)

---

## 日本語

SumahoClaw（以下「本アプリ」）は、利用者が自分で運用している OpenClaw Gateway に接続して、AI エージェントとの会話・ファイル・ターミナル・自動化を操作するための Android アプリです。本アプリは OpenClaw の公式アプリではありません。本アプリの作者（miyavi）は、利用者の個人情報を集めません。

### 集める情報

本アプリは、作者のサーバーに情報を送りません。作者のアカウントの作成も不要です。広告・解析・トラッキングの仕組みは入っていません。

### 端末の中だけに保存する情報

次の情報は、本アプリの動作のために端末の中だけに保存します。

- 接続先の Gateway のアドレスと、接続に使うトークン・端末の鍵（Android Keystore の鍵で暗号化して保存します）
- 設定（外観・テーマ・フォント・エージェントの表示など）
- 入力中のメッセージの下書き、編集中のファイルの下書き、会話の読んでいた位置
- 表示のための一時的なキャッシュ（画像など）
- 利用者がダウンロードを選んだファイル（端末の「ダウンロード」フォルダに保存します）

接続のトークンと端末の鍵は、端末のバックアップや機種変更では引き継がれません。それ以外の設定と下書きは、Android のバックアップに含まれることがあります。本アプリを削除すると、ダウンロードしたファイルを除き、これらの情報も削除されます。

### 通信する相手

本アプリは、利用者の操作に応じて、次の相手とだけ通信します。

- 利用者が指定した OpenClaw Gateway：会話・添付ファイル・ファイルの読み書き・ターミナルの入出力・自動化の設定のやり取り。これらの情報の扱いは、Gateway を運用する利用者自身の設定に従います。
- 会話の中のリンク：利用者が押したときだけ、端末のブラウザ（アプリ内のブラウザを含む）で開きます。

### 音声入力

音声入力のボタンを押したときだけマイクを使います。音声は、端末に入っている Android の音声認識サービス（端末によって Google などが提供）に渡され、本アプリは文字になった結果だけを受け取ります。音声の扱いは、その音声認識サービスの提供者の方針に従います。本アプリは音声を保存しません。

### 権限

- マイク：音声入力のため（ボタンを押したときにだけ許可を求めます）
- 通知：ファイルのダウンロードの進み具合と完了を知らせるため
- ネットワーク：Gateway との通信のため

### 子どもの利用

本アプリは子ども向けに作られたものではなく、子どもの情報を意図して集めることもありません。

### このポリシーの変更

内容を変える場合は、このページを更新し、最終更新日を改めます。

### 問い合わせ

このページを公開している GitHub のレポジトリの Issues からお知らせください。

---

## English

SumahoClaw ("the app") is an Android app for connecting to an OpenClaw Gateway that you run yourself, so you can work with your AI agents' conversations, files, terminal, and automations. The app is not an official OpenClaw app. The app's author (miyavi) does not collect your personal information.

### Information we collect

The app does not send any information to the author's servers, and no account with the author is required. It contains no advertising, analytics, or tracking.

### Information stored only on your device

The following is stored only on your device so the app can work:

- The address of your Gateway, and the token and device key used to connect (encrypted with an Android Keystore key)
- Settings (appearance, theme, font, agent display, and so on)
- Drafts of messages you are typing, drafts of files you are editing, and your reading position in conversations
- Temporary caches used for display (such as images)
- Files you choose to download (saved to your device's Downloads folder)

The token and device key are never carried over by device backups or device transfers. Other settings and drafts may be included in Android backups. Uninstalling the app deletes this information, except for files you downloaded.

### Who the app communicates with

The app communicates only with the following, in response to your actions:

- The OpenClaw Gateway you specify: conversations, attachments, reading and writing files, terminal input and output, and automation settings. How this information is handled is governed by the Gateway you operate.
- Links in conversations: opened in a browser on your device (including the in-app browser) only when you tap them.

### Voice input

The microphone is used only when you tap the voice input button. Audio is passed to the Android speech recognition service installed on your device (provided by Google or another vendor, depending on the device), and the app receives only the resulting text. Audio is handled according to that service provider's policy. The app does not store audio.

### Permissions

- Microphone: for voice input (requested only when you tap the button)
- Notifications: to show the progress and completion of file downloads
- Network: to communicate with your Gateway

### Children

The app is not directed at children and does not knowingly collect information from children.

### Changes to this policy

If this policy changes, this page will be updated along with the "Last updated" date.

### Contact

Please open an issue in the GitHub repository that hosts this page.
