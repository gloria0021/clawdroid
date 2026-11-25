# Contract: ショートカットの Intent とランチャーへの登録

## Intent（`core/notify/OpenIntents.kt`。通知と共通）

宛先は `MainActivity`。ショートカットは `action = ACTION_VIEW`（ショートカットの Intent は action が必須）。extras は文字列だけ。

| extra | 値 | 使うもの |
|---|---|---|
| `jp.miyavi.android.clawdroid.OPEN` | `session` / `new_chat` / `automation` / `approvals` | 行き先の種類（`new_chat` を足す） |
| `jp.miyavi.android.clawdroid.AGENT` | エージェント ID | `session` / `new_chat` |
| `jp.miyavi.android.clawdroid.SESSION` | セッションのキー | `session` |
| `jp.miyavi.android.clawdroid.CWD` | 作業フォルダ（無ければ入れない） | `new_chat` |
| `jp.miyavi.android.clawdroid.JOB` | 自動化の ID | `automation` |
| `jp.miyavi.android.clawdroid.SHORTCUT_ID` | ショートカットの ID | ショートカットだけ。`reportShortcutUsed` に使う |

読めない組み合わせ（`session` なのに `SESSION` が無いなど）は `null`（アプリを開くだけ）。

## ランチャーへの登録（`ShortcutPublisher`）

| 場面 | 呼ぶもの | 裏での回数制限 |
|---|---|---|
| 設定で枠を変えた | `setDynamicShortcuts(published)` | あり（前にいるので問題なし） |
| 前に戻った・接続した | 前回と違えば `setDynamicShortcuts(published)` | — |
| 名前の追いかけ | `updateShortcuts(changed)`。制限中なら前に戻ったときに回す | あり |
| アーカイブ・削除・エージェントの消滅 | `removeDynamicShortcuts(ids)` ＋ `disableShortcuts(ids, 理由)` | なし |
| アーカイブが戻された | `enableShortcuts(ids)`、並べ直しは前で | `enable` はなし |
| 接続情報を消した・別の接続先 | `removeAllDynamicShortcuts()` ＋ 全 ID を `disableShortcuts` | なし |

`ShortcutInfoCompat` の中身: `id`・`shortLabel = longLabel = label`・`icon`（R5）・`intent`・`rank`・`setLongLived(true)`。

## 無効にした理由の文言（`strings.xml`）

| 理由 | 文言（案） |
|---|---|
| アーカイブ | この会話はアーカイブされました |
| 削除 | この会話は削除されました |
| エージェント | このエージェントは見つかりません |
| 接続先 | 接続先が変わりました |
