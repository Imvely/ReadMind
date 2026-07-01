"""TXT 파서 — 평문 디코딩 (명세서 §5.2).

TXT는 페이지 개념이 없어 전체를 단일 페이지(page_no=1)로 낸다. 청킹은 상위
파이프라인이 담당(포맷 무관). 인코딩은 흔한 순서로 시도 후 마지막에 대체 문자.
"""

from __future__ import annotations

from app.parsers.base import Page, ParsedDoc, ParserError

_ENCODINGS = ("utf-8-sig", "utf-8", "cp949", "euc-kr")


def parse_txt(data: bytes) -> ParsedDoc:
    text: str | None = None
    for enc in _ENCODINGS:
        try:
            text = data.decode(enc)
            break
        except UnicodeDecodeError:
            continue
    if text is None:
        text = data.decode("utf-8", errors="replace")

    if not text.strip():
        raise ParserError("TXT에 텍스트가 없다.")
    return ParsedDoc(pages=[Page(page_no=1, text=text)], page_count=1)
