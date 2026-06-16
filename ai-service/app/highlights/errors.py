"""하이라이트 추출 계층 예외."""

from __future__ import annotations


class HighlightError(RuntimeError):
    """하이라이트 추출 실패(청크 없음·LLM 출력 형식 오류 등)."""
