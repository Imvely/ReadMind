import type { ApiResponse, RefreshResponse } from '@readmind/shared';
import { tokens } from './tokens';

export const API_BASE = '/api/v1';

/** 백엔드 공통 래퍼의 error를 그대로 들고 다니는 예외. UI는 code로 분기(QUOTA_EXCEEDED 등). */
export class ApiClientError extends Error {
  constructor(
    readonly code: string,
    message: string,
    readonly status: number,
  ) {
    super(message);
    this.name = 'ApiClientError';
  }
}

interface RequestOptions {
  method?: string;
  body?: unknown;
  /** true면 401에도 refresh를 시도하지 않는다(refresh 자신 / 로그인 호출용). */
  noAuthRetry?: boolean;
}

async function rawRequest<T>(path: string, opts: RequestOptions): Promise<T> {
  const access = tokens.getAccess();
  const res = await fetch(`${API_BASE}${path}`, {
    method: opts.method ?? 'GET',
    headers: {
      'Content-Type': 'application/json',
      ...(access ? { Authorization: `Bearer ${access}` } : {}),
    },
    body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
  });

  // 본문이 없을 수 있는 응답(예: 204)도 안전하게 처리.
  const text = await res.text();
  const parsed = text ? (JSON.parse(text) as ApiResponse<T>) : null;

  if (parsed && parsed.success) return parsed.data;

  const code = parsed && !parsed.success ? parsed.error.code : 'INTERNAL';
  const message =
    parsed && !parsed.success ? parsed.error.message : `요청 실패 (HTTP ${res.status})`;
  throw new ApiClientError(code, message, res.status);
}

/** access 토큰으로 refresh 한 번 시도. 실패하면 토큰을 비우고 false. */
async function tryRefresh(): Promise<boolean> {
  const refreshToken = tokens.getRefresh();
  if (!refreshToken) return false;
  try {
    const data = await rawRequest<RefreshResponse>('/auth/refresh', {
      method: 'POST',
      body: { refreshToken },
      noAuthRetry: true,
    });
    tokens.setAccess(data.accessToken);
    return true;
  } catch {
    tokens.clear();
    return false;
  }
}

/**
 * 인증 포함 API 호출. 401이면 refresh 후 1회 재시도(noAuthRetry면 생략).
 * 성공 시 data를 반환, 실패 시 ApiClientError를 throw.
 */
export async function apiRequest<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  try {
    return await rawRequest<T>(path, opts);
  } catch (err) {
    if (
      err instanceof ApiClientError &&
      err.status === 401 &&
      !opts.noAuthRetry &&
      (await tryRefresh())
    ) {
      return rawRequest<T>(path, opts);
    }
    throw err;
  }
}

/** S3 presigned URL로 파일을 직접 PUT 업로드(공통 래퍼 없음, 백엔드 경유 안 함). */
export async function uploadToPresignedUrl(url: string, file: File): Promise<void> {
  const res = await fetch(url, {
    method: 'PUT',
    headers: { 'Content-Type': file.type || 'application/octet-stream' },
    body: file,
  });
  if (!res.ok) {
    throw new Error(`파일 업로드 실패 (HTTP ${res.status})`);
  }
}
