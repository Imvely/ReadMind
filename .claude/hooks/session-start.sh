#!/bin/bash
# SessionStart hook — 세션 시작 시 상황 컨텍스트를 자동 주입한다 (CLAUDE.md §12.1의 기계적 강제).
# stdout이 그대로 컨텍스트에 들어가므로 간결하게 유지한다 (40줄 이내 목표).
ROOT="${CLAUDE_PROJECT_DIR:-.}"
cd "$ROOT" || exit 0
export PYTHONIOENCODING=utf-8  # Windows cp949 콘솔에서 한글 출력 깨짐 방지

echo "## 세션 시작 컨텍스트 (자동 주입 — CLAUDE.md §12.1)"

echo "### 최근 커밋"
git log --oneline -5 2>/dev/null

DIRTY=$(git status --short 2>/dev/null | head -10)
if [ -n "$DIRTY" ]; then
  echo "### ⚠️ 커밋 안 된 변경 있음"
  echo "$DIRTY"
fi

echo "### 진행 로그 최신 항목 (claude-progress.md)"
# 마지막 [날짜] 블록의 첫 6줄만
awk '/^\[20/{n=NR} {l[NR]=$0} END{for(i=n;i<=NR&&i<n+6;i++) print l[i]}' claude-progress.md 2>/dev/null

echo "### feature_list.json — 다음 작업 후보 (passes:false, phase 순)"
python - <<'EOF' 2>/dev/null
import json
d = json.load(open('feature_list.json', encoding='utf-8'))
ap = d.get('active_phase')
pend = [f for f in d['features'] if not f['passes']]
print(f"active_phase={ap}, 미완료 {len(pend)}개. 위에서부터:")
for f in pend[:4]:
    print(f"- [P{f['phase']}/{f['module']}] {f['id']}: {f['desc'][:60]}")
EOF

# 검증 도구 가용성 — 없으면 PostToolUse verify가 해당 검사를 건너뛴다. 반드시 인지시킨다.
MISSING=""
[ -d node_modules ] || MISSING="$MISSING node_modules(→npm install)"
if [ ! -x ai-service/.venv/Scripts/ruff.exe ] && ! command -v ruff >/dev/null 2>&1; then
  MISSING="$MISSING ruff(→pip install ruff)"
fi
[ -d ai-service/.venv ] || MISSING="$MISSING ai-service/.venv(pytest 불가)"
if [ -n "$MISSING" ]; then
  echo "### ⚠️ 검증 게이트 일부 비활성 — 누락:$MISSING"
  echo "이 상태에서 편집 시 해당 자동 검증이 건너뛰어진다. 코드 작업 전 설치를 권한다."
fi
exit 0
