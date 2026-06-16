#!/bin/bash
# 디스패처: 프로젝트의 .claude/skills/verify-* 를 순서대로 실행
ROOT="${CLAUDE_PROJECT_DIR:-.}"
for skill in "$ROOT"/.claude/skills/verify-*/check.sh; do
  [ -x "$skill" ] && bash "$skill"
done
exit 0
