"""토큰 수 추정.

실제 토크나이저(tiktoken 등) 벤더에 묶지 않기 위해(CLAUDE.md §10) 경량 휴리스틱을
쓴다. 라틴 단어/숫자 런은 1개, 그 외 비공백 문자(CJK·문장부호)는 각 1개로 센다.
청크 크기 산정용 단조 추정치이며 정확한 과금 토큰 수가 아니다.
"""

from __future__ import annotations

import re

_TOKEN_RE = re.compile(r"[A-Za-z0-9]+|[^\sA-Za-z0-9]")


def estimate_tokens(text: str) -> int:
    return len(_TOKEN_RE.findall(text))
