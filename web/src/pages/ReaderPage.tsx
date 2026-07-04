import { useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import PdfViewer, { type PdfViewerHandle } from '@/features/reader/PdfViewer';
import EpubViewer from '@/features/reader/EpubViewer';
import ReaderSettingsPanel from '@/features/reader/ReaderSettingsPanel';
import SummaryPanel from '@/features/reader/SummaryPanel';
import QaPanel from '@/features/reader/QaPanel';
import { useDocumentContentQuery, useDocumentQuery } from '@/hooks/documents';
import { useProgressQuery, useSaveProgress } from '@/hooks/annotations';

type Tab = 'summary' | 'qa';

export default function ReaderPage() {
  const { docId } = useParams();
  const id = Number(docId);
  const pdfRef = useRef<PdfViewerHandle>(null);
  const [tab, setTab] = useState<Tab>('summary');
  const [showSettings, setShowSettings] = useState(false);

  const docQuery = useDocumentQuery(id);
  const contentQuery = useDocumentContentQuery(id, docQuery.data != null);
  const progressQuery = useProgressQuery(id, docQuery.data != null);
  const { save: saveProgress } = useSaveProgress(id);
  const [resume, setResume] = useState<{ label: string } | null>(null);

  const doc = docQuery.data;
  const isReady = doc?.parseStatus === 'READY';
  // 저장된 위치(§4.2 P1.5 이어읽기). 뷰어는 progress 로드 후에 마운트해 복원 위치를 넘긴다.
  const savedLoc = (progressQuery.data?.location ?? null) as
    | { type?: string; page?: number; cfi?: string }
    | null;

  // 복원 배너 — 의미 있는 저장 위치(2p 이상/CFI)가 있으면 1회 표시, 6초 후 자동 닫힘.
  useEffect(() => {
    const p = progressQuery.data;
    if (!p) return;
    const loc = p.location as { page?: number; cfi?: string } | null;
    if ((loc?.page && loc.page > 1) || loc?.cfi) {
      const where = loc.page ? `p.${loc.page}` : '마지막 위치';
      setResume({ label: `이어읽기: ${where}부터 (${Math.round(Number(p.percent))}%)` });
      const t = window.setTimeout(() => setResume(null), 6000);
      return () => window.clearTimeout(t);
    }
  }, [progressQuery.data]);

  function jumpToPage(page: number, snippet?: string) {
    pdfRef.current?.scrollToPage(page, snippet);
  }

  function onPdfPosition(pos: { page: number; totalPages: number }) {
    saveProgress({
      location: { type: 'pdf', page: pos.page },
      percent: Math.min(100, Math.round((pos.page / Math.max(1, pos.totalPages)) * 1000) / 10),
    });
  }

  function onEpubPosition(pos: { cfi: string; spineIndex: number; spineTotal: number }) {
    saveProgress({
      location: { type: 'epub', cfi: pos.cfi },
      percent: Math.min(
        100,
        Math.round(((pos.spineIndex + 1) / Math.max(1, pos.spineTotal)) * 1000) / 10,
      ),
    });
  }

  return (
    <div className="flex h-full flex-col">
      <header className="flex items-center gap-3 border-b border-slate-200 px-5 py-3">
        <Link to="/" className="text-sm text-slate-400 hover:text-slate-700">
          ← 서재
        </Link>
        <h1 className="truncate text-sm font-medium text-slate-900">{doc?.title ?? '문서'}</h1>
        {doc && !isReady && (
          <span className="rounded bg-amber-100 px-2 py-0.5 text-xs text-amber-700">
            {doc.parseStatus === 'FAILED' ? '분석 실패' : '분석 중…'}
          </span>
        )}
        <div className="relative ml-auto">
          <button
            onClick={() => setShowSettings((v) => !v)}
            aria-label="리딩 설정"
            aria-expanded={showSettings}
            className={`rounded-lg border px-2.5 py-1 text-sm ${
              showSettings
                ? 'border-slate-900 bg-slate-900 text-white'
                : 'border-slate-300 text-slate-600 hover:bg-slate-50'
            }`}
          >
            Aa
          </button>
          {showSettings && (
            <div className="absolute right-0 top-full z-20 mt-2">
              <ReaderSettingsPanel />
            </div>
          )}
        </div>
      </header>

      <div className="flex min-h-0 flex-1">
        {/* 좌: 본문 */}
        <main className="relative min-w-0 flex-1 border-r border-slate-200">
          {resume && (
            <div className="absolute left-1/2 top-3 z-30 flex -translate-x-1/2 items-center gap-3 rounded-full border border-slate-200 bg-white/95 px-4 py-1.5 text-sm text-slate-700 shadow">
              <span>{resume.label}</span>
              <button
                onClick={() => {
                  pdfRef.current?.scrollToPage(1);
                  setResume(null);
                }}
                className="text-slate-400 underline hover:text-slate-700"
              >
                처음부터
              </button>
              <button onClick={() => setResume(null)} aria-label="닫기" className="text-slate-400">
                ✕
              </button>
            </div>
          )}
          {contentQuery.data && !progressQuery.isPending ? (
            doc?.format === 'EPUB' ? (
              // 렌더러는 어댑터(§5) — 같은 핸들 계약으로 포맷별 교체.
              <EpubViewer
                ref={pdfRef}
                url={contentQuery.data.url}
                initialCfi={savedLoc?.cfi}
                onPositionChange={onEpubPosition}
              />
            ) : (
              <PdfViewer
                ref={pdfRef}
                url={contentQuery.data.url}
                initialPage={savedLoc?.page}
                onPositionChange={onPdfPosition}
              />
            )
          ) : (
            <div className="flex h-full items-center justify-center text-slate-400">
              {docQuery.isError ? '문서를 찾을 수 없습니다.' : '문서 불러오는 중…'}
            </div>
          )}
        </main>

        {/* 우: AI 패널 */}
        <aside className="flex w-[400px] flex-col">
          <div className="flex border-b border-slate-200">
            {(
              [
                ['summary', '요약'],
                ['qa', 'Q&A'],
              ] as const
            ).map(([key, label]) => (
              <button
                key={key}
                onClick={() => setTab(key)}
                className={`flex-1 py-2.5 text-sm font-medium ${
                  tab === key
                    ? 'border-b-2 border-slate-900 text-slate-900'
                    : 'text-slate-400 hover:text-slate-600'
                }`}
              >
                {label}
              </button>
            ))}
          </div>

          <div className="min-h-0 flex-1 overflow-auto p-4">
            {!isReady ? (
              <p className="text-sm text-slate-400">
                문서 분석이 끝나면 요약과 Q&A를 사용할 수 있어요.
              </p>
            ) : tab === 'summary' ? (
              <SummaryPanel documentId={id} />
            ) : (
              <QaPanel documentId={id} onJumpToPage={jumpToPage} />
            )}
          </div>
        </aside>
      </div>
    </div>
  );
}
