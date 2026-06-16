"""PDF 파서 테스트 — 실제 PyMuPDF로 생성한 PDF 사용."""

from __future__ import annotations

import pytest

from app.parsers import parse_document
from app.parsers.base import ParserError, UnsupportedFormatError
from tests.conftest import make_pdf


def test_parse_pdf_pages_and_count():
    # PyMuPDF 기본 폰트(base14)는 CJK 미지원 → 픽스처는 ASCII로 작성.
    pdf = make_pdf(["First page content.", "Second page content."])
    parsed = parse_document("pdf", pdf)
    assert parsed.page_count == 2
    assert [p.page_no for p in parsed.pages] == [1, 2]
    assert "First page" in parsed.pages[0].text
    assert "Second page" in parsed.pages[1].text


def test_parse_pdf_case_insensitive_format():
    pdf = make_pdf(["x"])
    assert parse_document("PDF", pdf).page_count == 1


def test_unsupported_format_raises():
    with pytest.raises(UnsupportedFormatError):
        parse_document("epub", b"whatever")


def test_corrupt_pdf_raises():
    with pytest.raises(ParserError):
        parse_document("pdf", b"not a real pdf")


def test_pdf_without_text_raises():
    # 텍스트를 넣지 않은 빈 페이지 → 추출 텍스트 없음.
    empty = make_pdf(["", ""])
    with pytest.raises(ParserError, match="텍스트가 없다"):
        parse_document("pdf", empty)
