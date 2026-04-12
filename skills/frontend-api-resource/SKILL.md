---
name: frontend-api-resource
description: >
  Use this skill whenever you are adding or modifying any API-related code in `apps/web`:
  typed HTTP client functions, TanStack Query hooks, TypeScript DTO types, or any connection
  between a frontend component and a backend endpoint. Triggers include: "connect component to
  API", "add a typed client", "add a useQuery hook", "add a useMutation hook", "fetch [resource]
  from the backend", "update DTO types", "API client for [endpoint]", or any task that touches
  `src/api/`, `src/types/`, or server state management. This is the primary reference for all
  frontend data-fetching work.
---

# Frontend — Adding a Typed API Resource

Every backend endpoint consumed by the frontend has a corresponding typed client file in
`src/api/`. This file contains the fetch function, query key factory, and exported TanStack Query
hooks. TypeScript types mirroring the backend DTOs live in `src/types/`. There is no code
generation in v1 — types are hand-maintained (D-030).

## Context

**Rules (apps/web/CLAUDE.md §7):**

- Server state is managed exclusively by TanStack Query. Components never call `fetch` directly —
  they use hooks exported from `src/api/`.
- Types in `src/types/` are hand-written mirrors of backend DTOs. When a backend DTO changes,
  the corresponding frontend type must be updated in the same PR (no codegen drift).
- The JWT access token is stored in memory (Zustand auth store) — never `localStorage` (D-030).
  Every API request attaches it as `Authorization: Bearer <token>`.
- On `401`, the client silently refreshes once (POST /auth/refresh), then retries. If the retry
  also returns `401`, redirect to login. Do not surface `401` errors to users.
- All IDs are UUIDs (`string` in TypeScript). All timestamps are ISO 8601 UTC strings — parse
  to display only at the render boundary.

**Directory structure:**

```
src/
├── api/
│   ├── sessions.ts      ← session ingestion + status polling + diff + commit
│   ├── actors.ts        ← actor list + actor detail
│   ├── queries.ts       ← POST /campaigns/{id}/queries
│   ├── auth.ts          ← login, refresh, logout
│   └── campaigns.ts     ← campaign CRUD + membership
├── types/
│   ├── session.ts       ← Session, SessionStatus, DiffPayload, CommitPayload
│   ├── actor.ts         ← Actor, ActorVersion
│   ├── query.ts         ← QueryRequest, QueryResponse, Citation
│   └── ...
```

**Response envelope** — all backend responses follow:

```typescript
interface ApiResponse<T> {
  data: T;
  meta: PaginationMeta | null;
  errors: ApiError[];
}

interface ApiError {
  code: string;
  message: string;
  field: string | null;
}
```

## Workflow

### 1. Define the TypeScript types

Add or update the relevant file in `src/types/`. Types mirror the backend DTO shapes.

```typescript
// src/types/session.ts
export type SessionStatus = 'pending' | 'processing' | 'draft' | 'committed' | 'failed' | 'discarded';

export interface Session {
  id: string;        // UUID
  campaignId: string;
  sequenceNumber: number;
  status: SessionStatus;
  committedAt: string | null;  // ISO 8601 UTC
  createdAt: string;
}

export interface SessionStatusResponse {
  sessionId: string;
  status: SessionStatus;
  failureReason: 'EXTRACTION_FAILED' | 'BUDGET_EXCEEDED' | 'INTERNAL_ERROR' | null;
  message: string | null;
}
```

- Do not use `any`. Use `unknown` if the shape is genuinely unknown, then narrow with type guards.
- Use `string` for UUIDs — do not use a branded `UUID` type unless the whole codebase does.
- Use `string` for ISO 8601 timestamps — parse at display time only.
- Union types for status fields (not plain `string`).

### 2. Create or extend the API client file

One file per API resource in `src/api/`. Each file exports:
1. **Raw fetch functions** — typed async functions that call the backend
2. **Query key factory** — for TanStack Query cache management (GET endpoints only)
3. **TanStack Query hooks** — `useQuery` / `useMutation` wrappers

