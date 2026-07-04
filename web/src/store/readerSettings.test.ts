import { beforeEach, describe, expect, it } from 'vitest';
import { READER_DEFAULTS, READER_LIMITS, useReaderSettings } from './readerSettings';

describe('readerSettings store', () => {
  beforeEach(() => useReaderSettings.getState().reset());

  it('기본값으로 시작한다', () => {
    const s = useReaderSettings.getState();
    expect(s.theme).toBe(READER_DEFAULTS.theme);
    expect(s.fontScale).toBe(READER_DEFAULTS.fontScale);
    expect(s.columns).toBe(1);
  });

  it('부분 갱신이 다른 값에 영향을 주지 않는다', () => {
    useReaderSettings.getState().set({ theme: 'dark' });
    const s = useReaderSettings.getState();
    expect(s.theme).toBe('dark');
    expect(s.fontScale).toBe(READER_DEFAULTS.fontScale);
  });

  it('수치는 한계 범위로 클램프된다 (매직넘버 아닌 READER_LIMITS 공유)', () => {
    useReaderSettings.getState().set({ fontScale: 99 });
    expect(useReaderSettings.getState().fontScale).toBe(READER_LIMITS.fontScale.max);
    useReaderSettings.getState().set({ marginX: -50 });
    expect(useReaderSettings.getState().marginX).toBe(READER_LIMITS.marginX.min);
    useReaderSettings.getState().set({ autoScrollSpeed: 10_000 });
    expect(useReaderSettings.getState().autoScrollSpeed).toBe(READER_LIMITS.autoScrollSpeed.max);
  });

  it('reset은 기본값으로 되돌린다', () => {
    useReaderSettings.getState().set({ theme: 'sepia', columns: 2, autoScroll: true });
    useReaderSettings.getState().reset();
    const s = useReaderSettings.getState();
    expect(s.theme).toBe('light');
    expect(s.columns).toBe(1);
    expect(s.autoScroll).toBe(false);
  });
});
