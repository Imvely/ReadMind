"""파서 공통 타입/예외."""

from __future__ import annotations

from dataclasses import dataclass

# 탭/개행/CR(0x09,0x0a,0x0d)만 보존하고 나머지 C0 제어문자는 제거.
# NUL(0x00)은 PostgreSQL text 컬럼이 금지 → 저장 시 psycopg DataError.
# (PDF 추출물에 종종 섞임. 임베딩/언어감지에도 무의미.)
_CONTROL_DELETE = {c: None for c in range(0x20) if c not in (0x09, 0x0A, 0x0D)}


def sanitize_text(text: str) -> str:
    """NUL 및 기타 C0 제어문자 제거(탭/개행/CR 보존)."""
    return text.translate(_CONTROL_DELETE)


@dataclass(frozen=True)
class Page:
    """추출된 페이지 단위 텍스트. page_no는 1부터.

    text는 생성 시 자동 정제된다(NUL 등 제어문자 제거) — 모든 파서 공통 경로라
    포맷별로 중복 처리하지 않는다.
    """

    page_no: int
    text: str

    def __post_init__(self) -> None:
        cleaned = sanitize_text(self.text)
        if cleaned != self.text:  # frozen dataclass — 필요할 때만 우회 대입
            object.__setattr__(self, "text", cleaned)


@dataclass(frozen=True)
class ParsedDoc:
    """파서 출력: 페이지 목록 + 전체 페이지 수."""

    pages: list[Page]
    page_count: int


class ParserError(RuntimeError):
    """파싱 실패(손상 파일·텍스트 없음 등)."""


class UnsupportedFormatError(ParserError):
    """디스패처에 등록되지 않은 포맷."""
