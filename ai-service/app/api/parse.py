"""POST /ai/parse 라우터 (명세서 §5.2)."""

from __future__ import annotations

from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, status

from app.api.deps import (
    get_chunk_repository,
    get_embedder,
    get_storage,
    verify_service_token,
)
from app.parse.pipeline import run_parse
from app.parse.ports import ChunkRepository, Storage
from app.parsers import ParserError, UnsupportedFormatError
from app.providers.embedding import EmbeddingProvider
from app.providers.errors import ProviderError
from app.schemas.parse import ParseRequest, ParseResponse
from app.storage.s3 import S3DownloadError

router = APIRouter(prefix="/ai", tags=["ai"])

StorageDep = Annotated[Storage, Depends(get_storage)]
RepoDep = Annotated[ChunkRepository, Depends(get_chunk_repository)]
EmbedderDep = Annotated[EmbeddingProvider, Depends(get_embedder)]


@router.post(
    "/parse",
    response_model=ParseResponse,
    response_model_by_alias=True,
    dependencies=[Depends(verify_service_token)],
)
def parse_endpoint(
    req: ParseRequest,
    storage: StorageDep,
    repo: RepoDep,
    embedder: EmbedderDep,
) -> ParseResponse:
    try:
        return run_parse(req, storage=storage, repo=repo, embedder=embedder)
    except UnsupportedFormatError as exc:
        raise HTTPException(
            status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE, detail=str(exc)
        ) from exc
    except ParserError as exc:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc)
        ) from exc
    except S3DownloadError as exc:
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY, detail=str(exc)
        ) from exc
    except ProviderError as exc:
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY, detail=str(exc)
        ) from exc
