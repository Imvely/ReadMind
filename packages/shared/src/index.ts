/**
 * ReadMind API 계약 타입 — 웹/안드/백엔드 공유 SSOT (CLAUDE.md §3, 명세서 §4).
 * 백엔드 Kotlin DTO(JSON camelCase)와 1:1 대응. 여기서만 정의하고 클라이언트는 중복 정의하지 않는다.
 */

// ── 공통 래퍼 (명세서 §4) ──
export interface ApiError {
  code: string;
  message: string;
}
export type ApiResponse<T> =
  | { success: true; data: T }
  | { success: false; error: ApiError };

/** 백엔드 ErrorCode enum과 대응 (명세서 §4 에러 규약). */
export type ErrorCode =
  | 'VALIDATION'
  | 'UNAUTHORIZED'
  | 'INVALID_CREDENTIALS'
  | 'TOKEN_INVALID'
  | 'EMAIL_EXISTS'
  | 'NOT_FOUND'
  | 'FORBIDDEN'
  | 'QUOTA_EXCEEDED'
  | 'INTERNAL';

// ── 인증 (§4.1) ──
export interface SignupRequest {
  email: string;
  password: string;
  displayName?: string | null;
}
export interface LoginRequest {
  email: string;
  password: string;
}
export interface RefreshRequest {
  refreshToken: string;
}
export interface SignupResponse {
  userId: number;
}
export interface UserDto {
  id: number;
  email: string;
  displayName: string | null;
  tier: string;
}
export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  user: UserDto;
}
export interface RefreshResponse {
  accessToken: string;
}
export interface QuotaInfo {
  summaryUsed: number;
  summaryLimit: number | null;
  qaUsed: number;
  qaLimit: number | null;
  translateUsed: number;
  translateLimit: number | null;
  storageLimitMb: number | null;
}
export interface MeResponse {
  id: number;
  email: string;
  displayName: string | null;
  tier: string;
  quota: QuotaInfo;
}

// ── 문서 (§4.2) ──
export type ParseStatus = 'PENDING' | 'PARSING' | 'READY' | 'FAILED';

export interface CreateDocumentRequest {
  title: string;
  format: string;
  fileSize: number;
}
export interface CreateDocumentResponse {
  documentId: number;
  uploadUrl: string;
}
export interface CompleteResponse {
  documentId: number;
  parseStatus: ParseStatus;
}
export interface DocumentDto {
  id: number;
  title: string;
  format: string;
  fileSize: number;
  pageCount: number | null;
  language: string | null;
  parseStatus: ParseStatus;
  /** FAILED일 때만 내려오는 실패 사유 (백엔드는 null 필드 생략). */
  parseError?: string | null;
  createdAt: string | null;
}
export interface DocumentListResponse {
  items: DocumentDto[];
  totalElements: number;
  hasNext: boolean;
}
export interface DocumentContentResponse {
  url: string;
}

// ── AI 위임 (§4.4) ──
export type SummaryStyle = 'PAPER' | 'PLAIN';

export interface SummarizeRequest {
  scope?: string;
  scopeRef?: unknown;
  style?: SummaryStyle;
}
/** content는 AI가 생성한 구조화 요약 JSON(스키마는 §5.3, AI 소유) — 자유형. */
export interface SummarizeResponse {
  summaryId: number;
  scope: string;
  style: string;
  content: PaperSummaryContent | PlainSummaryContent | Record<string, unknown>;
  cached: boolean;
}
/** §5.3 PAPER 요약 스키마(참고용 — content가 이 형태로 옴). */
export interface PaperSummaryContent {
  tldr: string;
  structure: {
    objective: string;
    method: string;
    results: string;
    limitations: string;
    contribution: string;
  };
  keypoints: string[];
  glossary: { term: string; desc: string }[];
}
export interface PlainSummaryContent {
  tldr: string;
  keypoints: string[];
}

export interface QaRequest {
  sessionId?: number | null;
  question: string;
}
/** 근거 한 건 (§4.4 sources:[{page,snippet}]). 근거 없는 답변은 없다(§3). */
export interface QaSourceDto {
  page: number | null;
  snippet: string;
}
export interface QaResponse {
  sessionId: number;
  answer: string;
  sources: QaSourceDto[];
}
