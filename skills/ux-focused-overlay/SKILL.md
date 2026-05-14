---
name: ux-focused-overlay
description: >
  Use this skill whenever you need to build any contextual interaction that would traditionally
  use a modal or dialog: UNCERTAIN card resolution, annotation creation, entity detail expansion,
  confirmation prompts, inline editing with backdrop. Triggers: "overlay", "modal", "dialog",
  "contextual expand", "backdrop", "focus state", "UNCERTAIN card resolution". Traditional modals
  are forbidden (D-082) — this pattern is the only replacement.
---

# UX Skill — Focused Overlay

## Technical Context

**Files to create/use:**
- `src/components/domain/FocusedOverlay.tsx` — reusable overlay wrapper (create once, reuse everywhere)
- `src/hooks/useEscapeKey.ts` — ESC key handler hook

**Dependencies:** None beyond React. No external overlay/dialog library.

**Z-index contract:**
- `z-40` — backdrop (`<div>` covering the viewport)
- `z-50` — focused element (elevated above backdrop)
- Never use `z-index` values outside this pair for overlay stacking.

**UX_CONSTITUTION.md §4** is the authority. This skill implements it.

---

## Golden Rules

**Do:**
- Backdrop: `bg-slate-900/30 backdrop-blur-sm` — always this exact combination
- Enter transition: `duration-200 ease-out` | Leave transition: `duration-150 ease-in`
- Focused element shadow: `shadow-xl ring-2 ring-blue-500/50`
- Focused element stays **anchored to its original DOM position** — never re-centred with `fixed inset-0 flex items-center justify-center`
- Always call `useEscapeKey(onClose)` inside the overlay
- Always attach `onClick={onClose}` to the backdrop `<div>`
- Always wrap focus-trap: first focusable child gets `autoFocus`

**Don't:**
- Never render `<Dialog>` or `<AlertDialog>` from shadcn/ui for contextual actions
- Never centre overlays in the viewport — anchored position only
- Never skip the ESC handler — keyboard accessibility is mandatory (D-082)
- Never use `z-index` values other than `z-40` / `z-50`
- Never add a close "X" button without also supporting ESC and backdrop click

---

## Reference Snippet

```tsx
// src/hooks/useEscapeKey.ts
import { useEffect } from 'react';

export function useEscapeKey(handler: () => void) {
  useEffect(() => {
    const listener = (e: KeyboardEvent) => { if (e.key === 'Escape') handler(); };
    document.addEventListener('keydown', listener);
    return () => document.removeEventListener('keydown', listener);
  }, [handler]);
}
```

```tsx
// src/components/domain/FocusedOverlay.tsx
import { useEscapeKey } from '@/hooks/useEscapeKey';

interface Props {
  open: boolean;
  onClose: () => void;
  children: React.ReactNode;
  className?: string;
}

export function FocusedOverlay({ open, onClose, children, className }: Props) {
  useEscapeKey(onClose);

  if (!open) return null;

  return (
    <>
      {/* Backdrop — z-40 */}
      <div
        className="fixed inset-0 z-40 bg-slate-900/30 backdrop-blur-sm"
        onClick={onClose}
        aria-hidden="true"
      />
      {/* Focused element — z-50, anchored in flow */}
      <div
        className={[
          'relative z-50 shadow-xl ring-2 ring-blue-500/50 rounded-2xl',
          'transition-shadow duration-200 ease-out',
          className,
        ].join(' ')}
        role="dialog"
        aria-modal="true"
      >
        {children}
      </div>
    </>
  );
}
```

**Usage pattern (UNCERTAIN card example):**
```tsx
const [open, setOpen] = useState(false);

<div className="relative">
  <UncertainCard onClick={() => setOpen(true)} />
  <FocusedOverlay open={open} onClose={() => setOpen(false)}>
    <ResolutionForm onResolve={(r) => { onResolve(r); setOpen(false); }} />
  </FocusedOverlay>
</div>
```
