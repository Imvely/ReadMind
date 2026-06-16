"""요약 오케스트레이션 (명세서 §5.3).

document_chunks를 읽어(재파싱 X) 짧으면 단일 호출, 길면 map-reduce로 요약한다.
LLM 출력은 고정 pydantic 스키마로 검증해 누락/형식오류를 막는다.

소유권(user_id)·쿼터 검증은 백엔드 게이트(be-ai-gate-cache, §4.4)가 담당한다 —
이 서비스는 내부 전용이며 서비스 토큰으로만 호출된다(§5).
"""

from __future__ import annotations

from pydantic import ValidationError

from app.chunking.tokens import estimate_tokens
from app.core.jsonout import JsonOutputError, parse_json_object
from app.parse.ports import ChunkReader, ChunkText
from app.providers.llm import LLMProvider
from app.schemas.summarize import (
    PaperSummary,
    PlainSummary,
    SummarizeRequest,
    SummaryStyle,
)
from app.summarize import prompts
from app.summarize.errors import SummarizeError

# 단일 호출로 처리할 본문 토큰 상한. 초과 시 map-reduce.
SINGLE_PASS_TOKENS = 6000
# map 단계에서 한 번에 묶을 청크 토큰 상한.
MAP_GROUP_TOKENS = 3000

Summary = PaperSummary | PlainSummary


def summarize(
    req: SummarizeRequest,
    *,
    reader: ChunkReader,
    llm: LLMProvider,
    single_pass_tokens: int = SINGLE_PASS_TOKENS,
    map_group_tokens: int = MAP_GROUP_TOKENS,
) -> Summary:
    chunks = reader.fetch_document_chunks(req.document_id)
    if not chunks:
        raise SummarizeError("문서 청크가 없다(파싱되지 않았거나 빈 문서).")

    is_paper = req.style is SummaryStyle.PAPER
    system = prompts.paper_system() if is_paper else prompts.plain_system()

    full_text = "\n\n".join(c.content for c in chunks)
    if estimate_tokens(full_text) <= single_pass_tokens:
        raw = llm.complete(system, prompts.user_payload(full_text), json_mode=True)
    else:
        notes = _map_notes(chunks, llm=llm, group_tokens=map_group_tokens)
        raw = llm.complete(system, prompts.reduce_user(notes), json_mode=True)

    try:
        data = parse_json_object(raw)
    except JsonOutputError as exc:
        raise SummarizeError(str(exc)) from exc
    model = PaperSummary if is_paper else PlainSummary
    try:
        return model(**data)
    except ValidationError as exc:
        raise SummarizeError(
            f"요약 JSON이 {req.style.value} 스키마와 불일치: {exc}"
        ) from exc


def _map_notes(
    chunks: list[ChunkText], *, llm: LLMProvider, group_tokens: int
) -> str:
    """청크를 토큰 예산 단위로 묶어 부분 요약 노트를 만든다(map)."""
    notes: list[str] = []
    group: list[str] = []
    group_tok = 0

    def flush() -> None:
        nonlocal group, group_tok
        if not group:
            return
        text = "\n\n".join(group)
        notes.append(llm.complete(prompts.map_system(), prompts.map_user(text)))
        group = []
        group_tok = 0

    for c in chunks:
        tok = estimate_tokens(c.content)
        if group and group_tok + tok > group_tokens:
            flush()
        group.append(c.content)
        group_tok += tok
    flush()
    return "\n".join(notes)
