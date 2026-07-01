import { useState } from 'react';
import type { QaSourceDto } from '@readmind/shared';
import { ApiClientError } from '@/lib/api';
import { useAsk } from '@/hooks/ai';

interface Turn {
  question: string;
  answer: string;
  sources: QaSourceDto[];
}

export default function QaPanel({
  documentId,
  onJumpToPage,
}: {
  documentId: number;
  onJumpToPage: (page: number) => void;
}) {
  const ask = useAsk(documentId);
  const [sessionId, setSessionId] = useState<number | null>(null);
  const [question, setQuestion] = useState('');
  const [turns, setTurns] = useState<Turn[]>([]);

  const quotaExceeded = ask.error instanceof ApiClientError && ask.error.code === 'QUOTA_EXCEEDED';

  function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    const q = question.trim();
    if (!q || ask.isPending) return;
    ask.mutate(
      { sessionId, question: q },
      {
        onSuccess: (res) => {
          setSessionId(res.sessionId);
          setTurns((prev) => [...prev, { question: q, answer: res.answer, sources: res.sources }]);
          setQuestion('');
        },
      },
    );
  }

  return (
    <div className="flex h-full flex-col">
      <div className="flex-1 space-y-4 overflow-auto">
        {turns.length === 0 && (
          <p className="text-sm text-slate-400">문서 내용에 대해 무엇이든 물어보세요. 답변엔 근거가 함께 표시됩니다.</p>
        )}
        {turns.map((turn, i) => (
          <div key={i} className="space-y-2">
            <p className="text-sm font-medium text-slate-900">Q. {turn.question}</p>
            <p className="whitespace-pre-wrap text-sm leading-relaxed text-slate-800">{turn.answer}</p>
            {turn.sources.length > 0 && (
              <div className="flex flex-wrap gap-1.5">
                {turn.sources.map((s, j) => (
                  <button
                    key={j}
                    title={s.snippet}
                    onClick={() => s.page != null && onJumpToPage(s.page)}
                    disabled={s.page == null}
                    className="rounded border border-slate-200 bg-slate-50 px-2 py-0.5 text-xs text-slate-600 hover:border-amber-400 hover:text-amber-700 disabled:cursor-default disabled:opacity-50"
                  >
                    {s.page != null ? `p.${s.page}` : '근거'}
                  </button>
                ))}
              </div>
            )}
          </div>
        ))}
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
