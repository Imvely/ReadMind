"""PDF 파서 — PyMuPDF(fitz)로 페이지별 텍스트 추출 (명세서 §5.2).

스캔 PDF(텍스트 레이어 없음)는 OCR 대상이지만 OCR은 후순위(§8 P3)다. 여기서는
추출 텍스트가 전혀 없으면 ParserError로 올려 상위에서 처리하게 한다.
"""

from __future__ import annotations

import fitz  # PyMuPDF

from app.parsers.base import Page, ParsedDoc, ParserError


def parse_pdf(data: bytes) -> ParsedDoc:
    try:
        doc = fitz.open(stream=data, filetype="pdf")
    except Exception as exc:  # 손상 파일 등
        raise ParserError(f"PDF 열기 실패: {exc!r}") from exc

    try:
        pages: list[Page] = []
        for i, page in enumerate(doc):
            text = page.get_text("text") or ""
            pages.append(Page(page_no=i + 1, text=text))
        page_count = doc.page_count
    finally:
        doc.close()

    if not any(p.text.strip() for p in pages):
        raise ParserError(
            "PDF에서 추출 가능한 텍스트가 없다(스캔 문서일 수 있음 → OCR은 P3)."
        )
    return ParsedDoc(pages=pages, page_count=page_count)
