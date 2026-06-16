"""RAG Q&A 계층 예외."""

from __future__ import annotations


class QaError(RuntimeError):
    """Q&A 실패(LLM 출력 형식 오류 등)."""