```typescript
// src/api/sessions.ts
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import type { Session, SessionStatusResponse } from '../types/session';
import { apiClient } from './client';  // shared fetch wrapper with auth header

// ─── Query keys ────────────────────────────────────────────────────────────
export const sessionKeys = {
  all: (campaignId: string) => ['campaigns', campaignId, 'sessions'] as const,
  detail: (campaignId: string, sessionId: string) =>
    [...sessionKeys.all(campaignId), sessionId] as const,
  status: (campaignId: string, sessionId: string) =>
    [...sessionKeys.detail(campaignId, sessionId), 'status'] as const,
  diff: (campaignId: string, sessionId: string) =>
    [...sessionKeys.detail(campaignId, sessionId), 'diff'] as const,
};

// ─── Raw fetch functions ────────────────────────────────────────────────────
export async function submitSession(
  campaignId: string,
  summaryText: string
): Promise<{ sessionId: string; status: SessionStatus }> {
  const response = await apiClient.post(
    `/api/v1/campaigns/${campaignId}/sessions`,
    { summary_text: summaryText }
  );
  return response.data;
}

export async function getSessionStatus(
  campaignId: string,
  sessionId: string
): Promise<SessionStatusResponse> {
  const response = await apiClient.get(
    `/api/v1/campaigns/${campaignId}/sessions/${sessionId}/status`
  );
  return response.data;
}

// ─── TanStack Query hooks ───────────────────────────────────────────────────
export function useSessionStatus(campaignId: string, sessionId: string, enabled = true) {
  return useQuery({
    queryKey: sessionKeys.status(campaignId, sessionId),
    queryFn: () => getSessionStatus(campaignId, sessionId),
    enabled,
    refetchInterval: (query) => {
      // Poll every 2s while processing; stop when terminal status reached
      const status = query.state.data?.status;
      return status === 'processing' ? 2000 : false;
    },
  });
}

export function useSubmitSession(campaignId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (summaryText: string) => submitSession(campaignId, summaryText),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: sessionKeys.all(campaignId) });
    },
  });
}
```

### 3. Build the shared `apiClient` wrapper

A single fetch wrapper handles auth headers and token refresh. This wrapper is consumed by all
`src/api/` files — components never call `fetch` directly.

```typescript
// src/api/client.ts
import { useAuthStore } from '../store/authStore';

async function fetchWithAuth(url: string, options: RequestInit = {}): Promise<Response> {
  const token = useAuthStore.getState().accessToken;
  const response = await fetch(url, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
  });

  if (response.status === 401) {
    // Attempt silent refresh once
    const refreshed = await attemptTokenRefresh();
    if (!refreshed) {
      useAuthStore.getState().logout();
      window.location.href = '/login';
      throw new Error('Session expired');
    }
    // Retry the original request with new token
    return fetchWithAuth(url, options);
  }

  return response;
}
```

Extend `apiClient` with typed helpers: `apiClient.get<T>()`, `apiClient.post<T>()`, etc., that
parse the response envelope and throw on `errors`.

### 4. Handle loading, error, and empty states

Components must explicitly handle all three states. Never leave them implicit.

```tsx
function SessionStatusPoller({ campaignId, sessionId }) {
  const { data, isLoading, error } = useSessionStatus(campaignId, sessionId);

  if (isLoading) return <LoadingSpinner />;
  if (error) return <ErrorMessage message="Failed to check session status" />;
  if (!data) return null;

  if (data.status === 'failed') {
    return <FailedSessionMessage reason={data.failureReason} message={data.message} />;
  }

  return <StatusBadge status={data.status} />;
}
```

### 5. Invalidate TanStack Query cache after mutations

After any mutation that changes server state, invalidate the relevant query keys. This ensures
connected components re-fetch without manual refresh.

