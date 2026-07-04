import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import type { QaHistoryResponse } from '@readmind/shared';
import { ApiClientError } from '@/lib/api';
import QaPanel from './QaPanel';

// useAsk(mutation)·useQaHistoryQuery(이력)를 테스트가 제어할 수 있게 모킹한다.
const askState: {
  mutate: ReturnType<typeof vi.fn>;
  isPending: boolean;
  error: Error | null;
  variables: { sessionId: number | null; question: string } | undefined;
} = { mutate: vi.fn(), isPending: false, error: null, variables: undefined };

const historyState: {
  isPending: boolean;
  data: QaHistoryResponse | undefined;
} = { isPending: false, data: undefined };

vi.mock('@/hooks/ai', () => ({
  useAsk: () => askState,
  useQaHistoryQuery: () => historyState,
}));

function reset() {
  askState.mutate = vi.fn();
  askState.isPending = false;
  askState.error = null;
  askState.variables = undefined;
  historyState.isPending = false;
  historyState.data = { sessionId: null, messages: [] };
}

describe('QaPanel', () => {
  beforeEach(reset);

  it('서버 이력을 렌더하고, 근거 클릭 시 페이지+snippet으로 점프한다(기록 유지 UX)', () => {
    historyState.data = {
      sessionId: 7,
      messages: [
        { role: 'USER', content: '어떤 구조인가요?' },
        {
          role: 'ASSISTANT',
          content: '트랜스포머는 self-attention을 사용합니다.',
          sources: [
            { page: 3, snippet: 'self-attention 설명…' },
            { page: 5, snippet: '실험 결과…' },
          ],
        },
      ],
    };

    const onJump = vi.fn();
    render(<QaPanel documentId={1} onJumpToPage={onJump} />);

    // 이력이 그대로 복원된다(질문+답변).
    expect(screen.getByText(/어떤 구조인가요\?/)).toBeInTheDocument();
    expect(screen.getByText('트랜스포머는 self-attention을 사용합니다.')).toBeInTheDocument();

    // 근거 클릭 → 페이지 점프 + 본문 임시 하이라이트용 snippet 전달.
    fireEvent.click(screen.getByRole('button', { name: 'p.5' }));
    expect(onJump).toHaveBeenCalledWith(5, '실험 결과…');
  });

  it('질문 전송 시 세션 id와 질문으로 mutate한다', () => {
    historyState.data = { sessionId: 7, messages: [] };
    render(<QaPanel documentId={1} onJumpToPage={vi.fn()} />);

    fireEvent.change(screen.getByPlaceholderText('질문 입력…'), {
      target: { value: '후속 질문' },
    });
    fireEvent.click(screen.getByRole('button', { name: '질문' }));
    expect(askState.mutate).toHaveBeenCalledWith({ sessionId: 7, question: '후속 질문' });
  });

  it('처리 중이면 보낸 질문과 동적 생각중 인디케이터를 보여준다', () => {
    askState.isPending = true;
    askState.variables = { sessionId: null, question: '이건 무슨 뜻이야?' };
    render(<QaPanel documentId={1} onJumpToPage={vi.fn()} />);

    expect(screen.getByText(/이건 무슨 뜻이야\?/)).toBeInTheDocument();
    expect(screen.getByRole('status')).toBeInTheDocument();
    expect(screen.getByText(/문서에서 관련 구절을 찾는 중/)).toBeInTheDocument();
    // 처리 중엔 정적 안내 문구가 뜨지 않는다(베타 피드백).
    expect(screen.queryByText(/무엇이든 물어보세요/)).not.toBeInTheDocument();
  });

  it('page가 null인 근거는 점프 불가(비활성)로 표시된다', () => {
    historyState.data = {
      sessionId: 1,
      messages: [
        { role: 'ASSISTANT', content: '답변', sources: [{ page: null, snippet: '페이지 불명 근거' }] },
      ],
    };
    const onJump = vi.fn();
    render(<QaPanel documentId={1} onJumpToPage={onJump} />);

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
    expect(screen.getByRole('button', { name: '질문' })).toBeDisabled();
  });
});
