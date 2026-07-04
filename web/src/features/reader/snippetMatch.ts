/**
 * Q&A 근거 snippet ↔ pdf.js 텍스트 아이템 매칭 (§6.1 근거 플래시 하이라이트).
 *
 * 문제: AI 파서(PyMuPDF)와 pdf.js는 같은 PDF에서 미묘하게 다른 텍스트를 뽑는다 —
 * 리가처(ﬁ→fi), 스마트 따옴표, 대시, 공백/줄바꿈 차이. 원문 그대로 비교하면
 * 못 찾거나 엉뚱한 위치에 걸린다. 그래서 양쪽을 동일한 형태로 '접은(fold)' 뒤 비교한다.
 */

/** 유니코드 정규화 + 따옴표/대시 통일 + 영숫자·한글만 남김(공백·구두점 제거). */
export function foldText(s: string): string {
  return s
    .normalize('NFKC') // 리가처 ﬁ→fi 등 호환 분해
    .replace(/[‘’ʼ]/g, "'")
    .replace(/[“”]/g, '"')
    .replace(/[‐-―−]/g, '-')
    .toLowerCase()
    .replace(/[^a-z0-9가-힣]/gu, '');
}

/** 오탐 방지 최소 매칭 길이 — 이보다 짧은 일치는 우연일 가능성이 높아 버린다. */
const MIN_MATCH = 16;
/** 점진적 축소 후보 길이 — 긴 일치를 우선하고, 안 되면 앞부분만이라도 찾는다. */
const CANDIDATE_LENGTHS = [Infinity, 96, 64, 40, 24];

export interface SnippetSpan {
  /** 매칭에 걸린 첫/마지막 아이템 인덱스(양끝 포함). */
  firstItem: number;
  lastItem: number;
}

/**
 * 페이지 텍스트 아이템 문자열 배열에서 snippet과 일치하는 아이템 구간을 찾는다.
 * 못 찾으면 null — 엉뚱한 위치에 하이라이트하는 것보다 안 띄우는 게 낫다.
 */
export function findSnippetSpan(itemStrs: string[], snippet: string): SnippetSpan | null {
  const folded = itemStrs.map(foldText);
  const bounds: { start: number; end: number }[] = [];
  let joined = '';
  for (const s of folded) {
    bounds.push({ start: joined.length, end: joined.length + s.length });
    joined += s;
  }

  const target = foldText(snippet.replace(/[…]+$|\.{3,}$/u, ''));
  if (target.length < MIN_MATCH) return null;

  for (const len of CANDIDATE_LENGTHS) {
    const cand = target.slice(0, Math.min(len, target.length));
    if (cand.length < MIN_MATCH) break;
    const at = joined.indexOf(cand);
    if (at < 0) continue;
    const end = at + cand.length;
    let first = -1;
    let last = -1;
    bounds.forEach((b, i) => {
      if (b.end <= at || b.start >= end || b.start === b.end) return;
      if (first < 0) first = i;
      last = i;
    });
    if (first >= 0) return { firstItem: first, lastItem: last };
  }
  return null;
}
