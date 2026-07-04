import { useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type { DocumentDto } from '@readmind/shared';
import { useAuthStore } from '@/store/auth';
import {
  useDeleteDocument,
  useDocumentsQuery,
  useRetryParse,
  useUploadDocument,
} from '@/hooks/documents';

const STATUS_LABEL: Record<DocumentDto['parseStatus'], string> = {
  PENDING: '대기 중',
  PARSING: '분석 중…',
  READY: '준비됨',
  FAILED: '실패',
};

const STATUS_STYLE: Record<DocumentDto['parseStatus'], string> = {
  PENDING: 'bg-slate-100 text-slate-600',
  PARSING: 'bg-amber-100 text-amber-700',
  READY: 'bg-emerald-100 text-emerald-700',
  FAILED: 'bg-red-100 text-red-700',
};

export default function LibraryPage() {
  const navigate = useNavigate();
  const { user, logout } = useAuthStore();
  const { data, isLoading, isError } = useDocumentsQuery();
  const upload = useUploadDocument();
  const del = useDeleteDocument();
  const retry = useRetryParse();
  const fileInput = useRef<HTMLInputElement>(null);
  const [uploadError, setUploadError] = useState<string | null>(null);

  function onPickFile(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    e.target.value = ''; // 같은 파일 재선택 허용.
    if (!file) return;
    setUploadError(null);
    upload.mutate(file, {
      onError: (err) => setUploadError(err instanceof Error ? err.message : '업로드 실패'),
    });
  }

  return (
    <div className="mx-auto max-w-5xl px-6 py-8">
      <header className="mb-8 flex items-center justify-between">
        <h1 className="text-xl font-bold text-slate-900">내 서재</h1>
        <div className="flex items-center gap-3 text-sm text-slate-500">
          <span>{user?.email}</span>
          <button onClick={logout} className="text-slate-400 hover:text-slate-700">
            로그아웃
          </button>
        </div>
      </header>

      <div className="mb-8">
        <input
          ref={fileInput}
          type="file"
          accept="application/pdf,application/epub+zip,.epub"
          className="hidden"
          onChange={onPickFile}
        />
        <button
          onClick={() => fileInput.current?.click()}
          disabled={upload.isPending}
          className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700 disabled:opacity-50"
        >
          {upload.isPending ? '업로드 중…' : 'PDF 업로드'}
        </button>
        {uploadError && <p className="mt-2 text-sm text-red-600">{uploadError}</p>}
      </div>

      {isLoading && <p className="text-slate-400">불러오는 중…</p>}
      {isError && <p className="text-red-600">목록을 불러오지 못했습니다.</p>}

      {data && data.items.length === 0 && (
        <p className="text-slate-400">아직 문서가 없어요. PDF를 업로드해 시작하세요.</p>
      )}

      <ul className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        {data?.items.map((doc) => (
          <li
            key={doc.id}
            className="group flex items-center justify-between rounded-xl border border-slate-200 bg-white p-4 hover:border-slate-300"
          >
            <button
              className="min-w-0 flex-1 text-left"
              onClick={() => navigate(`/reader/${doc.id}`)}
            >
              <p className="truncate font-medium text-slate-900">{doc.title}</p>
              <span
                className={`mt-1 inline-block rounded px-2 py-0.5 text-xs ${STATUS_STYLE[doc.parseStatus]}`}
              >
                {STATUS_LABEL[doc.parseStatus]}
              </span>
              {doc.parseStatus === 'FAILED' && doc.parseError && (
                <p className="mt-1 truncate text-xs text-red-500" title={doc.parseError}>
                  {doc.parseError}
                </p>
              )}
              {doc.progressPercent != null && doc.progressPercent > 0 && (
                // 이어읽기 진행률(§4.2 P1.5) — 카드 하단 프로그레스 바.
                <span className="mt-2 flex items-center gap-2" aria-label={`읽기 진행률 ${Math.round(doc.progressPercent)}%`}>
                  <span className="h-1 flex-1 overflow-hidden rounded-full bg-slate-100">
                    <span
                      className="block h-full rounded-full bg-amber-400"
                      style={{ width: `${Math.min(100, doc.progressPercent)}%` }}
                    />
                  </span>
                  <span className="shrink-0 text-[11px] tabular-nums text-slate-400">
                    {Math.round(doc.progressPercent)}%
                  </span>
                </span>
              )}
            </button>
            {doc.parseStatus === 'FAILED' && (
              <button
                onClick={() => retry.mutate(doc.id)}
                disabled={retry.isPending}
                className="ml-3 shrink-0 text-xs text-slate-500 hover:text-slate-900 disabled:opacity-50"
              >
                다시 분석
              </button>
            )}
            <button
              onClick={() => del.mutate(doc.id)}
              className="ml-3 text-xs text-slate-300 opacity-0 hover:text-red-600 group-hover:opacity-100"
            >
              삭제
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}
