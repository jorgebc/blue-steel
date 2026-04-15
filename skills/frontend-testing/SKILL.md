---
name: frontend-testing
description: >
  Use this skill whenever you are writing, running, or debugging tests in `apps/web`.
  Triggers include: "write a frontend test", "test this component", "add a Vitest test",
  "React Testing Library", "axe-core", "vitest-axe", "accessibility test", "test a hook",
  "mock fetch", "component test", "Vitest setup", or any task involving the frontend test
  suite. This skill covers Vitest configuration, React Testing Library patterns, axe-core
  accessibility assertions, hook isolation testing, fetch mocking, and CI test execution.
  Read this before writing any frontend test.
---

# Frontend — Test Strategy

Blue Steel's frontend uses Vitest as the test runner with React Testing Library for component
rendering and `vitest-axe` for accessibility assertions. Tests are co-located with their
subject files. There are no E2E tests — the highest confidence tier is component integration
tests with mocked API calls (D-056).

**Core principle:** Test behaviour, not implementation. Assert on what the user sees and
interacts with — not on internal state variables, component structure, or hook internals.

## Context

**Relevant decisions:**
- D-056: No E2E tests — highest confidence tier is integration tests (Testcontainers for backend,
  mocked-fetch component tests for frontend)
- D-030: Frontend stack; no SSR/RSC

**Running tests:**

```bash
# From apps/web/
npx vitest              # watch mode (development)
npx vitest run          # single pass (CI mode)
npx vitest run --reporter=verbose   # verbose output for debugging

# Type check (separate from tests — run both in CI)
npx tsc --noEmit
```

---

## Test File Location and Naming

Co-locate test files with their subjects:

```
src/features/input/
├── DiffReviewPage.tsx
├── DiffReviewPage.test.tsx    ← component test lives here
└── hooks/
    ├── useDiffState.ts
    └── useDiffState.test.ts   ← hook test lives here
```

**Naming convention:**
- Components: `MyComponent.test.tsx`
- Hooks: `useMyHook.test.ts`
- API client files: `resourceName.test.ts`

---

## Vitest Configuration

```typescript
// vite.config.ts (test section)
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  test: {
    globals: true,          // no need to import describe/it/expect
    environment: 'jsdom',   // DOM APIs available
    setupFiles: ['./src/test/setup.ts'],
    css: false,             // skip CSS processing in tests
  },
});
```

```typescript
// src/test/setup.ts
import '@testing-library/jest-dom';
import { expect } from 'vitest';
import { configureAxe, toHaveNoViolations } from 'jest-axe';

// Extend Vitest's expect with jest-axe matchers
expect.extend(toHaveNoViolations);

// Configure axe-core (optional — use to suppress known false positives)
export const axe = configureAxe({
  rules: {
    // example: 'region' rule can be noisy in isolated component tests
    region: { enabled: false },
  },
});
```

---

## Tier 1 — Component Tests

Use React Testing Library to render components and assert on visible output. Never assert on
internal state. The user cannot see internal state; neither should the test.

```tsx
// src/features/input/components/CommitButton.test.tsx
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { axe } from '../../test/setup';
import { CommitButton } from './CommitButton';

describe('CommitButton', () => {
  it('is disabled when there are unresolved UNCERTAIN cards', () => {
    render(<CommitButton unresolvedCount={2} onCommit={vi.fn()} isPending={false} />);

    const button = screen.getByRole('button', { name: /commit/i });
    expect(button).toBeDisabled();
    expect(screen.getByText(/2 items require resolution/i)).toBeInTheDocument();
  });

  it('is enabled when all UNCERTAIN cards are resolved', () => {
    const onCommit = vi.fn();
    render(<CommitButton unresolvedCount={0} onCommit={onCommit} isPending={false} />);

    const button = screen.getByRole('button', { name: /commit/i });
    expect(button).toBeEnabled();
    await userEvent.click(button);
    expect(onCommit).toHaveBeenCalledOnce();
  });

  it('has no accessibility violations', async () => {
    const { container } = render(
      <CommitButton unresolvedCount={0} onCommit={vi.fn()} isPending={false} />
    );
    expect(await axe(container)).toHaveNoViolations();
  });
});
```

