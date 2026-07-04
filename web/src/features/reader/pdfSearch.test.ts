import { describe, expect, it } from 'vitest';
import {
  buildPageIndex,
  findMatches,
  initialActiveIndex,
  normalizeQuery,
} from './pdfSearch';

describe('pdfSearch', () => {
  it('대소문자를 무시하고 매치하며 원본 오프셋을 정확히 되돌린다', () => {
    const idx = buildPageIndex(['Attention Is All You Need']);
    const matches = findMatches(idx, normalizeQuery('ATTENTION'));
    expect(matches).toHaveLength(1);
    expect(matches[0].first).toEqual({ item: 0, char: 0 });
    // 'Attention'의 마지막 글자 n = 원본 코드유닛 8
    expect(matches[0].last).toEqual({ item: 0, char: 8 });
  });

  it('아이템 경계를 넘는 구도 찾는다 (줄 조각으로 쪼개진 pdf.js 아이템)', () => {
    const idx = buildPageIndex(['low rank ', 'adaptation']);
    const matches = findMatches(idx, normalizeQuery('rank adaptation'));
    expect(matches).toHaveLength(1);
    expect(matches[0].first).toEqual({ item: 0, char: 4 }); // 'rank'의 r
    expect(matches[0].last).toEqual({ item: 1, char: 9 }); // 'adaptation'의 마지막 n
  });

  it('공백/줄바꿈 차이를 흡수한다 — "deep learning"이 "deep\\nlearning"에 걸린다', () => {
    const idx = buildPageIndex(['deep\nlearning']);
    expect(findMatches(idx, normalizeQuery('deep learning'))).toHaveLength(1);
    expect(findMatches(idx, normalizeQuery('deeplearning'))).toHaveLength(1);
  });

  it('리가처를 NFKC로 펼쳐 매치한다 (ﬁ → fi), 오프셋은 원본 글자를 가리킨다', () => {
    // 원본: e(0) f(1) ﬁ(2) c(3) i(4) e(5) n(6) t(7)
    const idx = buildPageIndex(['efﬁcient']);
    const matches = findMatches(idx, normalizeQuery('efficient'));
    expect(matches).toHaveLength(1);
    expect(matches[0].first).toEqual({ item: 0, char: 0 });
    expect(matches[0].last).toEqual({ item: 0, char: 7 });
  });

  it('여러 일치를 비중첩으로 모두 찾는다', () => {
    const idx = buildPageIndex(['aaaa']);
    expect(findMatches(idx, 'aa')).toHaveLength(2);
    const idx2 = buildPageIndex(['LoRA는 LoRA다']);
    expect(findMatches(idx2, normalizeQuery('lora'))).toHaveLength(2);
  });

  it('빈 질의는 빈 결과 — 크래시 없이', () => {
    const idx = buildPageIndex(['some text']);
    expect(findMatches(idx, '')).toEqual([]);
    expect(findMatches(buildPageIndex([]), 'abc')).toEqual([]);
  });

  it('한글 검색이 동작한다', () => {
    const idx = buildPageIndex(['저차원 적응 기법은 ', '저차원 행렬로 근사한다']);
    const matches = findMatches(idx, normalizeQuery('저차원'));
    expect(matches).toHaveLength(2);
    expect(matches[1].first).toEqual({ item: 1, char: 0 });
  });

  it('initialActiveIndex — 현재 페이지 이후 첫 매치, 없으면 처음으로 순환', () => {
    expect(initialActiveIndex([1, 3, 5], 2)).toBe(1);
    expect(initialActiveIndex([1, 3, 5], 3)).toBe(1);
    expect(initialActiveIndex([1, 3, 5], 6)).toBe(0);
    expect(initialActiveIndex([4], 1)).toBe(0);
  });
});
