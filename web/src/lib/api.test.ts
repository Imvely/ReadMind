import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiClientError, apiRequest } from './api';
import { tokens } from './tokens';

/** fetch 응답 하나를 흉내내는 헬퍼. body는 공통 래퍼 JSON. */
function jsonResponse(status: number, body: unknown): Response {
  return {
    status,
    ok: status >= 200 && status < 300,
    text: async () => JSON.stringify(body),
  } as Response;
}

describe('apiRequest', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });
  afterEach(() => {
    localStorage.clear();
  });

  it('성공 응답이면 공통 래퍼를 벗겨 data만 반환한다', async () => {
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse(200, { success: true, data: { answer: 'hi' } }));

    const data = await apiRequest<{ answer: string }>('/documents/1/qa', { method: 'POST' });

    expect(data).toEqual({ answer: 'hi' });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('access 토큰이 있으면 Authorization 헤더를 붙인다', async () => {
    tokens.setAccess('tok-123');
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse(200, { success: true, data: {} }));

    await apiRequest('/auth/me');

    const init = fetchMock.mock.calls[0][1] as RequestInit;
    expect((init.headers as Record<string, string>).Authorization).toBe('Bearer tok-123');
  });

  it('실패 응답은 error.code를 담은 ApiClientError로 던진다', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      jsonResponse(400, { success: false, error: { code: 'VALIDATION', message: '잘못된 요청' } }),
    );

    await expect(apiRequest('/documents')).rejects.toMatchObject({
      code: 'VALIDATION',
      status: 400,
      message: '잘못된 요청',
    });
  });

  it('쿼터 초과(403)는 QUOTA_EXCEEDED code로 전달된다', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      jsonResponse(403, {
        success: false,
        error: { code: 'QUOTA_EXCEEDED', message: '한도 초과' },
      }),
    );

    const err = await apiRequest('/documents/1/qa', { method: 'POST' }).catch((e) => e);
    expect(err).toBeInstanceOf(ApiClientError);
    expect(err.code).toBe('QUOTA_EXCEEDED');
    expect(err.status).toBe(403);
  });

  it('401이면 refresh로 새 토큰을 받고 원 요청을 1회 재시도한다', async () => {
    tokens.set('old-access', 'refresh-tok');
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      // 1) 최초 호출 → 401
      .mockResolvedValueOnce(
        jsonResponse(401, { success: false, error: { code: 'UNAUTHORIZED', message: '만료' } }),
      )
      // 2) /auth/refresh → 새 access
      .mockResolvedValueOnce(
        jsonResponse(200, { success: true, data: { accessToken: 'new-access' } }),
      )
      // 3) 원 요청 재시도 → 성공
      .mockResolvedValueOnce(jsonResponse(200, { success: true, data: { ok: true } }));

    const data = await apiRequest<{ ok: boolean }>('/documents');

    expect(data).toEqual({ ok: true });
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(tokens.getAccess()).toBe('new-access'); // 갱신된 토큰 저장 확인
    // 재시도 요청은 새 토큰을 사용해야 한다.
    const retryInit = fetchMock.mock.calls[2][1] as RequestInit;
    expect((retryInit.headers as Record<string, string>).Authorization).toBe('Bearer new-access');
  });

  it('refresh 토큰이 없으면 401에서 재시도하지 않고 토큰을 비운다', async () => {
    tokens.setAccess('old-access'); // refresh 토큰은 없음
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(
        jsonResponse(401, { success: false, error: { code: 'UNAUTHORIZED', message: '만료' } }),
      );

    await expect(apiRequest('/documents')).rejects.toMatchObject({ status: 401 });
    expect(fetchMock).toHaveBeenCalledTimes(1); // 재시도 없음
  });

  it('noAuthRetry면 401에도 refresh를 시도하지 않는다(로그인/refresh 호출용)', async () => {
    tokens.set('old-access', 'refresh-tok');
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(
        jsonResponse(401, { success: false, error: { code: 'INVALID_CREDENTIALS', message: '실패' } }),
      );

    await expect(
      apiRequest('/auth/login', { method: 'POST', noAuthRetry: true }),
    ).rejects.toMatchObject({ code: 'INVALID_CREDENTIALS' });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
