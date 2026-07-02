#!/usr/bin/env bash
# ReadMind AI 실경로 e2e (Cloud Run 백엔드 → HF Space AI서비스 → Neon/R2).
# Cloud Shell 에서 실행: bash deploy/e2e-cloudrun.sh [BASE_URL]
# 사내망 로컬에선 *.run.app 이 MITM 으로 막히므로 Cloud Shell 에서 돌린다.
set -euo pipefail

ROOT="${1:-https://readmind-backend-53543020852.asia-northeast3.run.app}"
BASE="$ROOT/api/v1"
EMAIL="e2e-$(date +%s)@readmind.dev"
PASS="test1234"
echo "== target: $BASE  (user: $EMAIL) =="

# 1) 회원가입(있으면 무시) + 로그인 → access 토큰
curl -s -X POST "$BASE/auth/signup" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASS\"}" >/dev/null || true
ACCESS=$(curl -s -X POST "$BASE/auth/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASS\"}" | jq -r '.data.accessToken')
[ -n "$ACCESS" ] && [ "$ACCESS" != "null" ] || { echo "!! login 실패"; exit 1; }
AUTH="Authorization: Bearer $ACCESS"
echo "login OK"

# 2) 문서 생성(TXT) → documentId + presigned PUT URL
TXT=$'Transformers process sequences in parallel using self-attention.\nUnlike RNNs, they avoid sequential computation, which enables much faster training.\nThe attention mechanism weighs how relevant each token is to every other token.'
SIZE=$(printf '%s' "$TXT" | wc -c)
CREATE=$(curl -s -X POST "$BASE/documents" -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"title\":\"e2e-selfattention\",\"format\":\"TXT\",\"fileSize\":$SIZE}")
DOCID=$(echo "$CREATE" | jq -r '.data.documentId')
UPLOAD=$(echo "$CREATE" | jq -r '.data.uploadUrl')
[ "$DOCID" != "null" ] || { echo "!! create 실패: $CREATE"; exit 1; }
echo "documentId=$DOCID"

# 3) presigned R2 PUT (Content-Type 미바인딩 → 그대로 업로드)
printf '%s' "$TXT" | curl -s -o /dev/null -w "R2 PUT -> %{http_code}\n" --upload-file - "$UPLOAD"

# 4) complete → 백엔드가 Cloud Run에서 HF Space /ai/parse 를 @Async 로 트리거
curl -s -X POST "$BASE/documents/$DOCID/complete" -H "$AUTH" | jq -c '.data'

# 5) parseStatus 폴링 (PENDING/PARSING → READY|FAILED)
echo "-- parse 폴링 --"
for i in $(seq 1 40); do
  ST=$(curl -s "$BASE/documents/$DOCID" -H "$AUTH" | jq -r '.data.parseStatus')
  echo "  [$i] $ST"
  [ "$ST" = "READY" ] && break
  [ "$ST" = "FAILED" ] && { echo "!! parse FAILED — Cloud Run→HF Space 호출/토큰/네트워크 확인"; exit 1; }
  sleep 4
done
[ "${ST:-}" = "READY" ] || { echo "!! parse 미완(READY 아님). Cloud Run CPU throttling 의심 — 아래 주석 참고"; exit 1; }

# 6) 요약(PAPER)
echo "-- summarize(PAPER) --"
curl -s -X POST "$BASE/documents/$DOCID/summarize" -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"style":"PAPER"}' | jq '.data | {summaryId, cached, tldr: .content.tldr}'

# 7) Q&A (근거 sources 반드시 포함 — 없으면 환각방지 강등)
echo "-- qa --"
curl -s -X POST "$BASE/documents/$DOCID/qa" -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"question":"How do transformers process sequences?"}' | jq '.data | {answer, sources}'

echo "== e2e 완료 =="

# ─────────────────────────────────────────────────────────────
# ⚠️ parse 가 계속 PENDING/PARSING 이면 (Cloud Run @Async 함정):
#   Cloud Run 은 기본적으로 "요청 처리 중에만" CPU 를 준다. complete 가 응답을 돌려준 뒤
#   @Async 백그라운드 파싱 스레드는 CPU throttling 으로 멈출 수 있다.
#   해결: CPU 상시 할당으로 재배포 →
#     gcloud run services update readmind-backend --region=asia-northeast3 --no-cpu-throttling
#   (단독 사용자/저트래픽이면 비용 미미)
