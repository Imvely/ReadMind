import { create } from 'zustand';
import { persist } from 'zustand/middleware';

/** 리딩 설정 (명세서 §6.1). UI 상태이므로 Zustand(§5), 기기 로컬에 영속. */
export type ReaderTheme = 'light' | 'sepia' | 'dark';

export interface ReaderSettingsState {
  theme: ReaderTheme;
  /** 본문 배율 — PDF는 페이지 스케일, EPUB(추후)은 폰트 크기(%)로 해석. */
  fontScale: number;
  /** 줄간격 — 리플로우(EPUB) 전용. PDF는 고정 조판이라 적용 불가. */
  lineHeight: number;
  /** 본문 좌우 여백(px). */
  marginX: number;
  /** 단 구성 — 1=단일, 2=2단(펼침). */
  columns: 1 | 2;
  autoScroll: boolean;
  /** 자동 스크롤 속도(px/초). */
  autoScrollSpeed: number;
  set: (patch: Partial<Omit<ReaderSettingsState, 'set' | 'reset'>>) => void;
  reset: () => void;
}

export const READER_DEFAULTS = {
  theme: 'light' as ReaderTheme,
  fontScale: 1,
  lineHeight: 1.6,
  marginX: 16,
  columns: 1 as const,
  autoScroll: false,
  autoScrollSpeed: 60,
};

/** 조절 범위 — UI 스텝퍼와 검증이 공유(매직넘버 금지). */
export const READER_LIMITS = {
  fontScale: { min: 0.7, max: 1.8, step: 0.1 },
  lineHeight: { min: 1.2, max: 2.2, step: 0.1 },
  marginX: { min: 0, max: 96, step: 16 },
  autoScrollSpeed: { min: 20, max: 200, step: 20 },
} as const;

const clamp = (v: number, min: number, max: number) => Math.min(max, Math.max(min, v));

export const useReaderSettings = create<ReaderSettingsState>()(
  persist(
    (set) => ({
      ...READER_DEFAULTS,
      set: (patch) =>
        set((s) => ({
          ...s,
          ...patch,
          ...(patch.fontScale != null && {
            fontScale: clamp(patch.fontScale, READER_LIMITS.fontScale.min, READER_LIMITS.fontScale.max),
          }),
          ...(patch.lineHeight != null && {
            lineHeight: clamp(patch.lineHeight, READER_LIMITS.lineHeight.min, READER_LIMITS.lineHeight.max),
          }),
          ...(patch.marginX != null && {
            marginX: clamp(patch.marginX, READER_LIMITS.marginX.min, READER_LIMITS.marginX.max),
          }),
          ...(patch.autoScrollSpeed != null && {
            autoScrollSpeed: clamp(
              patch.autoScrollSpeed,
              READER_LIMITS.autoScrollSpeed.min,
              READER_LIMITS.autoScrollSpeed.max,
            ),
          }),
        })),
      reset: () => set((s) => ({ ...s, ...READER_DEFAULTS })),
    }),
    { name: 'readmind-reader-settings' },
  ),
);

/** 테마별 리더 표면 스타일 — PDF 캔버스는 CSS 필터로 근사, EPUB(추후)은 실제 테마 적용. */
export const THEME_SURFACE: Record<ReaderTheme, { bg: string; canvasFilter: string }> = {
  light: { bg: '#f1f5f9', canvasFilter: 'none' },
  sepia: { bg: '#f4ecd8', canvasFilter: 'sepia(0.4) contrast(0.95)' },
  dark: { bg: '#17171b', canvasFilter: 'invert(0.92) hue-rotate(180deg)' },
};
