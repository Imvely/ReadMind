/**
 * 증분 동기화 엔진 (명세서 §4.5, CLAUDE.md §6 — 웹·안드 공유).
 *
 * 플랫폼 무관 순수 로직: 저장소(SyncStore)와 전송(SyncApi)은 어댑터로 주입받는다.
 *   웹 = IndexedDB/localStorage + fetch, RN = MMKV/SQLite + fetch.
 *
 * 흐름(syncOnce):
 *   1) push — outbox(로컬 변경 큐)를 배치 전송.
 *      applied: 신규 항목의 clientId ↔ 서버 id 바인딩. conflicts: 서버 상태로 로컬 교체(LWW 패배).
 *      처리된 항목은 outbox에서 제거.
 *   2) pull — changes?since=cursor. tombstone(deletedAt)은 로컬 삭제, 그 외 upsert. cursor 저장.
 *      방금 push한 항목도 다시 내려오지만 upsert가 멱등이라 서버 정본으로 수렴할 뿐 무해하다.
 */

import type {
  PushBookmark,
  PushHighlight,
  PushNote,
  PushProgress,
  SyncBookmarkDto,
  SyncChangesResponse,
  SyncHighlightDto,
  SyncNoteDto,
  SyncProgressDto,
  SyncPushRequest,
  SyncPushResponse,
} from '../index';

export type SyncEntity = 'highlight' | 'note' | 'bookmark' | 'progress';

export type OutboxPayload = PushHighlight | PushNote | PushBookmark | PushProgress;

/** 로컬 변경 큐 항목 — outboxId는 큐 관리용 로컬 uuid. */
export interface OutboxItem {
  outboxId: string;
  entity: SyncEntity;
  payload: OutboxPayload;
}

export type ServerRecord = SyncHighlightDto | SyncNoteDto | SyncBookmarkDto | SyncProgressDto;

/** 저장소 계약 — 플랫폼 어댑터가 구현한다. 엔진은 저장 방식을 모른다. */
export interface SyncStore {
  getCursor(): Promise<string | null>;
  setCursor(cursor: string): Promise<void>;
  getOutbox(): Promise<OutboxItem[]>;
  removeFromOutbox(outboxIds: string[]): Promise<void>;
  /** 서버 상태를 로컬에 반영(생성 또는 갱신). 멱등이어야 한다. */
  upsertServerRecord(entity: SyncEntity, record: ServerRecord): Promise<void>;
  /** tombstone 수신 시 로컬에서 제거. progress에는 호출되지 않는다. */
  removeLocalRecord(entity: SyncEntity, serverId: number): Promise<void>;
  /** 신규 push 성공 시 로컬 임시 id(clientId)를 서버 id·version으로 치환. */
  bindServerId(entity: SyncEntity, clientId: string, serverId: number, version: number): Promise<void>;
}

/** 전송 계약 — 인증/베이스URL은 어댑터 책임. */
export interface SyncApi {
  push(req: SyncPushRequest): Promise<SyncPushResponse>;
  changes(since: string | null): Promise<SyncChangesResponse>;
}

export interface SyncResult {
  pushed: number;
  conflicts: number;
  pulled: number;
  removed: number;
  cursor: string;
}

/** outbox를 §4.5 push 요청 형태로 그룹핑한다. */
export function buildPushRequest(outbox: OutboxItem[]): SyncPushRequest {
  const changes: Required<SyncPushRequest>['changes'] = {};
  for (const item of outbox) {
    switch (item.entity) {
      case 'highlight':
        (changes.highlights ??= []).push(item.payload as PushHighlight);
        break;
      case 'note':
        (changes.notes ??= []).push(item.payload as PushNote);
        break;
      case 'bookmark':
        (changes.bookmarks ??= []).push(item.payload as PushBookmark);
        break;
      case 'progress':
        (changes.progress ??= []).push(item.payload as PushProgress);
        break;
    }
  }
  return { changes };
}

/** 1회 동기화: push(outbox) → pull(changes). 실패 시 예외 전파 — 호출측이 재시도 정책 결정. */
export async function syncOnce(api: SyncApi, store: SyncStore): Promise<SyncResult> {
  let pushed = 0;
  let conflicts = 0;

  // 1) push
  const outbox = await store.getOutbox();
  if (outbox.length > 0) {
    const res = await api.push(buildPushRequest(outbox));
    for (const a of res.applied) {
      if (a.clientId) {
        await store.bindServerId(a.entity, a.clientId, a.id, a.version);
      }
      pushed += 1;
    }
    for (const c of res.conflicts) {
      // LWW 패배 — 로컬을 서버 상태로 교체. 사용자 변경은 유실되지만 계약(§4.5)대로 최신이 이긴다.
      await store.upsertServerRecord(c.entity, c.server);
      conflicts += 1;
    }
    // applied/conflict 모두 큐에서 제거 — 남기면 무한 재전송된다.
    await store.removeFromOutbox(outbox.map((o) => o.outboxId));
  }

  // 2) pull
  const since = await store.getCursor();
  const res = await api.changes(since);
  let pulled = 0;
  let removed = 0;

  const apply = async (entity: SyncEntity, records: ServerRecord[]) => {
    for (const r of records) {
      const deletedAt = (r as { deletedAt?: string | null }).deletedAt;
      if (deletedAt) {
        await store.removeLocalRecord(entity, (r as { id: number }).id);
        removed += 1;
      } else {
        await store.upsertServerRecord(entity, r);
        pulled += 1;
      }
    }
  };
  await apply('highlight', res.changes.highlights);
  await apply('note', res.changes.notes);
  await apply('bookmark', res.changes.bookmarks);
  await apply('progress', res.changes.progress); // progress엔 tombstone 없음(§4.5)

  await store.setCursor(res.cursor);
  return { pushed, conflicts, pulled, removed, cursor: res.cursor };
}
