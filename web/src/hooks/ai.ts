import { useMutation } from '@tanstack/react-query';
import type { QaResponse, SummarizeResponse, SummaryStyle } from '@readmind/shared';
import { askDocument, summarizeDocument } from '@/api/ai';

export function useSummarize(documentId: number) {
  return useMutation<SummarizeResponse, Error, SummaryStyle>({
    mutationFn: (style) => summarizeDocument(documentId, { style }),
  });
}

export function useAsk(documentId: number) {
  return useMutation<QaResponse, Error, { sessionId: number | null; question: string }>({
    mutationFn: ({ sessionId, question }) => askDocument(documentId, { sessionId, question }),
  });
}
