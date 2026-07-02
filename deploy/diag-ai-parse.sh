#!/usr/bin/env bash
# HF Space /ai/parse 를 직접 호출해 502의 실제 detail(S3DownloadError vs ProviderError)을 본다.
# 백엔드로 문서 생성+R2 업로드까지 한 뒤, 같은 storageKey 로 Space 를 직접 때린다.
#
# 사용(Cloud Shell):
#   export AI_SERVICE_TOKEN=<Space의 AI_SERVICE_TOKEN 과 동일 값>
#   bash deploy/diag-ai-parse.sh
set -euo pipefail

ROOT="${1:-https://readmind-backend-53543020852.asia-northeast3.run.app}"
SPACE="${2:-https://dayeongim-readmind-ai.hf.space}"
: "${AI_SERVICE_TOKEN:?export AI_SERVICE_TOKEN=<Space와 동일 값> 후 재실행}"
BASE="$ROOT/api/v1"
EMAIL="diag-$(date +%s)@readmind.dev"; PASS="test1234"

ACCESS=$(curl -s -X POST "$BASE/auth/signup" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASS\"}" >/dev/null; \
  curl -s -X POST "$BASE/auth/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASS\"}" | jq -r .data.accessToken)
AUTH="Authorization: Bearer $ACCESS"

TMP=$(mktemp); printf '%s' 'Transformers use self-attention to process sequences in parallel.' > "$TMP"
CREATE=$(curl -s -X POST "$BASE/documents" -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"title\":\"diag\",\"format\":\"TXT\",\"fileSize\":$(wc -c <"$TMP")}")
DOCID=$(echo "$CREATE" | jq -r .data.documentId)
UPLOAD=$(echo "$CREATE" | jq -r .data.uploadUrl)
curl -s -o /dev/null -w "R2 PUT(backend presigned) -> %{http_code}\n" --upload-file "$TMP" "$UPLOAD"; rm -f "$TMP"

# uploadUrl( https://<host>/<bucket>/<key>?... )에서 storageKey 추출
KEY=$(echo "$UPLOAD" | sed -E 's#\?.*##; s#^https?://[^/]+/[^/]+/##')
echo "documentId=$DOCID  storageKey=$KEY"
echo "== HF Space /ai/parse 직접 호출 (502면 detail 에 원인) =="
curl -s -i -X POST "$SPACE/ai/parse" \
  -H "Content-Type: application/json" -H "X-Service-Token: $AI_SERVICE_TOKEN" \
  -d "{\"documentId\":$DOCID,\"storageKey\":\"$KEY\",\"format\":\"txt\"}"
echo
echo "-- 해석 --"
echo "  detail 에 S3/download/NoSuchKey/credentials → Space 의 S3_* (R2 자격증명/버킷/엔드포인트) 문제"
echo "  detail 에 provider/embedding/gemini/api key   → Space 의 LLM_*/EMBEDDING_* (Gemini 키) 문제"
