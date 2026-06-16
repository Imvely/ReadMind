#!/bin/bash
# M1 ai-service: ruff lint + (있으면) pytest 빠른 검사
ROOT="${CLAUDE_PROJECT_DIR:-.}"
[ -d "$ROOT/ai-service" ] || exit 0
cd "$ROOT/ai-service" || exit 0
echo "── [verify-python] ruff ──"
ruff check . 2>&1 | tail -15
exit 0
