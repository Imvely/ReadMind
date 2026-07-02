"""파서 공통 텍스트 정제 — NUL(0x00) 등 제어문자 제거.

PG text 컬럼은 NUL 금지 → 저장 시 psycopg DataError(PDF 추출물에 섞임).
Page 생성 시 자동 정제되어 모든 포맷이 안전하게 저장/임베딩된다.
"""

from __future__ import annotations

from app.parsers.base import Page, sanitize_text


def test_sanitize_removes_nul():
    assert sanitize_text("a\x00b") == "ab"


def test_sanitize_removes_other_c0_controls():
    # 0x01~0x08, 0x0b, 0x0c, 0x0e~0x1f 제거. 탭/개행/CR 은 보존.
    dirty = "x\x01\x07\x0b\x0c\x1fy\tz\nw\rv"
    assert sanitize_text(dirty) == "xy\tz\nw\rv"


def test_sanitize_keeps_normal_and_unicode():
    s = "정상 텍스트\tTransformers\n한국어 123"
    assert sanitize_text(s) == s


def test_page_text_is_sanitized_on_construction():
    page = Page(page_no=1, text="LoRA\x00 reduces\x00 params")
    assert page.text == "LoRA reduces params"
    assert "\x00" not in page.text


def test_page_without_control_chars_unchanged():
    page = Page(page_no=2, text="clean text")
    assert page.text == "clean text"
