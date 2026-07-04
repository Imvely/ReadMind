import {
  forwardRef,
  useCallback,
  useEffect,
  useImperativeHandle,
  useRef,
  useState,
  type CSSProperties,
} from 'react';
import type { PageViewport } from 'pdfjs-dist';
import {
  TextLayer,
  isTextItem,
  loadPdf,
  type PdfDocument,
  type TextContent,
  type TextItem,
} from './pdf';
import { findSnippetSpan } from './snippetMatch';
import {
  MIN_QUERY_LENGTH,
  buildPageIndex,
  findMatches,
  initialActiveIndex,
  normalizeQuery,
  type CharRef,
  type PageMatch,
  type PageSearchIndex,
} from './pdfSearch';
import { THEME_SURFACE, useReaderSettings } from '@/store/readerSettings';

export interface PdfViewerHandle {
  /**
   * 1-based 페이지로 스크롤하고 잠깐 강조한다(Q&A 근거 클릭 점프용).
   * snippet이 있으면 해당 텍스트 위치를 찾아 몇 초간 하이라이트했다가 페이드아웃한다.
   */
  scrollToPage: (pageNo: number, snippet?: string) => void;
  /** 본문 검색 바 열기(§6.1). PDF 전용 — EPUB 렌더러는 미구현이라 옵셔널. */
  openSearch?: () => void;
}

interface Props {
  url: string;
  /** 이어읽기 복원(1-based). 렌더 완료 후 1회 점프(§4.2 P1.5). */
  initialPage?: number;
  /** 스크롤로 현재 페이지가 바뀔 때 보고 — 진행률 저장용. */
  onPositionChange?: (pos: { page: number; totalPages: number }) => void;
}

/** 가상화 반경(§6.1) — 현재 페이지 기준 ±2p만 캔버스/텍스트 레이어를 실제로 렌더. */
const RENDER_RADIUS = 2;

/** 페이지 자리(placeholder) 크기 — 레이아웃 패스에서 전 페이지 계산, 스크롤바가 안 튀게 고정. */
interface PageLayout {
  width: number;
  height: number;
}

/** 문서 전체 기준 검색 매치 = 페이지 번호 + 페이지 내 아이템/문자 구간. */
interface DocMatch extends PageMatch {
  page: number;
}

interface OverlayRect {
  left: number;
  top: number;
  width: number;
  height: number;
}

/**
 * 아이템 구간(문자 오프셋 포함)을 페이지 뷰포트 픽셀 사각형으로 변환한다.
 * 문자 위치는 아이템 폭의 비율로 근사(가로쓰기 가정 — flashSnippet 기존 방식과 동일).
 */
function itemSpanRects(
  items: TextItem[],
  viewport: PageViewport,
  first: CharRef,
  last: CharRef,
): OverlayRect[] {
  const rects: OverlayRect[] = [];
  for (let i = first.item; i <= last.item; i++) {
    const it = items[i];
    if (!it || it.width === 0 || it.str.length === 0) continue;
    const len = it.str.length;
    const from = i === first.item ? first.char / len : 0;
    const to = i === last.item ? Math.min(1, (last.char + 1) / len) : 1;
    if (to <= from) continue;
    const fontHeight = Math.hypot(it.transform[1], it.transform[3]) || it.height;
    const [ax, ay] = viewport.convertToViewportPoint(
      it.transform[4] + it.width * from,
      it.transform[5],
    );
    const [bx, by] = viewport.convertToViewportPoint(
      it.transform[4] + it.width * to,
      it.transform[5] + fontHeight,
    );
    rects.push({
      left: Math.min(ax, bx) - 1,
      top: Math.min(ay, by) - 1,
      width: Math.abs(bx - ax) + 2,
      height: Math.abs(by - ay) + 2,
    });
  }
  return rects;
}

/** 페이지 래퍼 위에 하이라이트 오버레이 div들을 얹는다(제거는 호출자 책임). */
function paintOverlays(
  wrapper: HTMLElement,
  rects: OverlayRect[],
  className: string,
): HTMLDivElement[] {
  return rects.map((r) => {
    const div = document.createElement('div');
    div.className = `pointer-events-none absolute rounded-sm ${className}`;
    div.style.left = `${r.left}px`;
    div.style.top = `${r.top}px`;
    div.style.width = `${r.width}px`;
    div.style.height = `${r.height}px`;
    wrapper.appendChild(div);
    return div;
  });
}

