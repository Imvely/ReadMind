"""Phase 0 재사용률 지표 실행기 — reuse_rate.sql을 운영 DB에 읽기 전용 실행.

사용법 (레포 루트에서, ai-service venv의 psycopg 사용):
    ai-service/.venv/Scripts/python.exe scripts/metrics/run_reuse_rate.py

접속 문자열은 환경변수 POSTGRES_URL, 없으면 레포 루트 .env의 POSTGRES_URL을 읽는다.
읽기 전용 트랜잭션으로만 실행하므로 운영 데이터에 영향이 없다.
"""

from __future__ import annotations

import os
import re
import sys
from pathlib import Path

import psycopg

sys.stdout.reconfigure(encoding="utf-8")  # Windows cp949 콘솔 한글/특수문자 깨짐 방지

REPO_ROOT = Path(__file__).resolve().parents[2]
SQL_PATH = Path(__file__).with_name("reuse_rate.sql")


def load_db_url() -> str:
    url = os.environ.get("POSTGRES_URL")
    if not url:
        env_file = REPO_ROOT / ".env"
        if env_file.exists():
            for line in env_file.read_text(encoding="utf-8").splitlines():
                m = re.match(r"POSTGRES_URL=(.+)", line.strip())
                if m:
                    url = m.group(1).strip().strip("'\"")
                    break
    if not url:
        sys.exit("POSTGRES_URL이 없습니다 (환경변수 또는 레포 루트 .env).")
    return url


def split_statements(sql_text: str) -> list[str]:
    """주석 제거 후 ';' 기준 분리. psycopg3는 한 execute에 한 문장만 허용."""
    no_comments = "\n".join(
        line for line in sql_text.splitlines() if not line.lstrip().startswith("--")
    )
    return [s.strip() for s in no_comments.split(";") if s.strip()]


def print_table(columns: list[str], rows: list[tuple]) -> None:
    widths = [
        max(len(str(c)), *(len(str(r[i])) for r in rows)) if rows else len(str(c))
        for i, c in enumerate(columns)
    ]
    header = " | ".join(str(c).ljust(w) for c, w in zip(columns, widths, strict=True))
    print(header)
    print("-+-".join("-" * w for w in widths))
    for r in rows:
        print(" | ".join(str(v).ljust(w) for v, w in zip(r, widths, strict=True)))


def main() -> None:
    url = load_db_url()
    host = re.search(r"@([^/:?]+)", url)
    print(f"[접속] {host.group(1) if host else '(호스트 미상)'} — 읽기 전용\n")

    statements = split_statements(SQL_PATH.read_text(encoding="utf-8"))
    titles = ["1) 재사용률", "2) 업로드 수 분포", "3) 활성화율(요약 경험)"]

    with psycopg.connect(url, connect_timeout=15) as conn:
        conn.read_only = True  # 운영 DB 보호 — 어떤 쓰기도 불가
        with conn.cursor() as cur:
            for i, stmt in enumerate(statements):
                print(f"── {titles[i] if i < len(titles) else f'{i + 1})'} ──")
                cur.execute(stmt)
                cols = [d.name for d in cur.description or []]
                print_table(cols, cur.fetchall())
                print()


if __name__ == "__main__":
    main()
