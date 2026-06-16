"""핵심 문장 추출 오케스트레이션 (명세서 §5.5).

document_chunks를 읽어 LLM으로 핵심 문장을 뽑되, 각 문장이 실제 원문에 존재하는지
대조해 pageNo를 원문 청크에서 가져온다. 원문에 없는(지어낸) 문장은 버린다 —
환각 방지 + 모든 하이라이트가 실제 위치로 점프 가능(§3 근거 철학).

소유권/쿼터는 백엔드 게이트 담당(내부 전용, 서비스 토큰 §5).
"""

from __future__ import annotations

from app.chunking.tokens import estimate_tokens
from app.core.jsonout import JsonOutputError, parse_json_object
from app.highlights import prompts
from app.highlights.errors import HighlightError
from app.parse.ports import ChunkReader, ChunkText
from app.providers.llm import LLMProvider
from app.schemas.highlights import Highlight, SuggestHighlightsRequest

# LLM에 한 번에 넣을 청크 토큰 상한.
GROUP_TOKENS = 3000
# 윈도우당 요청할 문장 수.
PER_WINDOW = 5
# 원문 대조 매칭에 요구하는 최소 정규화 길이(사소한 단편 오매칭 방지).
_MIN_MATCH_LEN = 8


def _norm(text: str) -> str:
    return " ".join(text.split())


def suggest_highlights(
    req: SuggestHighlightsRequest,
    *,
    reader: ChunkReader,
    llm: LLMProvider,
    group_tokens: int = GROUP_TOKENS,
) -> list[Highlight]:
    chunks = reader.fetch_document_chunks(req.document_id)
    if not chunks:
        raise HighlightError("문서 청크가 없다(파싱되지 않았거나 빈 문서).")

    # 원문 대조용 인덱스: (정규화 본문, page_no).
    indexed = [(_norm(c.content), c.page_no) for c in chunks]

    results: list[Highlight] = []
    seen: set[str] = set()

    for window in _windows(chunks, group_tokens):
        if len(results) >= req.limit:
            break
        raw = llm.complete(
            prompts.system(), prompts.user(window, PER_WINDOW), json_mode=True
        )
        for item in _extract_items(raw):
            if len(results) >= req.limit:
                break
            text = str(item.get("text", "")).strip()
            reason = str(item.get("reason", "")).strip()
            norm = _norm(text)
            if len(norm) < _MIN_MATCH_LEN or norm in seen:
                continue
            page = _locate_page(norm, indexed)
            if page is _NOT_FOUND:  # 원문에 없음 → 환각으로 보고 버림
                continue
            seen.add(norm)
            results.append(Highlight(page_no=page, text=text, reason=reason))

    return results


_NOT_FOUND = object()


def _locate_page(norm_text: str, indexed: list[tuple[str, int | None]]):
    for norm_content, page_no in indexed:
        if norm_text in norm_content:
            return page_no
    return _NOT_FOUND


def _windows(chunks: list[ChunkText], group_tokens: int) -> list[str]:
    """청크를 토큰 예산 단위로 묶어 윈도우 텍스트 목록을 만든다."""
    windows: list[str] = []
    group: list[str] = []
    group_tok = 0
    for c in chunks:
        tok = estimate_tokens(c.content)
        if group and group_tok + tok > group_tokens:
            windows.append("\n\n".join(group))
            group, group_tok = [], 0
        group.append(c.content)
        group_tok += tok
    if group:
        windows.append("\n\n".join(group))
    return windows


def _extract_items(raw: str) -> list[dict]:
    try:
        data = parse_json_object(raw)
    except JsonOutputError as exc:
        raise HighlightError(str(exc)) from exc
    items = data.get("highlights", [])
    if not isinstance(items, list):
        raise HighlightError(f"highlights가 배열이 아니다: {items!r}")
    return [i for i in items if isinstance(i, dict)]
