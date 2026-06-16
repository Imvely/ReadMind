"""POST /ai/suggest-highlights 라우터 (명세서 §5.5)."""

from __future__ import annotations

from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, status

from app.api.deps import get_chunk_reader, get_llm, verify_service_token
from app.highlights.errors import HighlightError
from app.highlights.service import suggest_highlights
from app.parse.ports import ChunkReader
from app.providers.errors import ProviderError
from app.providers.llm import LLMProvider
from app.schemas.highlights import Highlight, SuggestHighlightsRequest

router = APIRouter(prefix="/ai", tags=["ai"])

ReaderDep = Annotated[ChunkReader, Depends(get_chunk_reader)]
LLMDep = Annotated[LLMProvider, Depends(get_llm)]


@router.post(
    "/suggest-highlights",
    response_model=list[Highlight],
    response_model_by_alias=True,
    dependencies=[Depends(verify_service_token)],
)
def suggest_highlights_endpoint(
    req: SuggestHighlightsRequest,
    reader: ReaderDep,
    llm: LLMDep,
) -> list[Highlight]:
    try:
        return suggest_highlights(req, reader=reader, llm=llm)
    except HighlightError as exc:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc)
        ) from exc
    except ProviderError as exc:
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY, detail=str(exc)
        ) from exc
