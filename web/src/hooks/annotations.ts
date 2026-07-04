import { useEffect, useMemo, useRef } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import type { ProgressDto, UpdateProgressRequest } from '@readmind/shared';
import { getProgress, putProgress } from '@/api/annotations';

/** 진행률 조회 — 리더 진입 시 1회(복원용). 이후 저장은 낙관적이라 재조회 불필요. */
export function useProgressQuery(documentId: number, enabled: boolean) {
  return useQuery<ProgressDto | null, Error>({
    queryKey: ['progress', documentId],
    queryFn: () => getProgress(documentId),
    enabled,
    staleTime: Infinity,
    retry: 1,
  });
}

/** 진행률 저장 위치가 바뀔 때마다 부르면 내부에서 디바운스(기본 2초)로 PUT 한다.
 * 저장 실패는 조용히 무시 — 이어읽기는 보조 기능이라 사용자 흐름을 막지 않는다(다음 이동에서 재시도됨). */
export function useSaveProgress(documentId: number, debounceMs = 2000) {
  const mutation = useMutation<ProgressDto, Error, UpdateProgressRequest>({
    mutationFn: (req) => putProgress(documentId, req),
  });
  const timer = useRef<number | null>(null);
  const latest = useRef<UpdateProgressRequest | null>(null);
  const mutateRef = useRef(mutation.mutate);
  mutateRef.current = mutation.mutate;

  useEffect(
    () => () => {
      // 언마운트(리더 이탈) 시 마지막 위치를 즉시 저장 — 디바운스 대기분 유실 방지.
      if (timer.current != null) window.clearTimeout(timer.current);
      if (latest.current) mutateRef.current(latest.current);
    },
    [],
  );

  return useMemo(
    () => ({
      save: (req: UpdateProgressRequest) => {
        latest.current = req;
        if (timer.current != null) window.clearTimeout(timer.current);
        timer.current = window.setTimeout(() => {
          timer.current = null;
          if (latest.current) {
            mutateRef.current(latest.current);
            latest.current = null;
          }
        }, debounceMs);
      },
    }),
    [debounceMs],
  );
}
