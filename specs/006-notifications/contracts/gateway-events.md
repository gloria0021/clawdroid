# Contract: 使う Gateway の出来事と RPC

| 用途 | 出来事 / RPC | 読むもの |
|---|---|---|
| 全会話の状態 | `sessions.subscribe`（既存）→ `sessions.changed` | `session.key`・`agentId`・`hasActiveRun`・`status`・`label` 等（`Parse.session`） |
| 応答の本文 | `chat.history { sessionKey, agentId, limit: 4 }` | 最後の assistant のメッセージ（`Parse.message`） |
| つなぎ直しの補い | `sessions.list`（既存の読み方） | 切れる前に実行中だった会話の状態 |
| 承認待ち | `exec.approval.requested/resolved`・`plugin.approval.requested/resolved`（既存の `ApprovalsRepository`） | `PendingApproval` |
| 承認の決定 | `exec.approval.resolve` / `plugin.approval.resolve`（既存の `ApprovalsRepository.resolve`） | |
| 自動化の終わり | `cron`（`action`・`jobId`・`status`・`error`・`job { id, name, agentId, state { lastRunStatus, lastStatus } }`） | 失敗のときだけ |
| 裏で止める | `sessions.messages.unsubscribe`（開いている会話） | |
| connect | `caps` に `chat-only-assistant-text` を足す | |
