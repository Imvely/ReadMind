import * as pdfjsLib from 'pdfjs-dist';
import workerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url';

// pdf.js 워커를 Vite 번들 URL로 연결(어댑터 격리 — 렌더러 교체 시 여기만 바꾼다, CLAUDE.md §5).
pdfjsLib.GlobalWorkerOptions.workerSrc = workerUrl;

export type PdfDocument = pdfjsLib.PDFDocumentProxy;

export function loadPdf(url: string): Promise<PdfDocument> {
  return pdfjsLib.getDocument({ url }).promise;
}
