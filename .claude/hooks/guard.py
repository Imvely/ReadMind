#!/usr/bin/env python
"""PreToolUse guard — CLAUDE.md §3/§10/§12.5의 기계적 강제.

차단 대상 (exit 2 = 툴 호출 차단, stderr가 Claude에게 전달됨):
1. 시크릿 하드코딩 (실키 포맷: sk-/AIza/hf_/ghp_/AKIA/AQ./private key 등)
2. `.env` 파일의 git 스테이징·커밋 (`.env.example`은 허용)
3. .txt 문서 생성 (Fasoo DRM이 .txt 평문을 손상 — 문서는 .md로만. requirements*.txt 등 생태계 강제 파일만 예외)

주의: 구현은 raw grep이 아니라 훅 JSON의 해당 필드만 검사한다 — 파일 내용에
규칙 설명 문구가 들어있다고 오탐하지 않게 하기 위함.
"""
import json
import re
import sys

# Windows 콘솔 기본 cp949로는 한글 차단 메시지가 깨져 전달됨 → UTF-8 강제
sys.stderr.reconfigure(encoding="utf-8")

TXT_ALLOWLIST = re.compile(r"(^|[/\\])(requirements[^/\\]*\.txt|robots\.txt|CMakeLists\.txt)$", re.I)

SECRET_PATTERNS = [
    r"sk-[A-Za-z0-9_-]{20,}",                      # OpenAI / Anthropic
    r"AIza[0-9A-Za-z_-]{35}",                      # Google API key
    r"hf_[A-Za-z0-9]{30,}",                        # HuggingFace token
    r"ghp_[A-Za-z0-9]{36}|github_pat_[A-Za-z0-9_]{40,}",  # GitHub
    r"AKIA[0-9A-Z]{16}",                           # AWS access key
    r"xox[baprs]-[A-Za-z0-9-]{10,}",               # Slack
    r"AQ\.[A-Za-z0-9_-]{30,}",                     # Vertex Express key
    r"-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----",
    r"(?i)jwt[_.]?secret\s*[:=]\s*['\"][^'\"]{12,}['\"]",
]

ENV_STAGE_RE = re.compile(r"git\s+(?:add|commit)[^|;]*\.env(?:$|[^.\w])")


def block(msg: str) -> None:
    print(f"BLOCKED: {msg}", file=sys.stderr)
    sys.exit(2)


def main() -> None:
    try:
        data = json.load(sys.stdin)
    except Exception:
        sys.exit(0)  # 파싱 불가 시 통과 (가드는 fail-open, 검증은 verify가 담당)

    tool = data.get("tool_name", "")
    ti = data.get("tool_input", {}) or {}
    file_path = str(ti.get("file_path", ""))
    command = str(ti.get("command", ""))
    # 시크릿 스캔 대상: 새로 쓰이는 텍스트 전부
    texts = " ".join(str(ti.get(k, "")) for k in ("command", "content", "new_string"))

    # 1) 시크릿 하드코딩
    for pat in SECRET_PATTERNS:
        m = re.search(pat, texts)
        if m:
            block(f"시크릿 하드코딩 감지({m.group()[:12]}…). .env로 옮기고 .env.example엔 자리표시자만. (§3)")

    # 2) .env 스테이징·커밋 차단
    if ENV_STAGE_RE.search(command):
        block(".env 커밋 시도. .env는 .gitignore 대상, .env.example만 커밋. (§3)")

    # 3) .txt 문서 생성 금지 (DRM) — 파일 툴 + 셸 리다이렉트 모두
    if tool in ("Write", "Edit") and file_path.lower().endswith(".txt") and not TXT_ALLOWLIST.search(file_path):
        block("문서 산출물은 .txt 금지 → .md로 작성 (Fasoo DRM이 .txt를 손상시킴. §12.1)")
    if tool in ("Bash", "PowerShell"):
        # 셸 리다이렉트(> >>) 와 PS 파일쓰기 cmdlet(파라미터 값 포함) 모두 커버
        m = re.search(r">>?\s*['\"]?([^\s'\"|;>]+\.txt)\b", command, re.I) or \
            re.search(r"\b(?:tee|Out-File|Set-Content|Add-Content)\b[^|;&]*?([^\s'\"|;>]+\.txt)\b", command, re.I)
        target = m.group(m.lastindex) if m else None
        if target and not TXT_ALLOWLIST.search(target):
            block(f"'{target}' — .txt 생성 금지 → .md로 작성 (Fasoo DRM. §12.1)")

    sys.exit(0)


if __name__ == "__main__":
    main()
