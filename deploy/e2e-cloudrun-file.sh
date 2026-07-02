#!/usr/bin/env bash
# 실제 파일을 백엔드 경로(브라우저와 동일: create→R2 PUT→complete→async parse 폴링→summarize→qa)로 e2e.
# diag-ai-parse-pdf.sh 가 Space를 "직접" 때리는 것과 달리, 이건 Cloud Run 백엔드의 @Async parse 경로를 탄다.
# 사용(Cloud Shell): bash deploy/e2e-cloudrun-file.sh ~/LoRA_3p.pdf pdf
set -euo pipefail

FILE="${1:?사용: bash deploy/e2e-cloudrun-file.sh <파일경로> [format]}"
FORMAT="${2:-pdf}"
ROOT="${ROOT:-https://readmind-backend-53543020852.asia-northeast3.run.app}"
BASE="$ROOT/api/v1"
[ -f "$FILE" ] || { echo "파일 없음: $FILE"; exit 1; }
EMAIL="e2efile-$(date +%s)@readmind.dev"; PASS="test1234"
FMT_UP=$(echo "$FORMAT" | tr '[:lower:]' '[:upper:]')
echo "== $FILE ($FMT_UP) via 백엔드 → $BASE  user=$EMAIL =="

curl -s -X POST "$BASE/auth/signup" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASS\"}" >/dev/null || true
ACCESS=$(curl -s -X POST "$BASE/auth/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASS\"}" | jq -r .data.accessToken)
AUTH="Authorization: Bearer $ACCESS"

SIZE=$(wc -c < "$FILE")
CREATE=$(curl -s -X POST "$BASE/documents" -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"title\":\"e2efile\",\"format\":\"$FMT_UP\",\"fileSize\":$SIZE}")
DOCID=$(echo "$CREATE" | jq -r .data.documentId)
UPLOAD=$(echo "$CREATE" | jq -r .data.uploadUrl)
[ "$DOCID" != "null" ] || { echo "!! create 실패: $CREATE"; exit 1; }
curl -s -o /dev/null -w "R2 PUT -> %{http_code}\n" --upload-file "$FILE" "$UPLOAD"

echo "documentId=$DOCID"
curl -s -X POST "$BASE/documents/$DOCID/complete" -H "$AUTH" | jq -c '.data'

echo "-- parse 폴링(백엔드 @Async) --"
for i in $(seq 1 40); do
  ST=$(curl -s "$BASE/documents/$DOCID" -H "$AUTH" | jq -r '.data.parseStatus')
  echo "  [$i] $ST"
  [ "$ST" = "READY" ] && break
  [ "$ST" = "FAILED" ] && { echo "!! 백엔드 parse FAILED (Space 직접호출은 되는데 백엔드 경로만 실패면 async/타임아웃/DB 확인)"; exit 1; }
  sleep 4
done
[ "${ST:-}" = "READY" ] || { echo "!! READY 안 됨(계속 PARSING) — @Async CPU throttling 의심"; exit 1; }

echo "-- summarize(PAPER) --"
curl -s -X POST "$BASE/documents/$DOCID/summarize" -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"style":"PAPER"}' | jq '.data | {summaryId, cached, tldr: .content.tldr}'
echo "-- qa --"
curl -s -X POST "$BASE/documents/$DOCID/qa" -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"question":"LoRA는 전체 파인튜닝 대비 학습 파라미터와 GPU 메모리를 얼마나 줄이나요?"}' \
  | jq '.data | {answer, sources}'
echo "== 백엔드 경로 e2e 완료 =="
