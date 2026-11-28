---
name: "spec-sync"
description: "コードを正として、プロジェクト全体の仕様（specs/ のすべての spec・plan ほか）とシステムルール（AGENTS.md・docs/design/screens.md・憲章）を加筆修正する。「仕様をコードに合わせて」「spec を同期して」「ドキュメントを追従させて」で使う。"
user-invocable: true
disable-model-invocation: false
---

## 入力

```text
$ARGUMENTS
```

空なら「前回の同期から今まで」。`全体` なら全仕様を今のコードと突き合わせる。コミットや日付が書かれていればそこを起点にする。

## 目的と向き

`/speckit-converge` の逆向き。converge は仕様を正としてコードの不足をタスクにする。このスキルは **コード（今のチェックアウトの HEAD）を正として、文書のほうを直す**。

- 直すのは文書だけ。アプリのコード・テスト・訳（`app/src/l10n/`）は触らない
- 対象は 1 つの機能ではなく、プロジェクト全体の文書（下の「対象の文書」）
- 他のワークツリーの、まだマージしていない変更は対象外

## 守ること

- **規則を破るコードは書き写さない**。コードが `AGENTS.md`・`docs/design/screens.md`・憲章の MUST に反していたら（色の直書き、禁止の部品など）、文書を合わせずに「コードの問題」として報告する。どちらが正しいか迷ったら質問する
- **数値の正は `core/designsystem/` のコード**（`AGENTS.md` の決まり）。文書の数値が食い違っていたら文書を直し、報告に書く。版の正は `gradle/libs.versions.toml`
- **憲章（`.specify/memory/constitution.md`）は承認制**。原則に関わる変更が要りそうなら、案と理由をまとめて先に聞く。承認されたら、冒頭の Sync Impact Report・版（意味的バージョニング）・Last Amended を合わせて直す
- **書きすぎない**。spec には利用者から見える振る舞いと決まりを書く。クラス名・ファイル名・内部の定数は plan・research・data-model・contracts に書く。利用者に見えない実装の都合で spec を細かくしない
- **履歴を消さない**。要件の番号は付け直さない。checklists と、tasks.md の過去のタスクは書き換えない
- 公開しない情報（`store-release.md` とその案内先、トークン、端末や家の中の宛先）を文書に写さない。アクセス禁止のファイルは開かない

## 対象の文書

| 文書 | 書くこと |
|---|---|
| `AGENTS.md` | 全体の決まり（技術スタック・書き方・色・文字・部品の使い分け・動き・禁止事項・リリース） |
| `docs/design/screens.md` | 画面ごとの決まりと例外 |
| `.specify/memory/constitution.md` | 原則（承認制） |
| `specs/NNN-*/spec.md` | User Story・受け入れシナリオ・FR・Edge Cases・Assumptions（範囲外）・Clarifications |
| `specs/NNN-*/plan.md`・`research.md`・`data-model.md`・`contracts/`・`quickstart.md` | 技術の決定・データの形・Gateway との約束・確かめ方。コードと食い違う所だけ直す |
| `README.md`・`README.ja.md` | 機能の一覧や画面の説明があれば、利用者に見える変化だけ |

## 手順

### 1. 起点を決める

```sh
git log -1 --format='%h %ad %s' --date=short --grep='仕様と AGENTS.md をコードに合わせる'
```

見つかったコミットを起点（BASE）にする。無ければ、または `全体` のときは、各 spec の Clarifications の最後の Session の日付と「実装の状況」の日付を見て、全体を突き合わせる。

### 2. 変化を集める

```sh
git log --reverse --date=short --format='%h %ad %s%n%b' BASE..HEAD -- . ':!app/src/l10n'
git diff --stat BASE..HEAD -- app/src/main app/build.gradle.kts gradle/libs.versions.toml scripts
git diff BASE..HEAD -- app/src/main/res/values/strings.xml
```

