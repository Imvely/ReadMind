"""Provider 계층 예외. 에러는 삼키지 않고 명시적으로 올린다(CLAUDE.md §5)."""

from __future__ import annotations


class ProviderError(RuntimeError):
    """LLM/임베딩 provider 호출 실패(네트워크·HTTP·응답 형식 오류)."""


class ProviderConfigError(ProviderError):
    """필수 설정(api_base/model 등) 누락 같은 구성 오류."""
