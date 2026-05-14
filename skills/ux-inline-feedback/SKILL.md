---
name: ux-inline-feedback
description: >
  Use this skill whenever you need to communicate system feedback to the user: form submission
  results, API errors, session commit success/failure, pipeline status changes. Triggers: "alert",
  "banner", "success message", "error message", "warning", "form feedback", "API error display",
  "inline notification". Toast notifications are forbidden (D-083) — this pattern is the
  only replacement.
---

# UX Skill — Inline Feedback (InlineBanner)

## Technical Context

**Files to create/use:**
- `src/components/domain/InlineBanner.tsx` — the single banner component; four variants

**Dependencies:** None. No toast library (`sonner`, `react-hot-toast`, etc.) is installed or permitted.

**Placement rule:** Banners render **above the content they describe** — inside the feature container, not at the page root. A commit error banner renders inside `DiffReviewPage`, not in `App.tsx`.

**UX_CONSTITUTION.md §5** is the authority. This skill implements it.

---

## Golden Rules

**Do:**
- Use `role="alert"` and `aria-live="polite"` on every banner
- Enter animation: `animate-in slide-in-from-top-2 duration-200`
- `success`, `warning`, `info` auto-clear after **8000ms** via `useEffect` + `setTimeout`
- `error` banners **never auto-clear** — user must dismiss with the X button
- Inject banner state with `useState<BannerState | null>(null)` in the feature component
- Dismiss via X button (`aria-label="Dismiss"`) always present, regardless of auto-clear

**Don't:**
- Never call any `toast()`, `sonner()`, or similar function
- Never render banners at a global portal level (no `ReactDOM.createPortal` to `document.body`)
- Never show multiple simultaneous banners — new banners replace the previous one
- Never use `variant="destructive"` from shadcn/ui Toast — use `InlineBanner` with `variant="error"`
- Never auto-clear an `error` banner

---

## Reference Snippet

```tsx
// src/components/domain/InlineBanner.tsx
import { X } from 'lucide-react';
import { useEffect } from 'react';

type Variant = 'success' | 'warning' | 'error' | 'info';

const STYLES: Record<Variant, string> = {
  success: 'bg-green-50 border-green-200 text-green-800',
  warning: 'bg-amber-50 border-amber-200 text-amber-800',
  error:   'bg-red-50 border-red-200 text-red-800',
  info:    'bg-blue-50 border-blue-200 text-blue-800',
};

interface Props {
  variant: Variant;
  message: string;
  onDismiss: () => void;
}

export function InlineBanner({ variant, message, onDismiss }: Props) {
  useEffect(() => {
    if (variant === 'error') return;
    const id = setTimeout(onDismiss, 8000);
    return () => clearTimeout(id);
  }, [variant, onDismiss]);

  return (
    <div
      role="alert"
      aria-live="polite"
      className={[
        'flex items-start gap-3 rounded-lg border p-4',
        'animate-in slide-in-from-top-2 duration-200',
        STYLES[variant],
      ].join(' ')}
    >
      <span className="flex-1 text-sm">{message}</span>
      <button
        onClick={onDismiss}
        aria-label="Dismiss"
        className="shrink-0 opacity-70 hover:opacity-100"
      >
        <X className="h-4 w-4" />
      </button>
    </div>
  );
}
```

**Usage pattern (commit result):**
```tsx
const [banner, setBanner] = useState<{ variant: Variant; message: string } | null>(null);

const handleCommit = async () => {
  try {
    await commitMutation.mutateAsync(payload);
    setBanner({ variant: 'success', message: 'Session committed to world state.' });
  } catch {
    setBanner({ variant: 'error', message: 'Commit failed. Check your card decisions and try again.' });
  }
};

// In JSX — above the content it describes:
{banner && (
  <InlineBanner
    variant={banner.variant}
    message={banner.message}
    onDismiss={() => setBanner(null)}
  />
)}
```