**Key RTL methods:**

| Method | When to use |
|---|---|
| `screen.getByRole` | Primary — prefer role-based queries (most accessible) |
| `screen.getByText` | Secondary — for text content |
| `screen.getByLabelText` | For form inputs with `<label>` |
| `screen.queryByRole` | When asserting something is NOT present |
| `screen.findByRole` | When waiting for async UI changes |
| `userEvent.click` | Simulating user clicks (prefer over `fireEvent`) |
| `userEvent.type` | Simulating keyboard input |

---

## Tier 2 — Hook Tests

Test custom hooks in isolation using `renderHook` from React Testing Library:

```typescript
// src/features/input/hooks/useDiffState.test.ts
import { renderHook, act } from '@testing-library/react';
import { useDiffState } from './useDiffState';

describe('useDiffState', () => {
  it('counts unresolved UNCERTAIN cards correctly', () => {
    const { result } = renderHook(() => useDiffState(mockDiffWithTwoUncertain));

    expect(result.current.unresolvedUncertainCount).toBe(2);
  });

  it('decrements unresolved count when an UNCERTAIN card is resolved', () => {
    const { result } = renderHook(() => useDiffState(mockDiffWithTwoUncertain));

    act(() => {
      result.current.setDecision('item-1', {
        action: 'uncertain_resolved',
        resolution: 'match',
        matchedEntityId: 'existing-actor-id',
      });
    });

    expect(result.current.unresolvedUncertainCount).toBe(1);
  });
});
```

For hooks that depend on external context (TanStack Query, Zustand), wrap `renderHook` with the
appropriate providers:

```typescript
const wrapper = ({ children }: { children: React.ReactNode }) => (
  <QueryClientProvider client={new QueryClient()}>
    {children}
  </QueryClientProvider>
);
const { result } = renderHook(() => useMyHook(), { wrapper });
```

---

## Tier 3 — API Client Tests (Mocked Fetch)

Test API client hooks by mocking `fetch` via Vitest's `vi.spyOn` or the `msw` library. Never
make real network calls in tests.

```typescript
// src/api/sessions.test.ts — using vi.spyOn on global fetch
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useSessionStatus } from './sessions';

describe('useSessionStatus', () => {
  afterEach(() => vi.restoreAllMocks());

  it('polls every 2 seconds while status is processing', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({
        data: { sessionId: 'abc', status: 'processing', failureReason: null, message: null }
      }), { status: 200 })
    );

    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const wrapper = ({ children }: { children: React.ReactNode }) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    );

    const { result } = renderHook(
      () => useSessionStatus('campaign-1', 'session-abc'),
      { wrapper }
    );

    await waitFor(() => expect(result.current.data?.status).toBe('processing'));
    expect(fetchSpy).toHaveBeenCalled();
  });
});
```

**Preferred: use `msw` (Mock Service Worker) for complex API mocking:**

```typescript
// src/test/server.ts
import { setupServer } from 'msw/node';
import { http, HttpResponse } from 'msw';

export const server = setupServer(
  http.get('/api/v1/campaigns/:id/sessions/:sid/status', () => {
    return HttpResponse.json({
      data: { sessionId: 'abc', status: 'draft', failureReason: null, message: null }
    });
  }),
);

// In setup.ts:
beforeAll(() => server.listen());
afterEach(() => server.resetHandlers());
afterAll(() => server.close());
```

---

## Accessibility Testing with axe-core

Every component in `components/domain/` and every feature-level page must have at least one
axe-core assertion. This is not optional (see ARCHITECTURE.md §9.6).

```tsx
import { render } from '@testing-library/react';
import { axe } from '../../test/setup';
import { AnnotationThread } from './AnnotationThread';

it('has no accessibility violations', async () => {
  const { container } = render(
    <AnnotationThread
      campaignId="campaign-1"
      entityType="actor"
      entityId="actor-1"
    />
  );
  expect(await axe(container)).toHaveNoViolations();
});
```

