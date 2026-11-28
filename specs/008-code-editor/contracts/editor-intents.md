# Contract: エディターの開き方（Intent）

## アプリの中から開く

`EditorActivity` を明示の Intent で開く。extras は文字列と整数だけ（Binder の上限と、プロセスの終了後に作り直される Intent に耐えるため）。

| extra | 型 | 必須 | 中身 |
|---|---|---|---|
| `editor.kind` | String | ○ | `session` / `agent` / `attachment` / `paste` |
| `editor.agentId` | String | session・agent・attachment | |
| `editor.sessionKey` | String | session・attachment | |
| `editor.path` | String | session・agent | ルートからの相対パス（agent はファイル名） |
| `editor.source` | String | attachment | 添付の source（URL） |
| `editor.messageId` | String | attachment | |
| `editor.name` | String | attachment | 表示と保存の名前 |
| `editor.mime` | String | — | attachment |
| `editor.chatKey` | String | paste | 会話の下書きの鍵 |
| `editor.textId` | String | paste | タイルの ID |
| `editor.handoff` | String | — | `EditorHandoff` の ID（読み込み済みの中身。無ければ読み直す） |
| `editor.line` | Int | — | 開いたときに見せる行（0 始まり。ビューアで読んでいた位置） |

- 呼び出し元: ファイルビューア（`FilesScreen`・`AgentFileScreen` の「編集」、新しいファイルを作った直後）、添付のシート（`FilePreviewSheet`）、コピーしたテキストのシート
- 組み立てと読み取りは `EditorIntents`（1 か所）で行い、単体テストする

## ほかのアプリから開く

```xml
<activity android:name=".feature.editor.EditorActivity"
          android:exported="true"
          android:windowSoftInputMode="adjustResize"
          android:label="@string/editor_launcher_label">
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <action android:name="android.intent.action.EDIT" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:scheme="content" />
        <data android:mimeType="text/*" />
        <data android:mimeType="application/json" />
        <data android:mimeType="application/xml" />
        <data android:mimeType="application/javascript" />
        <data android:mimeType="application/x-sh" />
        <data android:mimeType="application/x-yaml" />
        <data android:mimeType="application/toml" />
        <data android:mimeType="application/x-subrip" />
    </intent-filter>
</activity>
```

- 「アプリで開く」の候補に出る名前は `editor_launcher_label`（例: 「SumahoClaw エディター」）
- `exported="true"` で受けるのは上の 2 つの action だけ。アプリの中の extras（`editor.kind` など）は、外から来た Intent では読まない（`getCallingActivity`・`ACTION` で区別し、`data` が `content://` の `ACTION_VIEW` / `ACTION_EDIT` だけを `LocalDocument` にする）。ほかのアプリが Gateway のファイルを開かせることはできない
- 書けるかは research R8 の順で決める

## 結果

- 保存は `EditorResults` に流す（data-model）。`setResult` は使わない
- ほかのアプリから開いた場合は、元の URI に書くだけ。呼び出し元には何も返さない
