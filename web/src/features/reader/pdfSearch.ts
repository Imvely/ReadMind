/**
 * PDF 본문 검색 인덱스 (§6.1 web-pdf-textlayer).
 *
 * pdf.js 텍스트 아이템은 줄/조각 단위로 쪼개져 있고 공백·리가처 처리가 사용자가
 * 입력하는 검색어와 다르다. 그래서 페이지 텍스트를 "NFKC + 소문자 + 공백 제거"로
 * 접어 한 줄로 잇고, 접힌 문자마다 (아이템 인덱스, 아이템 내 코드유닛 오프셋)을
 * 기록해 매칭 결과를 다시 화면 좌표(아이템/문자 위치)로 되돌릴 수 있게 한다.
 * 줄바꿈으로 끊긴 구("low\nrank")도 "low rank" 검색에 걸리는 이유가 이 접기다.
 */

/** 오탐·과다 매치 방지 — 정규화 후 이 길이 미만의 질의는 검색하지 않는다. */
export const MIN_QUERY_LENGTH = 2;

/** 접힌 문자 → 원본 위치(아이템 인덱스, 아이템 문자열 내 코드유닛 오프셋). */
export interface CharRef {
  item: number;
  char: number;
}

export interface PageSearchIndex {
  /** 접힌 페이지 전체 텍스트. */
  text: string;
  /** text[i]가 유래한 원본 위치. text와 길이가 같다. */
  map: CharRef[];
}

/** 검색어 정규화 — 인덱스와 같은 접기(NFKC+소문자+공백 제거)를 적용한다. */
export function normalizeQuery(q: string): string {
  return q.normalize('NFKC').toLowerCase().replace(/\s+/gu, '');
}

export function buildPageIndex(itemStrs: string[]): PageSearchIndex {
  let text = '';
  const map: CharRef[] = [];
  itemStrs.forEach((s, item) => {
    let cu = 0; // 원본 코드유닛 오프셋 — 서로게이트 쌍 안전하게 for..of로 전진
    for (const ch of s) {
      // NFKC가 1글자를 여러 글자로 펼칠 수 있다(리가처 ﬁ→fi) — 전부 같은 원본 위치로 매핑.
      for (const c of ch.normalize('NFKC').toLowerCase()) {
        if (!/\s/u.test(c)) {
          text += c;
          map.push({ item, char: cu });
        }
      }
      cu += ch.length;
    }
  });
  return { text, map };
}

export interface PageMatch {
  first: CharRef;
  last: CharRef;
}

/** 페이지 인덱스에서 정규화된 질의의 모든(비중첩) 일치 구간을 찾는다. */
export function findMatches(index: PageSearchIndex, normQuery: string): PageMatch[] {
  const out: PageMatch[] = [];
  if (!normQuery) return out;
  let at = index.text.indexOf(normQuery);
  while (at >= 0) {
    out.push({ first: index.map[at], last: index.map[at + normQuery.length - 1] });
    at = index.text.indexOf(normQuery, at + normQuery.length);
  }
  return out;
}

/** 현재 페이지부터 순방향으로 가장 가까운 매치를 시작점으로 고른다(브라우저 찾기 관례). */
export function initialActiveIndex(matchPages: number[], currentPage: number): number {
  const idx = matchPages.findIndex((p) => p >= currentPage);
  return idx >= 0 ? idx : 0;
}
