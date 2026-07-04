import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useSaveProgress } from './annotations';

const putProgress = vi.fn().mockResolvedValue({});
vi.mock('@/api/annotations', () => ({
  putProgress: (...args: unknown[]) => putProgress(...args),
  getProgress: vi.fn(),
}));

function Harness({ onReady }: { onReady: (save: ReturnType<typeof useSaveProgress>['save']) => void }) {
  const { save } = useSaveProgress(1, 2000);
  onReady(save);
  return null;
}

function renderHarness() {
  const qc = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
  let save!: ReturnType<typeof useSaveProgress>['save'];
  const utils = render(
    <QueryClientProvider client={qc}>
      <Harness onReady={(s) => (save = s)} />
    </QueryClientProvider>,
  );
  return { save: (req: Parameters<typeof save>[0]) => save(req), ...utils };
}

describe('useSaveProgress', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    putProgress.mockClear();
  });
  afterEach(() => vi.useRealTimers());

  it('연속 호출은 디바운스되어 마지막 위치만 저장한다', async () => {
    const { save } = renderHarness();
    save({ location: { type: 'pdf', page: 1 }, percent: 10 });
    save({ location: { type: 'pdf', page: 2 }, percent: 20 });
    save({ location: { type: 'pdf', page: 3 }, percent: 30 });

    expect(putProgress).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(2000); // mutate는 마이크로태스크 경유 — async 진행 필요
    expect(putProgress).toHaveBeenCalledTimes(1);
    expect(putProgress).toHaveBeenCalledWith(1, { location: { type: 'pdf', page: 3 }, percent: 30 });
  });

  it('언마운트(리더 이탈) 시 대기 중이던 마지막 위치를 즉시 저장한다', async () => {
    const { save, unmount } = renderHarness();
    save({ location: { type: 'pdf', page: 7 }, percent: 70 });
    expect(putProgress).not.toHaveBeenCalled();

    unmount();
    await vi.advanceTimersByTimeAsync(0); // mutate 마이크로태스크 플러시
    expect(putProgress).toHaveBeenCalledWith(1, { location: { type: 'pdf', page: 7 }, percent: 70 });
  });
});
