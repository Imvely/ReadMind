"""POST /ai/qa 라우터 (명세서 §5.4). RAG Q&A — sources 필수."""

from __future__ import annotations

from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, status

from app.api.deps import (
    get_chunk_retriever,
    get_embedder,
    get_llm,
    verify_service_token,
)
from app.parse.ports import ChunkRetriever
from app.providers.embedding import EmbeddingProvider
from app.providers.errors import ProviderError
from app.providers.llm import LLMProvider
from app.rag.errors import QaError
from app.rag.qa import answer_question
from app.schemas.qa import QaRequest, QaResponse

router = APIRouter(prefix="/ai", tags=["ai"])

RetrieverDep = Annotated[ChunkRetriever, Depends(get_chunk_retriever)]
EmbedderDep = Annotated[EmbeddingProvider, Depends(get_embedder)]
LLMDep = Annotated[LLMProvider, Depends(get_llm)]


@router.post(
    "/qa",
    response_model=QaResponse,
    response_model_by_alias=True,
    dependencies=[Depends(verify_service_token)],
)
def qa_endpoint(
    req: QaRequest,
    retriever: RetrieverDep,
    embedder: EmbedderDep,
    llm: LLMDep,
) -> QaResponse:
    try:
        return answer_question(req, retriever=retriever, embedder=embedder, llm=llm)
    except QaError as exc:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc)
        ) from exc
    except ProviderError as exc:
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY, detail=str(exc)
        ) from exc
