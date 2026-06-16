"""파서 공통 타입/예외."""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class Page:
    """추출된 페이지 단위 텍스트. page_no는 1부터."""

    page_no: int
    text: str


@dataclass(frozen=True)
class ParsedDoc:
    """파서 출력: 페이지 목록 + 전체 페이지 수."""

    pages: list[Page]
    page_count: int


class ParserError(RuntimeError):
    """파싱 실패(손상 파일·텍스트 없음 등)."""


class UnsupportedFormatError(ParserError):
    """디스패처에 등록되지 않은 포맷."""
