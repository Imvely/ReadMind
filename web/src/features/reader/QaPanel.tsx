import { useEffect, useRef, useState } from 'react';
import type { QaHistoryMessageDto, QaSourceDto } from '@readmind/shared';
import { ApiClientError } from '@/lib/api';
import { useAsk, useQaHistoryQuery } from '@/hooks/ai';

/**
 * Q&A 패널 — 대화는 서버(qa_sessions/qa_messages)가 SSOT.
 * 이력 쿼리로 복원하므로 탭 전환·서재 재진입·재로그인에도 기록이 유지된다(§4.4 qa/history).
 */
export default function QaPanel({
  documentId,
  onJumpToPage,
}: {
  documentId: number;
  onJumpToPage: (page: number, snippet?: string) => void;
}) {
  const history = useQaHistoryQuery(documentId, true);
  const ask = useAsk(documentId);
  const [question, setQuestion] = useState('');
  const bottomRef = useRef<HTMLDivElement>(null);

  const messages = history.data?.messages ?? [];
  const sessionId = history.data?.sessionId ?? null;
  const quotaExceeded = ask.error instanceof ApiClientError && ask.error.code === 'QUOTA_EXCEEDED';
  // 전송 직후 질문을 즉시 보여주고(낙관적), 그 아래 '생각 중' 표시를 붙인다.
  const pendingQuestion = ask.isPending ? ask.variables?.question : undefined;

  useEffect(() => {
    // 옵셔널 호출: jsdom(테스트 환경)엔 scrollIntoView가 없다.
    bottomRef.current?.scrollIntoView?.({ behavior: 'smooth' });
  }, [messages.length, pendingQuestion]);

  function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    const q = question.trim();
    if (!q || ask.isPending) return;
    setQuestion('');
    ask.mutate({ sessionId, question: q });
  }

  return (
    <div className="flex h-full flex-col">
      <div className="flex-1 space-y-4 overflow-auto">
        {history.isPending && (
          <p className="text-sm text-slate-400">대화 기록 불러오는 중…</p>
        )}
        {!history.isPending && messages.length === 0 && !pendingQuestion && (
          <p className="text-sm text-slate-400">
            문서 내용에 대해 무엇이든 물어보세요. 답변엔 근거가 함께 표시됩니다.
          </p>
        )}

        {messages.map((m, i) => (
          <Message key={i} message={m} onJumpToPage={onJumpToPage} />
        ))}

        {pendingQuestion && (
          <div className="space-y-2">
            <p className="text-sm font-medium text-slate-900">Q. {pendingQuestion}</p>
            <ThinkingIndicator />
          </div>
        )}
        <div ref={bottomRef} />
      </div>

      {quotaExceeded && (
        <p className="mb-2 rounded-lg bg-amber-50 p-3 text-sm text-amber-800">
          무료 질문 사용량을 모두 썼어요. 업그레이드하면 계속 질문할 수 있습니다.
        </p>
      )}
      {ask.error && !quotaExceeded && (
        <p className="mb-2 text-sm text-red-600">{ask.error.message}</p>
      )}

      <form onSubmit={onSubmit} className="mt-3 flex gap-2 border-t border-slate-200 pt-3">
        <input
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          placeholder="질문 입력…"
          className="flex-1 rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none"
        />
        <button
          type="submit"
          disabled={ask.isPending || !question.trim()}
          className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700 disabled:opacity-50"
        >
          {ask.isPending ? '…' : '질문'}
        </button>
      </form>
    </div>
  );
}

function Message({
  message,
  onJumpToPage,
}: {
  message: QaHistoryMessageDto;
  onJumpToPage: (page: number, snippet?: string) => void;
}) {
  if (message.role === 'USER') {
    return <p className="text-sm font-medium text-slate-900">Q. {message.content}</p>;
  }
  const sources: QaSourceDto[] = message.sources ?? [];
  return (
    <div className="space-y-2">
      <p className="whitespace-pre-wrap text-sm leading-relaxed text-slate-800">{message.content}</p>
      {sources.length > 0 && (
        <div className="flex flex-wrap gap-1.5">
          {sources.map((s, j) => (
            <button
              key={j}
              title={s.snippet}
              onClick={() => s.page != null && onJumpToPage(s.page, s.snippet)}
              disabled={s.page == null}
              className="rounded border border-slate-200 bg-slate-50 px-2 py-0.5 text-xs text-slate-600 hover:border-amber-400 hover:text-amber-700 disabled:cursor-default disabled:opacity-50"
            >
              {s.page != null ? `p.${s.page}` : '근거'}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

/** RAG 처리 단계를 순환 표시하는 '생각 중' 인디케이터(§6.1 베타 피드백 — 정적 빈화면 대체). */
const THINKING_STEPS = [
  '문서에서 관련 구절을 찾는 중',
  '근거를 대조하는 중',
  '답변을 작성하는 중',
];

function ThinkingIndicator() {
  const [step, setStep] = useState(0);
  useEffect(() => {
    // 마지막 단계에 도달하면 유지 — 실제 완료 시점은 응답 도착이 결정한다.
    const timer = window.setInterval(
      () => setStep((s) => Math.min(s + 1, THINKING_STEPS.length - 1)),
      2200,
    );
    return () => window.clearInterval(timer);
  }, []);

  return (
    <div className="flex items-center gap-2 rounded-lg bg-slate-50 px-3 py-2.5" role="status">
      <span className="inline-block h-3.5 w-3.5 shrink-0 animate-spin rounded-full border-2 border-slate-300 border-t-amber-500" />
      <span className="text-sm text-slate-500">
        {THINKING_STEPS[step]}
        <AnimatedDots />
      </span>
    </div>
  );
}

function AnimatedDots() {
  return (
    <span aria-hidden>
      <span className="animate-bounce [animation-delay:0ms]">.</span>
      <span className="animate-bounce [animation-delay:150ms]">.</span>
      <span className="animate-bounce [animation-delay:300ms]">.</span>
    </span>
  );
}
