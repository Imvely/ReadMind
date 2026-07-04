import { describe, expect, it } from 'vitest';
import { uploadFlowFormat } from './documents';

function fakeFile(name: string, type = ''): File {
  return new File(['x'], name, { type });
}

describe('uploadFlowFormat', () => {
  it('PDF와 EPUB을 포맷 코드로 매핑한다 (MIME 또는 확장자)', () => {
    expect(uploadFlowFormat(fakeFile('paper.pdf', 'application/pdf'))).toBe('PDF');
    expect(uploadFlowFormat(fakeFile('BOOK.EPUB'))).toBe('EPUB');
    expect(uploadFlowFormat(fakeFile('novel.epub', 'application/epub+zip'))).toBe('EPUB');
  });

  it('뷰어가 없는 포맷은 거부한다', () => {
    expect(() => uploadFlowFormat(fakeFile('notes.docx'))).toThrow(/PDF와 EPUB/);
    expect(() => uploadFlowFormat(fakeFile('memo.hwp'))).toThrow();
  });
});
