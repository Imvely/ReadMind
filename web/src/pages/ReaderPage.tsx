import { useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import PdfViewer, { type PdfViewerHandle } from '@/features/reader/PdfViewer';
import EpubViewer from '@/features/reader/EpubViewer';
import ReaderSettingsPanel from '@/features/reader/ReaderSettingsPanel';
import SummaryPanel from '@/features/reader/SummaryPanel';
import QaPanel from '@/features/reader/QaPanel';
import { useDocumentContentQuery, useDocumentQuery } from '@/hooks/documents';

type Tab = 'summary' | 'qa';

export default function ReaderPage() {
  const { docId } = useParams();
  const id = Number(docId);
  const pdfRef = useRef<PdfViewerHandle>(null);
  const [tab, setTab] = useState<Tab>('summary');
  const [showSettings, setShowSettings] = useState(false);

  const docQuery = useDocumentQuery(id);
  const contentQuery = useDocumentContentQuery(id, docQuery.data != null);

  const doc = docQuery.data;
  const isReady = doc?.parseStatus === 'READY';

  function jumpToPage(page: number, snippet?: string) {
    pdfRef.current?.scrollToPage(page, snippet);
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
        {/* 좌: PDF */}
        <main className="min-w-0 flex-1 border-r border-slate-200">
          {contentQuery.data ? (
            doc?.format === 'EPUB' ? (
              // 렌더러는 어댑터(§5) — 같은 핸들 계약으로 포맷별 교체.
              <EpubViewer ref={pdfRef} url={contentQuery.data.url} />
            ) : (
              <PdfViewer ref={pdfRef} url={contentQuery.data.url} />
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
