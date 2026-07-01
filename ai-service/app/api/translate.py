"""POST /ai/translate 라우터 (명세서 §5, §4.4). 원문대조 번역."""

from __future__ import annotations

from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, status

from app.api.deps import get_llm, verify_service_token
from app.providers.errors import ProviderError
from app.providers.llm import LLMProvider
from app.schemas.translate import TranslateRequest, TranslateResponse
from app.translate.errors import TranslateError
from app.translate.service import translate

router = APIRouter(prefix="/ai", tags=["ai"])

LLMDep = Annotated[LLMProvider, Depends(get_llm)]


@router.post(
    "/translate",
    response_model=TranslateResponse,
    dependencies=[Depends(verify_service_token)],
)
def translate_endpoint(req: TranslateRequest, llm: LLMDep) -> TranslateResponse:
    try:
        return translate(req, llm=llm)
    except TranslateError as exc:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc)
        ) from exc
    except ProviderError as exc:
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY, detail=str(exc)
        ) from exc
