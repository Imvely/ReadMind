import type {
  QaHistoryResponse,
  QaRequest,
  QaResponse,
  SummarizeRequest,
  SummarizeResponse,
} from '@readmind/shared';
import { apiRequest } from '@/lib/api';

export function summarizeDocument(
  documentId: number,
  req: SummarizeRequest = {},
): Promise<SummarizeResponse> {
  return apiRequest(`/documents/${documentId}/summarize`, { method: 'POST', body: req });
}

export function askDocument(documentId: number, req: QaRequest): Promise<QaResponse> {
  return apiRequest(`/documents/${documentId}/qa`, { method: 'POST', body: req });
}

/** 최근 세션 대화 이력 (§4.4). 조회 전용 — 쿼터 미차감. */
export function getQaHistory(documentId: number): Promise<QaHistoryResponse> {
  return apiRequest(`/documents/${documentId}/qa/history`, { method: 'GET' });
}
