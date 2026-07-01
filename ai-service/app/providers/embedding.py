"""EmbeddingProvider 추상화 + OpenAI 호환 구현체 (명세서 §5.7).

    class EmbeddingProvider(Protocol):
        def embed(self, texts: list[str]) -> list[list[float]]: ...

반환 벡터 차원은 EMBEDDING_DIM(기본 1024)과 일치해야 한다 —
document_chunks.embedding vector(1024)(명세서 §3)에 그대로 저장되므로
불일치는 즉시 ProviderError로 막는다.
"""

from __future__ import annotations

import math
from typing import Protocol, runtime_checkable

import httpx

from app.core.config import Settings, get_settings
from app.providers._gemini import build_client, run_with_retry
from app.providers._http import post_json
from app.providers.errors import ProviderConfigError, ProviderError

# Gemini 임베딩 배치 상한(Developer API는 요청당 콘텐츠 수 제한이 있어 나눠 호출).
_GEMINI_EMBED_BATCH = 100


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


class GeminiEmbedding:
    """Google Gemini 임베딩 provider (google-genai SDK 경유).

    gemini-embedding-001 은 output_dimensionality 로 차원을 자를 수 있어 EMBEDDING_DIM
    (=1024)을 그대로 요청한다 → document_chunks.embedding vector(1024) 스키마와 일치.
    3072 미만으로 자르면 L2 정규화가 권장되므로 normalize=True 로 맞춘다(코사인 일관성).
    client 주입 시(.models.embed_content 페이크) 네트워크 없이 동작한다.
    """

    def __init__(
        self,
        *,
        api_key: str,
        model: str,
        dim: int,
        timeout: float = 60.0,
        max_retries: int = 2,
        use_vertex: bool | None = None,
        project: str = "",
        location: str = "global",
        normalize: bool = True,
        client: object | None = None,
    ) -> None:
        if not api_key:
            raise ProviderConfigError("LLM_API_KEY 가 비어 있다(Gemini 임베딩).")
        if not model:
            raise ProviderConfigError("EMBEDDING_MODEL 이 비어 있다(Gemini).")
        if dim <= 0:
            raise ProviderConfigError(f"EMBEDDING_DIM 이 올바르지 않다: {dim}")
        self._model = model
        self._dim = dim
        self._max_retries = max_retries
        self._normalize = normalize
        self._client = client or build_client(
            api_key=api_key,
            timeout=timeout,
            use_vertex=use_vertex,
            project=project,
            location=location,
        )

    def embed(self, texts: list[str]) -> list[list[float]]:
        if not texts:
            return []
        config = {"output_dimensionality": self._dim}
        vectors: list[list[float]] = []
        for start in range(0, len(texts), _GEMINI_EMBED_BATCH):
            batch = texts[start : start + _GEMINI_EMBED_BATCH]
            resp = run_with_retry(
                lambda b=batch: self._client.models.embed_content(  # type: ignore[attr-defined]
                    model=self._model, contents=b, config=config
                ),
                self._max_retries,
            )
            embeddings = getattr(resp, "embeddings", None)
            if embeddings is None:
                raise ProviderError(f"예상치 못한 임베딩 응답: {resp!r}")
            for emb in embeddings:
                values = getattr(emb, "values", None)
                if values is None:
                    raise ProviderError(f"임베딩 항목에 values 가 없다: {emb!r}")
                vectors.append(
                    _l2_normalize(list(values)) if self._normalize else list(values)
                )

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


def _l2_normalize(vec: list[float]) -> list[float]:
    norm = math.sqrt(sum(x * x for x in vec))
    if norm == 0.0:
        return vec
    return [x / norm for x in vec]


def get_embedding_provider(settings: Settings | None = None) -> EmbeddingProvider:
    """환경변수로 선택된 임베딩 provider를 만든다.

    embedding_provider 미설정 시 llm_provider 를 따른다(보통 한 벤더로 통일).
    """
    cfg = settings or get_settings()
    provider = cfg.embedding_provider or cfg.llm_provider
    if provider == "gemini":
        return GeminiEmbedding(
            api_key=cfg.resolved_embedding_api_key,
            model=cfg.embedding_model,
            dim=cfg.embedding_dim,
            timeout=cfg.llm_timeout_seconds,
            max_retries=cfg.llm_max_retries,
            use_vertex=cfg.gemini_vertexai,
            project=cfg.gemini_project,
            location=cfg.gemini_location,
        )
    return OpenAICompatEmbedding(
        api_base=cfg.resolved_embedding_api_base,
        model=cfg.embedding_model,
        dim=cfg.embedding_dim,
        api_key=cfg.resolved_embedding_api_key,
        timeout=cfg.llm_timeout_seconds,
        max_retries=cfg.llm_max_retries,
    )
