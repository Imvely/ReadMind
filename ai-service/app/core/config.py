"""AI 서비스 설정 — 모든 환경변수는 여기서 pydantic으로 검증해 읽는다.

명세서 §10 키와 1:1 대응. 특정 벤더에 코드를 묶지 않기 위해(CLAUDE.md §10)
LLM 접근은 LLM_API_BASE(엔드포인트) + LLM_MODEL 조합으로만 표현한다.
상용 API ↔ 자체 GPU(vLLM) 교체는 .env 값 변경만으로 가능하다.
"""

from __future__ import annotations

from functools import lru_cache
from typing import Literal

from pydantic_settings import BaseSettings, SettingsConfigDict

# 기본 임베딩 차원. document_chunks.embedding vector(1024)(명세서 §3)와 일치.
DEFAULT_EMBEDDING_DIM = 1024


class Settings(BaseSettings):
    """프로세스 환경변수에서 주입되는 설정값."""

    model_config = SettingsConfigDict(
        env_file=None,  # 값 주입은 컨테이너/셸 환경변수로 한다(.env는 compose가 로드).
        extra="ignore",
        case_sensitive=False,
    )

    # ── LLM (provider 추상화) ──
    llm_provider: Literal["commercial", "selfhosted"] = "commercial"
    llm_model: str = ""
    llm_api_base: str = ""
    llm_api_key: str = ""

    # ── 임베딩 ──
    embedding_model: str = ""
    embedding_api_base: str = ""  # 미설정 시 llm_api_base 재사용
    embedding_api_key: str = ""  # 미설정 시 llm_api_key 재사용
    embedding_dim: int = DEFAULT_EMBEDDING_DIM

    # ── 외부 호출 정책 ──
    llm_timeout_seconds: float = 60.0
    llm_max_retries: int = 2

    # ── 서비스 간 인증 / 인프라 (명세서 §10) ──
    ai_service_token: str = ""  # 비면 인증 비활성(로컬 dev). 운영은 반드시 설정.
    postgres_url: str = ""  # document_chunks 적재용 DSN
    s3_endpoint: str = ""
    s3_bucket: str = ""
    s3_key: str = ""
    s3_secret: str = ""
    s3_region: str = "us-east-1"

    @property
    def resolved_embedding_api_base(self) -> str:
        return self.embedding_api_base or self.llm_api_base

    @property
    def resolved_embedding_api_key(self) -> str:
        return self.embedding_api_key or self.llm_api_key


@lru_cache
def get_settings() -> Settings:
    """설정 싱글턴. 테스트에서는 get_settings.cache_clear() 후 재호출."""
    return Settings()
