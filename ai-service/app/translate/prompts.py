"""번역 프롬프트 — 충실 번역(의미 가감 없음), 번역문만 출력."""

from __future__ import annotations

# 흔한 언어 코드 → 사람이 읽는 이름. 없으면 코드를 그대로 쓴다.
_LANG_NAMES = {
    "ko": "한국어",
    "en": "English",
    "ja": "일본어",
    "zh": "중국어",
    "es": "스페인어",
    "fr": "프랑스어",
    "de": "독일어",
}


def lang_name(code: str) -> str:
    return _LANG_NAMES.get(code.strip().lower(), code)


def system(target_lang: str) -> str:
    name = lang_name(target_lang)
    return (
        f"너는 전문 번역가다. 입력 텍스트를 {name}로 정확하게 번역한다.\n"
        "규칙:\n"
        f"- 의미를 더하거나 빼지 말고 원문에 충실하게 {name}로만 번역한다.\n"
        "- 설명·머리말·따옴표·주석 없이 번역문 텍스트만 출력한다.\n"
        "- 코드·수식·고유명사·인용은 의미가 유지되도록 자연스럽게 옮긴다."
    )
