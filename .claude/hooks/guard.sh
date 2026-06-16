#!/bin/bash
INPUT=$(cat)
# 시크릿 하드코딩 차단
if echo "$INPUT" | grep -qE "sk-[A-Za-z0-9]{20,}|OPENAI_API_KEY *= *['\"]sk-|jwt.*secret *= *['\"][^'\"]{8,}"; then
    echo "BLOCKED: 시크릿 하드코딩 감지. .env로 옮기고 .env.example만 커밋하세요." >&2
    exit 2
fi
# .env 파일 커밋 차단
if echo "$INPUT" | grep -qE "git add.*\.env($|[^.])|git commit.*\.env($|[^.])"; then
    echo "BLOCKED: .env 커밋 시도. .gitignore 확인." >&2
    exit 2
fi
exit 0
