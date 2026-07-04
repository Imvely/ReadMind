import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { QaHistoryResponse, QaResponse, SummarizeResponse } from '@readmind/shared';
import { askDocument, getQaHistory, summarizeDocument } from '@/api/ai';
import { ApiClientError } from '@/lib/api';

/**
 * 요약 자동 로드: 리더 진입 시 버튼 없이 호출한다.
 * 서버 summaries 캐시가 SSOT — 캐시 히트면 AI 미호출·쿼터 미차감(§3)이라 자동 호출이 안전하다.
 * staleTime=Infinity: 생성된 요약은 불변이므로 탭 전환/재마운트에 재요청하지 않는다.
 */
export function useSummaryQuery(documentId: number, enabled: boolean) {
  return useQuery<SummarizeResponse, Error>({
    queryKey: ['summary', documentId],
    queryFn: () => summarizeDocument(documentId, { style: 'PAPER' }),
    enabled,
    staleTime: Infinity,
    // 쿼터 초과/검증 실패(4xx, ApiClientError)는 재시도해도 결과가 같다.
    retry: (failureCount, err) => !(err instanceof ApiClientError) && failureCount < 1,
  });
}

/** Q&A 대화 이력(§4.4 GET qa/history) — 서재 재진입/재로그인 후 대화 복원. */
export function useQaHistoryQuery(documentId: number, enabled: boolean) {
  return useQuery<QaHistoryResponse, Error>({
    queryKey: ['qa-history', documentId],
    queryFn: () => getQaHistory(documentId),
    enabled,
  });
}

export function useAsk(documentId: number) {
  const queryClient = useQueryClient();
  return useMutation<QaResponse, Error, { sessionId: number | null; question: string }>({
    mutationFn: ({ sessionId, question }) => askDocument(documentId, { sessionId, question }),
    onSuccess: (res, vars) => {
      // 서버가 이미 저장한 턴을 캐시에 즉시 반영 — 재조회 없이 이력과 일관.
      queryClient.setQueryData<QaHistoryResponse>(['qa-history', documentId], (prev) => ({
        sessionId: res.sessionId,
        messages: [
          ...(prev?.messages ?? []),
          { role: 'USER', content: vars.question },
          { role: 'ASSISTANT', content: res.answer, sources: res.sources },
        ],
      }));
    },
  });
}