**Where axe-core assertions are mandatory:**
- All components in `src/components/domain/`
- Diff review cards (UNCERTAIN card, Conflict card, DiffCard, CommitButton)
- Entity profile pages (Entities, Spaces)
- Timeline and Relations graph containers
- QueryPage and AnswerDisplay
- All form components (QuestionForm, session submission form, annotation input)

---

## Testing Zustand Stores

Test Zustand stores directly — no component wrapper needed:

```typescript
// src/store/authStore.test.ts
import { useAuthStore } from './authStore';

describe('authStore', () => {
  beforeEach(() => {
    // Reset store state between tests
    useAuthStore.setState({ accessToken: null, userId: null, isAdmin: false });
  });

  it('stores tokens on setTokens', () => {
    useAuthStore.getState().setTokens('my-jwt', 'user-123', false);

    expect(useAuthStore.getState().accessToken).toBe('my-jwt');
    expect(useAuthStore.getState().userId).toBe('user-123');
  });

  it('clears state on logout', () => {
    useAuthStore.getState().setTokens('my-jwt', 'user-123', false);
    useAuthStore.getState().logout();

    expect(useAuthStore.getState().accessToken).toBeNull();
  });
});
```

---

## What NOT to Test

- **Internal implementation details** — do not assert on state variable values directly from
  component tests; test the UI output instead.
- **shadcn/ui primitive internals** — `src/components/ui/` is third-party; do not write tests
  for it. Test your wrappers in `src/components/domain/`.
- **TanStack Query internals** — do not test that `useQuery` returns `isLoading: true`. Test
  that your component renders a loading spinner when `isLoading` is true.
- **CSS classes or Tailwind tokens** — test that an element is visible/invisible; not which
  CSS class it has.

---

## CI Test Execution

CI runs in order: `type-check → lint → vitest run → vite build` (see `apps/web/CLAUDE.md §4`
and `ci-cd` skill). All four steps must pass before a PR can merge.

Vitest runs with `--reporter=default` in CI unless a failure needs diagnosis. To diagnose
a specific failing test locally:

```bash
npx vitest run src/features/input/DiffReviewPage.test.tsx --reporter=verbose
```

---

## Common Pitfalls

- **Using `screen.getByTestId` as the default query.** `data-testid` is a last resort for
  elements that have no accessible role or label. Prefer `getByRole`, `getByLabelText`,
  `getByText` in that order.

- **Not wrapping `act(...)` around state updates.** When a test triggers a state update
  (e.g., calling a setter function from a hook), wrap it in `act()` to flush the update before
  asserting.

- **Forgetting to reset Zustand store state between tests.** Zustand stores persist across
  tests in the same file unless explicitly reset with `useStore.setState({...})` in `beforeEach`.

- **Using `fireEvent` instead of `userEvent`.** `userEvent` simulates real browser interaction
  (focus, click, key events in sequence). `fireEvent` dispatches raw DOM events and misses
  intermediate states. Use `userEvent` for all user interaction simulation.

- **Asserting on async output without `await`/`waitFor`.** When testing components with async
  effects (data fetching, polling), use `await screen.findByRole(...)` or `await waitFor(...)`.
  Synchronous assertions on async state will produce false positives.

- **Not isolating TanStack Query between tests.** Create a fresh `QueryClient` in each test
  or test file. A shared `QueryClient` lets cached responses bleed between tests.

  ```typescript
  // Helper: create a test QueryClient with retries disabled
  export function createTestQueryClient() {
    return new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    });
  }
  ```

## References

- `apps/web/CLAUDE.md` §7 (frontend conventions), §9 "Writing a frontend test"
- `DECISIONS.md` D-056 (no E2E), D-030 (frontend stack)
- `frontend-api-resource` skill (TanStack Query hook patterns)
- `frontend-diff-review` skill (diff card component test examples)
- `frontend-exploration` skill (React Flow accessibility test note)
- `frontend-query-mode` skill (QueryPage test patterns)
