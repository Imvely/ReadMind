import { READER_LIMITS, useReaderSettings, type ReaderTheme } from '@/store/readerSettings';

const THEMES: { key: ReaderTheme; label: string; swatch: string }[] = [
  { key: 'light', label: '라이트', swatch: 'bg-white border-slate-300' },
  { key: 'sepia', label: '세피아', swatch: 'bg-[#f4ecd8] border-amber-300' },
  { key: 'dark', label: '다크', swatch: 'bg-slate-900 border-slate-600' },
];

/** 리딩 설정 팝오버 (명세서 §6.1: 테마/크기/줄간격/여백/단/자동 스크롤). */
export default function ReaderSettingsPanel() {
  const s = useReaderSettings();

  return (
    <div className="w-72 space-y-4 rounded-xl border border-slate-200 bg-white p-4 shadow-lg">
      <Row label="테마">
        <div className="flex gap-2">
          {THEMES.map((t) => (
            <button
              key={t.key}
              onClick={() => s.set({ theme: t.key })}
              aria-pressed={s.theme === t.key}
              className={`h-8 w-14 rounded-lg border text-[11px] ${t.swatch} ${
                s.theme === t.key ? 'ring-2 ring-slate-900' : ''
              } ${t.key === 'dark' ? 'text-slate-200' : 'text-slate-700'}`}
            >
              {t.label}
            </button>
          ))}
        </div>
      </Row>

      <Stepper
        label="본문 크기"
        value={`${Math.round(s.fontScale * 100)}%`}
        onDec={() => s.set({ fontScale: s.fontScale - READER_LIMITS.fontScale.step })}
        onInc={() => s.set({ fontScale: s.fontScale + READER_LIMITS.fontScale.step })}
      />
      <Stepper
        label="줄간격 (EPUB)"
        value={s.lineHeight.toFixed(1)}
        onDec={() => s.set({ lineHeight: s.lineHeight - READER_LIMITS.lineHeight.step })}
        onInc={() => s.set({ lineHeight: s.lineHeight + READER_LIMITS.lineHeight.step })}
      />
      <Stepper
        label="여백"
        value={`${s.marginX}px`}
        onDec={() => s.set({ marginX: s.marginX - READER_LIMITS.marginX.step })}
        onInc={() => s.set({ marginX: s.marginX + READER_LIMITS.marginX.step })}
      />

      <Row label="단 구성">
        <div className="flex gap-2">
          {([1, 2] as const).map((n) => (
            <button
              key={n}
              onClick={() => s.set({ columns: n })}
              aria-pressed={s.columns === n}
              className={`rounded-lg border px-3 py-1 text-xs ${
                s.columns === n
                  ? 'border-slate-900 bg-slate-900 text-white'
                  : 'border-slate-300 text-slate-600'
              }`}
            >
              {n === 1 ? '단일' : '2단'}
            </button>
          ))}
        </div>
      </Row>

      <Row label="자동 스크롤">
        <div className="flex items-center gap-2">
          <button
            onClick={() => s.set({ autoScroll: !s.autoScroll })}
            aria-pressed={s.autoScroll}
            className={`rounded-lg border px-3 py-1 text-xs ${
              s.autoScroll
                ? 'border-slate-900 bg-slate-900 text-white'
                : 'border-slate-300 text-slate-600'
            }`}
          >
            {s.autoScroll ? '켜짐' : '꺼짐'}
          </button>
          {s.autoScroll && (
            <input
              type="range"
              aria-label="자동 스크롤 속도"
              min={READER_LIMITS.autoScrollSpeed.min}
              max={READER_LIMITS.autoScrollSpeed.max}
              step={READER_LIMITS.autoScrollSpeed.step}
              value={s.autoScrollSpeed}
              onChange={(e) => s.set({ autoScrollSpeed: Number(e.target.value) })}
              className="w-24"
            />
          )}
        </div>
      </Row>

      <button
        onClick={() => s.reset()}
        className="w-full rounded-lg border border-slate-200 py-1.5 text-xs text-slate-500 hover:bg-slate-50"
      >
        기본값으로
      </button>
    </div>
  );
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-3">
      <span className="text-xs font-medium text-slate-500">{label}</span>
      {children}
    </div>
  );
}

function Stepper({
  label,
  value,
  onDec,
  onInc,
}: {
  label: string;
  value: string;
  onDec: () => void;
  onInc: () => void;
}) {
  return (
    <Row label={label}>
      <div className="flex items-center gap-2">
        <button
          onClick={onDec}
          aria-label={`${label} 줄이기`}
          className="h-7 w-7 rounded-lg border border-slate-300 text-sm text-slate-600 hover:bg-slate-50"
        >
          −
        </button>
        <span className="w-12 text-center text-xs tabular-nums text-slate-800">{value}</span>
        <button
          onClick={onInc}
          aria-label={`${label} 늘리기`}
          className="h-7 w-7 rounded-lg border border-slate-300 text-sm text-slate-600 hover:bg-slate-50"
        >
          +
        </button>
      </div>
    </Row>
  );
}
