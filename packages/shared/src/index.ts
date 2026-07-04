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
  /** 읽기 진행률 % — 목록에서만 채워짐, 없으면 null (§4.2 P1.5 이어읽기). */
  progressPercent?: number | null;
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

// ── 주석/진행률 (§4.3) ──
/** 리더 위치 — 포맷 무관(§3): PDF={type:'pdf',page,scrollRatio?} / EPUB={type:'epub',cfi}. */
export interface UpdateProgressRequest {
  location: unknown;
  percent: number;
}
export interface ProgressDto {
  documentId: number;
  location: unknown;
  percent: number;
  updatedAt: string | null;
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

/** Q&A 대화 이력 한 줄 (§4.4 GET qa/history). ASSISTANT만 sources를 가진다. */
export interface QaHistoryMessageDto {
  role: 'USER' | 'ASSISTANT';
  content: string;
  sources?: QaSourceDto[] | null;
  createdAt?: string | null;
}
/** 최근 세션 대화 이력. 대화가 없으면 sessionId=null, messages=[]. */
export interface QaHistoryResponse {
  sessionId: number | null;
  messages: QaHistoryMessageDto[];
}

// ══════════ 동기화 (§4.5) — 웹·안드 공유 계약(§6) ══════════
/** 서버 상태 한 건. tombstone은 deletedAt non-null — 클라는 로컬에서 제거한다. */
export interface SyncHighlightDto {
  id: number;
  documentId: number;
  pageNo: number | null;
  location: unknown;
  selectedText: string;
  color: string;
  note: string | null;
  tags: string[];
  version: number;
  clientUpdatedAt: string | null;
  updatedAt: string | null;
  deletedAt: string | null;
}
export interface SyncNoteDto {
  id: number;
  documentId: number;
  pageNo: number | null;
  body: string;
  version: number;
  clientUpdatedAt: string | null;
  updatedAt: string | null;
  deletedAt: string | null;
}
export interface SyncBookmarkDto {
  id: number;
  documentId: number;
  location: unknown;
  label: string | null;
  version: number;
  clientUpdatedAt: string | null;
  updatedAt: string | null;
  deletedAt: string | null;
}
/** progress는 version/tombstone 없음(§4.5 예외) — clientUpdatedAt LWW만. */
export interface SyncProgressDto {
  documentId: number;
  location: unknown;
  percent: number;
  clientUpdatedAt: string | null;
  updatedAt: string | null;
}
export interface SyncChanges {
  highlights: SyncHighlightDto[];
  notes: SyncNoteDto[];
  bookmarks: SyncBookmarkDto[];
  progress: SyncProgressDto[];
}
export interface SyncChangesResponse {
  /** 다음 /sync/changes 호출의 since로 쓸 서버 시각(ISO). */
  cursor: string;
  changes: SyncChanges;
}

/** push 입력 — 신규는 id 없이 clientId(로컬 uuid)로 보내고 applied 매핑으로 서버 id를 받는다. */
export interface PushHighlight {
  clientId?: string;
  id?: number;
  documentId: number;
  pageNo?: number | null;
  location?: unknown;
  selectedText?: string;
  color?: string;
  note?: string | null;
  tags?: string[];
  version?: number;
  clientUpdatedAt?: string;
  deleted?: boolean;
}
export interface PushNote {
  clientId?: string;
  id?: number;
  documentId: number;
  pageNo?: number | null;
  body?: string;
  version?: number;
  clientUpdatedAt?: string;
  deleted?: boolean;
}
export interface PushBookmark {
  clientId?: string;
  id?: number;
  documentId: number;
  location?: unknown;
  label?: string | null;
  version?: number;
  clientUpdatedAt?: string;
  deleted?: boolean;
}
export interface PushProgress {
  documentId: number;
  location: unknown;
  percent: number;
  clientUpdatedAt?: string;
}
export interface SyncPushRequest {
  changes: {
    highlights?: PushHighlight[];
    notes?: PushNote[];
    bookmarks?: PushBookmark[];
    progress?: PushProgress[];
  };
}
export interface SyncAppliedDto {
  entity: 'highlight' | 'note' | 'bookmark' | 'progress';
  clientId: string | null;
  id: number;
  version: number;
}
export interface SyncConflictDto {
  entity: 'highlight' | 'note' | 'bookmark';
  id: number;
  /** 서버 현재 상태 — 클라이언트가 로컬을 이 값으로 교체한다. */
  server: SyncHighlightDto | SyncNoteDto | SyncBookmarkDto;
}
export interface SyncPushResponse {
  applied: SyncAppliedDto[];
  conflicts: SyncConflictDto[];
  cursor: string;
}

// 동기화 엔진(§4.5 클라이언트 로직) — 웹·안드가 저장소/전송 어댑터만 구현해 공유한다(§6).
export * from './sync/engine';
