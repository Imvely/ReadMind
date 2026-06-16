"""요약 계층 예외."""

from __future__ import annotations


class SummarizeError(RuntimeError):
    """요약 실패(청크 없음·LLM 출력 형식 오류 등)."""
