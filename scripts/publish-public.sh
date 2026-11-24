#!/usr/bin/env bash
# 公開用レポジトリへ、HEAD のうち公開する範囲（README と docs）だけを1コミットにして送る。
# ソースは出さない。作業中のフォルダとブランチには触らない。公開の流れはローカルの refs/heads/public に積む。
# アプリと Play ストアの掲載は、ここで公開した docs/privacy-policy.md をプライバシーポリシーとして指す。
#
#   PUBLIC_NAME=... PUBLIC_EMAIL=...@users.noreply.github.com scripts/publish-public.sh [--dry-run]
#
# 送り先は git remote の public（例: git remote add public https://github.com/<owner>/<公開用レポジトリ>.git）。
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

# 公開する範囲（ここに無いものは出ない）
PATHS=(README.md docs)
REMOTE=${PUBLIC_REMOTE:-public}
REF=refs/heads/public
: "${PUBLIC_NAME:?PUBLIC_NAME を指定してください}"
: "${PUBLIC_EMAIL:?PUBLIC_EMAIL（GitHub の noreply）を指定してください}"

[ -z "$(git status --porcelain)" ] || { echo "未コミットの変更があります。先にコミットしてください" >&2; exit 1; }
for p in "${PATHS[@]}"; do
  git cat-file -e "HEAD:$p" 2>/dev/null || { echo "$p が HEAD にありません" >&2; exit 1; }
done

version=$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' app/build.gradle.kts)

index=$(mktemp); trap 'rm -f "$index"' EXIT
export GIT_INDEX_FILE=$index
git read-tree --empty
git ls-tree -r HEAD -- "${PATHS[@]}" | git update-index --index-info
tree=$(git write-tree)

if [ "${1:-}" = --dry-run ]; then
  git ls-tree -r --name-only "$tree"
  exit 0
fi

git remote get-url "$REMOTE" >/dev/null 2>&1 || { echo "送り先 $REMOTE がありません" >&2; exit 1; }

prev=$(git rev-parse -q --verify "$REF" || true)
if [ -n "$prev" ] && [ "$(git rev-parse "$prev^{tree}")" = "$tree" ]; then
  # 前回と同じ内容。前のプッシュが失敗していても、同じコミットを送り直せばよい
  commit=$prev
else
  parent=()
  [ -z "$prev" ] || parent=(-p "$prev")
  commit=$(GIT_AUTHOR_NAME=$PUBLIC_NAME GIT_AUTHOR_EMAIL=$PUBLIC_EMAIL \
    GIT_COMMITTER_NAME=$PUBLIC_NAME GIT_COMMITTER_EMAIL=$PUBLIC_EMAIL \
    git commit-tree "$tree" "${parent[@]}" -m "Clawdroid $version")
  git update-ref "$REF" "$commit"
fi
git push "$REMOTE" "$REF:refs/heads/main"
echo "Clawdroid $version を $REMOTE に送りました ($commit)"