interface PageContentProps {
  pdf: PdfDocument;
  pageNo: number;
  viewport: PageViewport;
  canvasFilter: string;
  getTextContent: (pageNo: number) => Promise<TextContent>;
}

/** 페이지 1장 — 캔버스(조판) + 투명 텍스트 레이어(드래그 선택). 범위 밖이면 언마운트로 회수. */
function PdfPageContent({ pdf, pageNo, viewport, canvasFilter, getTextContent }: PageContentProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const textRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    let cancelled = false;
    let renderTask: { cancel: () => void; promise: Promise<unknown> } | null = null;
    let textLayer: TextLayer | null = null;
    const canvas = canvasRef.current;
    const textHost = textRef.current;
    if (!canvas || !textHost) return;

    (async () => {
      const page = await pdf.getPage(pageNo);
      if (cancelled) return;
      canvas.width = viewport.width;
      canvas.height = viewport.height;
      const ctx = canvas.getContext('2d');
      if (!ctx) return;
      renderTask = page.render({ canvasContext: ctx, viewport });
      const textContent = await getTextContent(pageNo);
      if (cancelled) return;
      textHost.textContent = '';
      textLayer = new TextLayer({ textContentSource: textContent, container: textHost, viewport });
      await Promise.all([renderTask.promise, textLayer.render()]);
    })().catch(() => {
      // 취소(범위 이탈/설정 변경) 또는 렌더 실패 — 자리(placeholder)는 유지된다.
    });

    return () => {
      cancelled = true;
      renderTask?.cancel();
      textLayer?.cancel();
    };
  }, [pdf, pageNo, viewport, getTextContent]);

  return (
    <>
      <canvas ref={canvasRef} className="absolute inset-0" style={{ filter: canvasFilter }} />
      {/* --scale-factor: pdf.js TextLayer가 스팬 좌표 calc에 요구하는 CSS 변수 */}
      <div
        ref={textRef}
        className="textLayer"
        style={{ '--scale-factor': String(viewport.scale) } as CSSProperties}
      />
    </>
  );
}

/**
 * pdf.js 렌더러. 전 페이지 자리를 실측 크기로 깔고(스크롤 안정), 현재 페이지 ±2p만
 * 캔버스+텍스트 레이어를 실제 렌더한다(가상화, §6.1). 텍스트 레이어로 드래그 선택이
 * 가능하고, 상단 검색 바(Ctrl+F)로 본문 검색·점프를 제공한다.
 * 리딩 설정(§6.1): 테마=캔버스 CSS 필터, 크기=스케일, 여백=패딩, 2단=그리드, 자동 스크롤.
 * 렌더러는 어댑터로 격리되어 있어 EPUB(epubjs) 등으로 교체 가능(§5).
 */
