import * as pdfjsLib from 'pdfjs-dist';
import workerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url';
import type { TextContent, TextItem } from 'pdfjs-dist/types/src/display/api';

// pdf.js 워커를 Vite 번들 URL로 연결(어댑터 격리 — 렌더러 교체 시 여기만 바꾼다, CLAUDE.md §5).
pdfjsLib.GlobalWorkerOptions.workerSrc = workerUrl;

export type PdfDocument = pdfjsLib.PDFDocumentProxy;
export type { TextContent, TextItem };
// 텍스트 레이어(캔버스 위 투명 선택 레이어, §6.1)도 어댑터를 거쳐 노출한다.
export { TextLayer } from 'pdfjs-dist';

/** getTextContent 아이템 중 실제 텍스트(TextItem)만 걸러낸다(marked content 제외). */
export function isTextItem(item: TextContent['items'][number]): item is TextItem {
  return 'str' in item;
}

export function loadPdf(url: string): Promise<PdfDocument> {
  return pdfjsLib.getDocument({ url }).promise;
}
