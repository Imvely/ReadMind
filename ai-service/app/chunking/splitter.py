"""청킹 (명세서 §5.2): 약 500~800 토큰, 문단/문장 경계 우선, 오버랩 80토큰.

페이지별 텍스트를 경계 단위 세그먼트로 쪼갠 뒤 목표 토큰까지 그리디로 모은다.
각 청크는 시작 세그먼트의 page_no를 보존한다(하이라이트/근거 위치 추적용).
"""

from __future__ import annotations

import re
from dataclasses import dataclass

from app.chunking.tokens import estimate_tokens
from app.parsers.base import Page

DEFAULT_TARGET_TOKENS = 800
DEFAULT_OVERLAP_TOKENS = 80
# 목표의 일정 비율을 넘으면 청크를 끊는다(문단 경계 존중).
_SOFT_RATIO = 0.62  # ~500 토큰 하한 근처에서 경계가 있으면 끊을 수 있게.

_PARA_RE = re.compile(r"\n\s*\n")
# 문장 종결: 라틴 마침표류 + CJK 종결부호. 뒤 공백/끝을 경계로.
_SENT_RE = re.compile(r"(?<=[.!?。！？])\s+")


@dataclass(frozen=True)
class Chunk:
    chunk_index: int
    page_no: int | None
    content: str
    token_count: int


@dataclass(frozen=True)
class _Seg:
    page_no: int
    text: str
    tokens: int


def _split_segments(pages: list[Page], hard_limit: int) -> list[_Seg]:
    """페이지 → (문단 → 문장 → 단어) 순으로 hard_limit 이하 세그먼트 생성."""
    segs: list[_Seg] = []
    for page in pages:
        for para in _PARA_RE.split(page.text):
            para = para.strip()
            if not para:
                continue
            _emit_unit(para, page.page_no, hard_limit, segs)
    return segs


def _emit_unit(text: str, page_no: int, hard_limit: int, out: list[_Seg]) -> None:
    tok = estimate_tokens(text)
    if tok <= hard_limit:
        out.append(_Seg(page_no=page_no, text=text, tokens=tok))
        return
    # 문단이 너무 큼 → 문장 분해.
    sentences = _SENT_RE.split(text)
    if len(sentences) > 1:
        for sent in sentences:
            sent = sent.strip()
            if sent:
                _emit_unit(sent, page_no, hard_limit, out)
        return
    # 단일 문장도 한계 초과 → 단어 윈도우로 하드 분할.
    words = text.split()
    cur: list[str] = []
    for w in words:
        cur.append(w)
        if estimate_tokens(" ".join(cur)) >= hard_limit:
            out.append(_make_seg(page_no, cur))
            cur = []
    if cur:
        out.append(_make_seg(page_no, cur))


def _make_seg(page_no: int, words: list[str]) -> _Seg:
    text = " ".join(words)
    return _Seg(page_no=page_no, text=text, tokens=estimate_tokens(text))


def _dominant_page(segs: list[_Seg]) -> int | None:
    """청크의 page_no = 토큰을 가장 많이 기여한 페이지(근거 위치 추적에 유용)."""
    if not segs:
        return None
    tally: dict[int, int] = {}
    for s in segs:
        tally[s.page_no] = tally.get(s.page_no, 0) + s.tokens
    return max(tally, key=lambda p: tally[p])


def chunk_pages(
    pages: list[Page],
    *,
    target_tokens: int = DEFAULT_TARGET_TOKENS,
    overlap_tokens: int = DEFAULT_OVERLAP_TOKENS,
) -> list[Chunk]:
    if target_tokens <= 0:
        raise ValueError("target_tokens는 양수여야 한다.")
    overlap_tokens = max(0, min(overlap_tokens, target_tokens - 1))
    soft_floor = int(target_tokens * _SOFT_RATIO)

    segs = _split_segments(pages, hard_limit=target_tokens)
    chunks: list[Chunk] = []
    cur: list[_Seg] = []
    cur_tokens = 0
    carried = 0  # cur 앞쪽 몇 개가 직전 청크에서 이월된 오버랩 세그먼트인지.

    def flush() -> None:
        nonlocal cur, cur_tokens, carried
        if not cur:
            return
        content = "\n\n".join(s.text for s in cur).strip()
        chunks.append(
            Chunk(
                chunk_index=len(chunks),
                page_no=_dominant_page(cur),
                content=content,
                token_count=estimate_tokens(content),
            )
        )
        # 오버랩: 끝에서부터 overlap_tokens 이하만큼 다음 청크로 이월.
        carry: list[_Seg] = []
        carry_tokens = 0
        for s in reversed(cur):
            if carry_tokens + s.tokens > overlap_tokens:
                break
            carry.insert(0, s)
            carry_tokens += s.tokens
        cur = carry
        cur_tokens = carry_tokens
        carried = len(carry)

    for seg in segs:
        # 경계 존중: 하한을 넘었고 추가 시 목표 초과면 먼저 끊는다.
        if cur and cur_tokens >= soft_floor and cur_tokens + seg.tokens > target_tokens:
            flush()
        cur.append(seg)
        cur_tokens += seg.tokens
        if cur_tokens >= target_tokens:
            flush()

    # 남은 cur에 이월분 외 새 내용이 있으면 마지막 청크로 낸다.
    if len(cur) > carried:
        flush()
    return chunks
