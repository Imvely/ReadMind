import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import type { SummarizeResponse } from '@readmind/shared';
import { ApiClientError } from '@/lib/api';
import SummaryPanel from './SummaryPanel';

// useSummaryQuery(자동 로드 쿼리)를 테스트가 제어할 수 있게 모킹한다.
const sumState: {
  isPending: boolean;
  error: Error | null;
  data: SummarizeResponse | undefined;
  refetch: ReturnType<typeof vi.fn>;
} = { isPending: false, error: null, data: undefined, refetch: vi.fn() };

vi.mock('@/hooks/ai', () => ({
  useSummaryQuery: () => sumState,
}));

function reset() {
  sumState.isPending = false;
  sumState.error = null;
  sumState.data = undefined;
  sumState.refetch = vi.fn();
}

describe('SummaryPanel', () => {
  beforeEach(reset);

  it('자동 로드된 PAPER 요약을 구조(목적/방법/…)와 핵심으로 렌더한다 — 버튼 없이', () => {
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
    // 자동 로드 UX — 수동 생성 버튼은 존재하지 않는다.
    expect(screen.queryByRole('button', { name: '요약 생성' })).not.toBeInTheDocument();
  });

  it('생성 중이면 동적 로딩(스켈레톤+진행 문구)을 보여준다', () => {
    sumState.isPending = true;
    render(<SummaryPanel documentId={1} />);
    expect(screen.getByText(/AI가 문서를 요약하고 있어요/)).toBeInTheDocument();
    expect(screen.getByRole('status', { name: '요약 생성 중' })).toBeInTheDocument();
  });

  it('저장된(캐시) 결과면 저장된 요약 배지를 보여준다', () => {
    sumState.data = {
      summaryId: 1,
      scope: 'DOCUMENT',
      style: 'PLAIN',
      cached: true,
      content: { tldr: '요약', keypoints: ['a'] },
    };
    render(<SummaryPanel documentId={1} />);
    expect(screen.getByText('저장된 요약')).toBeInTheDocument();
  });

  it('쿼터 초과면 업셀 안내를 보여준다', () => {
    sumState.error = new ApiClientError('QUOTA_EXCEEDED', '한도 초과', 403);
    render(<SummaryPanel documentId={1} />);
    expect(screen.getByText(/무료 요약 사용량을 모두 썼어요/)).toBeInTheDocument();
  });

  it('일반 오류면 다시 시도 버튼으로 refetch한다', () => {
    sumState.error = new Error('네트워크 오류');
    render(<SummaryPanel documentId={1} />);
    fireEvent.click(screen.getByRole('button', { name: '다시 시도' }));
    expect(sumState.refetch).toHaveBeenCalled();
  });
});
