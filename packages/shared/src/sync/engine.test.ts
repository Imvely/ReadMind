import { describe, expect, it } from 'vitest';
import type { SyncChangesResponse, SyncHighlightDto, SyncPushRequest, SyncPushResponse } from '../index';
import {
  buildPushRequest,
  syncOnce,
  type OutboxItem,
  type ServerRecord,
  type SyncApi,
  type SyncEntity,
  type SyncStore,
} from './engine';

// ── 테스트용 인메모리 어댑터 ──

function memoryStore(outbox: OutboxItem[] = [], cursor: string | null = null) {
  const state = {
    cursor,
    outbox: [...outbox],
    upserts: [] as { entity: SyncEntity; record: ServerRecord }[],
    removes: [] as { entity: SyncEntity; serverId: number }[],
    bindings: [] as { entity: SyncEntity; clientId: string; serverId: number; version: number }[],
  };
  const store: SyncStore = {
    getCursor: async () => state.cursor,
    setCursor: async (c) => void (state.cursor = c),
    getOutbox: async () => state.outbox,
    removeFromOutbox: async (ids) => {
      state.outbox = state.outbox.filter((o) => !ids.includes(o.outboxId));
    },
    upsertServerRecord: async (entity, record) => void state.upserts.push({ entity, record }),
    removeLocalRecord: async (entity, serverId) => void state.removes.push({ entity, serverId }),
    bindServerId: async (entity, clientId, serverId, version) =>
      void state.bindings.push({ entity, clientId, serverId, version }),
  };
  return { store, state };
}

const emptyChanges = (cursor: string): SyncChangesResponse => ({
  cursor,
  changes: { highlights: [], notes: [], bookmarks: [], progress: [] },
});

function fakeApi(pushRes: SyncPushResponse | null, changesRes: SyncChangesResponse) {
  const calls = { push: [] as SyncPushRequest[], changesSince: [] as (string | null)[] };
  const api: SyncApi = {
    push: async (req) => {
      calls.push.push(req);
      if (!pushRes) throw new Error('push가 호출되면 안 됨');
      return pushRes;
    },
    changes: async (since) => {
      calls.changesSince.push(since);
      return changesRes;
    },
  };
  return { api, calls };
}

const hl = (over: Partial<SyncHighlightDto> = {}): SyncHighlightDto => ({
  id: 1,
  documentId: 7,
  pageNo: 1,
  location: { type: 'pdf', page: 1 },
  selectedText: '원문',
  color: 'yellow',
  note: null,
  tags: [],
  version: 1,
  clientUpdatedAt: null,
  updatedAt: '2026-07-05T00:00:00Z',
  deletedAt: null,
  ...over,
});

// ── 테스트 ──

describe('buildPushRequest', () => {
  it('outbox를 엔티티별로 그룹핑한다', () => {
    const req = buildPushRequest([
      { outboxId: 'o1', entity: 'highlight', payload: { documentId: 7, selectedText: 'a' } },
      { outboxId: 'o2', entity: 'note', payload: { documentId: 7, body: 'b' } },
      { outboxId: 'o3', entity: 'highlight', payload: { documentId: 7, selectedText: 'c' } },
    ]);
    expect(req.changes.highlights).toHaveLength(2);
    expect(req.changes.notes).toHaveLength(1);
    expect(req.changes.bookmarks).toBeUndefined();
  });
});

describe('syncOnce', () => {
  it('push: applied의 clientId를 서버 id로 바인딩하고 outbox를 비운다', async () => {
    const { store, state } = memoryStore([
      {
        outboxId: 'o1',
        entity: 'highlight',
        payload: { clientId: 'local-1', documentId: 7, selectedText: '새것' },
      },
    ]);
    const { api } = fakeApi(
      {
        applied: [{ entity: 'highlight', clientId: 'local-1', id: 100, version: 1 }],
        conflicts: [],
        cursor: 'c1',
      },
      emptyChanges('c2'),
    );

    const res = await syncOnce(api, store);

    expect(state.bindings).toEqual([
      { entity: 'highlight', clientId: 'local-1', serverId: 100, version: 1 },
    ]);
    expect(state.outbox).toHaveLength(0);
    expect(res.pushed).toBe(1);
  });

  it('push 충돌: 서버 상태로 로컬을 교체한다(LWW 패배)', async () => {
    const serverState = hl({ id: 55, color: 'green', version: 9 });
    const { store, state } = memoryStore([
      {
        outboxId: 'o1',
        entity: 'highlight',
        payload: { id: 55, documentId: 7, color: 'red', version: 1 },
      },
    ]);
    const { api } = fakeApi(
      {
        applied: [],
        conflicts: [{ entity: 'highlight', id: 55, server: serverState }],
        cursor: 'c1',
      },
      emptyChanges('c2'),
    );

    const res = await syncOnce(api, store);

    expect(state.upserts).toEqual([{ entity: 'highlight', record: serverState }]);
    expect(state.outbox).toHaveLength(0); // 충돌 항목도 큐에서 제거 — 무한 재전송 방지
    expect(res.conflicts).toBe(1);
  });

  it('outbox가 비어 있으면 push를 호출하지 않는다', async () => {
    const { store } = memoryStore([]);
    const { api, calls } = fakeApi(null, emptyChanges('c1'));

    await syncOnce(api, store);

    expect(calls.push).toHaveLength(0);
  });

  it('pull: cursor를 since로 보내고, tombstone은 삭제·일반은 upsert 후 cursor 갱신', async () => {
    const { store, state } = memoryStore([], 'prev-cursor');
    const { api, calls } = fakeApi(null, {
      cursor: 'next-cursor',
      changes: {
        highlights: [hl({ id: 1 }), hl({ id: 2, deletedAt: '2026-07-05T01:00:00Z' })],
        notes: [],
        bookmarks: [],
        progress: [
          {
            documentId: 7,
            location: {},
            percent: 42,
            clientUpdatedAt: null,
            updatedAt: '2026-07-05T00:00:00Z',
          },
        ],
      },
    });

    const res = await syncOnce(api, store);

    expect(calls.changesSince).toEqual(['prev-cursor']);
    expect(state.upserts.map((u) => u.entity)).toEqual(['highlight', 'progress']);
    expect(state.removes).toEqual([{ entity: 'highlight', serverId: 2 }]);
    expect(state.cursor).toBe('next-cursor');
    expect(res).toMatchObject({ pulled: 2, removed: 1, cursor: 'next-cursor' });
  });

  it('첫 동기화(cursor 없음)는 since=null로 전체를 받는다', async () => {
    const { store } = memoryStore([]);
    const { api, calls } = fakeApi(null, emptyChanges('c1'));

    await syncOnce(api, store);

    expect(calls.changesSince).toEqual([null]);
  });
});
