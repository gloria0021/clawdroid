# Data Model: アプリのショートカット

## ShortcutSlot（設定に保存する。`SettingsStore.shortcuts` の JSON）

| フィールド | 型 | 備考 |
|---|---|---|
| `id` | String | ショートカットの ID。作るときに UUID。並べ替え・名前の変更では変えない |
| `agentId` | String | |
| `destination` | `ShortcutDestination` | 下の 3 種類 |
| `customLabel` | String? | マスターが付けた名前。無ければ行き先から作る（R6） |
| `archived` | Boolean | 行き先がアーカイブ済みと判定された。ランチャーには出さない |

並びは `ShortcutSettings.slots` のリストの順。上限 4（`MAX_SHORTCUTS`）。

## ShortcutDestination（sealed。JSON は `type` で分ける）

| 種類 | フィールド | 開き方 |
|---|---|---|
| `Home` | `sessionKey`（作るときのホームの会話のキー） | `OpenTarget.Session(agentId, sessionKey)` |
| `Folder` | `cwd` | `OpenTarget.NewChat(agentId, cwd)` |
| `Session` | `sessionKey` | `OpenTarget.Session(agentId, sessionKey)` |

## ShortcutSettings（保存の単位）

| フィールド | 型 | 備考 |
|---|---|---|
| `gatewayUrl` | String? | 枠を作ったときの接続先。違う接続先につながったら枠を全部消す（R7） |
| `slots` | List<ShortcutSlot> | |

## OpenTarget（既存に 1 つ足す）

```kotlin
sealed interface OpenTarget {
    data class Session(val agentId: String, val sessionKey: String) : OpenTarget   // 既存
    data class NewChat(val agentId: String, val cwd: String?) : OpenTarget        // 新規
    data class Automation(val jobId: String) : OpenTarget                         // 既存
    data object Approvals : OpenTarget                                            // 既存
}
```

## ReconcileResult（`reconcile(slots, lists, agents)` の結果。メモリ）

| フィールド | 型 | 備考 |
|---|---|---|
| `slots` | List<ShortcutSlot> | 保存し直す枠（削除・エージェントの消滅で外したもの、`archived` を付け外ししたものを反映） |
| `published` | List<PublishedShortcut> | ランチャーに出すもの（`archived` でない枠） |
| `disable` | Map<String, DisableReason> | ホーム画面の分を無効にする ID と理由（アーカイブ・削除・エージェントの消滅） |
| `enable` | Set<String> | アーカイブが戻されて有効に戻す ID |

**判定の移り変わり**（一覧を判定できるエージェントだけ。R4）

| 行き先の在りか | 前の `archived` | 結果 |
|---|---|---|
| `sessions`（アクティブ） | false | そのまま |
| `sessions`（アクティブ） | true | `archived = false`、`enable` に入れる |
| `archived` | false | `archived = true`、`disable`（アーカイブ） |
| `archived` | true | そのまま |
| どこにも無い | どちらでも | 枠を外す、`disable`（削除） |
| エージェントが消えた | どちらでも | 枠を外す、`disable`（エージェント） |

`Home` は判定しない（アーカイブできない）。`Folder` はセッションを持たないので、エージェントの消滅だけを判定する。

## PublishedShortcut（ランチャーに出す中身。前回と比べて無駄な登録を避ける）

| フィールド | 型 | 備考 |
|---|---|---|
| `id` | String | |
| `rank` | Int | 並びの順 |
| `label` | String | 短い名前と長い名前の両方に使う |
| `target` | OpenTarget | Intent の extras にする |
| `avatarUrl` | String? | アイコンの元。変わったときだけ読み直す |
