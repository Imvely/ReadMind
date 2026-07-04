#!/usr/bin/env python
"""PostToolUse verify — 편집된 파일이 속한 모듈만 골라 검증한다 (CLAUDE.md §12.5).

동작:
- ai-service/**/*.py  → ruff check <file> (venv ruff 우선, 없으면 전역 ruff)
- web|packages/**/*.ts(x) → npm run typecheck (워크스페이스 전체, node_modules 있을 때만)
- backend/**/*.kt     → 편집 시점 검사 없음(gradle이 느림). 커밋 전 /evening에서 gradlew test.

실패 시 exit 2 + stderr → Claude에게 즉시 피드백되어 다음 작업 전에 고치게 강제한다.
검증 도구가 없으면 조용히 통과한다(누락 경고는 SessionStart 훅이 이미 주입).
"""
import json
import os
import shutil
import subprocess
import sys

# Windows 콘솔 기본 cp949로는 한글 피드백이 깨져 전달됨 → UTF-8 강제
sys.stderr.reconfigure(encoding="utf-8")

ROOT = os.environ.get("CLAUDE_PROJECT_DIR", ".")
TAIL = 30  # 피드백은 간결하게 — 에러 출력 마지막 N줄만


def fail(header: str, output: str) -> None:
    lines = output.strip().splitlines()
    print(f"[verify] {header} — 다음 작업 전에 고칠 것 (§12.5):", file=sys.stderr)
    print("\n".join(lines[-TAIL:]), file=sys.stderr)
    sys.exit(2)


def run(cmd: str, cwd: str) -> "subprocess.CompletedProcess[str]":
    # encoding 미지정 시 Windows가 cp949로 디코드 → 검사 도구의 한글 출력에서 UnicodeDecodeError
    # → stdout=None → 훅이 exit 1로 죽어 실패가 침묵함. 반드시 utf-8 + errors=replace.
    return subprocess.run(cmd, cwd=cwd, shell=True, capture_output=True, text=True,
                          encoding="utf-8", errors="replace", timeout=120)


def main() -> None:
    try:
        data = json.load(sys.stdin)
    except Exception:
        sys.exit(0)

    fp = str((data.get("tool_input") or {}).get("file_path", "")).replace("\\", "/")
    if not fp:
        sys.exit(0)
    try:
        rel = os.path.relpath(fp, ROOT).replace("\\", "/")
    except ValueError:
        sys.exit(0)  # 프로젝트 밖(다른 드라이브 등)은 검사 대상 아님

    if rel.startswith("ai-service/") and rel.endswith(".py"):
        venv_ruff = os.path.join(ROOT, "ai-service", ".venv", "Scripts", "ruff.exe")
        ruff = venv_ruff if os.path.exists(venv_ruff) else shutil.which("ruff")
        if not ruff:
            sys.exit(0)
        r = run(f'"{ruff}" check "{fp}"', os.path.join(ROOT, "ai-service"))
        if r.returncode != 0:
            fail(f"ruff 실패: {rel}", (r.stdout or "") + (r.stderr or ""))

    elif (rel.startswith(("web/", "packages/"))) and rel.endswith((".ts", ".tsx")):
        if not os.path.isdir(os.path.join(ROOT, "node_modules")):
            sys.exit(0)
        r = run("npm run typecheck", ROOT)
        if r.returncode != 0:
            fail("typecheck 실패", (r.stdout or "") + (r.stderr or ""))

    sys.exit(0)


if __name__ == "__main__":
    main()
