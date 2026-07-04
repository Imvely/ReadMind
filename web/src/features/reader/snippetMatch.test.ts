import { describe, expect, it } from 'vitest';
import { findSnippetSpan, foldText } from './snippetMatch';

describe('foldText', () => {
  it('리가처·따옴표·대시·공백 차이를 접는다 (PyMuPDF vs pdf.js 추출 차이)', () => {
    expect(foldText('full ﬁne-tuning')).toBe(foldText('full fine-tuning'));
    expect(foldText('“low–rank” adaptation')).toBe(foldText('"low-rank" adaptation'));
    expect(foldText('self attention')).toBe(foldText('self\nattention'));
  });
});

describe('findSnippetSpan', () => {
  const items = [
    'LoRA: Low-Rank Adaptation of Large Language Models', // 0 제목
    'An important paradigm of natural language processing consists of', // 1
    'large-scale pre-training on general domain data and adaptation', // 2
    'to particular tasks. As we pre-train larger models, full ﬁne-tuning,', // 3 (리가처)
    'which retrains all model parameters, becomes less feasible.', // 4
  ];

  it('snippet과 겹치는 아이템 구간을 찾는다 (여러 아이템에 걸친 경우)', () => {
    const span = findSnippetSpan(
      items,
      'large-scale pre-training on general domain data and adaptation to particular tasks.',
    );
    expect(span).toEqual({ firstItem: 2, lastItem: 3 });
  });

  it('리가처 차이가 있어도 찾는다 (snippet은 fine, 페이지는 ﬁne)', () => {
    const span = findSnippetSpan(items, 'larger models, full fine-tuning, which retrains all');
    expect(span).not.toBeNull();
    expect(span!.firstItem).toBe(3);
  });

  it('말줄임 잘린 snippet도 앞부분으로 찾는다', () => {
    const span = findSnippetSpan(items, 'An important paradigm of natural language processing…');
    expect(span).toEqual({ firstItem: 1, lastItem: 1 });
  });

  it('페이지에 없는 텍스트는 null — 엉뚱한 위치에 하이라이트하지 않는다', () => {
    expect(findSnippetSpan(items, 'transformers process sequences in parallel using self')).toBeNull();
  });

  it('너무 짧은 snippet은 오탐 방지를 위해 null', () => {
    expect(findSnippetSpan(items, 'LoRA')).toBeNull();
  });
});
