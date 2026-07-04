import type { ProgressDto, UpdateProgressRequest } from '@readmind/shared';
import { apiRequest } from '@/lib/api';

// ── 진행률 (§4.3 — P1.5 이어읽기) ──
export function getProgress(documentId: number): Promise<ProgressDto | null> {
  return apiRequest(`/documents/${documentId}/progress`, { method: 'GET' });
}

export function putProgress(documentId: number, req: UpdateProgressRequest): Promise<ProgressDto> {
  return apiRequest(`/documents/${documentId}/progress`, { method: 'PUT', body: req });
}
