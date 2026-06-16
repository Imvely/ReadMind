"""요약 프롬프트 (명세서 §5.3). 스타일별 system/user 빌더 + map-reduce 프롬프트.

LLM 벤더에 묶지 않는다 — 프롬프트 문자열만 만들고 호출은 LLMProvider가 한다.
출력 언어는 원문 언어를 따르도록 지시(요약은 사용자 언어 유지가 자연스럽다).
"""

from __future__ import annotations

# 최종 JSON을 만들 때 LLM에 보여줄 스키마 설명.
_PAPER_SCHEMA = (
    '{"tldr": str, '
    '"structure": {"objective": str, "method": str, "results": str, '
    '"limitations": str, "contribution": str}, '
    '"keypoints": [str, ...], '
    '"glossary": [{"term": str, "desc": str}, ...]}'
)
_PLAIN_SCHEMA = '{"tldr": str, "keypoints": [str, ...]}'

_PAPER_SYSTEM = (
    "너는 학술 논문을 읽는 연구자를 돕는 요약기다. "
    "주어진 본문에만 근거해 요약한다(없는 내용을 지어내지 않는다). "
    "반드시 아래 JSON 스키마와 정확히 일치하는 JSON 객체 하나만 출력한다. "
    "코드펜스/설명/추가 텍스트 없이 JSON만. 값의 언어는 본문 언어를 따른다.\n"
    f"스키마: {_PAPER_SCHEMA}\n"
    "structure의 다섯 필드는 각각 연구 목적/방법/결과/한계/기여를 1~3문장으로. "
    "keypoints는 핵심 요점 3~7개. glossary는 핵심 용어와 짧은 설명(없으면 빈 배열)."
)

_PLAIN_SYSTEM = (
    "너는 문서를 쉽게 요약하는 요약기다. 본문에만 근거한다. "
    "아래 JSON 스키마와 정확히 일치하는 JSON 객체 하나만 출력한다(코드펜스 없이).\n"
    f"스키마: {_PLAIN_SCHEMA}\n"
    "tldr는 2~4문장, keypoints는 핵심 요점 3~7개."
)

_MAP_SYSTEM = (
    "너는 긴 문서의 한 부분을 요약하는 보조기다. 주어진 발췌에만 근거해 "
    "핵심 사실/주장/수치를 불릿로 간결히 정리한다. 추측 금지. 평문으로 출력한다."
)


def paper_system() -> str:
    return _PAPER_SYSTEM


def plain_system() -> str:
    return _PLAIN_SYSTEM


def user_payload(text: str) -> str:
    return f"본문:\n{text}"


def map_system() -> str:
    return _MAP_SYSTEM


def map_user(text: str) -> str:
    return f"다음 발췌를 불릿로 요약하라:\n{text}"


def reduce_user(notes: str) -> str:
    return (
        "다음은 한 문서의 부분 요약 노트들이다. 이를 종합해 전체 문서 요약을 "
        f"스키마에 맞춰 JSON으로 출력하라.\n부분 요약:\n{notes}"
    )
