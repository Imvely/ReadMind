"""POST /ai/summarize 라우터 (명세서 §5.3)."""

from __future__ import annotations

from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, status

from app.api.deps import get_chunk_reader, get_llm, verify_service_token
from app.parse.ports import ChunkReader
from app.providers.errors import ProviderError
from app.providers.llm import LLMProvider
from app.schemas.summarize import PaperSummary, PlainSummary, SummarizeRequest
from app.summarize.errors import SummarizeError
from app.summarize.service import summarize

router = APIRouter(prefix="/ai", tags=["ai"])

ReaderDep = Annotated[ChunkReader, Depends(get_chunk_reader)]
LLMDep = Annotated[LLMProvider, Depends(get_llm)]


@router.post(
    "/summarize",
    response_model=PaperSummary | PlainSummary,
    dependencies=[Depends(verify_service_token)],
)
def summarize_endpoint(
    req: SummarizeRequest,
    reader: ReaderDep,
    llm: LLMDep,
) -> PaperSummary | PlainSummary:
    try:
        return summarize(req, reader=reader, llm=llm)
    except SummarizeError as exc:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc)
        ) from exc
    except ProviderError as exc:
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY, detail=str(exc)
        ) from exc
