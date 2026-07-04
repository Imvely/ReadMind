import { forwardRef, useEffect, useImperativeHandle, useRef, useState } from 'react';
import ePub, { type Rendition } from 'epubjs';
import type { PdfViewerHandle } from './PdfViewer';
import { useReaderSettings, type ReaderTheme } from '@/store/readerSettings';

interface Props {
  url: string;
}

/** 테마별 EPUB 본문 색 — 리플로우 문서라 캔버스 필터가 아닌 실제 CSS로 적용한다. */
const EPUB_THEME: Record<ReaderTheme, { color: string; background: string }> = {
  light: { color: '#0f172a', background: '#ffffff' },
  sepia: { color: '#433422', background: '#f4ecd8' },
  dark: { color: '#d4d4d8', background: '#17171b' },
};

/**
 * epubjs 렌더러 어댑터 (§6.1, PdfViewer와 같은 핸들 계약 — 렌더러 교체 가능, §5).
 * 리딩 설정: 테마/크기/줄간격/여백은 epubjs themes로 실제 적용(리플로우),
 * 단 구성=2단이면 양면(paginated spread), 단일이면 연속 스크롤(자동 스크롤 가능).
 */
const EpubViewer = forwardRef<PdfViewerHandle, Props>(function EpubViewer({ url }, ref) {
  const hostRef = useRef<HTMLDivElement>(null);
  const renditionRef = useRef<Rendition | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const theme = useReaderSettings((s) => s.theme);
  const fontScale = useReaderSettings((s) => s.fontScale);
  const lineHeight = useReaderSettings((s) => s.lineHeight);
  const marginX = useReaderSettings((s) => s.marginX);
  const columns = useReaderSettings((s) => s.columns);
  const autoScroll = useReaderSettings((s) => s.autoScroll);
  const autoScrollSpeed = useReaderSettings((s) => s.autoScrollSpeed);

  useImperativeHandle(ref, () => ({
    // EPUB의 pageNo = "텍스트가 있는 spine 문서" 순서(ai-service epub 파서와 동일 규약).
    // 빈 섹션이 섞이면 ±1 오차 가능 — 베타 허용 오차. snippet 하이라이트는 EPUB 미지원(추후 CFI 기반).
    scrollToPage: (pageNo: number) => {
      const rendition = renditionRef.current;
      const book = rendition?.book;
      if (!rendition || !book) return;
      const item = book.spine.get(pageNo - 1);
      if (item?.href) void rendition.display(item.href);
    },
  }));

  // 렌더 — 단 구성이 바뀌면 flow가 달라져 재생성한다.
  useEffect(() => {
    const host = hostRef.current;
    if (!host) return;
    setLoading(true);
    setError(null);
    host.innerHTML = '';

    const book = ePub(url);
    // 단일 단 = continuous+scrolled: 챕터 경계 없이 스크롤만으로 이어 읽는다
    // (scrolled-doc은 현재 챕터만 렌더돼 다음 장 이동이 막힘 — 베타 피드백).
    const rendition = book.renderTo(host, {
      width: '100%',
      height: '100%',
      manager: columns === 2 ? 'default' : 'continuous',
      flow: columns === 2 ? 'paginated' : 'scrolled',
      spread: columns === 2 ? 'auto' : 'none',
    });
    renditionRef.current = rendition;

    rendition
      .display()
      .then(() => setLoading(false))
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : 'EPUB을 불러오지 못했습니다.');
        setLoading(false);
      });

    return () => {
      renditionRef.current = null;
      book.destroy();
    };
  }, [url, columns]);

  // 설정 적용 — themes는 재생성 없이 반영된다.
  useEffect(() => {
    const rendition = renditionRef.current;
    if (!rendition || loading) return;
    rendition.themes.register('readmind', {
      body: {
        color: EPUB_THEME[theme].color,
        background: EPUB_THEME[theme].background,
        'line-height': String(lineHeight),
        'padding-left': `${marginX}px !important`,
        'padding-right': `${marginX}px !important`,
      },
    });
    rendition.themes.select('readmind');
    rendition.themes.fontSize(`${Math.round(fontScale * 100)}%`);
  }, [theme, fontScale, lineHeight, marginX, loading]);

  // 자동 스크롤 — 연속 스크롤(단일 단) 모드에서만 의미가 있다.
  useEffect(() => {
    if (!autoScroll || columns === 2 || loading) return;
    const scrollEl = hostRef.current?.querySelector('.epub-container') as HTMLElement | null;
    if (!scrollEl) return;
    let raf = 0;
    let prev = performance.now();
    const tick = (now: number) => {
      scrollEl.scrollTop += (autoScrollSpeed * (now - prev)) / 1000;
      prev = now;
      if (scrollEl.scrollTop + scrollEl.clientHeight < scrollEl.scrollHeight - 1) {
        raf = requestAnimationFrame(tick);
      }
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [autoScroll, autoScrollSpeed, columns, loading]);

  // 2단(paginated)일 때 좌우 이동 — 키보드 ←/→.
  useEffect(() => {
    if (columns !== 2) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'ArrowRight') void renditionRef.current?.next();
      if (e.key === 'ArrowLeft') void renditionRef.current?.prev();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [columns]);

  return (
    // 바깥 여백/패딩 없음 — 이북 영역이 AI 패널까지 가득 찬다(베타 피드백).
    // 여백(marginX)은 본문 body 패딩(themes)으로만 적용하고, 배경도 본문과 같은 색으로
    // 맞춰 잔여 거터가 띠처럼 보이지 않게 한다.
    <div
      className="relative h-full"
      style={{ backgroundColor: EPUB_THEME[theme].background }}
    >
      {loading && (
        <p className="absolute left-1/2 top-4 z-10 -translate-x-1/2 text-slate-400">EPUB 로딩 중…</p>
      )}
      {error && <p className="p-4 text-red-600">{error}</p>}
      <div ref={hostRef} className="h-full" />
      {columns === 2 && !loading && !error && (
        <>
          <PageNavButton side="left" onClick={() => void renditionRef.current?.prev()} />
          <PageNavButton side="right" onClick={() => void renditionRef.current?.next()} />
        </>
      )}
    </div>
  );
});

function PageNavButton({ side, onClick }: { side: 'left' | 'right'; onClick: () => void }) {
  return (
    <button
      onClick={onClick}
      aria-label={side === 'left' ? '이전 페이지' : '다음 페이지'}
      className={`absolute top-1/2 z-10 -translate-y-1/2 rounded-full border border-slate-300 bg-white/80 px-3 py-2 text-slate-600 shadow hover:bg-white ${
        side === 'left' ? 'left-2' : 'right-2'
      }`}
    >
      {side === 'left' ? '‹' : '›'}
    </button>
  );
}

export default EpubViewer;
