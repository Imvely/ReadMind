import type {
  CompleteResponse,
  CreateDocumentRequest,
  CreateDocumentResponse,
  DocumentContentResponse,
  DocumentDto,
  DocumentListResponse,
} from '@readmind/shared';
import { apiRequest } from '@/lib/api';

/** 파일 → 백엔드 포맷 코드. Phase 0은 PDF만 지원(SUPPORTED_FORMATS) → 그 외는 거부. */
export function uploadFlowFormat(file: File): string {
  const isPdf = file.type === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf');
  if (!isPdf) {
    throw new Error('현재는 PDF만 업로드할 수 있어요.');
  }
  return 'PDF';
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
