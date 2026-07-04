import type {
  CompleteResponse,
  CreateDocumentRequest,
  CreateDocumentResponse,
  DocumentContentResponse,
  DocumentDto,
  DocumentListResponse,
} from '@readmind/shared';
import { apiRequest } from '@/lib/api';

/** 파일 → 백엔드 포맷 코드. 뷰어가 있는 포맷만 허용(P1: PDF+EPUB) → 그 외는 거부. */
export function uploadFlowFormat(file: File): string {
  const name = file.name.toLowerCase();
  if (file.type === 'application/pdf' || name.endsWith('.pdf')) return 'PDF';
  if (file.type === 'application/epub+zip' || name.endsWith('.epub')) return 'EPUB';
  throw new Error('현재는 PDF와 EPUB만 업로드할 수 있어요.');
}

export function createDocument(req: CreateDocumentRequest): Promise<CreateDocumentResponse> {
  return apiRequest('/documents', { method: 'POST', body: req });
}

export function completeDocument(id: number): Promise<CompleteResponse> {
  return apiRequest(`/documents/${id}/complete`, { method: 'POST' });
}

export function listDocuments(page = 0, size = 20): Promise<DocumentListResponse> {
  return apiRequest(`/documents?page=${page}&size=${size}`);
}

export function getDocument(id: number): Promise<DocumentDto> {
  return apiRequest(`/documents/${id}`);
}

export function getDocumentContent(id: number): Promise<DocumentContentResponse> {
  return apiRequest(`/documents/${id}/content`);
}

export function deleteDocument(id: number): Promise<void> {
  return apiRequest(`/documents/${id}`, { method: 'DELETE' });
}
