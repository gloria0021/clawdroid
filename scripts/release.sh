#!/usr/bin/env bash
# リリースを1回で行う: 訳を作る → 版を上げる → 確認一式 → コミットとタグ → 署名した App Bundle →
# 非公開レポジトリへ Push → 公開用レポジトリへ README と docs（プライバシーポリシー）を送る。
# Google Play へのアップロードと公開は、できた AAB を使って Play Console で行う。
#
#   scripts/release.sh 1.0.0              # 外に出す前（Push の前）に一度確かめる
#   scripts/release.sh 1.0.0 --yes        # 確かめずに最後まで進める
#   scripts/release.sh 1.0.0 --no-public  # 公開用レポジトリは飛ばす
#
# 署名の鍵は ~/.gradle/gradle.properties の CLAWDROID_* から読む（レポジトリには置かない）:
#   CLAWDROID_STORE_FILE=/path/to/upload-key.jks
#   CLAWDROID_STORE_PASSWORD=...  CLAWDROID_KEY_ALIAS=...  CLAWDROID_KEY_PASSWORD=...
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

VERSION=${1:?版を指定してください（例: scripts/release.sh 1.0.0）}
shift
YES=false; PUBLIC=true
for arg in "$@"; do
  case $arg in
    --yes) YES=true ;;
    --no-public) PUBLIC=false ;;
    *) echo "知らない指定です: $arg" >&2; exit 1 ;;
  esac
done

OUT=${CLAWDROID_RELEASE_DIR:-$HOME/Dev/android/clawdroid-release}
export PUBLIC_NAME=${PUBLIC_NAME:-miyavi}
export PUBLIC_EMAIL=${PUBLIC_EMAIL:-9840472+gloria0021@users.noreply.github.com}
GRADLE_FILE=app/build.gradle.kts

step() { printf '\n== %s\n' "$1"; }
fail() { echo "止めました: $1" >&2; exit 1; }

# --- 始める前の確認（ここで止まれば何も変わらない）
[[ $VERSION =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || fail "版は 1.0.0 の形で指定してください"
[ "$(git branch --show-current)" = main ] || fail "main ブランチで実行してください"
[ -z "$(git status --porcelain)" ] || fail "未コミットの変更があります"
git rev-parse -q --verify "refs/tags/v$VERSION" >/dev/null && fail "タグ v$VERSION はもうあります"
grep -q '^CLAWDROID_STORE_FILE=' "$HOME/.gradle/gradle.properties" 2>/dev/null || fail "署名の設定（~/.gradle/gradle.properties の CLAWDROID_*）がありません"
command -v claude >/dev/null || fail "訳に使う claude（Claude Code）がありません"
# Release のビルドを重ねると出力を取り合うので、ほかに動いていたら始めない
pgrep -f '^java .*gradle-wrapper\.jar .*(assemble|bundle)Release' >/dev/null && fail "ほかの Release ビルドが動いています"
if $PUBLIC; then
  git remote get-url public >/dev/null 2>&1 || fail "公開用の送り先 public がありません（git remote add public ... / --no-public で飛ばせます）"
fi

step "訳を作る（変わった文言だけ）"
# 失敗しても、できた分は残る。もう一度実行すれば残りだけを訳す
scripts/translate-strings.py || fail "訳が作れませんでした（できた分は app/src/release に残っています。コミットしてから再実行してください）"
scripts/translate-strings.py --check >/dev/null || fail "訳が最新になっていません"
if [ -n "$(git status --porcelain -- app/src/release)" ]; then
  git add app/src/release
  git commit -q -m "Update translations for $VERSION"
  echo "訳をコミットしました"
fi

step "版を $VERSION にする"
CODE=$(sed -n 's/.*versionCode = \([0-9]*\).*/\1/p' "$GRADLE_FILE")
sed -i "s/versionCode = $CODE/versionCode = $((CODE + 1))/; s/versionName = \"[^\"]*\"/versionName = \"$VERSION\"/" "$GRADLE_FILE"
grep -E 'versionCode|versionName' "$GRADLE_FILE"

step "確認一式（Release の lint・テスト）"
# Release の lint は訳の漏れ（MissingTranslation）と複数形の不足（MissingQuantity）も見る
./gradlew :app:lintRelease :app:testDebugUnitTest -q \
  || { git checkout -- "$GRADLE_FILE"; fail "確認一式が通りませんでした（版は元に戻しました）"; }

step "コミットとタグ v$VERSION"
git commit -q -am "Release $VERSION"
git tag "v$VERSION"

step "署名した App Bundle を作る"
./gradlew :app:bundleRelease -q
mkdir -p "$OUT"
AAB=$OUT/clawdroid-$VERSION.aab
cp app/build/outputs/bundle/release/app-release.aab "$AAB"
# 難読化の対応表は Play Console に登録する（異常終了の解析用）
cp app/build/outputs/mapping/release/mapping.txt "$OUT/mapping-$VERSION.txt"
keytool -printcert -jarfile "$AAB" | grep -m1 'Owner:' || fail "AAB が署名されていません"
echo "AAB（Google Play 用）: $AAB"
echo "難読化の対応表: $OUT/mapping-$VERSION.txt"

if ! $YES; then
  printf '\nここから外に出します（非公開レポジトリへ Push%s）。続けますか？ [y/N] ' "$($PUBLIC && echo '・公開用レポジトリへ README と docs を送信')"
  read -r answer
  [ "$answer" = y ] || { echo "ここで止めました。コミットとタグは手元に残っています。"; exit 0; }
fi

step "非公開レポジトリへ Push"
git push origin main "v$VERSION"

if $PUBLIC; then
  step "公開用レポジトリへ README と docs を送る"
  scripts/publish-public.sh
fi

step "完了: Clawdroid $VERSION"
echo "次: Play Console で $AAB をアップロードし、mapping-$VERSION.txt を登録して、新機能の文を添える"
