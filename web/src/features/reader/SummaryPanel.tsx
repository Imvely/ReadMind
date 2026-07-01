import type { PaperSummaryContent, PlainSummaryContent } from '@readmind/shared';
import { ApiClientError } from '@/lib/api';
import { useSummarize } from '@/hooks/ai';

function isPaper(content: unknown): content is PaperSummaryContent {
  return typeof content === 'object' && content !== null && 'structure' in content;
}
function isPlain(content: unknown): content is PlainSummaryContent {
  return typeof content === 'object' && content !== null && 'keypoints' in content && !('structure' in content);
}

export default function SummaryPanel({ documentId }: { documentId: number }) {
  const summarize = useSummarize(documentId);
  const result = summarize.data;
  const quotaExceeded =
    summarize.error instanceof ApiClientError && summarize.error.code === 'QUOTA_EXCEEDED';

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2">
        <button
          onClick={() => summarize.mutate('PAPER')}
          disabled={summarize.isPending}
          className="rounded-lg bg-slate-900 px-3 py-1.5 text-sm font-medium text-white hover:bg-slate-700 disabled:opacity-50"
        >
          {summarize.isPending ? '요약 중…' : '요약 생성'}
        </button>
        {result?.cached && <span className="text-xs text-slate-400">캐시됨</span>}
      </div>

      {quotaExceeded && (
        <p className="rounded-lg bg-amber-50 p-3 text-sm text-amber-800">
          무료 요약 사용량을 모두 썼어요. 업그레이드하면 계속 요약할 수 있습니다.
        </p>
      )}
      {summarize.error && !quotaExceeded && (
        <p className="text-sm text-red-600">{summarize.error.message}</p>
      )}

      {result && isPaper(result.content) && <PaperView content={result.content} />}
      {result && isPlain(result.content) && (
        <div className="space-y-3">
          <p className="text-sm leading-relaxed text-slate-800">{result.content.tldr}</p>
          <Keypoints points={result.content.keypoints} />
        </div>
      )}
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
