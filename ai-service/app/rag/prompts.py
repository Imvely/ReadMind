"""RAG Q&A 프롬프트 (명세서 §5.4). 발췌 근거 기반 + 인용 강제(환각 방지)."""

from __future__ import annotations

from app.parse.ports import RetrievedChunk
from app.schemas.qa import QaTurn

_SYSTEM = (
    "너는 문서 질의응답 도우미다. 아래 '발췌문'에만 근거해 답한다. "
    "발췌문에 답이 없으면 추측하지 말고 모른다고 답한다. "
    "발췌 밖의 사전지식을 쓰지 않는다. "
    "아래 JSON 객체 하나만 출력한다(코드펜스/추가텍스트 없이):\n"
    '{"answer": "답변", "citations": [근거로 사용한 발췌의 chunkIndex 정수, ...]}\n'
    "답을 만들 때 실제로 사용한 발췌의 chunkIndex만 citations에 넣는다. "
    "근거가 없으면 citations는 빈 배열로 둔다."
)


def system() -> str:
    return _SYSTEM


def _format_excerpts(hits: list[RetrievedChunk]) -> str:
    blocks = []
    for h in hits:
        page = "" if h.page_no is None else f", page={h.page_no}"
        blocks.append(f"[chunkIndex={h.chunk_index}{page}]\n{h.content}")
    return "\n\n".join(blocks)


def _format_history(history: list[QaTurn]) -> str:
    if not history:
        return ""
    lines = [f"{t.role}: {t.content}" for t in history]
    return "이전 대화:\n" + "\n".join(lines) + "\n\n"


def user(question: str, hits: list[RetrievedChunk], history: list[QaTurn]) -> str:
    return (
        f"{_format_history(history)}"
        f"발췌문:\n{_format_excerpts(hits)}\n\n"
        f"질문: {question}"
    )
