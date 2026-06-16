"""EmbeddingProvider 추상화 + OpenAI 호환 구현체 (명세서 §5.7).

    class EmbeddingProvider(Protocol):
        def embed(self, texts: list[str]) -> list[list[float]]: ...

반환 벡터 차원은 EMBEDDING_DIM(기본 1024)과 일치해야 한다 —
document_chunks.embedding vector(1024)(명세서 §3)에 그대로 저장되므로
불일치는 즉시 ProviderError로 막는다.
"""

from __future__ import annotations

from typing import Protocol, runtime_checkable

import httpx

from app.core.config import Settings, get_settings
from app.providers._http import post_json
from app.providers.errors import ProviderConfigError, ProviderError


@runtime_checkable
class EmbeddingProvider(Protocol):
    """임베딩 provider 계약."""

    def embed(self, texts: list[str]) -> list[list[float]]: ...


class OpenAICompatEmbedding:
    """OpenAI Embeddings 호환 provider({api_base}/embeddings)."""

    def __init__(
        self,
        *,
        api_base: str,
        model: str,
        dim: int,
        api_key: str = "",
        timeout: float = 60.0,
        max_retries: int = 2,
        client: httpx.Client | None = None,
    ) -> None:
        if not api_base:
            raise ProviderConfigError("EMBEDDING/LLM_API_BASE 가 비어 있다.")
        if not model:
            raise ProviderConfigError("EMBEDDING_MODEL 이 비어 있다.")
        if dim <= 0:
            raise ProviderConfigError(f"EMBEDDING_DIM 이 올바르지 않다: {dim}")
        self._model = model
        self._dim = dim
        self._api_key = api_key
        self._max_retries = max_retries
        self._owns_client = client is None
        self._client = client or httpx.Client(
            base_url=api_base.rstrip("/"), timeout=timeout
        )

    def embed(self, texts: list[str]) -> list[list[float]]:
        if not texts:
            return []
        data = post_json(
            self._client,
            "/embeddings",
            {"model": self._model, "input": texts},
            api_key=self._api_key,
            max_retries=self._max_retries,
        )
        try:
            items = sorted(data["data"], key=lambda d: d["index"])
            vectors = [item["embedding"] for item in items]
        except (KeyError, TypeError) as exc:
            raise ProviderError(f"예상치 못한 임베딩 응답: {data!r}") from exc

        if len(vectors) != len(texts):
            raise ProviderError(
                f"임베딩 개수 불일치: 입력 {len(texts)}개, 반환 {len(vectors)}개"
            )
        for vec in vectors:
            if len(vec) != self._dim:
                raise ProviderError(
                    f"임베딩 차원 불일치: EMBEDDING_DIM={self._dim} 이지만 "
                    f"{len(vec)} 차원 반환. 모델/스키마(vector({self._dim}))를 맞춰라."
                )
        return vectors

    def close(self) -> None:
        if self._owns_client:
            self._client.close()


def get_embedding_provider(settings: Settings | None = None) -> EmbeddingProvider:
    """환경변수로 선택된 임베딩 provider를 만든다."""
    cfg = settings or get_settings()
    return OpenAICompatEmbedding(
        api_base=cfg.resolved_embedding_api_base,
        model=cfg.embedding_model,
        dim=cfg.embedding_dim,
        api_key=cfg.resolved_embedding_api_key,
        timeout=cfg.llm_timeout_seconds,
        max_retries=cfg.llm_max_retries,
    )
