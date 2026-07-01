"""번역 도메인 예외."""

from __future__ import annotations


class TranslateError(RuntimeError):
    """번역 실패(빈 입력·과도한 길이·빈 결과 등)."""
