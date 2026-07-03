import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { DocumentDto } from '@readmind/shared';
import {
  completeDocument,
  createDocument,
  deleteDocument,
  getDocument,
  getDocumentContent,
  listDocuments,
  uploadFlowFormat,
} from '@/api/documents';
import { uploadToPresignedUrl } from '@/lib/api';

export const documentKeys = {
  all: ['documents'] as const,
  detail: (id: number) => ['documents', id] as const,
};

export function useDocumentsQuery() {
  return useQuery({
    queryKey: documentKeys.all,
    queryFn: () => listDocuments(),
    // 목록에 파싱 중(PENDING/PARSING)인 문서가 하나라도 있으면 2초 폴링 → 모두 끝나면 멈춘다.
    refetchInterval: (query) => {
      const items = query.state.data?.items ?? [];
      const inProgress = items.some(
        (d) => d.parseStatus === 'PENDING' || d.parseStatus === 'PARSING',
      );
      return inProgress ? 2000 : false;
    },
  });
}

/** 단일 문서. 파싱 중이면 자동 폴링(2초)하여 READY로 바뀌면 멈춘다. */
export function useDocumentQuery(id: number) {
  return useQuery({
    queryKey: documentKeys.detail(id),
    queryFn: () => getDocument(id),
    refetchInterval: (query) => {
      const status = query.state.data?.parseStatus;
      return status === 'PENDING' || status === 'PARSING' ? 2000 : false;
    },
  });
}

/** 렌더용 presigned GET URL. 문서가 존재하면 파싱 상태와 무관하게 받을 수 있다. */
export function useDocumentContentQuery(id: number, enabled: boolean) {
  return useQuery({
    queryKey: [...documentKeys.detail(id), 'content'],
    queryFn: () => getDocumentContent(id),
    enabled,
    staleTime: 5 * 60 * 1000, // presigned URL TTL(기본 15분)보다 짧게.
  });
}

/** 업로드 3단계: create(presigned) → S3 PUT → complete(파싱 트리거). */
export function useUploadDocument() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (file: File): Promise<DocumentDto> => {
      const created = await createDocument({
        title: file.name.replace(/\.[^.]+$/, ''),
        format: uploadFlowFormat(file),
        fileSize: file.size,
      });
      await uploadToPresignedUrl(created.uploadUrl, file);
      await completeDocument(created.documentId);
      return getDocument(created.documentId);
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: documentKeys.all });
    },
  });
}

/** FAILED 문서 재파싱: complete 재호출이 파싱을 다시 트리거한다(READY면 백엔드가 스킵). */
export function useRetryParse() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => completeDocument(id),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: documentKeys.all });
    },
  });
}

export function useDeleteDocument() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => deleteDocument(id),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: documentKeys.all });
    },
  });
}
