"""LLM JSON 출력 파싱. 코드펜스/잡텍스트를 견디고 첫 JSON 객체만 뽑는다."""

from __future__ import annotations

import json
from typing import Any

from app.summarize.errors import SummarizeError


def parse_json_object(raw: str) -> dict[str, Any]:
    text = raw.strip()
    # ```json ... ``` 펜스 제거.
    if text.startswith("```"):
        text = text.split("```", 2)[1] if text.count("```") >= 2 else text
        if text.startswith("json"):
            text = text[len("json") :]
        text = text.strip()
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        # 앞뒤 잡텍스트가 있으면 첫 '{' ~ 마지막 '}' 구간만 시도.
        start, end = text.find("{"), text.rfind("}")
        if start != -1 and end > start:
            try:
                return json.loads(text[start : end + 1])
            except json.JSONDecodeError as exc:
                raise SummarizeError(f"LLM JSON 파싱 실패: {exc!r}") from exc
        raise SummarizeError("LLM 응답에서 JSON 객체를 찾지 못했다.") from None
