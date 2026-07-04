#!/usr/bin/env bash
# 웹 프론트 정적 배포 — GitHub Pages(gh-pages 브랜치)로 web/dist 를 발행.
# URL: https://imvely.github.io/ReadMind/  (gh-pages 브랜치 push 시 Pages 자동 활성화)
#
# 사용(레포 루트): bash deploy/gh-pages-deploy.sh
#   - git push 권한은 로컬 credential manager 의 것을 사용한다(토큰 하드코딩 금지).
#
# 선행 조건(런북 deploy/cloudrun-backend.md):
#   - Cloud Run: CORS_ALLOWED_ORIGINS=https://imvely.github.io
#   - R2 CORS: https://imvely.github.io 오리진 허용(브라우저 presigned PUT용)
set -euo pipefail

API_BASE="${VITE_API_BASE_URL:-https://readmind-backend-53543020852.asia-northeast3.run.app/api/v1}"
# 프로젝트 Pages 는 /<repo>/ 하위 경로 서빙. 절대경로("/ReadMind/")는 Git Bash 의
# MSYS 경로 변환("/Program Files/Git/ReadMind/")에 오염되므로 상대 base 를 쓴다(HashRouter라 안전).
BASE_PATH="./"
REMOTE_URL=$(git config --get remote.origin.url)

[ -d web ] || { echo "!! 레포 루트에서 실행하세요."; exit 1; }

echo "== 1) 프로덕션 빌드 (API_BASE=$API_BASE, base=$BASE_PATH) =="
(cd web && VITE_API_BASE_URL="$API_BASE" npm run build -- --base="$BASE_PATH")

echo "== 2) gh-pages 브랜치에 dist 발행 =="
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
git clone --depth 1 --branch gh-pages "$REMOTE_URL" "$WORK/pages" 2>/dev/null \
  || { git init -q -b gh-pages "$WORK/pages"; git -C "$WORK/pages" remote add origin "$REMOTE_URL"; }
find "$WORK/pages" -mindepth 1 -maxdepth 1 ! -name .git -exec rm -rf {} +
cp -r web/dist/. "$WORK/pages/"
touch "$WORK/pages/.nojekyll"   # Jekyll 처리 비활성(정적 자산 그대로 서빙)

cd "$WORK/pages"
git add -A
if git diff --cached --quiet; then echo "변경 없음 — Pages 이미 최신."; exit 0; fi
git -c user.email="lim.dayeong@gmail.com" -c user.name="dayeong.lim" \
  commit -q -m "${DEPLOY_MSG:-deploy: web static build}"
git push -u origin gh-pages

echo "== 완료. https://imvely.github.io/ReadMind/ (첫 활성화는 1~2분) =="
