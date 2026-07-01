import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import type { SummarizeResponse } from '@readmind/shared';
import { ApiClientError } from '@/lib/api';
import SummaryPanel from './SummaryPanel';

const sumState: {
  mutate: ReturnType<typeof vi.fn>;
  isPending: boolean;
  error: Error | null;
  data: SummarizeResponse | undefined;
} = { mutate: vi.fn(), isPending: false, error: null, data: undefined };

vi.mock('@/hooks/ai', () => ({
  useSummarize: () => sumState,
}));

function reset() {
  sumState.mutate = vi.fn();
  sumState.isPending = false;
  sumState.error = null;
  sumState.data = undefined;
}

describe('SummaryPanel', () => {
  beforeEach(reset);

  it('PAPER 요약 결과를 구조(목적/방법/…)와 핵심으로 렌더한다', () => {
    sumState.data = {
      summaryId: 1,
      scope: 'DOCUMENT',
      style: 'PAPER',
      cached: false,
      content: {
        tldr: '한 줄 요약',
        structure: {
          objective: '목적문',
          method: '방법문',
          results: '결과문',
          limitations: '한계문',
          contribution: '기여문',
        },
        keypoints: ['포인트1', '포인트2'],
        glossary: [{ term: '용어A', desc: '설명A' }],
      },
    };
    render(<SummaryPanel documentId={1} />);

    expect(screen.getByText('한 줄 요약')).toBeInTheDocument();
    expect(screen.getByText('목적문')).toBeInTheDocument();
    expect(screen.getByText('기여문')).toBeInTheDocument();
    expect(screen.getByText('포인트1')).toBeInTheDocument();
    expect(screen.getByText('용어A')).toBeInTheDocument();
  });

  it('요약 생성 버튼을 누르면 PAPER 스타일로 mutate한다', () => {
    render(<SummaryPanel documentId={1} />);
    fireEvent.click(screen.getByRole('button', { name: '요약 생성' }));
    expect(sumState.mutate).toHaveBeenCalledWith('PAPER');
  });

  it('캐시된 결과면 캐시됨 배지를 보여준다', () => {
    sumState.data = {
      summaryId: 1,
      scope: 'DOCUMENT',
      style: 'PLAIN',
      cached: true,
      content: { tldr: '요약', keypoints: ['a'] },
    };
    render(<SummaryPanel documentId={1} />);
    expect(screen.getByText('캐시됨')).toBeInTheDocument();
  });

  it('쿼터 초과면 업셀 안내를 보여준다', () => {
    sumState.error = new ApiClientError('QUOTA_EXCEEDED', '한도 초과', 403);
    render(<SummaryPanel documentId={1} />);
    expect(screen.getByText(/무료 요약 사용량을 모두 썼어요/)).toBeInTheDocument();
  });
});