const PdfViewer = forwardRef<PdfViewerHandle, Props>(function PdfViewer(
  { url, initialPage, onPositionChange },
  ref,
) {
  const scrollRef = useRef<HTMLDivElement>(null);
  const pageRefs = useRef<(HTMLDivElement | null)[]>([]);
  const viewportRefs = useRef<(PageViewport | null)[]>([]);
  const pdfRef = useRef<PdfDocument | null>(null);
  const totalRef = useRef(0);
  const currentPageRef = useRef(1);
  const restoredRef = useRef(false);
  const prevUrlRef = useRef<string | null>(null);

  const [pdfDoc, setPdfDoc] = useState<PdfDocument | null>(null);
  const [layouts, setLayouts] = useState<PageLayout[] | null>(null);
  const [range, setRange] = useState({ from: 0, to: RENDER_RADIUS });
  const [error, setError] = useState<string | null>(null);

  // 본문 검색 상태(§6.1) — 인덱스/아이템 캐시는 문서(url) 단위로 유지.
  const [searchOpen, setSearchOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [matches, setMatches] = useState<DocMatch[]>([]);
  const [activeIdx, setActiveIdx] = useState(0);
  const [indexing, setIndexing] = useState(false);
  const textContentCache = useRef(new Map<number, Promise<TextContent>>());
  const pageItems = useRef(new Map<number, TextItem[]>());
  const pageIndexes = useRef(new Map<number, PageSearchIndex>());
  const indexBuildRef = useRef<Promise<void> | null>(null);
  const searchSeqRef = useRef(0);
  const activeOverlaysRef = useRef<HTMLDivElement[]>([]);
  const searchInputRef = useRef<HTMLInputElement>(null);

  const theme = useReaderSettings((s) => s.theme);
  const fontScale = useReaderSettings((s) => s.fontScale);
  const marginX = useReaderSettings((s) => s.marginX);
  const columns = useReaderSettings((s) => s.columns);
  const autoScroll = useReaderSettings((s) => s.autoScroll);
  const autoScrollSpeed = useReaderSettings((s) => s.autoScrollSpeed);

  /** 페이지 텍스트는 문서당 1회만 추출(캐시) — 텍스트 레이어와 검색이 공유한다. */
  const getTextContent = useCallback((pageNo: number): Promise<TextContent> => {
    let p = textContentCache.current.get(pageNo);
    if (!p) {
      const pdf = pdfRef.current;
      if (!pdf) return Promise.reject(new Error('PDF not loaded'));
      p = pdf.getPage(pageNo).then((page) => page.getTextContent());
      p.catch(() => textContentCache.current.delete(pageNo));
      textContentCache.current.set(pageNo, p);
    }
    return p;
  }, []);

  /** 렌더 범위를 pageNo(1-based) 중심 ±반경으로 갱신. 2단이면 화면에 페이지가 2배로 보여 반경도 2배. */
  const applyRange = useCallback((pageNo: number) => {
    const total = totalRef.current;
    if (!total) return;
    const radius =
      useReaderSettings.getState().columns === 2 ? RENDER_RADIUS * 2 : RENDER_RADIUS;
    const from = Math.max(0, pageNo - 1 - radius);
    const to = Math.min(total - 1, pageNo - 1 + radius);
    setRange((prev) => (prev.from === from && prev.to === to ? prev : { from, to }));
  }, []);

  function clearActiveOverlays() {
    activeOverlaysRef.current.forEach((o) => o.remove());
    activeOverlaysRef.current = [];
  }

  useImperativeHandle(ref, () => ({
    scrollToPage: (pageNo: number, snippet?: string) => {
      const el = pageRefs.current[pageNo - 1];
      if (!el) return;
      applyRange(pageNo);
      el.scrollIntoView({ behavior: 'smooth', block: 'start' });
      el.classList.add('ring-2', 'ring-amber-400');
      window.setTimeout(() => el.classList.remove('ring-2', 'ring-amber-400'), 1600);
      if (snippet) void flashSnippet(pageNo, snippet);
    },
    openSearch: () => {
      setSearchOpen(true);
      searchInputRef.current?.focus();
    },
  }));

  /** 근거 snippet의 텍스트 위치를 찾아 임시 하이라이트를 띄운다(§6.1 — 못 찾으면 페이지 링만). */
  async function flashSnippet(pageNo: number, snippet: string) {
    const wrapper = pageRefs.current[pageNo - 1];
    const viewport = viewportRefs.current[pageNo - 1];
    if (!wrapper || !viewport) return;
    try {
      const textContent = await getTextContent(pageNo);
      const items = textContent.items.filter(isTextItem);
      // 유니코드 접기 기반 매칭 — 리가처/따옴표/공백 차이를 흡수한다(snippetMatch.ts).
      const span = findSnippetSpan(items.map((it) => it.str), snippet);
      if (!span) return; // 못 찾으면 정직하게 페이지 링만 — 엉뚱한 위치 하이라이트 금지
      const rects = itemSpanRects(
        items,
        viewport,
        { item: span.firstItem, char: 0 },
        { item: span.lastItem, char: Math.max(0, items[span.lastItem].str.length - 1) },
      );
      const overlays = paintOverlays(wrapper, rects, 'bg-amber-300/60 transition-opacity duration-700');
      if (overlays.length === 0) return;
      // 근거 줄 자체로 스크롤 — "페이지 상단만 보여서 어딘지 모르는" 문제 해결.
      overlays[0].scrollIntoView?.({ behavior: 'smooth', block: 'center' });
      // 2.4초 표시 후 페이드아웃 → 제거.
      window.setTimeout(() => overlays.forEach((o) => (o.style.opacity = '0')), 2400);
      window.setTimeout(() => overlays.forEach((o) => o.remove()), 3200);
    } catch {
      // 하이라이트는 보조 연출 — 실패해도 점프(페이지 링)는 이미 동작했다.
    }
  }

  // 레이아웃 패스 — 문서를 열고 전 페이지 크기만 계산한다(렌더는 범위 내 페이지가 각자).
  // 크기(fontScale)/단(columns)이 바뀌면 페이지 크기가 달라져 다시 계산한다.
  useEffect(() => {
    let cancelled = false;

    async function layout() {
      setError(null);
      setLayouts(null);
      setPdfDoc(null);
      if (prevUrlRef.current !== url) {
        // 새 문서 — 문서 단위 캐시·검색 상태·복원 플래그 초기화.
        prevUrlRef.current = url;
        textContentCache.current.clear();
        pageItems.current.clear();
        pageIndexes.current.clear();
        indexBuildRef.current = null;
        restoredRef.current = false;
        currentPageRef.current = 1;
        clearActiveOverlays();
        setSearchOpen(false);
        setQuery('');
        setMatches([]);
        setActiveIdx(0);
      }
      try {
        const pdf = await loadPdf(url);
        if (cancelled) {
          void pdf.destroy();
          return;
        }
        pdfRef.current = pdf;

        const scroller = scrollRef.current;
        const gap = 16;
        const padX = 16 + useReaderSettings.getState().marginX;
        const avail = (scroller?.clientWidth ?? 800) - padX * 2 - (columns === 2 ? gap : 0);
        const pageWidth = (avail / columns) * fontScale;

        const arr: PageLayout[] = [];
        viewportRefs.current = [];
        for (let n = 1; n <= pdf.numPages; n++) {
          const page = await pdf.getPage(n);
          if (cancelled) return;
          const base = page.getViewport({ scale: 1 });
          const viewport = page.getViewport({ scale: pageWidth / base.width });
          viewportRefs.current[n - 1] = viewport;
          arr.push({ width: viewport.width, height: viewport.height });
        }
        if (cancelled) return;
        totalRef.current = arr.length;
        setLayouts(arr);
        setPdfDoc(pdf);
        // 초기 렌더 범위 — 복원 대상 페이지(이어읽기) 또는 현재 페이지 중심.
        const startPage =
          !restoredRef.current && initialPage && initialPage > 1
            ? initialPage
            : currentPageRef.current;
        applyRange(startPage);
      } catch (err) {
        if (!cancelled) setError(err instanceof Error ? err.message : 'PDF를 불러오지 못했습니다.');
      }
    }

    void layout();
    return () => {
      cancelled = true;
      void pdfRef.current?.destroy();
      pdfRef.current = null;
    };
    // marginX는 페이지 폭에 영향을 주지만 재계산 비용 대비 미미 — 기존과 동일하게 제외.
  }, [url, fontScale, columns, initialPage, applyRange]);

  // 이어읽기 복원 — 최초 레이아웃에서만(설정 변경 재계산에선 현재 위치 유지가 자연스러움).
  useEffect(() => {
    if (!layouts) return;
    clearActiveOverlays(); // 재레이아웃되면 기존 오버레이 좌표는 무효
    if (!restoredRef.current && initialPage && initialPage > 1) {
      restoredRef.current = true;
      currentPageRef.current = initialPage;
      pageRefs.current[initialPage - 1]?.scrollIntoView({ block: 'start' });
    }
  }, [layouts, initialPage]);

  // 현재 페이지 추적 — 뷰포트 상단에 걸친 페이지를 rAF 스로틀로 계산해
  // 렌더 범위(가상화)를 옮기고, 페이지가 바뀔 때만 진행률을 보고한다.
  useEffect(() => {
    const el = scrollRef.current;
    if (!el) return;
    let raf = 0;
    const update = () => {
      raf = 0;
      const top = el.scrollTop + 48;
      let page = 1;
      for (let i = 0; i < totalRef.current; i++) {
        const w = pageRefs.current[i];
        if (w && w.offsetTop <= top) page = i + 1;
      }
      if (page === currentPageRef.current) return;
      currentPageRef.current = page;
      applyRange(page);
      onPositionChange?.({ page, totalPages: totalRef.current });
    };
    const onScroll = () => {
      if (!raf) raf = requestAnimationFrame(update);
    };
    el.addEventListener('scroll', onScroll, { passive: true });
    return () => {
      el.removeEventListener('scroll', onScroll);
      if (raf) cancelAnimationFrame(raf);
    };
  }, [onPositionChange, layouts, applyRange]);

  // 자동 스크롤 — rAF 기반, 속도는 px/초. 끝에 닿으면 멈춘다.
  useEffect(() => {
    if (!autoScroll) return;
    const el = scrollRef.current;
    if (!el) return;
    let raf = 0;
    let prev = performance.now();
    const tick = (now: number) => {
      el.scrollTop += (autoScrollSpeed * (now - prev)) / 1000;
      prev = now;
      if (el.scrollTop + el.clientHeight < el.scrollHeight - 1) raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [autoScroll, autoScrollSpeed, layouts]);

  // Ctrl/Cmd+F — 브라우저 찾기 대신 본문 검색 바를 연다(문서 리더 관례).
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'f') {
        e.preventDefault();
        setSearchOpen(true);
        searchInputRef.current?.focus();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  /** 전 페이지 텍스트 인덱스 구축(문서당 1회, 캐시 재사용). */
  function ensureSearchIndex(): Promise<void> {
    if (!indexBuildRef.current) {
      const pdf = pdfRef.current;
      if (!pdf) return Promise.reject(new Error('PDF not loaded'));
      indexBuildRef.current = (async () => {
        setIndexing(true);
        try {
          for (let p = 1; p <= pdf.numPages; p++) {
            if (pageIndexes.current.has(p)) continue;
            const textContent = await getTextContent(p);
            const items = textContent.items.filter(isTextItem);
            pageItems.current.set(p, items);
            pageIndexes.current.set(p, buildPageIndex(items.map((it) => it.str)));
          }
        } finally {
          setIndexing(false);
        }
      })();
      indexBuildRef.current.catch(() => {
        indexBuildRef.current = null; // 실패 시 다음 검색에서 재시도
      });
    }
    return indexBuildRef.current;
  }

  async function runSearch(q: string) {
    const seq = ++searchSeqRef.current;
    clearActiveOverlays();
    const nq = normalizeQuery(q);
    if (nq.length < MIN_QUERY_LENGTH) {
      setMatches([]);
      setActiveIdx(0);
      return;
    }
    try {
      await ensureSearchIndex();
    } catch {
      return; // 문서가 내려갔거나 로드 실패 — 조용히 무시
    }
    if (seq !== searchSeqRef.current) return; // 그 사이 질의가 바뀜
    const found: DocMatch[] = [];
    for (let p = 1; p <= totalRef.current; p++) {
      const idx = pageIndexes.current.get(p);
      if (!idx) continue;
      for (const m of findMatches(idx, nq)) found.push({ page: p, ...m });
    }
    setMatches(found);
    const start = found.length
      ? initialActiveIndex(found.map((f) => f.page), currentPageRef.current)
      : 0;
    setActiveIdx(start);
    if (found.length) jumpToMatch(found[start]);
  }

  /** 매치 위치로 점프 + 지속 하이라이트(다음 이동/질의 변경까지 유지). */
  function jumpToMatch(m: DocMatch) {
    clearActiveOverlays();
    const wrapper = pageRefs.current[m.page - 1];
    if (!wrapper) return;
    applyRange(m.page);
    const viewport = viewportRefs.current[m.page - 1];
    const items = pageItems.current.get(m.page);
    if (viewport && items) {
      const overlays = paintOverlays(
        wrapper,
        itemSpanRects(items, viewport, m.first, m.last),
        'bg-orange-400/50',
      );
      activeOverlaysRef.current = overlays;
      if (overlays.length > 0) {
        overlays[0].scrollIntoView?.({ behavior: 'smooth', block: 'center' });
        return;
      }
    }
    wrapper.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  function navigateMatch(dir: 1 | -1) {
    if (matches.length === 0) return;
    const next = (activeIdx + dir + matches.length) % matches.length;
    setActiveIdx(next);
    jumpToMatch(matches[next]);
  }

  function closeSearch() {
    clearActiveOverlays();
    setSearchOpen(false);
    setQuery('');
    setMatches([]);
    setActiveIdx(0);
  }

  // 타이핑 중 라이브 검색(250ms 디바운스).
  useEffect(() => {
    if (!searchOpen) return;
    const t = window.setTimeout(() => void runSearch(query), 250);
    return () => window.clearTimeout(t);
    // runSearch는 렌더마다 새 함수지만 이 효과의 클로저가 같은 query를 보므로 deps에서 제외.
  }, [query, searchOpen]);

  const surface = THEME_SURFACE[theme];
  const loading = !layouts && !error;

  return (
    <div
      ref={scrollRef}
      className="relative h-full overflow-auto"
      style={{ backgroundColor: surface.bg, padding: `16px ${16 + marginX}px` }}
    >
      {/* h-0: 검색바가 열려도 본문이 밀리지 않게 흐름에서 높이를 제거(오버레이처럼 뜬다). */}
      <div className="pointer-events-none sticky top-0 z-30 flex h-0 justify-end">
        {searchOpen && (
          <div className="pointer-events-auto flex items-center gap-1.5 rounded-lg border border-slate-200 bg-white/95 px-2.5 py-1.5 shadow-lg">
            <input
              ref={searchInputRef}
              autoFocus
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  e.preventDefault();
                  navigateMatch(e.shiftKey ? -1 : 1);
                } else if (e.key === 'Escape') {
                  closeSearch();
                }
              }}
              placeholder="본문 검색 (2자 이상)"
              aria-label="본문 검색"
              className="w-44 bg-transparent text-sm text-slate-800 outline-none placeholder:text-slate-400"
            />
            <span className="min-w-12 text-right text-xs tabular-nums text-slate-400">
              {indexing
                ? '준비 중…'
                : matches.length > 0
                  ? `${activeIdx + 1}/${matches.length}`
                  : normalizeQuery(query).length >= MIN_QUERY_LENGTH
                    ? '0건'
                    : ''}
            </span>
            <button
              onClick={() => navigateMatch(-1)}
              disabled={matches.length === 0}
              aria-label="이전 결과"
              className="rounded px-1 text-sm text-slate-500 hover:bg-slate-100 disabled:opacity-30"
            >
              ↑
            </button>
            <button
              onClick={() => navigateMatch(1)}
              disabled={matches.length === 0}
              aria-label="다음 결과"
              className="rounded px-1 text-sm text-slate-500 hover:bg-slate-100 disabled:opacity-30"
            >
              ↓
            </button>
            <button
              onClick={closeSearch}
              aria-label="검색 닫기"
              className="rounded px-1 text-sm text-slate-400 hover:bg-slate-100"
            >
              ✕
            </button>
          </div>
        )}
      </div>

      {loading && (
        <p className="absolute left-1/2 top-4 -translate-x-1/2 text-slate-400">PDF 로딩 중…</p>
      )}
      {error && <p className="text-red-600">{error}</p>}

      <div
        className={
          columns === 2
            ? 'grid grid-cols-2 items-start justify-items-center gap-4'
            : 'flex flex-col items-center gap-4'
        }
      >
        {layouts?.map((l, i) => {
          const viewport = viewportRefs.current[i];
          return (
            <div
              key={i}
              ref={(el) => {
                pageRefs.current[i] = el;
              }}
              data-page-no={i + 1}
              className="relative rounded shadow-sm transition-shadow"
              style={{
                width: l.width,
                height: l.height,
                // 미렌더 자리 배경 — 다크 테마에서 흰 플래시가 튀지 않게 근사색.
                backgroundColor: theme === 'dark' ? '#26262b' : '#ffffff',
              }}
            >
              {pdfDoc && viewport && i >= range.from && i <= range.to && (
                <PdfPageContent
                  pdf={pdfDoc}
                  pageNo={i + 1}
                  viewport={viewport}
                  canvasFilter={surface.canvasFilter}
                  getTextContent={getTextContent}
                />
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
});

export default PdfViewer;
