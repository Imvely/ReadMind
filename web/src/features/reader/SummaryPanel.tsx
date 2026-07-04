import type { PaperSummaryContent, PlainSummaryContent } from '@readmind/shared';
import { ApiClientError } from '@/lib/api';
import { useSummaryQuery } from '@/hooks/ai';

function isPaper(content: unknown): content is PaperSummaryContent {
  return typeof content === 'object' && content !== null && 'structure' in content;
}
function isPlain(content: unknown): content is PlainSummaryContent {
  return typeof content === 'object' && content !== null && 'keypoints' in content && !('structure' in content);
}

/**
 * 요약 패널 — 리더 진입 시 자동 로드(버튼 없음). 서버 캐시가 SSOT라
 * 탭 전환·재방문·재로그인에도 같은 요약이 즉시 복원된다(캐시 히트=쿼터 미차감).
 */
export default function SummaryPanel({ documentId }: { documentId: number }) {
  const summary = useSummaryQuery(documentId, true);
  const result = summary.data;
  const quotaExceeded =
    summary.error instanceof ApiClientError && summary.error.code === 'QUOTA_EXCEEDED';

  return (
    <div className="space-y-4">
      {summary.isPending && <SummarySkeleton />}

      {quotaExceeded && (
        <p className="rounded-lg bg-amber-50 p-3 text-sm text-amber-800">
          무료 요약 사용량을 모두 썼어요. 업그레이드하면 계속 요약할 수 있습니다.
        </p>
      )}
      {summary.error && !quotaExceeded && (
        <div className="space-y-2">
          <p className="text-sm text-red-600">{summary.error.message}</p>
          <button
            onClick={() => void summary.refetch()}
            className="rounded-lg bg-slate-900 px-3 py-1.5 text-sm font-medium text-white hover:bg-slate-700"
          >
            다시 시도
          </button>
        </div>
      )}

      {result && (
        <>
          {result.cached && <span className="text-xs text-slate-400">저장된 요약</span>}
          {isPaper(result.content) && <PaperView content={result.content} />}
          {isPlain(result.content) && (
            <div className="space-y-3">
              <p className="text-sm leading-relaxed text-slate-800">{result.content.tldr}</p>
              <Keypoints points={result.content.keypoints} />
            </div>
          )}
        </>
      )}
    </div>
  );
}

/** 생성 중 동적 표시 — 진행 문구 + 문단 스켈레톤(§6.1 베타 피드백). */
function SummarySkeleton() {
  return (
    <div className="space-y-3" role="status" aria-label="요약 생성 중">
      <p className="flex items-center gap-2 text-sm text-slate-500">
        <span className="inline-block h-3.5 w-3.5 animate-spin rounded-full border-2 border-slate-300 border-t-slate-600" />
        AI가 문서를 요약하고 있어요…
      </p>
      <div className="animate-pulse space-y-2">
        <div className="h-3 w-11/12 rounded bg-slate-200" />
        <div className="h-3 w-full rounded bg-slate-200" />
        <div className="h-3 w-4/5 rounded bg-slate-200" />
        <div className="mt-4 h-3 w-1/3 rounded bg-slate-200" />
        <div className="h-3 w-10/12 rounded bg-slate-200" />
        <div className="h-3 w-9/12 rounded bg-slate-200" />
      </div>
    </div>
  );
}

function PaperView({ content }: { content: PaperSummaryContent }) {
  return (
    <div className="space-y-4 text-sm">
      <p className="leading-relaxed text-slate-800">{content.tldr}</p>
      <dl className="space-y-2">
        {(
          [
            ['목적', content.structure.objective],
            ['방법', content.structure.method],
            ['결과', content.structure.results],
            ['한계', content.structure.limitations],
            ['기여', content.structure.contribution],
          ] as const
        ).map(([label, value]) => (
          <div key={label}>
            <dt className="font-semibold text-slate-500">{label}</dt>
            <dd className="text-slate-800">{value}</dd>
          </div>
        ))}
      </dl>
      <Keypoints points={content.keypoints} />
      {content.glossary.length > 0 && (
        <div>
          <p className="mb-1 font-semibold text-slate-500">용어</p>
          <ul className="space-y-1">
            {content.glossary.map((g, i) => (
              <li key={i} className="text-slate-800">
                <span className="font-medium">{g.term}</span> — {g.desc}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

function Keypoints({ points }: { points: string[] }) {
  if (points.length === 0) return null;
  return (
    <div>
      <p className="mb-1 font-semibold text-slate-500">핵심</p>
      <ul className="list-disc space-y-1 pl-5 text-slate-800">
        {points.map((p, i) => (
          <li key={i}>{p}</li>
        ))}
      </ul>
    </div>
  );
}
