"""FastAPI 의존성 — 실제 어댑터를 조립한다. 테스트는 app.dependency_overrides로 교체.

서비스 토큰 검증: AI_SERVICE_TOKEN이 설정돼 있으면 X-Service-Token 헤더를 강제한다
(명세서 §5: 내부 전용, 서비스 토큰 호출). 비어 있으면 로컬 dev로 보고 통과.
"""

from __future__ import annotations

from typing import Annotated

from fastapi import Depends, Header, HTTPException, status

from app.core.config import Settings, get_settings
from app.parse.ports import ChunkReader, ChunkRepository, Storage
from app.providers.embedding import EmbeddingProvider, get_embedding_provider
from app.providers.llm import LLMProvider, get_llm_provider
from app.repositories.chunks_pg import PgChunkRepository
from app.storage.s3 import S3Storage

SettingsDep = Annotated[Settings, Depends(get_settings)]


def verify_service_token(
    settings: SettingsDep,
    x_service_token: Annotated[str | None, Header()] = None,
) -> None:
    expected = settings.ai_service_token
    if not expected:
        return  # dev: 인증 비활성
    if x_service_token != expected:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="invalid service token",
        )


def get_storage(settings: SettingsDep) -> Storage:
    return S3Storage(
        endpoint_url=settings.s3_endpoint,
        bucket=settings.s3_bucket,
        access_key=settings.s3_key,
        secret_key=settings.s3_secret,
        region=settings.s3_region,
    )


def get_chunk_repository(settings: SettingsDep) -> ChunkRepository:
    return PgChunkRepository(settings.postgres_url)


def get_chunk_reader(settings: SettingsDep) -> ChunkReader:
    return PgChunkRepository(settings.postgres_url)


def get_embedder(settings: SettingsDep) -> EmbeddingProvider:
    return get_embedding_provider(settings)


def get_llm(settings: SettingsDep) -> LLMProvider:
    return get_llm_provider(settings)
