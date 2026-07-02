#!/usr/bin/env bash
# 실제 파일(PDF 등)로 HF Space /ai/parse 를 직접 호출해 동기적으로 에러 detail 을 본다.
# async/로그 타이밍을 전부 우회. txt는 되는데 PDF만 실패하는 원인(429 rate limit vs Parser vs credits)을 확정.
#
# 사용(Cloud Shell):
#   1) Cloud Shell 우상단 ⋮ → "Upload" 로 LoRA_3p.pdf 를 홈(~)에 올린다
#   2) export AI_SERVICE_TOKEN=<Space의 AI_SERVICE_TOKEN 과 동일 값>
#   3) bash deploy/diag-ai-parse-pdf.sh ~/LoRA_3p.pdf pdf
set -euo pipefail

FILE="${1:?사용: bash deploy/diag-ai-parse-pdf.sh <파일경로> [format]}"
FORMAT="${2:-pdf}"
ROOT="${ROOT:-https://readmind-backend-53543020852.asia-northeast3.run.app}"
SPACE="${SPACE:-https://dayeongim-readmind-ai.hf.space}"
: "${AI_SERVICE_TOKEN:?export AI_SERVICE_TOKEN=<Space와 동일 값> 후 재실행}"
[ -f "$FILE" ] || { echo "파일 없음: $FILE"; exit 1; }
BASE="$ROOT/api/v1"; EMAIL="diagpdf-$(date +%s)@readmind.dev"; PASS="test1234"

curl -s -X POST "$BASE/auth/signup" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASS\"}" >/dev/null || true
ACCESS=$(curl -s -X POST "$BASE/auth/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASS\"}" | jq -r .data.accessToken)
AUTH="Authorization: Bearer $ACCESS"

SIZE=$(wc -c < "$FILE")
FMT_UP=$(echo "$FORMAT" | tr '[:lower:]' '[:upper:]')
FMT_LO=$(echo "$FORMAT" | tr '[:upper:]' '[:lower:]')
CREATE=$(curl -s -X POST "$BASE/documents" -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"title\":\"diagpdf\",\"format\":\"$FMT_UP\",\"fileSize\":$SIZE}")
DOCID=$(echo "$CREATE" | jq -r .data.documentId)
UPLOAD=$(echo "$CREATE" | jq -r .data.uploadUrl)
[ "$DOCID" != "null" ] || { echo "!! create 실패: $CREATE"; exit 1; }
curl -s -o /dev/null -w "R2 PUT -> %{http_code}\n" --upload-file "$FILE" "$UPLOAD"
KEY=$(echo "$UPLOAD" | sed -E 's#\?.*##; s#^https?://[^/]+/[^/]+/##')
echo "documentId=$DOCID  format=$FMT_LO  size=$SIZE  storageKey=$KEY"

echo "== Space /ai/parse 직접 호출(동기, 시간+상태+detail) =="
time curl -s -i --max-time 180 -X POST "$SPACE/ai/parse" \
  -H "Content-Type: application/json" -H "X-Service-Token: $AI_SERVICE_TOKEN" \
  -d "{\"documentId\":$DOCID,\"storageKey\":\"$KEY\",\"format\":\"$FMT_LO\"}"
echo
echo "-- 해석 --"
echo "  200 + chunkCount=N          → 파싱 자체는 OK (백엔드 async/타임아웃 문제였음)"
echo "  502 Gemini … 429 rate limit → 무료 임베딩 레이트리밋(청크 多) → ai-service 백오프/배치 조정"
echo "  502 Gemini … prepayment     → 키 크레딧 소진(무료 아님)"
echo "  422 Parser / no text        → PDF 텍스트 추출 실패(스캔본 등)"
