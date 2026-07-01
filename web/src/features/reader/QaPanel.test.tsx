import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import type { QaResponse } from '@readmind/shared';
import { ApiClientError } from '@/lib/api';
import QaPanel from './QaPanel';

// useAsk(mutation)를 테스트가 제어할 수 있게 모킹한다.
const askState: {
  mutate: ReturnType<typeof vi.fn>;
  isPending: boolean;
  error: Error | null;
} = { mutate: vi.fn(), isPending: false, error: null };

vi.mock('@/hooks/ai', () => ({
  useAsk: () => askState,
}));

function resetAsk() {
  askState.mutate = vi.fn();
  askState.isPending = false;
  askState.error = null;
}

describe('QaPanel', () => {
  beforeEach(resetAsk);

  it('질문을 보내면 답변과 근거 버튼을 렌더하고, 근거 클릭 시 해당 페이지로 점프한다', () => {
    const response: QaResponse = {
      sessionId: 7,
      answer: '트랜스포머는 self-attention을 사용합니다.',
      sources: [
        { page: 3, snippet: 'self-attention 설명…' },
        { page: 5, snippet: '실험 결과…' },
      ],
    };
    // mutate가 즉시 onSuccess를 호출하도록.
    askState.mutate = vi.fn((_vars, opts) => opts?.onSuccess?.(response));

    const onJump = vi.fn();
    render(<QaPanel documentId={1} onJumpToPage={onJump} />);

    fireEvent.change(screen.getByPlaceholderText('질문 입력…'), {
      target: { value: '어떤 구조인가요?' },
    });
    fireEvent.click(screen.getByRole('button', { name: '질문' }));

    // 답변이 렌더된다.
    expect(screen.getByText('트랜스포머는 self-attention을 사용합니다.')).toBeInTheDocument();

    // 근거(sources)가 페이지 칩으로 표시된다.
    const p3 = screen.getByRole('button', { name: 'p.3' });
    const p5 = screen.getByRole('button', { name: 'p.5' });
    expect(p3).toBeInTheDocument();
    expect(p5).toBeInTheDocument();

    // 근거 클릭 → 본문 위치 점프(핵심 요구).
    fireEvent.click(p5);
    expect(onJump).toHaveBeenCalledWith(5);
  });

  it('page가 null인 근거는 점프 불가(비활성)로 표시된다', () => {
    const response: QaResponse = {
      sessionId: 1,
      answer: '답변',
      sources: [{ page: null, snippet: '페이지 불명 근거' }],
    };
    askState.mutate = vi.fn((_vars, opts) => opts?.onSuccess?.(response));

    const onJump = vi.fn();
    render(<QaPanel documentId={1} onJumpToPage={onJump} />);
    fireEvent.change(screen.getByPlaceholderText('질문 입력…'), { target: { value: 'q' } });
    fireEvent.click(screen.getByRole('button', { name: '질문' }));

    const chip = screen.getByRole('button', { name: '근거' });
    expect(chip).toBeDisabled();
    fireEvent.click(chip);
    expect(onJump).not.toHaveBeenCalled();
  });

  it('쿼터 초과면 업셀 안내를 보여준다', () => {
    askState.error = new ApiClientError('QUOTA_EXCEEDED', '한도 초과', 403);
    render(<QaPanel documentId={1} onJumpToPage={vi.fn()} />);
    expect(screen.getByText(/무료 질문 사용량을 모두 썼어요/)).toBeInTheDocument();
  });

  it('빈 질문은 전송하지 않는다', () => {
    render(<QaPanel documentId={1} onJumpToPage={vi.fn()} />);
    // 입력이 비어 있으면 전송 버튼은 비활성.
    expect(screen.getByRole('button', { name: '질문' })).toBeDisabled();
  });
});
