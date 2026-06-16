"""OpenAI 호환 HTTP 호출 공통 로직 (재시도·타임아웃).

LLM/임베딩 provider가 공유한다. 외부 호출은 재시도·타임아웃을 둔다(CLAUDE.md §Python).
"""

from __future__ import annotations

from typing import Any

import httpx

from app.providers.errors import ProviderError

# 일시적 장애로 보고 재시도할 HTTP 상태(429 + 5xx).
_RETRYABLE_STATUS = {429, 500, 502, 503, 504}


def post_json(
    client: httpx.Client,
    path: str,
    payload: dict[str, Any],
    *,
    api_key: str,
    max_retries: int,
) -> dict[str, Any]:
    """OpenAI 호환 엔드포인트에 POST하고 JSON 본문을 돌려준다.

    재시도는 네트워크 오류와 일시적 상태코드(429/5xx)에만 적용한다.
    4xx(인증·요청 오류)는 즉시 ProviderError로 올린다(삼키지 않음).
    """
    headers = {"Content-Type": "application/json"}
    if api_key:
        headers["Authorization"] = f"Bearer {api_key}"

    last_exc: Exception | None = None
    # 첫 시도 + max_retries회 재시도.
    for attempt in range(max_retries + 1):
        try:
            resp = client.post(path, json=payload, headers=headers)
        except httpx.HTTPError as exc:  # 연결/타임아웃 등 전송 계층 오류
            last_exc = exc
            if attempt < max_retries:
                continue
            raise ProviderError(f"요청 전송 실패: {exc!r}") from exc

        if resp.status_code in _RETRYABLE_STATUS and attempt < max_retries:
            continue
        if resp.status_code >= 400:
            raise ProviderError(
                f"provider HTTP {resp.status_code}: {resp.text[:300]}"
            )

        try:
            return resp.json()
        except ValueError as exc:  # JSON 파싱 실패
            raise ProviderError(f"응답 JSON 파싱 실패: {exc!r}") from exc

    # 재시도 소진(네트워크 오류 반복) — 위 raise로 빠지지 않은 경우 방어적으로.
    raise ProviderError(f"재시도 소진: {last_exc!r}")
