"""Provider 추상화 (명세서 §5.7).

LLM/임베딩 접근을 Protocol로 추상화하고, OpenAI 호환 HTTP 구현체 1개로
상용 API와 자체 호스팅(vLLM)을 모두 커버한다. 벤더를 코드에 직접 결합하지
않는다(CLAUDE.md §10) — 교체는 .env(LLM_API_BASE/LLM_MODEL) 변경만으로.
"""

from app.providers.embedding import (
    EmbeddingProvider,
    OpenAICompatEmbedding,
    get_embedding_provider,
)
from app.providers.errors import ProviderConfigError, ProviderError
from app.providers.llm import LLMProvider, OpenAICompatLLM, get_llm_provider

__all__ = [
    "EmbeddingProvider",
    "LLMProvider",
    "OpenAICompatEmbedding",
    "OpenAICompatLLM",
    "ProviderConfigError",
    "ProviderError",
    "get_embedding_provider",
    "get_llm_provider",
]
