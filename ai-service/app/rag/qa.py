"""RAG Q&A 오케스트레이션 (명세서 §5.4) — 환각 방지 필수.

질문 임베딩 → 문서 내 top-k 코사인 검색 → 발췌만 근거로 LLM 답변 →
LLM이 인용한 chunkIndex를 실제 검색 결과와 교차검증해 sources를 만든다.

핵심 규칙(CLAUDE.md §3): 근거(sources) 없는 답변은 내지 않는다. 검증된 인용이
하나도 없으면 답을 '모른다'로 강등한다 — snippet/pageNo는 실제 청크에서만 온다.
"""

from __future__ import annotations

from app.core.jsonout import JsonOutputError, parse_json_object
from app.parse.ports import ChunkRetriever
from app.providers.embedding import EmbeddingProvider
from app.providers.llm import LLMProvider
from app.rag import prompts
from app.rag.errors import QaError
from app.schemas.qa import QaRequest, QaResponse, Source

DEFAULT_TOP_K = 6
SNIPPET_MAX = 200
NO_EVIDENCE = "문서에서 답을 찾을 수 없습니다."


def answer_question(
    req: QaRequest,
    *,
    retriever: ChunkRetriever,
    embedder: EmbeddingProvider,
    llm: LLMProvider,
    k: int = DEFAULT_TOP_K,
) -> QaResponse:
    query_embedding = embedder.embed([req.question])[0]
    hits = retriever.search_similar_chunks(req.document_id, query_embedding, k)
    if not hits:
        return QaResponse(answer=NO_EVIDENCE, sources=[])

    raw = llm.complete(
        prompts.system(),
        prompts.user(req.question, hits, req.history),
        json_mode=True,
    )
    try:
        data = parse_json_object(raw)
    except JsonOutputError as exc:
        raise QaError(str(exc)) from exc

    answer = str(data.get("answer", "")).strip()
    by_index = {h.chunk_index: h for h in hits}

    sources: list[Source] = []
    seen: set[int] = set()
    for cited in data.get("citations", []):
        idx = _as_int(cited)
        if idx is None or idx not in by_index or idx in seen:
            continue
        seen.add(idx)
        hit = by_index[idx]
        sources.append(
            Source(
                chunk_index=hit.chunk_index,
                page_no=hit.page_no,
                snippet=_snippet(hit.content),
            )
        )

    # 검증된 근거가 없으면 근거 없는 답변을 내보내지 않는다(§3).
    if not sources:
        return QaResponse(answer=NO_EVIDENCE, sources=[])
    if not answer:
        raise QaError("LLM이 빈 답변을 반환했다.")
    return QaResponse(answer=answer, sources=sources)


def _as_int(value: object) -> int | None:
    try:
        return int(value)  # type: ignore[arg-type]
    except (TypeError, ValueError):
        return None


def _snippet(content: str) -> str:
    text = " ".join(content.split())
    return text if len(text) <= SNIPPET_MAX else text[:SNIPPET_MAX].rstrip() + "…"
