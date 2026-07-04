import { forwardRef, useEffect, useImperativeHandle, useRef, useState } from 'react';
import type { PageViewport } from 'pdfjs-dist';
import { loadPdf, type PdfDocument } from './pdf';
import { findSnippetSpan } from './snippetMatch';

export interface PdfViewerHandle {
  /**
   * 1-based 페이지로 스크롤하고 잠깐 강조한다(Q&A 근거 클릭 점프용).
   * snippet이 있으면 해당 텍스트 위치를 찾아 몇 초간 하이라이트했다가 페이드아웃한다.
   */
  scrollToPage: (pageNo: number, snippet?: string) => void;
}

interface Props {
  url: string;
}

/**
 * pdf.js 렌더러. 모든 페이지를 세로로 렌더하고 scrollToPage로 점프한다.
 * 렌더러는 어댑터로 격리되어 있어 EPUB(epubjs) 등으로 교체 가능(§5).
 */
const PdfViewer = forwardRef<PdfViewerHandle, Props>(function PdfViewer({ url }, ref) {
  const containerRef = useRef<HTMLDivElement>(null);
  const pageRefs = useRef<(HTMLDivElement | null)[]>([]);
  const viewportRefs = useRef<(PageViewport | null)[]>([]);
  const pdfRef = useRef<PdfDocument | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useImperativeHandle(ref, () => ({
    scrollToPage: (pageNo: number, snippet?: string) => {
      const el = pageRefs.current[pageNo - 1];
      if (!el) return;
      el.scrollIntoView({ behavior: 'smooth', block: 'start' });
      el.classList.add('ring-2', 'ring-amber-400');
      window.setTimeout(() => el.classList.remove('ring-2', 'ring-amber-400'), 1600);
      if (snippet) void flashSnippet(pageNo, snippet);
    },
  }));

  /** 근거 snippet의 텍스트 위치를 찾아 임시 하이라이트를 띄운다(§6.1 — 못 찾으면 페이지 링만). */
  async function flashSnippet(pageNo: number, snippet: string) {
    const pdf = pdfRef.current;
    const wrapper = pageRefs.current[pageNo - 1];
    const viewport = viewportRefs.current[pageNo - 1];
    if (!pdf || !wrapper || !viewport) return;

    try {
      const page = await pdf.getPage(pageNo);
      const textContent = await page.getTextContent();
      const items = textContent.items.filter(
        (it): it is import('pdfjs-dist/types/src/display/api').TextItem => 'str' in it,
      );

      // 유니코드 접기 기반 매칭 — 리가처/따옴표/공백 차이를 흡수한다(snippetMatch.ts).
      const span = findSnippetSpan(items.map((it) => it.str), snippet);
      if (!span) return; // 못 찾으면 정직하게 페이지 링만 — 엉뚱한 위치 하이라이트 금지

      wrapper.style.position = 'relative';
      const overlays: HTMLDivElement[] = [];
      for (let i = span.firstItem; i <= span.lastItem; i++) {
        const it = items[i];
        if (!it || it.width === 0) continue;
        const fontHeight = Math.hypot(it.transform[1], it.transform[3]) || it.height;
        const [x1, y1] = viewport.convertToViewportPoint(it.transform[4], it.transform[5]);
        const [x2, y2] = viewport.convertToViewportPoint(
          it.transform[4] + it.width,
          it.transform[5] + fontHeight,
        );
        const div = document.createElement('div');
        div.className =
          'pointer-events-none absolute rounded-sm bg-amber-300/60 transition-opacity duration-700';
        div.style.left = `${Math.min(x1, x2) - 1}px`;
        div.style.top = `${Math.min(y1, y2) - 1}px`;
        div.style.width = `${Math.abs(x2 - x1) + 2}px`;
        div.style.height = `${Math.abs(y2 - y1) + 2}px`;
        wrapper.appendChild(div);
        overlays.push(div);
      }
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

  useEffect(() => {
    let cancelled = false;

    async function render() {
      setLoading(true);
      setError(null);
      try {
        const pdf = await loadPdf(url);
        pdfRef.current = pdf;
        if (cancelled) return;
        const container = containerRef.current;
        if (!container) return;
        container.innerHTML = '';
        pageRefs.current = [];
        viewportRefs.current = [];

        const width = container.clientWidth - 32;
        for (let n = 1; n <= pdf.numPages; n++) {
          if (cancelled) return;
          const page = await pdf.getPage(n);
          const base = page.getViewport({ scale: 1 });
          const scale = width / base.width;
          const viewport = page.getViewport({ scale });

          const wrapper = document.createElement('div');
          wrapper.className = 'mx-auto mb-4 rounded bg-white shadow-sm transition-shadow';
          wrapper.style.width = `${viewport.width}px`;

          const canvas = document.createElement('canvas');
          canvas.width = viewport.width;
          canvas.height = viewport.height;
          const ctx = canvas.getContext('2d');
          if (!ctx) continue;

          wrapper.appendChild(canvas);
          container.appendChild(wrapper);
          pageRefs.current[n - 1] = wrapper;
          viewportRefs.current[n - 1] = viewport;

          await page.render({ canvasContext: ctx, viewport }).promise;
        }
        if (!cancelled) setLoading(false);
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : 'PDF를 불러오지 못했습니다.');
          setLoading(false);
        }
      }
    }

    void render();
    return () => {
      cancelled = true;
      void pdfRef.current?.destroy();
      pdfRef.current = null;
    };
  }, [url]);

  return (
    <div className="relative h-full overflow-auto bg-slate-100 p-4">
      {loading && <p className="absolute left-1/2 top-4 -translate-x-1/2 text-slate-400">PDF 로딩 중…</p>}
      {error && <p className="text-red-600">{error}</p>}
      <div ref={containerRef} />
    </div>
  );
});

export default PdfViewer;