- コミットの本文の「マスター指定 YYYY-MM-DD」「CJ レビュー」は、決めた人と日付の手がかりになる
- 文言の差分（`values/strings.xml`）は、利用者に見える変化の一覧として使う
- 変化ごとに、必要な所だけコードの差分を読んで、振る舞いを確かめる。ファイルを丸ごと読み直さない
- すでに文書を一緒に直したコミット（同じコミットで specs/・docs/・AGENTS.md も変えたもの）は、その中身がコードと合っているかだけ確かめる

### 3. 文書に割り当てる

各 spec の題と FR を見出しだけ拾い、どの文書の持ち物か決める。

```sh
for f in specs/*/spec.md; do echo "== $f"; head -1 "$f"; grep -o '^- \*\*FR-[0-9]*[a-z]*\*\*: [^。]*' "$f" | cut -c1-90; done
grep -n '^## \|^### ' AGENTS.md docs/design/screens.md
```

- 既にある FR・節に当てはまるなら、そこを直す
- どの spec にも当てはまらない小さな機能は、いちばん近い spec に足す（会話・設定・全体の見た目なら 001）
- 新しい spec を起こすほど大きい機能は、ここで書かずに「`/speckit-specify` で起こす候補」として報告する
- 見た目の決まりは、全体なら `AGENTS.md`、1 つの画面なら `docs/design/screens.md`

### 4. 書く（このプロジェクトの書き方に合わせる）

| 変化 | 書き方 |
|---|---|
| 新しい振る舞い | 近い FR の下に枝番で足す（`FR-026a`、`FR-026b`…）。末尾に（マスター指定 YYYY-MM-DD）か（YYYY-MM-DD） |
| 振る舞いが変わった | その FR をその場で書き換え、末尾に（YYYY-MM-DD 改訂。以前は〜）。受け入れシナリオも合わせる |
| 機能を外した | `- ~~**FR-043**~~: YYYY-MM-DD 削除。理由` |
| まとまった決定 | spec の Clarifications に `### Session YYYY-MM-DD（実装をもとに反映。コードを正とする）` を足し、1 決定 1 行 |
| 入れないと決めたもの | Assumptions に「範囲外」として足す（憲章 VII） |
| 「実装の状況」にある未実装が実装された | その行を消すか「済み」にする |

- 文はこのリポジトリの言葉づかいに合わせる（漢語を避けた平らな日本語、短く断定）
- 見出しの Status の行に改訂の日付があれば更新する
- 1 つの事実は 1 か所に書き、ほかからは参照する（同じ決まりを `AGENTS.md` と spec の両方に長々と書かない）

### 5. 確かめる

```sh
# 要件の番号の重複（spec ごと）
for f in specs/*/spec.md; do grep -o '\*\*FR-[0-9]*[a-z]*\*\*' "$f" | sort | uniq -d | sed "s|^|$f: |"; done
# 文書に書いたパスが実在するか（型・置き換え・行番号付きは外す。docs/gateway/ は同梱の OpenClaw の資料）
grep -ohE '`(app|core|feature|docs|specs|scripts|gradle)/[^` ]+`' AGENTS.md docs/design/screens.md specs/*/*.md | tr -d '`' \
  | grep -v '[*<>]\|\.\.\.' | sed 's/:[0-9]*$//' | sort -u | while read p; do
    [ -e "$p" ] || [ -e "reference/openclaw/$p" ] || ls -d app/src/main/kotlin/*/*/*/*/"$p" >/dev/null 2>&1 || echo "見つからない: $p"
  done
git diff --stat
```

- 古い呼び方（改名前の名前・消した機能）が文書に残っていないか、`grep -rn` で確かめる
- 差分を読み直し、コードに無いことを書いていないか、書きすぎていないかを見る

### 6. コミットする（push はしない）

1 つのコミットにまとめる。

```text
docs: 仕様と AGENTS.md をコードに合わせる（YYYY-MM-DD までの変更）

- 001: 〜（FR-0xxa）、〜
- 007: 〜
- AGENTS.md: 〜
- screens.md: 〜
```

### 7. 報告する

- 文書ごとに、足した・直した・外したもの（要件の番号付き）
- コードの問題（規則に反していて書き写さなかったもの）
- 新しい spec を起こす候補、憲章の改定の案（承認待ち）
- 判断がつかず残した点
