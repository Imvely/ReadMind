import type {
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
