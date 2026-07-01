"""번역 서비스 — LLMProvider 경유(벤더 무관, §5.7). 원문대조 응답."""

from __future__ import annotations

from app.providers.llm import LLMProvider
from app.schemas.translate import TranslateRequest, TranslateResponse
from app.translate import prompts
from app.translate.errors import TranslateError

# LLM 호출 비용/토큰 폭주 방지 — 한 번에 번역할 최대 글자 수(초과는 상위에서 분할).
MAX_CHARS = 20_000


def translate(req: TranslateRequest, *, llm: LLMProvider) -> TranslateResponse:
    text = req.text.strip()
    if not text:
        raise TranslateError("번역할 텍스트가 없다.")
    if len(text) > MAX_CHARS:
        raise TranslateError(
            f"번역 요청이 너무 길다({len(text)}자 > {MAX_CHARS}). 선택 범위를 줄여라."
        )

    out = llm.complete(prompts.system(req.target_lang), text, json_mode=False).strip()
    if not out:
        raise TranslateError("번역 결과가 비어 있다.")

    return TranslateResponse(
        translated=out,
        source_excerpt=text,
        target_lang=req.target_lang,
    )
