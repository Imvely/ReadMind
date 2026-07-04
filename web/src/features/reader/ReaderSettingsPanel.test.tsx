import { beforeEach, describe, expect, it } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { useReaderSettings } from '@/store/readerSettings';
import ReaderSettingsPanel from './ReaderSettingsPanel';

describe('ReaderSettingsPanel', () => {
  beforeEach(() => useReaderSettings.getState().reset());

  it('테마 선택이 스토어에 반영된다', () => {
    render(<ReaderSettingsPanel />);
    fireEvent.click(screen.getByRole('button', { name: '세피아' }));
    expect(useReaderSettings.getState().theme).toBe('sepia');
    expect(screen.getByRole('button', { name: '세피아' })).toHaveAttribute('aria-pressed', 'true');
  });

  it('본문 크기 스텝퍼가 배율을 조절한다', () => {
    render(<ReaderSettingsPanel />);
    fireEvent.click(screen.getByRole('button', { name: '본문 크기 늘리기' }));
    expect(useReaderSettings.getState().fontScale).toBeCloseTo(1.1);
    expect(screen.getByText('110%')).toBeInTheDocument();
  });

  it('2단 선택과 자동 스크롤 토글(속도 슬라이더 노출)이 동작한다', () => {
    render(<ReaderSettingsPanel />);
    fireEvent.click(screen.getByRole('button', { name: '2단' }));
    expect(useReaderSettings.getState().columns).toBe(2);

    expect(screen.queryByLabelText('자동 스크롤 속도')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '꺼짐' }));
    expect(useReaderSettings.getState().autoScroll).toBe(true);
    fireEvent.change(screen.getByLabelText('자동 스크롤 속도'), { target: { value: '120' } });
    expect(useReaderSettings.getState().autoScrollSpeed).toBe(120);
  });

  it('기본값으로 버튼이 전체를 리셋한다', () => {
    useReaderSettings.getState().set({ theme: 'dark', columns: 2 });
    render(<ReaderSettingsPanel />);
    fireEvent.click(screen.getByRole('button', { name: '기본값으로' }));
    expect(useReaderSettings.getState().theme).toBe('light');
    expect(useReaderSettings.getState().columns).toBe(1);
  });
});
