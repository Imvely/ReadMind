"""포맷별 파서 디스패처 (명세서 §5.2).

새 포맷은 parser 함수를 추가하고 _REGISTRY에 등록만 하면 된다 — 스키마/파이프라인
변경 없음(CLAUDE.md §Python: 디스패처 패턴).
"""

from __future__ import annotations

from collections.abc import Callable

from app.parsers.base import ParsedDoc, ParserError, UnsupportedFormatError
from app.parsers.pdf import parse_pdf

# format(소문자) → 파서 함수(bytes -> ParsedDoc)
_REGISTRY: dict[str, Callable[[bytes], ParsedDoc]] = {
    "pdf": parse_pdf,
}


def parse_document(fmt: str, data: bytes) -> ParsedDoc:
    """포맷 문자열로 파서를 골라 실행. 미지원 포맷은 명시적 에러."""
    parser = _REGISTRY.get(fmt.strip().lower())
    if parser is None:
        raise UnsupportedFormatError(
            f"지원하지 않는 포맷: {fmt!r} (지원: {sorted(_REGISTRY)})"
        )
    return parser(data)


def supported_formats() -> list[str]:
    return sorted(_REGISTRY)


__all__ = [
    "ParsedDoc",
    "ParserError",
    "UnsupportedFormatError",
    "parse_document",
    "supported_formats",
]
