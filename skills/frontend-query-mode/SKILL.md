---
name: frontend-query-mode
description: >
  Use this skill whenever you are building or modifying the Query Mode UI in
  `apps/web/src/features/query/`. Triggers include: "query mode", "natural language
  question", "ask a question", "query form", "citation rendering", "query answer",
  "QueryPage", "useSubmitQuery", "504 query timeout", "query response display", or any
  task inside `features/query/`. This skill covers the complete Query Mode frontend:
  question input form, mutation hook, answer display with citation rendering, 504 timeout
  handling, and the stateless (no history) pattern. Read this before touching any part
  of the Query Mode UI.
---

# Frontend — Query Mode

Query Mode allows campaign members to ask natural language questions about the world state.
The frontend posts the question, waits synchronously for a complete response, and renders
the answer with navigable citations. There is no query history in v1 (D-058). The backend
enforces a timeout and returns `504 QUERY_TIMEOUT` if the LLM call exceeds the deadline (D-052).

## Context

**Key decisions:**
- D-052: Synchronous execution — the client waits for the complete response; no streaming; `504` on timeout
- D-058: No Q&A history in v1 — queries are stateless; if the user navigates away, the answer is gone
- D-003: Every answer claim must be attributed to a specific session

**How it works:**
1. User types a question and submits the form.
2. Frontend calls `POST /api/v1/campaigns/{id}/queries`.
3. Backend holds the connection, runs the full pipeline (embed → vector search → LLM call), and returns the complete answer.
4. Frontend renders the answer text and a citation list. Each citation is a navigable link to the referenced session.
5. On `504 QUERY_TIMEOUT`, surface a user-friendly message with a suggestion to rephrase.

**Directory structure:**

```
src/features/query/
├── QueryPage.tsx             ← container: form + answer display
├── components/
│   ├── QuestionForm.tsx      ← controlled input + submit button
│   ├── AnswerDisplay.tsx     ← renders answer text with inline citation markers
│   └── CitationList.tsx      ← list of session citations as navigable links
└── hooks/
    └── (no extra hooks — useSubmitQuery lives in src/api/queries.ts)
```

---

## TypeScript Types

```typescript
// src/types/query.ts
export interface QueryRequest {
  question: string;
}

export interface Citation {
  sessionId: string;
  sequenceNumber: number;
  claim: string;  // the specific claim this citation supports
}

export interface QueryResponse {
  answer: string;
  citations: Citation[];
}
```

---

## API Client

```typescript
// src/api/queries.ts
import { useMutation } from '@tanstack/react-query';
import { fetchWithAuth } from './client';
import type { QueryRequest, QueryResponse } from '../types/query';

export async function submitQuery(
  campaignId: string,
  question: string
): Promise<QueryResponse> {
  const response = await fetchWithAuth(
    `/api/v1/campaigns/${campaignId}/queries`,
    {
      method: 'POST',
      body: JSON.stringify({ question } satisfies QueryRequest),
    }
  );

  if (!response.ok) {
    const body = await response.json().catch(() => null);
    const errorCode = body?.errors?.[0]?.code ?? null;
    throw new QueryApiError(response.status, errorCode, body?.errors?.[0]?.message ?? 'Query failed');
  }

  const { data } = await response.json();
  return data as QueryResponse;
}

// Custom error class to carry structured error info
export class QueryApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string | null,
    message: string
  ) {
    super(message);
  }
}

export function useSubmitQuery(campaignId: string) {
  return useMutation({
    mutationFn: (question: string) => submitQuery(campaignId, question),
    // Queries are stateless (D-058) — no cache invalidation needed
  });
}
```

---

## Component: QueryPage

```tsx
// src/features/query/QueryPage.tsx
import { useState } from 'react';
import { useSubmitQuery, QueryApiError } from '../../api/queries';
import { useCampaignStore } from '../../store/campaignStore';
import { QuestionForm } from './components/QuestionForm';
import { AnswerDisplay } from './components/AnswerDisplay';
import type { QueryResponse } from '../../types/query';

export function QueryPage() {
  const campaignId = useCampaignStore(s => s.campaignId)!;
  const [lastAnswer, setLastAnswer] = useState<QueryResponse | null>(null);
  const [timeoutError, setTimeoutError] = useState(false);

  const { mutate, isPending, error } = useSubmitQuery(campaignId);

  function handleSubmit(question: string) {
    setLastAnswer(null);
    setTimeoutError(false);
    mutate(question, {
      onSuccess: (data) => setLastAnswer(data),
      onError: (err) => {
        if (err instanceof QueryApiError && err.status === 504) {
          setTimeoutError(true);
        }
        // Other errors surface via the `error` state from useMutation
      },
    });
  }

  return (
    <section aria-labelledby="query-mode-heading">
      <h1 id="query-mode-heading">Ask the World</h1>

      <QuestionForm onSubmit={handleSubmit} isPending={isPending} />

      {isPending && (
        <p aria-live="polite" aria-busy="true">
          Searching the world state…
        </p>
      )}

      {timeoutError && (
        <p role="alert" className="text-destructive">
          The query timed out. Try rephrasing your question or narrowing the scope.
        </p>
      )}

      {error && !timeoutError && (
        <p role="alert" className="text-destructive">
          Something went wrong. Please try again.
        </p>
      )}

      {lastAnswer && !isPending && (
        <AnswerDisplay response={lastAnswer} campaignId={campaignId} />
      )}
    </section>
  );
}
```

---

## Component: QuestionForm