```typescript
onSuccess: (newSession) => {
  // Invalidate session list for this campaign
  queryClient.invalidateQueries({ queryKey: sessionKeys.all(campaignId) });
  // Pre-populate the new session in the cache
  queryClient.setQueryData(sessionKeys.detail(campaignId, newSession.id), newSession);
}
```

## Patterns & Conventions

**One file per API resource.** `actors.ts`, `sessions.ts`, `queries.ts`, `campaigns.ts`. Do not
create `index.ts` aggregators that re-export everything from all files.

**Query key factories always in the same file as the hooks that use them.** This keeps cache
invalidation logic co-located with the fetching logic.

**Error handling hierarchy:**
1. Network errors — handle in `apiClient` wrapper, surface as user-friendly message
2. `4xx` with `errors[]` — parse `code` for machine-readable handling; display `message`
3. `422` — likely a UI bug for UNCERTAIN or conflict validation errors; log to console at ERROR
4. `504 QUERY_TIMEOUT` — user-facing: "Query timed out, try rephrasing" (D-052)
5. `400 SUMMARY_TOO_LARGE` — user-facing: show `max_tokens` and suggest splitting summary

**Mutation loading states:** Always disable submit buttons while a mutation is in-flight:

```tsx
const { mutate, isPending } = useSubmitSession(campaignId);
<Button onClick={() => mutate(text)} disabled={isPending}>Submit</Button>
```

**Never put server data in Zustand.** TanStack Query is the cache. Zustand stores client state
(auth token, active campaign context, UI flags). If a component needs data from the server, it
uses a TanStack Query hook.

## Examples

**Adding the `POST /campaigns/{id}/queries` endpoint:**

```typescript
// src/types/query.ts
export interface QueryRequest { question: string; }
export interface Citation { sessionId: string; sequenceNumber: number; claim: string; }
export interface QueryResponse { answer: string; citations: Citation[]; }

// src/api/queries.ts
export function useSubmitQuery(campaignId: string) {
  return useMutation({
    mutationFn: async (question: string): Promise<QueryResponse> => {
      const res = await apiClient.post(`/api/v1/campaigns/${campaignId}/queries`, { question });
      return res.data;
    },
    // No cache invalidation needed — queries are stateless (D-058)
  });
}
```

## Common Pitfalls

- **Storing the JWT access token in `localStorage`.** It must live in the Zustand auth store
  (in-memory). `localStorage` persists across browser sessions and is accessible via JS — a
  security risk for auth tokens.

- **Calling `fetch` directly in a component.** All fetching goes through `src/api/` hooks. A
  component that calls `fetch` directly bypasses auth headers, error handling, and cache.

- **Not updating `src/types/` when a backend DTO changes.** TypeScript will catch drift at
  compile time — but only if you run `tsc`. Update types in the same PR as the backend DTO change.

- **Using the same pagination strategy for Timeline and entity lists.** Timeline uses keyset
  (cursor-based) pagination; actors/spaces/events/relations use offset (D-055). Implement
  separate hooks with different parameter shapes.

- **Not polling for session status.** `POST /sessions` returns `{ sessionId, status: 'processing' }`.
  The diff is not in that response. The client must poll `GET /sessions/{id}/status` until
  `status === 'draft'` or `status === 'failed'`. See `useSessionStatus` hook above.

- **Surfacing `401` directly to the user.** A `401` triggers a silent token refresh attempt.
  Only redirect to login if the refresh also fails. Raw `401` errors are a session management
  concern, not a user error.

## References

- `apps/web/CLAUDE.md` §5 (architecture overview), §7 (all frontend rules), §9 (common workflows)
- `apps/web/CLAUDE.md` §10 (frontend gotchas)
- `ARCHITECTURE.md` §7 (full API endpoint catalogue and response shapes)
- `DECISIONS.md` D-030 (frontend stack), D-052 (query timeout), D-055 (pagination strategy),
  D-058 (no Q&A log)
