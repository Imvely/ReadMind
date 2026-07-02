#!/usr/bin/env bash
# HF Space(dayeongim/readmind-ai) 재배포 — ai-service/app + requirements.txt 만 최신으로 동기화.
# Space 전용 파일(HF Dockerfile 7860, README frontmatter)은 건드리지 않는다.
#
# 사용:
#   export HF_TOKEN=<write 권한 토큰>          # 또는 로컬 .env 의 HF_TOKEN 자동 사용
#   bash deploy/hf-space-deploy.sh
#
# 실행 위치: 레포 루트(ai-service/ 가 보이는 곳). 사내망에서 huggingface.co 가 막히면 Cloud Shell 에서.
set -euo pipefail

# 로컬 .env 에 HF_TOKEN 이 있으면 자동 로드(Cloud Shell 엔 .env 없음 → export 로 주입).
[ -f .env ] && export "$(grep -E '^HF_TOKEN=' .env | head -1 | xargs)" 2>/dev/null || true
: "${HF_TOKEN:?HF_TOKEN 이 필요합니다. export HF_TOKEN=... 후 재실행.}"

[ -d ai-service/app ] || { echo "!! ai-service/app 이 없습니다. 레포 루트에서 실행하세요."; exit 1; }

HF_USER=dayeongim
SPACE=dayeongim/readmind-ai
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT   # 토큰이 박힌 임시 클론을 항상 정리

echo "== Space 클론: $SPACE =="
git clone --depth 1 "https://${HF_USER}:${HF_TOKEN}@huggingface.co/spaces/${SPACE}" "$WORK/space" 2>&1 \
  | sed -E "s/${HF_TOKEN}/***/g"

echo "== app/ + requirements.txt 동기화(나머지 Space 파일 보존) =="
rm -rf "$WORK/space/app"
cp -r ai-service/app "$WORK/space/app"
cp ai-service/requirements.txt "$WORK/space/requirements.txt"

cd "$WORK/space"
if git diff --quiet && git diff --cached --quiet; then
  echo "변경 없음 — Space 이미 최신."
  exit 0
fi
git add -A
git -c user.email="lim.dayeong@gmail.com" -c user.name="dayeong.lim" \
  commit -m "deploy: sync ai-service (ai-parse-multi txt/docx/epub + translate + gemini)"
git push 2>&1 | sed -E "s/${HF_TOKEN}/***/g"

echo "== push 완료. HF Space 가 자동 재빌드합니다(수 분). 빌드 완료 후 e2e 재실행 =="
echo "   Space 로그/상태: https://huggingface.co/spaces/${SPACE}"