```tsx
// src/features/query/components/QuestionForm.tsx
import { useRef } from 'react';
import { Button } from '../../../components/ui/button';
import { Textarea } from '../../../components/ui/textarea';

interface Props {
  onSubmit: (question: string) => void;
  isPending: boolean;
}

export function QuestionForm({ onSubmit, isPending }: Props) {
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    const question = textareaRef.current?.value.trim() ?? '';
    if (!question) return;
    onSubmit(question);
  }

  return (
    <form onSubmit={handleSubmit}>
      <label htmlFor="query-input" className="sr-only">
        Ask a question about the campaign
      </label>
      <Textarea
        id="query-input"
        ref={textareaRef}
        placeholder="What happened to Aldric after the Battle of Thornwall?"
        disabled={isPending}
        aria-disabled={isPending}
        rows={3}
      />
      <Button type="submit" disabled={isPending || false}>
        {isPending ? 'Searching…' : 'Ask'}
      </Button>
    </form>
  );
}
```

---

## Component: AnswerDisplay with Citations

Citations must render as navigable links to the referenced session. Each citation links to
the session detail view so users can see the original narrative.

```tsx
// src/features/query/components/AnswerDisplay.tsx
import { Link } from 'react-router-dom';
import { CitationList } from './CitationList';
import type { QueryResponse } from '../../../types/query';

interface Props {
  response: QueryResponse;
  campaignId: string;
}

export function AnswerDisplay({ response, campaignId }: Props) {
  return (
    <article aria-label="Query answer">
      <section aria-labelledby="answer-heading">
        <h2 id="answer-heading" className="sr-only">Answer</h2>
        {/* Render answer text. Do NOT use dangerouslySetInnerHTML — the answer is plain text */}
        <p className="whitespace-pre-wrap">{response.answer}</p>
      </section>

      {response.citations.length > 0 && (
        <CitationList citations={response.citations} campaignId={campaignId} />
      )}
    </article>
  );
}
```

```tsx
// src/features/query/components/CitationList.tsx
import { Link } from 'react-router-dom';
import type { Citation } from '../../../types/query';

interface Props {
  citations: Citation[];
  campaignId: string;
}

export function CitationList({ citations, campaignId }: Props) {
  return (
    <aside aria-labelledby="citations-heading">
      <h3 id="citations-heading">Sources</h3>
      <ol>
        {citations.map((c) => (
          <li key={`${c.sessionId}-${c.claim}`}>
            <Link
              to={`/campaigns/${campaignId}/sessions/${c.sessionId}`}
              aria-label={`Session ${c.sequenceNumber}: ${c.claim}`}
            >
              Session {c.sequenceNumber}
            </Link>
            {' — '}
            <span>{c.claim}</span>
          </li>
        ))}
      </ol>
    </aside>
  );
}
```

---

## Stateless Pattern (D-058)

Query Mode is stateless in v1. There is no query history, no saved answers, and no Q&A panel.

- Do NOT persist questions or answers to any store.
- Do NOT add a query history endpoint or table.
- When the user navigates away, `lastAnswer` state is lost — this is correct and expected.
- The `QueryPage` local state (`lastAnswer`, `timeoutError`) resets on each new question submission.

If users want to keep a record of an answer, they must copy it manually. This is a deliberate
v1 constraint (D-058).

---

## Role Gating

Query Mode is accessible to all campaign roles (`gm`, `editor`, `player`) and to `admin`.
There is no role gate on the Query Mode route itself. The backend enforces that the caller is
a member of the campaign on every query request.

```tsx
// In the campaign router — Query Mode is visible to all authenticated members
<Route path="query" element={<QueryPage />} />
```

---

## Accessibility Requirements

- The answer section uses `aria-live="polite"` on the loading state to announce completion
  to screen readers.
- Timeout error uses `role="alert"` for immediate announcement.
- `QuestionForm` has a visually hidden `<label>` associated with the textarea.
- `CitationList` items include descriptive `aria-label` on each link.
- Write `vitest-axe` assertions on `QueryPage`, `AnswerDisplay`, and `CitationList`.

---

## Common Pitfalls

- **Not handling `504 QUERY_TIMEOUT` as a distinct case.** A generic "something went wrong"
  message is unhelpful for a timeout. Surface a specific message with a rephrasing suggestion.

- **Rendering the answer with `dangerouslySetInnerHTML`.** The LLM answer is plain text.
  Render it with `whitespace-pre-wrap` CSS. Never set innerHTML from LLM output.

- **Expecting a streaming response.** Query Mode is synchronous in v1 (D-052). There is no
  SSE, no chunked response, and no partial render. The mutation hook waits for the full response.
  Do not add streaming UI in v1.

- **Storing citations in TanStack Query cache.** Queries are stateless — do not create a
  query key or cache the response. Use component-local state (`useState`) for the answer.

- **Not validating that `citations.length > 0` before rendering `CitationList`.** A valid
  response may have an empty `citations` array if no evidence was found in the world state.
  The UI must handle this gracefully (omit the section, do not render an empty list).

- **Conflating `isPending` and `error` states.** When a new question is submitted, clear both
  `lastAnswer` and `timeoutError` before calling `mutate`. Otherwise stale state from the
  previous query bleeds into the new one's loading experience.

## References

- `apps/web/CLAUDE.md` §6 (Query Mode concepts), §7 (state management rules)
- `ARCHITECTURE.md` §7.8 (query endpoint specification)
- `DECISIONS.md` D-003, D-052, D-058
- `query-pipeline` skill (backend counterpart — embed → vector search → LLM → citations)
- `frontend-api-resource` skill (typed API client patterns, error handling hierarchy)
- `frontend-testing` skill (component test setup, axe-core patterns)
