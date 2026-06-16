#!/bin/bash
# M3 web: TypeScript 타입체크
ROOT="${CLAUDE_PROJECT_DIR:-.}"
[ -f "$ROOT/web/package.json" ] || exit 0
cd "$ROOT/web" || exit 0
echo "── [verify-web] tsc --noEmit ──"
npx tsc --noEmit 2>&1 | tail -15
exit 0
