"""EPUB 파서 — ebooklib로 스파인 문서(XHTML)별 텍스트 추출 (명세서 §5.2).

EPUB는 챕터/섹션(HTML 문서) 단위라, 각 문서 아이템을 한 '페이지'로 매핑한다
(page_no는 텍스트가 있는 문서 순서대로 1부터). HTML 태그는 제거하고 텍스트만.
ebooklib.read_epub 은 파일 경로를 받으므로 임시 파일을 경유한다.
"""

from __future__ import annotations

import os
import tempfile

import ebooklib
from bs4 import BeautifulSoup
from ebooklib import epub

from app.parsers.base import Page, ParsedDoc, ParserError


def parse_epub(data: bytes) -> ParsedDoc:
    tmp_path: str | None = None
    try:
        with tempfile.NamedTemporaryFile(suffix=".epub", delete=False) as tmp:
            tmp.write(data)
            tmp_path = tmp.name
        try:
            book = epub.read_epub(tmp_path)
        except Exception as exc:  # 손상/비EPUB 등
            raise ParserError(f"EPUB 열기 실패: {exc!r}") from exc

        pages: list[Page] = []
        for item in book.get_items_of_type(ebooklib.ITEM_DOCUMENT):
            # 목차(nav) 문서는 본문이 아니므로 제외.
            props = getattr(item, "properties", None) or []
            if "nav" in props or isinstance(item, epub.EpubNav):
                continue
            soup = BeautifulSoup(item.get_content(), "html.parser")
            text = soup.get_text("\n").strip()
            if not text:
                continue
            pages.append(Page(page_no=len(pages) + 1, text=text))
    finally:
        if tmp_path and os.path.exists(tmp_path):
            os.unlink(tmp_path)

    if not pages:
        raise ParserError("EPUB에서 추출 가능한 텍스트가 없다.")
    return ParsedDoc(pages=pages, page_count=len(pages))
