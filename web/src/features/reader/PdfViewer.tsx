import { forwardRef, useEffect, useImperativeHandle, useRef, useState } from 'react';
import { loadPdf, type PdfDocument } from './pdf';

export interface PdfViewerHandle {
  /** 1-based 페이지로 스크롤하고 잠깐 강조한다(Q&A 근거 클릭 점프용). */
  scrollToPage: (pageNo: number) => void;
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
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useImperativeHandle(ref, () => ({
    scrollToPage: (pageNo: number) => {
      const el = pageRefs.current[pageNo - 1];
      if (!el) return;
      el.scrollIntoView({ behavior: 'smooth', block: 'start' });
      el.classList.add('ring-2', 'ring-amber-400');
      window.setTimeout(() => el.classList.remove('ring-2', 'ring-amber-400'), 1600);
    },
  }));

  useEffect(() => {
    let cancelled = false;
    let pdf: PdfDocument | null = null;

    async function render() {
      setLoading(true);
      setError(null);
      try {
        pdf = await loadPdf(url);
        if (cancelled) return;
        const container = containerRef.current;
        if (!container) return;
        container.innerHTML = '';
        pageRefs.current = [];

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
      void pdf?.destroy();
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
